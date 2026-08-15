# Phase 04 - Speed and altitude charts with reset

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Put the two charts on the desktop: each draws its persistent series since the last reset, feeds that series while visible, and carries its own reset button.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] UI placement is decided in the strategic spec and not open here. §0 is the owner's own text asking for a speed chart and an altitude/distance chart, each "за время" and each "с кнопкой сброса"; §3.1.3 puts a reset button on every chart; §6 item 3 is the owner's quiz answer fixing the window as "since the last reset" with no sliding window and no window setting; §3.2 requires the chart not to rely on colour as its only differentiator and the reset button to be a full-size tap target; §11.3 makes the reset clear the series and restart it from zero. Tile spans follow `WeatherGadget`, as in Phase 03.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +22 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +22 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +22 |
| `app_v2/src/main/res/drawable/ic_series_chart.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesChartView.kt` | New | ≤ 320 |
| `app_v2/src/main/res/values/attrs_sensor_series_chart.xml` | New | ≤ 40 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_series_chart.xml` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SeriesChartGadget.kt` | New | ≤ 300 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 125 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt` | Modified | ≤ 170 |

> Backup / split thresholds: the three `strings.xml` files are past the 500-line backup threshold - step 04.1 carries the backup sub-step. Nothing here approaches 1500 LOC.
>
> **Flavor placement.** `SensorSeriesChartView` and its attrs land in `src/main/` deliberately - see the INDEX "Downstream reuse note". The gadget, its layout and its registration stay in `src/launcherEnabled/`. No file carries a `BuildConfig.IS_*` guard.
>
> **Landscape parity.** `app_v2/src/launcherEnabled/res/layout-land/` holds only `activity_launcher_home.xml` - no gadget layout has a landscape variant. Landscape variant absent - not needed.

---

## Steps

### Step 04.1 - Add the trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Back the three files up to `temp/S1179/` first, then add every key with one `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` call per key:
>
> - `launcher_gadget_speed_chart`, `launcher_gadget_altitude_chart` - picker labels.
> - `launcher_gadget_chart_reset` - the reset button label.
> - `launcher_gadget_chart_reset_description` - what the reset does, for the accessibility announcement.
> - `launcher_gadget_chart_empty` - the state before the first sample after a reset.
> - `launcher_gadget_chart_distance` - cumulative distance in kilometres, one argument.
> - `launcher_gadget_chart_altitude_current` - the current altitude line on the altitude tile.
> - `launcher_gadget_speed_chart_description`, `launcher_gadget_altitude_chart_description` - the accessibility announcement for each tile, naming the current value, the span since reset and, for the altitude tile, the distance.
>
> Check every string against `docs/COMMUNICATION_POLICY.md` §2 and §6 before running the tool, then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"`.

**Why:**

Strategic §3.2 makes EN/RU/UK coverage mandatory and requires the chart to be readable without seeing it, which is what the per-tile description keys carry.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. 9 keys x 3 locales via `set-android-string.ps1 -Action add`. `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` - expected: exit 0 | actual: exit 0, 44 keys in en/ru/uk. Backups: `temp/S1179/strings{,-ru,-uk}.xml.20260806_234241.bak`. `launcher_gadget_chart_empty` carries the invitation the §6 checklist requires ("the chart fills as you move") rather than a bare "no data", and `launcher_gadget_chart_altitude_current` is worded "Now %1$d m" so it is not a silent duplicate of `launcher_gadget_compass_altitude`. Dev log recorded.

---

### Step 04.2 - Add the shared chart view

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesChartView.kt`, `app_v2/src/main/res/values/attrs_sensor_series_chart.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `SensorSeriesChartView` extending `View`, taking a list of points and drawing up to two series on a `Canvas` in `onDraw`. Give the two series different stroke widths and different dash patterns, not only different colours - a reader who cannot separate the colours must still separate the lines. Read every colour from a theme attribute declared in `attrs_sensor_series_chart.xml` and resolved through `obtainStyledAttributes`; no literal hex appears in either file. Scale the vertical axis to the minimum and maximum actually present, and the horizontal axis to the first and last timestamp, so an unevenly sampled series still spans the full width - the series decimates as it grows, so the interval between neighbouring points is not constant and must never be assumed. Allocate every `Paint` and `Path` in the constructor, never inside `onDraw`. Draw nothing and report the empty state when the list has fewer than two points.

