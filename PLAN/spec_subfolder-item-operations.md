# Specification: Browse — Subfolder Item Operations (Select / Copy / Move / Rename / Delete)

**Status:** Draft
**Date:** 2026-04-02
**Tier:** 3 — Moderate (4–8h, medium risk)
**Source:** `PLAN/newFolder.md`, item 2

---

## 1. Problem Statement

When `showSubfoldersAsItems` is enabled on a resource, directories appear in the Browse file list as `MediaFile(isDirectory = true)`. Users can long-press to select them, but all operation paths in the Browse pipeline treat every selected item as a regular file:

- `BrowseViewModel.deleteSelectedFiles()` — feeds all paths to `FileOperationUseCase.execute(Delete)`, which calls `moveFile` / `deleteFile` on the path. Directories are not supported by these file-level APIs and the operation silently fails or causes corrupt state.
- Copy and move — orchestrated by `BrowseFileOperationsManager` via `FileOperationUseCase`, same problem.
- Rename — `BrowseDialogHelper` calls back into `viewModel.renameFile()`, which calls `UnifiedFileOperationHandler.executeRename()`. This operates on a single file path with no directory awareness.

The protocol strategies (`LocalOperationStrategy`, `SmbOperationStrategy`, `SftpOperationStrategy`, `FtpOperationStrategy`, `CloudOperationStrategy`) already implement `deleteDirectory`, `copyDirectory`, `moveDirectory`, and `renameDirectory`, but no code in the Browse pipeline routes `isDirectory = true` items to these methods.

---

## 2. Goals

1. Long-press on a directory item in Browse activates selection mode — identical UX to regular files.
2. **Delete** — selected directory items are recursively deleted via the appropriate protocol strategy; works for local, SMB, SFTP, FTP, and cloud resources.
3. **Copy** — selected directory items are recursively copied to the destination resource.
4. **Move** — selected directory items are recursively moved (copy + delete source).
5. **Rename** — a single selected directory item is renamed via the protocol strategy.
6. Confirmation dialogs for delete/copy/move mention "folder and all its contents" when at least one directory is selected.
7. Mixed selections (files + directories) are handled: files follow the existing path, directories follow the new directory path.
8. Cross-protocol directory copy/move returns a clear user-facing error.
9. No new files exceed the 1000-line policy.

Non-goals for this spec:
- Recursive undo of directory operations (undo stays file-only).
- Cross-protocol recursive directory transfer (out of scope; deferred).
- Drag-and-drop reordering of subfolder items.
- Create subfolder — covered in `spec_create-subfolder-menu.md`.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | All resource types |
| `lite`     | ✅ | Local + network resources |
| `photos`   | ✅ | Photo resources with subfolders |
| `legacy`   | ✅ | Same as standard; `minSdk 23` — `File.deleteRecursively()` is available from API 1 |

No `BuildConfig` flag gates this feature. Subfolder mode is gated on `resource.showSubfoldersAsItems`.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | `java.io.File.deleteRecursively()` available. No API concerns. |
| 26+ (standard minSdk) | Default path. |
| 29 (Android 10) | Local directory delete via `File` (non-MediaStore) works for app-accessible paths. |
| 30+ (Android 11) | `MediaStore` batch delete does **not** apply to directories — `File` API must be used directly (already the case in `LocalOperationStrategy.deleteDirectory`). |
| 34+ (Android 14) | No additional directory API constraints. |

### 3.3 Wear OS Impact

