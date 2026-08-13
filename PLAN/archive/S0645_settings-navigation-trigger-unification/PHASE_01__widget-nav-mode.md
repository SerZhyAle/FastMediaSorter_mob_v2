# Phase 01 - Widget navigation mode

**Strategic spec:** [`../S0645_settings-navigation-trigger-unification.md`](../S0645_settings-navigation-trigger-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Add a navigation mode to `SettingsSelectionRow`: a single boolean attribute that swaps the trailing glyph from the value chevron to a real forward arrow and collapses the row content to no-stretch (arrow pinned right after the text). No new widget class; no UI wiring yet.

---

## Prerequisites

- [ ] Strategic §6 owner decisions Resolved (extend the widget; arrow vs chevron). Confirmed in strategic spec.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ +3 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsSelectionRow.kt` | Modified | ≤ 320 |

> No layout file edits in this phase. `view_settings_selection_row.xml` is unchanged - the arrow is applied at runtime by swapping the existing `ssr_chevron` ImageView's drawable. The widget layout is a single `<merge>` with no `layout-land` counterpart, so no landscape variant applies.

---

## Steps

### Step 01.1 - Declare the `ssr_navMode` attribute

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the existing `<declare-styleable name="SettingsSelectionRow">` block, add one boolean attribute `ssr_navMode` with a comment: navigation mode renders the trailing glyph as a real forward arrow (`->`) instead of the value chevron and collapses the row content to hug the left so the arrow sits right after the text. Default false. Do not touch the `SettingsDropdownRow` styleable below it.

**Verification:**

- `Grep` - `app_v2/src/main/res/values/attrs.xml` contains `name="ssr_navMode"` exactly once.
- `Grep` - the new attr line sits between `<declare-styleable name="SettingsSelectionRow">` and its closing `</declare-styleable>`.

**Status:** `[ ]` not done

---

### Step 01.2 - Implement navigation mode in `SettingsSelectionRow`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsSelectionRow.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a public `fun setNavigationMode(enabled: Boolean)`. When enabled: set the trailing glyph drawable to `R.drawable.ic_arrow_forward` (the real arrow), ensure the glyph is visible, and apply the existing no-stretch content collapse so the arrow sits right after the text (reuse `applyInlineLayout()` - extract it if it is currently only callable from the inline path; the row itself stays full-width-clickable, only its content hugs the left). Leading icon and subtitle/hint must keep rendering. When disabled, keep the default chevron (`ic_chevron_right`) - this is the value-row appearance. In `applyAttributes`, read `R.styleable.SettingsSelectionRow_ssr_navMode` (default false) and call `setNavigationMode(true)` when set. Do not regress the existing `ssr_inline`, `ssr_showChevron`, `ssr_value`, help-icon, or `setOnRowClickListener` behaviour. Update the class KDoc to mention the navigation mode. No trivial restating comments.

**Verification:**

- `Grep` - `fun setNavigationMode(` matches exactly once in `SettingsSelectionRow.kt`.
- `Grep` - `R.drawable.ic_arrow_forward` referenced in `SettingsSelectionRow.kt`.
- `Grep` - `ssr_navMode` (styleable read) referenced in `applyAttributes`.
- `Grep -n "Log\.d\("` on the file returns zero hits (Timber only).
- `/build` -> `standard debug` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (batch with later phases is acceptable).
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 04 (public API of an existing class changed, not a new class).

---

## Handoff Notes to Next Phase

`SettingsSelectionRow` now exposes `app:ssr_navMode` (XML) and `setNavigationMode()` (Kotlin). Phase 02 applies the XML flag to the two Bucket A rows; Phase 03 migrates the two Bucket B ad-hoc rows onto the widget with the same flag. Watch the no-stretch + subtitle combination: nav-mode is the first use of the content collapse on rows that also have a subtitle - confirm the subtitle is not truncated (device-test item).

---

## Rollback Plan

Revert the phase commit(s) - no data migration or persisted state changed; the new attribute is additive and defaults to the prior chevron behaviour.
