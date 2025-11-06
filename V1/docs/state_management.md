# Application State Management in FastMediaSorter Mobile

## State Management Overview

The application uses a combination of approaches for state management:
- **LiveData/ViewModel** for UI state
- **Room Database** for persistent data
- **SharedPreferences** for simple settings
- **In-memory cache** for temporary data

## UI State Management

### ViewModel State

#### ConnectionViewModel
```kotlin
class ConnectionViewModel : ViewModel() {
    // LiveData for reactive UI
    val networkConfigs = MutableLiveData<List<ConnectionConfig>>()
    val localCustomFolders = MutableLiveData<List<ConnectionConfig>>()
    val sortDestinations = MutableLiveData<List<ConnectionConfig>>()
    
    // Loading states
    val isLoading = MutableLiveData<Boolean>()
    val errorMessage = MutableLiveData<String?>()
    
    // Current selection
    var selectedConfig: ConnectionConfig? = null
}
```

**Features:**
- **Lifecycle-aware:** Automatic cleanup on Activity destroy
- **Reactive:** UI updates automatically on data changes
- **Thread-safe:** All changes in main thread

### Activity State

#### MainActivity State
- **currentConfigId:** Selected configuration ID
- **currentConfig:** Current configuration object
- **currentInterval:** Slideshow interval from input field

#### SlideshowActivity State
- **currentIndex:** Current position in file list
- **images:** List of file paths
- **isPaused:** Playback state
- **currentBitmap:** Currently loaded image

#### SortActivity State
- **currentIndex:** Position in sorting list
- **sortDestinations:** List of target folders
- **imageFiles:** List of files to process

## Persistent State

### Room Database Entities

#### ConnectionConfig Entity
```kotlin
@Entity(tableName = "connection_configs")
data class ConnectionConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val serverAddress: String = "",
    val username: String = "",
    val password: String = "",
    val folderPath: String = "",
    val type: String, // SMB, LOCAL_STANDARD, LOCAL_CUSTOM
    val interval: Int = 10,
    val lastUsed: Long = System.currentTimeMillis(),
    val writePermission: Boolean = false,
    val localUri: String? = null,
    val localDisplayName: String? = null
)
```

**Configuration Types:**
- **SMB:** Network folders with authentication
- **LOCAL_STANDARD:** Device standard folders (Camera, Downloads)
- **LOCAL_CUSTOM:** Custom local folders

### SharedPreferences

#### PreferenceManager
```kotlin
class PreferenceManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // App state
    fun isWelcomeShown(): Boolean
    fun setWelcomeShown(shown: Boolean)
    
    // Session state
    fun getLastFolderAddress(): String
    fun saveLastSession(folderAddress: String, interval: Int)
    
    // Settings
    fun isAllowDelete(): Boolean
    fun setAllowDelete(allow: Boolean)
    
    // UI preferences
    fun getTheme(): String
    fun setTheme(theme: String)
}
```

## State Synchronization

### Database Synchronization
- **Single Source of Truth:** Room as primary data source
- **LiveData Integration:** Automatic UI updates
- **Transactions:** ACID for operation integrity

### Cross-Activity State
- **Intent Extras:** State transfer between activities
- **SavedInstanceState:** Saving on screen rotation
- **Singleton Managers:** PreferenceManager for global state

## State Transitions

### Slideshow States

```
STOPPED ──► LOADING ──► PLAYING ──► PAUSED
    ▲           │           │           │
    └───────────┼───────────┼───────────┘
                ▼           ▼           ▼
             ERROR ───► RECOVERY ──► STOPPED
```

**Transitions:**
- **LOADING:** Loading file list and first image
- **PLAYING:** Automatic flipping
- **PAUSED:** Waiting for user action
- **ERROR:** Loading failure, show dialog
- **RECOVERY:** Attempt to continue with next file

### Sorting States

```
IDLE ──► LOADING ──► SORTING ──► PROCESSING ──► COMPLETED
 │           │           │           │            │
 └───────────┼───────────┼───────────┼────────────┘
             ▼           ▼           ▼            ▼
          CANCELLED   ERROR      TIMEOUT      RESET
```

**Transitions:**
- **LOADING:** Loading file list
- **SORTING:** Waiting for user action selection
- **PROCESSING:** Executing copy/move
- **COMPLETED:** All files processed

## Error State Handling

### Error States
- **NetworkError:** Connection issues
- **FileError:** Inaccessible files
- **PermissionError:** Missing permissions
- **MemoryError:** Memory shortage

### Recovery Strategies
- **Retry:** Automatic retry for temporary errors
- **Skip:** Skip problematic files
- **Fallback:** Use alternative sources
- **User Intervention:** Dialogs for fixes

## Memory Management

### Object Lifecycle
- **ViewModel Scope:** Cleanup on Activity destroy
- **Application Scope:** Singleton objects
- **Activity Scope:** Local state

### Resource Cleanup
- **Bitmap Recycling:** Image memory release
- **Coroutine Cancellation:** Stop background tasks
- **Database Connections:** Proper closing

### Cache Management
- **LRU Cache:** Remove rarely used data
- **Size Limits:** Memory consumption limits
- **Background Cleanup:** Cleanup on low memory

## State Persistence

### Automatic Persistence
- **Database:** All configurations saved automatically
- **Preferences:** Settings saved on change
- **Instance State:** UI state on rotation

### Manual Persistence
- **Export/Import:** Settings backup
- **Migration:** DB schema update on app update
- **Cleanup:** Remove outdated data

## Testing State Management

### Unit Tests
- **ViewModel Tests:** State logic verification
- **Repository Tests:** Data access testing
- **State Transitions:** Valid transition validation

### Integration Tests
- **Database Tests:** Persistence verification
- **UI Tests:** Interface state with Espresso
- **Lifecycle Tests:** Resource cleanup verification

## Future Improvements

### State Management Enhancements
- **MVI Pattern:** More strict unidirectional flow
- **Compose State:** Modern state for Compose UI
- **Redux-like:** Centralized state management

### Persistence Improvements
- **Encrypted Database:** Sensitive data encryption
- **Cloud Backup:** Settings synchronization
- **Versioned State:** State versioning support</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\state_management.md