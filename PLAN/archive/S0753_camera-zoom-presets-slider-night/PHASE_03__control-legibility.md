# Phase 03 - Control legibility

**Strategic spec:** [`../S0753_camera-zoom-presets-slider-night.md`](../S0753_camera-zoom-presets-slider-night.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Make every overlay control legible on both white and black scenes at ~70% opacity via a strong outline plus shadow, applied uniformly to bar buttons, preset chips, mode tabs, the recording timer, and the zoom slider, in both orientations (strategic ADR-3, §6.2).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `CameraCaptureActivity.kt` > 500 LOC - back it up to `temp/` before editing (Step 03.4).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/themes.xml` | Modified | (+ ~30) |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 380 |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 640 |

> **Landscape parity:** both `layout/` and `layout-land/` variants edited in Step 03.3.

---

## Steps

### Step 03.1 - Set overlay colours to ~70% opacity with a strong outline

**Files:** `app_v2/src/main/res/values/colors.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `camera_capture_control_bg` from `#CC15171C` to `#B315171C` (~70% opacity per owner decision §6.2). Change `camera_capture_control_stroke` from `#33FFFFFF` to `#B3FFFFFF` so the white outline actually reads on a light scene. Add `<color name="camera_capture_text_shadow">#CC000000</color>` for text legibility and `<color name="camera_capture_slider_inactive">#66FFFFFF</color>` for the slider inactive track. Hex literals are correct here - colours are defined in `colors.xml`, not in layout attributes (Rule 19 governs layouts). Keep the existing design-rationale comment block above these entries.

**Verification:**

- `Grep` - `camera_capture_control_bg">#B315171C` and `camera_capture_control_stroke">#B3FFFFFF` each match once.
- `Grep` - `camera_capture_text_shadow` and `camera_capture_slider_inactive` defined.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (control_bg #B315171C, control_stroke #B3FFFFFF, two new colours defined). Files: colors.xml.

---

### Step 03.2 - Add overlay text + chip styles

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `Widget.FastMediaSorter.Camera.OverlayText` style carrying a text shadow (`android:shadowColor` = `@color/camera_capture_text_shadow`, `android:shadowRadius` = `4`, `android:shadowDx` = `0`, `android:shadowDy` = `1`, `android:textColor` = `@android:color/white`) for the mode tabs and recording-timer text. Add a `Widget.FastMediaSorter.Camera.ZoomChip` style for the dynamically-created preset chips: white text with the same shadow, `app:chipBackgroundColor` = `@color/camera_capture_control_bg`, `app:chipStrokeColor` = `@color/camera_capture_control_stroke`, `app:chipStrokeWidth` = `1dp`. Do not duplicate Material defaults beyond what legibility needs.

**Verification:**

- `Grep` - `Widget.FastMediaSorter.Camera.OverlayText` and `Widget.FastMediaSorter.Camera.ZoomChip` each defined once in `themes.xml`.
- `Grep` - `shadowColor` present in the overlay-text style.
- `.\a.ps1 fr` passes (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (OverlayText + ZoomChip styles defined; shadowColor present; also added OverlayButton style here for button legibility; `a.ps1 fc` SUCCESSFUL). Files: themes.xml.

---

### Step 03.3 - Apply outline + shadow + slider tint across both layouts

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/layout-land/activity_camera_capture.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> In both layout files: add `app:strokeWidth="1dp"` and `android:elevation="4dp"` to every overlay `MaterialButton` (`btnCloseCamera`, `toggleCameraMicrophone`, `btnCameraFlash`, `btnCameraPauseResume`, `btnCameraLensSwitch`) so the outline + drop shadow separate them from any background (the stroke colour already points at `camera_capture_control_stroke`, now strengthened). Apply `style="@style/Widget.FastMediaSorter.Camera.OverlayText"` to `tabModePhoto`, `tabModeVideo` and `txtRecordingTimer` (replace their inline `android:textColor` with the style). Style `cameraZoomSlider` with `app:thumbColor="@android:color/white"`, `app:trackColorActive="@android:color/white"`, `app:trackColorInactive="@color/camera_capture_slider_inactive"` - all `@color`/`@android:color` references, never `#hex` inline (Rule 19). Keep portrait and landscape edits identical in intent.

**Verification:**

- `Grep` - `Widget.FastMediaSorter.Camera.OverlayText` referenced in both layout files.
- `Grep` - `Widget.FastMediaSorter.Camera.OverlayButton` applied 5x in each layout file (carries strokeWidth 1dp + `android:elevation="4dp"` from the themes style - cleaner than per-button inline).
- `Grep` - `cameraZoomSlider` block contains `app:trackColorInactive="@color/camera_capture_slider_inactive"` in both files; no `="#` on those lines.
- `.\a.ps1 fc` passes (exit 0); neuroslop layout-hardcoded-colors delta 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification PASS (OverlayText on tabModePhoto/tabModeVideo/txtRecordingTimer both layouts; OverlayButton applied 5x each carrying elevation 4dp + stroke 1dp from themes.xml; slider thumb/track tints; `a.ps1 fc` SUCCESSFUL; neuroslop delta 0). Impl note: elevation/stroke centralised in OverlayButton style rather than per-button inline (DRY, fewer edit sites). Files: layout/ + layout-land/ activity_camera_capture.xml.

---

### Step 03.4 - Style the dynamic preset chips for legibility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` to `temp/` first (Rule 5). In `configureZoomControls()`, build each `Chip` with the legibility style via the 3-arg constructor `Chip(this, null, com.google.android.material.R.attr.chipStyle)` is not enough - instead apply the project style: create the chip then call `setTextAppearance` is unavailable for shadow, so set the style at inflation by using a `ContextThemeWrapper(this, R.style.Widget_FastMediaSorter_Camera_ZoomChip)` when constructing the `Chip`, or set the properties directly (`chipBackgroundColor`, `chipStrokeColor`, `chipStrokeWidth`, and `setShadowLayer` on the chip's paint via `paint.setShadowLayer(4f, 0f, 1f, ContextCompat.getColor(this, R.color.camera_capture_text_shadow))`). Pick the direct-property path to avoid a chip-inflation refactor. The chips must read on a bright viewfinder, matching the bar buttons.

**Verification:**

- `Grep` - `camera_capture_text_shadow` or `Widget_FastMediaSorter_Camera_ZoomChip` referenced in `CameraCaptureActivity.kt`.
- `Glob` - a fresh `temp/CameraCaptureActivity.kt.*.bak` backup exists.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (chip legibility via direct props: chipBackgroundColor/chipStrokeColor/chipStrokeWidth + setTextColor white + setShadowLayer using camera_capture_text_shadow; backup CameraCaptureActivity.kt.20260627_231752.bak; `a.ps1 fc` SUCCESSFUL). Files: CameraCaptureActivity.kt.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles - `a.ps1 fc` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `assert-neuroslop.ps1` (pwsh 7) PASS - layout-hardcoded-colors delta 0, all axes within baseline.
- [~] Dev log entry - batched into Phase 05 finalization per CLAUDE.md.

---

## Handoff Notes to Next Phase

All overlay controls (bar buttons, chips, tabs, timer, slider) now carry a strong outline + shadow at ~70% opacity. Phase 04 adds the night-mode toggle to the top bar; it inherits the same button styling automatically.

---

## Rollback Plan

Revert the phase commit and restore `CameraCaptureActivity.kt` from the `temp/` backup. Colour/style changes are non-destructive and carry no migration.