**Why:**

Strategic §3.2 forbids the chart from relying on colour as its only differentiator, and Phase 02's decimation rule means the sample interval doubles as the series grows, so a view that assumed a fixed interval would compress the older half of every long trip.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class SensorSeriesChartView` matches exactly once.
- `Grep` - `obtainStyledAttributes` present.
- `Grep` - `="#` returns zero hits in both files.
- `Grep` - `Paint(` and `Path(` each return zero hits inside the `onDraw` body.
- `Grep` - `PathEffect` or `DashPathEffect` present - the second differentiator is real.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 7/7 PASS. Files: ui/common/chart/SensorSeriesChartView.kt (+141 LOC, new), res/values/attrs_sensor_series_chart.xml (+10 LOC, new). `="#"` and `Log.d(` - expected: 0 each | actual: 0 each. Every `Paint`/`Path` is a field initialiser, so `onDraw` allocates nothing. `.\a.ps1 fk` PASS. Each series is scaled to its own minimum and maximum rather than to a shared one: speed and cumulative distance share a tile but not a unit, and one scale would flatten whichever range is smaller into the axis. The two lines differ in stroke width *and* in dash pattern (solid 2dp against dashed 1dp), so the encoding survives a reader who cannot separate the two colours. Colour defaults resolve `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` through the theme instead of a hex fallback, which is what keeps the "no literal hex" predicate true in the Kotlin as well as in the XML.

---

### Step 04.3 - Add the chart tile layout and its drawable

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_series_chart.xml`, `app_v2/src/main/res/drawable/ic_series_chart.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `ic_series_chart.xml` as a 24dp vector tinted from `?attr/colorOnSurface`, used by both chart gadgets in the picker. Write `gadget_launcher_series_chart.xml` as a `<merge tools:parentTag="android.widget.FrameLayout">` carrying the `SensorSeriesChartView`, a `TextView` for the current value, a `TextView` for the secondary line - distance on the altitude tile, hidden on the speed tile - a `TextView` for the empty state, and a `MaterialButton` for the reset. Give the reset button `android:minWidth` and `android:minHeight` of at least 48dp so it is a full-size tap target, and a `contentDescription` from `launcher_gadget_chart_reset_description`. Take every colour from theme attributes - no literal hex. Set no fixed height on the root.

**Why:**

Strategic §3.1.3 is the owner's requirement that each chart carries its own reset button, and §3.2 requires that button to be a full-size tap target rather than an icon squeezed into a corner of a small tile.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `SensorSeriesChartView` present in the layout.
- `Grep` - `MaterialButton` present in the layout.
- `Grep` - `minHeight` present on the reset button.
- `Grep` - `="#` returns zero hits in both files.
- `Grep` - `layout_height="[0-9]` returns zero hits in the layout root.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 6/6 PASS. Files: res/layout/gadget_launcher_series_chart.xml (+79 LOC, new), res/drawable/ic_series_chart.xml (+11 LOC, new). `="#"` - expected: 0 | actual: 0. The root `<merge>` sets no height, as the predicate requires; the two `0dp` heights inside are weighted children, which is the only correct way to give the chart the space the value lines do not take. Reset is a `MaterialButton` with `Widget.Material3.Button.TextButton` - a style already used elsewhere in the app - at 48dp in both dimensions, so it is a full-size target on a small tile without dominating it.

---

