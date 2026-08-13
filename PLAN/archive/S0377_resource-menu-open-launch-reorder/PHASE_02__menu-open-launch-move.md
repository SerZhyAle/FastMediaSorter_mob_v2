# Phase 02 - Menu: Open, Launch, Move to Edge

**Strategic spec:** [`../S0377_resource-menu-open-launch-reorder.md`](../S0377_resource-menu-open-launch-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Add the dropdown-menu items "Open" (top), "Launch" (media storage only, after Open), "Move to very top" and "Move to very bottom"; wire them in both adapter view-holder popup blocks and in `MainActivity`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/resource_item_actions.xml` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +4 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +4 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +4 keys |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 820 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ +12 (delta only) |

> `ResourceAdapter.kt` is projected >500 lines after change → create a timestamped backup in `temp/` before editing (Step 02.3).
> Menu and layout: `res/menu/resource_item_actions.xml` is a menu resource, not a layout - no `layout-land` counterpart applies. No `res/layout/*.xml` is edited in this phase.

---

## Steps

### Step 02.1 - Add trilingual menu strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four new string keys in lockstep across EN/RU/UK using a single call per key: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`. Keys and copy (apply `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist before committing): `resource_menu_open` = EN "Open" / RU "Открыть" / UK "Відкрити"; `resource_menu_launch` = EN "Launch" / RU "Запустить" / UK "Запустити"; `resource_menu_move_to_top` = EN "Move to very top" / RU "Переместить в самый верх" / UK "Перемістити в самий верх"; `resource_menu_move_to_bottom` = EN "Move to very bottom" / RU "Переместить в самый низ" / UK "Перемістити в самий низ". Use `ё` where correct; use `..` not `...`.

**Verification:**

- `Grep` - each of the 4 keys present in all three `strings.xml` files (12 hits total).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_menu_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS: 12 string hits (4 keys x EN/RU/UK), `check_strings_localized.ps1 -KeyPrefix resource_menu_` exit 0, §6 tone OK (imperative action labels). Cyrillic verified intact via Grep (added in-process via UTF-8 wrapper to avoid argv mojibake). Dev log recorded for all 3 strings.xml.

---

### Step 02.2 - Add menu items to resource_item_actions.xml

**Files:** `app_v2/src/main/res/menu/resource_item_actions.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `<item android:id="@+id/action_open_resource" android:title="@string/resource_menu_open" android:icon="@drawable/ic_open_in_browse" />` as the FIRST item in the menu. Immediately after it add `<item android:id="@+id/action_launch_player" android:title="@string/resource_menu_launch" android:icon="@drawable/ic_play_arrow" />` (use an existing play/launch drawable; if none, reuse `ic_open_in_browse` and note it for Phase 03 review). After the existing `action_move_down` item add `action_move_to_top` (`@string/resource_menu_move_to_top`, icon `@drawable/ic_arrow_upward`) and `action_move_to_bottom` (`@string/resource_menu_move_to_bottom`, icon `@drawable/ic_arrow_downward`). Keep existing items unchanged.

**Verification:**

- `Grep` - `action_open_resource`, `action_launch_player`, `action_move_to_top`, `action_move_to_bottom` each present once.
- `Grep` - `action_open_resource` line precedes `action_edit` line (open is first).

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 2/2 PASS: 4 new ids each present once; `action_open_resource` (line 4) precedes `action_edit` (line 12); launch follows open; move-to-top/bottom sit after `action_move_down`, before `action_delete`. Icon deviation: prescriptor named `@drawable/ic_play_arrow` (absent) - used existing `@drawable/ic_play` (standard 24dp play triangle) per the prompt's "use an existing play/launch drawable" allowance; no Phase 03 icon follow-up needed. Dev log recorded.

---

### Step 02.3 - Wire new menu items in both ResourceAdapter popup blocks

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Back up the file to `temp/ResourceAdapter.kt.<timestamp>.bak` first (>500 lines). Add two new constructor callbacks `onMoveToTopClick: (MediaResource) -> Unit` and `onMoveToBottomClick: (MediaResource) -> Unit`. In BOTH popup-menu blocks (grid view-holder near the existing `R.id.action_move_down` handler, and list view-holder likewise): after inflating, set `popup.menu.findItem(R.id.action_launch_player)?.isVisible = isQuickSlideshowEligible(resource)`. In each `setOnMenuItemClickListener` `when`, add: `R.id.action_open_resource -> { onItemClick(resource); true }`, `R.id.action_launch_player -> { onIconClick(resource); true }`, `R.id.action_move_to_top -> { onMoveToTopClick(resource); true }`, `R.id.action_move_to_bottom -> { onMoveToBottomClick(resource); true }`. "Open" reuses the existing `onItemClick`; "Launch" reuses the existing `onIconClick` - do not add new callbacks for those two.

**Verification:**

- `Grep` - `onMoveToTopClick` and `onMoveToBottomClick` each present in the constructor parameter list.
- `Grep -c "R.id.action_open_resource ->"` returns 2 (both view holders).
- `Grep -c "R.id.action_launch_player ->"` returns 2.
- `Grep -c "action_launch_player)?.isVisible"` returns 2.
- `Grep -c "R.id.action_move_to_top ->"` returns 2 and `R.id.action_move_to_bottom ->` returns 2.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 6/6 PASS: onMoveToTopClick/onMoveToBottomClick each in constructor (1); `R.id.action_open_resource ->`, `R.id.action_launch_player ->`, `action_launch_player)?.isVisible`, `R.id.action_move_to_top ->`, `R.id.action_move_to_bottom ->` each = 2 (both view holders); 0 `Log.d(`. File 795 LOC (< 1500). Backup at temp/ResourceAdapter.kt.20260607_040326.bak. "Open" reuses onItemClick, "Launch" reuses onIconClick (no new callbacks). Dev log recorded. Catalog regen deferred to Phase 04.2.

---

### Step 02.4 - Wire adapter callbacks in MainActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In the `ResourceAdapter(...)` constructor call, add `onMoveToTopClick = { resource -> viewModel.moveResourceToTop(resource) }` and `onMoveToBottomClick = { resource -> viewModel.moveResourceToBottom(resource) }`, alongside the existing `onMoveUpClick` / `onMoveDownClick` wiring.

**Verification:**

- `Grep` - `onMoveToTopClick = { resource -> viewModel.moveResourceToTop(resource) }` present once.
- `Grep` - `onMoveToBottomClick = { resource -> viewModel.moveResourceToBottom(resource) }` present once.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 2/2 PASS: both callbacks present once (lines 642/643), wired to existing viewModel.moveResourceToTop/Bottom. Single-line lambda form chosen to match the literal verification predicate. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - consolidated `a.ps1 dq` BUILD SUCCESSFUL 1m35s (temp/build_debug_20260607_040914.log), covers Phases 01-03.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_menu_"` exits 0 (Step 02.1).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to single regen in Phase 04.2.

---

## Handoff Notes to Next Phase

Menu and reorder are functional. Phase 03 adds the visible icon frame; the `isQuickSlideshowEligible` predicate is the shared gate for both the "Launch" item visibility and the icon frame.

---

## Rollback Plan

Revert phase commit(s) - menu and string additions are additive; no data migration. Restore `ResourceAdapter.kt` from the `temp/` backup if needed.
