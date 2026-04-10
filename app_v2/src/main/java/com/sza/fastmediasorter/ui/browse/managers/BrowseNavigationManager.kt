package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Cache entry for directory contents with hash-based validation.
 * Hash is computed from file names, sizes, and modification dates.
 */
internal data class DirectoryCacheEntry(
    val files: List<com.sza.fastmediasorter.domain.model.MediaFile>,
    val contentHash: String,
    val timestamp: Long
)

/**
 * Manages subfolder navigation in the Browse screen.
 *
 * Responsibilities:
 * - Maintain path stack (navigateToFolder, navigateBack, navigateUp).
 * - Cache per-directory file listings with hash-based invalidation.
 * - Provide breadcrumb helpers (getCurrentBreadcrumb, getBreadcrumbPath, etc.).
 * - Enable / disable subfolder navigation mode.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseNavigationManager(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val mediaScannerFactory: MediaScannerFactory,
    /** Cancels any in-flight file-load job and sets shouldStopScan. */
    private val cancelLoad: () -> Unit,
    /** Triggers a full media-files re-scan (flat / recursive). */
    private val onLoadMediaFiles: () -> Unit,
    /** Triggers a full resource reload. */
    private val onLoadResource: () -> Unit
) {
    // ── Directory cache ───────────────────────────────────────────────────────

    private val directoryCache = mutableMapOf<String, DirectoryCacheEntry>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Navigate into [folderPath] (string overload — used by resume logic and internal callers).
     */
    fun navigateToFolder(folderPath: String) {
        val currentPath = stateFlow.value.currentPath ?: stateFlow.value.resource?.path
        Timber.d("BrowseNavigationManager.navigateToFolder: from='$currentPath' to='$folderPath'")

        scope.launch(ioDispatcher) {
            val newStack = stateFlow.value.pathStack.toMutableList()
            if (currentPath != null) newStack.add(currentPath)

            updateState {
                it.copy(
                    currentPath = folderPath,
                    pathStack = newStack,
                    isSubfolderMode = true,
                    mediaFiles = emptyList(),
                    selectedFiles = emptySet()
                )
            }
            loadDirectoryContents(folderPath)
        }
    }

    /**
     * Navigate into [folder] (MediaFile overload — used by UI item click).
     */
    fun navigateToFolder(folder: MediaFile) {
        if (!folder.isDirectory) {
            Timber.w("BrowseNavigationManager.navigateToFolder: not a directory: ${folder.path}")
            return
        }

        val currentPath = stateFlow.value.currentPath
        val newStack = if (currentPath != null) stateFlow.value.pathStack + currentPath else emptyList()

        Timber.d("BrowseNavigationManager.navigateToFolder: ${folder.path}, stack=${newStack.size}")

        cancelLoad()
        updateState {
            it.copy(currentPath = folder.path, pathStack = newStack, isSubfolderMode = true)
        }
        scope.launch(ioDispatcher) {
            loadDirectoryContents(folder.path)
        }
    }

    /**
     * Navigate back to the parent folder.
     * @return `true` if navigation happened; `false` if already at root (activity should finish).
     */
    fun navigateBack(): Boolean {
        val pathStack = stateFlow.value.pathStack
        if (pathStack.isEmpty()) {
            Timber.d("BrowseNavigationManager.navigateBack: at root")
            return false
        }

        cancelLoad()

        val parentPath = pathStack.last()
        val newStack = pathStack.dropLast(1)
        Timber.d("BrowseNavigationManager.navigateBack: → '$parentPath', stack=${newStack.size}")

        scope.launch(ioDispatcher) {
            updateState {
                it.copy(
                    currentPath = if (newStack.isEmpty()) null else parentPath,
                    pathStack = newStack,
                    isSubfolderMode = newStack.isNotEmpty(),
                    mediaFiles = emptyList(),
                    selectedFiles = emptySet()
                )
            }
            loadDirectoryContents(parentPath)
        }
        return true
    }

    /** Returns `true` if the user can navigate upward from the current position. */
    fun canNavigateUp(): Boolean {
        val s = stateFlow.value
        return s.isSubfolderMode &&
            (s.pathStack.isNotEmpty() || (s.currentPath != null && s.currentPath != s.resource?.path))
    }

    /**
     * Navigate up one level.
     * @return `true` if navigation happened.
     */
    fun navigateUp(): Boolean {
        if (!canNavigateUp()) return false

        cancelLoad()

        val stack = stateFlow.value.pathStack
        val newPath = stack.lastOrNull()
        val newStack = if (stack.isNotEmpty()) stack.dropLast(1) else emptyList()

        Timber.d("BrowseNavigationManager.navigateUp: → ${newPath ?: "root"}, stack=${newStack.size}")

        val resource = stateFlow.value.resource
        val finalPath = newPath ?: resource?.path
        val isStillInSubfolderMode = stateFlow.value.isSubfolderMode

        updateState {
            it.copy(
                currentPath = finalPath,
                pathStack = newStack,
                isSubfolderMode = isStillInSubfolderMode
            )
        }
        scope.launch(ioDispatcher) {
            if (finalPath != null) loadDirectoryContents(finalPath)
        }
        return true
    }

    /** Reset subfolder navigation to the resource root and trigger a full recursive scan. */
    fun resetToRoot() {
        if (!stateFlow.value.isSubfolderMode) return
        Timber.d("BrowseNavigationManager.resetToRoot")
        updateState { it.copy(currentPath = null, pathStack = emptyList(), isSubfolderMode = false) }
        onLoadMediaFiles()
    }

    /**
     * Switch into subfolder-navigation mode — loads the resource root directory listing.
     */
    fun enableSubfolderMode() {
        Timber.d("BrowseNavigationManager.enableSubfolderMode")
        val resourcePath = stateFlow.value.resource?.path ?: return

        scope.launch(ioDispatcher) {
            updateState {
                it.copy(
                    isSubfolderMode = true,
                    currentPath = resourcePath,
                    pathStack = emptyList(),
                    mediaFiles = emptyList()
                )
            }
            loadDirectoryContents(resourcePath)
        }
    }

    /**
     * Switch out of subfolder-navigation mode — triggers a full recursive scan.
     */
    fun disableSubfolderMode() {
        Timber.d("BrowseNavigationManager.disableSubfolderMode")
        scope.launch(ioDispatcher) {
            updateState { it.copy(isSubfolderMode = false, currentPath = null, pathStack = emptyList()) }
            onLoadResource()
        }
    }

    /**
     * Returns `true` when the current resource has both `scanSubdirectories` and `showSubfoldersAsItems` enabled.
     */
    fun isSubfolderModeEnabled(): Boolean {
        val resource = stateFlow.value.resource ?: return false
        return resource.scanSubdirectories && resource.showSubfoldersAsItems
    }

    /**
     * Navigate to a specific breadcrumb depth.
     * @param depth 0 = resource root, 1 = first subfolder, etc.
     */
    fun navigateToDepth(depth: Int) {
        if (!stateFlow.value.isSubfolderMode) return

        val resource = stateFlow.value.resource ?: return
        val currentPath = stateFlow.value.currentPath ?: return
        val resourcePath = resource.path

        val relativePath = if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            Timber.w("BrowseNavigationManager.navigateToDepth: path mismatch")
            return
        }

        if (relativePath.isEmpty()) return

        val pathParts = relativePath.split("/", "\\").filter { it.isNotEmpty() }

        val targetPath = when {
            depth == 0 -> resourcePath
            depth in 1..pathParts.size ->
                resourcePath.trimEnd('/', '\\') + "/" + pathParts.take(depth).joinToString("/")
            else -> {
                Timber.w("BrowseNavigationManager.navigateToDepth: invalid depth $depth (max ${pathParts.size})")
                return
            }
        }

        val newStack = if (depth == 0) {
            emptyList()
        } else {
            buildList {
                var buildPath = resourcePath.trimEnd('/', '\\')
                for (i in 0 until depth - 1) {
                    buildPath += "/" + pathParts[i]
                    add(buildPath)
                }
            }
        }

        Timber.d("BrowseNavigationManager.navigateToDepth: depth=$depth → $targetPath, stack=${newStack.size}")
        updateState { it.copy(currentPath = targetPath, pathStack = newStack, isSubfolderMode = true) }
        scope.launch(ioDispatcher) { loadDirectoryContents(targetPath) }
    }

    // ── Breadcrumb helpers ────────────────────────────────────────────────────

    /** Returns the relative path from resource root to current folder (e.g. "Folder/Sub"). */
    fun getCurrentBreadcrumb(): String {
        val resourcePath = stateFlow.value.resource?.path ?: return ""
        val currentPath = stateFlow.value.currentPath ?: return ""
        return if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            File(currentPath).name
        }
    }

    /** Returns the current folder name, or `null` if at root. */
    fun getCurrentFolderName(): String? {
        val currentPath = stateFlow.value.currentPath ?: return null
        return File(currentPath).name
    }

    /** Returns a display string like "Resource / Folder1 / Folder2". */
    fun getBreadcrumbPath(): String {
        val resource = stateFlow.value.resource ?: return ""
        val currentPath = stateFlow.value.currentPath ?: return resource.name

        val resourcePath = resource.path
        val relativePath = if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            return "${resource.name} / ${File(currentPath).name}"
        }

        if (relativePath.isEmpty()) return resource.name

        val pathParts = relativePath.split("/", "\\").filter { it.isNotEmpty() }
        return (listOf(resource.name) + pathParts).joinToString(" / ")
    }

    /**
     * Returns breadcrumb as (resourceName, [folder1, folder2, ...]) for interactive breadcrumb UI.
     */
    fun getBreadcrumbParts(): Pair<String, List<String>> {
        val resource = stateFlow.value.resource ?: return Pair("", emptyList())
        val currentPath = stateFlow.value.currentPath

        if (currentPath == null || currentPath == resource.path) return Pair(resource.name, emptyList())

        val resourcePath = resource.path
        val relativePath = if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            return Pair(resource.name, listOf(File(currentPath).name))
        }

        if (relativePath.isEmpty()) return Pair(resource.name, emptyList())

        val pathParts = relativePath.split("/", "\\").filter { it.isNotEmpty() }
        return Pair(resource.name, pathParts)
    }

    // ── Directory cache management ────────────────────────────────────────────

    /**
     * Invalidate cache for the current path and its parents.
     * Must be called after any file operation (delete, move, rename).
     */
    fun invalidateDirectoryCache() {
        val currentPath = stateFlow.value.currentPath
        if (currentPath != null) {
            directoryCache.remove(currentPath)
            Timber.d("BrowseNavigationManager: invalidated cache for '$currentPath'")
        }
        stateFlow.value.pathStack.forEach { path -> directoryCache.remove(path) }
        if (directoryCache.isNotEmpty()) {
            Timber.d("BrowseNavigationManager: clearing ${directoryCache.size} remaining cache entries")
            directoryCache.clear()
        }
    }

    /** Clear all directory cache entries (called on ViewModel.onCleared). */
    fun clearDirectoryCache() {
        directoryCache.clear()
    }

    /**
     * Reload current subfolder from cache/disk.
     * Used by `syncWithCache()` when returning from the player in subfolder mode.
     */
    fun reloadCurrentSubfolder(path: String) {
        scope.launch(ioDispatcher) { loadDirectoryContents(path) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Load (or cache-hit) the directory listing for [path] and update state.
     */
    suspend fun loadDirectoryContents(path: String) {
        val resource = stateFlow.value.resource ?: return

        val cachedEntry = directoryCache[path]
        if (cachedEntry != null) {
            Timber.d("BrowseNavigationManager.loadDirectoryContents: cache found for '$path', validating…")
            val currentHash = computeDirectoryHash(path)
            if (currentHash != null && currentHash == cachedEntry.contentHash) {
                Timber.d("BrowseNavigationManager.loadDirectoryContents: cache hit (${cachedEntry.files.size} files)")
                withContext(Dispatchers.Main) {
                    updateState { it.copy(mediaFiles = cachedEntry.files, loadingProgress = 0) }
                }
                return
            } else {
                Timber.d("BrowseNavigationManager.loadDirectoryContents: cache invalid — rescanning")
                directoryCache.remove(path)
            }
        }

        setLoading(true)
        updateState { it.copy(loadingProgress = 0) }

        try {
            Timber.d("BrowseNavigationManager.loadDirectoryContents: loading '$path'")

            val scanner = mediaScannerFactory.getScanner(resource.type)
            val supportedTypes = resource.supportedMediaTypes.ifEmpty { MediaType.entries.toSet() }

            val contents = scanner.listDirectoryContents(
                path = path,
                supportedTypes = supportedTypes,
                sizeFilter = null,
                credentialsId = resource.credentialsId,
                showHiddenFiles = false
            )

            val filteredContents = if (resource.scanSubdirectories) contents
            else contents.filter { !it.isDirectory }

            Timber.d("BrowseNavigationManager.loadDirectoryContents: ${contents.size} items → ${filteredContents.size} after filter")

            val contentHash = computeContentHash(filteredContents)
            directoryCache[path] = DirectoryCacheEntry(
                files = filteredContents,
                contentHash = contentHash,
                timestamp = System.currentTimeMillis()
            )

            withContext(Dispatchers.Main) {
                updateState { it.copy(mediaFiles = filteredContents, loadingProgress = 0) }
            }

            if (filteredContents.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage("Folder is empty"))
            }
        } catch (e: Exception) {
            Timber.e(e, "BrowseNavigationManager.loadDirectoryContents: error loading '$path'")
            sendEvent(BrowseEvent.ShowError("Failed to load folder: ${e.message}"))
        } finally {
            setLoading(false)
        }
    }

    private suspend fun computeDirectoryHash(path: String): String? {
        return try {
            val resource = stateFlow.value.resource ?: return null
            val scanner = mediaScannerFactory.getScanner(resource.type)
            val supportedTypes = resource.supportedMediaTypes.ifEmpty { MediaType.entries.toSet() }
            val contents = scanner.listDirectoryContents(
                path = path,
                supportedTypes = supportedTypes,
                sizeFilter = null,
                credentialsId = resource.credentialsId,
                showHiddenFiles = false
            )
            computeContentHash(contents)
        } catch (e: Exception) {
            Timber.w(e, "BrowseNavigationManager: failed to compute directory hash for '$path'")
            null
        }
    }

    private suspend fun computeContentHash(files: List<MediaFile>): String =
        withContext(Dispatchers.Default) {
            var hash = 0x9E3779B97F4A7C15uL
            files.sortedBy { it.name }.forEach { file ->
                val itemMix = mix64(file.name.hashCode().toLong()) xor
                    mix64(file.size) xor
                    mix64(file.createdDate) xor
                    if (file.isDirectory) 0xD6E8FEB86659FD93uL else 0uL
                hash = java.lang.Long.rotateLeft((hash xor itemMix).toLong(), 11).toULong() *
                    0x9E3779B185EBCA87uL
            }
            hash.toString(16)
        }

    private fun mix64(value: Long): ULong {
        var x = value.toULong() + 0x9E3779B97F4A7C15uL
        x = (x xor (x shr 30)) * 0xBF58476D1CE4E5B9uL
        x = (x xor (x shr 27)) * 0x94D049BB133111EBuL
        return x xor (x shr 31)
    }
}
