# Phase 02 - Dialog layout (portrait + landscape)

**Strategic spec:** [`../S0378_system-info-dialog-compact-actions.md`](../S0378_system-info-dialog-compact-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Replace the full-width `btnSaveToFile` with a single compact horizontal icon-button row, and give the main scroll area a distinct surface background plus an always-visible scrollbar. Both orientations changed in lockstep (Strict Rule 12).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (new drawables exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_error_detail.xml` | Modified | ≤ 130 |
| `app_v2/src/main/res/layout-land/dialog_error_detail.xml` | Modified | ≤ 130 |

> Landscape counterpart exists - both edited in this phase.

---

## Steps

### Step 02.1 - Highlight the main scroll area

**Files:** `app_v2/src/main/res/layout/dialog_error_detail.xml`, `app_v2/src/main/res/layout-land/dialog_error_detail.xml`

**Prompt for developer:**

> On the first (main message) `ScrollView` in BOTH layouts add `android:background="@drawable/bg_dialog_scroll_surface"`, `android:fadeScrollbars="false"`, `android:scrollbarStyle="insideInset"`, and inner padding (`android:padding="@dimen/padding_small"` or similar) so the surface reads as a distinct, scrollable panel from the dialog background. Do not alter `tvErrorMessage` selectability. Leave the collapsible `scrollDetails` block as-is.

**Verification:**

- `Grep` - `@drawable/bg_dialog_scroll_surface` present in both files.
- `Grep` - `android:fadeScrollbars="false"` present in both files.
- `Grep` - `android:textIsSelectable="true"` still present on `tvErrorMessage` in both files.

**Status:** `[ ]` not done

---

### Step 02.2 - Replace inline button with compact icon action row

**Files:** `app_v2/src/main/res/layout/dialog_error_detail.xml`, `app_v2/src/main/res/layout-land/dialog_error_detail.xml`

**Prompt for developer:**

> In BOTH layouts, replace the `<Button android:id="@+id/btnSaveToFile" .../>` with a horizontal `LinearLayout` (`android:id="@+id/layoutDialogActions"`, `android:gravity="end"`, top margin) containing four `com.google.android.material.button.MaterialButton` children in order: `btnPrimary`, `btnInlineAction`, `btnCopy`, `btnClose`. Each uses `style="@style/Widget.Material3.Button.IconButton"` so the 48dp minimum touch target is enforced by the style. Set default icons in XML: `btnPrimary` -> `@drawable/ic_share`, `btnInlineAction` -> `@drawable/ic_create_text_file`, `btnCopy` -> `@drawable/ic_copy`, `btnClose` -> `@drawable/ic_clear`. Do not set text in XML (wired in Phase 03). Ensure focus order follows child order (default for a horizontal LinearLayout of focusable buttons).

**Verification:**

- `Grep` - `@+id/layoutDialogActions` present in both files.
- `Grep` - all four ids `btnPrimary`, `btnInlineAction`, `btnCopy`, `btnClose` present in both files.
- `Grep` - `btnSaveToFile` absent in both files.
- `Grep` - `Widget.Material3.Button.IconButton` present in both files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Both portrait and landscape layouts edited identically (Strict Rule 12).
- [ ] Build deferred to Phase 03 (layout + wiring validated together by one `standard debug` build).

---

## Handoff Notes to Next Phase

The layout now exposes `layoutDialogActions` with `btnPrimary` / `btnInlineAction` / `btnCopy` / `btnClose`, and no longer has `btnSaveToFile`. Phase 03 must update `ErrorDialog.kt` to bind the new ids and drop the old `btnSaveToFile` reference, or the build breaks.

---

## Rollback Plan

Revert both layout files to the prior `btnSaveToFile` form - Phase 03 reverts in tandem.