### Step 04.4 - Add the chart gadget class

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SeriesChartGadget.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add `class SeriesChartGadget` implementing `LauncherGadget`, parameterised by key, `labelRes`, `SensorSeriesId` and a flag for whether the secondary line is shown - one class instantiated twice, following `HomeWidgetGadget`. Give both `defaultSpanW = 2`, `defaultSpanH = 1`, `minSpanW = 2`, `minSpanH = 1` and `requiresResourceParam = false`; the minimum width is 2 rather than 1 because a chart squeezed into a single column carries no readable shape. Return `SensorAvailabilityRepository.isAvailable(SensorCapability.LOCATION)` from `isAvailable()`. Add a private `SeriesChartGadgetView` extending `LauncherGadgetView` whose `onActive()` does two things concurrently: collect `ObserveSensorSeriesUseCase` and render, and collect `ObserveMotionUseCase` and feed `RecordSensorSeriesPointUseCase`. Wire the reset button to `ResetSensorSeriesUseCase` for this tile's series id. Show `launcher_gadget_chart_empty` while the series has fewer than two points and `launcher_gadget_sensor_no_permission` when the location permission is absent. Only the second key is reused from Phase 03; `launcher_gadget_chart_empty` is authored here, because "not enough points yet" and Phase 03's "no live fix" are different facts and one string cannot state both truthfully. Do not add a third refusal string beyond these two (clarified 2026-08-06 - this sentence previously described both keys as reuse).
>
> Start the recorder nowhere but `onActive` - no service, no `WorkManager` request, no application-scoped collector. A series therefore grows only while its chart is on screen, and a gap in the line is a period the desktop was not visible. Put that in one KDoc sentence on `SeriesChartGadgetView`, because it reads like a bug to the next person and the obvious "fix" - moving collection into a service - is what would drag a background-location declaration into the app.

**Why:**