No Wear OS changes required. Wear module does not expose Browse screen or subfolder operations.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `BrowseViewModel.deleteSelectedFiles()` | `ui/browse/BrowseViewModel.kt` (3 821 lines) | Collects `selectedFiles` paths → `FileOperationUseCase.execute(Delete)` — no directory routing |
| `BrowseFileOperationsManager` | `ui/browse/managers/BrowseFileOperationsManager.kt` (666 lines) | Copy/move UI orchestration via `FileOperationUseCase` — treats all items as files |
| `BrowseDialogHelper` | `ui/browse/managers/BrowseDialogHelper.kt` | Rename dialog calls `viewModel.renameFile()` — no `isDirectory` branch |
| `UnifiedFileOperationHandler` | `data/transfer/UnifiedFileOperationHandler.kt` (402 lines) | Exposes `executeCopy/executeMove/executeDelete/executeRename/executeCreateDirectory`; no directory-aware variants |
| `FileOperationStrategy` (abstract) | `data/transfer/FileOperationStrategy.kt` | Declares `deleteDirectory`, `copyDirectory`, `moveDirectory`, `renameDirectory` with default `UnsupportedOperationException` |
| `LocalOperationStrategy` | `data/transfer/strategy/LocalOperationStrategy.kt` | Implements `deleteDirectory`, `renameDirectory`, `copyDirectory` (and `moveDirectory` via base default) |
| `SmbOperationStrategy` | `data/transfer/strategy/SmbOperationStrategy.kt` | Implements `deleteDirectory`, `renameDirectory`, `copyDirectory` |
| `SftpOperationStrategy` | `data/transfer/strategy/SftpOperationStrategy.kt` | Implements all four directory methods |
| `FtpOperationStrategy` | `data/transfer/strategy/FtpOperationStrategy.kt` | Implements `deleteDirectory`, `renameDirectory`, `copyDirectory` |
| `CloudOperationStrategy` | `data/transfer/strategy/CloudOperationStrategy.kt` | Implements `deleteDirectory`, `renameDirectory`, `copyDirectory` |
| `MediaFile.isDirectory` | `domain/model/Models.kt` | `true` when item is a directory |

**Key gap:** `UnifiedFileOperationHandler` holds `FileTransferProvider` instances (not `FileOperationStrategy`) and has no directory-routing methods. Browse flows never inspect `MediaFile.isDirectory`.

---

## 5. Proposed Architecture

### 5.1 Directory Methods in UnifiedFileOperationHandler

Add four public suspension methods. Each resolves the correct `FileOperationStrategy` from a strategy registry (see 5.3) and delegates:

```kotlin
/** Delete a directory and all its contents recursively. */
suspend fun executeDeleteDirectory(
    dirPath: String,
    progressCallback: ((deletedCount: Int, total: Int, currentName: String) -> Unit)? = null
): Result<Int>

/** Rename a directory in place (same parent). newName must not contain path separators. */
suspend fun executeRenameDirectory(
    oldPath: String,
    newName: String
): Result<String>

/** Copy a directory tree to destParentPath/dirName. */
suspend fun executeCopyDirectory(
    sourcePath: String,
    destParentPath: String,
    progressCallback: ((copiedCount: Int, total: Int, currentName: String) -> Unit)? = null
): Result<Int>

/** Move a directory tree (copy + delete source). */
suspend fun executeMoveDirectory(
    sourcePath: String,
    destParentPath: String,
    progressCallback: ((movedCount: Int, total: Int, currentName: String) -> Unit)? = null
): Result<Int>
```

Cross-protocol detection (source protocol ≠ destination protocol for copy/move): return `Result.failure` with a clear message — do not attempt cross-protocol directory transfer.

### 5.2 Strategy Registry in UnifiedFileOperationHandler

`UnifiedFileOperationHandler` must gain access to `FileOperationStrategy` instances. Inject a `Map<String, FileOperationStrategy>` alongside the existing provider registry:

```kotlin
@Singleton
class UnifiedFileOperationHandler @Inject constructor(
    private val localProvider: LocalTransferProvider,
    private val tempFileManager: TempFileManager,
    private val progressTracker: ProgressTracker,
    private val errorHandler: FileOperationErrorHandler,
    // NEW:
    private val operationStrategies: Map<String, @JvmSuppressWildcards FileOperationStrategy>
) { ... }
```

The Hilt module in `di/` must bind the map with keys `"local"`, `"smb"`, `"sftp"`, `"ftp"`, `"cloud"` — verify whether such a binding already exists.

`getStrategy(path: String): FileOperationStrategy` resolves by path prefix (same logic as `getProvider(path)`).

### 5.3 New UseCase: DeleteDirectoriesUseCase

Thin domain-layer façade to keep ViewModel dispatch clean:

```kotlin
class DeleteDirectoriesUseCase @Inject constructor(
    private val fileOperationHandler: UnifiedFileOperationHandler
) {
    /**
     * Delete each directory in [directories] recursively.
     * @return Result<Int> — total number of entries deleted across all directories.
     */
    suspend operator fun invoke(
        directories: List<MediaFile>,
        progressCallback: ((deletedCount: Int, total: Int, currentName: String) -> Unit)? = null
    ): Result<Int>
}
```

