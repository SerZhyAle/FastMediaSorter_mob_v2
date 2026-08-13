# Phase 02 - Main player wiring

**Strategic spec:** [`../S0681_send-to-menu-resource-picker.md`](../S0681_send-to-menu-resource-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 1 / 1
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Make the pinned «Select resource..» entry appear and work in the main player by passing `onPickResource` from the player's send-to call sites to the existing player copy-to dialog.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 440 |

> No new strings, layouts, or classes. Reuses the existing `PlayerDialogAndUiStateManager.showCopyDialog()` (full recipient dialog with «..») already wired in the main player.

---

## Steps

### Step 02.1 - Pass `onPickResource` from the player send-to call sites

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> In `onSendToClicked()`, pass `onPickResource = { activity.dialogAndUiStateManager.showCopyDialog() }` to `activity.sendToMenuManager.show(activity, content, settings, onPickResource = ..)`. In `onSendToOverflowSubMenuRequested(menu, order)`, pass the same callback to `buildOverflowSubMenu(..)`. The reused `showCopyDialog()` already opens the COPY recipient dialog for the current file: it excludes the current `resourceId` from the recipient list (strategic §6.2) and honors the existing `goToNextAfterCopy` setting on completion (strategic §6.3) - do not duplicate that logic here.

**Verification:**

- `Grep` - `onPickResource` matches at least twice in `PlayerCommandPanelCallbackImpl.kt` (one per call site).
- `Grep` - `dialogAndUiStateManager.showCopyDialog()` matches in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `PlayerCommandPanelCallbackImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Step 02.1: Verification 3/3 PASS. Both `onSendToClicked` and `onSendToOverflowSubMenuRequested` pass `onPickResource = { activity.dialogAndUiStateManager.showCopyDialog() }`. Build SUCCESSFUL (shared `fc`).

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Main player now shows the pinned «Select resource..» entry (bottom sheet + overflow) and opens the existing player copy dialog. Standalone hosts still need their own entry point (Phase 03).

---

## Rollback Plan

Revert phase commit - removes the two callback arguments; the menu reverts to receivers-only in the main player.
