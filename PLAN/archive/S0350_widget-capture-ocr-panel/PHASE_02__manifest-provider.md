# Phase 02 - Manifest Provider

**Strategic spec:** [`../S0350_widget-capture-ocr-panel.md`](../S0350_widget-capture-ocr-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Add the AppWidgetProvider and register it in the manifest.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CaptureOcrPanelWidgetProvider.kt` | New | <= 120 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | <= 600 |
| `app_v2/src/lite/AndroidManifest.xml` | Modified | <= 120 |
| `app_v2/src/photos/AndroidManifest.xml` | Modified | <= 120 |

---

## Steps

### Step 02.1 - Add provider class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CaptureOcrPanelWidgetProvider.kt`

**Prompt for developer:**

> Create `CaptureOcrPanelWidgetProvider : AppWidgetProvider`. Bind `R.id.widget_capture_camera_photos_action` to `MainActivity.ACTION_CAMERA_PHOTOS` and `R.id.widget_capture_camera_ocr_action` to `MainActivity.ACTION_CAMERA_OCR_TRANSLATE`. Do not add new `BuildConfig.SUPPORT_*` checks in `src/main`.

**Verification:**

- `Glob` - provider file exists.
- `Grep` - `class CaptureOcrPanelWidgetProvider` matches once.
- `Grep` - `ACTION_CAMERA_PHOTOS` and `ACTION_CAMERA_OCR_TRANSLATE` both appear.
- `Grep` - `BuildConfig` returns zero hits in the provider.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Provider exists, binds Camera Photos and Camera OCR actions, and has zero `BuildConfig` / `Log.d` hits.

### Step 02.2 - Register provider

**Files:** `app_v2/src/main/AndroidManifest.xml`

**Prompt for developer:**

> Add a receiver for `.widget.CaptureOcrPanelWidgetProvider` near the other widget receivers. Use label `@string/widget_capture_ocr_panel_label`, icon `@drawable/ic_camera_ocr_translate`, and meta-data `@xml/widget_capture_ocr_panel_info`.

**Verification:**

- `Grep` - `CaptureOcrPanelWidgetProvider` exists in `AndroidManifest.xml`.
- `Grep` - `widget_capture_ocr_panel_info` exists in `AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Main manifest receiver and provider metadata present; lite/photos overlays remove the panel because OCR translation is unavailable in those flavors.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/widget/CaptureOcrPanelWidgetProvider.kt` returns zero hits.

---

## Rollback Plan

Remove the provider class and manifest receiver.
