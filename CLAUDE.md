# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- Build: `./gradlew build` (or `gradlew.bat build` on Windows)
- Install debug build: `./gradlew installDebug`
- Unit tests: `./gradlew test` — single test: `./gradlew test --tests "dev.whayn.thyme.SomeTest"`
- Instrumented tests (needs a device/emulator): `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

**Add `--rerun-tasks` when you need KSP to actually re-run.** Gradle frequently reports `compileDebugKotlin UP-TO-DATE` right after source edits, which silently skips Room's code generation and its validation warnings.

**Read `w: [ksp]` lines in the build output.** Room reports query/entity mismatches as *warnings*, not errors, so the build goes green while the field is silently null forever. `AS logID` vs a `logId` property cost a debugging session exactly this way.

## Tooling

`adb` lives at `C:\Users\mayeu\AppData\Local\Android\Sdk\platform-tools`. The usual loop:

```bash
adb -s emulator-5554 shell am force-stop dev.whayn.thyme
adb -s emulator-5554 shell am start -n dev.whayn.thyme/.MainActivity
adb -s emulator-5554 exec-out screencap -p > shot.png
```

**Inspect the database through Android Studio's App Inspection → Database Inspector**, not by copying files off the device. Room runs SQLite in WAL mode, so `thyme.db` is nearly empty while `thyme.db-wal` holds the real data. Copying all three files (including a stale `-shm`) makes SQLite serve confidently wrong rows — this produced a completely bogus "writes aren't persisting" diagnosis. If you must copy, take `.db` + `-wal` only and let SQLite rebuild the index.

**To seed test data** (the only sane way to test a 21-on/7-off cycle that started three weeks ago): force-stop, pull `.db` + `-wal`, `PRAGMA wal_checkpoint(TRUNCATE)`, INSERT with Python's `sqlite3`, push back with `base64 -w0 file | adb shell "run-as dev.whayn.thyme sh -c 'base64 -d > databases/thyme.db'"`, then **delete `thyme.db-wal` and `thyme.db-shm` on device** or the stale WAL replays over your work.

**Driving the UI with `adb shell input` is fiddly.** `input text` does not raise the soft keyboard, so a following `keyevent 4` (BACK) hits the app's own BackHandler and exits instead of dismissing anything. Disabling the Latin IME just hands control to the voice IME, which covers the save button. Prefer seeding the database over scripting long form flows.

## Build configuration

- `android.disallowKotlinSourceSets=false` in `gradle.properties` is **required**: KSP still registers generated sources via the `kotlin.sourceSets` DSL, which AGP 9's built-in Kotlin support forbids. Remove it once KSP catches up. Gradle silently ignores misspelled properties, so a typo here looks exactly like the flag not working.
- `androidx.compose.material:material-icons-core` is frozen at **1.7.8** (deprecated, still pinned by the Compose BOM) and only carries a small curated icon subset. `material-icons-extended` is included alongside it for everything else — prefer real `Icons.Filled.*`/`Icons.AutoMirrored.Filled.*` icons over hand-rolled vector drawables.
- Room still runs `.fallbackToDestructiveMigration(dropAllTables = true)` and `exportSchema = false`. **Both must go before real data exists**; at that point turn on schema export and start writing `Migration`s.
- "Kotlin does not yet support 25 JDK target, falling back to JVM_24" is harmless.

## Architecture

Single-module app (`:app`), package `dev.whayn.thyme`, Jetpack Compose + Material 3, min SDK 26 / target SDK 37, Kotlin 2.2.

**Navigation** (`ui/nav/`): one `NavHost` (`ThymeNavHost.kt`) with type-safe `@Serializable` routes (`Destinations.kt`): `Today`, `Medications`, `Settings`, `MedicationEditor(medicationId?, regimenId?)`. `MainActivity.kt` hosts the Scaffold, bottom nav, and the FAB that opens the editor; each destination composable creates its own ViewModel via a `factory(context)` on the companion object (no DI framework).

The editor's two nullable ids encode three modes: **both null** adds a new medication; **medicationId only** adds another course to an existing drug; **both set** edits that course. This is the only path to multiple concurrent regimens (a taper, say), so keep it intact.

**Data layer is event-sourced, deliberately** (Room, `data/`): `medications` (the drug) → `regimens` (one course: `startDate`/`endDate`, `daysOfWeek` bitmask, `intervalDays`, `cycleOnDays`/`cycleOffDays`) → `scheduled_doses` (time + quantity within a regimen) → `dose_logs` (a take event: `scheduledDoseId`, `forDate`, `takenAt`). There is **no `taken` boolean anywhere** — a tick *is* a log row, so history and stats fall out of `GROUP BY dose_logs` for free and midnight rollover needs no job. Never reintroduce a boolean flag for "taken".

Two load-bearing pieces of logic, both in `data/DoseDao.kt`:
- `observeDosesForDate` computes which doses apply on a given day entirely inside one SQL `WHERE` clause (day-of-week bitmask AND interval-days modulo AND on/off cycle), relying on dates being bound as epoch-day longs (see `Converters.fromLocalDate`) so the modulo arithmetic works in SQLite. The `forDate` match sits in the `LEFT JOIN … ON`, **not** in `WHERE` — moving it down collapses the left join into an inner one and untaken doses vanish from the list.
- `saveMedication` is a single `@Transaction` that upserts a medication, its regimen, and reconciles `scheduled_doses` **by row id, not by clock time** — editing a dose's time moves that row (keeping its `dose_logs` attached) rather than deleting/recreating it.

Soft delete throughout: stopping a course sets `regimen.endDate = today` (past days keep rendering what was actually taken); deleting a medication sets `medication.active = 0`. Rows are never hard-deleted because `dose_logs` point at them.

`SettingsRepository` (DataStore Preferences, not Room) persists `ThymeThemeMode` (System/Light/Dark) and dynamic color — lives in `data/` rather than `ui/theme/` because dependencies point UI → data, never the reverse.

**Theme** (`ui/theme/Color.kt`): forest green is the app accent (light: `Forest40`, dark: `Forest80`) — "dark green" describes the accent color, not a forced dark mode; the app always follows system theme unless overridden in Settings. `Honey` is reserved exclusively for "due" state and is deliberately excluded from `MedicationColorsDark/Light`, so no medication can be visually mistaken for an overdue one. `Medication.colorIndex` stores an index into that palette (not an ARGB value) so one saved choice renders correctly in both light and dark.

**The day starts at 05:00, not midnight.** `dayOrder()` in `ui/DoseListScreen.kt` maps times to minutes-since-05:00 so a 02:00 dose sorts to the *end* of the list as tonight's last pill. Sorting, section order (Morning/Midday/Evening/Night) and overdue detection all derive from that one function — keep them agreeing.

## Conventions worth preserving

- ViewModels expose `StateFlow`, obtained via `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ...)`; read from the backing source flow (not a stale `.value`) when a write needs the current value while unsubscribed — see `DoseListViewModel.currentDate()`.
- Route matching against `NavDestination` uses `hasRoute(Destinations.X::class)`, not string comparison, to avoid route-name-prefix collisions.
- A whole row is one control: `Modifier.clip(shape).toggleable(..., role = Role.Checkbox)` on the card, with the visual checkbox/indicator inside made decorative (`onCheckedChange = null`, `contentDescription = null`). Clip *before* toggleable so the ripple matches the shape that looks tappable.
- Text fields own **raw text**, never a parsed value. Binding a field to a parsed `Double` makes it impossible to clear or to type "0." on the way to "0.5" — hence `EditableDose.quantityText`. Parse at save time and let validation block the button.
- Validation says *what* is wrong (`ValidationHint` in the editor) rather than leaving a mysteriously greyed-out button.
- Motion respects the system setting via `rememberReducedMotion()` (`ui/theme/Theme.kt`), which reads `ANIMATOR_DURATION_SCALE` and swaps springs for `snap()`.
- Localized day names come from `getDisplayName(TextStyle.NARROW, locale)` — never `DayOfWeek.name.take(1)`, which is English-only.
- App-specific color roles that Material lacks (e.g. "due") live in `ThymeAccents` behind a `CompositionLocal`, not as hardcoded `Color` values at call sites.

## Gotchas already paid for

- **Segmented buttons do not wrap.** `SingleChoiceSegmentedButtonRow` is one connected control; chunking it into rows renders as several independent toggles with the wrong corner rounding. Use `FilterChip`s in a `FlowRow` for anything that might need to wrap.
- **Trailing lambdas bind to the *last* parameter.** `joinToString { " · " }` sets the *transform*, not the separator — valid Kotlin, wrong output, no compiler error.
- **Reconcile by identity, never by an editable value.** Keying on something the user can change turns an edit into a delete-plus-insert and silently strands whatever pointed at the old row.
- Material3's `TimePicker` defaults its AM/PM selector to `tertiaryContainer`, which here is thyme-blossom lilac; the editor overrides it via `TimePickerDefaults.colors(...)`.
