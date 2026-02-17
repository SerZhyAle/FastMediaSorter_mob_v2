# Network Transfer Operations Completion Specification

**Status:** Planned (Validated: 2026-02-17)  
**Priority:** High  
**Blocked by:** Network Credentials Resolution System  
**Related TODOs:** 13 TODOs in transfer strategies

---

## Overview

Complete the implementation of SFTP and FTP file transfer operations. Currently, these operations have stub implementations waiting for the credentials resolution system to be completed.

---

## Actuality Check (2026-02-17)

### Conclusion

✅ **Task is still actual.**

### Verified Current State in Code

- `SftpToSftpStrategy`, `SftpToLocalStrategy`, `LocalToSftpStrategy` still contain stub `copy/move` implementations.
- `FtpToFtpStrategy`, `FtpToLocalStrategy`, `LocalToFtpStrategy` still contain stub `copy/move` implementations.
- `SftpFileAccess` and `FtpFileAccess` still return hardcoded `false` for `exists/delete`.
- All these classes are bound in DI (`TransferModule`) and therefore still affect runtime behavior.

### Important Corrections to This Spec

1. **Credentials resolver is partially ready (not fully missing).**
    - `NetworkCredentialsResolver` already exists in `data/cloud/NetworkCredentialsResolver.kt`.
    - It supports parsing `smb://`, `sftp://`, `ftp://` and conversion to SMB/SFTP connection info.
    - Missing part: integration/wiring of this resolver into transfer strategies + FTP mapping helper.

2. **Examples in this spec use outdated method signatures.**
    - Current `TransferStrategy` implementation methods are `Boolean`-based (`copy/move` with `Uri` + optional `sourceCredentialsId`).
    - Current `FileAccess` contract returns `Boolean` for `exists/delete`.
    - Examples with `MediaFile` + `FileOperationResult` are conceptually useful but not signature-accurate for direct copy-paste.

3. **FTP client API differs from assumptions in examples.**
    - `FtpClient` does not expose `fileExists(...)`.
    - Existence checks must be implemented via available FTP operations (e.g., listing/metadata probe pattern).
    - FTP operations require explicit connection lifecycle (or dedicated `*WithNewConnection` methods).

4. **Outstanding TODO scope now reads as 12 stubs + 1 adjacent dependency TODO.**
    - 8 strategy stubs (SFTP/FTP copy/move)
    - 4 file-access stubs (`exists/delete` for SFTP/FTP)
    - plus adjacent TODO: SFTP passphrase support in separate operation-strategy path

## Problem Statement

The application supports SMB network file operations but has incomplete support for SFTP and FTP protocols. The following operations need implementation:

### SFTP Operations (7 TODOs)

- SFTP → SFTP copy/move
- SFTP → Local copy
- Local → SFTP copy
- File existence check
- File deletion

### FTP Operations (6 TODOs)

- FTP → FTP copy/move
- FTP → Local copy
- Local → FTP copy
- File existence check
- File deletion

## Current State

### Affected Files

#### SFTP Strategies

- [SftpToSftpStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToSftpStrategy.kt)
- [SftpToLocalStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToLocalStrategy.kt)
- [LocalToSftpStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToSftpStrategy.kt)

#### FTP Strategies

- [FtpToFtpStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToFtpStrategy.kt)
- [FtpToLocalStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToLocalStrategy.kt)
- [LocalToFtpStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToFtpStrategy.kt)

#### File Access

- [SftpFileAccess.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/SftpFileAccess.kt)
- [FtpFileAccess.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/FtpFileAccess.kt)

### Stub Implementation Example

```kotlin
// Current stub in SftpToSftpStrategy.kt
override suspend fun move(source: MediaFile, destination: String): FileOperationResult {
    // TODO: Implement SftpToSftp move when credentials resolution is available
    return FileOperationResult.Error("SFTP to SFTP move not implemented")
}
```

---

## Prerequisites

### 1. Credentials Resolution System ⚠️ **Partially Ready**

What is already available:

- Resolving credentials by path for `smb://`, `sftp://`, `ftp://`
- Conversion helper for `SftpConnectionInfo`

What still blocks this task:

- Wiring resolver into transfer strategies and `FileAccess` implementations
- FTP connection-info adapter/helper (or direct parameter mapping at call sites)
- Consistent credential lookup strategy between source and destination paths

### 2. Existing Infrastructure ✅ **Available**

The following components already exist and can be used:

- `SftpClient` - SFTP operations client
- `FtpClient` - FTP operations client
- `NetworkCredentialsResolver` - credentials extraction from paths
- Transfer result types (`FileOperationResult`)

