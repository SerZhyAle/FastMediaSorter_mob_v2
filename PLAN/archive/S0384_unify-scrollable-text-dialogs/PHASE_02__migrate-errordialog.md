# Phase 02 - Migrate ErrorDialog callers

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 2

---

## Objective

Repoint all `ErrorDialog.show(..)` call sites to `ScrollableTextDialog.show(..)` (params are identical), then delete `ErrorDialog.kt`.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| 9 caller files (GoogleAccountSettingsHelper, GeneralSettingsLogHelper, MainActivity, FileOperationDestinationDialog, UiMessageProjector, AddResourceConnectionManager, PlayerEventHandler, BrowseDialogHelper, BrowseErrorDisplayManager) | Modified | rename call + import |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt` | Deleted | superseded |

---

## Steps

### Step 02.1 - Repoint 15 ErrorDialog.show calls

**Prompt for developer:**

> In every file that calls `ErrorDialog.show(` (15 calls / 9 files), replace `ErrorDialog.show(` with `ScrollableTextDialog.show(` and fix the import (`...ui.dialog.ErrorDialog` → `...ui.dialog.ScrollableTextDialog`). Params are unchanged - the new signature is a superset with matching names. Default `showSave=true` preserves the prior save-to-file inline default for these sites.

**Verification:**

- `Grep` - `ErrorDialog.show(` returns zero hits in `app_v2/src/main/java`.
- `Grep` - `ScrollableTextDialog.show(` present in each of the 9 caller files.

**Status:** `[ ]` not done

---

### Step 02.2 - Delete ErrorDialog.kt + build

**Prompt for developer:** Delete `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt`. Build `standard debug`.

**Verification:**

- `Glob` - `ErrorDialog.kt` absent.
- `Grep` - `ErrorDialog` (as a type reference) returns zero hits in `app_v2/src/main`.
- `.\a.ps1 dq` - BUILD SUCCESSFUL.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All steps `[x]`; `standard debug` green.

---

## Handoff Notes to Next Phase

All former error-dialog sites now use the unified component. `ErrorDialog` no longer exists.
