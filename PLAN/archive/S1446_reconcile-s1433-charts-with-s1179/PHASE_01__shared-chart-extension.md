# Phase 01 - Shared chart gains the two capabilities a full-screen consumer needs

**Strategic spec:** [`../S1446_reconcile-s1433-charts-with-s1179.md`](../S1446_reconcile-s1433-charts-with-s1179.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Give `SensorSeriesChartView` an opt-in labelled value axis and an opt-in numeric summary of the series, both off by default, so a full-screen consumer needs no second chart class.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired before the first source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesChartView.kt` | Modified | ≤ 260 |
| `app_v2/src/main/res/values/attrs_sensor_series_chart.xml` | Modified | ≤ 20 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesSummaryTest.kt` | New | ≤ 90 |

> No layout files change: the view is instantiated by its hosts, so there is no `res/layout` edit and therefore no `res/layout-land` counterpart to match.
>
> **Flavor placement.** The view already lives in `src/main` and is flavor-neutral. `SUPPORT_LAUNCHER` and `SUPPORT_NETWORK_MONITOR` cover the same flavor pair, so both consumers reach it without any flavor guard.

---

## Steps

### Step 01.1 - Add the opt-in labelled value axis

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesChartView.kt`, `app_v2/src/main/res/values/attrs_sensor_series_chart.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `var showValueAxis: Boolean = false` which invalidates on change, and draw the axis only while it is true: a vertical guide plus the minimum and maximum of the primary series as labels, using the same min and max `onDraw` already computes for scaling. Add one styleable attribute for the label colour to `attrs_sensor_series_chart.xml`, defaulted from a theme attribute like the two colour attributes already there, never a hex literal. Nothing about the existing drawing path changes while the flag is false.

**Why:**

Strategic §5.1 names the labelled value axis as one of exactly two capabilities the shared view lacks for full-screen use, and the INDEX invariant requires it off by default because S1179 is in `BlockNeedUserTest` and a default-on change would alter what the owner is about to verify on a real phone.

**Verification:**

- `Grep` - `var showValueAxis: Boolean = false` matches exactly once.
- `Grep` - no hex colour literal (`="#`) in `attrs_sensor_series_chart.xml`.
- `Grep` - every axis-drawing call inside `onDraw` sits under a `showValueAxis` guard.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `SensorSeriesChartView.kt` gained `showValueAxis`, an axis guide paint, a label paint and `drawValueAxis`/`formatLabel`; `attrs_sensor_series_chart.xml` gained `sensorSeriesAxisLabelColor`, defaulted from `colorOnSurfaceVariant` exactly as the two existing colour attributes are - no hex literal (grep exit 1). `onDraw` calls `drawValueAxis` from inside a single `if (showValueAxis)` block. `.\a.ps1 fc` exit 0. Dev log recorded.

---

### Step 01.2 - Expose the series summary as numbers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesChartView.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `SensorSeriesSummary` value type carrying `last`, `min`, `max` and a `trend` of rising, falling or flat for the primary series, and a `fun summary(): SensorSeriesSummary?` returning null while `hasData` is false. Compute the trend by comparing the last point against the previous one. Return numbers only: no string building, no resource lookup, no formatting.

**Why:**

Strategic §5.1 names the summary as the second missing capability, and §3.2 with risk three place the wording at the consumer, which owns the strings and the locale, so the view must not start holding phrasing.

**Verification:**

- `Grep` - `fun summary(): SensorSeriesSummary?` matches exactly once.
- `Grep` - `getString`, `R.string` and `Context.resources` return zero hits in `SensorSeriesChartView.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. Added `SensorSeriesTrend`, `SensorSeriesSummary` and `internal fun summarizeSensorSeries` as top-level declarations in the same file, with `fun summary()` delegating to it - the extraction Step 01.3 asks for, done here so the test never needs a `Context`. All three greps 0. `.\a.ps1 fk` exit 0. Dev log recorded.
- The file's only `resources` reference is the pre-existing `resources.displayMetrics.density` at the top of the class - a density read, not a string lookup, and outside what this predicate targets.

---

### Step 01.3 - Unit-test the summary computation

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesSummaryTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Cover the summary with one assertion per case: an empty series and a single point both yield null; a rising, a falling and a flat tail each yield the matching trend; `min` and `max` come from the whole series rather than from its tail; a series whose secondary values are all null still summarises its primary. Keep the computation reachable without instantiating a `View` - extract it to an internal top-level function in the same file if the test would otherwise need a `Context`.

**Why:**

Strategic §11 criterion 1 makes the summary part of the shipped contract, and the trend is the only derived value here, so an off-by-one in the tail comparison would otherwise reach both consumers unnoticed.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - at least seven assertions in that file.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*SensorSeriesSummaryTest*"` exits 0. This predicate is currently unrunnable: the Gradle test executor dies before running anything, parked as S1463. Do not tick this step on the static predicates alone - leave it `[~]` until S1463 is resolved and the run is green.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `SensorSeriesSummaryTest.kt` created with 7 tests and 12 assertions. The blocker this predicate names is gone: **S1463 is `Verified`** as of 2026-08-07, so the run was made rather than skipped - exit 0, and `app_v2/build/test-results/testStandardDebugUnitTest/TEST-..SensorSeriesSummaryTest.xml` reads `tests="7" skipped="0" failures="0" errors="0"`, read directly because a green exit alone does not prove the suite ran. Dev log recorded.
- 2026-08-08 - `SensorSeriesPoint.primaryValue` is non-nullable, so the `mapNotNull` written in Steps 01.1 and 01.2 was a no-op filter; both are now plain `map`. Only `secondaryValue` is nullable, which is why `drawSeries` still takes a nullable selector.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 and the unit run's own `compileStandardDebugKotlin` + `hiltJavaCompileStandardDebug` green, 2026-08-08.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in both modified `.kt` files.
- [x] Both new capabilities default to off, and no S1179 gadget passes either one - zero hits under `ui/launcher/gadget/`, and the only repo-wide `.summary()` hits belong to an unrelated stream-diagnostics object.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1` (three batched calls).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2580 records, `catalog-sync` PASS inside the closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit (2026-08-08)

- Layer 1 - architecture and budgets. `SensorSeriesChartView.kt` is 258 LOC against a 260 budget, the test 79 against 90, the attrs file 11 against 20 - all inside, but the view has ~2 lines of slack left, so the next consumer-driven addition belongs in a helper rather than in this file.
- Layer 2 - lifecycle and coroutines. Nothing asynchronous was added; both capabilities are synchronous reads of already-held state.
- Layer 3 - listener and memory ownership. No listener, no observer, no retained reference is created. The paints are field-initialised, not allocated per frame, matching the file's existing shape.
- Layer 4 - Room. Not applicable.
- P3, recorded not fixed: `drawValueAxis` allocates a list and two strings per draw. This is the same shape `drawSeries` has used since S1179 and only runs while the axis is opted in, so fixing it here would be an unrequested refactor of a pre-existing pattern - the place to address it is whichever ticket makes the chart redraw continuously.

---

## Handoff Notes to Next Phase

`SensorSeriesChartView` now covers the full-screen case, so S1433's phase 05 has nothing left to author for drawing. Its ring buffer and its four samplers stay its own - that verdict is recorded, not reopened.

---

## Rollback Plan

Revert phase commit(s) - both additions are opt-in members guarded by a false default, so no existing consumer changes behaviour.
