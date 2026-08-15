# Phase 05 - Input Exit

**Strategic spec:** [`../S0249_vr-test-immersive-image-stage1a.md`](../S0249_vr-test-immersive-image-stage1a.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 02, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Close the diagnostic XR session on any controller, trigger, mouse, keyboard, or runtime shutdown event and return to Settings.

---

## Prerequisites

- [ ] Phase 02 is Done.
- [ ] Phase 04 is Done.
- [ ] Failure UX blocker in `INDEX.md` is closed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/input/DiagnosticXrInputExitHandler.kt` | New | <= 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt` | Modified | <= 280 |
| `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` | Modified | <= 650 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockExtension.kt` | Modified | <= 220 |

---

## Steps

### Step 05.1 - Add Android input exit handler

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/input/DiagnosticXrInputExitHandler.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add a small handler that treats any Android `KeyEvent` or `MotionEvent` after the first-present grace period as an exit request. Keep it reusable for the diagnostic runtime and do not add Activity logic.

**Verification:**

- `Glob` - `DiagnosticXrInputExitHandler.kt` exists.
- `Grep` - `KeyEvent` appears in `DiagnosticXrInputExitHandler.kt`.
- `Grep` - `MotionEvent` appears in `DiagnosticXrInputExitHandler.kt`.
- `Grep` - grace-period constant appears in `DiagnosticXrInputExitHandler.kt`.

**Status:** `[x]` done (2026-05-19)

---

### Step 05.2 - Wire native OpenXR input exit

**Files:** `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add native OpenXR input polling for the minimal action set available on Quest and Android XR. Any action event should call the same session-exit path as Android key/motion input.

**Verification:**

- `Grep` - `xrCreateActionSet` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `xrSyncActions` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `xrGetActionStateBoolean` or documented equivalent appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - exit request function is called from native input polling.

**Status:** `[x]` done (2026-05-19)

---

### Step 05.3 - Return to Settings on exit

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockExtension.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Propagate session exit back to the Settings fragment lifecycle and leave the user on the same Settings tab. The runtime must release OpenXR objects before returning control.

**Verification:**

- `Grep` - `requestExit` appears in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `onDestroyView` or lifecycle cleanup appears in `VrSettingsBlockExtension.kt` if callbacks are registered.
- `Grep` - `xrDestroySession` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `xrDestroyInstance` appears in `diagnostic_xr_runtime.cpp`.

**Status:** `[x]` done (2026-05-19)

---

### Step 05.4 - Add debug probe tags for device gate

**Files:** changed flow entry Kotlin files from Phases 04 and 05
**Depends on:** Step 05.3

**Prompt for developer:**

> If this phase transitions S0249 to `BlockNeedUserTest`, insert one `Timber.d("S0249: <entry-point description>")` tag at each changed flow entry: Settings button launch, runtime session start, and input exit. Do not add more than one tag per flow entry.

**Verification:**

- `Grep` - `Timber.d("S0249:` appears in the changed flow-entry Kotlin files before status changes to `BlockNeedUserTest`.
- `Grep` - no `Timber.d("S0249:` line exists outside files changed by S0249.
- `Grep` - `Log.d(` returns zero hits in changed Kotlin files.

**Status:** `[x]` done (2026-05-19)

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x]` done.
- [ ] Project compiles - run `/build` for VR debug.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog scan/render run after Kotlin changes.

---

## Handoff Notes to Next Phase

The diagnostic immersive session can be launched and closed by any supported input path.

---

## Rollback Plan

Revert Phase 05 commit(s); remove any S0249 debug tags if the ticket does not remain `BlockNeedUserTest`.
