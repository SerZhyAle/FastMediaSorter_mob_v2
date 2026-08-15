# Research: root cause of cloud internal ID leaking into browser title/breadcrumb

**Ticket:** S0906
**Item:** strategic §6 item 1

---

## Method

5 parallel read-only research passes: browser title-building code, player title-building code, `MediaFile`/`MediaResource` domain model properties, Google Drive REST mapper, Dropbox/OneDrive REST mappers. Then one synthesis pass merging all 5 into a single root-cause diagnosis.

## Finding 1 - player title is NOT affected

`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt:219`:

```kotlin
val displayName = file.title?.takeIf { it.isNotBlank() } ?: file.name
```

`file.title` is only ever populated for internet-stream playback (channel name, S0590); for every cloud file it stays `null`, so `displayName` always resolves to `file.name` - the real file name. Used at `:220` (`binding.toolbar.title`) and `:236`/`:239` (`tvFileNameOverlay.text`). No id fallback anywhere in this path.

`StandalonePlayerActivity.kt` and `PhotoVideoStandaloneActivity.kt` handle external `ACTION_VIEW` intents (open-with), not in-app cloud browsing, and use `mediaFile.name` directly - also clean.

## Finding 2 - all 3 cloud REST mappers are correct

- Google Drive: `GoogleDriveRestClientUtils.kt:27-28` - `id = item.getString("id")`, `name = item.getString("name")` - separate fields, `getString` throws if `name` is missing (no silent id-fallback).
- OneDrive: `OneDriveRestClientUtils.kt:57-58` - same pattern against Graph API DriveItem JSON.
- Dropbox: `DropboxClientUtils.kt:128/139/150` - `name = metadata.name` (SDK field), `id = metadata.pathDisplay ?: metadata.pathLower ?: ""` - never merged.
- `CloudMediaScanner.kt:140,170` forwards `cloudFile.name` verbatim into `MediaFile.name`; `cloudFile.id` only becomes `MediaFile.cloudItemId` (`:148,178`), never a name substitute.

Conclusion: the id never contaminates `name` anywhere in the mapping pipeline, for any of the 3 providers.

## Finding 3 - the actual bug: browse-screen breadcrumb/title builder assumes hierarchical local paths

Both consumers independently try to derive a folder's display name by string-manipulating `currentPath` relative to `resource.path`:

1. `BrowseUtilityManager.kt:104-107` (`buildBreadcrumb`, feeds `tvResourceInfo` via `buildResourceInfo:73` -> `BrowseStateUiUpdater.kt:173`):
   ```kotlin
   if (!currentPath.startsWith(rootPath)) { return currentPath }
   ```
2. `BrowseNavigationManager.kt:317-334` (`getBreadcrumbParts`, feeds `BreadcrumbView` via `BrowseManagerInitializer.kt:948-949`):
   ```kotlin
   } else {
       return Pair(resource.name, listOf(File(currentPath).name))
   }
   ```
3. Same fallback pattern duplicated in `getCurrentBreadcrumb()` (`:280-288`) and `getBreadcrumbPath()` (`:296-312`) - both exposed via `BrowseViewModel.kt:755,927` but no call site found under `ui/browse/**` (candidate dead code, not actioned by this ticket).

