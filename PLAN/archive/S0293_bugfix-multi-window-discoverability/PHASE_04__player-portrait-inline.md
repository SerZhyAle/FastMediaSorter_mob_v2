# Phase 04 - Player Portrait Inline Visibility (Bug C)

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - parallel-safe with Phase 01/02/03
**Blocks:** -
**Steps done:** 1 / 1
**Started:** 2026-05-23
**Completed:** 2026-05-23

---

## Objective

In the player command-panel controller's portrait and big-buttons branches, set `btnOpenInSeparateWindowCmd.isVisible = lastKnownAllowSeparateWindow` symmetrically to the landscape branch (per ADR-2). The button stops depending on the planner's priority-based bar/overflow decision and behaves like a pinned bar command - if it fits, it renders inline; if not, the layout pass naturally pushes it off-screen, but it is never silently relegated to the overflow ⋮ by command-priority ordering.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1300 |

> File is currently around 1200 LOC. Backup required if it crosses 1500 LOC after edit. Current delta is < 10 LOC, no backup needed.

---

## Steps

### Step 04.1 - Pin `btnOpenInSeparateWindowCmd` visibility in portrait + big-buttons branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `CommandPanelController.updateCommandAvailability` (or equivalent visibility-update function), locate the two non-landscape branches: (1) `bigButtonsMode && effectiveShowCommandPanel` around line 361, and (2) `showInPortrait` around line 389. After each branch's `getOverflowableButtons().forEach { it.isVisible = false }` line - which clears the adaptive buttons before applying the planner result - add an explicit override line:
>
> `safeViews.btnOpenInSeparateWindowCmd.isVisible = lastKnownAllowSeparateWindow`
>
> This must execute AFTER the `getOverflowableButtons().forEach { it.isVisible = false }` clear and BEFORE (or after; either is correct) the `result.barCommands.forEach { cmd -> barViewForCommand(cmd)?.isVisible = true }` planner pass. The planner may still add the same command to its overflow list - that is harmless because the visibility we just set takes precedence on the inline button, and the overflow menu populates from a separate command list whose presence in overflow is independent.
>
> Also, in `CommandPanelLayoutPlanner.buildActiveCommands`, the `if (allowSeparateWindow) add(PlayerCommand.OPEN_IN_SEPARATE_WINDOW)` line stays untouched - the planner continues to include the command in its bookkeeping so the existing landscape overflow list and the click-routing in `CommandPanelController` remain correct.

**Verification:**

- `Grep -n` - in `CommandPanelController.kt`, `safeViews.btnOpenInSeparateWindowCmd.isVisible = lastKnownAllowSeparateWindow` appears exactly THREE times (once in landscape, once in portrait, once in big-buttons).
- `Grep` - the existing landscape line `safeViews.btnOpenInSeparateWindowCmd.isVisible = lastKnownAllowSeparateWindow` (around line 478) is unchanged - confirm via context match.
- `Grep -n "Log\.d\("` on `CommandPanelController.kt` returns zero hits in the lines touched by this step (the file already contains expected `Timber.d` lines unrelated to this change).
- Compile check via `/build` (target: `assembleStandardDebug`) - PASS (combined build with Phase 03).

**Status:** `[x] done`

**Step Log:**

- 2026-05-23 - Verification 4/4 PASS. 3 occurrences of pin line (landscape + portrait + bigButtonsMode). Build PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for `CommandPanelController.kt` via post-change.ps1.

---

## Handoff Notes to Next Phase

The "Open in new window" button in the player command panel now renders inline in portrait and big-buttons modes whenever `allowSeparateWindow` is true, mirroring landscape behavior. Phase 05 makes this same flag react to runtime DeX entry/exit on Samsung phones.

---

## Rollback Plan

Revert the phase commit. Restores the prior planner-controlled behavior where the button always sat in overflow ⋮ in portrait. No persistent state changes.
