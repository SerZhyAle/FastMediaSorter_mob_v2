# Phase 03 - Compass and speed gadgets

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 7 / 7
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Put the first two readings on the desktop as gadgets, and establish the two rules every later sensor gadget inherits: a gadget whose sensor is absent is never offered, and a gadget whose permission was refused stays on the desktop showing an honest empty state.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] UI placement is decided in the strategic spec and not open here. §0 and §3.1.1 are the owner's own words placing altitude and heading together on one tile; §3.1.2 fixes kilometres per hour; §11.1 places both tiles on the launcher desktop through the existing gadget picker; §11.2 removes an unavailable tile from that picker; §11.5 keeps a permission-refused tile on the desktop in an empty state; §3.2 requires heading and altitude to be readable as words and not only as an arrow. Tile spans are not an owner decision - S1178 Phase 04 settled the same question by taking `WeatherGadget`'s spans, and this phase does the same.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +26 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +26 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +26 |
| `app_v2/src/main/res/drawable/ic_compass.xml` | New | ≤ 30 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadget.kt` | Modified | 107 → ≤ 125 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | 71 → ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | Modified | ≤ +4 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_compass.xml` | New | ≤ 100 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_speed.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/CompassGadget.kt` | New | ≤ 230 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SpeedGadget.kt` | New | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherSensorPermissionManager.kt` | New | ≤ 190 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | 798 → ≤ 815 |

> Backup / split thresholds: `LauncherHomeActivity.kt` (798 LOC) and the three `strings.xml` files are past the 500-line backup threshold - steps 03.1 and 03.7 carry the backup sub-step. Nothing here approaches 1500 LOC.
>
> **Flavor placement.** Every Kotlin and layout file above lives under `app_v2/src/launcherEnabled/`, mounted for `standard` and `noLegal` only; the strings and the drawable are shared resources under `src/main/res/`, as every existing `launcher_gadget_*` key is. No file carries a `BuildConfig.IS_*` guard.
>
> **Landscape parity.** `app_v2/src/launcherEnabled/res/layout-land/` holds only `activity_launcher_home.xml` - no gadget layout has a landscape variant, and the desktop grid measures a gadget's cell identically in both orientations. Landscape variant absent - not needed.

---

## Steps

### Step 03.1 - Add the trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> First copy the three `strings.xml` files to `temp/S1179/` with a timestamped name - each is past the 500-line backup threshold. Then add every key with one `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` call per key, never by hand-editing the three files:
>
> - `launcher_gadget_compass`, `launcher_gadget_speed` - picker labels.
> - `launcher_gadget_compass_heading` - heading as words, taking the degrees and the cardinal point.
> - `launcher_gadget_compass_cardinal_n` through `_nw` - the eight cardinal names.
> - `launcher_gadget_compass_altitude` - altitude in metres, one argument.
> - `launcher_gadget_compass_calibrate` - the hint shown while accuracy is unreliable.
> - `launcher_gadget_speed_value` - speed in kilometres per hour, one argument.
> - `launcher_gadget_sensor_no_fix` - waiting for a position.
> - `launcher_gadget_sensor_no_permission` - the location permission was refused and the tile is idle until it is granted.
> - `launcher_gadget_compass_description`, `launcher_gadget_speed_description` - the accessibility announcement for each tile, naming both the value and what it means.
>
> Check every new string against `docs/COMMUNICATION_POLICY.md` §2 for the message formula of its type and §6 for the tone checklist before running the tool. After the last key, run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"`.

**Why:**

Strategic §3.2 makes EN/RU/UK coverage mandatory for this feature and requires heading and altitude to be announced as words rather than carried by the arrow alone, which is what the cardinal and description keys exist for.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `Glob` - `temp/S1179/` contains the three timestamped `strings.xml` backups.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. 18 keys x 3 locales added through `set-android-string.ps1 -Action add`, never by hand. `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` - expected: exit 0 | actual: exit 0, all 35 keys present in en/ru/uk. Backups: `temp/S1179/strings{,-ru,-uk}.xml.20260806_231957.bak`. Two decisions worth naming: the cardinals are full words rather than the usual `N`/`NE` abbreviations, because §3.2 requires the heading to be readable *as words* by TalkBack and a screen reader says "en" for `N`; and `launcher_gadget_compass_calibrate` was shortened after the tone checklist's 360 dp line ("Wave the phone in a figure eight", not "Move the phone in a figure eight to calibrate the compass") - the hint sits on a 2x1 tile. `launcher_gadget_sensor_no_permission` names a next step ("Allow it in app permissions") rather than only stating the refusal. Dev log recorded.

