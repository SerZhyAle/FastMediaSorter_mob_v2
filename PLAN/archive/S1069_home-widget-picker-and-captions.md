# S1069 - Home-widget picker completeness + icon captions

**Status:** Archived

## 0. Problem

Two issues around home-screen widget discoverability:

- **A.** Two implemented widgets were absent from the in-app "Add widget to home screen" picker
  (`HomeWidgetCatalog.allEntries` listed 12 of the 15 providers): `CameraLaunchWidgetProvider`
  (S0568) and `CaptureOcrPanelWidgetProvider`. Both have a working provider, a merged-manifest
  `<receiver>`, an `*_info.xml`, and ready label/description strings - they were simply never
  registered. (`ResourceLaunchWidgetProvider` is correctly excluded - it pins per-resource from the
  resource editor.)
- **B.** Every single-tap launch widget renders as a **bare icon with no caption**. The launcher does
  not draw a label under a widget (unlike an app shortcut), so a user who does not remember what each
  glyph means cannot tell the widgets apart. A `1x1` (48dp) cell has no room for icon + text.

## 1. Goal

- All pinnable widgets (except the per-resource shortcut) appear in the in-app picker.
- Every launch widget shows a readable one-line caption under its icon on the home screen.

## 2. Approach

### Part A - register missing widgets (DONE)

- Added `CaptureOcrPanelWidgetProvider` (grouped next to Camera OCR/Translate) and
  `CameraLaunchWidgetProvider` (grouped next to the camera widgets) to `HomeWidgetCatalog.allEntries`.
- No compile-time flavor flag: `CaptureOcrPanel` is `tools:node="remove"`-d in lite/photos, so the
  existing `installedProviders` gate hides it there automatically.
- `compileStandardDebugKotlin` -> BUILD SUCCESSFUL.

### Part B - captions on launch widgets (TODO)

Owner decision (locked): **vertical 1x2** layout - icon on top, caption below, like an app shortcut -
applied to **all** launch widgets. Content widgets (Favorites, Scheduled Tasks, Audio Now Playing,
Random Photo Frame) already carry text and are untouched.

Per launch widget, uniform transform:
- Layout: add a single-line, centered, `ellipsize=end` caption `TextView` under the existing icon in
  the vertical `LinearLayout`; bind it to the widget's existing `*_label` string.
- `*_info.xml`: `targetCellHeight` 1 -> 2, `minHeight` 48dp -> 110dp (`70*n-30`); width unchanged.

Launch widgets in scope:
- `widget_calculator`, `widget_camera_launch`, `widget_camera_ocr_translate`, `widget_camera_photos`,
  `widget_camera_quick_capture`, `widget_continue_reading`, `widget_game_launch`,
  `widget_quick_audio_recorder`, `widget_random_music`, `widget_resource_launch`.

## 3. Constraints / accepted limitations

- **Capture & OCR panel** already has two action buttons in one cell; it takes a caption per button
  (photo / OCR), not one title. Its `*_info.xml` grows to fit the two labels.
- `resource_launch` caption is the per-instance resource name (its label is already dynamic), not a
  static string.
- Widget size is read by the launcher at pin time: already-placed instances keep their old `1x1`
  footprint; only newly pinned instances get the taller captioned size. Acceptable (OS behaviour).
- No `BuildConfig.IS_*` flavor guards - the merged manifest stays the flavor gate (Rule 14/15).

## 4. Verification

- In-app picker lists Camera and Capture & OCR (standard/legacy); neither appears in lite/photos.
- On-device: pin each launch widget -> a one-line caption is visible under the icon and not truncated
  for the longest label; the tap action is unchanged.
