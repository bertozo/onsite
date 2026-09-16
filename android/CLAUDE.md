# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

GeoTracker — an Android app (Kotlin + Jetpack Compose) that lets a user record how long they spend at a location using GPS. Tapping **Start** saves a timestamped record (lat/lon + optional label); tapping **Stop** saves a matching end record. History is listed with computed session durations. All comments/strings in the codebase are in Portuguese (pt-BR); keep new UI text and comments consistent with that unless told otherwise.

## Build & run

- Open the project root in Android Studio (Iguana/Koala+) and let it sync Gradle — the `gradle/wrapper/gradle-wrapper.jar` binary is intentionally not committed; Android Studio downloads/creates it on first sync (accept the prompt). If working from the CLI instead, run `gradle wrapper` once to generate it, then use `./gradlew`.
- Build debug APK: `./gradlew assembleDebug`
- Install on a connected device/emulator: `./gradlew installDebug`
- There is no test source set yet (no `app/src/test` or `app/src/androidTest`), so there are no test/lint commands to run.
- GPS testing requires either a physical device or an emulator with a location set via Extended Controls > Location. There is no background location tracking — only foreground (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`).

## Architecture

Single-screen MVVM app, all under `app/src/main/java/com/rodolfobertozo/geotracker/`:

- `MainActivity.kt` — the entire UI in one Composable (`GeoTrackerScreen`). Handles the runtime location-permission flow directly (via `rememberLauncherForActivityResult`) and renders state from the ViewModel: current lat/lon, label input, Start/Stop button, and a `LazyColumn` history list.
- `LocationTrackerViewModel.kt` — an `AndroidViewModel` holding a `TrackerUiState` (`MutableStateFlow`). Fetches a one-shot location via `FusedLocationProviderClient.getCurrentLocation(...)` (not continuous updates). `startTracking()`/`stopTracking()` each insert one `LocationRecord` row (type START or STOP) rather than updating a single session row — a "session" is reconstructed at read time.
- `data/` — Room persistence:
  - `LocationRecord.kt` — the entity (`location_records` table) plus the `EventType { START, STOP }` enum.
  - `LocationDao.kt` — insert, and two queries: all records descending by time, and the most recent record (used on ViewModel init to restore whether a session is currently active — active means the latest record is a START with no matching STOP yet).
  - `AppDatabase.kt` — singleton Room database (`geotracker.db`) via double-checked-locking `getInstance()`.
  - `Converters.kt` — Room TypeConverter for `EventType` <-> String.

**Session/duration logic**: there is no explicit "session" entity. `records` is a flat, time-descending stream of START/STOP rows. Duration for a completed session is computed in the UI (`MainActivity.kt`, in the `LazyColumn` items block) by looking at `records[index + 1]` immediately after a STOP and checking it's a START — i.e., it assumes STOP/START rows always alternate correctly and are contiguous by insertion order. Any change to how records are inserted (e.g., allowing edits, deletions, or concurrent sessions) must preserve this adjacency assumption or the duration calculation will silently break.

**State flow**: UI state changes originate only from the ViewModel (`_uiState.update { ... }`); `MainActivity` never mutates state directly, it only reads `uiState`/`records` via `collectAsState()` and calls ViewModel methods.
