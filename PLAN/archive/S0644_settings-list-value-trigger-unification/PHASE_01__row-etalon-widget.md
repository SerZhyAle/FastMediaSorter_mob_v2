# Phase 01 - Row etalon at the widget level

**Strategic spec:** [`../S0644_settings-list-value-trigger-unification.md`](../S0644_settings-list-value-trigger-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** S0648 (concrete upload-target instance of this etalon)
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Make the list-value trigger-row etalon (chevron right after the text, content hugs left, no full-width stretch) the default for every `SettingsSelectionRow` value row, in portrait and landscape, without per-row layout edits and without dropping the touch-target height. Reuse the existing hug-left mechanism (`applyInlineLayout` from S0645/S0618) by extracting its layout part.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsSelectionRow.kt` | Modified | n/a |

> Widget layout `view_settings_selection_row.xml` is orientation-shared (no `layout-land` variant), so a single widget-level change covers portrait + landscape across all usages.

---

## Steps

### Step 01.1 - Extract collapse-to-left and make it the value-row default

**Files:** `SettingsSelectionRow.kt`
**Prompt for developer:**

> Extract the layout part of `applyInlineLayout()` (text group `weight=0`/`WRAP_CONTENT`, title line `WRAP_CONTENT`, title-line spacer `GONE`) into a private `collapseContentToLeft()`. Keep `applyInlineLayout()` = `minimumHeight = 0` + `collapseContentToLeft()` (dense landscape / navigation rows still drop the touch band). In `init`, after chevron visibility is set and before the `ssr_inline` / `ssr_navMode` checks, call `collapseContentToLeft()` by default when the row has no subtitle (`subtitleView.visibility == View.GONE`), so single-line value rows pin the chevron right after the text while subtitle rows keep the full-width text group.

**Verification:**

- `Grep` - `private fun collapseContentToLeft()` present; `applyInlineLayout` calls it.
- `Grep` - default `collapseContentToLeft()` call guarded by `subtitleView.visibility == View.GONE` in `init`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Implemented. Value rows hug-left by default (chevron after text); subtitle/navigation rows unaffected. Debug tag `Timber.d("S0644: ..")` added at the default-collapse branch.

---

### Step 01.2 - Build

**Files:** (build only)
**Prompt for developer:**

> Build standard debug.

**Verification:**

- `/build` -> `standard debug` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - BUILD SUCCESSFUL (APK v2.60.6211.547-DEBUG). Neuroslop gate delta 0.

---

## Phase Done Criteria

- [x] Steps `[x] done`.
- [x] Project compiles.
- [x] Single widget change covers portrait + landscape (shared widget layout).

---

## Audit note

All known list-value trigger rows already use `SettingsSelectionRow` (delivered by S0567, extended by S0646). The widget-level change therefore reaches every value-selection row (gesture actions, OCR font/family/engine/model, audio empty-state, destination pickers, statistics dashboard entry, etc.) without per-row edits. Any future value row that is not a `SettingsSelectionRow` would be a separate follow-up.

---

## Rollback Plan

Revert the widget change: `collapseContentToLeft()` re-inlined into `applyInlineLayout()` and the default call removed; rows return to the S0618 right-edge chevron.
