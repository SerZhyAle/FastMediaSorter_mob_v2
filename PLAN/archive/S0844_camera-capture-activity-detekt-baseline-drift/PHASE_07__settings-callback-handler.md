# Phase 07 - Settings Callback Handler

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 08
**Steps done:** 2 / 2
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Extract the `CameraSettingsDialogFragment.Callbacks` implementation and the dialog-show trigger into a dedicated handler class that the Activity constructs and assigns to `CameraSettingsDialogFragment.callbacks` in place of itself (same substitution pattern as Phase 06, per strategic ADR-1 - no Kotlin `by` delegation).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 750 (net shrink) |

---

## Steps

### Step 07.1 - Create CameraSettingsCallbackHandler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class CameraSettingsCallbackHandler(private val sessionManager: CameraCaptureSessionManager, private val flowManager: CameraCaptureFlowManager, private val onGridToggled: () -> Unit) : CameraSettingsDialogFragment.Callbacks` in package `com.sza.fastmediasorter.ui.cameracapture.helpers`. Move the exact bodies of `onCameraSettingsPreviewChanged`, `onCameraSettingsApplied` (replace its `renderGridOverlay()` call with `onGridToggled()`), `onCameraSettingsCancelled` from `CameraCaptureActivity` as overrides here. Also move `showCameraSettingsDialog()`'s body as a new public method `fun show(fragmentManager: androidx.fragment.app.FragmentManager)`: it must still guard against a duplicate dialog (`fragmentManager.findFragmentByTag(CameraSettingsDialogFragment.TAG) != null`), construct `CameraSettingsDialogFragment().apply { callbacks = this@CameraSettingsCallbackHandler; capabilities = flowManager.currentCapabilities; initialSettings = ... }` (same `CameraSettingsState` construction as today, reading from `sessionManager`/`flowManager`), and call `.show(fragmentManager, CameraSettingsDialogFragment.TAG)`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt` exists.
- `Grep` - `class CameraSettingsCallbackHandler(` matches exactly once.
- `Grep` - `: CameraSettingsDialogFragment.Callbacks` matches exactly once in the new file.
- `Grep` - `fun show(fragmentManager: androidx.fragment.app.FragmentManager)` (or equivalent imported `FragmentManager` type) matches exactly once.

**Status:** `[x]` done

---

### Step 07.2 - Remove the Callbacks implementation from the Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Remove `onCameraSettingsPreviewChanged`, `onCameraSettingsApplied`, `onCameraSettingsCancelled`, and `showCameraSettingsDialog` from `CameraCaptureActivity`. Remove `CameraSettingsDialogFragment.Callbacks` from the class's supertype list entirely. Add `private lateinit var settingsCallbackHandler: CameraSettingsCallbackHandler`, constructed in `setupViews()` with `CameraSettingsCallbackHandler(sessionManager, flowManager, onGridToggled = ::renderGridOverlay)` (keep `renderGridOverlay()` itself in the Activity - it is also called independently from `setupViews()`'s initial render, so it is not part of this extraction). Change the `binding.btnCameraSettings.setOnClickListener { showCameraSettingsDialog() }` call site to `{ settingsCallbackHandler.show(supportFragmentManager) }`.

**Verification:**

- `Grep` - `override fun onCameraSettingsPreviewChanged` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `CameraSettingsDialogFragment.Callbacks` returns zero hits in `CameraCaptureActivity.kt`'s class declaration.
- `Grep` - `settingsCallbackHandler.show(supportFragmentManager)` matches exactly once.
- `Grep` - `private fun renderGridOverlay` matches exactly once still (kept in Activity).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).
- [ ] `Grep` - the class declaration's supertype list in `CameraCaptureActivity.kt` now reads exactly `BaseActivity<ActivityCameraCaptureBinding>(), CameraCaptureFlowManager.Host, SelfManagedScreenOrientation` (3 entries; `Wrapping`'s multi-line-colon rule from Phase 01 still applies if it still wraps).

---

## Handoff Notes to Next Phase

All 6 extractions (Phases 02-07) are complete. `CameraCaptureActivity` now implements only `CameraCaptureFlowManager.Host` and `SelfManagedScreenOrientation` (down from 4 supertypes). Phase 08 runs the final detekt verification and cleans the now-permanently-stale baseline entries.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - plain constructor-substitution, not a language-level delegation mechanism; if the settings dialog regresses, revert and re-verify via `/build` before moving on.
