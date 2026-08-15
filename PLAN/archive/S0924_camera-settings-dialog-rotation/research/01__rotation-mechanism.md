# S0924 Research - Independent camera settings dialog rotation

**Артефакт research-фазы.** Read-only investigation, 2026-07-04. Source: `android-solution-researcher`.

## Rotation infra

- Signal source: `CameraOrientationManager` (`ui/cameracapture/helpers/CameraOrientationManager.kt:11-74`). Wraps one `OrientationEventListener`, buckets raw angle into `Surface.ROTATION_0/90/180/270`, fires only on bucket change.
- Constructor takes two fixed lambdas (`onIconRotationChanged: (Float)->Unit`, `onTargetRotationChanged: (Int)->Unit`) - **no** `addListener`/getter. A third consumer (the dialog) cannot subscribe without an API addition.
- Owned once in `CameraCaptureActivity.initializeHelperManagers()` (`CameraCaptureActivity.kt:205-209`); enabled/disabled symmetrically in `onResumeWithViews()`/`onPause()` (`:265-275`).
- Icon consumer: `CameraOverlayRotationManager` (`helpers/CameraOverlayRotationManager.kt`) counter-rotates 18 individual controls in place via `View.rotation` - never a whole panel, never anything with a nested popup.

## Dialog + layouts

- `CameraSettingsDialogFragment` (`ui/cameracapture/CameraSettingsDialogFragment.kt:21`) - plain `DialogFragment`. `onCreateDialog()` inflates `DialogCameraSettingsBinding` then `MaterialAlertDialogBuilder(...).setView(binding.root).create()` - a centered `AlertDialog`. No `isCancelable`/`setCanceledOnTouchOutside` override.
- Shown via `CameraSettingsCallbackHandler.show()` (`helpers/CameraSettingsCallbackHandler.kt:19-38`), triggered by `btnCameraSettings` (`CameraCaptureActivity.kt:163`).
- Root: `ScrollView` > vertical `LinearLayout`, 192 lines. Canonical rows: 4x `SettingsDropdownRow`, 3x `SettingsToggleRow`, 2 slider blocks, `DialogCancel`/`DialogConfirm` action row.
- **Parity:** `layout/dialog_camera_settings.xml` and `layout-land/dialog_camera_settings.xml` are BYTE-IDENTICAL except one added line on the land ScrollView: `android:maxHeight="@dimen/dialog_landscape_max_height"` (320dp, `dimens.xml:725`). The `-land` file is NOT a redesign - only a height cap - and is dead/unreachable code.
- Camera is the ONLY `screenOrientation="portrait"` activity (manifest:227) and also hard-sets `requestedOrientation = SCREEN_ORIENTATION_PORTRAIT` (`CameraCaptureActivity.kt:152`). So this dead-`-land`-dialog problem is a one-off, not systemic.

## Mechanism decision

**Chosen: (C) scoped down** - real `View.rotation` transform of the dialog content + `dialog.window?.setLayout(w,h)` to reshape the floating window. NOT (A) (`createConfigurationContext` is strictly dominated - the `-land` file is content-identical, so forcing its resolution buys nothing but still needs the rotation transform on top). (B) (adaptive layout, no rotation) kept only as an MVP fallback - it does not "rotate", so it fails the owner's explicit scope.

Sub-question answers:
- **Window vs content:** A `Dialog` is its own `Window` via `WindowManager`; its `LayoutParams` are settable independent of the Activity's `screenOrientation` lock. Resize the dialog window wide/short + apply `View.rotation` to content so text reads upright when the phone is held sideways. No OS orientation change, no `Configuration` trickery.
- **Touch:** `View.rotation`/`pivot` on a `ViewGroup` is honored by the framework's matrix-based touch dispatch - taps/slider drags on rotated content remap correctly, no manual math for primary content.
- **Sizing:** content height is DYNAMIC (rows hidden per `CameraRuntimeCapabilities`), so a fixed dp box will not work - needs a "rotate-and-swap-measure" container that swaps width/height `MeasureSpec` before laying out the rotated child. No precedent in this codebase (`grep view.rotation` finds only the two camera managers). Reuse `dialog_landscape_max_height` (320dp) as the post-rotation vertical budget.
- **State:** Activity is portrait-locked, so physical rotation never triggers recreate/`onConfigurationChanged` - no `onSaveInstanceState` work needed; the fragment's `draft` field stays in memory across a tilt.
- **Dismiss/outside-touch:** unaffected - outside-touch compares against the window decor bounds (post-`setLayout`), independent of content rotation.

## Known visual seams (device-verify, not functional breakage)

- `SettingsDropdownRow` uses `AutoCompleteTextView`; its suggestion popup is a separate `PopupWindow` added directly to `WindowManager` - it does NOT inherit the anchor's rotation, so the 4 dropdown popups render upright over a rotated row.
- `TooltipDialog.show()` builds a new top-level `AlertDialog` - also unrotated.
- These are visual-consistency seams; call them out in acceptance criteria as an accepted known limitation or explicit follow-up.

## Risk: HIGH for a blind (no-device) implementation

Drivers: (a) no precedent for rotating an entire interactive panel; (b) dynamic content height requires a genuine swapped-MeasureSpec custom container; (c) two visual seams only manifest visually; (d) zero unit/instrumentation coverage for the touched classes. Pivot + swapped-measure sizing, window `setLayout` across DPIs, the popup/tooltip seams, and system-bar/cutout interaction at rotated edges are all device-verification-only.

## Implementation outline (for /spec-tech + /spec-dev on a device)

1. `CameraOrientationManager` - add a registration API (listener list or `StateFlow<Int>` of the current `Surface.ROTATION_*` bucket) for late/dynamic subscribers, since the dialog is created on-demand after the manager is built.
2. New helper `CameraSettingsDialogRotationManager` (`helpers/`) - owns the rotate-and-swap-measure container + `View.rotation` + `dialog.window?.setLayout`, keyed off the bucket. No rotation math in the Fragment (Rule 3/6).
3. `CameraSettingsCallbackHandler.show()` - pass a hook so the constructed fragment can register with the Activity's single `CameraOrientationManager` (avoid a second `OrientationEventListener`).
4. `CameraSettingsDialogFragment` - wrap `binding.root` in the rotation container in `onCreateDialog()`; register in `onStart()`, deregister in `onDestroyView()` (listener symmetry, matching `CameraCaptureActivity.kt:266,272`).
5. Delete dead `layout-land/dialog_camera_settings.xml` (Rule 20); reuse `dialog_landscape_max_height` programmatically.
6. Device-verification-only: pivot + swapped-measure sizing vs dynamic rows; window `setLayout` across widths/DPIs; popup/tooltip seams; system bars/cutouts at rotated edges.

## Coverage / flavors

- No unit/androidTest coverage for the touched camera classes (consistent with the UI layer's manual/device verification model).
- No `BuildConfig` flags; feature is in `src/main` for all four flavors (standard/lite/photos/legacy). APIs used are well below minSdk 23.
