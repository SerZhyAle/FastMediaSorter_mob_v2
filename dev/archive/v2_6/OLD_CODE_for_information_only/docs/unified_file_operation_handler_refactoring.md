# UnifiedFileOperationHandler Refactoring Plan

## Problem Statement

Current architecture has **4 separate FileOperationHandler classes** with massive code duplication:

| Handler | Lines | Size (KB) | Duplication % |
|---------|-------|-----------|---------------|
| **SmbFileOperationHandler** | 1,452 | 80.52 | ~70% |
| **SftpFileOperationHandler** | 1,445 | 81.49 | ~70% |
| **FtpFileOperationHandler** | 1,331 | 75.25 | ~70% |
| **CloudFileOperationHandler** | 1,750 | 88.68 | ~60% |
| **TOTAL** | **5,978** | **325.94** | - |

**Estimated duplicated code**: ~4,000 lines (70% of total)

---

## Duplicated Logic Patterns

### 1. Copy Operations (~300 lines per handler)
All handlers implement similar copy logic:
- Source/destination path validation
- Progress callback setup
- Cancel flag checking
- Error handling and recovery
- Temporary file creation for cross-protocol transfers
- Cleanup on completion/cancellation

### 2. Move Operations (~400 lines per handler)
All handlers implement similar move logic:
- Copy + delete pattern
- Soft-delete to `.trash/` folder
- Undo support with original path tracking
- Same directory optimization (rename)
- Cross-protocol move handling
- Rollback on failure

### 3. Delete Operations (~150 lines per handler)
All handlers implement similar delete logic:
- Soft-delete to `.trash/` folder
- Trash folder creation if missing
- Error handling for permission issues
- Multiple retry attempts
- Cleanup of empty trash folders

### 4. Rename Operations (~150 lines per handler)
All handlers implement similar rename logic:
- Path validation (same directory)
- Extension preservation
- Collision detection
- Error recovery

### 5. Progress Tracking (~100 lines per handler)
All handlers implement similar progress logic:
- Throttled progress updates (every 100ms)
- Percentage calculation
- Cancel flag checking
- Handler posting to main thread

### 6. Error Handling (~200 lines per handler)
All handlers implement similar error logic:
- Exception type mapping (network, permission, disk space)
- User-friendly error messages
- Diagnostic information generation
- Retry logic with exponential backoff
- Cleanup on error

### 7. Temporary File Management (~150 lines per handler)
All handlers implement similar temp file logic:
- Unique temp file naming (`temp_UUID_filename`)
- Cleanup on success/failure
- Disk space checking
- Cache directory usage

---

## Proposed Architecture: Strategy Pattern + Template Method

### Core Components

#### 1. FileTransferProvider Interface
Protocol-specific implementations (SMB, SFTP, FTP, Cloud):

```kotlin
interface FileTransferProvider {
    val protocolName: String
    
    suspend fun downloadFile(
        sourcePath: String,
        destinationFile: File,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit>
    
    suspend fun uploadFile(
        sourceFile: File,
        destinationPath: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit>
    
    suspend fun deleteFile(path: String): Result<Unit>
    
    suspend fun renameFile(oldPath: String, newPath: String): Result<Unit>
    
    suspend fun exists(path: String): Result<Boolean>
    
    suspend fun getFileInfo(path: String): Result<FileInfo>
    
    suspend fun createDirectory(path: String): Result<Unit>
}
```

#### 2. UnifiedFileOperationHandler
Orchestrates operations using providers:

