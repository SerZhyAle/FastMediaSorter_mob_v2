# Phase 02 - Rotation container manager

**Strategic spec:** [`../S0924_camera-settings-dialog-rotation.md`](../S0924_camera-settings-dialog-rotation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3

> **DEVICE-GATED (strategic §6).** This phase is the HIGH-risk core: pivot, swapped-`MeasureSpec` sizing against dynamic rows, and `window.setLayout` across DPIs are visual-iteration-only. Execute with a device/emulator attached; do not author the rotation math blind. There is no codebase precedent for rotating a whole interactive panel (only per-icon `View.rotation` in `CameraOverlayRotationManager`).

---

## Objective

Introduce `CameraSettingsDialogRotationManager` that visually rotates the settings dialog content with physical device rotation while `CameraCaptureActivity` stays portrait-locked. The manager owns a rotate-and-swap-measure container: it applies `View.rotation` to the dialog content root and reshapes the floating dialog window via `dialog.window?.setLayout(w, h)`, keyed off the `Surface.ROTATION_*` bucket from Phase 01. The fragment registers with the Activity's single `CameraOrientationManager` (no second `OrientationEventListener`), with symmetric register/deregister on its lifecycle edges.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`CameraOrientationManager` publishes the bucket as observable state).
- [ ] Device/emulator attached for visual iteration (strategic §6).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsDialogRotationManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt` | Modified | ≤ 220 |

> All rotation math lives in the manager, not the Fragment (Rule 3/6). The Fragment only wraps its content root, registers, and deregisters.
> Reuse `@dimen/dialog_landscape_max_height` (320dp, `dimens.xml:725`) programmatically as the post-rotation vertical budget (`resources.getDimensionPixelSize`). Note: that dimen is shared by 14+ landscape dialogs, so it is not orphaned by Phase 03's layout deletion.

---

## Steps

### Step 02.1 - Create CameraSettingsDialogRotationManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsDialogRotationManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraSettingsDialogRotationManager` in `ui/cameracapture/helpers/`. It wraps the dialog content root in a rotate-and-swap-measure container and, given a `Surface.ROTATION_*` bucket, applies the correct `View.rotation` (0/90/180/270) with a pivot at the container centre, then swaps width/height `MeasureSpec` so the rotated child measures against the perpendicular axis. Call `dialog.window?.setLayout(w, h)` to reshape the floating window to the rotated content bounds, capped by `dialog_landscape_max_height` on the post-rotation vertical axis and inset from system bars / display cutout (strategic §3.2, risk table row 4). Expose `attach(dialog, contentRoot)` and `applyRotation(bucket: Int)`; keep it idempotent (re-applying the same bucket is a no-op). No business logic; no logging noise (Timber only if truly needed). **Device iteration:** tune pivot, the swapped-`MeasureSpec` sizing against dynamic row visibility, and the `setLayout` values visually across portrait/landscape and at least two DPIs.

**Verification:**

- `Glob` - `CameraSettingsDialogRotationManager.kt` exists in `helpers/`.
- `Grep` - `class CameraSettingsDialogRotationManager` matches exactly once.
- `Grep` - `fun applyRotation` and `setLayout(` both present.
- `Grep -n "Log\.d\("` - zero hits.
- Device: dialog content reads upright and fully visible in both portrait and landscape holds (strategic criteria 1-2).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 02.2 - Wire the registration hook through show()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `CameraSettingsCallbackHandler.show()` (`:19-38`), pass a hook so the constructed `CameraSettingsDialogFragment` can subscribe to the Activity's single `CameraOrientationManager` bucket state (Phase 01). Do not build a second orientation listener; hand the fragment a reference/lambda to the existing manager's state. Keep the handler thin - it only forwards the subscription seam.

**Verification:**

- `Grep` - `show(` in `CameraSettingsCallbackHandler.kt` now passes the orientation-state hook to the fragment.
- `Grep -n "OrientationEventListener"` - zero new hits in `CameraSettingsCallbackHandler.kt` (no second listener).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 02.3 - Fragment wraps, registers, deregisters (listener symmetry)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `CameraSettingsDialogFragment.onCreateDialog()`, after inflating `DialogCameraSettingsBinding`, wrap `binding.root` in the `CameraSettingsDialogRotationManager` container before `setView(...)`. Subscribe to the bucket state and call `applyRotation` in `onStart()`; unsubscribe in `onDestroyView()` so registration/deregistration sit on a symmetric lifecycle edge (mirror `CameraCaptureActivity.kt:266,272`). The Activity is portrait-locked, so physical rotation never recreates the fragment - the `draft` field survives a tilt; no `onSaveInstanceState` work. As the final code edit before this phase's build, insert one `Timber.d("S0924: <entry - dialog rotation registered for bucket=$bucket>")` at the registration entry point (device-test probe; strategic status will move to `BlockNeedUserTest`).

**Verification:**

- `Grep` - `CameraSettingsDialogRotationManager(` constructed/used in `CameraSettingsDialogFragment.kt`.
- `Grep` - subscription in `onStart` and matching unsubscription in `onDestroyView` (symmetry).
- `Grep` - exactly one `Timber.d("S0924:` in `CameraSettingsDialogFragment.kt`.
- Project compiles - `.\a.ps1 fc` (standard code + resources) BUILD SUCCESSFUL.
- Device: taps, slider drags, and the 4 dropdowns operate correctly in the rotated container (strategic criterion 3).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `a.ps1 fc` BUILD SUCCESSFUL (includes the inserted `S0924:` probe tag).
- [ ] Listener register/deregister symmetric (`onStart`/`onDestroyView`); no second `OrientationEventListener`.
- [ ] Exactly one `Timber.d("S0924:` probe present (BlockNeedUserTest invariant).
- [ ] Device: strategic criteria 1-3 hold on at least two DPIs.

---

## Handoff Notes to Next Phase

The dialog now rotates with the device while the camera screen stays portrait. Phase 03 removes the dead `layout-land` file, records the known popup/tooltip seams as an accepted limitation, and closes out device verification of all strategic criteria.

---

## Rollback Plan

Delete `CameraSettingsDialogRotationManager.kt`; revert the `show()` hook and the fragment's wrap/register/deregister edits. Phase 01's published bucket state becomes an unused seam (harmless) until re-consumed. No data migration.
