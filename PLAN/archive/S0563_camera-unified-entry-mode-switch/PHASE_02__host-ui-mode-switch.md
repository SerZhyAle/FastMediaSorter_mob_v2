# Phase 02 - Host UI: in-screen PHOTO|VIDEO switch

**Strategic spec:** [`../S0563_camera-unified-entry-mode-switch.md`](../S0563_camera-unified-entry-mode-switch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3

---

## Objective

Surface a Samsung-familiar segmented `PHOTO|VIDEO` control above the shutter, visible only when the
caller allowed mode switching. Switching rebinds the session and refreshes the controls. Both
orientations stay in sync (CLAUDE.md Rule 11).

---

## Files Touched

| File | New / Modified |
|------|:--------------:|
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified |
| `app_v2/src/main/res/values/strings.xml` | Modified |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified |

---

## Steps

### Step 2.1 - Add the segmented switch to both orientations

**Files:** `res/layout/activity_camera_capture.xml`, `res/layout-land/activity_camera_capture.xml`

**Prompt for developer:**

> Add a `com.google.android.material.button.MaterialButtonToggleGroup` with id `cameraModeSwitchGroup`
> (`app:singleSelection="true"`, `app:selectionRequired="true"`, `android:visibility="gone"`)
> containing two `MaterialButton`s `btnModePhoto` and `btnModeVideo` (text `@string/camera_mode_photo`
> / `@string/camera_mode_video`, each `focusable`/`clickable`, with `contentDescription`). Use theme
> attributes / existing `@color/camera_capture_*` resources only - no hardcoded hex (CLAUDE.md
> Rule 19).
> - Portrait: constrain `cameraModeSwitchGroup` bottom to top of `cameraActionBar`, centered
>   horizontally, with a bottom margin; change `cameraZoomBar`'s `layout_constraintBottom_toTopOf`
>   from `cameraActionBar` to `cameraModeSwitchGroup` so the stack reads zoom / switch / shutter
>   (a GONE switch collapses to just above the shutter, preserving current zoom placement).
> - Landscape: constrain `cameraModeSwitchGroup` end to start of `cameraActionBar`, centered
>   vertically, with an end margin; change `cameraZoomBar`'s `layout_constraintEnd_toStartOf` from
>   `cameraActionBar` to `cameraModeSwitchGroup` (mirror of the portrait chain).

**Verification:**

- `Grep` - `cameraModeSwitchGroup`, `btnModePhoto`, `btnModeVideo` present in BOTH layout files.
- `Grep` - `cameraZoomBar` constrains to `cameraModeSwitchGroup` in BOTH layout files.
- `pwsh scripts/quality/assert-neuroslop.ps1` - no new hardcoded-hex hits in either layout.

**Status:** `[ ]` not done

---

### Step 2.2 - Wire the switch and runtime mode change in the Activity

**File:** `CameraCaptureActivity.kt`

**Prompt for developer:**

> In `setupViews`, after `setupCaptureMode()`, call a new `setupModeSwitch()`:
> - When `!flowManager.allowModeSwitch`: keep `cameraModeSwitchGroup` GONE and return.
> - Else: make it VISIBLE, check the button matching the initial mode (`btnModePhoto` /
>   `btnModeVideo`), and set an `addOnButtonCheckedListener` that, on a newly-checked button, maps it to
>   a `CameraCaptureMode`, calls `flowManager.switchMode(target)`, and when it returns true:
>   `sessionManager.applyMode(videoMode = target == VIDEO)` then re-run the capture-mode UI
>   (mic toggle visibility + shutter record/photo semantics).
> Refactor `setupCaptureMode()` into an idempotent `applyCaptureModeUi()` callable on every switch:
> it must both show (video) and hide (photo) the microphone toggle and reset/raise the shutter
> recording state for the active mode, and set `sessionManager.videoMode` to match. Disable
> `cameraModeSwitchGroup` while a recording is active (re-enable when it stops) so the pipeline is not
> rebuilt mid-record. Keep all camera/session side effects out of business logic - the Activity only
> wires events into `flowManager` / `sessionManager` (CLAUDE.md Rule 3).

**Verification:**

- `Grep` - `setupModeSwitch` and `applyCaptureModeUi` present in `CameraCaptureActivity.kt`.
- `Grep` - `addOnButtonCheckedListener` present and routes to `flowManager.switchMode`.
- `Grep` - `sessionManager.applyMode(` present.
- `Grep` - `cameraModeSwitchGroup.isEnabled` toggled around recording start/stop.
- `Grep` - `android.util.Log` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 2.3 - Add localized switch strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`

**Prompt for developer:**

> Add `camera_mode_photo` and `camera_mode_video` in EN/RU/UK via
> `scripts/utils/set-android-string.ps1 -Action add` (keeps the three locales in lockstep). EN
> "Photo"/"Video", RU "Фото"/"Видео", UK "Фото"/"Відео". Short segmented-control labels; check
> `docs/COMMUNICATION_POLICY.md` tone before adding.

**Verification:**

- `pwsh scripts/check_strings_localized.ps1 -KeyPrefix "camera_mode_"` exits 0.
- `Grep` - `camera_mode_photo` and `camera_mode_video` present in all three `strings.xml`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` is `[x] done`.
- [ ] `.\a.ps1 fc` compiles (code + resources).
- [ ] Switch hidden for every fixed-mode entry point (allowModeSwitch defaults false).

---

## Rollback Plan

Revert phase commit(s) - layout + activity + strings only, no data change.