Iterates directories sequentially, calls `fileOperationHandler.executeDeleteDirectory()`, aggregates counts. On partial failure, logs errors and returns `Result.failure` with an aggregate message.

### 5.4 Directory-Aware Dispatch in BrowseViewModel

`BrowseViewModel` is 3 821 lines — additions must be minimal pure-dispatch code only.

**Delete:** In `deleteSelectedFiles()`, partition the resolved `MediaFile` list before dispatching:

```kotlin
val allFiles = state.value.mediaFiles
val selectedSet = state.value.selectedFiles
val (dirItems, fileItems) = allFiles
    .filter { it.path in selectedSet }
    .partition { it.isDirectory }

// fileItems → existing FileOperationUseCase.execute(Delete) path (unchanged)
// dirItems  → deleteDirectoriesUseCase(dirItems)
```

Results from both paths are merged for the UI message.

**Rename:** Add `fun renameDirectory(path: String, newName: String)` alongside the existing `renameFile`:

```kotlin
fun renameDirectory(path: String, newName: String) {
    viewModelScope.launch(ioDispatcher + exceptionHandler) {
        val result = fileOperationHandler.executeRenameDirectory(path, newName)
        withContext(Dispatchers.Main) {
            result.onSuccess { loadResource() }
                  .onFailure { e -> sendEvent(BrowseEvent.ShowError(e.message ?: "Rename failed")) }
        }
    }
}
```

**Copy/Move:** Dispatch handled in `BrowseFileOperationsManager` (Section 5.5).

### 5.5 Directory-Aware Dispatch in BrowseFileOperationsManager

After destination is selected, partition selection:

```kotlin
val (dirItems, fileItems) = resolvedFiles.partition { it.isDirectory }
// fileItems → existing FileOperationUseCase path
// dirItems  → per-item call to fileOperationHandler.executeCopyDirectory / executeMoveDirectory
```

If `fileItems` and `dirItems` both exist, run both operations and aggregate results.

### 5.6 Rename Dialog Branch in BrowseDialogHelper

`showRenameDialog(file: MediaFile)` — add a single branch on `file.isDirectory`:

```kotlin
.setPositiveButton(android.R.string.ok) { _, _ ->
    val newName = inputEdit.text.toString().trim()
    if (file.isDirectory) {
        callbacks.onDirectoryRenameConfirmed(oldPath = file.path, newName = newName)
    } else {
        callbacks.onRenameConfirmed(oldName = file.name, newName = newName)
    }
}
```

Add `onDirectoryRenameConfirmed(oldPath: String, newName: String)` to `DialogCallbacks`. Implementation calls `viewModel.renameDirectory(oldPath, newName)`.

### 5.7 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `DeleteDirectoriesUseCase.kt` | `domain/usecase/` | ≤ 70 |

### 5.8 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | BrowseActivity delegates to ViewModel + Managers |
| Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel` | ✅ | `DeleteDirectoriesUseCase` |
| Data flow `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | ViewModel → UseCase → UnifiedFileOperationHandler → FileOperationStrategy |
| No `Log.d()` — Timber only | ✅ | |
| Room schema: version incremented | N/A | No DB changes |
| `StateFlow` / `SharedFlow` | ✅ | Uses existing `BrowseEvent` |
| Hilt DI: new bindings in module file | ⚠️ | Must verify / add `Map<String, FileOperationStrategy>` binding in `di/`; add `DeleteDirectoriesUseCase` (auto-resolved via `@Inject`) |

---

## 6. Data Flow

### 6.1 Delete Selected Directory Items

```
User selects folders → Action bar "Delete"
  → BrowseViewModel.deleteSelectedFiles()
  → partition(isDirectory): dirItems / fileItems
  → fileItems → FileOperationUseCase.execute(Delete)  [existing path, unchanged]
  → dirItems  → DeleteDirectoriesUseCase(dirItems)
                → UnifiedFileOperationHandler.executeDeleteDirectory(dir.path)
                   → getStrategy(dir.path)
                   → FileOperationStrategy.deleteDirectory(path, callback)
                ← Result<Int> (entries deleted)
  → removeFiles(dirPaths + filePaths) + sendEvent(ShowMessage)
```

