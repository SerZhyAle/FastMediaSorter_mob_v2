# Atomic File Copy - Existing Code Analysis

**Date**: 2026-02-10  
**Phase**: 1.1 - Analyze Existing Transfer Logic

---

## Architecture Overview

### Three-Layer Structure

```
FileOperationUseCase (Entry Point)
         ↓
FileOperationHandlers (Protocol Routers)
  - SmbFileOperationHandler
  - SftpFileOperationHandler
  - FtpFileOperationHandler
  - CloudFileOperationHandler
         ↓
FileOperationStrategy (Protocol Implementations)
  - LocalOperationStrategy
  - SmbOperationStrategy
  - SftpOperationStrategy
  - FtpOperationStrategy
  - CloudOperationStrategy
```

---

## Entry Points

### 1. FileOperationUseCase
**Location**: `domain/usecase/FileOperationUseCase.kt`

**Key Methods**:
- `executeWithProgress(operation: FileOperation): Flow<FileOperationProgress>`
- `executeInternal(operation: FileOperation, progressCallback: ByteProgressCallback?): FileOperationResult`

**Operations**:
- `FileOperation.Copy(sources, destination, overwrite)`
- `FileOperation.Move(sources, destination, overwrite)`
- `FileOperation.Delete(files, softDelete)`
- `FileOperation.Rename(file, newName)`

**Protocol Detection Logic**:
```kotlin
fun File.isNetworkPath(protocol: String): Boolean {
    val pathStr = this.path
    return pathStr.startsWith("$protocol://") || 
           pathStr.startsWith("/$protocol://") || 
           pathStr.startsWith("/$protocol:/") ||
           pathStr.startsWith("$protocol:/")
}
```