For local/SMB/FTP/SFTP resources, `resource.path` and `currentPath` are real hierarchical filesystem paths, so `currentPath.startsWith(rootPath)` succeeds and splitting the remainder by `/`/`\` happens to yield real folder names (a path segment IS the folder name there).

For cloud resources, both `resource.path` and per-item `path` are flat, unrelated id strings:

- Google Drive root: `path = "cloud://google_drive/${folder.id}"` - `GoogleDriveFolderPickerViewModel.kt:154` (name at `:155` correctly uses `folder.name`).
- OneDrive root: `path = "cloud://onedrive/${folder.id}"` - `OneDriveFolderPickerViewModel.kt:142`.
- Dropbox root: `path = "cloud://dropbox${folder.id}"` - `DropboxFolderPickerViewModel.kt:150`.
- Per-item path when scanning a cloud folder: `path = "cloud://$provider/${cloudFile.id}"`, `name = cloudFile.name` - `CloudMediaScanner.kt:139-141` (folder branch), `:169-171` (file branch).

Since a cloud subfolder's id has no relationship to its parent's id, `currentPath.startsWith(rootPath)` is false the moment the user descends past the resource root - the fallback branch fires every time, and `File(currentPath).name` on `"cloud://google_drive/1a2B3c..."` yields the raw id.

The real name (`cloudFile.name`) IS available on the `MediaFile` at scan time - it is simply discarded once `navigateToFolder(folder: MediaFile)` (`BrowseNavigationManager.kt:90-108`) uses it only to build the new `currentPath` (`:103`, `folder.path`), never storing the name itself anywhere in `BrowseState`.

Existing correct-use precedent for the same underlying data already in the codebase (proves the fix pattern, just not wired into these breadcrumb builders): `MediaFile.cloudDisplayPath` (human-readable cloud path, `Models.kt:298`) is consulted in `FileInfoFileSectionHelper.kt:26,94` and `MediaFilePathDescriptor.kt:22-29` (`cloudDisplayPath ?: path`).

## Finding 4 - `BrowseState` has no name-tracking, only path-tracking

`BrowseState.kt:29-30`:

```kotlin
val currentPath: String? = null
val pathStack: List<String> = emptyList()
```

No parallel "name at this depth" field exists. This is the structural gap the fix closes.

## Conclusion (synthesis)

1. Bug lives in the display/breadcrumb layer (`BrowseUtilityManager.kt:104-107`, `BrowseNavigationManager.kt:280-334`), not in any REST mapper or the player.
2. Affects all 3 cloud providers identically - the bug is generic string-prefix logic, provider-agnostic.
3. One shared root cause, two files, effectively duplicated logic - both independently re-derive a name from `currentPath` instead of using the already-known `MediaFile.name` captured at navigation time.
4. Minimal fix: track real folder names alongside the existing path stack in `BrowseState`, populated at the one point a real `MediaFile` is available (`navigateToFolder(folder: MediaFile)`), and have both breadcrumb consumers read that name list instead of re-deriving it from path strings.

## Finding 5 - second manifestation at resource-root level (added during tactical planning)

`BrowseUtilityManager.kt:85-98` (`buildRootPathDisplay`, the branch of `buildResourceInfo` taken whenever `isSubfolderMode` is false or `currentPath` is null - i.e. every cloud resource before the user ever enters a subfolder):

```kotlin
private fun buildRootPathDisplay(resourcePath: String, resourceName: String): String {
    val normalizedPath = resourcePath.trimEnd('/', '\\')
    if (normalizedPath.isEmpty()) { return resourcePath }
    val lastSegment = normalizedPath.substringAfterLast('/').substringAfterLast('\\')
    if (!lastSegment.equals(resourceName, ignoreCase = true)) { return resourcePath }
    val parentPath = normalizedPath.substringBeforeLast('/', "").substringBeforeLast('\\', "")
    return if (parentPath.isNotBlank()) parentPath else resourcePath
}
```

For a local resource (`resourcePath = "/storage/emulated/0/DCIM"`, `resourceName = "DCIM"`), the last path segment equals the resource name, so the method returns the parent directory - a sensible "where is this resource located" display. For a cloud resource (`resourcePath = "cloud://google_drive/<folderId>"`, `resourceName` = the real folder name), the last path segment is the raw internal id, which never equals the resource's real name - the `!lastSegment.equals(resourceName, ...)` guard is always true, so the method returns `resourcePath` **unchanged**, i.e. the raw `cloud://provider/<id>` string, shown directly in `tvResourceInfo` the moment the user opens the resource, before ever touching a subfolder.

This is the same underlying assumption-mismatch as Finding 3 (path segment == folder name, true for local, false for cloud), just triggered at depth 0 instead of depth >= 1. `BrowseState.isCloudResource` (`BrowseState.kt:24`, set at `BrowseResourceLoadManager.kt:171` via `resource.type == ResourceType.CLOUD`) is already computed and available to gate this branch cleanly - no new resource-type detection needed.

## Out-of-scope findings surfaced (not actioned by this ticket)

- `BrowseNavigationManager.getCurrentBreadcrumb()` (`:280-288`) and `getBreadcrumbPath()` (`:296-312`) have no found call site under `app_v2/src/main/java` (only exposed via thin `BrowseViewModel.kt:755,927` wrappers) - candidate dead code per Rule 20, pending confirmation against `wear/` and reflection-based test harnesses. Not parked as a separate ticket: this spec's fix already touches both methods (uniform name-stack rewrite applies to all 4 breadcrumb getters), so no separate action is needed either way.
- `OneDriveRestClient.kt:539-549` `copyFile()` builds a synthetic `CloudFile` by hand with `name = newName ?: "copying..."` (a placeholder string, not an id) for the async Graph-copy 202-Accepted response with no body - informational only, unrelated to the listing/display path this ticket fixes, too trivial (short-lived UI placeholder) to warrant its own ticket.
- `BrowseNavigationManager.navigateToDepth(depth: Int)` (`:232-275`) - the handler for clicking an arbitrary (non-adjacent) breadcrumb segment - requires `currentPath.startsWith(resourcePath)` (`:239`) to compute the target path; for cloud resources this is always false, so the method logs a warning (`:242`) and silently no-ops instead of navigating. This is a distinct, deeper bug (broken click-to-navigate targeting, not just wrong display text) that this ticket's display-only fix does not address - parked as a candidate follow-up ticket, not fixed here (out of scope: fixing it requires reconstructing an arbitrary-depth target path for a flat id-based cloud hierarchy, a separate design problem from tracking display names).
