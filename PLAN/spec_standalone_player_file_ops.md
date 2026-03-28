# Specification: III.11 — StandalonePlayer File Operations

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 3 — Moderate (4–8h, medium risk)
**Roadmap entry:** Delete, Share, Favorite, Open-in-Browse actions — Needs file operation infrastructure wiring

---

## 1. Problem Statement

`StandalonePlayerActivity` opens media files from external intents (`ACTION_VIEW`, `ACTION_SEND`) but provides no file management actions. A user who opens a photo from their gallery to view it in FMS cannot delete it, share it, or add it to FMS Favorites without leaving the player. The "Open with" entry point is a dead end unless the app also gives the user a path back into FMS browse.

---

## 2. Goals

1. **Delete** — confirm and remove the current file; finish the Activity on success.
2. **Share** — re-share the file via Android's share sheet (`ACTION_SEND`).
3. **Favorite** — toggle the file in/out of FMS Favorites (star icon, persisted in Room).
4. **Open-in-Browse** — navigate to the parent folder in `BrowseActivity`, pre-scrolled to the file; fall back to `MainActivity` for unresolvable URIs.

Non-goals: rename, copy/move, batch operations, network file support (standalone receives only `content://` and `file://` URIs from external apps).

---

## 3. Context: Standalone vs. Normal Player

| Aspect | `PlayerActivity` | `StandalonePlayerActivity` |
|--------|-----------------|--------------------------|
| Source | FMS resource DB | External intent |
| URI types | `file://`, `smb://`, `sftp://`, `ftp://`, `cloud://` | `content://`, `file://` only |
| File model | `MediaFile` with DB-sourced metadata | Synthetic `MediaFile` constructed from URI + display name |
| Resource context | Present (`MediaResource`, resource ID) | Absent (`resourceId = 0`) |
| Favorites key | `mediaFile.path` (stable file path) | `uri.toString()` (may be ephemeral for some providers) |
| Delete infrastructure | `FileOperationUseCase` via domain layer | Direct `ContentResolver` / `File.delete()` |

---

## 4. Architecture

### 4.1 UI Layer: `StandalonePlayerActivity`

Four buttons in the top command panel (`topCommandPanel`), reusing existing binding IDs:

| Action | Binding ID | Icon |
|--------|-----------|------|
| Delete | `btnDeleteCmd` | `ic_delete` |
| Share | `btnShareCmd` | `ic_share` |
| Favorite | `btnFavorite` | `ic_star_outline` / `ic_star_filled` |
| Open-in-Browse | `btnInfoCmd` (repurposed) | `ic_open_in_browse` |

Prev/next (`btnPreviousCmd`, `btnNextCmd`) are hidden — standalone has no playlist.

All four button handlers live in `StandalonePlayerActivity` (direct Activity methods, not a manager class). The logic is simple enough that a dedicated manager would be over-engineering; total addition is under 120 lines.

### 4.2 ViewModel: `StandalonePlayerViewModel`

Two new concerns added to the existing ViewModel:

```kotlin
// Favorite state
private val _isFavorite = MutableStateFlow(false)
val isFavorite: StateFlow<Boolean>

fun checkFavoriteStatus(uri: String)   // called after loadFromUri
fun toggleFavorite()                   // called by button tap

// Open-in-Browse
suspend fun findResourceForPath(folderPath: String?): Long?
```

**`toggleFavorite()`** delegates to `FavoritesUseCase.toggleFavorite(mediaFile, resourceId = 0L)`.
`resourceId = 0` signals a standalone favorite (not tied to any FMS resource).

**`findResourceForPath()`** queries `ResourceRepository.getAllResourcesSync()` and finds the first `ResourceType.LOCAL` resource whose `path` is a prefix of `folderPath`. Returns `null` if none match.

**Dependencies added to `StandalonePlayerViewModel`:**
- `FavoritesUseCase` (already injected via Hilt)
- `ResourceRepository` (already injected via Hilt)