**Routing**:
- Detects protocol from path (smb://, sftp://, ftp://, cloud://, or local)
- Routes to appropriate Handler
- Handles mixed-protocol operations

---

## Protocol Handlers

### SmbFileOperationHandler
**Location**: `data/network/SmbFileOperationHandler.kt`

**Methods**:
- `executeCopy(operation: FileOperation.Copy, progressCallback: ByteProgressCallback?)`
- `executeMove(operation: FileOperation.Move, progressCallback: ByteProgressCallback?)`
- `executeDelete(operation: FileOperation.Delete)`
- `executeRename(operation: FileOperation.Rename)`

**Delegates to**: `SmbOperationStrategy`

### FtpFileOperationHandler
**Location**: `data/network/FtpFileOperationHandler.kt`
**Delegates to**: `FtpOperationStrategy`

### SftpFileOperationHandler
**Location**: `data/network/SftpFileOperationHandler.kt`
**Delegates to**: `SftpOperationStrategy`

### CloudFileOperationHandler
**Location**: `data/cloud/CloudFileOperationHandler.kt`
**Delegates to**: `CloudOperationStrategy`

---

## Protocol Strategies (Implementation Layer)

### Base Interface: FileOperationStrategy
**Location**: `data/transfer/FileOperationStrategy.kt`

**Core Methods**:
```kotlin
suspend fun copyFile(
    source: String,
    destination: String,
    overwrite: Boolean,
    progressCallback: ByteProgressCallback? = null
): Result<String>

suspend fun moveFile(
    source: String,
    destination: String
): Result<Unit>

suspend fun deleteFile(path: String): Result<Unit>
```

---

### LocalOperationStrategy

**Location**: `data/transfer/strategy/LocalOperationStrategy.kt`

#### copyFile() Flow:
1. Check source exists
2. Check destination exists (if !overwrite → exception)
3. Create parent directories
4. **Stream copy with 8KB buffer**
5. Progress callback during copy
6. Return destination path

#### moveFile() Flow:
1. Check source exists
2. **Try `File.renameTo()` first (atomic for same FS)**
3. If rename fails → fallback to copy+delete
4. Delete source after successful copy

**Key Notes**:
- Uses `File.renameTo()` for same-filesystem moves (instant)
- Handles Android 10+ MediaStore for shared storage deletion
- Scoped Storage handling for different storage types

---

### SmbOperationStrategy

**Location**: `data/transfer/strategy/SmbOperationStrategy.kt`

#### copyFile() Flow:
**Scenarios**:
1. **SMB → SMB**: `copySmbToSmb()` - buffer transfer via SmbClient
2. **SMB → Local**: `downloadFromSmb()` - download via SmbClient
3. **Local → SMB**: `uploadToSmb()` - upload via SmbClient

#### moveFile() Flow:
1. Check if source and dest on **same SMB share**
2. If same share → **server-side rename via SmbClient.moveFile()**
3. If different shares → copy+delete
4. Cross-protocol → copy+delete

**Key Notes**:
- Server-side moves only work within same share
- Uses `SmbClient` with connection pooling
- Supports progress callbacks for all operations

---

### FtpOperationStrategy

**Location**: `data/transfer/strategy/FtpOperationStrategy.kt`

#### copyFile() Flow:
**Scenarios**:
1. **FTP → FTP**: `copyFtpToFtp()` - buffer transfer
2. **FTP → Local**: `downloadFromFtp()` - download
3. **Local → FTP**: `uploadToFtp()` - upload

#### moveFile() Flow:
1. Check if source and dest on **same FTP server**
2. If same server → **server-side rename via FtpClient.moveFile()**
3. If different servers → copy+delete
4. Cross-protocol → copy+delete

**Key Notes**:
- FTP rename uses `RNFR`/`RNTO` commands
- Connection handling via `FtpClient`
- Must ensure destination directory exists before rename

---

### SftpOperationStrategy

**Location**: `data/transfer/strategy/SftpOperationStrategy.kt`  
**Similar to FTP**, uses SSHJ for operations

---

### CloudOperationStrategy

**Location**: `data/transfer/strategy/CloudOperationStrategy.kt`  
**Handles**: Google Drive, OneDrive, Dropbox

---

## Current Copy/Move Flow Summary

### Copy Operation
```
User Action
  ↓
FileOperationUseCase.executeWithProgress(FileOperation.Copy)
  ↓
Protocol Detection (isNetworkPath)
  ↓
Route to Appropriate Handler
  ↓
Handler.executeCopy(operation, progressCallback)
  ↓
Strategy.copyFile(source, destination, overwrite, progressCallback)
  ↓
Actual File Transfer (streams, buffers, network protocols)
  ↓
Return Result<String> (destination path)
```

### Move Operation
```
User Action
  ↓
FileOperationUseCase.executeWithProgress(FileOperation.Move)
  ↓
Protocol Detection
  ↓
Handler.executeMove(operation, progressCallback)
  ↓
Strategy.moveFile(source, destination)
  ↓
Logic:
  1. Try server-side rename (if same FS/server/share)
  2. Fallback: Strategy.copyFile() + Strategy.deleteFile()
  ↓
Return Result<Unit>
```

---

## Key Findings for Atomic Copy Implementation

### 1. **Atomic Move Already Exists (Partially)**
- **LocalOperationStrategy**: Uses `File.renameTo()` for same-filesystem moves
- **SmbOperationStrategy**: Uses server-side rename for same-share moves
- **FtpOperationStrategy**: Uses FTP `RNFR`/`RNTO` for same-server moves

### 2. **Copy Operations Have NO Atomic Behavior**
- All protocols write directly to final destination name
- No temporary naming during transfer
- Files visible immediately to external systems

### 3. **Modification Points for Atomic Copy**

#### Option A: Modify Strategies (Low-Level)
**Pros**: Clean separation, protocol-specific handling  
**Cons**: Must modify all 5 strategies

**Files to Modify**:
- `LocalOperationStrategy.copyFile()` - Line ~22
- `SmbOperationStrategy.copySmbToSmb/uploadToSmb()` - Multiple methods
- `FtpOperationStrategy.uploadToFtp()` - Upload logic
- `SftpOperationStrategy` - Similar to FTP
- `CloudOperationStrategy` - Cloud-specific

#### Option B: Add Wrapper Layer (Mid-Level)
**Pros**: Single point of control, settings toggle easy  
**Cons**: Additional abstraction layer

**New Component**:
- `AtomicTransferWrapper` that wraps existing strategies
- Intercepts `copyFile()` calls
- Adds `.temp_copy` suffix logic
- Calls original strategy with modified destination
- Performs rename after completion

#### Option C: Extend FileOperationStrategy Interface
**Pros**: Clear API contract  
**Cons**: Breaking change to interface

**Add Methods**:
```kotlin
suspend fun copyFileAtomic(
    source: String,
    destination: String,
    overwrite: Boolean,
    progressCallback: ByteProgressCallback?
): Result<String>
```

---

## Recommendations

### Approach: **Option B (Wrapper Layer)**

**Rationale**:
1. **Non-Breaking**: Doesn't modify existing working code
2. **Configurable**: Easy to add settings toggle
3. **Testable**: Can test atomic behavior independently
4. **Rollback-Safe**: Can disable via settings if issues arise

### Implementation Plan:

1. **Create Helper**: `TempFileNamingStrategy.kt`
   - `fun getTempFileName(originalPath: String): String`
   - `fun getOriginalFileName(tempPath: String): String`

2. **Create Wrapper**: `AtomicTransferStrategy.kt`
   - Implements `FileOperationStrategy`
   - Delegates to underlying strategy
   - Intercepts `copyFile()` to add atomic behavior
   - Uses `renameFile()` or protocol-specific rename after copy

3. **Settings Integration**: `AppSettings.kt`
   - Add `enableAtomicTransfer: Boolean = true`
   - Read in FileOperationUseCase

4. **Factory/Injection**: Modify Hilt modules
   - Create atomic wrapper if setting enabled
   - Otherwise use direct strategy

---

## Open Questions (From Requirements)

### 1. Collision Handling
**Requirement**: Overwrite  
**Action**: If `destination.temp_copy` exists → delete before copy

### 2. Move Within Same Local FS
**Requirement**: Direct rename (no .temp_copy)  
**Current State**: ✅ Already implemented via `File.renameTo()`  
**Action**: No changes needed for same-FS local moves

### 3. FTP Rename Support Detection
**Requirement**: Check `FEAT` for RNFR/RNTO support  
**Current State**: ❓ Need to check `FtpClient` implementation  
**Action**: Review FtpClient, add feature detection

### 4. SMB Rename Atomicity
**Requirement**: Document limitations if not atomic  
**Current State**: Uses `SmbClient.moveFile()` - need to verify  
**Action**: Test SMB rename behavior, document findings

### 5. Android SAF DocumentFile.renameTo()
**Requirement**: Handle API level differences  
**Current State**: LocalOperationStrategy handles MediaStore for API 29+  
**Action**: Test all scenarios (MediaStore, SAF, External SD)

### 6. Progress Reporting
**Requirement**: Operation not done until rename completes  
**Current State**: Progress already tracked via ByteProgressCallback  
**Action**: Add rename phase to progress (99% copy → 100% after rename)

### 7. Cleanup Strategy
**Requirement**: Cleanup on resource open only  
**Action**: Add cleanup task to resource loading phase

---

## Next Steps

1. ✅ **Phase 1.1 Complete**: Analysis documented
2. **Phase 1.2**: Create `TempFileNamingStrategy` helper with unit tests
3. **Phase 1.3**: Create `AtomicTransferStrategy` wrapper interface
4. **Phase 2**: Implement wrapper for each protocol
5. **Phase 3**: Settings integration
6. **Phase 4**: Cleanup logic
7. **Phase 5**: E2E testing

---

**End of Analysis Document**