---

### Step 03.2 - Add the compass drawable

**Files:** `app_v2/src/main/res/drawable/ic_compass.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `ic_compass.xml` as a 24dp vector drawable whose `android:tint="?attr/colorOnSurface"` carries the colour - no literal hex anywhere in the file. Reuse the existing `ic_speed.xml` for the speed gadget and add no second drawable for it.

**Why:**

Strategic §11.1 requires each gadget to be recognisable in the picker, where the drawable is the only visual the option carries, and CLAUDE.md Rule 19 forbids a hardcoded colour in a resource.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_compass.xml` exists.
- `Grep` - `="#` returns zero hits in that file.
- `Glob` - no new drawable other than `ic_compass.xml` was added in this phase.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Files: res/drawable/ic_compass.xml (+15 LOC, new). `="#"` - expected: 0 | actual: 0. Colour comes from `android:tint="?attr/colorOnSurface"`; the two `fillColor` values are `@color/white`, the same resource `ic_speed.xml` uses, so the tint has something to replace and no literal hex enters the file. `ic_speed.xml` is reused for the speed tile, so this is the phase's only new drawable. Dev log recorded.

---

### Step 03.3 - Teach the registry and the picker to hide an unavailable gadget

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadget.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `fun isAvailable(): Boolean = true` to the `LauncherGadget` interface, documenting that it answers the hardware question only and never the permission question. Add `fun available(): List<LauncherGadget> = gadgets.filter { it.isAvailable() }` to `LauncherGadgetRegistry`, leaving `all()` and `byKey()` untouched - a cell already on the desktop must still resolve by key even if the sensor later stops answering, or an existing desktop would lose a tile on upgrade. Change `gadgetOptions()` in the picker to call `available()` instead of `all()`. This mirrors how the same dialog already hides an unsupported contact category through `ContactActionAvailabilityProvider`.

**Why:**

Strategic ADR-3 makes "no sensor - no gadget in the list" a rule of one layer rather than five copies, and §11.2 states the picker, not the tile, is where an unavailable gadget disappears.

**Verification:**

- `Grep` - `fun isAvailable()` present in `LauncherGadget.kt` and matches exactly once.
- `Grep` - `fun available()` present in `LauncherGadgetRegistry.kt`.
- `Grep` - `gadgetRegistry.all()` returns zero hits in `LauncherCellContentPickerDialogFragment.kt`.
- `Grep` - `fun all()` and `fun byKey(` each still present in `LauncherGadgetRegistry.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Files: gadget/LauncherGadget.kt (107 -> 114), gadget/LauncherGadgetRegistry.kt (71 -> 78), picker/LauncherCellContentPickerDialogFragment.kt (1 line). `gadgetRegistry.all()` in the picker - expected: 0 | actual: 0. `isAvailable()` is a defaulted interface member, so the five existing gadgets need no edit and keep answering true. Dev log recorded.

---

### Step 03.4 - Add the two tile layouts

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_compass.xml`, `app_v2/src/launcherEnabled/res/layout/gadget_launcher_speed.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Write both as `<merge tools:parentTag="android.widget.FrameLayout">`, following `gadget_launcher_weather.xml`. The compass layout carries an `ImageView` for the rotating needle, a `TextView` for the heading words, a `TextView` for the altitude and a smaller `TextView` for the calibration hint with `android:visibility="gone"`. The speed layout carries an autosizing `TextView` for the value and a smaller `TextView` for the message line, also `gone` by default. Take every colour from `?attr/colorOnSurface` and `?attr/colorOnSurfaceVariant` - no literal hex. Set the needle's `contentDescription` to `@null`; the announcement comes from the root view. Set no fixed height on either root - the desktop grid measures the cell.

**Why:**

Strategic §3.2 requires heading and altitude to be readable as text and not carried by the needle alone, which is why the heading is a `TextView` beside the arrow rather than a rotation alone.

**Verification:**

- `Glob` - both layout files exist.
- `Grep` - `="#` returns zero hits in both files.
- `Grep` - `layout_height="[0-9]` returns zero hits in both files.
- `Grep` - `tools:parentTag` present in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Files: res/layout/gadget_launcher_compass.xml (+71 LOC, new), res/layout/gadget_launcher_speed.xml (+44 LOC, new). `="#"` - expected: 0 | actual: 0. `layout_height="<digit>"` - expected: 0 | actual: 0. Landscape parity re-checked against the tree rather than taken from this file's header: `src/launcherEnabled/res/layout-land/` holds `activity_launcher_home.xml` alone, so no gadget layout has a landscape counterpart to mirror. The speed message line sits below the value instead of replacing it, which is what lets the tile keep the last reading while a message shows. Dev log recorded.

