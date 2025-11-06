# Local Storage Features of FastMediaSorter Mobile

## MediaStore Integration

### Media File Access
- **Android 13+:** READ_MEDIA_IMAGES, READ_MEDIA_VIDEO permissions
- **Android 10-12:** READ_EXTERNAL_STORAGE permission
- **Scoped Storage:** Modern file access model
- **Media Location:** Optional geolocation metadata access

### Standard Folders
- **Camera:** DCIM/Camera - camera photos
- **Downloads:** Download - downloaded files
- **Screenshots:** Pictures/Screenshots - screen captures
- **Pictures:** Pictures - user images

### Custom Folders
- **Storage Access Framework:** Arbitrary directory selection
- **Persistent permissions:** Access preservation after reboot
- **Nested folders:** Work with nested directories
- **Custom scanning:** Selected directory scanning

## File Operations

### Reading and Display
- **URI-based access:** Work through Content URI instead of paths
- **Metadata extraction:** Size, date, MIME type retrieval
- **Thumbnail loading:** Fast thumbnail loading
- **Full resolution:** Full image loading on demand

### File Modification
- **Copy operations:** File copying between folders
- **Move operations:** File moving with MediaStore update
- **Delete operations:** Safe deletion with confirmation
- **Rename operations:** File renaming (Android 11+)

### Folder Creation
- **Directory creation:** New directory creation
- **Path validation:** Path correctness checking
- **Permission checks:** Creation rights validation
- **Naming conflicts:** Name conflict resolution

## Caching and Performance

### Content Observer
```kotlin
// Monitoring MediaStore changes
val observer = object : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        // Cache update on changes
        invalidateCache()
    }
}
contentResolver.registerContentObserver(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    true,
    observer
)
```

### Preloading
- **Batch loading:** Group metadata loading
- **Thumbnail caching:** Thumbnail caching
- **Progressive loading:** Gradual large collection loading
- **Memory management:** Memory consumption limiting

### Search and Filtering
- **Date-based sorting:** Sorting by creation/modification date
- **Type filtering:** Filtering by file type (image/video)
- **Size filtering:** Filtering by file size
- **Path-based filtering:** Folder-based limiting

## Android Storage Access Framework (SAF)

### Directory Selection
```kotlin
// Request folder access
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}
startActivityForResult(intent, REQUEST_CODE_FOLDER)
```

### Persistent URI Permissions
```kotlin
// Permission saving
contentResolver.takePersistableUriPermission(
    uri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
)
```

### SAF Operations
- **DocumentFile API:** Files and folders abstraction
- **Tree navigation:** File tree navigation
- **File operations:** Creation, copying, moving
- **Permission handling:** Permission management

## Error Handling

### Missing Permissions
- **Permission dialogs:** Permission necessity explanation
- **Graceful degradation:** Limited functionality without permissions
- **Recovery options:** Permission re-request
- **Alternative access:** SAF fallback for partial permissions

### Inaccessible Files
- **File deleted:** File deleted by another app
- **Storage removed:** SD card ejected
- **Corrupted files:** Damaged files
- **Path changes:** Storage structure changes

### State Recovery
- **URI resolution:** URI recalculation on changes
- **Cache invalidation:** Outdated cache cleanup
- **State recovery:** Position restoration after errors
- **User notification:** Informative messages

## Special Features

### External Storage
- **SD card support:** External SD card work
- **USB storage:** USB drive support
- **OTG devices:** External devices via OTG
- **Multiple volumes:** Work with multiple volumes

### Cloud Storage
- **Google Photos:** Integration via MediaStore
- **Media sync:** Synchronization with cloud services
- **Offline access:** Caching for offline mode
- **Hybrid storage:** Local and cloud combination

## Performance and Optimizations

### Memory Management
- **Bitmap recycling:** Bitmap object reuse
- **LRU caching:** Rarely used data removal
- **Size limits:** Cache size limits
- **Background cleanup:** Background cleanup

### I/O Optimizations
- **Async operations:** Asynchronous file operations
- **Batch processing:** Group operations for performance
- **Priority queuing:** Critical operation prioritization
- **Resource pooling:** Connection reuse

### Monitoring
- **Storage metrics:** Free space, I/O speed
- **Performance counters:** Operation times, cache hit rate
- **Error tracking:** Error statistics by type
- **Usage analytics:** Storage usage metrics

## Security

### Scoped Access
- **Minimal permissions:** Only necessary permissions
- **User consent:** Explicit access confirmation
- **Temporary URIs:** Limited permission lifetime
- **Access logging:** Access operation logging

### Data Protection
- **No sensitive data:** No personal data storage
- **Secure URIs:** Protected file identifiers
- **Permission validation:** Validation before each operation
- **Cleanup:** Temporary data cleanup

## Compatibility

### Android Versions
- **Android 14:** New MediaStore features
- **Android 13:** Granular media permissions
- **Android 11-12:** Scoped storage enforcement
- **Android 10:** Scoped storage introduction
- **Android 9-:** Legacy storage model

### Devices
- **Phones:** Standard functionality
- **Tablets:** Extended capabilities
- **Chrome OS:** Support through Android apps
- **TV devices:** Limited support

## Future Improvements

### New Features
- **Photo Picker:** Modern media file selection
- **Media editing:** Basic image editing
- **Advanced search:** Search by metadata, faces, objects
- **Smart organization:** Automatic categorization

### Extensions
- **Cloud integration:** Deep cloud service integration
- **File management:** Advanced file operations
- **Backup/restore:** Media backup
- **Sharing features:** File sharing between devices</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\local_features.md