```kotlin
class UnifiedFileOperationHandler @Inject constructor(
    private val smbProvider: SmbTransferProvider,
    private val sftpProvider: SftpTransferProvider,
    private val ftpProvider: FtpTransferProvider,
    private val cloudProvider: CloudTransferProvider,
    private val tempFileManager: TempFileManager,
    private val progressTracker: ProgressTracker,
    private val errorHandler: FileOperationErrorHandler
) {
    suspend fun executeCopy(
        operation: FileOperation.Copy,
        onProgress: ((Int) -> Unit)? = null,
        cancelFlag: () -> Boolean = { false }
    ): FileOperationResult {
        val sourceProvider = getProvider(operation.sourcePath)
        val destProvider = getProvider(operation.destinationPath)
        
        return when {
            // Same protocol optimization
            sourceProvider::class == destProvider::class -> 
                executeSameProtocolCopy(sourceProvider, operation, onProgress, cancelFlag)
            
            // Cross-protocol transfer
            else -> 
                executeCrossProtocolCopy(sourceProvider, destProvider, operation, onProgress, cancelFlag)
        }
    }
    
    suspend fun executeMove(
        operation: FileOperation.Move,
        onProgress: ((Int) -> Unit)? = null,
        cancelFlag: () -> Boolean = { false }
    ): FileOperationResult {
        // Template method pattern: copy + delete with undo support
        val copyResult = executeCopy(
            FileOperation.Copy(operation.sourcePath, operation.destinationPath),
            onProgress,
            cancelFlag
        )
        
        if (copyResult is FileOperationResult.Success) {
            val deleteResult = executeSoftDelete(operation.sourcePath, operation.sourcePath)
            return if (deleteResult is FileOperationResult.Success) {
                FileOperationResult.Success(
                    filePath = copyResult.filePath,
                    originalPath = operation.sourcePath // For undo
                )
            } else {
                // Rollback: delete copied file
                getProvider(operation.destinationPath).deleteFile(copyResult.filePath)
                deleteResult
            }
        }
        
        return copyResult
    }
    
    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult {
        val provider = getProvider(operation.filePath)
        return provider.renameFile(operation.filePath, operation.newName)
            .fold(
                onSuccess = { FileOperationResult.Success(filePath = it) },
                onFailure = { errorHandler.handleError(it, "rename", operation) }
            )
    }
    
    suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult {
        return executeSoftDelete(operation.filePath, operation.filePath)
    }
    
    private suspend fun executeSoftDelete(
        filePath: String,
        originalPath: String
    ): FileOperationResult {
        val provider = getProvider(filePath)
        val trashPath = getTrashPath(filePath)
        
        // Create .trash folder if needed
        val trashDir = trashPath.substringBeforeLast('/')
        provider.createDirectory(trashDir)
        
        // Move to trash
        return provider.renameFile(filePath, trashPath)
            .fold(
                onSuccess = { 
                    FileOperationResult.Success(
                        filePath = trashPath,
                        originalPath = originalPath
                    ) 
                },
                onFailure = { errorHandler.handleError(it, "delete", operation) }
            )
    }
    
    private fun getProvider(path: String): FileTransferProvider {
        return when {
            path.startsWith("smb://") -> smbProvider
            path.startsWith("sftp://") -> sftpProvider
            path.startsWith("ftp://") -> ftpProvider
            path.startsWith("cloud://") -> cloudProvider
            else -> LocalTransferProvider() // Local file system
        }
    }
}
```

#### 3. SmbTransferProvider Implementation Example

```kotlin
class SmbTransferProvider @Inject constructor(
    private val smbClient: SmbClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : FileTransferProvider {
    override val protocolName = "SMB"
    
    override suspend fun downloadFile(
        sourcePath: String,
        destinationFile: File,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> {
        val credentials = credentialsRepository.getCredentialsForPath(sourcePath)
            ?: return Result.failure(Exception("No credentials found"))
        
        return smbClient.downloadFile(
            connectionInfo = credentials.toSmbConnectionInfo(),
            remotePath = sourcePath.removePrefix("smb://..."),
            localFile = destinationFile,
            onProgress = onProgress
        ).toResult()
    }
    
    override suspend fun uploadFile(
        sourceFile: File,
        destinationPath: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> {
        val credentials = credentialsRepository.getCredentialsForPath(destinationPath)
            ?: return Result.failure(Exception("No credentials found"))
        
        return smbClient.uploadFile(
            connectionInfo = credentials.toSmbConnectionInfo(),
            localFile = sourceFile,
            remotePath = destinationPath.removePrefix("smb://..."),
            onProgress = onProgress
        ).toResult()
    }
    
    // ... other methods
}
```

