# Phase 03 - Migrate DialogUtils callers

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 2

---

## Objective

Repoint the 5 live `DialogUtils.showScrollableDialog(..)` calls to `ScrollableTextDialog.show(..)`, then delete `DialogUtils.kt`, the dead `ErrorDialogHelper.kt`, and `dialog_scrollable_text.xml` (+land).

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| AddResourceConnectionManager.kt, GeneralSettingsImportExportHelper.kt, GeneralSettingsLogHelper.kt | Modified | repoint 5 calls |
| `ui/common/DialogUtils.kt` | Deleted | only had showScrollableDialog |
| `ui/common/ErrorDialogHelper.kt` | Deleted | 0 external callers |
| `res/layout/dialog_scrollable_text.xml` + `layout-land/dialog_scrollable_text.xml` | Deleted | superseded |

---

## Steps

### Step 03.1 - Repoint 5 DialogUtils calls

**Prompt for developer:**

> Replace the 5 live `DialogUtils.showScrollableDialog(context, title, message, <positive>)` calls with `ScrollableTextDialog.show(context = .., title = .., message = .., showSave = false)`. The old positive/OK button maps to the unified Close action (no separate positive needed). For the log dialog in `GeneralSettingsLogHelper` (full/session log) pass `monospace = true`. Fix imports. Do not touch the dead calls inside `ErrorDialogHelper` (whole file is deleted in 03.2).

**Verification:**

- `Grep` - `showScrollableDialog(` returns zero hits outside `DialogUtils.kt`/`ErrorDialogHelper.kt` (both deleted in 03.2).
- `Grep` - `ScrollableTextDialog.show(` present in the 3 caller files.

**Status:** `[ ]` not done

---

### Step 03.2 - Delete DialogUtils + ErrorDialogHelper + scrollable_text layouts + build

**Prompt for developer:** Delete `DialogUtils.kt`, `ErrorDialogHelper.kt`, `res/layout/dialog_scrollable_text.xml`, `res/layout-land/dialog_scrollable_text.xml`. Build `standard debug`.

**Verification:**

- `Glob` - all four files absent.
- `Grep` - `DialogUtils`, `ErrorDialogHelper`, `dialog_scrollable_text` return zero hits in `app_v2/src/main`.
- `.\a.ps1 dq` - BUILD SUCCESSFUL.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All steps `[x]`; `standard debug` green.

---

## Handoff Notes to Next Phase

Both former dialog utilities are gone; only `ScrollableTextDialog` remains for free-text dialogs.