---

### Step 03.5 - Add the compass gadget

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/CompassGadget.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add `CompassGadget` implementing `LauncherGadget` with key `LauncherGadgetRegistry.KEY_COMPASS`, `defaultSpanW = 2`, `defaultSpanH = 1`, `minSpanW = 1`, `minSpanH = 1`, `requiresResourceParam = false`, `labelRes = R.string.launcher_gadget_compass`, `iconRes = R.drawable.ic_compass`, and `isAvailable()` returning `SensorAvailabilityRepository.isAvailable(SensorCapability.COMPASS)`. Add a private `CompassGadgetView` extending `LauncherGadgetView`, inflating the layout through view binding and overriding `onActive()` to collect `ObserveCompassUseCase` combined with `ObserveMotionUseCase` for the altitude - the use cases from step 01.6, never the `data/sensors` sources directly - rendering heading words, the needle rotation and the altitude on each emission and setting the root `contentDescription` from `launcher_gadget_compass_description` every time. Show the calibration hint while accuracy is `UNRELIABLE` and hide it otherwise. When the location permission is absent, show `launcher_gadget_sensor_no_permission` in place of the altitude and keep the heading live - the compass needs no permission and must not go blank because the altitude cannot be read. Start no job outside `onActive` and hold no reference to anything outliving the view.

**Why:**

Strategic §3.1.1 is the owner's requirement that heading and altitude share one tile, and §11.5 requires a refused permission to degrade the tile to an honest empty state rather than remove or break it.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class CompassGadget` matches exactly once and its declaration line contains `LauncherGadget`.
- `Grep` - `class CompassGadgetView` matches exactly once and extends `LauncherGadgetView`.
- `Grep` - `override suspend fun CoroutineScope.onActive` present.
- `Grep` - `override fun isAvailable()` present.
- `Grep` - `contentDescription` present.
- `Grep` - `launcher_gadget_sensor_no_permission` present.
- `Grep` - `GlobalScope`, `lifecycleScope`, `registerListener` each return zero hits in the file - the source owns the subscription, the view only collects.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 9/9 PASS. Files: gadget/CompassGadget.kt (+123 LOC, new). `GlobalScope` / `lifecycleScope` / `registerListener` / `Log.d(` - expected: 0 each | actual: 0 each. Two predicates were read by intent, not by their literal one-line assumption: `class CompassGadget` is matched with a word boundary so it does not also count `CompassGadgetView`, and the `LauncherGadget` supertype sits on the closing `) : LauncherGadget {` line because the constructor is multi-line - which is exactly the shape of `WeatherGadget`, the file this step is told to copy. Heading and altitude are collected as two flows combined, so a refused location grant costs the altitude line alone and the heading stays live; the location flow is not started at all without the grant, because `MotionReadingSource` documents that its caller must check one.

---

### Step 03.6 - Add the speed gadget

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SpeedGadget.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Add `SpeedGadget` and its private `SpeedGadgetView` in the same shape as step 03.5, with key `LauncherGadgetRegistry.KEY_SPEED`, `iconRes = R.drawable.ic_speed`, and `isAvailable()` returning `SensorAvailabilityRepository.isAvailable(SensorCapability.LOCATION)`. Collect `ObserveMotionUseCase` and render `speedKmh` through `launcher_gadget_speed_value`. Show `launcher_gadget_sensor_no_permission` when the permission is absent, `launcher_gadget_sensor_no_fix` when the permission is present but no fix has arrived or the fix carries no speed, and the value otherwise. Keep the last drawn value on screen while a message is shown, as `WeatherGadgetView.showMessage` does - a momentary loss of fix must not blank a readable tile.

**Why:**