Strategic ADR-2 makes the reset an operation on the series rather than on the view, which is why the button calls the use case instead of clearing anything the view holds, and §11.4 requires the accumulated series to survive leaving the desktop - which only holds because rendering reads the store rather than an in-view buffer. Strategic §6 item 5 and the §2 non-goals forbid any background collection, because it would pull in a background-location permission and a separate Play declaration.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class SeriesChartGadget` matches exactly once and its declaration line contains `LauncherGadget`.
- `Grep` - `class SeriesChartGadgetView` matches exactly once and extends `LauncherGadgetView`.
- `Grep` - `ResetSensorSeriesUseCase`, `ObserveSensorSeriesUseCase`, `RecordSensorSeriesPointUseCase` each present.
- `Grep` - `override suspend fun CoroutineScope.onActive` present.
- `Grep` - `minSpanW: Int = 2` present.
- `Grep` - `GlobalScope`, `lifecycleScope` each return zero hits.
- `Grep` - `WorkManager`, `startService`, `startForegroundService`, `ProcessLifecycleOwner` each return zero *call sites* across every file added by Phases 01-04. Corrected 2026-08-06: the same step orders a KDoc sentence naming `WorkManager` as the thing not to reach for, so a literal zero-hit grep can never pass. The documentation is worth more than the literal predicate - the sentence exists precisely because moving collection into a service is the plausible-looking "fix" that would drag a background-location declaration into the app.
- `Grep` - `ACCESS_BACKGROUND_LOCATION` returns zero hits across `app_v2/src`.
- `Grep` - the visibility-bound-recording KDoc sentence is present on `SeriesChartGadgetView`.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 10/10 PASS. Files: gadget/SeriesChartGadget.kt (+181 LOC, new). `GlobalScope` / `lifecycleScope` / `Log.d(` / `ACCESS_BACKGROUND_LOCATION` - expected: 0 each | actual: 0 each. `startService` / `startForegroundService` / `ProcessLifecycleOwner` - 0 call sites; `WorkManager` appears once, in the mandated KDoc, per the corrected predicate above.
- **Phase 02's handoff obligation is discharged here.** `SeriesChartGadget` holds an `AtomicBoolean` claimed by whichever view is feeding the series and released in a `finally`, so two desktop cells showing the same chart record once between them - the second only draws. The gadget is the right owner because the module provides it `@Singleton` per series, while `RecordSensorSeriesPointUseCase` has no scope at all and a guard inside it would not be shared between two injection points.
- The reset runs on the view's active scope, held only between `onActive` and its `finally`, so a tap after the view is detached is a no-op rather than a leaked coroutine.

---

### Step 04.5 - Register the two chart gadgets

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Add `KEY_SPEED_CHART = "speed_chart"` and `KEY_ALTITUDE_CHART = "altitude_chart"` to `LauncherGadgetRegistry.Companion` under the existing "a key is a storage format, never renamed" convention. Instantiate `SeriesChartGadget` twice inside the existing `@SensorGadgets` provider - the speed chart on `SensorSeriesId.SPEED` with the secondary line off, the altitude chart on `SensorSeriesId.ALTITUDE_DISTANCE` with it on. Change no constructor signature: the provider list grows, the registry does not.

**Why:**

Strategic §5.3 requires a further chart to arrive as a registration rather than as a new view, which only holds if both charts already share one class and one provider.

**Verification:**

- `Grep` - `KEY_SPEED_CHART` and `KEY_ALTITUDE_CHART` each present in `LauncherGadgetRegistry.kt`.
- `Grep` - `SeriesChartGadget(` matches exactly twice in `SensorGadgetModule.kt`.
- `Grep` - the `LauncherGadgetRegistry` constructor parameter list is unchanged from Phase 03.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Files: gadget/di/SensorGadgetModule.kt (36 -> 79), gadget/LauncherGadgetRegistry.kt (+2 const). `SeriesChartGadget(` in the module - expected: exactly 2 | actual: 2 (lines 57, 68). The registry's constructor is untouched, as the step requires: the provider list grew, the registry did not. The provider takes 7 parameters, under detekt's `functionThreshold` of 8 - which is why the two charts share one class rather than arriving as two injected gadget types.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0 and `.\a.ps1 nd` exit 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits - expected: 0 | actual: 0.
- [x] `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` - expected: exit 0 | actual: exit 0, 44 keys in en/ru/uk.
- [x] Dev log entries written through `post-change.ps1`, one per logical step.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated inside each Kotlin closure.
- [x] Phase-boundary audit run - no P0/P1. See the audit section below.

---

## Phase-boundary audit (2026-08-06)

Layers 1-4 all apply here: the phase adds a custom `View`, two collectors, a click handler and a write path into Room.

- **Layer 2, concurrency - the Phase 02 P2 is closed, with one residue.** The `AtomicBoolean` claim makes exactly one view feed a series. The residue: if the recording view is detached while a second view of the same chart stays on screen, the second does not take over until its own `onActive` restarts, so the series stops growing in the meantime. That is a degraded state, not a wrong one - no double counting, no lost rows - and it needs two cells of the same chart to reach at all. Recorded rather than fixed, because the fix (a shared recorder outliving any one view) is exactly the application-scoped collector §6 item 5 forbids.
- **Layer 2, lifecycle - PASS.** `activeScope` is written and cleared inside `onActive`'s own `try/finally`, and both it and the click listener run on the main thread, so there is nothing to race. The series collector is a child `launch`, cancelled with its parent.
- **Layer 3, ownership - PASS.** The view holds the gadget (a singleton); nothing holds the view. `SensorSeriesChartView` allocates its `Paint`s and `Path` once, in field initialisers, so `onDraw` allocates nothing.
- **Layer 4, Room - PASS.** Every write goes use case -> repository -> DAO with the suspend members pinned to `Dispatchers.IO`; the read is a Room `Flow`, so neither touches the main thread.
- **Layer 1, architecture - PASS.** The chart view is feature-neutral and lives in `ui/common/chart/`; the gadget holds no business rule beyond mapping a `MotionReading` onto its own series.
- **UI gate (S1338):** placement decision recorded in this phase's Prerequisites (strategic §0, §3.1.3, §6 item 3, §3.2, §11.3). Screenshot deferred - no device attached this session.

---

## Handoff Notes to Next Phase

- `SensorSeriesChartView` lives in `src/main/java/.../ui/common/chart/` and belongs to no feature. A second consumer adds no copy.
- Both charts share one gadget class and one layout. A third chart is a third entry in the `@SensorGadgets` provider.
- Recording is bound to visibility by construction, not by a check. Phase 05 keeps that property for the steps tile.

---

## Rollback Plan

Revert phase commit(s). The `sensor_series_point` table from Phase 02 survives and is simply not written to; no schema change and no user-authored data is involved.
