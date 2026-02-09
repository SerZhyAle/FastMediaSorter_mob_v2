# SmbClient Refactoring Plan

## Current State
- **Total lines**: 2214
- **Size**: 97.75 KB
- **Status**: God Object with multiple responsibilities
- **Main issues**:
  - Mixed concerns: low-level SMB protocol, connection pooling, file operations, media scanning
  - Hard to test individual components
  - Code duplication with SFTP/FTP clients
  - Connection pooling logic could be reused

## Refactoring Strategy: Extract Layers

### Phase 1: Extract Connection Management → `SmbConnectionManager`
**Estimated lines**: ~400-500 lines

**What to extract**:
```
Lines ~51-105: Connection data classes (ConnectionKey, PooledConnection, SmbConnectionInfo)
Lines ~106-185: Client factory methods (getNormalClient, getDegradedClient, getClient)
Lines ~1667-1870: withConnection() - core connection wrapper with retry logic
Lines ~1871-2061: Connection pool management (isConnectionValid, removeConnection, cleanup, etc.)
```

**Responsibilities**:
- SMB client initialization (normal vs degraded mode)
- Connection pooling (acquire, release, validation)
- Connection lifecycle management
- Idle connection cleanup
- Connection reset/force reset

**Interface**:
```kotlin
class SmbConnectionManager @Inject constructor() {
    data class ConnectionKey(
        val server: String,
        val port: Int,
        val shareName: String,
        val username: String,
        val domain: String
    )
    
    data class PooledConnection(
        val session: Session,
        val share: DiskShare,
        val lastUsed: Long,
        val key: ConnectionKey
    )
    
    suspend fun <T> withConnection(
        connectionInfo: SmbConnectionInfo,
        operation: suspend (Session, DiskShare) -> T
    ): SmbResult<T>
    
    fun resetClients()
    fun forceFullReset()
    fun clearConnectionPool()
    fun close()
}
```

**Benefits**:
- Reusable connection management for different SMB operations
- Easier testing (mock connection pool)
- Clear separation of protocol vs business logic

---

### Phase 2: Extract File Operations → `SmbFileOperations`
**Estimated lines**: ~600-700 lines

**What to extract**:
```
Lines ~1133-1182: downloadFile()
Lines ~1183-1235: readFileBytes()
Lines ~1236-1288: readPartialFile()
Lines ~1289-1307: readFileBytesRange()
Lines ~1308-1349: uploadFile()
Lines ~1350-1404: deleteFile()
Lines ~1405-1438: deleteDirectory()
Lines ~1439-1515: renameFile()
Lines ~1516-1584: moveFile()
Lines ~1585-1602: createDirectory()
Lines ~1603-1621: exists()
Lines ~1622-1666: getFileInfo()
Lines ~2134-2194: checkWritePermission()
Lines ~2209-2250: openInputStream()
```

**Responsibilities**:
- Basic file CRUD operations (read, write, delete, rename, move)
- Directory operations (create, delete)
- File existence checks and metadata retrieval
- Streaming operations (download, upload, partial reads)
- Permission checking

**Interface**:
```kotlin
class SmbFileOperations @Inject constructor(
    private val connectionManager: SmbConnectionManager
) {
    suspend fun downloadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localFile: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): SmbResult<Unit>
    
    suspend fun uploadFile(
        connectionInfo: SmbConnectionInfo,
        localFile: File,
        remotePath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): SmbResult<Unit>
    
    suspend fun deleteFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit>
    
    suspend fun renameFile(
        connectionInfo: SmbConnectionInfo,
        oldPath: String,
        newPath: String
    ): SmbResult<Unit>
    
    suspend fun moveFile(
        connectionInfo: SmbConnectionInfo,
        sourcePath: String,
        destinationPath: String
    ): SmbResult<Unit>
    
    suspend fun createDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit>
    
    suspend fun exists(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Boolean>
    
    suspend fun getFileInfo(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<FileInfo>
    
    suspend fun checkWritePermission(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Boolean>
}
```

**Benefits**:
- Standard file operation interface (similar to SFTP/FTP)
- Easier to create unified FileOperationHandler
- Clear API surface for file operations

