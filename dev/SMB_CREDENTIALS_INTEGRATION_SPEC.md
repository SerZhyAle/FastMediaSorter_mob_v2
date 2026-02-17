# SMB Credentials Integration Specification

**Status:** Partially актуально (as of 2026-02-17)  
**Priority:** Medium (SMB provider path is incomplete; does not directly block current SFTP path)  
**Related TODOs:** 8 TODOs across `SmbTransferProvider`, `NetworkCredentialsRepositoryImpl`, `SftpOperationStrategy`

---

## Overview

Integrate/align SMB transfer paths with `NetworkCredentialsRepository` so credential resolution is consistent and explicit. One SMB transfer path still uses hardcoded empty credentials.

## Relevance Check (Current Codebase)

### What is still актуально

- `SmbTransferProvider` still has stub credential handling (`username/password/domain = ""`).
- `SmbTransferProvider.downloadFile()` still passes `fileSize = 0L` with TODO for pre-fetch.
- `NetworkCredentialsRepositoryImpl.getById(id: Long)` still returns `null` (stub).
- `SftpOperationStrategy` still has TODO for SSH key passphrase support.

### What changed vs original task text

- Main SMB runtime path appears to be `SmbOperationStrategy`, not `SmbTransferProvider`.
- `SmbOperationStrategy` already resolves SMB credentials via repository (`getByServerAndShare` + host fallback).
- Therefore the statement "Blocks SFTP/FTP transfer operations" is outdated and should be treated as overstated.

### Architectural Decision Required

Choose one path before implementation:

1. **Finish + wire `SmbTransferProvider`** into actual DI/runtime usage, then implement all TODOs there.
2. **Deprecate/remove `SmbTransferProvider`** and keep SMB credential logic only in `SmbOperationStrategy` to avoid duplicate transfer stacks.

Without this decision, effort may be spent on a non-used code path.

## Problem Statement

The `SmbTransferProvider` has stub implementations that use empty string credentials:

```kotlin
// Current state in SmbTransferProvider.kt
SmbConnectionInfo(
    server = "server",
    port = 445,
    shareName = "share",
    username = "", // TODO: credentials.username
    password = "", // TODO: credentials.password
    domain = "",   // TODO: credentials.domain
    workgroup = ""
)
```

In the `SmbTransferProvider` path, this prevents:

- Accessing password-protected SMB shares
- Using domain-authenticated shares
- Proper error reporting (connections fail silently)

## Current State

### Affected Files

- [SmbTransferProvider.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/SmbTransferProvider.kt) - 6 TODOs
- [NetworkCredentialsRepositoryImpl.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt) - 1 TODO
- [SftpOperationStrategy.kt](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt) - 1 TODO (passphrase)

### Existing Infrastructure

✅ **Already Available:**

- `NetworkCredentialsRepository` interface and implementation
- `NetworkCredentialsEntity` data model
- `NetworkCredentialsDao` for database access
- `SmbClient` with credential support
- `NetworkCredentialsResolver` for extracting credentials from paths

⚠️ **Needs Integration / Cleanup:**

- Resolve ownership between `SmbTransferProvider` and `SmbOperationStrategy`
- If provider path is retained: wire `NetworkCredentialsRepository` into `SmbTransferProvider`
- If provider path is retained: add file size lookup before download operations
- Keep credential error handling explicit and non-silent

---

## Implementation Plan

### Phase 1: Inject NetworkCredentialsRepository

**File:** `SmbTransferProvider.kt`

**Changes:**

```kotlin
class SmbTransferProvider @Inject constructor(
    private val context: Context,
    private val smbClient: SmbClient,
    private val credentialsRepository: NetworkCredentialsRepository // ADD THIS
) {
    // ...
}
```

### Phase 2: Fetch Credentials from Repository

Replace current stub code:

