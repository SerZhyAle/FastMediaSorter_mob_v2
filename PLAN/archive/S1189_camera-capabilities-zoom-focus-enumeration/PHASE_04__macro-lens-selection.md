# Phase 04 - Macro as a lens choice

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-07-25
**Completed:** 2026-07-25

---

## Objective

Make macro switch to a close-focus lens when the device has one, keep the focus-distance lock as the fallback but only offer it where it is applicable, and stop tap-to-focus from silently cancelling an active macro lock.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `availableLenses` in the session manager carries per-entry minimum focus distance.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 820 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 740 |

> No layout file is touched - the macro button already exists on the capture screen; only its visibility rule changes.

---

## Steps

### Step 04.1 - Identify the close-focus lens in the reachable set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a probe function that returns the `CameraLensEntry` of the same facing with the highest minimum focus distance in diopters, when that value clears the existing macro threshold. Add `macroLensAvailable: Boolean` to `CameraRuntimeCapabilities`, set from that lookup, and keep the existing `supportsMacro` meaning "the active lens can lock close focus". Macro is offered when either flag is true.

**Verification:**

- `Grep` - `macroLensAvailable` present in `CameraRuntimeCapabilities.kt`.
- `Grep` - `const val MACRO_MIN_DIOPTERS` matches exactly once in `CameraCapabilityProbe.kt` (one threshold, reused - not a second literal).
- `Grep` - a function whose name contains `macroLens` present in `CameraCapabilityProbe.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. The predicate originally demanded `MACRO_MIN_DIOPTERS` appear exactly once, which the pre-existing file already violated (declaration plus one use); rewritten to check the declaration is single, which is what "reuse the threshold" actually means. `macroLensFor` picks the closest-focusing lens of the requested facing.

---

### Step 04.2 - Route macro through a lens switch when one exists

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Change `applyMacro(enabled)` so that when a close-focus lens exists it rebinds to that entry and remembers the entry that was active before, and turning macro off rebinds back to the remembered entry. When no close-focus lens exists, keep today's Camera2 focus-distance lock. Enabling macro must not silently leave the user on a different lens after they toggle it off.

**Verification:**

- `Grep` - `fun applyMacro` matches exactly once in `CameraCaptureSessionManager.kt`.
- `Grep` - a private property holding the pre-macro entry (name containing `beforeMacro` or `preMacro`) present in `CameraCaptureSessionManager.kt`.
- `Grep` - `LENS_FOCUS_DISTANCE` still present in `CameraCaptureSessionManager.kt` (fallback path retained).

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. Extracted as a `rebindForMacro` helper first; detekt rejected it on the class's 40-function ceiling, so both branches now resolve a target index and share one rebind at the end of `applyMacro`. `NO_LENS_CHANGE` names the "apply on the active lens instead" case so the sentinel is not a bare -1.

---

### Step 04.3 - Stop tap-to-focus from cancelling the macro lock

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Make `startFocusAndMetering` a no-op while the focus-distance macro lock is active, so an accidental tap does not return the lens to autofocus without any visible cue. The lens-switch macro path is unaffected - tap-to-focus stays available there.

**Verification:**

- `Grep` - `macroEnabled` present inside the `startFocusAndMetering` function body in `CameraCaptureSessionManager.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 2/2 PASS (`a.ps1 fk` BUILD SUCCESSFUL). Guarded on `lensBeforeMacro == null` so the block applies only to the focus-lock path; on a dedicated macro lens tap-to-focus keeps working, since there is no fixed distance to undo.

---

### Step 04.4 - Hide the macro button where macro is unreachable

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> The macro control is a button on the capture screen, not a row in the settings dialog: its visibility is decided in `CameraCaptureActivity` and its toggle is guarded in `CameraCaptureFlowManager`. Change both guards from `supportsMacro` alone to `supportsMacro || macroLensAvailable`, and keep the existing "photo mode only" condition on the activity side, so a device with a dedicated macro lens gains the button and a device that can neither switch nor lock loses it rather than showing a control that does nothing (strategic §2 goal 3).

**Verification:**

- `Grep` - `macroLensAvailable` present in `CameraCaptureActivity.kt`.
- `Grep` - `macroLensAvailable` present in `CameraCaptureFlowManager.kt`.
- `Grep` - `capabilities.supportsMacro &&` returns zero hits in `CameraCaptureActivity.kt`.
- `.\a.ps1 fk` exits 0 (no resource file changed in this phase).

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS. Three guards moved in lockstep: button visibility in the activity, the toggle guard in the flow manager, and the post-rebind state restore that also read `supportsMacro` alone - leaving that third one behind would have silently dropped macro on every rebind of a dedicated macro lens.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Macro can now move the active lens, so any later code that assumes the active entry only changes on an explicit switch is wrong. Phase 06 renders the lens label from the active entry and therefore reflects a macro-driven switch automatically.

---

## Rollback Plan

Revert the phase commit(s) - macro returns to the focus-distance lock on every device, which is the pre-S1189 behaviour.