---

### Phase 3: Extract Media Scanning → `SmbMediaScanner`
**Estimated lines**: ~700-800 lines

**What to extract**:
```
Lines ~320-359: listFiles()
Lines ~360-393: scanMediaFiles()
Lines ~394-427: scanMediaFilesChunked()
Lines ~428-459: scanMediaFilesPaged()
Lines ~460-492: countMediaFiles()
Lines ~493-599: scanDirectoryRecursive() - all media files
Lines ~600-682: scanDirectoryRecursiveWithLimit() - chunked scan
Lines ~683-748: scanDirectoryNonRecursive() - single level
Lines ~749-808: scanDirectoryNonRecursiveWithOffset() - paged scan
Lines ~809-842: countDirectoryNonRecursive()
Lines ~843-893: countDirectoryRecursive()
Lines ~894-972: scanDirectoryWithOffsetLimit() - pagination helper
```

**Responsibilities**:
- Media file discovery (recursive, non-recursive, chunked, paged)
- File counting (with and without recursion)
- Media type filtering (image, video, audio, GIF)
- Progress reporting for long scans
- Pagination support for large directories

**Interface**:
```kotlin
class SmbMediaScanner @Inject constructor(
    private val connectionManager: SmbConnectionManager
) {
    suspend fun listFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<List<FileInfo>>
    
    suspend fun scanMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        scanRecursive: Boolean,
        onProgress: ((Int) -> Unit)? = null
    ): SmbResult<List<MediaFile>>
    
    suspend fun scanMediaFilesChunked(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        scanRecursive: Boolean,
        chunkSize: Int = 500,
        onChunk: (List<MediaFile>) -> Unit
    ): SmbResult<Int>
    
    suspend fun scanMediaFilesPaged(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        offset: Int,
        limit: Int
    ): SmbResult<List<MediaFile>>
    
    suspend fun countMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        scanRecursive: Boolean
    ): SmbResult<Int>
}
```

**Benefits**:
- Focused media scanning logic
- Reusable scanning algorithms
- Consistent pagination interface
- Easier to optimize scan performance

---

### Phase 4: Extract Share Discovery → `SmbShareDiscovery`
**Estimated lines**: ~150-200 lines

**What to extract**:
```
Lines ~973-1132: listShares() - discover available shares on server
```

**Responsibilities**:
- Share enumeration on SMB server
- Share metadata extraction (name, type, hidden status)
- Access permission checking per share

**Interface**:
```kotlin
class SmbShareDiscovery @Inject constructor(
    private val connectionManager: SmbConnectionManager
) {
    data class ShareInfo(
        val name: String,
        val type: String,
        val hidden: Boolean,
        val accessible: Boolean
    )
    
    suspend fun listShares(
        server: String,
        port: Int,
        username: String,
        password: String,
        domain: String = ""
    ): SmbResult<List<ShareInfo>>
}
```

**Benefits**:
- Isolated share discovery logic
- Easier to extend for network browsing features
- Clear API for UI components

---

### Phase 5: Extract Testing/Validation → `SmbConnectionTester`
**Estimated lines**: ~150-200 lines

**What to extract**:
```
Lines ~186-238: testConnection() - public test API
Lines ~239-319: performTestConnection() - actual test implementation
Lines ~2061-2102: getUserFriendlyMessage() - error translation
Lines ~2103-2133: buildDiagnosticMessage() - diagnostic info
```

**Responsibilities**:
- Connection testing with detailed diagnostics
- Error message translation (technical → user-friendly)
- Diagnostic information generation
- Connection validation

**Interface**:
```kotlin
class SmbConnectionTester @Inject constructor(
    private val connectionManager: SmbConnectionManager
) {
    suspend fun testConnection(
        connectionInfo: SmbConnectionInfo,
        path: String = ""
    ): SmbResult<String>
    
    fun getUserFriendlyMessage(exception: Exception): String
    
    fun buildDiagnosticMessage(
        server: String,
        port: Int,
        shareName: String,
        username: String,
        domain: String,
        remotePath: String,
        exception: Exception
    ): String
}
```

