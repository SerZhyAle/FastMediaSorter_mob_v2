# Phase 01 - Action assets

**Strategic spec:** [`../S0378_system-info-dialog-compact-actions.md`](../S0378_system-info-dialog-compact-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Add the two new vector/shape assets the redesigned dialog needs: a dedicated "Copy full report" icon and a highlighted scroll-area background. All other action icons are reused (`ic_clear`, `ic_copy`, `ic_share`, `ic_create_text_file`).

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_copy_full_report.xml` | New | ≤ 15 |
| `app_v2/src/main/res/drawable/bg_dialog_scroll_surface.xml` | New | ≤ 12 |

---

## Steps

### Step 01.1 - Add Copy-full-report vector icon

**Files:** `app_v2/src/main/res/drawable/ic_copy_full_report.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a 24dp vector drawable using the Material `copy_all` glyph (two stacked sheets) so it reads as "copy the entire report" and stays visually distinct from the single-sheet `ic_copy`. Use `android:fillColor="#FFFFFF"` to match the existing icon set (tint is applied by the button style).

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_copy_full_report.xml` exists.
- `Grep` - exactly one `<vector` in the file.
- `Grep` - `android:pathData=` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Add highlighted scroll-area background

**Files:** `app_v2/src/main/res/drawable/bg_dialog_scroll_surface.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `<shape android:shape="rectangle">` drawable with `<solid android:color="?attr/colorSurfaceVariant" />` and small `<corners android:radius="@dimen/..." />` (reuse an existing small corner-radius dimen; if none fits, use `8dp`). This is the non-color-only differentiation surface for the scrollable text area; it reuses the Material3 `colorSurfaceVariant` theme attr (strategic §6 item 3) and resolves correctly in light and dark themes.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/bg_dialog_scroll_surface.xml` exists.
- `Grep` - `?attr/colorSurfaceVariant` present in the file.
- `Grep` - `<corners` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (deferred to Phase 03 - assets alone do not need a standalone build; aapt validates them in the Phase 02/03 build).
- [ ] Dev log entry added for both new files (Phase 04).

---

## Handoff Notes to Next Phase

`ic_copy_full_report` and `bg_dialog_scroll_surface` exist and are referenced by the Phase 02 layout. Reused icons confirmed present: `ic_clear` (close, plain X), `ic_copy`, `ic_share`, `ic_create_text_file` (save to file, note_add glyph).

---

## Rollback Plan

Delete the two new drawable files - no other code references them until Phase 02.