```kotlin
// OLD CODE (remove)
private suspend fun getSmbConnectionInfo(smbPath: String): SmbConnectionInfo? {
    // TODO: Fetch credentials from NetworkCredentialsRepository
    // For now, return stub with empty credentials
    val parsedInfo = NetworkCredentialsResolver.parseSmbPath(smbPath)
    return SmbConnectionInfo(
        server = parsedInfo.server,
        port = parsedInfo.port,
        shareName = parsedInfo.shareName,
        username = "", // TODO: credentials.username
        password = "", // TODO: credentials.password
        domain = "",   // TODO: credentials.domain
        workgroup = ""
    )
}

// NEW CODE (implement)
private suspend fun getSmbConnectionInfo(smbPath: String): SmbConnectionInfo? {
    val parsedInfo = NetworkCredentialsResolver.parseSmbPath(smbPath)
    
    // Fetch credentials from repository
    val credentials = credentialsRepository.getCredentials(smbPath)
    
    if (credentials == null) {
        Timber.e("No credentials found for SMB path: $smbPath")
        return null
    }
    
    return SmbConnectionInfo(
        server = credentials.server,
        port = credentials.port,
        shareName = parsedInfo.shareName,
        username = credentials.username,
        password = credentials.password,
        domain = credentials.domain ?: "",
        workgroup = credentials.workgroup ?: ""
    )
}
```

### Phase 3: Add File Size Lookup

**Problem:** Current code uses `fileSize = 0L` for downloads

```kotlin
// TODO: Get file size from getFileInfo first
fileSize = 0L,
```

**Solution:** Call `smbClient.getFileInfo()` before download

```kotlin
private suspend fun downloadFile(source: MediaFile, destination: String): FileOperationResult {
    val connectionInfo = getSmbConnectionInfo(source.path) ?: return FileOperationResult.Error("No SMB credentials")
    
    // Get file info to retrieve actual size
    val fileInfoResult = smbClient.getFileInfo(
        connectionInfo,
        extractRemotePath(source.path)
    )
    
    val fileSize = when (fileInfoResult) {
        is SmbResult.Success -> fileInfoResult.data?.size ?: source.size
        is SmbResult.Error -> {
            Timber.w("Failed to get file size, using MediaFile.size: ${fileInfoResult.message}")
            source.size
        }
    }
    
    // Proceed with download using actual file size
    val destFile = File(destination)
    destFile.parentFile?.mkdirs()
    
    return smbClient.downloadFile(
        connectionInfo,
        extractRemotePath(source.path),
        destFile.outputStream(),
        fileSize, // Use actual size, not 0L
        progressCallback = null
    ).toFileOperationResult()
}
```

### Phase 4: Error Handling for Credentials

Add proper error handling when credentials are missing:

```kotlin
override suspend fun downloadToLocal(source: MediaFile, destination: String): FileOperationResult {
    val connectionInfo = getSmbConnectionInfo(source.path)
        ?: return FileOperationResult.AuthenticationRequired(
            provider = "SMB",
            message = "No credentials configured for ${source.path}"
        )
    
    // Continue with download...
}
```

### Phase 5: Add getById to NetworkCredentialsRepository (Optional)

**File:** `NetworkCredentialsRepository.kt` and `NetworkCredentialsRepositoryImpl.kt`

**Current:**

```kotlin
override suspend fun getById(id: Long): NetworkCredentialsEntity? {
    return null // TODO: Add getById to DAO if needed
}
```

**Decision:**

- ✅ **Keep stub if unused** - No callers found in codebase
- ⚠️ **Implement if needed** - Add DAO method if future features require it

```kotlin
// If implementing:
// In NetworkCredentialsDao.kt
@Query("SELECT * FROM network_credentials WHERE id = :id")
suspend fun getById(id: Long): NetworkCredentialsEntity?

// In NetworkCredentialsRepositoryImpl.kt
override suspend fun getById(id: Long): NetworkCredentialsEntity? {
    return dao.getById(id)
}
```

---

## Data Flow (Recommended Target)

```
User requests SMB file operation
    ↓
Selected SMB transfer path (Strategy or Provider)
    ↓
Parse SMB path → server/share/remotePath
    ↓
NetworkCredentialsRepository.getByServerAndShare(server, share)
    ↓
fallback: NetworkCredentialsRepository.getCredentialsByHost(server)
    ↓
Build SmbConnectionInfo (username/password/domain)
    ↓
SmbClient operation (download/upload/delete/...)
    ↓
SMBJ library connects with resolved credentials
```

---

## Testing Strategy

### Unit Tests

