# Phase 02 - Indicator placement seam

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Move the clock and the five device indicators into two reusable layouts and retype the tray renderer to
those layouts instead of the taskbar binding, so one renderer can serve a second placement. Behaviour
preserving - the taskbar tray must look and act exactly as it does today.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/launcher_status_clock.xml` | New | ≤ 25 |
| `app_v2/src/launcherEnabled/res/layout/launcher_status_indicators.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml` | Modified | ≤ 190 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | Modified | ≤ 400 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 985 |

> `launcher_taskbar.xml` is included by both orientations of `activity_launcher_home.xml` and has no
> `-land` counterpart by design (research 01 §7) - no landscape file to mirror in this phase.
>
> `LauncherHomeActivity.kt` is 974 LOC - step 02.1 backs it up before editing (Rule 5).

---

## Steps

### Step 02.1 - Back up `LauncherHomeActivity.kt`

**Files:** `temp/S1431/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `LauncherHomeActivity.kt` into `temp/S1431/` with a timestamp in the name before editing it.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup under `temp/` before editing a file over 500 LOC, and
this one is 974.

**Verification:**

- `Glob` - `temp/S1431/LauncherHomeActivity*.kt` matches at least one file.

**Status:** `[x] done`

---

### Step 02.2 - Extract the clock into its own layout

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_status_clock.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `launcher_status_clock.xml` holding the `TextClock` with id `trayClock` exactly as it stands in
> `launcher_taskbar.xml` lines 117-124, including its colour, text size and `tools:text`. Do not set
> `format12Hour` or `format24Hour` in the layout - the renderer chooses the format per placement in
> phase 04. Keep the existing comment explaining that `TextClock` registers and drops its own receivers.

**Why:**

Strategic §5.1 requires the clock to be positioned independently of the indicators, because the owner
placed it at the opposite edge of the strip from them (§4.3); one layout holding both could not be split
across two zones.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/launcher_status_clock.xml` exists.
- `Grep` - `android:id="@+id/trayClock"` matches exactly once in that file.
- `Grep` - `format12Hour` returns zero hits in that file.

**Status:** `[x] done`

---

### Step 02.3 - Extract the five device indicators into their own layout

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_status_indicators.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `launcher_status_indicators.xml` as a horizontal `LinearLayout` holding `trayBluetooth`,
> `traySim1`, `traySim2`, `trayNetwork` and `trayBatteryLevel` in that order, copied verbatim from
> `launcher_taskbar.xml` lines 129-174 with their sizes, margins, `visibility="gone"` defaults and
> `tools:` attributes. Carry over the S1415 comment that records the owner-fixed order. Use no hardcoded
> hex colour - keep the `?attr/` and `@drawable/` references as they are.

**Why:**

Strategic ADR-1 makes the set and order of indicators a property of the registry rather than of a
screen, and defining the row once is what stops the two placements from drifting apart - strategic §11
criterion 7 requires them to stay identical.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/launcher_status_indicators.xml` exists.
- `Grep` - each of `trayBluetooth`, `traySim1`, `traySim2`, `trayNetwork`, `trayBatteryLevel` matches
  exactly once in that file.
- `Grep` - `="#` returns zero hits in that file (Rule 19: no hardcoded hex colour in a layout).

**Status:** `[x] done`

---

### Step 02.4 - Include both layouts from the taskbar

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Replace the six inline views inside `trayContainer` (lines 115-174) with two `<include>` tags -
> `launcher_status_clock` then `launcher_status_indicators` - giving each an id so view binding reaches
> it. Keep `trayContainer` itself, its padding and its position in the bar unchanged.

**Why:**

Strategic ADR-1 rejects a second copy of the renderer, and a second copy of the views would reintroduce
the same drift by another route; both placements must inflate the one definition.

**Verification:**

- `Grep` - `layout="@layout/launcher_status_clock"` matches exactly once in `launcher_taskbar.xml`.
- `Grep` - `layout="@layout/launcher_status_indicators"` matches exactly once in `launcher_taskbar.xml`.
- `Grep` - `@+id/trayBluetooth` returns zero hits in `launcher_taskbar.xml` (moved out in step 02.3).
- `Grep` - `@+id/trayContainer` still matches in `launcher_taskbar.xml`.

**Status:** `[x] done`

---

