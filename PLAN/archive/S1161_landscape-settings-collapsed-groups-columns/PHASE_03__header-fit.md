# Phase 03 - Fit at half width

**Strategic spec:** [`../S1161_landscape-settings-collapsed-groups-columns.md`](../S1161_landscape-settings-collapsed-groups-columns.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Keep a half-width card readable: refuse to split into columns when a column would be too narrow to
hold a settings card, and stop a pathological group title from growing a card by several lines.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsGroupsGridLayout.kt` | Modified | ≤ 210 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | +1 line |
| `app_v2/src/main/res/layout/view_collapsible_section_header.xml` | Modified | +2 lines |

---

## Steps

### Step 03.1 - Minimum column width fallback

**Files:** `SettingsGroupsGridLayout.kt`, `app_v2/src/main/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add dimen `settings_group_min_column_width` (300dp) and apply it in `onMeasure`: compute the
> candidate column width first, and if it falls below the dimen, drop to a single column for this
> pass. The resource integer stays the requested column count - this is a width-driven veto on top
> of it.
>
> Why this and not a title truncation: the failure this guards against is a card whose content
> (a toggle row with a label and a switch, a two-field input row) stops fitting, and no amount of
> title shortening fixes that. It also removes the need to enumerate which small landscape phones are
> safe - the layout decides from the width it actually got.

**Verification:**

- `Grep` - `settings_group_min_column_width` matches exactly once in `values/dimens.xml` and once in `SettingsGroupsGridLayout.kt`.
- `Grep` - the dimen is not defined under `app_v2/src/main/res/values-land/` (it is orientation-independent).
- `.\a.ps1 fc` - exit code 0.

**Status:** `[x]` done

---

### Step 03.2 - Bound the group title height

**Files:** `app_v2/src/main/res/layout/view_collapsible_section_header.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `android:maxLines="3"` and `android:ellipsize="end"` to `@id/csh_title`. It currently wraps
> without limit, which at half width lets one long translated title set the height of an entire grid
> row. Three lines is above every shipped EN/RU/UK title at half width, so this is a guard against a
> future string, not a truncation of a current one - if a real title ever hits the cap, shorten the
> string rather than raising the cap.
>
> There is no `layout-land` variant of this widget layout, so CLAUDE.md Rule 11 needs no counterpart
> edit - confirm with a `Glob` before concluding that.

**Verification:**

- `Grep` - `maxLines` and `ellipsize` both appear in the `csh_title` block of `view_collapsible_section_header.xml`.
- `Glob` - `app_v2/src/main/res/layout-land/view_collapsible_section_header.xml` does not exist.
- `.\a.ps1 fr` - exit code 0 (resources/manifest).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Layout behaviour is complete. What remains is on-device confirmation (strategic §11 criteria 4-7),
which is the ticket's `BlockNeedUserTest` gate, and the mechanical closure in Phase 04.

---

## Rollback Plan

Revert the dimen and the two title attributes; the grid falls back to unconditional column splitting.
