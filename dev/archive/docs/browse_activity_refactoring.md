# BrowseActivity Refactoring Plan

## Current State
- **Total lines**: 2018
- **Total methods**: 62
- **Status**: Монолитный класс с множественной ответственностью
- **Main issues**:
  - Смешаны concerns: UI, бизнес-логика, диалоги, selection management
  - Дублирование логики с PlayerActivity (dialogs, error handling)
  - Сложно поддерживать и тестировать

## Refactoring Strategy: Extract Manager Classes

### Phase 1: Extract Selection Management → `SelectionManager`
**Estimated lines**: ~200-250 lines

**What to extract**:
- Selection state management
- Select all / Clear selection
- Toggle selection
- Multi-selection logic
- Selection UI updates

**Interface**:
```kotlin
class SelectionManager(
    private val callback: SelectionCallback
) {
    interface SelectionCallback {
        fun onSelectionChanged(count: Int)
        fun onSelectionModeChanged(enabled: Boolean)
        fun getSelectedFiles(): List<MediaFile>
    }
    
    fun toggleSelection(file: MediaFile)
    fun selectAll(files: List<MediaFile>)
    fun clearSelection()
    fun isSelected(file: MediaFile): Boolean
    fun getSelectionCount(): Int
}
```

---

### Phase 2: Extract Dialog Management → `BrowseDialogHelper`
**Estimated lines**: ~400-500 lines

**What to extract**:
- Filter dialog (MediaType, date range, file name)
- Sort dialog (Name, Date, Size, Type)
- Delete confirmation dialog
- Rename dialogs (single/multiple)
- Copy/Move destination dialogs
- Cloud authentication dialog
- Error dialogs with details

**Interface**:
```kotlin
class BrowseDialogHelper(
    private val activity: AppCompatActivity,
    private val viewModel: BrowseViewModel,
    private val callback: BrowseDialogCallback
) {
    interface BrowseDialogCallback {
        fun onFilterApplied(filter: FileFilter)
        fun onSortChanged(sortMode: SortMode)
        fun onDeleteConfirmed(files: List<MediaFile>)
        fun onRenameRequested(files: List<MediaFile>, newNames: List<String>)
        fun onCopyRequested(destinationId: Long)
        fun onMoveRequested(destinationId: Long)
        fun onCloudAuthRequested(provider: String)
    }
    
    fun showFilterDialog(currentFilter: FileFilter)
    fun showSortDialog(currentSort: SortMode)
    fun showDeleteConfirmation(count: Int)
    fun showRenameDialog(files: List<MediaFile>)
    fun showCopyDialog()
    fun showMoveDialog()
    fun showCloudAuthDialog(errorMessage: String)
}
```

---

### Phase 3: Extract File Operations → `BrowseFileOperationsHandler`
**Estimated lines**: ~300-350 lines

**What to extract**:
- Execute copy/move/delete operations
- Share files
- Open with external apps
- File operation callbacks
- Progress handling
- Error handling for operations

**Interface**:
```kotlin
class BrowseFileOperationsHandler(
    private val fileOperationUseCase: FileOperationUseCase,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: FileOperationsCallback
) {
    interface FileOperationsCallback {
        fun onOperationStarted()
        fun onOperationSuccess(message: String, filesAffected: List<String>)
        fun onOperationError(message: String, exception: Throwable?)
        fun getCurrentFiles(): List<MediaFile>
        fun getSelectedFiles(): List<MediaFile>
        fun getCurrentResource(): MediaResource?
    }
    
    suspend fun executeCopy(destinationId: Long)
    suspend fun executeMove(destinationId: Long)
    suspend fun executeDelete(files: List<MediaFile>)
    suspend fun executeRename(files: List<MediaFile>, newNames: List<String>)
    fun shareFiles(files: List<MediaFile>)
    fun openFileWith(file: MediaFile)
}
```

---

### Phase 4: Extract Toolbar/Action Management → `BrowseActionBarController`
**Estimated lines**: ~200-250 lines

**What to extract**:
- Toolbar setup
- Search view setup and handling
- Menu item visibility updates
- Action mode (selection) toolbar
- Back button handling
- Display mode toggle (List/Grid)

**Interface**:
```kotlin
class BrowseActionBarController(
    private val activity: AppCompatActivity,
    private val binding: ActivityBrowseBinding,
    private val callback: ActionBarCallback
) {
    interface ActionBarCallback {
        fun onSearchQueryChanged(query: String)
        fun onDisplayModeToggle()
        fun onFilterClicked()
        fun onSortClicked()
        fun onRefreshClicked()
        fun onSelectAllClicked()
        fun onBackPressed()
        fun getSelectionCount(): Int
    }
    
    fun setupToolbar()
    fun setupSearchView()
    fun updateMenuVisibility(hasFiles: Boolean, isSelectionMode: Boolean)
    fun showActionMode(count: Int)
    fun hideActionMode()
    fun updateSearchQuery(query: String)
}
```

---

### Phase 5: Extract RecyclerView Management → `BrowseRecyclerViewManager`
**Estimated lines**: ~250-300 lines

**What to extract**:
- RecyclerView setup (LayoutManager)
- Adapter initialization and binding
- FastScroller setup
- Scroll listeners
- Item click/long click handlers
- Display mode switching (List ↔ Grid)
- Empty state handling

