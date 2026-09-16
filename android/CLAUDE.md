# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

OnSite (package `com.xbertz.onsite`) — an Android app (Kotlin + Jetpack Compose) for a self-employed tradesperson in Australia to log work sessions per site (GPS or manual entry), keep clients/sites/job types, and generate invoice PDFs. UI strings live in `res/values*/strings.xml` in English (default), Portuguese (`values-pt`) and Spanish (`values-es`); **never hardcode user-facing text in Kotlin** — add a key to all three files. Code comments are in English.

## Build & run

- Open the project root in Android Studio and let it sync Gradle. `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed; Android Studio generates it on first sync.
- CLI (Windows/Git Bash): the wrapper needs a JDK 17–21, e.g. `export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"` (the JBR bundled with Android Studio is too new for Gradle 8.14). Then `./gradlew assembleDebug` or `./gradlew installDebug`.
- No test source sets yet; verification is a compile + install on the connected device (`adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`).
- Toolchain: Kotlin 1.9.24 (no K2), AGP 8.5.2, Compose BOM 2024.06.00 / compiler ext 1.5.14, Room 2.6.1 via **kapt** (not KSP), minSdk 26 / target 34, JVM target 1.8. Stay within those APIs when adding code or dependencies.
- Location is foreground only (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`); `INTERNET` is used only for address search. `CAMERA` is requested at runtime for the site-photo tool (CameraX 1.3.4, preview + capture both 4:3, preview letterboxed so the overlay can be laid on the frame rectangle); `WRITE_EXTERNAL_STORAGE` is declared only for API ≤ 28 to save stamped photos to the gallery.

## Architecture

MVVM, everything under `app/src/main/java/com/xbertz/onsite/`:

- `MainActivity.kt` — **all** Compose UI: `AppRoot` switches between `Screen` values (menu, tracker, reports, companies, sites, job types, profile, settings, planning, photo); each screen is a composable in the same file, plus its dialogs. `MainActivity.attachBaseContext` wraps the context with `LocaleManager` (defined in `AppLocale.kt`, together with `AppLanguage`) so the chosen language applies before resources are read; a language change calls `recreate()` and `AppRoot` keeps the current screen via `rememberSaveable`. Theme colours live in `ui/theme/Theme.kt`.
- ViewModels are `AndroidViewModel`s, one per area: `LocationTrackerViewModel` (start/stop with GPS, manual sessions, edit/delete), `CompanyViewModel`, `SiteViewModel` (address search with debounce), `JobTypeViewModel`, `ProfileViewModel`, `InvoiceViewModel` (PDF generation + sequential invoice number), `ReportViewModel` (column selection), `SettingsViewModel` (theme + language), `PlanningViewModel` (planned jobs per day; calendar navigation lives in `CalendarNavigator`/`CalendarUiState` in `CalendarState.kt`, rendered by the `CalendarPanel` composable), `PhotoViewModel` (site-photo tool: in-app CameraX capture from `StampCameraView`, stamped by `photo/PhotoStamper.kt` and saved to the gallery through MediaStore). The live overlay in `StampCameraView` and the final stamp share the geometry constants on `PhotoStamper` (`TEXT_SIZE_FRACTION`, `PADDING_FRACTION`, `LINE_HEIGHT_FACTOR`, `STROKE_FACTOR`, `LOGO_WIDTH_FRACTION`) and the line list from `PhotoViewModel.stampLines` — change them together or the viewfinder stops matching the saved photo. UI state flows out via `StateFlow`; screens never mutate state directly.
- ViewModel messages are **string resource ids** (`@StringRes Int` or `UiMessage(resId, args)`), resolved in the UI with `stringResource`, so they follow the app language. Code holding only an Application context uses `LocaleManager.resources(context)`.
- `data/` — Room (`onsite.db`, currently version 10, migrations in `AppDatabase`). Entities: `TrackingSession` (one row per session; `companyName`/`siteLabel`/`jobTypeLabel` stored **as text**), `PlannedJob` (scheduled job for a day, same by-name references, optional fields), `Company`, `Site` (with lat/lon + address), `JobType`, `Profile` (single row, id = 1).
- Non-DB persisted state is in SharedPreferences: `settings` (`theme_mode`, `language`, `photo_timestamp_position`, `photo_label`) and `invoices` (`next_invoice_number`, bumped by `InvoiceViewModel` after each PDF). Files in `filesDir`: `profile_photo.jpg`, `stamp_logo.png` (photo-tool logo; absence means "no logo").
- `invoice/` — `InvoiceData.kt` groups sessions into one `InvoiceLine` per worked day and exposes `valueFor(ReportColumn)`; `InvoicePdfTemplate.kt` renders the fixed A4 layout with Android's `PdfDocument` (no PDF library). PDFs go to `cacheDir/invoices/` and are shared through the `FileProvider` declared in the manifest (`res/xml/file_paths.xml`).
- `report/ReportColumn.kt` — the single column template used by both the on-screen `ReportTable` and the PDF; enum order is display order, `DATE`/`HOURS` are always shown.
- `Validators.kt` — ABN (modulus-89 check), BSB, account number, phone, email; blank is valid (fields are optional).
- `AddressSearch.kt` — Photon (OSM) geocoder, restricted to Australia's bounding box.

## Things that are easy to break

- Sessions and planned jobs reference companies/job types **by name**. Renaming one must call `TrackingSessionDao.renameCompany`/`renameJobType` **and** the `PlannedJobDao` equivalents (the edit flows already do) or reports and invoices stop matching.
- Sites are also referenced by `siteLabel`, and the report/invoice `ADDRESS` column is resolved by looking the label up in `sitesByLabel` (`buildInvoiceLines`). There is **no** `renameSite` yet — `SiteViewModel.saveEditedSite` only updates the `sites` row, so renaming a site silently drops the address for its past sessions. Add a rename query if you touch that flow.
- `ReportColumn` order and `InvoicePdfTemplate.columnSpecs` widths define the invoice layout; changing columns means updating both `valueFor` and the specs.
- Room schema changes require a new `Migration` in `AppDatabase` and a version bump; `exportSchema` is false.
- `LocaleManager` reads SharedPreferences `settings`/`language` synchronously — keep it cheap, it runs in `attachBaseContext`.
