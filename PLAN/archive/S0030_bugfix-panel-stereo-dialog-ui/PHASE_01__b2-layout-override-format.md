# Phase 01 — Б2: Fix layout of "Override format type" row

**Strategic spec:** [`../S0030_bugfix-panel-stereo-dialog-ui.md`](../S0030_bugfix-panel-stereo-dialog-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

`MaterialSwitch` for "Override format type" currently uses `android:layout_width="match_parent"`, which pushes text to the far left and thumb to the far right — a wide gap that breaks visual consistency with all other toggle rows in the app. Fix: wrap the switch + label into a `LinearLayout horizontal` following the canonical pattern (`switch wrap_content` on left, `TextView weight=1` on right). XML-only change; Kotlin logic untouched.

## Files Touched

| File | Change |
|------|--------|
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Wrap `switchVrOverrideFormatType` in canonical horizontal LinearLayout |
| `app_v2/src/main/res/layout-land/dialog_playback_control.xml` | Same fix for landscape variant |

---

## Steps

### Step 1.1 — Fix portrait layout

**Status:** `[ ] not done`
**Depends on:** —

**Prompt for developer:**
In `app_v2/src/main/res/layout/dialog_playback_control.xml`, locate the `<com.google.android.material.materialswitch.MaterialSwitch android:id="@+id/switchVrOverrideFormatType"` element.

Replace the standalone `MaterialSwitch` with a `LinearLayout horizontal` wrapper:

- Outer `LinearLayout`: `layout_width="match_parent"`, `layout_height="wrap_content"`, `orientation="horizontal"`, `gravity="center_vertical"`, keep existing `layout_marginBottom="8dp"`.
- `MaterialSwitch` inside: `id="@+id/switchVrOverrideFormatType"`, `layout_width="wrap_content"`, `layout_height="wrap_content"`, `layout_marginEnd="8dp"`, `focusable="true"`, `focusableInTouchMode="false"` — **remove** the `android:text` attribute (label moves to TextView).
- `TextView` after the switch: `layout_width="0dp"`, `layout_height="wrap_content"`, `layout_weight="1"`, `android:text="@string/playback_settings_3d_override_format_type"`.

**Verification:** `grep -n "switchVrOverrideFormatType" layout/dialog_playback_control.xml` shows the switch ID inside a LinearLayout child. The switch no longer has `android:text`. Lint reports no new warnings for this file.

---

### Step 1.2 — Fix landscape layout

**Status:** `[ ] not done`
**Depends on:** —

**Prompt for developer:**
Apply the identical fix to `app_v2/src/main/res/layout-land/dialog_playback_control.xml`.
Locate `android:id="@+id/switchVrOverrideFormatType"` (currently at line ~276).
Replace with the same canonical pattern: outer `LinearLayout horizontal` (keep `layout_marginBottom="6dp"`), switch `wrap_content` with `layout_marginEnd="8dp"`, then `TextView weight=1` with the string resource.

**Verification:** Same check as Step 1.1 but for the landscape file.

---

### Step 1.3 — Dev log + lint

**Status:** `[ ] not done`
**Depends on:** 1.1, 1.2

**Prompt for developer:**

1. Run `.\gradlew.bat lintStandardDebug` — verify zero new errors in `dialog_playback_control.xml` and `dialog_playback_control.xml (land)`.
2. Run `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/dialog_playback_control.xml" "switchVrOverrideFormatType" "B2: Wrap override-format switch in canonical horizontal LinearLayout (portrait)"`
3. Run `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_playback_control.xml" "switchVrOverrideFormatType" "B2: Wrap override-format switch in canonical horizontal LinearLayout (landscape)"`

**Verification:** Both dev-log commands exit 0. Lint output shows `lintStandardDebug: No issues found` or same baseline count as before.