#### 4. TempFileManager
Centralized temporary file management:

```kotlin
class TempFileManager @Inject constructor(
    private val context: Context
) {
    private val tempFiles = mutableSetOf<File>()
    
    fun createTempFile(prefix: String, suffix: String): File {
        val tempFile = File(context.cacheDir, "temp_${UUID.randomUUID()}_$prefix$suffix")
        tempFiles.add(tempFile)
        return tempFile
    }
    
    fun cleanupTempFile(file: File) {
        if (file.exists()) file.delete()
        tempFiles.remove(file)
    }
    
    fun cleanupAllTempFiles() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }
}
```

#### 5. ProgressTracker
Centralized progress tracking with throttling:

```kotlin
class ProgressTracker {
    private var lastProgressUpdate = 0L
    private val throttleMs = 100L
    
    fun reportProgress(
        current: Long,
        total: Long,
        onProgress: ((Int) -> Unit)?
    ) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate >= throttleMs || current >= total) {
            lastProgressUpdate = now
            val percentage = ((current.toDouble() / total.toDouble()) * 100).toInt()
            onProgress?.invoke(percentage)
        }
    }
}
```

#### 6. FileOperationErrorHandler
Centralized error handling:

```kotlin
class FileOperationErrorHandler {
    fun handleError(
        throwable: Throwable,
        operation: String,
        context: FileOperation
    ): FileOperationResult.Error {
        val userMessage = when (throwable) {
            is NetworkException -> "Network error during $operation"
            is PermissionException -> "Permission denied for $operation"
            is DiskSpaceException -> "Insufficient disk space for $operation"
            else -> "Error during $operation: ${throwable.message}"
        }
        
        return FileOperationResult.Error(
            message = userMessage,
            exception = throwable,
            operation = context
        )
    }
}
```

---

## Benefits of Unified Architecture

### 1. Code Reduction
- **Before**: 5,978 lines across 4 handlers
- **After**: ~1,500 lines (UnifiedHandler) + 4 × 300 lines (Providers) = ~2,700 lines
- **Savings**: ~3,278 lines (-55% reduction)

### 2. Maintainability
- Single source of truth for operation logic
- Bug fixes apply to all protocols automatically
- Easier to add new protocols (just implement FileTransferProvider)

### 3. Consistency
- Identical behavior across all protocols
- Same error handling everywhere
- Unified progress reporting
- Consistent undo support

### 4. Testability
- Mock providers for unit testing
- Test UnifiedHandler independently of protocols
- Protocol-specific tests in provider classes

### 5. Extensibility
- Adding new protocol = implement FileTransferProvider interface (~300 lines)
- No changes to UnifiedHandler needed
- Easy to add protocol-specific optimizations

---

## Migration Strategy

### Phase 1: Create Provider Interface and Base Implementation
1. Define `FileTransferProvider` interface
2. Create `LocalTransferProvider` (simplest, for testing)
3. Create base test suite for providers

### Phase 2: Implement Protocol Providers
1. `SmbTransferProvider` (wrap existing SmbClient methods)
2. `SftpTransferProvider` (wrap existing SftpClient methods)
3. `FtpTransferProvider` (wrap existing FtpClient methods)
4. `CloudTransferProvider` (wrap existing cloud clients)

### Phase 3: Build UnifiedFileOperationHandler
1. Implement core operation methods (copy, move, rename, delete)
2. Add cross-protocol transfer logic
3. Integrate TempFileManager, ProgressTracker, ErrorHandler
4. Add comprehensive logging