```kotlin
@Test
fun `downloadToLocal uses credentials from repository`() = runTest {
    // Mock credentials repository
    val mockRepo = mock<NetworkCredentialsRepository>()
    whenever(mockRepo.getCredentials("smb://server/share/file.txt"))
        .thenReturn(NetworkCredentialsEntity(
            server = "server",
            port = 445,
            username = "user",
            password = "pass",
            domain = "DOMAIN"
        ))
    
    val provider = SmbTransferProvider(context, smbClient, mockRepo)
    
    // Verify correct credentials passed to SmbClient
    provider.downloadToLocal(mediaFile, "/local/dest")
    
    verify(smbClient).downloadFile(
        argThat { it.username == "user" && it.password == "pass" },
        any(),
        any(),
        any(),
        any()
    )
}

@Test
fun `downloadToLocal returns error when no credentials`() = runTest {
    val mockRepo = mock<NetworkCredentialsRepository>()
    whenever(mockRepo.getCredentials(any())).thenReturn(null)
    
    val provider = SmbTransferProvider(context, smbClient, mockRepo)
    val result = provider.downloadToLocal(mediaFile, "/local/dest")
    
    assertTrue(result is FileOperationResult.AuthenticationRequired)
}
```

### Integration Tests

Test with real SMB server:

- Connect to password-protected share
- Download file with correct credentials
- Verify download fails with wrong credentials
- Test domain authentication
- Test anonymous access (empty credentials)

### Manual Testing Checklist

- [ ] Configure SMB credentials in app settings
- [ ] Browse password-protected SMB share
- [ ] Download file from protected share
- [ ] Verify error message when credentials missing
- [ ] Test with domain-authenticated share
- [ ] Test with workgroup-authenticated share
- [ ] Test anonymous SMB share (no credentials)

---

## Migration Plan

### Phase 1: Add Credentials Support (Non-Breaking)

- Inject `NetworkCredentialsRepository` into `SmbTransferProvider`
- Fetch credentials but fallback to empty if not found
- No UI changes needed yet

### Phase 2: Require Credentials (Breaking)

- Return `AuthenticationRequired` error when credentials missing
- Update UI to prompt for credentials
- Add credentials management screen

### Phase 3: Optimize

- Cache credentials in memory for duration of transfer session
- Add file size lookup optimization
- Implement connection pooling if needed

---

## Security Considerations

### Credential Storage

- ✅ Credentials already encrypted in database (Room + EncryptedSharedPreferences)
- ✅ Credentials never logged
- ⚠️ Ensure credentials not leaked in error messages

### Network Security

- Use SMB3 protocol encryption when available
- Validate server certificates (if applicable)
- Support NTLMv2 authentication (avoid NTLM)

---

## Dependencies

### Required

1. ✅ `NetworkCredentialsRepository` (already implemented)
2. ✅ `SmbClient` with credential support (already implemented)
3. ✅ `NetworkCredentialsResolver` (already implemented)

### Optional

1. ⚠️ Credentials management UI (if not already exists)
2. ⚠️ Passphrase support for SSH keys (SFTP, separate spec)

---

## Success Criteria

✅ SMB operations use credentials from `NetworkCredentialsRepository`  
✅ Missing credentials return proper error (not silent failure)  
✅ File size lookup works before download  
✅ Domain authentication works  
✅ Workgroup authentication works  
✅ Unit tests pass (95%+ coverage)  
✅ Integration tests pass with real SMB server  
✅ No credentials leaked in logs or error messages

---

## Estimated Effort

- **Phase 1-2 (Inject repository & fetch credentials):** 2-3 hours
- **Phase 3 (File size lookup):** 1-2 hours
- **Phase 4 (Error handling):** 2-3 hours
- **Phase 5 (Optional getById):** 1 hour (if needed)
- **Testing:** 4-6 hours

**Total:** ~12-15 hours

---

## Related Specifications

- [Network Transfer Operations Completion](file:///c:/GIT/FastMediaSorter_mob_v2/dev/NETWORK_TRANSFER_COMPLETION_SPEC.md)
- [SFTP Passphrase Support](file:///c:/GIT/FastMediaSorter_mob_v2/dev/SFTP_PASSPHRASE_SPEC.md) (to be created)

---

## Notes

- This is not a hard prerequisite for SFTP/FTP completion; it is SMB-path consistency and maintainability work
- Implementation is straightforward since infrastructure already exists
- Main risk is testing with various SMB server configurations (domain, workgroup, anonymous)
- Consider adding credentials validation UI before attempting connection
