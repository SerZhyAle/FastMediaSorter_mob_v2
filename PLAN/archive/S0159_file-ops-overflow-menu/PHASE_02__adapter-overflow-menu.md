# Phase 02 — Adapter & Overflow Menu

**Strategic spec:** [`../S0159_file-ops-overflow-menu.md`](../S0159_file-ops-overflow-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add `btnOverflowMenu` to list and grid item layouts, create `BrowseFileOverflowMenuManager` and `menu_file_ops.xml`, update `MediaFileAdapter` to support overflow mode via `setFileOpsInOverflowMenu()` and `onOverflowMenuClick` callback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_media_file.xml` | Modified | ≤ 40 lines |
| `app_v2/src/main/res/layout/item_media_file_grid.xml` | Modified | ≤ 45 lines |
| `app_v2/src/main/res/menu/menu_file_ops.xml` | **New** | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` | **New** | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt` | Modified | ≤ 1150 |

> `MediaFileAdapter.kt` is 1106 LOC → create timestamped backup in `temp/` before editing.
> `item_media_file.xml` has no `layout-land/` counterpart — single file handles both orientations. No landscape change required.
> `item_media_file_grid.xml` has no `layout-land/` counterpart.

---

## Steps

### Step 2.1 — Add `btnOverflowMenu` to the list item layout

**Files:** `app_v2/src/main/res/layout/item_media_file.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `item_media_file.xml`, add an `ImageButton` with `android:id="@+id/btnOverflowMenu"` after the `btnFavorite` declaration. The button must:
> - Size: 32×32 dp, same style as `btnFavorite`.
> - Icon: `@drawable/ic_more_vert`, tinted with `@color/selector_themed_button_tint`.
> - `android:contentDescription="@string/overflow_menu"` (string added in Phase 04, use placeholder key for now).
> - `android:visibility="gone"` by default.
> - `tools:visibility="visible"`.
> - Constrained: `app:layout_constraintEnd_toStartOf="@id/btnCopyItem"`, `app:layout_constraintTop_toBottomOf="@id/tvFileName"`, `app:layout_constraintBottom_toBottomOf="parent"`.
>
> This places it at the same horizontal zone as the op buttons; when ops are GONE and `btnOverflowMenu` is VISIBLE, it sits at the leading edge of the op-button area.

**Verification:**

- `Grep` — `android:id="@+id/btnOverflowMenu"` present in `item_media_file.xml`.
- `Grep` — `ic_more_vert` referenced in `item_media_file.xml`.
- `Grep` — `android:visibility="gone"` in `item_media_file.xml` (matches `btnOverflowMenu` line or near it).

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: `layout/item_media_file.xml` (+1 ImageButton). Dev log recorded.

---

### Step 2.2 — Add `btnOverflowMenu` to the grid item layout

**Files:** `app_v2/src/main/res/layout/item_media_file_grid.xml`
**Depends on:** — start of phase (parallel to 2.1)

**Prompt for developer:**

> In `item_media_file_grid.xml`, inside `flThumbnailContainer`, add an `ImageButton` with `android:id="@+id/btnOverflowMenu"` after the `stubOperations` ViewStub:
> - Size: 32×32 dp, padding 6 dp.
> - Icon: `@drawable/ic_more_vert`, tinted with `@color/selector_themed_button_tint`.
> - `android:contentDescription="@string/overflow_menu"`.
> - `android:layout_gravity="bottom|end"`.
> - `android:visibility="gone"` by default.
> - `tools:visibility="visible"`.
>
> Both `stubOperations` and `btnOverflowMenu` are at `bottom|end`; exactly one is VISIBLE at any time, so they never overlap simultaneously.

**Verification:**

- `Grep` — `android:id="@+id/btnOverflowMenu"` present in `item_media_file_grid.xml`.
- `Grep` — `ic_more_vert` referenced in `item_media_file_grid.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Files: `layout/item_media_file_grid.xml` (+1 ImageButton). Dev log recorded.

---

### Step 2.3 — Create `menu_file_ops.xml`

**Files:** `app_v2/src/main/res/menu/menu_file_ops.xml` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `app_v2/src/main/res/menu/menu_file_ops.xml` with the following items (all visible by default; runtime code hides inapplicable ones):
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <menu xmlns:android="http://schemas.android.com/apk/res/android">
>     <!-- Basic ops — shown/hidden based on permissions -->
>     <item android:id="@+id/action_file_copy"   android:title="@string/copy" />
>     <item android:id="@+id/action_file_move"   android:title="@string/move" />
>     <item android:id="@+id/action_file_rename" android:title="@string/rename" />
>     <item android:id="@+id/action_file_delete" android:title="@string/delete" />
>     <!-- Extended ops divider -->
>     <item android:id="@+id/divider_extended"   android:title="" style="@style/Widget.Material3.PopupMenu.Item" />
>     <!-- Extended: move-up/move-down (grid mode with manual order) -->
>     <item android:id="@+id/action_file_move_up"   android:title="@string/move_up" />
>     <item android:id="@+id/action_file_move_down" android:title="@string/move_down" />
> </menu>
> ```
>
> The strings `copy`, `move`, `rename`, `delete` already exist in `strings.xml`. `move_up` and `move_down` strings are added in Phase 04. Use their string keys now; the build will fail on Phase 04's string step if they are missing.

**Verification:**

- `Glob` — `app_v2/src/main/res/menu/menu_file_ops.xml` exists.
- `Grep` — `action_file_copy` present in `menu_file_ops.xml`.
- `Grep` — `action_file_delete` present in `menu_file_ops.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: `menu/menu_file_ops.xml` (new, 12 lines). Dev log recorded.

---

### Step 2.4 — Create `BrowseFileOverflowMenuManager`

**Files:** `ui/browse/helpers/BrowseFileOverflowMenuManager.kt` (New)
**Depends on:** Step 2.3

**Prompt for developer:**

> Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt`:
>
> ```kotlin
> @dagger.hilt.android.scopes.ActivityScoped
> class BrowseFileOverflowMenuManager @Inject constructor(
>     @dagger.hilt.android.qualifiers.ActivityContext private val context: android.content.Context
> ) {
>     fun showFor(
>         anchor: android.view.View,
>         file: com.sza.fastmediasorter.domain.model.MediaFile,
>         appSettings: com.sza.fastmediasorter.domain.model.AppSettings,
>         isWritable: Boolean,
>         hasDestinations: Boolean,
>         isGridMode: Boolean,
>         onCopy: (com.sza.fastmediasorter.domain.model.MediaFile) -> Unit,
>         onMove: (com.sza.fastmediasorter.domain.model.MediaFile) -> Unit,
>         onRename: (com.sza.fastmediasorter.domain.model.MediaFile) -> Unit,
>         onDelete: (com.sza.fastmediasorter.domain.model.MediaFile) -> Unit,
>         onMoveUp: ((com.sza.fastmediasorter.domain.model.MediaFile) -> Unit)? = null,
>         onMoveDown: ((com.sza.fastmediasorter.domain.model.MediaFile) -> Unit)? = null
>     ) {
>         val popup = androidx.appcompat.widget.PopupMenu(context, anchor)
>         popup.inflate(R.menu.menu_file_ops)
>         // Basic ops visibility — same gates as the direct buttons
>         popup.menu.findItem(R.id.action_file_copy)?.isVisible = hasDestinations && appSettings.enableCopying
>         popup.menu.findItem(R.id.action_file_move)?.isVisible = isWritable && appSettings.enableMoving
>         popup.menu.findItem(R.id.action_file_rename)?.isVisible = isWritable && appSettings.allowRename
>         popup.menu.findItem(R.id.action_file_delete)?.isVisible = isWritable && appSettings.allowDelete
>         // Extended: grid move-up/move-down only in grid mode with manual order callbacks
>         val showGridMoves = isGridMode && onMoveUp != null && onMoveDown != null
>         popup.menu.findItem(R.id.divider_extended)?.isVisible = showGridMoves
>         popup.menu.findItem(R.id.action_file_move_up)?.isVisible = showGridMoves
>         popup.menu.findItem(R.id.action_file_move_down)?.isVisible = showGridMoves
>         popup.setOnMenuItemClickListener { item ->
>             when (item.itemId) {
>                 R.id.action_file_copy   -> { onCopy(file); true }
>                 R.id.action_file_move   -> { onMove(file); true }
>                 R.id.action_file_rename -> { onRename(file); true }
>                 R.id.action_file_delete -> { onDelete(file); true }
>                 R.id.action_file_move_up   -> { onMoveUp?.invoke(file); true }
>                 R.id.action_file_move_down -> { onMoveDown?.invoke(file); true }
>                 else -> false
>             }
>         }
>         popup.show()
>     }
> }
> ```
>
> Use `Timber.d` for any debug logging; do not use `Log.d`.

**Verification:**

- `Glob` — `ui/browse/helpers/BrowseFileOverflowMenuManager.kt` exists.
- `Grep` — `class BrowseFileOverflowMenuManager` matches exactly once.
- `Grep` — `fun showFor` present in `BrowseFileOverflowMenuManager.kt`.
- `Grep` — `Log\.d(` returns zero hits in `BrowseFileOverflowMenuManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. Files: `ui/browse/helpers/BrowseFileOverflowMenuManager.kt` (new, 55 lines). Dev log recorded.

---

### Step 2.5 — Backup and add `fileOpsInOverflowMenu` state to `MediaFileAdapter`

**Files:** `ui/browse/MediaFileAdapter.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> 1. Copy `MediaFileAdapter.kt` to `temp/MediaFileAdapter_<timestamp>.kt.backup`.
> 2. Add a private field after `hideGridActionButtons`:
>    ```kotlin
>    private var fileOpsInOverflowMenu: Boolean = false
>    ```
> 3. Add a public setter alongside `setHideGridActionButtons`:
>    ```kotlin
>    fun setFileOpsInOverflowMenu(enabled: Boolean) {
>        if (this.fileOpsInOverflowMenu != enabled) {
>            this.fileOpsInOverflowMenu = enabled
>            notifyDataSetChanged()
>        }
>    }
>    ```
> 4. Add a constructor parameter for the overflow click callback (insert after the last existing `on*Click` param, before `onFolderClick`):
>    ```kotlin
>    private val onOverflowMenuClick: (MediaFile, android.view.View) -> Unit = { _, _ -> },
>    ```

**Verification:**

- `Glob` — `temp/MediaFileAdapter_*.kt.backup` exists.
- `Grep` — `private var fileOpsInOverflowMenu: Boolean = false` present in `MediaFileAdapter.kt`.
- `Grep` — `fun setFileOpsInOverflowMenu` present in `MediaFileAdapter.kt`.
- `Grep` — `onOverflowMenuClick` present in `MediaFileAdapter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. Backup: `temp/MediaFileAdapter_20260513_163142.kt.backup`. Files: `ui/browse/MediaFileAdapter.kt` (+7 LOC). Dev log recorded.

---

### Step 2.6 — Wire overflow mode into `ListViewHolder.bind()` and `GridViewHolder.bind()`

**Files:** `ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 2.5

**Prompt for developer:**

> In **`ListViewHolder.init {}`**, add a click listener for the new button:
> ```kotlin
> binding.btnOverflowMenu.setOnClickListener {
>     val file = getItemByPosition() ?: return@setOnClickListener
>     onOverflowMenuClick(file, it)
> }
> ```
>
> In **`ListViewHolder.bind()`**, in the section that sets `btnCopyItem.isVisible` / `btnMoveItem.isVisible` / etc. (around line 819), replace the existing visibility block with:
> ```kotlin
> val shouldHideActions = (isGridMode && hideGridActionButtons) || isFolder
> val useOverflow = fileOpsInOverflowMenu && !isFolder
> // Overflow button
> binding.btnOverflowMenu.isVisible = useOverflow
> // Direct op buttons — hide when overflow mode OR standard shouldHideActions rule applies
> btnCopyItem.isVisible = !shouldHideActions && !useOverflow
> btnMoveItem.isVisible = isWritable && !shouldHideActions && !useOverflow
> btnRenameItem.isVisible = isWritable && !shouldHideActions && !useOverflow
> btnDeleteItem.isVisible = isWritable && !shouldHideActions && !useOverflow
> ```
> Leave `btnPlayInline` logic unchanged (play button is never moved to the overflow menu).
>
> In **`GridViewHolder.bind()`**, replace the block that sets `operationsContainer?.isVisible` and the individual button visibility:
> ```kotlin
> val useOverflow = fileOpsInOverflowMenu && !isFolder
> binding.btnOverflowMenu.isVisible = useOverflow
> if (!useOverflow) {
>     val shouldShowAnyOperation = true
>     if (shouldShowAnyOperation) ensureOperationsInflated()
>     operationsContainer?.isVisible = shouldShowAnyOperation && !hideGridActionButtons
>     btnCopyItem?.isVisible = !hideGridActionButtons
>     btnMoveItem?.isVisible = isWritable && !hideGridActionButtons
>     btnRenameItem?.isVisible = isWritable && !hideGridActionButtons
>     btnDeleteItem?.isVisible = isWritable && !hideGridActionButtons
> } else {
>     operationsContainer?.isVisible = false
> }
> ```
>
> In **`GridViewHolder.init {}`**, add a click listener (after `binding.btnFavorite.setOnClickListener`):
> ```kotlin
> binding.btnOverflowMenu.setOnClickListener {
>     val file = getItemByPosition() ?: return@setOnClickListener
>     onOverflowMenuClick(file, it)
> }
> ```

**Verification:**

- `Grep` — `btnOverflowMenu.isVisible = useOverflow` present in `MediaFileAdapter.kt` (at least 2 occurrences — list and grid holders).
- `Grep` — `onOverflowMenuClick(file, it)` present in `MediaFileAdapter.kt` (at least 2 occurrences).
- `Grep` — `Log\.d(` returns zero hits in `MediaFileAdapter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS (2× btnOverflowMenu.isVisible, 2× onOverflowMenuClick, 0× Log.d). Files: `ui/browse/MediaFileAdapter.kt` (+16 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 2.* above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `MediaFileAdapter` now accepts `onOverflowMenuClick: (MediaFile, View) -> Unit`. The callback is wired in Phase 03 (`BrowseManagerInitializer`).
- `BrowseFileOverflowMenuManager` exists and can be injected with `@Inject`.
- Phase 03 connects the adapter callback to the manager and wires `setFileOpsInOverflowMenu()` to the settings observer.

---

## Rollback Plan

Revert phase commit(s). The backup in `temp/` allows manual recovery. No DB migration or DataStore schema change.
