# Phase 02 - List/Grid Menu + Order Consumers

**Strategic spec:** [`../S0938_pinned-stream-reorder.md`](../S0938_pinned-stream-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Surface the reorder commands in the three-dot menu of both the list and grid streams adapters, wire them to `ReorderPinnedStreamUseCase`, and make the list/grid pinned block display in manual (`sortIndex`) order so the commands visibly reorder.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `ReorderPinnedStreamUseCase` + `PinnedStreamMove` grep in the codebase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | + 3 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | + 3 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | + 3 keys |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 900 |

> No layout XML is touched - the reorder items are added to the existing `PopupMenu` built in code, so there is no `res/layout-land` counterpart to mirror. `StreamsActivity.kt` and `StreamsViewModel.kt` are >500 LOC or near it - back each up under `temp/S0938/` before editing (Rule 5).

---

## Steps

### Step 02.1 - Add trilingual reorder menu strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three menu labels in lockstep across EN/RU/UK with a single call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key streams_move_up -En "Move up" -Ru "Вверх" -Uk "Вгору"`, likewise `streams_move_down` = "Move down"/"Вниз"/"Вниз", `streams_move_to_top` = "Move to top"/"В самый верх"/"На самий верх".
> Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (short imperative menu labels, no punctuation, `..` not `...`, Russian ё where grammatical). Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_move_"` and fix any gap.

**Verification:**

- `Grep` - `name="streams_move_up"` present in all three `strings.xml` files.
- `Grep` - `name="streams_move_down"` present in all three.
- `Grep` - `name="streams_move_to_top"` present in all three.
- `check_strings_localized.ps1 -KeyPrefix "streams_move_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 5/5 PASS. Files: res/values{,-ru,-uk}/strings.xml (+3 keys each). Parity OK.

---

### Step 02.2 - ViewModel: reorder intent + manual pinned-block order

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Inject `private val reorderPinnedStream: ReorderPinnedStreamUseCase` (add to the existing `@Suppress("LongParameterList")` constructor). Add `fun onMovePinned(source: StreamSourceEntity, move: PinnedStreamMove) = viewModelScope.launch { reorderPinnedStream(source.id, move) }`.
> In `applyFilter`, stop re-sorting the pinned block by the secondary `SortMode`. Partition `matched` into pinned + unpinned: keep the pinned rows in their incoming order (the source list from `observeAll` is already `sortIndex ASC` within pinned), and apply the `secondary` comparator only to the unpinned rows; return `pinned + unpinnedSorted`. Update `StreamsFilterTest` expectations that assumed the pinned block follows the chosen sort - the contract is now manual order for pinned, chosen sort for unpinned.

**Verification:**

- `Grep` - `reorderPinnedStream: ReorderPinnedStreamUseCase` present in the constructor.
- `Grep` - `fun onMovePinned(` matches once.
- `Grep` - `applyFilter` no longer sorts pinned by secondary (no `compareByDescending<StreamSourceEntity> { it.pinned }.then(secondary)`); a partition of pinned vs unpinned is present.
- `.\a.ps1 fu` - `StreamsFilterTest` passes with the updated pinned-order expectations.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Structural verification 3/3 PASS (constructor dep, onMovePinned, partition). Added `pinned rows keep manual order` test. Test + compile run at phase boundary. Files: StreamsViewModel.kt (+11 LOC), StreamsFilterTest.kt (+14 LOC).

---

### Step 02.3 - List adapter: reorder menu items with edge gating

**Files:** `ui/streams/StreamSourceAdapter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add three callbacks to the constructor with `{}` defaults: `onMoveUp`, `onMoveDown`, `onMoveToTop`, each `(StreamSourceEntity) -> Unit`. In the overflow `PopupMenu`, when `source.pinned` and more than one row is pinned, prepend the three reorder items (`ID_MOVE_UP`, `ID_MOVE_DOWN`, `ID_MOVE_TO_TOP`, new companion int ids) before the favorites/shortcut group. Compute the row's index within the pinned sublist of `currentList` (`currentList.filter { it.pinned }`) and disable "up"/"to top" when it is first, "down" when it is last (`menu.findItem(id).isEnabled = ...`). Route the clicks to the matching callback. Keep the `else -> false` fallthrough.

**Verification:**

- `Grep` - `onMoveUp`, `onMoveDown`, `onMoveToTop` each present in the constructor.
- `Grep` - `ID_MOVE_UP`, `ID_MOVE_DOWN`, `ID_MOVE_TO_TOP` present in the companion.
- `Grep` - `source.pinned` gates the reorder-item block.
- `Grep` - `isEnabled` used to disable an edge command.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS (16 matches). Files: StreamSourceAdapter.kt (+~20 LOC).

---

### Step 02.4 - Grid adapter: mirror the reorder menu items

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror Step 02.3 in the grid adapter's `btnGridOverflow` `PopupMenu`: same three defaulted callbacks, same pinned-gated block with edge disabling, same companion int ids. Keep the menu order identical to the list adapter (reorder group first, then favorite/shortcut/edit/share/remove) so both surfaces read the same.

**Verification:**

- `Grep` - `onMoveUp`, `onMoveDown`, `onMoveToTop` each present in `StreamGridAdapter.kt`.
- `Grep` - `ID_MOVE_UP`, `ID_MOVE_DOWN`, `ID_MOVE_TO_TOP` present in the companion.
- `Grep` - `isEnabled` used for edge gating.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS (15 matches). Files: StreamGridAdapter.kt (+~20 LOC).

---

### Step 02.5 - Activity: wire adapter callbacks to the ViewModel

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.2, Step 02.3, Step 02.4

**Prompt for developer:**

> In both the `StreamSourceAdapter(..)` and lazy `StreamGridAdapter(..)` constructions, wire the three new callbacks: `onMoveUp = { viewModel.onMovePinned(it, PinnedStreamMove.UP) }`, `onMoveDown = { viewModel.onMovePinned(it, PinnedStreamMove.DOWN) }`, `onMoveToTop = { viewModel.onMovePinned(it, PinnedStreamMove.TO_TOP) }`. Import `PinnedStreamMove`. No other Activity logic - this is pure delegation (Rule 3).

**Verification:**

- `Grep` - `onMoveUp = { viewModel.onMovePinned(it, PinnedStreamMove.UP)` present.
- `Grep` - `onMoveDown = { viewModel.onMovePinned(it, PinnedStreamMove.DOWN)` present.
- `Grep` - `onMoveToTop = { viewModel.onMovePinned(it, PinnedStreamMove.TO_TOP)` present twice (list + grid).
- `.\a.ps1 fc` - resources + Kotlin compile clean.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS (6 matches, 3 per adapter). Files: StreamsActivity.kt (+7 LOC, +1 import; backed up to temp/S0938/). fc BUILD SUCCESSFUL, StreamsFilterTest green.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles + resources pass - run `/build` (`.\a.ps1 fc`).
- [ ] `.\a.ps1 fu` - `StreamsFilterTest` green.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Feature is code-complete: reorder commands in list + grid menus, gated to pinned rows and disabled at edges, delegating to `ReorderPinnedStreamUseCase`; the list/grid pinned block now shows manual order, matching the panel and player prev/next. Phase 03 regenerates the catalog, records the capability, and logs the change.

---

## Rollback Plan

Revert the phase commit(s). The `applyFilter` change is the only behavioural shift on an existing screen - reverting restores the prior pinned-follows-sort ordering. No data migration.