### 6.2 Rename Directory Item

```
User selects 1 folder → Action bar "Rename"
  → BrowseDialogHelper.showRenameDialog(file)  // file.isDirectory == true
  → callbacks.onDirectoryRenameConfirmed(oldPath, newName)
  → BrowseViewModel.renameDirectory(oldPath, newName)
  → UnifiedFileOperationHandler.executeRenameDirectory(oldPath, newName)
     → getStrategy(oldPath)
     → FileOperationStrategy.renameDirectory(oldPath, newPath)
  ← Result<String> (newPath)
  → loadResource()
```

### 6.3 Copy/Move Directory Items

```
User selects folders → Action bar "Copy" / "Move"
  → BrowseFileOperationsManager.showDestinationPicker()
  → user picks destination resource
  → partition(isDirectory): dirItems / fileItems
  → fileItems → existing FileOperationUseCase path
  → dirItems  → per item:
                UnifiedFileOperationHandler.executeCopyDirectory(src, destParent)
                  [or executeMoveDirectory for Move]
                  → getStrategy(src) — must match getStrategy(destParent)
                  → if cross-protocol: Result.failure("Cross-protocol folder copy not supported")
                  → else: FileOperationStrategy.copyDirectory / moveDirectory
  ← Result<Int> per directory
  → aggregate + sendEvent(ShowMessage / ShowError)
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `data/transfer/UnifiedFileOperationHandler.kt` | Add 4 directory methods + strategy map injection (~65 lines) | ~470 lines |
| `di/` (relevant Hilt module) | Add `Map<String, FileOperationStrategy>` binding if absent | minor |
| `ui/browse/BrowseViewModel.kt` | Partition delete; add `renameDirectory()` (~40 lines net) | ~3 860 lines |
| `ui/browse/managers/BrowseFileOperationsManager.kt` | Partition copy/move + dispatch dirItems (~55 lines) | ~720 lines |
| `ui/browse/managers/BrowseDialogHelper.kt` | `isDirectory` branch in rename callback; add `onDirectoryRenameConfirmed` to interface | ~minor |
| `app_v2/src/main/res/values/strings.xml` | 3 new strings (see §13 step 6) | minor |
| `app_v2/src/main/res/values-ru/strings.xml` | RU mirrors | minor |
| `app_v2/src/main/res/values-uk/strings.xml` | UK mirrors | minor |

> **Backup rule:** `BrowseViewModel.kt` is 3 821 lines. Create `temp/BrowseViewModel_backup_<YYYYMMDD_HHmm>.kt` before editing.  
> `BrowseFileOperationsManager.kt` is 666 lines — create backup before editing.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| `BrowseViewModel` grows further past 1 000-line rule | High | All new logic lives in `DeleteDirectoriesUseCase` and `UnifiedFileOperationHandler`; ViewModel gets ≤ 40 lines of dispatch only |
| `UnifiedFileOperationHandler` does not currently hold `FileOperationStrategy` instances | Medium | Verify DI graph; inject strategy map in constructor; update Hilt module |
| Cloud `copyDirectory` returns `UnsupportedOperationException` for some providers | Medium | Catch `UnsupportedOperationException` in `executeCopyDirectory` and return `Result.failure` with a user-visible message |
| Cross-protocol directory copy silently corrupts state | Low | Explicit cross-protocol check before delegation; return error before touching FS |
| Recursive delete on large tree causes long blocking | Low | All strategy `deleteDirectory` implementations run on `Dispatchers.IO`; add progress toast for tree >50 items |
| Undo not available for directory delete | Low | Show toast "Folder deleted — undo not available" (separate from file undo snackbar) |

---

## 9. Testing Plan

### 9.1 Unit Tests

| Test Class | Key Scenarios |
|-----------|--------------|
| `DeleteDirectoriesUseCaseTest` (new) | Single directory: success → count returned; strategy throws → `Result.failure`; multiple directories: partial failure → error message aggregated |
| `UnifiedFileOperationHandlerDirectoryTest` (new) | `executeDeleteDirectory` calls `strategy.deleteDirectory`; `executeRenameDirectory` calls `strategy.renameDirectory`; `executeCopyDirectory` with same-protocol → delegates; cross-protocol → `Result.failure` |

### 9.2 Manual Test Cases

**Local resource (primary verification):**
1. Long-press a directory item (`isDirectory = true`) → selection mode activates. ✓
2. Select 1 directory → tap Delete → confirmation dialog says "Delete folder and all its contents". Confirm → folder (and contents) removed from list. ✓
3. Select 1 directory → tap Rename → enter new name → folder appears with new name in list. ✓
4. Select 1 directory → tap Copy → pick destination → destination now contains folder with full contents. ✓
5. Select 1 directory → tap Move → pick destination → folder gone from source, present in destination with contents. ✓
6. Select 3 directories → Delete → all three removed. ✓
7. Select mixed set (1 file + 1 directory) → Copy → both appear in destination. ✓

**Network/cloud:**
8. SMB resource: delete selected subfolder → SMB share reflects deletion. ✓
9. SFTP resource: rename selected subfolder → rename applied on server. ✓
10. Cloud (Google Drive): delete selected subfolder → folder gone from Drive. ✓

**Error states:**
11. Select directory on cloud resource → Copy to different cloud provider → error "Cross-protocol folder copy/move is not supported." ✓
12. **Error state:** Simulate `deleteDirectory` failure (e.g., server disconnect) → error toast shown; list unchanged. ✓

### 9.3 Maestro E2E (if applicable)

Add (or extend) `maestro/smoke/browse_subfolder_ops.yaml`:
- Long-press directory item → selection activated
- Delete selected directory → item removed from list

Cloud/network directory ops require a dedicated test server; not suitable for Maestro smoke.

---

## 10. Accessibility

Delete confirmation dialog and rename dialog use `MaterialAlertDialogBuilder` — fully TalkBack-accessible. No new visual-only elements. Action-bar items for copy/move/rename/delete are the same as for file selection; content descriptions already localised. No additional accessibility changes required.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): Under **§2 Browsing** — "Select, copy, move, rename, and delete subfolders directly in Browse when 'Show subfolders as items' is enabled; works for local, network (SMB/SFTP/FTP), and cloud resources."
- `docs/FEATURES_RU.md` (RU): В разделе **§2 Просмотр** — "Выбор, копирование, перемещение, переименование и удаление подпапок прямо в Браузере при включённой опции «Показывать подпапки как элементы»; поддерживается для локальных, сетевых (SMB/SFTP/FTP) и облачных ресурсов."
- `docs/FEATURES_UK.md` (UK): У розділі **§2 Перегляд** — "Вибір, копіювання, переміщення, перейменування та видалення підпапок безпосередньо в Браузері при увімкненому налаштуванні «Показувати підпапки як елементи»; підтримується для локальних, мережевих (SMB/SFTP/FTP) та хмарних ресурсів."

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Route directory ops through `UnifiedFileOperationHandler`, not a parallel handler**
- **Decision:** Extend `UnifiedFileOperationHandler` with four directory-specific methods.
- **Alternatives:** (a) Separate `DirectoryOperationHandler`; (b) extend `FileOperationUseCase` with `isDirectory` flag; (c) call strategies directly from ViewModel.
- **Reason:** Single-entry-point principle for all FS mutations; avoids DI proliferation; ViewModel already coupled to `UnifiedFileOperationHandler` indirectly via use cases.

**ADR-2: No undo for directory delete**
- **Decision:** Directory deletion is unrecoverable; show a distinct toast instead of the undo snackbar.
- **Alternatives:** Move entire directory tree to `.trash/`.
- **Reason:** Network/cloud delete is already hard-delete. Moving large trees is slow and introduces complex state. Consistency across resource types is more valuable than local-only recovery.

**ADR-3: Cross-protocol directory copy/move returns an error**
- **Decision:** If source protocol ≠ destination protocol for a directory operation, return `Result.failure` with a user-visible message.
- **Alternatives:** Download tree to temp → upload to destination.
- **Reason:** Unbounded temp storage usage; FTP/SMB/cloud each have failure edge cases mid-transfer; deferred to a dedicated spec.

---

## 13. Implementation Steps

1. **[Handler — DI setup]** Inspect `di/` Hilt modules — determine if `Map<String, FileOperationStrategy>` binding exists.  
   If absent, add it to the appropriate `@Module` file.  
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../di/<ModuleFile>.kt" "<ModuleName>" "Bind Map<String, FileOperationStrategy> for directory operation dispatch"`

