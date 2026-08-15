# Phase 01 - Lifecycle Re-entry

**Strategic spec:** [`../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md`](../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Make same-process immersive re-entry resilient when stale native state survived the previous exit.

---

## Prerequisites

- [x] Strategic §6 lifecycle question mapped to implementation.
- [x] Working branch is `DEBUG-v008`.
- [x] Large-file backup exists for touched files above 500 LOC.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt` | Modified | ≤ 260 |
| `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` | Modified | ≤ 180 |
| `app_v2/src/vr/cpp/xr_session.h` | Modified | ≤ 140 |

---

## Steps

### Step 01.1 - Add native initialized-state probe

**Files:** `NativeDiagnosticXrRuntime.kt`, `diagnostic_xr_runtime.cpp`, `xr_session.h`
**Depends on:** start of phase

**Prompt for developer:**

> Expose a native initialized-state probe distinct from running-state. Use it to detect stale native state from a previous session without treating a legitimately running session as recoverable.

**Verification:**

- `Grep` - `nativeIsInitialized` appears in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `NativeDiagnosticXrRuntime_nativeIsInitialized` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `xr_session_is_initialized` appears in `xr_session.h`.

**Status:** `[x]` done

### Step 01.2 - Force-clean stale non-running state before init

**Files:** `NativeDiagnosticXrRuntime.kt`, `diagnostic_xr_runtime.cpp`
**Depends on:** Step 01.1

**Prompt for developer:**

> Before creating a fresh native session, force shutdown only when native state is initialized but not running. Preserve `AlreadyRunning` for concurrent active sessions.

**Verification:**

- `Grep` - `stale native state detected; forcing shutdown before init` appears in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `nativeInitSession: stale initialized state detected; forcing shutdown` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `NativeResult::AlreadyRunning` appears in the initialized-state branch in `diagnostic_xr_runtime.cpp`.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Kotlin catalog sync runs after Kotlin edit.
- [x] noLegal debug build covers JNI symbol changes.

## Handoff Notes to Next Phase

Phase 02 can assume repeat launch no longer trips over stale non-running native state.

## Rollback Plan

Revert phase commit(s). No persisted user data changes.
