# Local Storage Integration in FastMediaSorter Mobile

## Local Storage Overview

The application uses **MediaStore API** and **Storage Access Framework (SAF)** for accessing local media files on Android device. Both standard folders (Camera, Downloads) and custom directories are supported.

## Components Architecture

```
Android MediaStore / SAF
    │
    ▼
LocalStorageClient
    │
    ▼
ImageRepository / SortRepository
    │
    ▼
UI Components
```

## Key Components

### LocalStorageClient
Main class for local storage operations.

```kotlin
class LocalStorageClient(private val context: Context) {
    
    // Permission checking
    fun hasMediaPermission(): Boolean
    
    // Getting standard folders
    fun getStandardFolders(): List<MediaFolder>
    
    // Getting files from folder
    fun getMediaFilesFromFolder(folderUri: Uri, recursive: Boolean): List<MediaFile>
    
    // File operations
    fun copyFile(sourceUri: Uri, destinationUri: Uri): Boolean
    fun moveFile(sourceUri: Uri, destinationUri: Uri): Boolean
    fun deleteFile(uri: Uri): Boolean
    
    // Folder creation
    fun createFolder(parentUri: Uri, name: String): Uri?
}
```

### MediaFile Model
```kotlin
data class MediaFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String,
    val isVideo: Boolean = false
)
```

### MediaFolder Model
```kotlin
data class MediaFolder(
    val uri: Uri,
    val name: String,
    val displayName: String,
    val isCustom: Boolean = false
)
```

## Permissions and Access

### Media Permissions (Android 13+)
```kotlin
// In manifest
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

// Check and request
val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_IMAGES
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}
```

### Scoped Storage (Android 10+)
- **MediaStore API:** For media file access
- **SAF (Storage Access Framework):** For custom folders
- **Media Location:** Optional location metadata access

## Working with Standard Folders

### Standard Locations
```kotlin
enum class StandardFolder {
    CAMERA,      // DCIM/Camera
    DOWNLOADS,   // Download
    SCREENSHOTS, // Pictures/Screenshots
    PICTURES     // Pictures
}
```

### MediaStore Query
```kotlin
val projection = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.DISPLAY_NAME,
    MediaStore.Images.Media.DATA,
    MediaStore.Images.Media.SIZE,
    MediaStore.Images.Media.DATE_MODIFIED,
    MediaStore.Images.Media.MIME_TYPE
)

val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
val selectionArgs = arrayOf("%Camera%")

val cursor = context.contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    projection,
    selection,
    selectionArgs,
    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
)
```

## Working with Custom Folders

### Storage Access Framework (SAF)
```kotlin
// Request folder access
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
startActivityForResult(intent, REQUEST_CODE_FOLDER)

// Handle result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE_FOLDER && resultCode == RESULT_OK) {
        val uri = data?.data
        // Save persistent permissions
        contentResolver.takePersistableUriPermission(
            uri!!,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}
```

### File Operations via SAF
```kotlin
// Copy file
fun copyFile(sourceUri: Uri, destinationUri: Uri): Boolean {
    return try {
        contentResolver.openInputStream(sourceUri)?.use { input ->
            contentResolver.openOutputStream(destinationUri)?.use { output ->
                input.copyTo(output)
                true
            } ?: false
        } ?: false
    } catch (e: Exception) {
        false
    }
}
```

## Android 11+ Scoped Storage

### Media Store Changes
- **File path access:** Limited, use URI
- **All files access:** Requires MANAGE_EXTERNAL_STORAGE
- **App-specific directories:** Full access without permissions

### Code Adaptation
```kotlin
// Instead of File(path)
val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

// Instead of FileInputStream
val inputStream = contentResolver.openInputStream(uri)

// Instead of File.exists()
val exists = try {
    contentResolver.openInputStream(uri)?.close()
    true
} catch (e: FileNotFoundException) {
    false
}
```

## Caching and Performance

### Content Observer
```kotlin
class MediaObserver(handler: Handler) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        // Update cache on changes
        refreshMediaCache()
    }
}

// Registration
contentResolver.registerContentObserver(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    true,
    mediaObserver
)
```

### Preloading
- **Thumbnail loading:** Fast thumbnail loading
- **Metadata caching:** Size and date caching
- **Batch operations:** Group queries for lists

## Error Handling

### Missing Permissions
```kotlin
if (!hasMediaPermission()) {
    // Show explanation dialog
    showPermissionDialog()
    return
}
```

### Inaccessible Files
- **File deleted:** File deleted by another app
- **Storage removed:** SD card ejected
- **Corrupted file:** Damaged file

### Recovery
- **Permission recovery:** Re-request permissions
- **Path resolution:** URI recalculation on changes
- **Cache invalidation:** Clear outdated cache

## Security and Privacy

### Scoped Access
- **Minimal permissions:** Only necessary permissions
- **User consent:** Explicit access confirmation
- **Temporary access:** Limited token lifetime

### Data Protection
- **No sensitive data:** No personal data stored
- **Secure URIs:** Protected file URIs
- **Permission checks:** Validation before each operation

## Compatibility

### Android Versions
- **Android 13+:** READ_MEDIA_IMAGES/VIDEO
- **Android 10-12:** READ_EXTERNAL_STORAGE
- **Android 9-:** WRITE_EXTERNAL_STORAGE for writing

### Devices
- **Phones:** Standard media folders
- **Tablets:** Same as phones
- **TV/Chromecast:** Limited support

## Monitoring and Debugging

### Permission Diagnostics
```kotlin
fun diagnosePermissions(): PermissionStatus {
    return when {
        hasMediaPermission() -> PermissionStatus.GRANTED
        shouldShowRequestPermissionRationale() -> PermissionStatus.DENIED
        else -> PermissionStatus.PERMANENTLY_DENIED
    }
}
```

### Operation Logging
- **File access:** URI and operations
- **Permission changes:** Permission events
- **Storage events:** Storage changes

## Future Improvements

### New Android Features
- **Photo Picker:** Modern file selection
- **Media permissions:** More granular permissions
- **Cloud media:** Google Photos integration

### Optimizations
- **Virtual files:** Virtual file support
- **Batch operations:** Group operations
- **Background sync:** Background synchronization</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\local_storage_integration.md