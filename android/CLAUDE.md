# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

OnSite (package `com.rodolfobertozo.onsite`) — an Android app (Kotlin + Jetpack Compose) for a self-employed tradesperson in Australia to log work sessions per site (GPS or manual entry), keep clients/sites/job types, and generate invoice PDFs. UI strings live in `res/values*/strings.xml` in English (default), Portuguese (`values-pt`) and Spanish (`values-es`); **never hardcode user-facing text in Kotlin** — add a key to all three files. Code comments are in English.

## Build & run

- Open the project root in Android Studio and let it sync Gradle. `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed; Android Studio generates it on first sync.
- CLI (Windows/Git Bash): the wrapper needs a JDK 17–21, e.g. `export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"` (the JBR bundled with Android Studio is too new for Gradle 8.14). Then `./gradlew assembleDebug` or `./gradlew installDebug`.
- No test source sets yet; verification is a compile + install on the connected device (`adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`).
- Location is foreground only (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`); `INTERNET` is used only for address search.

## Architecture

MVVM, everything under `app/src/main/java/com/rodolfobertozo/onsite/`:

- `MainActivity.kt` — **all** Compose UI: `AppRoot` switches between `Screen` values (menu, tracker, reports, companies, sites, job types, profile, settings); each screen is a composable in the same file, plus its dialogs. `MainActivity.attachBaseContext` wraps the context with `LocaleManager` so the chosen language applies before resources are read; a language change calls `recreate()` and `AppRoot` keeps the current screen via `rememberSaveable`.
- ViewModels are `AndroidViewModel`s, one per area: `LocationTrackerViewModel` (start/stop with GPS, manual sessions, edit/delete), `CompanyViewModel`, `SiteViewModel` (address search with debounce), `JobTypeViewModel`, `ProfileViewModel`, `InvoiceViewModel` (PDF generation + sequential invoice number), `ReportViewModel` (column selection), `SettingsViewModel` (theme + language). UI state flows out via `StateFlow`; screens never mutate state directly.
- ViewModel messages are **string resource ids** (`@StringRes Int` or `UiMessage(resId, args)`), resolved in the UI with `stringResource`, so they follow the app language. Code holding only an Application context uses `LocaleManager.resources(context)`.
- `data/` — Room (`onsite.db`, currently version 9, migrations in `AppDatabase`). Entities: `TrackingSession` (one row per session; `companyName`/`siteLabel`/`jobTypeLabel` stored **as text**), `Company`, `Site` (with lat/lon + address), `JobType`, `Profile` (single row, id = 1).
- `invoice/` — `InvoiceData.kt` groups sessions into one `InvoiceLine` per worked day and exposes `valueFor(ReportColumn)`; `InvoicePdfTemplate.kt` renders the fixed A4 layout with Android's `PdfDocument` (no PDF library). PDFs go to `cacheDir/invoices/` and are shared through the `FileProvider` declared in the manifest (`res/xml/file_paths.xml`).
- `report/ReportColumn.kt` — the single column template used by both the on-screen `ReportTable` and the PDF; enum order is display order, `DATE`/`HOURS` are always shown.
- `Validators.kt` — ABN (modulus-89 check), BSB, account number, phone, email; blank is valid (fields are optional).
- `AddressSearch.kt` — Photon (OSM) geocoder, restricted to Australia's bounding box.

## Things that are easy to break

- Sessions reference companies/job types **by name**. Renaming one must call `TrackingSessionDao.renameCompany`/`renameJobType` (the edit flows already do) or reports and invoices stop matching.
- `ReportColumn` order and `InvoicePdfTemplate.columnSpecs` widths define the invoice layout; changing columns means updating both `valueFor` and the specs.
- Room schema changes require a new `Migration` in `AppDatabase` and a version bump; `exportSchema` is false.
- `LocaleManager` reads SharedPreferences `settings`/`language` synchronously — keep it cheap, it runs in `attachBaseContext`.
