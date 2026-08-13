# Phase 05 - Panel Return Dispatcher

**Strategic spec:** [`../S0989_vr-diagxr-activity-decompose.md`](../S0989_vr-diagxr-activity-decompose.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Move the entire exit/return playbook (ACTIVITY_RESULT vs LEGACY_PANEL_RETURN branch, Home + PendingIntent handoff, return-intent construction, idempotency guard) into `VrPanelReturnDispatcher`.

---

## Prerequisites

- [ ] Phase 04 ✅ Done (`playbackCtrl.player` available for the player snapshot).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrPanelReturnDispatcher.kt` | New | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1500 |

> Flavor placement: vr-only helper under `src/vr/java/...`.

---

## Steps

### Step 05.1 - Create VrPanelReturnDispatcher

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrPanelReturnDispatcher.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `VrPanelReturnDispatcher` in `...ui.xr.helpers` with constructor deps: `activity: ComponentActivity`, `payloadHolder: VrLaunchPayloadHolder`, `returnTargetProvider: () -> VrPanelReturnTarget`, `launchInputProvider: () -> VrLaunchInput?` (nullable so the onCreate preflight-failure path before `launchInput` assignment falls back to `LEGACY_PANEL_RETURN`, matching today's `runCatching { launchInput.deliveryMode }`), and `playerProvider: () -> ExoPlayer?`. Move verbatim: `deliverReturnAndFinish`, `deliverViaActivityResult`, `returnToSettingsTaskOrFinish`, `launchPanelHostInHome`, `launchPanelHostFallback`, `buildPlayerReturnTarget`, `buildReturnIntent`, `scheduleHostFinish`, the `panelReturnDispatched` AtomicBoolean, the `exitResult` field, and the `EXTRA_LAUNCH_IN_HOME_PENDING_INTENT` / `MEDIA_SETTINGS_TAB_INDEX` constants. Replace `this`/Activity references: `startActivity` -> `activity.startActivity`, `setResult`/`finish` -> `activity.setResult`/`activity.finish`, `applicationContext` -> `activity.applicationContext`, `window.decorView` -> `activity.window.decorView`, `isFinishing`/`isDestroyed` -> `activity.isFinishing`/`activity.isDestroyed`, `exoPlayer` (in `buildPlayerReturnTarget`) -> `playerProvider()`, `returnTarget` -> `returnTargetProvider()`. Public API: `fun deliverReturnAndFinish(result: VrLaunchResult)`.

**Verification:**

- `Glob` - `VrPanelReturnDispatcher.kt` exists.
- `Grep` - `class VrPanelReturnDispatcher` matches exactly once.
- `Grep` - `fun deliverReturnAndFinish(` present in the new file.
- `Grep` - `buildReturnIntent` / `launchPanelHostInHome` return zero hits in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

---

### Step 05.2 - Rewire Activity exit paths

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Instantiate `private val returnDispatcher by lazy { VrPanelReturnDispatcher(this, payloadHolder, { returnTarget }, { if (::launchInput.isInitialized) launchInput else null }, { playbackCtrl.player }) }`. Replace every `deliverReturnAndFinish(x)` call site (onCreate preflight, runtime-unavailable, prepareLaunchMedia guards, onBackPressed fallback, render-thread exit/start-failed callbacks) with `returnDispatcher.deliverReturnAndFinish(x)`. Remove the moved methods, fields, and constants from the Activity. Ensure `returnDispatcher` is reachable in `onCreate` before the first preflight-failure `deliverReturnAndFinish` (use `by lazy` so it initialises on first use regardless of the permission-gated `proceedWithInitialization`).

**Verification:**

- `Grep` - `private fun deliverReturnAndFinish` returns zero hits in the Activity.
- `Grep` - `returnDispatcher.deliverReturnAndFinish(` present at each former call site (>= 6).
- `Grep` - `panelReturnDispatched` returns zero hits in the Activity.
- `/build` - `standard debug` + `vr debug` compile.
- `Grep` - `DiagnosticXrActivity.kt` line count < 1500 (`(Get-Content ...).Count`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - `/build` `standard debug` + `vr debug`.
- [ ] `DiagnosticXrActivity.kt` < 1500 LOC (primary strategic criterion).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] Phase-boundary audit - no unresolved P0/P1; verify preflight-failure exit path still delivers (dispatcher initialised before first use).

---

## Handoff Notes to Next Phase

Decomposition complete; Phase 06 regenerates catalog + dev log and records the capability-neutral change.

---

## Rollback Plan

Revert phase commit(s) - return playbook logic unchanged; no data migration.
