# Phase 01 - Row Primitives

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Adjust the two shared compound rows so consumers can place value next to title (R3) and render a dropdown with its label inline on one line (R6). No fragment layout changes here.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] Strategic §6 item 1 Resolved (it is - see research/01).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/view_settings_selection_row.xml` | Modified | ≤ 110 |
| `app_v2/src/main/res/values/attrs.xml` | Modified | +1 attr |
| `app_v2/src/main/res/layout/view_settings_dropdown_row.xml` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt` | Modified | ≤ 230 |

> Both row layouts have no `layout-land/` variant - they are single shared merge files used in both orientations (intended per ADR-1). No landscape counterpart to mirror.

---

## Steps

### Step 01.1 - Value hugs title in the selection row (R3)

**Files:** `app_v2/src/main/res/layout/view_settings_selection_row.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Make the value sit inline next to the title while keeping the chevron pinned right and protecting long titles. Move `ssr_value` from a root-level sibling INTO the title-line `LinearLayout`, placed after `ssr_iconHelp` and before that line's weighted spacer. Keep `ssr_textGroup` at `layout_width="0dp"` + `layout_weight="1"` (long-title safety per ARCHITECTURE) so the chevron stays at the row's right edge. Result inside the text group's title line: `[title][help][value][spacer]`; row: `[icon][textGroup weight=1][trailingSlot][chevron]`. Do not touch `SettingsSelectionRow.kt` (ids unchanged).

**Verification:**

- `Grep` - `ssr_value` matches exactly once in `view_settings_selection_row.xml`.
- `Grep` - `ssr_textGroup` retains `layout_weight="1"`.
- `/build` (`.\a.ps1 fc`) passes (at phase boundary).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 2/2 PASS (build at phase boundary). Refined from the planned spacer-relocation to value-inline-in-title-line: keeping `ssr_textGroup` weight protects long titles from crowding the chevron (ARCHITECTURE Pattern A invariant). File: view_settings_selection_row.xml.

---

### Step 01.2 - Inline mode for the dropdown row (R6)

**Files:** `app_v2/src/main/res/values/attrs.xml`, `app_v2/src/main/res/layout/view_settings_dropdown_row.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt`
**Depends on:** Step 01.1 (independent file; ordered for a single phase build)

**Prompt for developer:**

> Add an opt-in inline layout to `SettingsDropdownRow` so the label sits left of the field on one line. In `attrs.xml`, add `<attr name="sdr_inline" format="boolean" />` to the `SettingsDropdownRow` styleable. In `view_settings_dropdown_row.xml`, give the existing anonymous title-line weighted spacer `android:id="@+id/sdr_titleLineSpacer"`. In `SettingsDropdownRow.kt`, read `sdr_inline` (default false) in `applyAttributes`; when true call a new private `applyInlineLayout()` that sets root `orientation = HORIZONTAL`, `gravity = CENTER_VERTICAL`, sets the title-line `LayoutParams` to `wrap_content` with an end margin (`settings_help_icon_margin`), collapses `sdr_titleLineSpacer` to `GONE`, and sets `inputLayout` `LayoutParams` to `width = 0dp, weight = 1f, gravity = CENTER_VERTICAL`. Portrait/default path is unchanged (still vertical, stacked). No host code changes.

**Verification:**

- `Grep` - `sdr_inline` matches in `attrs.xml` and in `SettingsDropdownRow.kt`.
- `Grep` - `sdr_titleLineSpacer` matches once in `view_settings_dropdown_row.xml`.
- `Grep -n "Log\.d\("` in `SettingsDropdownRow.kt` returns zero hits.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification PASS. Added `sdr_inline` attr; gave the title-line spacer id `sdr_titleLineSpacer`; `applyInlineLayout()` switches root to horizontal (label left of field) when set. Build `.\a.ps1 fc` SUCCESSFUL. Files: attrs.xml, view_settings_dropdown_row.xml, SettingsDropdownRow.kt.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (SettingsDropdownRow public attr surface changed) - done in Phase 06.

---

## Handoff Notes to Next Phase

`ssr_valueSpacer` now pins the chevron right while value hugs title - every `SettingsSelectionRow` host (incl. Device profile) inherits R3 automatically. `app:sdr_inline="true"` is available for landscape dropdown rows. Phase 02 consumes both.

---

## Rollback Plan

Revert phase commit(s) - both rows fall back to prior stacked/right-edge layout. No data migration or persisted surface changed.