### 3. Contract Alignment ⚠️ **Required before implementation**

The implementation plan must align with current interfaces:

- `TransferStrategy.copy/move(...): Boolean` (credentials-aware overload)
- `FileAccess.exists/delete(...): Boolean`

If richer error propagation is required, it should be handled via logging + upstream wrappers, or via an intentional interface change tracked as a separate task.

---

## Implementation Plan

### Contract-Accurate Target (Implementation-Ready)

All changes must use current interfaces exactly:

- `TransferStrategy.copy(...): Boolean`
- `TransferStrategy.move(...): Boolean`
- `FileAccess.exists(...): Boolean`
- `FileAccess.delete(...): Boolean`

No `MediaFile`/`FileOperationResult` signatures in this layer.

### Phase 1: Shared Helpers and Resolver Wiring

**Files to modify:**

- `SftpToSftpStrategy.kt`
- `SftpToLocalStrategy.kt`
- `LocalToSftpStrategy.kt`
- `FtpToFtpStrategy.kt`
- `FtpToLocalStrategy.kt`
- `LocalToFtpStrategy.kt`
- `SftpFileAccess.kt`
- `FtpFileAccess.kt`

**Required constructor change pattern:**

```kotlin
private val credentialsResolver: NetworkCredentialsResolver
```

**Required helper pattern per protocol class:**

```kotlin
private fun extractRemotePath(uri: Uri, schemePrefix: String, host: String, port: Int): String {
    val full = uri.toString()
    val prefix = "$schemePrefix://$host:$port"
    return full.removePrefix(prefix).ifBlank { "/" }
}
```

### Phase 2: SFTP FileAccess Completion (`Boolean` contract)

**File:** `SftpFileAccess.kt`

Implement:

- `exists(uri)`:
  - resolve credentials by `uri.toString()`
  - compute remote path
  - call `sftpClient.exists(connectionInfo, remotePath)`
  - return `result.getOrDefault(false)`
- `delete(uri)`:
  - resolve credentials; if null return `false`
  - call `sftpClient.deleteFile(connectionInfo, remotePath)`
  - return `isSuccess`

### Phase 3: FTP FileAccess Completion (`Boolean` contract)

**File:** `FtpFileAccess.kt`

Implement:

- `exists(uri)`:
  - resolve credentials; if null return `false`
  - connect via `ftpClient.connect(...)`; if failed return `false`
  - check existence using available FTP operations (directory/file listing probe)
  - always `disconnect()` in `finally`
- `delete(uri)`:
  - resolve credentials; if null return `false`
  - connect; if failed return `false`
  - call `ftpClient.deleteFile(remotePath)` and return `isSuccess`
  - always `disconnect()` in `finally`

### Phase 4: SFTP Transfer Strategies (`Boolean` contract)

**Files:**

- `SftpToSftpStrategy.kt`
- `SftpToLocalStrategy.kt`
- `LocalToSftpStrategy.kt`

#### 4.1 `SftpToSftpStrategy.copy(...)`

- resolve source and destination credentials
- create temp file in `context.cacheDir`
- download source to temp using `sftpClient.downloadFile(...)`
- upload temp to destination using `sftpClient.uploadFile(...)`
- return `true/false`
- delete temp file in `finally`

#### 4.2 `SftpToSftpStrategy.move(...)`

- if same host+port+username: perform server-side rename via `sftpClient.rename(...)`
- else: fallback to `copy(...)` then delete source via `sftpClient.deleteFile(...)`
- return `true` only if full sequence succeeds

#### 4.3 `SftpToLocalStrategy.copy(...)`

- resolve source credentials
- resolve destination output stream by URI scheme:
  - `file`/`null` -> `FileOutputStream`
  - `content` -> `contentResolver.openOutputStream(...)`
- call `sftpClient.downloadFile(...)`

#### 4.4 `LocalToSftpStrategy.copy(...)`

- resolve destination credentials
- resolve source input stream by URI scheme:
  - `file`/`null` -> `FileInputStream`
  - `content` -> `contentResolver.openInputStream(...)`
- call `sftpClient.uploadFile(...)`

### Phase 5: FTP Transfer Strategies (`Boolean` contract)

**Files:**

- `FtpToFtpStrategy.kt`
- `FtpToLocalStrategy.kt`
- `LocalToFtpStrategy.kt`

#### 5.1 `FtpToFtpStrategy.copy(...)`

- connect to source FTP
- download source into temp file
- disconnect source
- connect to destination FTP
- upload temp file
- disconnect destination
- cleanup temp file

