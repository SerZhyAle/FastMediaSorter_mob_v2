# Phase 02 - Edit submenu (embedded player)

**Strategic spec:** [`../S1364_image-player-rotation-edit-submenu.md`](../S1364_image-player-rotation-edit-submenu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Group the editing commands of the embedded player's overflow menu under one «Редактирование» section, built programmatically from the planner's already-filtered list, and never shown when it would be empty.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `menu_edit_submenu_title` resolves and `ROTATE_CONTENT_CCW` exists.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 45 |

> Over 500 LOC - back it up to `temp/S1364/` before editing (CLAUDE.md Rule 5). No `res/layout*` file is touched, so Rule 11 does not apply.
>
> If this file approaches the 1500-LOC ceiling, extract the submenu builder into `ui/player/helpers/PlayerEditSubMenuBuilder.kt` rather than growing the host (CLAUDE.md Rule 2). Check the current count before writing.

---

## Steps

### Step 02.1 - Build the editing section from the filtered list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define the editing set as a single named collection so the membership has one home:
>
> ```kotlin
> // S1364: the owner's editing set - anything that produces a changed image. Rename and undo stay
> // outside it: undo restores a deleted file rather than editing the current one.
> private val EDIT_SUBMENU_COMMANDS = setOf(
>     PlayerCommand.EDIT, PlayerCommand.CROP, PlayerCommand.CROP_TO_FILE,
>     PlayerCommand.DRAW_OVERLAY, PlayerCommand.ROTATE_CONTENT,
>     PlayerCommand.ROTATE_CONTENT_CCW, PlayerCommand.COMPRESS_COPY,
> )
> ```
>
> In `showOverflowMenu()`, before the existing per-command loop, partition `commands` into the editing members and the rest. Follow the `SendToMenuManager.buildOverflowSubMenu()` precedent exactly: count first, return early when empty, only then create the submenu.
>
> ```kotlin
> val editCommands = commands.filter { it in EDIT_SUBMENU_COMMANDS }
> val editSubMenu = if (editCommands.isEmpty()) null else {
>     popup.menu.addSubMenu(
>         android.view.Menu.NONE, android.view.Menu.NONE,
>         editCommands.minOf { it.priority },
>         context.getString(R.string.menu_edit_submenu_title)
>     ).apply { clearHeader() }
> }
> ```
>
> In the loop, add an editing command's item to `editSubMenu` instead of `popup.menu`; everything else keeps going to `popup.menu` unchanged. Icon, title and click wiring stay exactly as they are for both branches - the only change is which menu receives the item. Leave the `menu_send_to` special case ahead of this untouched.

**Why:**

Strategic §5 requires the section to be built programmatically from the planner's filtered list because `overflow_menu_player.xml` is never inflated, and §6 item 2 records that Android does not hide an empty submenu on its own, which is why the count-then-guard order from `SendToMenuManager` is copied rather than approximated.

**Verification:**

- `Grep` - `EDIT_SUBMENU_COMMANDS` matches in `CommandPanelController.kt`.
- `Grep` - `addSubMenu` matches in `CommandPanelController.kt`.
- `Grep` - `R.string.menu_edit_submenu_title` matches in that file.
- `Grep` - `PlayerCommand.RENAME` and `PlayerCommand.UNDO` do NOT appear inside the `EDIT_SUBMENU_COMMANDS` declaration - strategic §11 criterion 4.
- `Grep` - the `editCommands.isEmpty()` guard textually precedes the first `addSubMenu` call in the file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 02.2 - Correct the submenu-tinting comment this change falsifies

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> After the per-command loop, `showOverflowMenu()` walks the built menu and tints the icon of every entry that `hasSubMenu()`. Its comment states that the «Send to..» submenu "is the only sub-menu entry in this popup" - Step 02.1 makes that false. Rewrite the comment to say there are now two sections and that the loop tints both; do not change the loop itself, which already handles any number.
>
> Click routing needs no change and must not be given one: the popup sets a single `setOnMenuItemClickListener` that resolves the command by `menuItem.itemId` against the enum, and Android propagates a submenu child's click to the root popup's listener. Adding a second listener would double-dispatch. Record that in the Step Log as the evidence for criterion 7 rather than editing anything.

**Why:**

CLAUDE.md Rule 9 makes an existing comment a requirement and a stale one a defect to remove, and this particular comment is load-bearing: it is the reason a future reader would assume a single submenu and write a loop that handles only one.

**Verification:**

- `Grep` - the phrase "the only sub-menu entry" no longer appears in `CommandPanelController.kt`.
- `Grep` - `setOnMenuItemClickListener` still matches exactly once in that file, proving no second listener was added.
- Step Log records the click-routing evidence for strategic §11 criterion 7.
- `.\a.ps1 fk` - exit 0.
- Device check deferred to the `BlockNeedUserTest` note: D-pad traversal of the new section.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0 in 37s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `CommandPanelController.kt` is under the 1500-LOC ceiling (956, measured).
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Step Log

- 2026-08-07 - Step 02.1 done. `EDIT_SUBMENU_COMMANDS` placed in the existing companion object so the membership has exactly one home; the section is created only after `editCommands.isEmpty()` returns false, and the guard sits at line 609 against the single `addSubMenu` at 612 - the order the `SendToMenuManager` precedent requires. Item routing is one line: `editSubMenu?.takeIf { cmd in EDIT_SUBMENU_COMMANDS } ?: popup.menu`.
- 2026-08-07 - Step 02.1 correction during the edit: the first version wrote `targetMenu!!.add(..)`. The repo bans non-null assertions (`scripts/quality/assert-non-null-assertion.ps1`), and the compiler genuinely cannot prove the invariant, so it was rewritten with `?.takeIf { .. } ?: popup.menu`, which needs no assertion at all. Predicate `targetMenu!!` now returns 0.
- 2026-08-07 - Step 02.2 done. The tinting comment claiming «Send to..» is "the only sub-menu entry in this popup" was made false by 02.1 and is rewritten to state there are now two sections; the loop itself was already general and was not touched.
- 2026-08-07 - **Criterion 7 (click routing) evidence, as promised rather than assumed.** The popup sets exactly one `setOnMenuItemClickListener` (predicate: matches once), which resolves the command by `menuItem.itemId` against the enum. Android routes a submenu child's selection to the root popup's callback - `SubMenuBuilder` delegates its callback to the parent `MenuBuilder` - so grouping the items changes nothing about dispatch, and adding a second listener would double-dispatch. No code change was needed here and none was made.
- 2026-08-07 - Icon tinting checked for a regression that would have been easy to miss: the per-item tint (`drawable?.setTint(iconColor)`) runs inside the per-command loop, before the item is added, so it applies regardless of which menu receives the item. Moving the editing items into a submenu does not un-tint them. The separate trailing loop only tints submenu *headers*, and the new section's header carries no icon, so it is a no-op there.
- 2026-08-07 - Phase-boundary audit. Layer 1: the change is presentation-only, the membership set is a named constant rather than an inline literal, no non-null assertion, host file at ~851 LOC against the 1500 ceiling. Layers 2-4: no coroutine, listener, lifecycle or Room surface touched. No P0/P1.

---

## Handoff Notes to Next Phase

The editing membership lives in one named set in `CommandPanelController`. Phase 03 mirrors that same membership in the standalone menu XML; if the two ever disagree, this set is the source of truth for what the owner ruled.

---

## Rollback Plan

Revert the phase commit. The change is confined to how the overflow menu is rendered - no state, no persistence, no resource removed.
