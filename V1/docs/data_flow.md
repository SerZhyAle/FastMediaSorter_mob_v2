# Data Flows in FastMediaSorter Mobile

## Data Flow Overview

The application works with two main types of data:
1. **Media files** (images and videos)
2. **Metadata** (configurations, settings, state)

## Media Data Flow

### Local Media Files

#### Source: MediaStore API
```
MediaStore API → LocalStorageClient → ImageRepository → Glide → UI
```

**Process:**
1. **Permission Request:** `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`
2. **Scanning:** MediaStore.ContentResolver to get file URIs
3. **Filtering:** By folders (Camera, Downloads, Screenshots)
4. **Caching:** Glide cache for fast access
5. **Display:** PhotoView for scaling

#### Key Components:
- **LocalStorageClient:** Interface to MediaStore
- **ImageRepository:** Image loading management
- **Glide:** Caching and decoding
- **PhotoView:** Interactive display

### Network Media Files (SMB)

#### Source: SMB Protocol
```
SMB Server → SmbClient → SmbDataSource → ExoPlayer/Glide → UI
```

**Process:**
1. **Connection:** jcifs-ng for authentication
2. **File Listing:** Getting folder contents
3. **Loading:** Streaming download over SMB
4. **Decoding:** Glide for images, ExoPlayer for videos
5. **Caching:** Memory cache for performance

#### Key Components:
- **SmbClient:** SMB connection and operations
- **SmbDataSource:** DataSource for ExoPlayer
- **ImageRepository:** Loading coordination

## Metadata Flow

### Connection Configurations

#### Storage: Room Database
```
UI Input → ViewModel → Repository → Room DAO → SQLite
```

**Process:**
1. **Data Input:** User enters connection parameters
2. **Validation:** Format and availability checking
3. **Saving:** Room entity `ConnectionConfig`
4. **Caching:** LiveData for reactive updates

#### Data Structure:
```kotlin
data class ConnectionConfig(
    val id: Long,
    val name: String,
    val serverAddress: String,
    val username: String,
    val password: String,
    val folderPath: String,
    val type: String, // SMB, LOCAL_STANDARD, LOCAL_CUSTOM
    val interval: Int,
    val lastUsed: Long,
    val writePermission: Boolean
)
```

### Application Settings

#### Storage: SharedPreferences + Encrypted
```
UI → PreferenceManager → EncryptedSharedPreferences
```

**Settings:**
- **welcomeShown:** First launch flag
- **lastFolderAddress:** For session auto-resume
- **allowDelete:** File deletion permission
- **shuffleMode:** Random playback mode
- **connectionSettings:** Cached connection parameters

## Asynchronous Flows

### Coroutines Flow

#### Slideshow Flow
```
Timer (interval) → Coroutine → Load Next Image → Update UI
```

**Features:**
- **Cancellation:** Stop on pause/exit
- **Exception Handling:** Skip corrupted files
- **Preloading:** Parallel loading of next file

#### Sorting Flow
```
User Action → Coroutine → File Operation → Progress Update → Next File
```

**Features:**
- **Background Processing:** IO dispatcher for operations
- **Progress Callbacks:** UI progress updates
- **Error Recovery:** Retry attempts on failures

### LiveData Flows

#### Connection Configurations
```
Database Changes → DAO → Repository → ViewModel → LiveData → UI
```

**Reactive Updates:**
- Adding new connection
- Settings changes
- Availability status updates

#### UI State
```
User Actions → ViewModel → State LiveData → UI Updates
```

## Caching and Optimization

### Multi-level Caching

#### Memory Cache
- **Glide LruCache:** For decoded images
- **Bitmap Pool:** Bitmap object reuse
- **Object Pool:** For frequently used objects

#### Disk Cache
- **Room Database:** Metadata and configurations
- **Glide Disk Cache:** Compressed images
- **File Cache:** Temporary files for videos

#### Network Cache
- **SMB Connection Pool:** Connection reuse
- **DNS Cache:** Name resolution caching
- **Authentication Cache:** Session persistence

### Preloading

#### Strategies
- **Next Item:** Preloading next file
- **Batch Loading:** Group loading for lists
- **Progressive Loading:** Gradual loading of large files

#### Memory Management
- **LRU Eviction:** Removing rarely used data
- **Size Limits:** Cache size restrictions
- **Cleanup:** Cleanup on low memory

## Data Synchronization

### Local Synchronization
- **Database Transactions:** ACID for integrity
- **Version Control:** For conflict resolution
- **Backup/Restore:** Settings export/import

### Network Synchronization
- **Connection Testing:** Availability check before operations
- **Retry Logic:** Retries on network failures
- **Offline Mode:** Work with cached data

## Error Handling in Flows

### Graceful Degradation
- **Fallback Sources:** Alternative data sources
- **Partial Loading:** Loading available data
- **User Notification:** Problem notification

### Error Propagation
```
Data Source → Repository → ViewModel → UI (Dialog/Toast)
```

### Recovery Strategies
- **Automatic Retry:** For temporary failures
- **Manual Recovery:** Dialogs with fix options
- **State Preservation:** Progress saving on errors

## Monitoring and Debugging

### Performance Metrics
- **Load Times:** File loading times
- **Cache Hit Rate:** Caching efficiency
- **Memory Usage:** Memory consumption

### Logging
- **Data Flow Logs:** Operation tracing
- **Error Logs:** Exception details
- **Performance Logs:** Performance metrics

### Diagnostics
- **Connection Tests:** Network connection checks
- **File Access Tests:** Permission validation
- **Cache Diagnostics:** Caching efficiency analysis</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\data_flow.md