2. **[Handler — directory methods]** Add `executeDeleteDirectory`, `executeRenameDirectory`, `executeCopyDirectory`, `executeMoveDirectory` to `UnifiedFileOperationHandler.kt`. Include cross-protocol check in copy/move methods.  
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../data/transfer/UnifiedFileOperationHandler.kt" "UnifiedFileOperationHandler" "Add executeDeleteDirectory, executeRenameDirectory, executeCopyDirectory, executeMoveDirectory"`

3. **[UseCase]** Create `domain/usecase/DeleteDirectoriesUseCase.kt`.  
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../domain/usecase/DeleteDirectoriesUseCase.kt" "DeleteDirectoriesUseCase" "New UseCase: recursively delete directory MediaFile items"`

4. **[ViewModel]** Create backup: `temp/BrowseViewModel_backup_<YYYYMMDD_HHmm>.kt`.  
   In `deleteSelectedFiles()`: partition by `isDirectory`, dispatch `dirItems` to `DeleteDirectoriesUseCase`.  
   Add `fun renameDirectory(path: String, newName: String)`.  
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ui/browse/BrowseViewModel.kt" "BrowseViewModel" "Directory-aware delete/rename dispatch for isDirectory MediaFile items"`

5. **[Copy/Move Manager]** Create backup: `temp/BrowseFileOperationsManager_backup_<YYYYMMDD_HHmm>.kt`.  
   In `BrowseFileOperationsManager`: partition copy/move selection, dispatch `dirItems` to `UnifiedFileOperationHandler`.  
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ui/browse/managers/BrowseFileOperationsManager.kt" "BrowseFileOperationsManager" "Directory-aware copy/move dispatch for isDirectory MediaFile items"`