**Benefits**:
- Reusable connection testing
- Consistent error messaging
- Easier debugging with diagnostics

---

## Final SmbClient Structure

After refactoring, `SmbClient` becomes a facade that delegates to specialized components:

```kotlin
@Singleton
class SmbClient @Inject constructor(
    private val connectionManager: SmbConnectionManager,
    private val fileOperations: SmbFileOperations,
    private val mediaScanner: SmbMediaScanner,
    private val shareDiscovery: SmbShareDiscovery,
    private val connectionTester: SmbConnectionTester
) {
    // Delegation methods (1-2 lines each)
    suspend fun testConnection(...) = connectionTester.testConnection(...)
    suspend fun listShares(...) = shareDiscovery.listShares(...)
    suspend fun listFiles(...) = mediaScanner.listFiles(...)
    suspend fun scanMediaFiles(...) = mediaScanner.scanMediaFiles(...)
    suspend fun downloadFile(...) = fileOperations.downloadFile(...)
    suspend fun uploadFile(...) = fileOperations.uploadFile(...)
    // ... other delegation methods
    
    fun close() = connectionManager.close()
}
```

**Estimated final size**: ~200-300 lines (delegation layer only)

---

## Benefits of This Refactoring

1. **Single Responsibility**: Each class has one clear purpose
2. **Testability**: Components can be tested in isolation
3. **Reusability**: Connection manager and file operations can be used by other network protocols
4. **Maintainability**: Smaller, focused files are easier to understand
5. **Extensibility**: Easy to add new features to specific components
6. **Code Deduplication**: Shared logic can be extracted to common base classes

---

## Implementation Order

1. **Phase 1**: SmbConnectionManager (foundation for all operations)
2. **Phase 5**: SmbConnectionTester (uses ConnectionManager, needed for testing)
3. **Phase 2**: SmbFileOperations (basic CRUD, uses ConnectionManager)
4. **Phase 3**: SmbMediaScanner (media-specific logic, uses ConnectionManager)
5. **Phase 4**: SmbShareDiscovery (independent feature, uses ConnectionManager)
6. **Final**: Refactor SmbClient to facade pattern

---

## Testing Strategy

After each phase:
1. Create unit tests for extracted component
2. Run existing integration tests to ensure no regression
3. Build and run app to validate real-world usage
4. Update documentation

---

## Migration Path

For backward compatibility:
1. Keep original SmbClient methods as deprecated wrappers
2. Gradually migrate callers to new API
3. Remove deprecated methods after migration complete
4. Update all usages to use SmbClient facade

---

## Similar Refactoring for SFTP/FTP

After SmbClient refactoring, apply same pattern to:
- **SftpClient** (~83 KB): Extract SftpConnectionManager, SftpFileOperations, SftpMediaScanner
- **FtpClient** (~77 KB): Extract FtpConnectionManager, FtpFileOperations, FtpMediaScanner

This creates consistent architecture across all network protocols.

---

## Status

- ⏳ **Phase 1**: Not started
- ⏳ **Phase 2**: Not started
- ⏳ **Phase 3**: Not started
- ⏳ **Phase 4**: Not started
- ⏳ **Phase 5**: Not started
- ⏳ **Final Refactoring**: Not started

---

## Estimated Timeline

- **Phase 1**: 2-3 hours (connection management is complex)
- **Phase 2**: 2-3 hours (many file operations to extract)
- **Phase 3**: 3-4 hours (complex scanning logic)
- **Phase 4**: 1 hour (straightforward share discovery)
- **Phase 5**: 1 hour (testing and error handling)
- **Final**: 1 hour (create facade, update callers)

**Total**: ~10-14 hours

---

## Notes

- All extracted classes should use `@Inject constructor()` for Hilt DI
- Use `@Singleton` annotation for stateful components (ConnectionManager)
- Keep error handling consistent across all components
- Maintain existing `SmbResult<T>` wrapper for type-safe error handling
- Document all public APIs with KDoc comments
- Add `@VisibleForTesting` for methods needed by tests

