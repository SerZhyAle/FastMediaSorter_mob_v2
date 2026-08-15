# Phase 03 - Overflow menu host

**Strategic spec:** [`../S1672_bugfix-command-bar-last-item-clipped.md`](../S1672_bugfix-command-bar-last-item-clipped.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-15

---

## Objective

Surface every evicted command inside the existing main-window "⋮" popup, and make that button appear whenever the popup has at least one item to show.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `MainLayoutChromeManager.isOverflowed()` and its `onOverflowChanged` callback exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandOverflowMenuManager.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1470 |

> `MainActivity.kt` is 1450 LOC against CLAUDE.md Rule 2's 1500-LOC ceiling, so the dispatch and item-building logic belongs in the new manager and the activity keeps only its wiring lines. The file is over 500 LOC, so take the Rule 5 timestamped backup before editing it. No `res/layout*` file is touched: the anchor button already exists in all three variants, so CLAUDE.md Rule 11 landscape parity does not apply.

---

## Steps

### Step 03.1 - Write the overflow menu manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandOverflowMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MainCommandOverflowMenuManager` in package `com.sza.fastmediasorter.ui.main.helpers`, taking `ActivityMainBinding` and an `isOverflowed: (Int) -> Boolean` predicate.
>
> Give it `fun populate(popup: PopupMenu): Int`, which adds one item per evicted command to group 0 with order 0 - so the block sorts above the programs items, which use orders 1 to 10 - and returns how many it added. Walk the commands in bar order: `btnExit`, `btnAddResource`, `btnFilter`, `btnRefresh`, `btnSettings`, `btnToggleView`, `btnFavorites`, `btnStartPlayer`, each with the string resource `MainLayoutChromeManager.updateToolbarButtonLabels` already uses for its label and the same icon the button carries.
>
> Use each command's own view id as the menu item id. That id space cannot collide with `MainProgramsMenuCoordinator`'s small integer constants, and it makes dispatch a lookup rather than a second mapping table to keep in sync.
>
> Give it `fun handleMenuItem(itemId: Int): Boolean` that resolves the item id back to its bar button and calls `performClick()` on it, returning false for an unknown id. Route through the button rather than duplicating each command's action: the button keeps its click listener while it is `GONE`, so the menu entry and the bar entry cannot drift apart.

**Why:**

Owner decision 2 states the evicted command moves into the "⋮" menu as it does on the file-browse screen and that no command may become unreachable, and the strategic §4 check requires the menu to contain exactly the evicted commands.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandOverflowMenuManager.kt` exists.
- `Grep` - `class MainCommandOverflowMenuManager` matches exactly once.
- `Grep` - `fun populate(` and `fun handleMenuItem(` each present once.
- `Grep` - `performClick()` present.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 03 in one pass: MainCommandOverflowMenuManager (60 LOC) builds the evicted-command block from the chrome managers own label list and routes a tap through the hidden button; MainActivity appends the block after the programs items (which clear the menu first), suppresses the programs block when the panel is on, routes overflow ids before the programs coordinator, and shows the anchor when either source has an item. Greps: class 1, populate 1, handleMenuItem 1, performClick 1, Log.d 0; MainActivity mentions manager 3x, hasOverflow 1, onOverflowChanged 1, isProgramsPanelEnabled 6, 1462 LOC (< 1500). Deviation from the written prompt: the manager takes the chrome manager rather than a bare isOverflowed lambda, so the label list has exactly one owner. a.ps1 dq exit 0, APK v2.60.8151.612.

---

### Step 03.2 - Show the evicted commands in the main-window popup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `showMainWindowDropdownMenu()`, build the programs items first as today, then call the new manager's `populate()` and add its count to the item count that decides whether the popup opens at all. When the programs panel is enabled the programs items stay suppressed exactly as now - clear the menu and let the overflow block be the popup's whole content.
>
> Extend `handleMainWindowMenuItem(itemId)` to try `MainCommandOverflowMenuManager.handleMenuItem(itemId)` before the programs coordinator, and construct the manager where the other main-window menu managers are constructed in `setupViews()`.

**Why:**

`MainProgramsMenuCoordinator.populate()` begins by clearing the popup, so an overflow block added before it would be discarded; ordering the calls this way is what lets one anchor host both sets, which owner decision 2 requires.

**Verification:**

- `Grep` - `MainCommandOverflowMenuManager` appears in `MainActivity.kt` at least twice (construction plus use).
- `Grep` - `populateMainWindowDropdownMenu` still present (programs path intact).
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 03 in one pass: MainCommandOverflowMenuManager (60 LOC) builds the evicted-command block from the chrome managers own label list and routes a tap through the hidden button; MainActivity appends the block after the programs items (which clear the menu first), suppresses the programs block when the panel is on, routes overflow ids before the programs coordinator, and shows the anchor when either source has an item. Greps: class 1, populate 1, handleMenuItem 1, performClick 1, Log.d 0; MainActivity mentions manager 3x, hasOverflow 1, onOverflowChanged 1, isProgramsPanelEnabled 6, 1462 LOC (< 1500). Deviation from the written prompt: the manager takes the chrome manager rather than a bare isOverflowed lambda, so the label list has exactly one owner. a.ps1 dq exit 0, APK v2.60.8151.612.

---

### Step 03.3 - Make the anchor's visibility follow the overflow set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Change `refreshMainWindowDropdownMenuVisibility()` so the three-dots button is shown when the existing S0755 condition holds - programs panel off and at least one programs item - or when at least one command is currently evicted. Both the `layoutMainDropdownMenu` wrapper and `btnMainDropdownMenu` follow that one condition, as they do today.
>
> Wire `MainLayoutChromeManager`'s `onOverflowChanged` callback to call this method so the button appears the moment a command is evicted and disappears again when the whole row fits.

**Why:**

Strategic §3 item 3 requires the menu to appear exactly when it holds at least one item and to disappear when the row fits whole; without this the S0755 rule would keep the anchor hidden whenever the programs panel is on, stranding every evicted command.

**Verification:**

- `Grep` - `isOverflowed` or the overflow-count call present inside `refreshMainWindowDropdownMenuVisibility`.
- `Grep` - `onOverflowChanged` present in `MainActivity.kt`.
- `Grep` - `isProgramsPanelEnabled` still referenced in that method (S0755 rule retained).
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 03 in one pass: MainCommandOverflowMenuManager (60 LOC) builds the evicted-command block from the chrome managers own label list and routes a tap through the hidden button; MainActivity appends the block after the programs items (which clear the menu first), suppresses the programs block when the panel is on, routes overflow ids before the programs coordinator, and shows the anchor when either source has an item. Greps: class 1, populate 1, handleMenuItem 1, performClick 1, Log.d 0; MainActivity mentions manager 3x, hasOverflow 1, onOverflowChanged 1, isProgramsPanelEnabled 6, 1462 LOC (< 1500). Deviation from the written prompt: the manager takes the chrome manager rather than a bare isOverflowed lambda, so the label list has exactly one owner. a.ps1 dq exit 0, APK v2.60.8151.612.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `MainActivity.kt` is still under 1500 LOC (CLAUDE.md Rule 2).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Every command is reachable in both orientations and in `layout-w600dp`, because all three variants share this one code path and the anchor button they already carry. The on-device confirmation on the live Samsung is the ticket's remaining gate.

---

## Rollback Plan

Revert the phase commit - one new file plus wiring lines in `MainActivity`; restore that file from the timestamped backup taken before the edit if it went wrong.
