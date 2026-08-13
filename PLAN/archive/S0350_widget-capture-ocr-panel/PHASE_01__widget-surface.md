# Phase 01 - Widget Surface

**Strategic spec:** [`../S0350_widget-capture-ocr-panel.md`](../S0350_widget-capture-ocr-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Create the RemoteViews surface, provider info, and trilingual strings for the Capture & OCR panel.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/widget_capture_ocr_panel.xml` | New | <= 120 |
| `app_v2/src/main/res/xml/widget_capture_ocr_panel_info.xml` | New | <= 40 |
| `app_v2/src/main/res/values/strings_widget.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-ru/strings_widget.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-uk/strings_widget.xml` | Modified | <= 80 |

Landscape variant absent: widget RemoteViews use resizable appwidget-provider sizing, not an Activity layout.

---

## Steps

### Step 01.1 - Add panel strings

**Files:** `app_v2/src/main/res/values/strings_widget.xml`, `app_v2/src/main/res/values-ru/strings_widget.xml`, `app_v2/src/main/res/values-uk/strings_widget.xml`

**Prompt for developer:**

> Add label and description strings for the Capture & OCR panel in EN/RU/UK. Reuse existing Camera Photos and Camera OCR action labels.

**Verification:**

- `Grep` - `widget_capture_ocr_panel_label` exists in all three files.
- `Grep` - `widget_capture_ocr_panel_description` exists in all three files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. EN/RU/UK keys present; `check_strings_localized.ps1 -KeyPrefix widget_capture_ocr_panel` exit 0.

### Step 01.2 - Add panel layout

**Files:** `app_v2/src/main/res/layout/widget_capture_ocr_panel.xml`

**Prompt for developer:**

> Create a horizontal RemoteViews layout using the existing widget background, two equal action containers, and existing Camera Photos / Camera OCR drawables. Each action has its own content description and stable id for PendingIntent binding.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout/widget_capture_ocr_panel.xml` exists.
- `Grep` - `widget_capture_camera_photos_action` exists in the layout.
- `Grep` - `widget_capture_camera_ocr_action` exists in the layout.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Layout exists with `widget_capture_camera_photos_action` and `widget_capture_camera_ocr_action`.

### Step 01.3 - Add provider info

**Files:** `app_v2/src/main/res/xml/widget_capture_ocr_panel_info.xml`

**Prompt for developer:**

> Create a resizable appwidget-provider targeting `2x1` with `4x1` horizontal resize support through `resizeMode="horizontal"`, using the panel layout as both initial and preview layout.

**Verification:**

- `Glob` - `app_v2/src/main/res/xml/widget_capture_ocr_panel_info.xml` exists.
- `Grep` - `targetCellWidth="2"` exists.
- `Grep` - `resizeMode="horizontal"` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Provider info exists with `targetCellWidth="2"` and `resizeMode="horizontal"`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] String locale audit passes for key prefix `widget_capture_ocr_panel`.

---

## Rollback Plan

Remove the new layout, provider info, and string keys.