### Step 02.5 - Retype the tray renderer to the extracted layouts

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Change `LauncherTrayManager`'s constructor to take the clock binding and the indicators binding instead
> of `LauncherTaskbarBinding`, and replace every `binding.tray*` reference in `apply()`,
> `applyBluetooth()`, `applySim()` and `renderBattery()` with a reference through the new bindings.
> Update the construction site in `LauncherHomeActivity` to pass the two included bindings from the
> taskbar. Change no rendering rule, no subscription and no permission flow.

**Why:**

Research 01 §1 records that the renderer reaches its views by name through `LauncherTaskbarBinding`,
which is the single thing preventing the same set from being drawn anywhere else; strategic ADR-1 makes
removing that coupling the alternative to a duplicated renderer.

**Verification:**

- `Grep` - `LauncherTaskbarBinding` returns zero hits in `LauncherTrayManager.kt`.
- `Grep` - `class LauncherTrayManager` matches exactly once in that file.
- `Grep -n "Log\.d\("` - zero hits in both touched Kotlin files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [~] The taskbar tray renders exactly as before this phase: same six items, same order, same behaviour.
  Structurally proven (same views, same order, same margins, centring preserved through the nested row),
  but "looks identical" is an on-device claim - carried into this ticket's device test rather than ticked
  on inspection.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The clock and the indicator row are now inflatable anywhere, and `LauncherTrayManager` renders into
whichever pair of bindings it is handed. Phase 04 supplies a second pair from the strip. The indicator
order lives in `launcher_status_indicators.xml` alone - do not re-list it anywhere else.

---

## Rollback Plan

Revert phase commit(s) - no persisted data, no user-facing surface changed; the tray returns to inline
views.

---

## Step Log

- 2026-08-09 - Step 02.1 done. `LauncherHomeActivity.kt` (975 LOC) copied to `temp/S1431/LauncherHomeActivity_20260809_phase02.kt`. expected: >=1 match | actual: 1.
- 2026-08-09 - Step 02.2 done. `launcher_status_clock.xml` created, root `TextClock` id `trayClock`, no format attribute. expected: id 1, format12Hour 0 | actual: 1, 0.
- 2026-08-09 - Step 02.3 done. `launcher_status_indicators.xml` created, five ids in the owner-fixed order. expected: each id 1, `="#` 0 | actual: 1/1/1/1/1, 0.
- 2026-08-09 - Step 02.4 done. Both includes carry the included roots' own ids on purpose: an `<include android:id>` REPLACES the root view's id at inflation, so repeating it leaves the inflated clock still named `trayClock` and changes no id the rest of the bar refers to. expected: 1 clock include, 1 indicators include, 0 `@+id/trayBluetooth`, `trayContainer` kept | actual: 1, 1, 0, 1.
- 2026-08-09 - Step 02.5 done. `LauncherTrayManager` now takes `LauncherStatusClockBinding` + `LauncherStatusIndicatorsBinding`; 23 `binding.tray*` references rewritten, `binding.root.post` -> `indicators.root.post`, zero `binding.` references left. Construction site in `LauncherHomeActivity` passes `binding.launcherTaskbar.trayClock` / `.trayIndicators`. expected: `LauncherTaskbarBinding` 0, class 1, `Log.d(` 0/0, `..ps1 fk` exit 0 | actual: 0, 1, 0/0, exit 0.
- 2026-08-09 - Phase-boundary audit. No listener, coroutine, Room, DI or lifecycle change - the renderer's subscription, permission and blink logic is untouched and only its view handles moved. Vertical centring survives because the new indicator row is itself `center_vertical` inside a `center_vertical` container, and the trailing item still carries no end margin, so the tray's metrics are unchanged. No P0/P1 findings.
- 2026-08-09 - Closure. `post-change.ps1 -Files <5> -ScopeToFile`: every gate PASS except `assert-detekt`, which FAILed on two findings in `LauncherHomeActivity.kt` - `LargeClass` and `CyclomaticComplexMethod` on `registerAddFlowListeners`. Proven pre-existing: the file was restored from the pre-edit backup and the gate re-run - both findings still fired (exit 1), one line higher. Neither is in `config/detekt/baseline-app_v2.xml`. Parked as S1541 (same family as S1198 / S1247 / S1311); dev log and `catalog_sync` run directly, since a failed facade writes no changelog row. Residual carried, not fixed - fixing it means extracting ~375 LOC out of the Activity, which is S1541's scope.
