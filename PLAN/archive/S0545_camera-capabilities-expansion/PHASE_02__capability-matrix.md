# Phase 02 - Capability matrix

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Add a runtime capability layer for the active camera so flash, zoom, focus, and lens-switch UI can be driven by facts instead of assumptions.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 420 |

---

## Steps

### Step 2.1 - Add immutable capability models for the active lens

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define immutable models that describe the active camera lens and its runtime capabilities: flash presence, lens facing options, zoom range, zoom presets, tap-to-focus support, and any other capability the UI must read without touching CameraX objects directly. Keep the model Android-view agnostic so both the flow manager and future tests can consume it.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` exists.
- `Grep` - `data class CameraRuntimeCapabilities` matches once.
- `Grep` - `hasFlashUnit`, `supportsTapToFocus`, and `availableLensFacings` are present in `CameraRuntimeCapabilities.kt`.

**Status:** `[ ]` not done

---

### Step 2.2 - Teach the session manager to probe and expose capabilities

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Extend `CameraCaptureSessionManager` so it binds a selected lens, refreshes `CameraRuntimeCapabilities` after every bind or lens switch, and exposes imperative hooks for torch, zoom, and focus metering. Keep probing isolated in `CameraCapabilityProbe` instead of spreading Camera2/CameraInfo reads through the Activity or XML layer.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` exists.
- `Grep` - `fun switchCamera` is present in `CameraCaptureSessionManager.kt`.
- `Grep` - `fun setZoomRatio` and `fun setTorchEnabled` are present in `CameraCaptureSessionManager.kt`.
- `Grep` - `fun startFocusAndMetering` is present in `CameraCaptureSessionManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

### Step 2.3 - Surface capabilities into the capture host state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> Wire the session callbacks into `CameraCaptureFlowManager` so the host can observe the current lens, current zoom ratio, flash availability, and focus support without querying CameraX directly. Keep the state explicit and single-sourced in the helper - later phases should only render or act on this state, never recompute hardware assumptions in the view layer.

**Verification:**

- `Grep` - `CameraRuntimeCapabilities` is referenced from `CameraCaptureFlowManager.kt`.
- `Grep` - `onCapabilitiesChanged` or an equivalent callback name is present in `CameraCaptureSessionManager.kt`.
- `Grep` - `currentCapabilities` or an equivalent state holder is present in `CameraCaptureFlowManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The capture host now knows what the active camera can actually do. Phase 03 should only render that state and hide unsupported controls instead of inventing its own probing logic.

---

## Rollback Plan

Revert phase commit(s) - runtime capability layer only, no strings or layout changes yet.
