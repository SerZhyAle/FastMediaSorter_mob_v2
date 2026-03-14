s solution is done. Read it, think about it. How else we can check and improve this task?# IMPLEMENTATION PLAN: Virtual Aggregate Folders

**Spec Reference**: `PLAN/SPEC_VIRTUAL_AGGREGATE_FOLDERS.md`  
**Created**: 2026-03-14  
**Status**: Ready for execution  
**Language**: English (code + comments + plan)

---

## TABLE OF CONTENTS

1. [Overview & Approach](#1-overview--approach)
2. [Phase 1 — Constants & Helpers](#phase-1--constants--helpers)
3. [Phase 2 — MediaStore Repository Layer](#phase-2--mediastore-repository-layer)
4. [Phase 3 — LocalMediaScanner Integration](#phase-3--localmediascanner-integration)
5. [Phase 4 — ScanLocalFoldersUseCase Update](#phase-4--scanlocalfoldersusecase-update)
6. [Phase 5 — First-Launch Provisioning](#phase-5--first-launch-provisioning)
7. [Phase 6 — AddResource UI: Manual Add Dialog](#phase-6--addresource-ui-manual-add-dialog)
8. [Phase 7 — Resource Editor: Limited Edit Mode](#phase-7--resource-editor-limited-edit-mode)
9. [Phase 8 — Mass Rescan Warning Dialog](#phase-8--mass-rescan-warning-dialog)
10. [Phase 9 — Visual Differentiation (Icons & Badges)](#phase-9--visual-differentiation-icons--badges)
11. [Phase 10 — String Resources (i18n)](#phase-10--string-resources-i18n)
12. [Phase 11 — IncrementalScanStrategy Guard](#phase-11--incrementalscanstrategy-guard)
13. [Phase 12 — Unit Tests](#phase-12--unit-tests)
14. [Appendix A — Risk Map & Edge Cases](#appendix-a--risk-map--edge-cases)
15. [Appendix B — Files Modified (Summary)](#appendix-b--files-modified-summary)
16. [Appendix C — Complex Use Cases for QA](#appendix-c--complex-use-cases-for-qa)

---

## 1. OVERVIEW & APPROACH

### What this feature does

Add three new virtual aggregate resources — "All Music" (`virtual://all_audio`), "All Videos" (`virtual://all_video`), "All Documents" (`virtual://all_docs`) — alongside the existing "Recent" (`virtual://recent`). These are predefined virtual resources that aggregate **all files of a certain type across the entire device** into a flat list, queried via MediaStore.

### Golden rule: DO NOT BREAK EXISTING FUNCTIONALITY

The most critical constraint is preserving the existing `virtual://recent` behavior and all other resource types (LOCAL, SMB, SFTP, FTP, CLOUD). Every phase includes explicit regression checks.

### Implementation strategy

- **Incremental phases**: Each phase is a self-contained, buildable, testable unit.
- **After each phase**: Build (`.\gradlew.bat assembleStandardDebug`), fix errors, commit.
- **Pattern reuse**: The new virtual paths mirror `virtual://recent` closely. Always use the existing pattern as a template.
- **Flavor safety**: New resources only appear in `standard` and `legacy` flavors. In `lite` and `photos` — controlled by `settings.supportAudio`, `settings.supportVideos`, `BuildConfig.SUPPORT_DOCUMENTS` checks.
- **No DB migration**: `ResourceEntity.path` is a plain string; `virtual://all_*` values work without schema changes.

### Terminology

| Term | Meaning |
|------|---------|
| **Virtual path** | A `virtual://` URI stored in `ResourceEntity.path` (not a real filesystem path) |
| **Aggregate resource** | A virtual resource that queries MediaStore for ALL files of a given type |
| **Provisioning** | Auto-creation of virtual resources on first app launch |

---

## PHASE 1 — Constants & Helpers

**Goal**: Define new constants and a small helper utility for virtual path detection.  
**Risk**: LOW — additive only, no existing code modified.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 1.1 — Add constants to `LocalMediaScanner.kt`

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt`  
**Location**: Lines 37–39 (next to `VIRTUAL_PATH_RECENT` and `RECENT_FILES_LIMIT`)

**Action**: Add the following constants:

```kotlin
const val VIRTUAL_PATH_ALL_AUDIO  = "virtual://all_audio"
const val VIRTUAL_PATH_ALL_VIDEO  = "virtual://all_video"
const val VIRTUAL_PATH_ALL_DOCS   = "virtual://all_docs"
const val VIRTUAL_ALL_FILES_LIMIT = 10_000
```

**Regression check**: Verify `VIRTUAL_PATH_RECENT` and `RECENT_FILES_LIMIT` are unchanged.

### Step 1.2 — Create `VirtualPathUtils.kt` helper

**File** (NEW): `app_v2/src/main/java/com/sza/fastmediasorter/util/VirtualPathUtils.kt`

**Purpose**: Centralized helper for virtual path checks used across multiple layers.

```kotlin
package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_AUDIO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_DOCS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_VIDEO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_RECENT

object VirtualPathUtils {

    /** True for any virtual:// path (recent + aggregates). */
    fun isVirtualPath(path: String): Boolean = path.startsWith("virtual://")

    /** True only for aggregate virtual resources (not "recent"). */
    fun isAggregateVirtualPath(path: String): Boolean =
        path == VIRTUAL_PATH_ALL_AUDIO ||
        path == VIRTUAL_PATH_ALL_VIDEO ||
        path == VIRTUAL_PATH_ALL_DOCS

    /** All four predefined virtual paths. */
    val ALL_VIRTUAL_PATHS = setOf(
        VIRTUAL_PATH_RECENT,
        VIRTUAL_PATH_ALL_AUDIO,
        VIRTUAL_PATH_ALL_VIDEO,
        VIRTUAL_PATH_ALL_DOCS
    )
}
```

**Why a separate file**: This logic is needed in `LocalMediaScanner`, `ScanLocalFoldersUseCase`, `IncrementalScanStrategy`, `ResourceEditorFragment`, `ResourceAdapter`, `ResourceScanCoordinator`, `AddResourceActivity`. A centralized util prevents duplication.

### Step 1.3 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
# Fix any compilation errors
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../LocalMediaScanner.kt" "LocalMediaScanner" "Added VIRTUAL_PATH_ALL_AUDIO/VIDEO/DOCS constants and VIRTUAL_ALL_FILES_LIMIT=10000"
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../VirtualPathUtils.kt" "VirtualPathUtils" "New helper for virtual path detection"
```

---

## PHASE 2 — MediaStore Repository Layer

**Goal**: Add `getAllFilesByTypes()` to the repository interface and implement it.  
**Risk**: MEDIUM — new MediaStore query, but follows existing `getRecentFiles()` pattern exactly.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 2.1 — Add method to `MediaStoreRepository` interface

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/MediaStoreRepository.kt`  
**Location**: After `getRecentFiles()` signature.

```kotlin
/**
 * Query MediaStore for all files matching the given types across all volumes.
 * No bucket_id filtering — returns files from internal storage, SD card, USB drives.
 * No sorting guarantee — files returned in MediaStore's natural order.
 *
 * @param allowedTypes  Set of media types to include (AUDIO, VIDEO, TEXT, PDF, EPUB, etc.)
 * @param limit         Maximum number of files to return
 * @param showHiddenFiles  Whether to include files starting with '.'
 * @return Flat list of MediaFile, up to [limit] entries
 */
suspend fun getAllFilesByTypes(
    allowedTypes: Set<MediaType>,
    limit: Int,
    showHiddenFiles: Boolean = false
): List<MediaFile>
```

### Step 2.2 — Implement in `MediaStoreRepositoryImpl`

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt`  
**Location**: After `getRecentFiles()` implementation.

**Implementation approach**:
1. **Copy** `getRecentFiles()` as a template (lines 127–240 of current impl).
2. **Key differences from `getRecentFiles()`**:
   - No `DATE_MODIFIED DESC` sort — omit sorting entirely (faster query, spec says "no specific ordering").
   - Same limit mechanism (Bundle API for API 26+, SQL LIMIT for older).
   - Same projection (\_ID, DATA, DISPLAY\_NAME, SIZE, DATE\_MODIFIED, MIME\_TYPE, MEDIA\_TYPE, WIDTH, HEIGHT, DURATION).
   - Same volume: `MediaStore.Files.getContentUri("external")` — already covers ALL external volumes.
   - Same trash path filter via `isTrashPath()`.
   - Same type resolution via `resolveType()` + `allowedTypes` filter.
   - Respect `showHiddenFiles` parameter — if false, skip files where name starts with `.`.
3. **Selection query**: Reuse existing `buildSelectionForAllowedTypes()`.

**IMPORTANT nuance for document types**: `buildSelectionForAllowedTypes()` currently handles IMAGE, VIDEO, AUDIO, GIF via MediaStore `MEDIA_TYPE` column. For TEXT, PDF, EPUB — it returns `null` for those enum values. The document types are matched via `resolveType()` (mime + extension) AFTER retrieval.

This means: for `virtual://all_docs` the query WILL return fewer results at WebStore level, but `resolveType()` catches documents via mime type. 

**Developer must check**: Does `buildSelectionForAllowedTypes()` handle document types correctly? If it returns null for TEXT/PDF/EPUB, the query will have no selection clause for docs → it will return ALL files from MediaStore and filter client-side. This is EXTREMELY inefficient for 10,000 limit.

**Recommended fix**: Extend `buildSelectionForAllowedTypes()` to handle document types via MIME_TYPE conditions:
```kotlin
MediaType.TEXT -> "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%')"
MediaType.PDF -> "(${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/pdf')"
MediaType.EPUB -> "(${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/epub+zip')"
```

This ensures database-level filtering and makes the 10,000 limit meaningful.

### Step 2.3 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../MediaStoreRepository.kt" "MediaStoreRepository" "Added getAllFilesByTypes() interface method"
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../MediaStoreRepositoryImpl.kt" "MediaStoreRepositoryImpl" "Implemented getAllFilesByTypes() with multi-volume support and MIME filtering for docs"
```

### Regression check
- Open any existing resource → should still work
- "Recent" virtual resource → should still scan correctly

---

## PHASE 3 — LocalMediaScanner Integration

**Goal**: Add virtual path branches to `scanFolder()`, `getFileCount()`, `isWritable()`.  
**Risk**: MEDIUM — modifying core scanning logic. Must not break `virtual://recent` or real paths.  
**Build gate**: `assembleStandardDebug` must pass + manual test opening existing resources.

### Step 3.1 — Add `scanAllByTypes()` private method

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt`  
**Location**: After `scanRecentFiles()` (line ~112).

**Template**: Follow the exact same pattern as `scanRecentFiles()`:

```kotlin
private suspend fun scanAllByTypes(
    allowedTypes: Set<MediaType>,
    sizeFilter: SizeFilter?,
    showHiddenFiles: Boolean,
    onProgress: ScanProgressCallback?
): List<MediaFile> {
    return try {
        val files = mediaStoreRepository.getAllFilesByTypes(
            allowedTypes = allowedTypes,
            limit = VIRTUAL_ALL_FILES_LIMIT,
            showHiddenFiles = showHiddenFiles
        )

        val filteredFiles = files.filter { file ->
            sizeFilter == null || file.size <= 0L ||
                MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
        }

        onProgress?.onComplete(filteredFiles.size, 0)
        filteredFiles
    } catch (e: Exception) {
        Timber.e(e, "LocalMediaScanner: failed to scan all files by types: $allowedTypes")
        onProgress?.onComplete(0, 1)
        emptyList()
    }
}
```

**Key difference from `scanRecentFiles()`**: No hidden file re-filtering needed — `getAllFilesByTypes()` already handles `showHiddenFiles` parameter. Size filter IS applied at this level (consistent with existing pattern).

### Step 3.2 — Add `docTypesFromSettings()` helper

**File**: Same (`LocalMediaScanner.kt`)  
**Purpose**: Build dynamic set of document MediaTypes from supportedTypes parameter.

```kotlin
private fun docTypesFromSettings(supportedTypes: Set<MediaType>): Set<MediaType> {
    return supportedTypes.filter { it in setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB) }.toSet()
}
```

This filters the `supportedTypes` parameter (which already reflects user settings) to extract only document types. If user disables TEXT in settings, it won't be in `supportedTypes`, so it won't be in the query.

### Step 3.3 — Add branches to `scanFolder()`

**File**: `LocalMediaScanner.kt`  
**Location**: Lines 49–55 (right after the `VIRTUAL_PATH_RECENT` branch).

Add `when` or if-else block:

```kotlin
// Existing:
if (path == VIRTUAL_PATH_RECENT) {
    return@withContext scanRecentFiles(supportedTypes, sizeFilter, showHiddenFiles, onProgress)
}

// NEW — add immediately after:
if (path == VIRTUAL_PATH_ALL_AUDIO) {
    return@withContext scanAllByTypes(setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles, onProgress)
}
if (path == VIRTUAL_PATH_ALL_VIDEO) {
    return@withContext scanAllByTypes(setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles, onProgress)
}
if (path == VIRTUAL_PATH_ALL_DOCS) {
    val docTypes = docTypesFromSettings(supportedTypes)
    return@withContext if (docTypes.isNotEmpty()) {
        scanAllByTypes(docTypes, sizeFilter, showHiddenFiles, onProgress)
    } else {
        onProgress?.onComplete(0, 0)
        emptyList()
    }
}
```

**CRITICAL**: Use the same `return@withContext` pattern as the existing `VIRTUAL_PATH_RECENT` branch. If the method is structured differently (e.g., `when` block), adapt accordingly.

### Step 3.4 — Add branches to `getFileCount()`

**Location**: Lines ~219–232 (next to `VIRTUAL_PATH_RECENT` branch).

```kotlin
// Existing:
if (path == VIRTUAL_PATH_RECENT) {
    return@withContext scanRecentFiles(supportedTypes, sizeFilter, showHiddenFiles, null).size
}

// NEW:
if (path == VIRTUAL_PATH_ALL_AUDIO) {
    return@withContext scanAllByTypes(setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles, null).size
}
if (path == VIRTUAL_PATH_ALL_VIDEO) {
    return@withContext scanAllByTypes(setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles, null).size
}
if (path == VIRTUAL_PATH_ALL_DOCS) {
    val docTypes = docTypesFromSettings(supportedTypes)
    return@withContext if (docTypes.isNotEmpty()) {
        scanAllByTypes(docTypes, sizeFilter, showHiddenFiles, null).size
    } else 0
}
```

### Step 3.5 — Update `isWritable()`

**Location**: Lines ~268–271.

**Current code** checks `path == VIRTUAL_PATH_RECENT`. Change to a broader check:

```kotlin
// BEFORE:
if (path == VIRTUAL_PATH_RECENT) return@withContext false

// AFTER:
if (VirtualPathUtils.isVirtualPath(path)) return@withContext false
```

This covers ALL virtual paths at once (current + future). Non-breaking because `virtual://recent` was already returning `false`.

### Step 3.6 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../LocalMediaScanner.kt" "LocalMediaScanner" "Added scanAllByTypes(), docTypesFromSettings(), branches for virtual://all_audio/video/docs in scanFolder/getFileCount/isWritable"
```

### Regression checklist
- [ ] `virtual://recent` still works (unchanged branch, still returns recent files)
- [ ] Real local paths still scan correctly
- [ ] `isWritable()` for real paths still works

---

## PHASE 4 — ScanLocalFoldersUseCase Update

**Goal**: When user clicks "Scan" in AddResourceActivity, the three new virtual resources appear in the scan results list (if not already added).  
**Risk**: MEDIUM — existing logic for "Недавние" must stay intact.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 4.1 — Add virtual resource blocks

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt`  
**Location**: After the existing "Недавние" block (lines ~42–58).

**Important context**: The existing code creates `MediaResource` with these key fields:
- `id = 0` (autoincrement)
- `type = ResourceType.LOCAL`
- `isWritable = false`
- `isDestination = false`
- `scanSubdirectories = false`
- `supportedMediaTypes` — dynamically from settings

Add three blocks. Example for "All Music":

```kotlin
// AFTER the existing VIRTUAL_PATH_RECENT block:

if (VIRTUAL_PATH_ALL_AUDIO !in existingPaths && settings.supportAudio) {
    resources.add(
        MediaResource(
            id = 0,
            name = context.getString(R.string.virtual_all_music),
            path = VIRTUAL_PATH_ALL_AUDIO,
            type = ResourceType.LOCAL,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isDestination = false,
            destinationOrder = null,
            isWritable = false,
            slideshowInterval = settings.slideshowInterval,
            scanSubdirectories = false,
            supportedMediaTypes = setOf(MediaType.AUDIO),
            sortMode = SortMode.NAME_ASC,
            profile = ResourceProfile.AUDIO_LIBRARY,
            allFiles = false
        )
    )
}
```

For "All Videos":
- `supportedMediaTypes = setOf(MediaType.VIDEO)`
- `profile = ResourceProfile.VIDEO_LIBRARY`
- Condition: `settings.supportVideos`

For "All Documents":
- `supportedMediaTypes` = dynamic: `buildSet { if (settings.supportText) add(TEXT); if (settings.supportPdf) add(PDF); if (settings.supportEpub) add(EPUB) }`
- `profile = ResourceProfile.DOCUMENTS`
- Condition: `docTypes.isNotEmpty()`

### Step 4.2 — Flavor safety

**Check**: The settings flags (`settings.supportAudio`, `settings.supportVideos`, `settings.supportText`, etc.) are already flavor-aware:
- In `lite` flavor: `supportAudio` may be `true` (BuildConfig.SUPPORT_AUDIO=true) but `supportText`/`supportPdf`/`supportEpub` are `false` (BuildConfig.SUPPORT_DOCUMENTS=false).
- In `photos` flavor: `supportAudio=false`, `supportVideos=false`.

**Additional guard**: Wrap the entire block in `BuildConfig.SUPPORT_DOCUMENTS` check for docs, `BuildConfig.SUPPORT_AUDIO` for audio. This prevents virtual resources from even being considered in excluded flavors.

```kotlin
if (BuildConfig.SUPPORT_AUDIO && VIRTUAL_PATH_ALL_AUDIO !in existingPaths && settings.supportAudio) { ... }
if (VIRTUAL_PATH_ALL_VIDEO !in existingPaths && settings.supportVideos) { ... }
if (BuildConfig.SUPPORT_DOCUMENTS && VIRTUAL_PATH_ALL_DOCS !in existingPaths && docTypes.isNotEmpty()) { ... }
```

### Step 4.3 — Import new constants

Add imports for `VIRTUAL_PATH_ALL_AUDIO`, `VIRTUAL_PATH_ALL_VIDEO`, `VIRTUAL_PATH_ALL_DOCS` from `LocalMediaScanner`.

### Step 4.4 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ScanLocalFoldersUseCase.kt" "ScanLocalFoldersUseCase" "Added 3 virtual aggregate resources to scan results with flavor guards"
```

### Regression checklist
- [ ] "Недавние" still appears in scan results when not added
- [ ] "Недавние" does NOT appear when already in existingPaths
- [ ] Real folders still appear in scan results

---

## PHASE 5 — First-Launch Provisioning

**Goal**: On first app launch (empty DB), create all 4 virtual resources with immediate scanning and progress display.  
**Risk**: HIGH — touches the init flow. Must understand existing `WelcomeActivity` → `MainActivity` → `AppStartupInitializer` chain.  
**Build gate**: `assembleStandardDebug` must pass + manual first-launch test.

### Step 5.1 — Architecture decision: Where to provision

**Current first-launch flow** (from research):
1. `MainActivity.onCreate()` → checks `welcomeViewModel.isWelcomeCompleted()`.
2. If NOT completed → redirects to `WelcomeActivity` (permissions, onboarding).
3. After welcome → sets `welcome_completed = true`, navigates to `MainActivity`.
4. `MainActivity` → loads resources via `MainViewModel` → shows resource list.
5. `AppStartupInitializer.initialize()` — runs background housekeeping (cache sync, thumbnail cleanup, etc.).

**Best insertion point**: After welcome is completed AND before resources are shown.

**Two approaches**:

**Option A** (Recommended): Create `ProvisionDefaultResourcesUseCase` and call it from `MainViewModel.init{}` / `loadResources()`:
- Check if DB is empty → if yes, create 4 resources + scan each.
- Show progress on the main screen itself (loading state).
- Pro: Simple. Con: Brief loading state on first launch.

**Option B**: Call from `WelcomeActivity` completion flow:
- After permissions granted → provision + scan → then navigate to MainActivity.
- Pro: User never sees empty main screen. Con: Longer welcome flow.

**Decision for developer**: Use **Option A**. It's simpler and the loading state is natural. The main screen can show a progress indicator during provisioning. This is the same UX as when loading any resource list.

### Step 5.2 — Create `ProvisionDefaultResourcesUseCase`

**File** (NEW): `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt`

```kotlin
package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.*
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Creates the four predefined virtual resources on first app launch.
 * Condition: DB contains zero resources.
 * Each resource is created AND scanned (fileCount populated via scanner).
 */
class ProvisionDefaultResourcesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val addResourceUseCase: AddResourceUseCase
) {
    /**
     * Returns true if provisioning was performed; false if skipped (not first launch).
     */
    suspend operator fun invoke(): Boolean {
        val existingResources = resourceRepository.getAllResources().first()
        if (existingResources.isNotEmpty()) return false  // Not first launch

        val settings = settingsRepository.getSettings().first()
        var displayOrder = 0

        // 1. "Недавние" (Recent)
        createVirtualResource(
            name = context.getString(R.string.recent_media),
            path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
            supportedMediaTypes = settings.getSupportedMediaTypes(),
            profile = ResourceProfile.NONE,
            displayOrder = displayOrder++
        )

        // 2. "Вся музыка" (All Music)
        if (BuildConfig.SUPPORT_AUDIO && settings.supportAudio) {
            createVirtualResource(
                name = context.getString(R.string.virtual_all_music),
                path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
                supportedMediaTypes = setOf(MediaType.AUDIO),
                profile = ResourceProfile.AUDIO_LIBRARY,
                displayOrder = displayOrder++
            )
        }

        // 3. "Все видео" (All Videos)
        if (settings.supportVideos) {
            createVirtualResource(
                name = context.getString(R.string.virtual_all_video),
                path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
                supportedMediaTypes = setOf(MediaType.VIDEO),
                profile = ResourceProfile.VIDEO_LIBRARY,
                displayOrder = displayOrder++
            )
        }

        // 4. "Все документы" (All Documents)
        if (BuildConfig.SUPPORT_DOCUMENTS) {
            val docTypes = buildSet {
                if (settings.supportText) add(MediaType.TEXT)
                if (settings.supportPdf) add(MediaType.PDF)
                if (settings.supportEpub) add(MediaType.EPUB)
            }
            if (docTypes.isNotEmpty()) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_docs),
                    path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
                    supportedMediaTypes = docTypes,
                    profile = ResourceProfile.DOCUMENTS,
                    displayOrder = displayOrder++
                )
            }
        }

        return true
    }

    private suspend fun createVirtualResource(
        name: String,
        path: String,
        supportedMediaTypes: Set<MediaType>,
        profile: ResourceProfile,
        displayOrder: Int
    ) {
        val resource = MediaResource(
            id = 0,
            name = name,
            path = path,
            type = ResourceType.LOCAL,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isDestination = false,
            destinationOrder = null,
            isWritable = false,
            scanSubdirectories = false,
            supportedMediaTypes = supportedMediaTypes,
            sortMode = SortMode.NAME_ASC,
            profile = profile,
            allFiles = false,
            displayOrder = displayOrder
        )
        resourceRepository.addResource(resource)
    }
}
```

**Note**: This creates resources WITHOUT scanning. File counts will be 0. Scanning happens:
- When `ResourceScanCoordinator.scanAllResources()` runs (main screen init).
- Or when user opens a resource (Browse triggers scan on empty cache).

**Alternative** (if spec strictly requires scanned-before-display): The developer can add explicit scan calls after provisioning. However, the simpler approach is to let the existing mass-scan-on-load handle it.

### Step 5.3 — Hook into MainViewModel

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`

Find the resource loading init flow. Add `ProvisionDefaultResourcesUseCase` as a dependency and call it before loading resources:

```kotlin
// In init{} or loadResources():
viewModelScope.launch {
    val provisioned = provisionDefaultResourcesUseCase()
    if (provisioned) {
        // Optionally trigger mass scan to populate file counts:
        // resourceScanCoordinator.scanAllResources(...)
    }
    // Continue loading resources as usual
}
```

**Developer must**: Read `MainViewModel.kt` carefully to find the exact insertion point. Look for where `getAllResources()` is first called or where the resource list is first observed. The provisioning must happen BEFORE the first emission.

### Step 5.4 — Hilt DI registration

The `ProvisionDefaultResourcesUseCase` uses `@Inject constructor` so Hilt should provide it automatically. Verify that `AddResourceUseCase` and all other dependencies are already Hilt-provided.

### Step 5.5 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ProvisionDefaultResourcesUseCase.kt" "ProvisionDefaultResourcesUseCase" "New use case: creates 4 virtual resources on first launch"
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../MainViewModel.kt" "MainViewModel" "Hook ProvisionDefaultResourcesUseCase into init flow"
```

### Regression checklist
- [ ] Existing install with resources → provisioning is SKIPPED (not first launch)
- [ ] Fresh install → 4 virtual resources appear on main screen
- [ ] Resources have correct names, icons, profiles
- [ ] "Recent" still works as before

---

## PHASE 6 — AddResource UI: Manual Add Dialog

**Goal**: Add "Special Virtual Folders" section to the manual-add dialog with 4 buttons.  
**Risk**: MEDIUM — UI layout changes. Must not break existing folder selection logic.  
**Build gate**: `assembleStandardDebug` must pass + visually verify dialog.

### Step 6.1 — Update `dialog_folder_selection.xml`

**File**: `app_v2/src/main/res/layout/dialog_folder_selection.xml`  
**Location**: Add a NEW section at the TOP of the dialog (before "Quick Select Common Folders").

Add:
1. `TextView` section header: `@string/special_virtual_folders`
2. Four `MaterialButton` entries:
   - `btnVirtualRecent` with icon `@drawable/ic_virtual_recent` (or existing icon)
   - `btnVirtualAllMusic` with icon `@drawable/ic_virtual_music`
   - `btnVirtualAllVideo` with icon `@drawable/ic_virtual_video`
   - `btnVirtualAllDocs` with icon `@drawable/ic_virtual_docs`
3. `View` divider after the section.

**Button styling**: Match existing quick-folder buttons (same height, padding, text style). Use `style="@style/Widget.MaterialComponents.Button.OutlinedButton"` or whatever style existing buttons use.

**Disabled state**: Each button can be enabled/disabled programmatically based on whether the virtual path is already in existing resources.

### Step 6.2 — Add `addVirtualResource()` to `AddResourceViewModel`

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt`

Add a new method:

```kotlin
fun addVirtualResource(virtualPath: String) {
    viewModelScope.launch(ioDispatcher + exceptionHandler) {
        setLoading(true)
        try {
            val existingResources = resourceRepository.getAllResources().first()
            if (existingResources.any { it.path == virtualPath }) {
                sendEvent(AddResourceEvent.ShowMessage(
                    context.getString(R.string.virtual_resource_already_added)
                ))
                return@launch
            }

            val settings = settingsRepository.getSettings().first()
            val resource = buildVirtualResource(virtualPath, settings)
            val id = resourceRepository.addResource(resource)

            sendEvent(AddResourceEvent.ResourcesAdded)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add virtual resource: $virtualPath")
            sendEvent(AddResourceEvent.ShowError(e.message ?: "Unknown error"))
        } finally {
            setLoading(false)
        }
    }
}
```

Also add a helper `buildVirtualResource()` that creates the correct `MediaResource` based on virtual path (same logic as Phase 4 / Phase 5).

Also add `existingVirtualPaths: LiveData<Set<String>>` (or `StateFlow`) that the dialog observes to set button enabled/disabled states:

```kotlin
val existingVirtualPaths: StateFlow<Set<String>> = resourceRepository.getAllResources()
    .map { resources -> resources.map { it.path }.filter { VirtualPathUtils.isVirtualPath(it) }.toSet() }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
```

### Step 6.3 — Wire dialog buttons in `AddResourceActivity`

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`  
**Location**: Inside `showFolderSelectionDialog()` (~line 1542).

After inflating the dialog:

```kotlin
// Observe existing virtual paths to set button states
val existingVirtualPaths = viewModel.existingVirtualPaths.value

listOf(
    R.id.btnVirtualRecent   to VIRTUAL_PATH_RECENT,
    R.id.btnVirtualAllMusic to VIRTUAL_PATH_ALL_AUDIO,
    R.id.btnVirtualAllVideo to VIRTUAL_PATH_ALL_VIDEO,
    R.id.btnVirtualAllDocs  to VIRTUAL_PATH_ALL_DOCS
).forEach { (btnId, path) ->
    dialogView.findViewById<MaterialButton>(btnId)?.apply {
        isEnabled = path !in existingVirtualPaths
        if (!isEnabled) {
            text = getString(R.string.virtual_resource_already_added)
        }
        setOnClickListener {
            viewModel.addVirtualResource(path)
            dialog.dismiss()
        }
    }
}
```

**Flavor visibility**: Hide audio/docs buttons based on `BuildConfig`:
```kotlin
if (!BuildConfig.SUPPORT_AUDIO) {
    dialogView.findViewById<View>(R.id.btnVirtualAllMusic)?.visibility = View.GONE
}
if (!BuildConfig.SUPPORT_DOCUMENTS) {
    dialogView.findViewById<View>(R.id.btnVirtualAllDocs)?.visibility = View.GONE
}
```

### Step 6.4 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../AddResourceViewModel.kt" "AddResourceViewModel" "Added addVirtualResource() and existingVirtualPaths flow"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/.../dialog_folder_selection.xml" "dialog_folder_selection" "Added Special Virtual Folders section with 4 buttons"
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../AddResourceActivity.kt" "AddResourceActivity" "Wired virtual folder buttons in showFolderSelectionDialog()"
```

---

## PHASE 7 — Resource Editor: Limited Edit Mode

**Goal**: When editing a virtual resource, hide "Path" and "Supported Media Types" fields. Allow only: name, PIN, slideshow interval, comment.  
**Risk**: MEDIUM — modifying the field schema system. Must not hide fields for non-virtual resource types.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 7.1 — Architecture approach

**Existing mechanism**: `ResourceEditorUseCase.fieldSchema()` delegates to per-type strategies that return a list of `ResourceFieldSchema`. `ResourceEditorFragment.renderFieldSchema()` sets visibility based on which keys are present.

**Two approaches**:

**Option A** (Recommended): Add logic in `ResourceEditorFragment.renderFieldSchema()` to hide PATH and MEDIA_TYPES fields when the loaded resource has a `virtual://` path. This is minimal-impact — no strategy changes needed.

**Option B**: Create a new `LocalVirtualFieldStrategy` that returns a reduced field list. Cleaner architecture but more files to create.

**Decision**: Use **Option A** — smaller change, less risk.

### Step 7.2 — Modify `ResourceEditorFragment.renderFieldSchema()`

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`  
**Location**: `renderFieldSchema()` method (~line 653).

After the existing visibility logic, add:

```kotlin
// Hide path and media types for virtual resources
val currentPath = viewModel.uiState.value.formData.path
if (VirtualPathUtils.isVirtualPath(currentPath)) {
    binding.tilPath.isVisible = false
    binding.tilServerPath.isVisible = false
    // Hide media types section
    binding.layoutMediaTypes?.isVisible = false  // or however the checkboxes container is referenced
    // Hide scanning options (irrelevant for virtual resources)
    binding.layoutScanningOptions?.isVisible = false
}
```

**Developer must**: Read `ResourceEditorFragment.kt` lines 653–700 carefully to identify the exact view IDs for:
- Path input field container
- Media types checkbox section container
- Scanning options section (scanSubdirectories, showHiddenFiles, etc.)

### Step 7.3 — Prevent path modification via ViewModel

**File**: `ResourceFormViewModel.kt`  
**Location**: `onFieldChanged()` method.

Add early-return guard:

```kotlin
fun onFieldChanged(fieldKey: ResourceFieldKey, value: Any?) {
    // Prevent path/mediaTypes changes for virtual resources
    val currentPath = _uiState.value.formData.path
    if (VirtualPathUtils.isVirtualPath(currentPath) && fieldKey in setOf(ResourceFieldKey.PATH, ResourceFieldKey.MEDIA_TYPES)) {
        return  // Silently ignore
    }
    // ... existing logic
}
```

### Step 7.4 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ResourceEditorFragment.kt" "ResourceEditorFragment" "Hide path and media types for virtual resources in edit mode"
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ResourceFormViewModel.kt" "ResourceFormViewModel" "Guard against path/mediaTypes changes for virtual resources"
```

### Regression checklist
- [ ] Edit a LOCAL resource → all fields visible as before
- [ ] Edit an SMB resource → all SMB fields visible as before
- [ ] Edit a virtual resource → only name, PIN, slideshow, comment visible

---

## PHASE 8 — Mass Rescan Warning Dialog

**Goal**: Show a confirmation dialog before mass rescan if aggregate virtual resources are present.  
**Risk**: LOW — additive UI, no logic changes to scanning itself.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 8.1 — Add warning check to `ResourceScanCoordinator`

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt`  
**Location**: Before `scanAllResources()` is called (or at the beginning of the method).

**The decision of WHERE to show the dialog** depends on the caller. The dialog must be shown in the Activity/Fragment (UI layer), not in the coordinator (logic layer).

**Recommended approach**: Add a check method to `ResourceScanCoordinator`:

```kotlin
fun hasAggregateVirtualResources(resources: List<MediaResource>): Boolean {
    return resources.any { VirtualPathUtils.isAggregateVirtualPath(it.path) }
}
```

Then in the caller (likely `MainViewModel` or the Fragment that triggers mass rescan):

```kotlin
// Before calling scanAllResources():
if (resourceScanCoordinator.hasAggregateVirtualResources(resources)) {
    // Show ConfirmationDialog with R.string.rescan_all_virtual_warning_message
    // Only proceed with scanAllResources() on positive confirmation
} else {
    // Proceed directly
    resourceScanCoordinator.scanAllResources(...)
}
```

### Step 8.2 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ResourceScanCoordinator.kt" "ResourceScanCoordinator" "Added hasAggregateVirtualResources() check for mass rescan warning"
```

---

## PHASE 9 — Visual Differentiation (Icons & Badges)

**Goal**: Create custom vector drawables for virtual resources and display them in resource lists.  
**Risk**: LOW — purely visual, no logic changes.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 9.1 — Create vector drawable icons

**Files** (NEW, all in `app_v2/src/main/res/drawable/`):
- `ic_virtual_recent.xml` — clock/history icon (may already exist)
- `ic_virtual_music.xml` — music note + library lines
- `ic_virtual_video.xml` — film reel / video camera
- `ic_virtual_docs.xml` — stacked documents

Use Material Design icon guidelines. 24dp × 24dp. Monochrome (tintable).

### Step 9.2 — Update `ResourceAdapter` icon logic

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` (or wherever icon assignment happens, see research: ~line 220)

In the icon assignment logic, add virtual path checks BEFORE the `resource.type` switch:

```kotlin
val iconRes = when {
    resource.path == VIRTUAL_PATH_RECENT    -> R.drawable.ic_virtual_recent
    resource.path == VIRTUAL_PATH_ALL_AUDIO -> R.drawable.ic_virtual_music
    resource.path == VIRTUAL_PATH_ALL_VIDEO -> R.drawable.ic_virtual_video
    resource.path == VIRTUAL_PATH_ALL_DOCS  -> R.drawable.ic_virtual_docs
    resource.id == -100L                    -> R.drawable.ic_resource_favorites
    else -> when (resource.type) {
        ResourceType.LOCAL -> R.drawable.ic_resource_local
        ResourceType.SMB -> R.drawable.ic_resource_smb
        // ... existing logic
    }
}
```

### Step 9.3 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/" "icons" "Added 4 virtual resource vector drawables"
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../ResourceAdapter.kt" "ResourceAdapter" "Virtual resource icon assignment by path"
```

---

## PHASE 10 — String Resources (i18n)

**Goal**: Add all localized strings for EN, RU, UK.  
**Risk**: LOW — additive only.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 10.1 — Add strings to all three locales

**Files**:
- `app_v2/src/main/res/values/strings.xml` (English)
- `app_v2/src/main/res/values-ru/strings.xml` (Russian)
- `app_v2/src/main/res/values-uk/strings.xml` (Ukrainian)

**Strings to add**:

| Key | EN | RU | UK |
|-----|----|----|-----|
| `virtual_all_music` | All Music | Вся музыка | Вся музика |
| `virtual_all_video` | All Videos | Все видео | Усі відео |
| `virtual_all_docs` | All Documents | Все документы | Усі документи |
| `special_virtual_folders` | Special Folders | Специальные папки | Спеціальні папки |
| `virtual_resource_already_added` | Already added | Уже добавлен | Вже додано |
| `virtual_resource_added` | Resource "%1$s" added | Ресурс «%1$s» добавлен | Ресурс «%1$s» додано |
| `rescan_all_virtual_warning_title` | Rescan All Resources | Ресканирование всех ресурсов | Повторне сканування всіх ресурсів |
| `rescan_all_virtual_warning_message` | The list includes virtual aggregate folders (All Music, All Videos, All Documents). Full rescan may take significant time. Continue? | Список включает виртуальные агрегирующие папки (Вся музыка, Все видео, Все документы). Их полное ресканирование может занять значительное время. Продолжить? | Список включає віртуальні агрегуючі папки (Вся музика, Усі відео, Усі документи). Повне сканування може зайняти значний час. Продовжити? |

### Step 10.2 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values*/strings.xml" "strings" "Added virtual aggregate folder strings in EN/RU/UK"
```

---

## PHASE 11 — IncrementalScanStrategy Guard

**Goal**: Ensure `currentFolderMtime()` doesn't crash on virtual paths.  
**Risk**: LOW — the current code already returns `null` for non-existent paths. But an explicit guard is cleaner and more future-proof.  
**Build gate**: `assembleStandardDebug` must pass.

### Step 11.1 — Add virtual path guard

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/IncrementalScanStrategy.kt`  
**Location**: `currentFolderMtime()`, at the very beginning (~line 135).

```kotlin
fun currentFolderMtime(path: String): Long? {
    // Virtual resources have no folder — always return null to force full scan
    if (VirtualPathUtils.isVirtualPath(path)) return null

    return try {
        val file = File(path)
        if (file.exists() && file.isDirectory) file.lastModified() else null
    } catch (e: SecurityException) {
        StructuredLogger.w(e, "IncrementalScan: cannot read mtime", "path" to path)
        null
    }
}
```

**Why explicit guard**: The current code happens to return `null` because `File("virtual://recent").exists()` is `false`. But this is an implementation detail of `java.io.File`. An explicit check makes intent clear and prevents any future regression if the method is refactored.

### Step 11.2 — Build & Commit

```powershell
.\gradlew.bat assembleStandardDebug
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../IncrementalScanStrategy.kt" "IncrementalScanStrategy" "Explicit virtual path guard in currentFolderMtime()"
```

---

## PHASE 12 — Unit Tests

**Goal**: Cover all new logic with unit tests.  
**Risk**: LOW — test code only.  
**Build gate**: `testStandardDebugUnitTest` must pass.

### Step 12.1 — `VirtualPathUtilsTest`

Test `isVirtualPath()`, `isAggregateVirtualPath()`, `ALL_VIRTUAL_PATHS`.

### Step 12.2 — `LocalMediaScannerTest` additions

- `scanFolder("virtual://all_audio")` → calls `getAllFilesByTypes` with `{AUDIO}`, returns filtered list.
- `scanFolder("virtual://all_video")` → calls `getAllFilesByTypes` with `{VIDEO}`.
- `scanFolder("virtual://all_docs")` → calls `getAllFilesByTypes` with dynamic doc types from settings.
- `scanFolder("virtual://all_docs")` with all doc types disabled → returns empty list.
- `getFileCount("virtual://all_audio")` → returns correct count.
- `isWritable("virtual://all_audio")` → returns `false`.
- `isWritable("virtual://all_video")` → returns `false`.
- Limit test: mock `getAllFilesByTypes` returning 15,000 files → verify only 10,000 returned after limit.

### Step 12.3 — `ScanLocalFoldersUseCaseTest` additions

- Empty `existingPaths` + all types enabled → result list contains "Recent", "All Music", "All Videos", "All Documents" + real folders.
- `VIRTUAL_PATH_ALL_AUDIO` in `existingPaths` → "All Music" NOT in results.
- `settings.supportAudio = false` → "All Music" NOT in results.
- `settings.supportText = false && supportPdf = false && supportEpub = false` → "All Documents" NOT in results.
- Verify no duplication on repeated calls.

### Step 12.4 — `ProvisionDefaultResourcesUseCaseTest`

- Empty DB → 4 resources created (or fewer if types disabled).
- Non-empty DB → returns `false`, no resources created.
- Verify `displayOrder` is 0, 1, 2, 3.
- Verify `profile` values: NONE, AUDIO_LIBRARY, VIDEO_LIBRARY, DOCUMENTS.

### Step 12.5 — `IncrementalScanStrategyTest` addition

- `currentFolderMtime("virtual://all_audio")` → returns `null`.
- `currentFolderMtime("virtual://all_video")` → returns `null`.

### Step 12.6 — Build & Commit

```powershell
.\gradlew.bat testStandardDebugUnitTest
.\scripts\add_to_dev_log.ps1 "app_v2/src/test/.../VirtualPathUtilsTest.kt" "VirtualPathUtilsTest" "New test class for virtual path helpers"
.\scripts\add_to_dev_log.ps1 "app_v2/src/test/.../LocalMediaScannerTest.kt" "LocalMediaScannerTest" "Added tests for virtual aggregate paths"
# ... etc for each test class
```

---

## APPENDIX A — Risk Map & Edge Cases

### High-Risk Areas

| Risk | Mitigation |
|------|-----------|
| Provisioning runs on non-first-launch | Check `existingResources.isNotEmpty()` FIRST |
| `buildSelectionForAllowedTypes()` doesn't handle docs | Extend with MIME_TYPE conditions (Phase 2) |
| `isAudioOnly()` returns false for "All Music" | `supportedMediaTypes = {AUDIO}` + `allFiles = false` → returns `true` ✓ |
| `isWritable()` misses new virtual paths | Use `VirtualPathUtils.isVirtualPath()` instead of hardcoded check (Phase 3) |
| Dialog buttons crash on missing view IDs | Use `?.` safe calls; verify XML IDs match code |
| Mass rescan takes too long with 10K virtual files | Warning dialog (Phase 8); timeout on scan is existing behavior |
| Settings change: user disables audio type | "All Music" stays in DB but fileCount becomes 0 on next rescan — acceptable |
| `docTypesFromSettings()` returns empty set | Handled: return empty list, fileCount = 0 |

### Edge Cases to Verify

1. **Cold start, no permissions yet**: Provisioning creates resources → MediaStore returns empty lists → fileCount = 0, resources still appear.
2. **SD card removed after initial scan**: Next rescan → files from removed volume don't appear → fileCount decreases.
3. **User changes language**: Virtual resource names are stored as user-editable strings in DB — they don't auto-translate. This is consistent with how "Recent" works.
4. **User renames "All Music" to "My Tunes"**: Allowed (name is editable). Path stays `virtual://all_audio`.
5. **DB backup/restore**: Virtual paths are plain strings → survive backup/restore without issues.
6. **Destination check**: Virtual resources have `isDestination = false`. Verify they NEVER appear in destination picker for copy/move operations.

---

## APPENDIX B — Files Modified (Summary)

### New Files
| File | Phase |
|------|-------|
| `util/VirtualPathUtils.kt` | 1 |
| `domain/usecase/ProvisionDefaultResourcesUseCase.kt` | 5 |
| `res/drawable/ic_virtual_music.xml` | 9 |
| `res/drawable/ic_virtual_video.xml` | 9 |
| `res/drawable/ic_virtual_docs.xml` | 9 |
| `res/drawable/ic_virtual_recent.xml` (if not exists) | 9 |
| Test files (Phase 12) | 12 |

### Modified Files
| File | Phase(s) | What changes |
|------|----------|-------------|
| `LocalMediaScanner.kt` | 1, 3 | Constants + `scanAllByTypes()` + branches in `scanFolder()`/`getFileCount()`/`isWritable()` |
| `MediaStoreRepository.kt` | 2 | New interface method `getAllFilesByTypes()` |
| `MediaStoreRepositoryImpl.kt` | 2 | Implementation of `getAllFilesByTypes()` + extend `buildSelectionForAllowedTypes()` for docs |
| `ScanLocalFoldersUseCase.kt` | 4 | 3 new virtual resource blocks |
| `MainViewModel.kt` | 5 | Hook provisioning use case |
| `AddResourceViewModel.kt` | 6 | `addVirtualResource()` + `existingVirtualPaths` flow |
| `AddResourceActivity.kt` | 6 | Wire virtual buttons in dialog |
| `dialog_folder_selection.xml` | 6 | "Special Folders" section |
| `ResourceEditorFragment.kt` | 7 | Hide path/media types for virtual resources |
| `ResourceFormViewModel.kt` | 7 | Guard against field changes for virtual paths |
| `ResourceScanCoordinator.kt` | 8 | `hasAggregateVirtualResources()` method |
| `ResourceAdapter.kt` | 9 | Virtual path icon assignment |
| `strings.xml` (EN/RU/UK) | 10 | 8 new string resources |
| `IncrementalScanStrategy.kt` | 11 | Explicit virtual path guard |

---

## APPENDIX C — Complex Use Cases for QA

### UC-1: Fresh install → First-launch provisioning

**Steps**:
1. Clear app data (or fresh install).
2. Complete Welcome/onboarding screens.
3. Grant storage permissions.
4. Arrive at main resource list.

**Expected**:
- 4 virtual resources visible: Недавние, Вся музыка, Все видео, Все документы.
- File counts may be 0 initially; mass scanning populates them.
- Opening any resource triggers scan with progress.

### UC-2: Delete + Re-add virtual resource

**Steps**:
1. Long-press "Вся музыка" → Delete.
2. Confirm deletion.
3. Tap "+" → "Add manually" → "Special Folders" section.
4. "All Music" button should be enabled. Tap it.
5. Resource re-appears in list.

**Expected**:
- Resource deleted from DB and list.
- "All Music" button enabled in dialog.
- After re-add: resource appears with fileCount = 0, then scan populates it.
- Favorites: any previously favorited audio files are still marked (stored by URI, not resourceId).

### UC-3: Edit virtual resource (limited mode)

**Steps**:
1. Long-press "Все видео" → Edit.
2. Observe form fields.

**Expected**:
- Visible: Name, PIN, Slideshow Interval, Comment.
- NOT visible: Path, Media Types checkboxes, Scan Subdirectories, Show Hidden Files.
- User can rename to "My Videos" → Save → name updated in list.

### UC-4: Flavor `lite` — no virtual aggregate resources

**Steps**:
1. Build `lite` flavor: `.\gradlew.bat assembleLiteDebug`.
2. Fresh install of lite APK.
3. Go through welcome.

**Expected**:
- Only "Недавние" (Recent) appears — no aggregate resources.
- "Add manually" dialog does NOT show "All Music" / "All Documents" buttons.
- "All Videos" may appear (lite supports VIDEO).

### UC-5: User disables all document types in settings

**Steps**:
1. Open Settings → disable TEXT, PDF, EPUB.
2. Return to main screen.
3. Open "Все документы".
4. Pull-to-refresh.

**Expected**:
- After rescan: file list is empty, fileCount = 0.
- Resource is NOT auto-deleted — it stays with 0 files.
- User can re-enable types and rescan to see files again.

### UC-6: Mass rescan with aggregate resources

**Steps**:
1. Have all 4 virtual resources + 3 real folders.
2. Trigger "Rescan All" from main menu.

**Expected**:
- Warning dialog appears mentioning virtual aggregate folders.
- After confirmation: all 7 resources scanned with progress.
- Virtual resources show updated file counts.

### UC-7: 10,000 limit behavior

**Steps**:
1. Device has 15,000+ audio files.
2. Open "Вся музыка".

**Expected**:
- Resource displays exactly 10,000 files (or fewer if size filter applies).
- No crash, no OOM.
- Sort mode works on the 10,000 files displayed.

### UC-8: SD card plugged/unplugged

**Steps**:
1. Have files on SD card and internal storage.
2. Open "Вся музыка" → note file count (e.g., 5,000).
3. Remove SD card.
4. Pull-to-refresh "Вся музыка".

**Expected**:
- File count decreases (e.g., 3,000) — SD card files gone.
- No crash.
- Re-insert SD card → rescan → files reappear.

### UC-9: Virtual resource as source for copy/move

**Steps**:
1. Open "Все видео".
2. Select a file → Copy → choose a real destination folder.

**Expected**:
- Copy proceeds successfully.
- File appears in destination.
- Virtual resource is NOT shown in destination picker.

### UC-10: Player slideshow auto-activation

**Steps**:
1. Open "Вся музыка" (profile = AUDIO_LIBRARY).
2. Tap Play.

**Expected**:
- Player opens with slideshow mode enabled automatically.
- Same for "Все видео" (profile = VIDEO_LIBRARY).
- "Все документы" (profile = DOCUMENTS) → no auto-slideshow.