6. **[Rename dialog]** In `BrowseDialogHelper.showRenameDialog()`, add `isDirectory` branch; add `onDirectoryRenameConfirmed` to `DialogCallbacks` interface.  
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ui/browse/managers/BrowseDialogHelper.kt" "BrowseDialogHelper" "Route directory rename to onDirectoryRenameConfirmed callback"`

7. **[Strings]** Add to `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`:
   - `delete_folder_confirm` — EN: "Delete folder \"%s\" and all its contents? This cannot be undone." / RU: "Удалить папку «%s» и всё её содержимое? Это нельзя отменить." / UK: "Видалити папку «%s» та весь її вміст? Скасувати не можна."
   - `delete_n_folders_confirm` — EN: "Delete %d folders and all their contents? This cannot be undone." / RU: "Удалить %d папок и весь их контент? Это нельзя отменить." / UK: "Видалити %d папок та весь їхній вміст? Скасувати не можна."
   - `error_cross_protocol_dir_not_supported` — EN: "Folder copy/move between different storage types is not supported." / RU: "Копирование/перемещение папок между разными типами хранилищ не поддерживается." / UK: "Копіювання/переміщення папок між різними типами сховищ не підтримується."
   - `msg_folder_renamed` — EN: "Folder renamed" / RU: "Папка переименована" / UK: "Папку перейменовано"  
   Run: `.\scripts\add_to_dev_log.ps1` for each strings file.

8. **[Tests]** Write unit tests for `DeleteDirectoriesUseCase` and `UnifiedFileOperationHandlerDirectoryTest`.  
   Run: `.\gradlew.bat testStandardDebugUnitTest`

9. **[Maestro]** Add/extend `maestro/smoke/browse_subfolder_ops.yaml`.

10. **[Docs]** Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` per Section 11.  
    Run dev log for each.

**Mandatory checklist:**
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated
- [ ] Room DB migration: N/A
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- Cross-protocol recursive directory copy/move (e.g., SMB → Google Drive).
- Undo support for directory delete.
- Progress dialog for long-running recursive directory operations.
- Drag-and-drop subfolder reordering.
- Create subfolder action — covered in `spec_create-subfolder-menu.md`.