**Interface**:
```kotlin
class BrowseRecyclerViewManager(
    private val binding: ActivityBrowseBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: RecyclerViewCallback
) {
    interface RecyclerViewCallback {
        fun onItemClick(file: MediaFile)
        fun onItemLongClick(file: MediaFile)
        fun onLoadMore()
        fun onScrollStateChanged(isScrolling: Boolean)
        fun getCurrentDisplayMode(): DisplayMode
    }
    
    fun setupRecyclerView(displayMode: DisplayMode)
    fun updateDisplayMode(displayMode: DisplayMode)
    fun scrollToPosition(position: Int)
    fun submitList(files: List<MediaFile>)
    fun notifyDataSetChanged()
}
```

---

### Phase 6: Extract MediaStore Observer → `MediaStoreObserverManager`
**Estimated lines**: ~100-150 lines

**What to extract**:
- MediaStore content observer setup
- File change detection
- Auto-refresh trigger
- Observer lifecycle management

**Interface**:
```kotlin
class MediaStoreObserverManager(
    private val context: Context,
    private val callback: MediaStoreCallback
) : DefaultLifecycleObserver {
    interface MediaStoreCallback {
        fun onMediaStoreChanged()
    }
    
    fun startObserving()
    fun stopObserving()
    override fun onResume(owner: LifecycleOwner)
    override fun onPause(owner: LifecycleOwner)
}
```

---

## Remaining BrowseActivity Responsibilities
**Estimated lines**: ~600-800 lines (manageable)

**What stays**:
- Activity lifecycle (onCreate, onResume, onPause, onDestroy)
- ViewModel observation (state, events)
- Manager initialization and coordination
- Intent handling (resource ID, return results)
- Cloud authentication result handling
- Player activity launcher
- Keyboard shortcut handling

---

## Implementation Order

### Step 1: Create Manager Classes (Empty Shells)
Priority order based on complexity and dependencies:

1. **SelectionManager** (lowest dependencies)
2. **BrowseActionBarController** (UI-only)
3. **BrowseRecyclerViewManager** (UI-only)
4. **MediaStoreObserverManager** (independent)
5. **BrowseDialogHelper** (depends on SelectionManager)
6. **BrowseFileOperationsHandler** (depends on all above)

### Step 2: Incremental Migration
- One manager per session
- Test after each phase
- Atomic git commits

### Step 3: Cleanup
- Remove duplicate code
- Simplify remaining Activity code
- Update tests

---

## Expected Benefits

### Code Quality
- **Size reduction**: 2018 → ~700 lines (-65%)
- **Complexity**: 62 methods → ~20 methods
- **Testability**: Each manager independently testable

### Architecture
- Clear separation of concerns
- Reusable components (dialogs, selection)
- Consistent with PlayerActivity pattern

### Maintainability
- Easier to locate functionality
- Simpler code reviews
- Reduced merge conflicts

---

## Risks & Mitigation

### Risk 1: Selection State Synchronization
**Mitigation**: 
- Use callback pattern for state updates
- Keep single source of truth in ViewModel
- Managers only update UI based on state

### Risk 2: Dialog Dependency Chain
**Mitigation**:
- Dialogs should only depend on ViewModel
- Use LiveData/StateFlow for dialog results
- Avoid manager-to-manager calls

### Risk 3: RecyclerView Performance
**Mitigation**:
- Keep adapter reference in manager
- Use DiffUtil for efficient updates
- Avoid unnecessary adapter recreations

---

## Timeline Estimate

- **Phase 1 (SelectionManager)**: 3-4 hours
- **Phase 2 (BrowseDialogHelper)**: 6-8 hours
- **Phase 3 (BrowseFileOperationsHandler)**: 5-6 hours
- **Phase 4 (BrowseActionBarController)**: 4-5 hours
- **Phase 5 (BrowseRecyclerViewManager)**: 5-6 hours
- **Phase 6 (MediaStoreObserverManager)**: 2-3 hours
- **Testing & Bug Fixes**: 5-7 hours

**Total**: ~30-39 hours of development work

---

## Next Steps

1. ✅ Create refactoring plan (COMPLETED)
2. ⏳ Create empty manager class files with interfaces
3. ⏳ Phase 1: SelectionManager
4. ⏳ Phase 2: BrowseDialogHelper
5. ⏳ Phase 3: BrowseFileOperationsHandler
6. ⏳ Phase 4: BrowseActionBarController
7. ⏳ Phase 5: BrowseRecyclerViewManager
8. ⏳ Phase 6: MediaStoreObserverManager

---

## Comparison with PlayerActivity Refactoring

| Metric | PlayerActivity | BrowseActivity (Estimated) |
|--------|----------------|----------------------------|
| Initial size | 3607 lines | 2018 lines |
| Target size | 2287 lines | ~700 lines |
| Reduction | -36.6% | ~-65% |
| Managers created | 9 classes | 6 classes |
| Development time | ~4 hours | ~30-39 hours |

BrowseActivity is more complex due to:
- Selection management (multi-select)
- More dialogs (Filter, Sort, Rename multiple)
- RecyclerView complexity (List/Grid switching)
- MediaStore observer integration

---

## Notes

- Reuse dialog components from PlayerActivity where possible
- Consider extracting common base classes (BaseDialogHelper)
- Document all callback interfaces clearly
- Use Hilt injection for managers with repository dependencies