Strategic §3.1.2 fixes kilometres per hour as the unit the owner asked for, and §11.5 requires the refused-permission state to be honest rather than empty or crashed.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class SpeedGadget` matches exactly once and its declaration line contains `LauncherGadget`.
- `Grep` - `override fun isAvailable()` present.
- `Grep` - `launcher_gadget_speed_value`, `launcher_gadget_sensor_no_fix`, `launcher_gadget_sensor_no_permission` each present.
- `Grep` - `contentDescription` present.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 6/6 PASS. Files: gadget/SpeedGadget.kt (+96 LOC, new). `Log.d(` - expected: 0 | actual: 0. Same word-boundary and multi-line-supertype reading as step 03.5. The message line sits beside the value rather than replacing it, mirroring `WeatherGadgetView.showMessage`, so a lost fix leaves the last speed readable; the accessibility announcement falls back to the message only when no value has been drawn yet.

---

### Step 03.7 - Register both gadgets and request the permission at add time

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherSensorPermissionManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 03.6

**Prompt for developer:**

> First copy `LauncherHomeActivity.kt` to `temp/S1179/` with a timestamped name - it is past the 500-line backup threshold. Add `SensorGadgetModule` in `ui/launcher/gadget/di/`, `@InstallIn(SingletonComponent::class)`, declaring a `@SensorGadgets` qualifier and a `@Provides @Singleton` function returning the sensor gadgets as one `List<LauncherGadget>` - mirroring `HomeWidgetGadgetModule`, which exists precisely to keep the registry under detekt's constructor threshold. Add the qualified parameter to `LauncherGadgetRegistry`'s constructor as `@SensorGadgets sensors: List<@JvmSuppressWildcards LauncherGadget>` - the `@JvmSuppressWildcards` is required or Dagger fails with `MissingBinding` at `hiltJavaCompile`, after `a.ps1 fk` has already reported the Kotlin clean - and append it to the `gadgets` list. Add `KEY_COMPASS = "compass"` and `KEY_SPEED = "speed"` to the companion with the existing "a key is a storage format, never renamed" comment convention.
>
> Add `LauncherSensorPermissionManager` in `ui/launcher/helpers/`, holding the whole permission decision: which gadget key needs which permission, whether it is already granted, and the `ActivityResultLauncher` that asks. Give `LauncherHomeActivity` only the registration of that launcher and a single delegating call on the picker result - the placement of the cell proceeds whether the answer is grant or refusal.

**Why:**

Strategic §3.3 requires the permission to be asked at the moment the user adds the gadget that needs it rather than at startup, and CLAUDE.md Rule 3 keeps that decision out of the activity, which is already 798 lines.

**Verification:**

- `Glob` - `SensorGadgetModule.kt` and `LauncherSensorPermissionManager.kt` exist.
- `Grep` - `@SensorGadgets` present in both the module and `LauncherGadgetRegistry.kt`.
- `Grep` - `@JvmSuppressWildcards` present on the new registry parameter.
- `Grep` - `KEY_COMPASS` and `KEY_SPEED` each present in `LauncherGadgetRegistry.kt`.
- `Grep` - `ACCESS_FINE_LOCATION` present in `LauncherSensorPermissionManager.kt` and returns zero hits in `LauncherHomeActivity.kt`.
- `Grep` - `registerForActivityResult` present in `LauncherHomeActivity.kt`.
- `Grep` - the diff to `LauncherHomeActivity.kt` adds no more than 20 lines.
- `Glob` - `temp/S1179/` contains the timestamped `LauncherHomeActivity.kt` backup.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 8/8 PASS. Files: gadget/di/SensorGadgetModule.kt (+36 LOC, new), helpers/LauncherSensorPermissionManager.kt (+53 LOC, new), gadget/LauncherGadgetRegistry.kt (78 -> 89), LauncherHomeActivity.kt (+7 lines: one import, the manager field, one delegating call - budget was 20). `ACCESS_FINE_LOCATION` in the activity - expected: 0 | actual: 0. Backup: `temp/S1179/LauncherHomeActivity.kt.20260806_232955.bak`. `.\a.ps1 fk` PASS, which by itself proves nothing about the new qualified binding - the `@SensorGadgets` / `@JvmSuppressWildcards` pair this step warns about only fails at `hiltJavaCompile`, so the phase build is the real gate. The compass key maps to the location permission too: its heading needs none, but the altitude line it shares with the speed tile does.
- 2026-08-06 (boundary audit) - `requestIfNeeded(key)` replaced by `placeAfterAsking(key, place)`; `placeGadget` split into the ask and `placeGadgetNow`. The cell is now written after the user answers, which is what makes a fresh grant visible at all - see the P1 entry in the audit section. `LauncherHomeActivity` is at +11 lines against the step's budget of 20.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `standard debug` and `noLegal debug` both green after the boundary-audit fix (`.\a.ps1 dq` exit 0, `.\a.ps1 nd` exit 0). The packaging builds are also what prove the new `@SensorGadgets` binding resolves; `fk` cannot, and this step's own prompt says so.
- [x] `Grep` for `TODO(phase-03)` returns zero hits - expected: 0 | actual: 0.
- [x] `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` - expected: exit 0 | actual: exit 0, 35 keys in en/ru/uk.
- [x] Dev log entries written - one per step through `post-change.ps1`, per the repo's one-entry-per-logical-change convention.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` ran inside every Kotlin step's closure.
- [x] Phase-boundary audit run - one P1 found and fixed in this phase, no unresolved P0/P1. See the audit section above.

---

## Phase-boundary audit (2026-08-06)

Run per CLAUDE.md §13 against this phase's `Files Touched`. Layers 1, 2 and 3 apply; Layer 4 does not - the phase touches no Room surface.

- **P1, found and fixed in this phase: a granted permission did not reach the tile.** `LauncherGadgetView` restarts `onActive()` through `repeatOnLifecycle(STARTED)`, and the system permission dialog is a dialog-themed activity - it takes the launcher to PAUSED, never to STOPPED. So the collector was never restarted after the user answered, and both tiles kept showing `launcher_gadget_sensor_no_permission` right after a grant. That is the primary flow of the whole phase: add tile -> allow -> tile stays wrong.
  - The obvious remedy does not work, and is written down so it is not tried again: calling the activity's `renderDesktop()` from the permission callback is a no-op, because `LauncherCellViewBinder.bind` returns early on `lastBound == Triple(cells, columns, editMode) && lastRows == rows`. That guard is deliberate (S1173/S1288) - it exists so returning to Home does not destroy every gadget's just-started work - so forcing a rebind through it would trade this defect for that one.
  - What does work is ordering: the cell is now placed **after** the user answers, not before. A new cell changes `cells`, which is exactly what the guard tests, so the bind happens and the view is created reading the grant the user just gave. Placement still proceeds on a refusal - the answer decides what the tile shows, never whether it appears.
  - Narrow edge case, accepted and recorded rather than hidden: if the Activity is destroyed while the permission dialog is up, the pending placement is lost and the cell is not added. `LauncherContactPickManager` carries the same exposure for the same reason (one modal flow at a time), and the user's recovery is to add the tile again.
- **Layer 2, lifecycle - otherwise PASS.** Neither gadget view starts a job outside `onActive`; `GlobalScope`, `lifecycleScope` and `registerListener` are absent from both files by predicate. The subscription itself lives in the `data/sensors` source, so the view only collects.
- **Layer 3, ownership - PASS.** `LauncherSensorPermissionManager` holds the Activity, and is itself an Activity field, so it cannot outlive it. The gadget views hold use cases (singletons), never the host.
- **Layer 1, architecture - PASS.** No business logic in the Activity: it gained one field, one import and one delegating call. `isAvailable()` answers the hardware question only; the permission question stays in the manager and in the views' own check.
- **UI gate (S1338):** the placement decision is recorded in this phase's Prerequisites, quoting strategic §0, §3.1.1, §3.1.2, §11.1, §11.2, §11.5 and §3.2. Screenshot deferred (no device attached this session) - the whole ticket is device-gated anyway and ends in `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

- `isAvailable()` on `LauncherGadget` is the one place a gadget disappears from the picker. Phases 04 and 05 override it and add no second filter.
- `LauncherSensorPermissionManager` owns the key-to-permission mapping. Phase 05 adds one entry to it for `ACTIVITY_RECOGNITION` rather than a second manager.
- `SensorGadgetModule` is where every later sensor gadget registers. The registry constructor does not grow again.
- `launcher_gadget_sensor_no_permission` is the one refusal string every sensor tile shares - Phases 04 and 05 reuse it and add no synonym.
- `launcher_gadget_sensor_no_fix` belongs to the live-value tiles and is used by Phase 03 alone. Phase 04 authors its own `launcher_gadget_chart_empty` deliberately, because a chart's empty state has a different trigger - fewer than two accumulated points, not an absent live fix - and one string cannot say both truthfully. Corrected 2026-08-06: this note previously claimed both strings were shared and that Phase 04 added no key.
- The `KEY_*` constants land in `LauncherGadgetRegistry`'s companion object, which today carries no "a key is a storage format, never renamed" comment - the convention is attested in `HomeWidgetGadgetModule`, `HomeWidgetEntry` and `SensorSeriesPoint`, so step 03.7 originates it in this file rather than copying one already there.

---

## Rollback Plan

Revert phase commit(s). No schema change, no data migration; a desktop carrying a compass or speed cell resolves it to `null` through `byKey` afterwards, which the host already renders as an unknown cell.