No new Hilt module changes needed.

---

## 5. Operation Details

### 5.1 Delete

**Dialog:** `MaterialAlertDialogBuilder` confirmation showing the file name.

**Deletion logic (by URI scheme):**

```
content:// URI
  ├─ isDocumentUri(uri)?  →  DocumentsContract.deleteDocument(contentResolver, uri)
  ├─ Android 11+ (API 30) →  MediaStore.createDeleteRequest(contentResolver, listOf(uri))
  │                           launch result via ActivityResultLauncher
  └─ Android 10 (API 29)  →  contentResolver.delete(uri, null, null)
                              catch RecoverableSecurityException → prompt user

file:// URI             →  File(uri.path!!).delete()
```

**Post-delete:** Toast confirmation + `finish()`.

**Failure cases:**
- `SecurityException` without `RecoverableSecurityException`: show toast "Permission denied — delete from the gallery app instead."
- `Exception`: show toast with error message.
- `DocumentsContract.deleteDocument()` returning `false`: show toast "Could not delete file."

**Note:** `DocumentsContract.isDocumentUri()` must be checked before calling `deleteDocument()`. Plain MediaStore URIs (`content://media/...`) are not document URIs and will throw if passed to `deleteDocument()`.

### 5.2 Share

```kotlin
val shareUri = when (uri.scheme) {
    "file" -> FileProvider.getUriForFile(this, "$packageName.fileprovider", File(uri.path!!))
    else   -> uri   // content:// — pass through; read permission was granted by caller
}
val intent = Intent(ACTION_SEND).apply {
    type = contentResolver.getType(uri) ?: "*/*"
    putExtra(EXTRA_STREAM, shareUri)
    addFlags(FLAG_GRANT_READ_URI_PERMISSION)
}
startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
```

**Edge case — `file://` on Android 7+:** Direct `file://` URIs cause `FileUriExposedException` when passed to other apps. FileProvider wraps them as `content://` automatically.

**Edge case — `content://` from app with revoked permission:** If the caller's URI grant expires before the user taps Share, the target app will receive a URI it cannot read. This is an inherent limitation of transient URI grants; no mitigation beyond prompt timing.

### 5.3 Favorite

`FavoritesUseCase.toggleFavorite(mediaFile, resourceId = 0L)` stores the `mediaFile.path` (which equals `uri.toString()` for standalone) as the favorite key.

**Favorite icon:** `binding.btnFavorite` observes `viewModel.isFavorite: StateFlow<Boolean>`.
- Filled star (`ic_star_filled`) when `true`
- Outline star (`ic_star_outline`) when `false`

**Limitation — URI stability:** `content://` URIs from external apps may be session-scoped. A favorite added for `content://com.google.android.apps.photos.contentprovider/.../...` will not be findable in future sessions if the URI changes. This is acceptable for the standalone use case; network-backed and persistent URIs (local file apps, Files by Google) are stable.

**Checking initial state:** `FavoritesUseCase.isFavoriteSync(uri.toString())` is called immediately after `loadFromUri()` resolves the file type.

### 5.4 Open-in-Browse

```
resolveToLocalPath(uri)
  ├─ "file://"      → uri.path
  └─ "content://"   → query(MediaStore.MediaColumns.DATA)
                       → may return null for cloud-backed content

localPath != null
  └─ parentDir = File(localPath).parent
       findResourceForPath(parentDir)
         ├─ resourceId found  → BrowseActivity.createIntent(resourceId, initialFilePath = localPath)
         └─ resourceId null   → MainActivity (resource not added to FMS)

localPath == null
  └─ MainActivity (content URI not resolvable to a local path)
```

After launching the target Activity: `finish()` the `StandalonePlayerActivity`.

**`MediaStore.MediaColumns.DATA`** is deprecated since API 29 but remains functional on all Android versions in our `minSdk` range (API 26–35). For scoped storage purposes, standalone only receives files the user chose to share, so the DATA column is available.

