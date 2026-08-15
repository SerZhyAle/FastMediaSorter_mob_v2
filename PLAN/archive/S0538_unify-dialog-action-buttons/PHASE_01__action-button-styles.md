# Phase 01 - Action Button Styles

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Introduce the named dialog action-button style family (confirm / cancel / destructive) whose size is driven by a theme attribute, plus the large + compact dimens and the compact theme overlay - so the pair follows the global "Compact elements" toggle. No Kotlin hook or theme seam yet (Phase 02).

---

## Prerequisites

- [ ] Strategic §6 items 1-4 Resolved (they are; owner sign-off 2026-06-19, compact mechanism research 03).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ 12 added |
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ 6 added |
| `app_v2/src/main/res/values/themes.xml` | Modified | ≤ 80 added |

> Colors `@color/success_color` / `@color/delete_button` already carry light + `values-night` variants - no new color resources. Styles + theme overlay live in `values/themes.xml` (the button taxonomy family is defined there). Compact = exact 50% of large per the documented "reduce by 50%" rule (research 03).

---

## Steps

### Step 01.1 - Add large + compact dialog action-button dimens

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four dimens for the unified dialog action pair: large `dialog_action_button_min_height` = 56dp and `dialog_action_button_gap` = 16dp; compact `dialog_action_button_min_height_compact` = 28dp and `dialog_action_button_gap_compact` = 8dp (exactly half - honors the "Compact elements reduces sizes by 50%" contract). Reference these via the theme attribute (Step 01.3), never hardcode them in layouts.

**Verification:**

- `Grep` - `dialog_action_button_min_height">56dp` and `dialog_action_button_gap">16dp` each match once.
- `Grep` - `dialog_action_button_min_height_compact">28dp` and `dialog_action_button_gap_compact">8dp` each match once.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. `dimens.xml` +4 dimens (large 56/16, compact 28/8). Dev log recorded.

---

### Step 01.2 - Declare the dialog action-button theme attributes

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare two theme attributes so the action-button size can be swapped by a theme overlay (compact vs large) instead of being hardcoded: `<attr name="dialogActionButtonMinHeight" format="dimension|reference" />` and `<attr name="dialogActionButtonGap" format="dimension|reference" />`. Place them with the other app theme attrs.

**Verification:**

- `Grep` - `name="dialogActionButtonMinHeight"` matches once in `attrs.xml`.
- `Grep` - `name="dialogActionButtonGap"` matches once in `attrs.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. `attrs.xml` +2 theme attrs (dialogActionButtonMinHeight, dialogActionButtonGap). Dev log recorded.

---

### Step 01.3 - Add the styles, base-theme attr values, and compact overlay

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Three edits in `themes.xml`:
> 1. In `Theme.FastMediaSorter.App`, bind the attrs to the LARGE dimens (default, since compact default is false): `dialogActionButtonMinHeight`=`@dimen/dialog_action_button_min_height`, `dialogActionButtonGap`=`@dimen/dialog_action_button_gap`.
> 2. Add `ThemeOverlay.FastMediaSorter.CompactDialogButtons` (no parent or `parent=""`) that rebinds the same two attrs to the `_compact` dimens. Phase 02 applies this overlay in `BaseActivity` when compact is on.
> 3. Below the `Widget.FastMediaSorter.Button.*` family add the three action styles, each taking min-height from the attribute (not a fixed dimen):
>    - `Widget.FastMediaSorter.Button.DialogConfirm` parent `..Button.Filled`: `backgroundTint`=`@color/success_color`, `android:textColor`=`@color/white`, `android:minHeight`=`?attr/dialogActionButtonMinHeight`.
>    - `Widget.FastMediaSorter.Button.DialogDestructive` parent `..Button.Filled`: `backgroundTint`=`@color/delete_button`, `android:textColor`=`@color/white`, `android:minHeight`=`?attr/dialogActionButtonMinHeight`.
>    - `Widget.FastMediaSorter.Button.DialogCancel` parent `..Button.Outlined`: `android:textColor`=`?attr/colorOnSurface`, `strokeColor`=`?attr/colorOutline`, `android:minHeight`=`?attr/dialogActionButtonMinHeight`.
> Never put a raw `#hex` on a button (Rule 19) - `@color/` and `?attr/` only.

**Verification:**

- `Grep` - `name="Widget.FastMediaSorter.Button.DialogConfirm"`, `..DialogCancel"`, `..DialogDestructive"` each match once.
- `Grep` - `name="ThemeOverlay.FastMediaSorter.CompactDialogButtons"` matches once.
- `Grep` - `?attr/dialogActionButtonMinHeight` appears in each of the three style blocks.
- `Grep` - `dialogActionButtonMinHeight` set to `@dimen/dialog_action_button_min_height` inside `Theme.FastMediaSorter.App` and to `@dimen/dialog_action_button_min_height_compact` inside the compact overlay.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. `themes.xml` +3 styles (?attr-sized), +compact overlay, +base-theme attr bindings. Dev log recorded.

---

### Step 01.4 - Compile-check the resource additions

**Files:** (validation step)
**Depends on:** Step 01.3

**Prompt for developer:**

> Confirm the new attrs, styles, dimens and overlay resolve. Run `.\a.ps1 fr`. The `?attr/` references and style parents must resolve with no AAPT error.

**Verification:**

- `.\a.ps1 fr` exits 0 (expected: PASS).

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - `.\a.ps1 fr` BUILD SUCCESSFUL (exit 0). Attrs, styles, dimens, overlay resolve, no AAPT error.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project resources compile - `.\a.ps1 fr` exits 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `themes.xml` + `dimens.xml` + `attrs.xml`.

---

## Handoff Notes to Next Phase

Three named styles (`DialogConfirm` green, `DialogCancel` outlined neutral, `DialogDestructive` red), each sized from `?attr/dialogActionButtonMinHeight`. Default theme = large; `ThemeOverlay.FastMediaSorter.CompactDialogButtons` = 50% compact. Phase 02 wires the styles into the Material builder seam AND applies the compact overlay in `BaseActivity` so every dialog follows the global toggle.

---

## Rollback Plan

Revert phase commit(s) - pure resource additions, no data migration or user-facing surface changed yet.