#### 5.2 `FtpToFtpStrategy.move(...)`

- if same FTP endpoint and user: server-side `ftpClient.moveFile(oldPath, newPath)`
- otherwise: `copy(...)` then delete source using source connection

#### 5.3 `FtpToLocalStrategy.copy(...)`

- connect using source credentials
- stream download to destination (`file`/`content` support)
- disconnect in `finally`

#### 5.4 `LocalToFtpStrategy.copy(...)`

- connect using destination credentials
- stream upload from source (`file`/`content` support)
- disconnect in `finally`

### Phase 6: Progress and Failure Semantics

- Forward `progressCallback` to client APIs where supported.
- For operations where callback is not natively supported (certain FTP paths), keep deterministic completion semantics and return `false` on any failure.
- Use `Timber.e/w` for root-cause logs.

### Phase 7: Commit Plan (recommended)

1. **Commit A:** Resolver wiring + remote-path helpers
2. **Commit B:** `SftpFileAccess` + `FtpFileAccess`
3. **Commit C:** `SftpTo*` strategies
4. **Commit D:** `FtpTo*` strategies
5. **Commit E:** tests + lint/build validation fixes

---

## Testing Strategy

### Unit Tests

Create test cases for each strategy:

- Mock `SftpClient` and `FtpClient`
- Mock `NetworkCredentialsResolver`
- Test success paths
- Test error handling (no credentials, connection failure, etc.)

### Integration Tests

Test with real SFTP/FTP servers (test environment):

- Same-server operations
- Cross-server operations
- Large file transfers
- Connection timeout handling

### Manual Testing

- Transfer files between local ↔ SFTP
- Transfer files between local ↔ FTP
- Transfer files between SFTP ↔ SFTP (same/different servers)
- Transfer files between FTP ↔ FTP (same/different servers)
- Verify file integrity after transfer
- Test progress callbacks
- Test cancellation

---

## Error Handling

### Credentials Errors

- Missing credentials → `FileOperationResult.Error("No credentials")`
- Invalid credentials → Return SFTP/FTP client error

### Network Errors

- Connection timeout → Propagate client error
- Connection refused → Propagate client error
- Transfer interrupted → Clean up temp files

### File Errors

- Source not found → `FileOperationResult.Error("Source not found")`
- Permission denied → Propagate client error
- Disk full → Propagate client error

---

## Performance Considerations

### Temp File Usage

- Use temp files for cross-server/cross-protocol transfers
- Clean up temp files in `finally` blocks
- Consider direct stream-to-stream for same-server operations

### Progress Reporting

- Implement progress callbacks for large files
- Update UI during long transfers
- Allow cancellation

### Connection Pooling

- Reuse connections where possible (SFTP supports this)
- FTP requires connection per operation (stateful protocol)

---

## Dependencies

### Blocking Issues

1. ✅ SFTP client implementation (already exists)
2. ✅ FTP client implementation (already exists)
3. ⚠️ **Credentials resolution wiring into transfer layer** (needs implementation)
4. ⚠️ **FTP credentials mapping/adapter + exists strategy** (needs implementation)

### Related Work

- [SMB Credentials Integration Spec](file:///c:/GIT/FastMediaSorter_mob_v2/dev/SMB_CREDENTIALS_INTEGRATION_SPEC.md) (to be created)

---

## Success Criteria

✅ All SFTP transfer operations work (copy, move, delete, check exists)  
✅ All FTP transfer operations work (copy, move, delete, check exists)  
✅ Progress reporting works for large files  
✅ Error handling is robust (credentials, network, file errors)  
✅ Temp files are cleaned up properly  
✅ Unit tests pass (90%+ coverage)  
✅ Integration tests pass with real servers  
✅ No memory leaks during large transfers

---

## Estimated Effort

- **Phase 1 (SFTP File Access):** 2-3 hours
- **Phase 2 (FTP File Access):** 2-3 hours
- **Phase 3 (SFTP Strategies):** 8-10 hours
- **Phase 4 (FTP Strategies):** 8-10 hours
- **Testing:** 6-8 hours

**Total:** ~30-35 hours (assuming credentials system is ready)

---

## Notes

- Implementation can proceed incrementally (SFTP first, then FTP)
- Each phase can be tested independently
- Consider adding configuration for transfer buffer sizes
- May need to handle SSH key authentication for SFTP (see passphrase TODO)
- Before coding, decide whether to:
  - keep current `Boolean` contracts and map errors to logs/failure booleans, or
  - refactor transfer contracts to return structured results in this layer.
