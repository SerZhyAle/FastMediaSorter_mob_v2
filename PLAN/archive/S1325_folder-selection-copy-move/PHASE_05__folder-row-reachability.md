# Phase 05 - Folder row reachability

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Give a folder row the same entry points as a file row - checkbox, long-press selection, range selection, overflow button, direct operation buttons - in all three view holders, with a tap still navigating into the folder.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `BrowseItemOperationPolicy` exists.
- [ ] Read `research/03__current-state-directory-ops.md` - it lists every `isDirectory` branch to change.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt` | Modified | ≤ 1340 |

`MediaFileAdapter.kt` is 1294 LOC - back it up into `temp/S1325/` before editing and keep the delta small; it must stay under the 1500-line ceiling. If the edit would push it past ~1400, extract the row-visibility decisions into a helper under `ui/browse/helpers/` instead of growing the adapter.

Layout files are not touched: `item_media_file.xml`, `item_media_file_grid.xml` and `item_media_file_grid_no_thumb.xml` already declare `cbSelect` and `btnOverflowMenu`, and none of the three has a `res/layout-land/` counterpart - landscape variant absent, not needed.

---

## Steps

### Step 05.1 - Back up the adapter

**Files:** `temp/S1325/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `MediaFileAdapter.kt` into `temp/S1325/` with a timestamped name before editing.

**Verification:**

- `Glob` - `temp/S1325/*MediaFileAdapter*.kt` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 1/1 PASS. Backup taken before any edit; adapter was 1294 LOC at that point.

---

### Step 05.2 - Show the checkbox for folder rows

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In all three `applySelectionVisual` implementations replace `binding.cbSelect.isVisible = !isFolder` with a call to `BrowseItemOperationPolicy.isSelectable(file)`, and apply the checked state and the listener for every selectable item instead of only for non-folders. In the three `selectionCheckedChangeListener` bodies drop the `if (!file.isDirectory)` gate and call `onSelectionChanged` for any selectable item.

**Verification:**

- `Grep` - `cbSelect.isVisible = !isFolder` returns zero hits.
- `Grep` - `BrowseItemOperationPolicy.isSelectable(` matches at least three times in the adapter.
- `Grep` - inside the three `OnCheckedChangeListener` blocks, `if (!file.isDirectory)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `cbSelect.isVisible = !isFolder` 0 hits, `BrowseItemOperationPolicy.isSelectable(` 10 hits, no `isDirectory` gate left in any checked-change listener. The checked state and the listener are now applied for every selectable row, so a folder's checkbox survives the `PAYLOAD_SELECTION` partial rebind like a file's.

---

### Step 05.3 - Let a long press select a folder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> In the list view holder's root and thumbnail long-click listeners, replace `if (!file.isDirectory) onFileLongClick(file)` with a policy-gated call so a folder starts range selection like a file. Do the same for the checkbox long-click range handlers in the two grid holders. The single-tap behaviour stays untouched: `bindFileTypeClick` still routes a directory to `onFolderClick`.

**Verification:**

- `Grep` - `if (!file.isDirectory) onFileLongClick(file)` returns zero hits.
- `Grep` - `!file.isDirectory && !binding.cbSelect.isChecked` returns zero hits.
- `Grep` - `file.isDirectory -> onFolderClick(file)` still matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Old long-click and range gates 0 hits each; `file.isDirectory -> onFolderClick(file)` still 1 hit, so a single tap keeps opening the folder while a long press now starts selection.

---

### Step 05.4 - Show the row's operation controls for folders

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> In each view holder's bind, stop folding `isFolder` into `shouldHideActions` and `useOverflow`. Decide each control from the policy: the overflow button follows `fileOpsInOverflowMenu` alone; copy, move, rename and delete buttons follow the policy plus the existing writability and settings gates; favourite, inline play and drag handle keep their current non-folder gating, expressed through the policy rather than an inline `isDirectory` test.

**Verification:**

- `Grep` - `fileOpsInOverflowMenu && !isFolder` returns zero hits.
- `Grep` - `(isGridMode && hideGridActionButtons) || isFolder` returns zero hits.
- `Grep` - `BrowseItemOperationPolicy.supports(` matches at least three times in the adapter.
- `Grep` - `Log.d(` returns zero hits in the adapter.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS. `fileOpsInOverflowMenu && !isFolder` 0 hits, `(isGridMode && hideGridActionButtons) || isFolder` 0 hits, `BrowseItemOperationPolicy.supports(` 5 hits, `Log.d(` 0 hits. Folder rows now show the overflow button and the copy/move/rename/delete buttons; favourite, inline play and the drag handle stay off through the policy rather than an inline type test.

---

### Step 05.5 - State the folder role for accessibility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Set a content description on the row checkbox that names the item and whether it is a folder, so TalkBack announces the type together with the selection state. Reuse existing string resources if one already covers "folder"; add a trilingual key through `set-android-string.ps1 -Action add` only if none exists, and run the string audit afterwards.

**Verification:**

- `Grep` - `contentDescription` present on the checkbox binding in all three holders; record the line numbers.
- If a key was added: it matches exactly once in each of the three `strings.xml` files and `check_strings_localized.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. No existing key covered "folder <name>, select", so `browse_row_folder_checkbox` and `browse_row_file_checkbox` were added in EN/RU/UK; `check_strings_localized.ps1 -KeyPrefix browse_row_` - expected: 0 | actual: 0. `cbSelect.contentDescription` set in all three holders, so TalkBack announces the type and the item name alongside the checkbox state.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode CodeAndResources` BUILD SUCCESSFUL, exit 0 (resources included because new string keys are referenced).
- [x] `MediaFileAdapter.kt` is under 1500 lines - expected: < 1500 | actual: 1321.
- [x] `Grep` for `TODO(phase-05)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added via `post-change.ps1` closure (entry at 02:11:59; the run then failed on the pre-existing `LongParameterList` finding described above).
- 2026-07-31 - Detekt on the adapter, two findings, handled differently because only one was mine:
  - `ImportOrdering` was mine - the two new helper imports landed in an already unsorted block, which resurfaces the file-level baseline signature. Fixed properly by sorting the whole import block (project imports alphabetically, then `java.*`); re-run is clean of it.
  - `LongParameterList 24/10` on the constructor is not mine and not fixable here: the baselined signature ends at `disableThumbnails`, so it drifted when S0783 added the three favicon parameters. I changed no constructor parameter. Left as is - re-baselining a project-wide artifact from inside this ticket would bury another change's debt under this one.
- 2026-07-31 - Dev log written (`dev/CHANGELOG.md`, 02:11:59) before the detekt gate failed, so the entry exists; the closure's other gates all passed.

- [x] Phase-boundary audit run - Layers 1-3. `PAYLOAD_SELECTION` path re-checked: `applySelectionVisual` now sets visibility, checked state, content description and the listener for every selectable row, so a recycled folder row rebinds correctly; the listener is detached before the checked state is written, so a rebind cannot fire a spurious selection event. No P0/P1.

---

## Handoff Notes to Next Phase

Folder rows are now selectable and expose the overflow button. Phase 06 decides what that menu contains and how a folder-bearing selection reaches a destination.

---

## Rollback Plan

Revert phase commit(s) - view-binding changes only, no persisted state, no schema.
