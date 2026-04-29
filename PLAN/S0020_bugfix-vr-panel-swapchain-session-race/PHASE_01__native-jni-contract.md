# Phase 01 — Native JNI Contract

**Strategic spec:** [`../S0020_bugfix-vr-panel-swapchain-session-race.md`](../S0020_bugfix-vr-panel-swapchain-session-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Align `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeCreatePanelSwapchain` with the HUD-JNI contract: succeed iff `g_ctx.session != XR_NULL_HANDLE`, otherwise fail immediately. Remove the dead "stored" defer path that survives nowhere (no auto-pickup exists in `createSessionAndSwapchains`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | n/a (3000+ LOC pre-existing native code; this phase removes lines, adds none) |

---

## Steps

### Step 01.1 — Replace `sessionRunning` check with `session != XR_NULL_HANDLE`

**Files:** `OpenXrNative.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> In `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeCreatePanelSwapchain` (around line 3420), change the gate from `if (!g_ctx.sessionRunning)` to `if (g_ctx.session == XR_NULL_HANDLE)`. This matches the HUD JNI bridge at line 3303, which already uses the loose check and works reliably. Update the log message to reflect the new condition.

**Verification:**

- `Grep` — `if (!g_ctx.sessionRunning)` returns zero hits inside the `nativeCreatePanelSwapchain` function block.
- `Grep` — `if (g_ctx.session == XR_NULL_HANDLE)` matches at least 2 times in `OpenXrNative.cpp` (one for HUD JNI, one for panel JNI).

**Status:** `[x]` done

---

### Step 01.2 — Remove the dead "stored" fallback for panel

**Files:** `OpenXrNative.cpp`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `nativeCreatePanelSwapchain`, remove the assignments to `g_ctx.panelSwapchainWidth` and `g_ctx.panelSwapchainHeight` that happened in the early-return branch. They had no consumer — `createSessionAndSwapchains` does not auto-create the panel from stored dims (unlike HUD). The function on session-not-ready now just logs and returns `JNI_FALSE` without side effects.

**Verification:**

- `Grep` — `nativeCreatePanelSwapchain: %dx%d stored — session not yet up` returns zero hits in `OpenXrNative.cpp`.
- `Grep` — `g_ctx.panelSwapchainWidth = static_cast<uint32_t>(width)` returns zero hits in `OpenXrNative.cpp` outside `createPanelSwapchainImpl`.

**Status:** `[x]` done

---

### Step 01.3 — Add atomic `panel ready` log marker on success

**Files:** `OpenXrNative.cpp`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inside `createPanelSwapchainImpl` after the swapchain is fully created and image list populated, add a single `LOGI("nativeCreatePanelSwapchain: panel ready %ux%u imgCount=%u", g_ctx.panelSwapchainWidth, g_ctx.panelSwapchainHeight, imgCount);` line, distinct from the existing "%ux%u swapchain created" line. This is the on-device-verifiable marker `panel ready` from strategic ADR-3.

**Verification:**

- `Grep` — `nativeCreatePanelSwapchain: panel ready %ux%u` matches exactly once in `OpenXrNative.cpp`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `vr debug` (native code only built in vr flavor).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `OpenXrNative.cpp`.

---

## Handoff Notes to Next Phase

Phase 02 adds Kotlin-side diagnostic markers and removes the redundant `running.get()` gate (the native side now has the authoritative check).

---

## Rollback Plan

Revert phase commit. Native code is the only file touched; the ABI is unchanged (same JNI signature).