**Fallback to `MainActivity`:**
- URI authority belongs to a cloud app (Google Photos, OneDrive)
- File is in a folder not registered as an FMS resource
- MediaStore query fails

In all fallback cases: launch `MainActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`.

---

## 6. Files to Create / Modify

| File | Change | Size impact |
|------|--------|-------------|
| `StandalonePlayerActivity.kt` | Add `setupFileOperationButtons()`, `deleteCurrentFile()`, `performDelete()`, `shareCurrentFile()`, `openInFms()`, `resolveToLocalPath()`, `launchMainActivity()`; observe `isFavorite` flow | +~120 lines |
| `StandalonePlayerViewModel.kt` | Add `_isFavorite`, `isFavorite`, `checkFavoriteStatus()`, `toggleFavorite()`, `findResourceForPath()`; inject `FavoritesUseCase`, `ResourceRepository` | +~45 lines |
| `res/values/strings.xml` | Add: `confirm_delete_standalone`, `delete_permission_denied`, `file_deleted`, `delete_failed`, `open_in_fms` | 5 new strings |
| `res/values-ru/strings.xml` | Same 5 strings in Russian | — |
| `res/values-uk/strings.xml` | Same 5 strings in Ukrainian | — |

No new layout files — all four buttons already exist in `activity_player_unified.xml`.
No new Hilt modules — both `FavoritesUseCase` and `ResourceRepository` are already provided.

---

## 7. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `DocumentsContract.deleteDocument()` called on a non-document `content://` URI → `IllegalArgumentException` | Medium — depends on URI authority of the source app | Check `DocumentsContract.isDocumentUri()` before calling; fall back to `contentResolver.delete()` |
| Android 10 `RecoverableSecurityException` on MediaStore delete | High — all Android 10 devices | Catch explicitly; launch `IntentSenderRequest` via `ActivityResultLauncher` |
| Android 11+ batch delete permission dialog | Medium — only for MediaStore-backed files | Use `MediaStore.createDeleteRequest()` with an `ActivityResultLauncher` |
| Favorite URI becomes stale across sessions | Low for local files, high for cloud provider URIs | Acceptable known limitation; document in user-facing help |
| `MediaStore.MediaColumns.DATA` null for scoped-storage files | Low on target API range (API 26–34); possible on API 35 | Null-safe check already in place; falls back to `MainActivity` |
| `resolveActivity()` returns null for share intent on API 30+ | Possible if no apps installed to handle the MIME type | Wrap in try-catch; show toast if chooser fails |

---

## 8. Implementation Steps

1. Add `FavoritesUseCase` and `ResourceRepository` constructor params to `StandalonePlayerViewModel`.
2. Add `_isFavorite` flow + `checkFavoriteStatus()` + `toggleFavorite()` + `findResourceForPath()` to the ViewModel.
3. Register `ActivityResultLauncher` for delete permission in `StandalonePlayerActivity` (for Android 10+).
4. Implement `setupFileOperationButtons()` in the Activity wiring all four buttons.
5. Implement `deleteCurrentFile()` / `performDelete()` with scheme-aware branching and API-level guards.
6. Implement `shareCurrentFile()` with FileProvider wrapping for `file://`.
7. Implement `openInFms()` + `resolveToLocalPath()` + `launchMainActivity()`.
8. Observe `viewModel.isFavorite` in `observeData()` to update star icon.
9. Add string resources in all three locales.
10. Run `.\scripts\add_to_dev_log.ps1` for each modified file.

---

## 9. Out of Scope

- Rename in standalone mode (requires write access; content URIs often don't support rename via `ContentResolver`).
- Copy/move in standalone mode (no destination picker without FMS resource context).
- Network file operations (standalone never receives `smb://`, `sftp://`, etc.).
- Batch operations (standalone receives one file at a time, except `ACTION_SEND_MULTIPLE` which III.12 covers separately).