### Phase 4: Create Helper Components
1. `TempFileManager` for temp file lifecycle
2. `ProgressTracker` for throttled progress updates
3. `FileOperationErrorHandler` for error translation

### Phase 5: Migrate Callers
1. Update BrowseViewModel to use UnifiedHandler
2. Update PlayerViewModel to use UnifiedHandler
3. Update file operation dialogs
4. Remove old handler classes

### Phase 6: Testing and Validation
1. Run existing integration tests
2. Manual testing of all protocols
3. Performance benchmarking
4. Memory profiling

---

## Testing Strategy

### Unit Tests
- Test each provider independently with mocked clients
- Test UnifiedHandler with mocked providers
- Test error handling paths
- Test cancel flag behavior

### Integration Tests
- Test real file operations on test servers (SMB/SFTP/FTP)
- Test cross-protocol transfers
- Test soft-delete and undo
- Test concurrent operations

### Performance Tests
- Compare operation speeds before/after refactoring
- Memory usage profiling
- Large file transfer benchmarks
- Concurrent operation stress tests

---

## Implementation Timeline

| Phase | Estimated Time | Priority |
|-------|----------------|----------|
| **Phase 1**: Provider Interface | 4 hours | High |
| **Phase 2**: Protocol Providers | 12 hours | High |
| **Phase 3**: UnifiedHandler | 16 hours | High |
| **Phase 4**: Helper Components | 4 hours | Medium |
| **Phase 5**: Migration | 8 hours | High |
| **Phase 6**: Testing | 8 hours | High |
| **TOTAL** | **52 hours** (~1.5 weeks) | - |

---

## Risks and Mitigation

### Risk 1: Performance Regression
**Mitigation**: Benchmark operations before/after. Optimize provider implementations if needed.

### Risk 2: Breaking Changes
**Mitigation**: Keep old handlers temporarily. Gradual migration with feature flags.

### Risk 3: Protocol-Specific Edge Cases
**Mitigation**: Comprehensive test coverage. Protocol experts review provider implementations.

### Risk 4: Increased Complexity for Simple Operations
**Mitigation**: Provider pattern keeps protocol-specific code simple. Unified logic is clearer.

---

## Success Metrics

- ✅ 55% code reduction (3,278 lines removed)
- ✅ Zero functionality regressions
- ✅ Same or better performance
- ✅ Improved test coverage (>80%)
- ✅ Easier to add new protocols
- ✅ Consistent behavior across protocols

---

## Future Enhancements

After unification, easy to add:
1. **Protocol Detection**: Auto-detect protocol from path
2. **Operation Queue**: Prioritize operations, batch similar operations
3. **Retry Strategies**: Configurable retry logic per protocol
4. **Caching**: Cache file metadata to reduce network calls
5. **Bandwidth Throttling**: Limit transfer speed per protocol
6. **Multi-part Uploads**: Split large files for parallel upload
7. **Resume Support**: Resume interrupted transfers

---

## Related Refactorings

This refactoring complements:
- **SmbClient Refactoring**: After SmbClient is split, `SmbTransferProvider` uses `SmbFileOperations`
- **PlayerActivity Refactoring**: Already complete, uses file operation handlers
- **BrowseActivity**: Will benefit from unified error handling

---

## Status

- ⏳ **Phase 1**: Not started
- ⏳ **Phase 2**: Not started
- ⏳ **Phase 3**: Not started
- ⏳ **Phase 4**: Not started
- ⏳ **Phase 5**: Not started
- ⏳ **Phase 6**: Not started

---

## Notes

- Keep existing handlers during migration for rollback safety
- Use `@VisibleForTesting` for methods needed by tests
- Document provider contract thoroughly (thread-safety, error handling, etc.)
- Consider adding metrics/analytics for operation success rates
- Plan for backward compatibility with existing ResourceType enums

