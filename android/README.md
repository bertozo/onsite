# OnSite

Android app (Kotlin + Jetpack Compose) for logging hours worked per site and generating invoice PDFs. Built for self-employed tradespeople in Australia (ABN, BSB).

## Features

- **Start tracking** — pick a company, site and job type and tap **Start**; the app records the GPS position. **Stop** ends the session and shows a summary. Sessions can also be **added manually** (date, start time, duration).
- **Records** — Companies (clients, with ABN/phone/email), Sites (locations, with address search), Job types. All can be edited and deleted.
- **Profile** — your details (name, title, phone, email, ABN, photo) and bank details (BSB, account) printed on the invoice.
- **Reports** — sessions by period, with configurable columns (Date, Site, Address, Company, Job type, Start, End, Hours).
- **PDF invoice** — from a report, choose the company, invoice number and hourly rate; the PDF uses a fixed A4 template with the same columns as the report and opens in the share sheet.
- **Languages** — English, Portuguese and Spanish, selectable in Settings (plus light/dark theme).

Data is stored locally (Room/SQLite). There is no background tracking.

## Running

1. Open the project folder in **Android Studio** (Iguana/Koala or newer) and let Gradle sync. `gradle/wrapper/gradle-wrapper.jar` is not committed — Android Studio generates it on the first sync (or run `gradle wrapper` in the project root).
2. Connect a physical device (recommended for real GPS) or an emulator with a configured location.
3. Run ▶ and grant the location permission.

From the command line: `./gradlew assembleDebug` (APK) or `./gradlew installDebug` (installs on the connected device).

## Structure

```
app/src/main/java/com/xbertz/onsite/
├── MainActivity.kt            # all Compose UI (menu, screens, dialogs)
├── *ViewModel.kt              # one ViewModel per area (tracker, companies, sites, job types, profile, invoice, report, settings)
├── AppLocale.kt               # language selection (SharedPreferences + attachBaseContext)
├── Validators.kt              # ABN, BSB, account, phone and email rules
├── AddressSearch.kt           # address search (Photon/OSM, restricted to Australia)
├── data/                      # Room: entities, DAOs, AppDatabase (migrations)
├── invoice/                   # InvoiceData (one line per day) + InvoicePdfTemplate (A4 layout)
└── report/ReportColumn.kt     # column template shared by the report and the PDF
```

## Address search

Uses the public [Photon](https://photon.komoot.io) API (OpenStreetMap), limited to Australia's bounding box. Requires `INTERNET`.
