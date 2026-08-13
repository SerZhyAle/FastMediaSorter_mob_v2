# S0153 — bugfix: StandalonePlayer delete crashes with IllegalArgumentException on content URIs

## Status
Draft → Approved → In Progress → Implemented

## Problem

`StandaloneFileOperationsHandler.performDelete()` routes any `content://` URI that is not a
DocumentsContract document to `MediaStore.createDeleteRequest()` on API 30+:

```kotlin
// StandaloneFileOperationsHandler.kt, line 85–92
uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
    val pendingIntent = MediaStore.createDeleteRequest(activity.contentResolver, listOf(uri))
    ...
}
```

`MediaStore.createDeleteRequest()` is only valid for items in the **Images, Video, or Audio**
collections. It does **not** support `MediaStore.Downloads` items. Passing a Downloads URI
(`content://media/external/downloads/<id>`) raises:

```
java.lang.IllegalArgumentException:
  All requested items must be referenced by specific ID
```

This was confirmed in log 3 (line 250–252, API 36 / Android 16):
- File: `689306896_...n.jpg` (an image auto-downloaded to Downloads via `LinkDownloadWriter`)
- Incoming intent URI: `content://media/external/downloads/16496`
- Stack trace points to `StandaloneFileOperationsHandler.kt:87` → `MediaStore.createDeleteRequest`

The exception is caught at `StandaloneFileOperationsHandler.kt:126` and logged as
`E StandalonePlayer: delete failed for <name>` without any user-facing recovery path.

**User-visible effect**: delete button silently fails with a generic error toast; the file
is not deleted.

## Root Cause

`MediaStore.createDeleteRequest()` is documented to work only with Images, Video, and Audio
collection URIs. The routing condition `uri.scheme == "content" && Build.VERSION.SDK_INT >= R`
is too broad — it sends Downloads URIs (`content://media/external/downloads/...`) to
`createDeleteRequest`, which rejects them.

The Downloads collection must be deleted via `contentResolver.delete()` instead, same as the
API 26–29 path.

## Affected Files

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt`

## Fix

Add a collection check before routing to `createDeleteRequest`. Only Images/Video/Audio URIs
should use the permission-dialog path; Downloads (and any other collection) fall through to
`contentResolver.delete()`.

```kotlin
uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && isMediaStorePermissionRequestCollection(uri) -> {
    val pendingIntent = MediaStore.createDeleteRequest(activity.contentResolver, listOf(uri))
    ...
}
```

Helper:

```kotlin
@RequiresApi(Build.VERSION_CODES.R)
private fun isMediaStorePermissionRequestCollection(uri: Uri): Boolean {
    // createDeleteRequest() only supports Images/Video/Audio, NOT Downloads or other collections.
    val path = uri.path ?: return false
    return path.contains("/images/") || path.contains("/video/") || path.contains("/audio/")
}
```

Downloads URIs (`/downloads/`) fall through to the existing API 26–29 branch:
- `contentResolver.delete()` → works on Downloads items without a permission dialog on API 30+.
- `RecoverableSecurityException` on API 29 is already caught.

## Implementation Notes

- Helper is private — no new public API.
- The `contentResolver.delete()` branch on API 30+ for a Downloads URI should succeed
  without needing a special permission dialog (Downloads items are owned by the app or
  the MediaStore grants direct delete access).
- No changes to `deleteCurrentFile()` required.
- Reproduced on API 36 with `content://media/external/downloads/16496`.

## Verification

- Download an image via the app's share-receive flow → open in StandalonePlayer → tap delete
  → file must be deleted successfully (no `IllegalArgumentException`, no generic error toast).
- Open a MediaStore image from Images/DCIM in StandalonePlayer → tap delete → system
  permission dialog must still appear (API 30+, unchanged behaviour).
- Open a MediaStore video → tap delete → same permission dialog flow must work.

## Last Audit
_Not audited yet._
