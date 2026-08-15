# Phase 04 - ScheduledLog reuse unified layer

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 2

---

## Objective

Make `ScheduledLogDialog` reuse the unified component instead of its own layout, keeping its `Dialog` lifecycle and in-place "clear" behaviour (§6.3).

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| `ui/dialog/ScheduledLogDialog.kt` | Modified | reuse `ScrollableTextDialog` |
| `res/layout/dialog_scheduled_log.xml` | Deleted | superseded (no land variant) |

---

## Steps

### Step 04.1 - Refactor ScheduledLogDialog onto the unified component

**Prompt for developer:**

> Replace the single caller (`OperationsSettingsFragment` line ~434) so the scheduled-ops log is shown via `ScrollableTextDialog.show(context, title = scheduled_ops_log_title, message = logContent-or-empty, monospace = true, showShare = false, showSave = false, extraAction = ExtraAction(icon = <existing erase/delete drawable>, contentDescription = scheduled_ops_log_clear, dismissOnClick = false, onClick = { onClearLog() }))`. Copy and Close come from the unified row. The "clear" handler invokes the existing clear logic; since the unified dialog is fire-and-forget, after clear the dialog may be dismissed or left as-is (clear is a terminal action here). Remove the now-unused `ScheduledLogDialog` class if nothing else references it, or keep it as a thin wrapper delegating to `ScrollableTextDialog.show`. Pick the existing clear/erase icon by Grep (`ic_delete`, `ic_clear`, `ic_filter_clear`); if none semantically fits "erase log", reuse `ic_clear`.

**Verification:**

- `Grep` - `ScrollableTextDialog.show(` present in `OperationsSettingsFragment.kt` (or in a thin `ScheduledLogDialog` wrapper).
- `Grep` - `DialogScheduledLogBinding` returns zero hits (binding no longer used).

**Status:** `[ ]` not done

---

### Step 04.2 - Delete dialog_scheduled_log.xml + build

**Prompt for developer:** Delete `res/layout/dialog_scheduled_log.xml`. Build `standard debug`.

**Verification:**

- `Glob` - `dialog_scheduled_log.xml` absent.
- `.\a.ps1 dq` - BUILD SUCCESSFUL.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All steps `[x]`; `standard debug` green.

---

## Handoff Notes to Next Phase

The scheduled-ops log uses the unified component. `dialog_scheduled_log.xml` removed; its auto-strings handled in Phase 05.
