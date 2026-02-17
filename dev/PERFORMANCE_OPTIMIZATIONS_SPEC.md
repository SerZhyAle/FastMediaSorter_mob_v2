# Performance Optimizations Specification

**Status:** Planned  
**Priority:** Low (Implement if performance issues reported)  
**Related TODOs:** 5 TODOs for performance optimizations
**Last Verification:** 2026-02-17 (codebase scan)  
**Verification Result:** 5/5 TODOs still present in code

---

## Overview

This specification covers performance optimization opportunities identified in TODO comments. These are "nice-to-have" improvements that should be implemented only if users report performance issues.

---

## Actuality Check (2026-02-17)

| Optimization | TODO Status | Actuality | Notes |
|--------------|------------|-----------|-------|
| FTP Native Pagination | Present | ✅ Actual | `scanFolderPaged()` still loads full list and paginates in memory |
| SMB Metadata Extraction | Present (2 TODOs) | ✅ Actual | EXIF/video metadata still intentionally skipped during SMB scan |
| Network Trash Cleanup | Present | ⚠️ Conditional | Local cleanup exists; network cleanup is still not implemented |
| SFTP Passphrase Support | Present | ⚠️ Partially Actual | SFTP client supports passphrase, but strategy layer still passes `null` |

### Conclusion

- Spec is still relevant.
- Section "SFTP Passphrase Support" required clarification: this is not full missing feature anymore, it is consistency gap between modules.
- Network trash remains requirement-dependent and should be explicitly decided (keep/defer/remove TODO).

---

## Optimization 1: FTP Native Pagination

**Priority:** Medium  
**File:** [FtpMediaScanner.kt:148](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt)

### Problem

```kotlin
// TODO: optimize FTP client to support native pagination
```

Currently, FTP scanner loads all files from directory before paginating in memory. For directories with thousands of files, this causes:

- Slow initial load
- High memory usage
- Poor UX (long wait before first results)

### Current Implementation

```kotlin
// Load ALL files first
val allFiles = ftpClient.listFiles(remotePath)

// Then paginate in memory
val page = allFiles.drop(offset).take(limit)
```

### Proposed Implementation

**Option 1: MLSD with Range (if FTP server supports)**

```kotlin
// Use MLSD command with range parameters (if server supports)
val pageFiles = ftpClient.listFilesRange(remotePath, offset, limit)
```

**Option 2: Stream-based pagination**

```kotlin
// Stream files and stop when page is full
val pageFiles = ftpClient.listFilesStream(remotePath)
    .drop(offset)
    .take(limit)
    .toList()
```

### Impact

- **Memory:** Reduced from O(n) to O(limit)
- **Speed:** First page loads immediately
- **UX:** Users see results faster

### Testing

- [ ] Directory with 10,000+ files
- [ ] Pagination works correctly
- [ ] Memory usage stays low
- [ ] Fallback for servers without MLSD support

### Estimated Effort: 4-6 hours

---

## Optimization 2: SMB Metadata Extraction

**Priority:** Low  
**Files:** [SmbMediaScanner.kt:111, 115](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt)

### Problem

```kotlin
// TODO: Extract EXIF from SMB files (requires downloading file header)
// TODO: Extract video metadata from SMB files (requires downloading file or partial read)
```

Currently, SMB files show no EXIF data or video metadata. This is because:

- EXIF requires reading first ~64KB of file
- Video metadata requires reading file header
- Downloading entire file for metadata is too slow

### Proposed Implementation

**Phase 1: EXIF Extraction (Images)**

```kotlin
private suspend fun extractExif(smbPath: String): ExifData? {
    // Download only first 64KB for EXIF
    val headerBytes = smbClient.downloadPartial(
        path = smbPath,
        offset = 0,
        length = 64 * 1024 // 64KB
    )
    
    return ExifInterface(ByteArrayInputStream(headerBytes)).run {
        ExifData(
            width = getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
            height = getAttributeInt(ExifInterface.TAG_IMAGE_HEIGHT, 0),
            dateTime = getAttribute(ExifInterface.TAG_DATETIME),
            gpsLatLong = latLong
        )
    }
}
```

**Phase 2: Video Metadata (Videos)**

```kotlin
private suspend fun extractVideoMetadata(smbPath: String): VideoMetadata? {
    // Download first 1MB for video header
    val headerBytes = smbClient.downloadPartial(
        path = smbPath,
        offset = 0,
        length = 1024 * 1024 // 1MB
    )
    
    // Use MediaMetadataRetriever on header
    val tempFile = File.createTempFile("smb_video_", ".tmp")
    tempFile.writeBytes(headerBytes)
    
    return MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(tempFile.absolutePath)
        VideoMetadata(
            duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLong(),
            width = retriever.extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toInt(),
            height = retriever.extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toInt()
        )
    }.also {
        tempFile.delete()
    }
}
```

### Requires

1. **SmbClient.downloadPartial()** - new method for range requests

```kotlin
suspend fun downloadPartial(
    connectionInfo: SmbConnectionInfo,
    remotePath: String,
    offset: Long,
    length: Int
): ByteArray
```

1. **Cache metadata** - avoid re-downloading headers

```kotlin
private val metadataCache = LruCache<String, ExifData>(maxSize = 100)
```

### Trade-offs

| Approach | Pros | Cons |
|----------|------|------|
| Download headers | Fast, accurate metadata | Requires partial download support |
| Skip metadata | No network overhead | Users don't see EXIF/duration |
| Download full file | Complete metadata | Very slow for large files |

### Recommendation

✅ **Implement header-only extraction** if users request EXIF/metadata  
❌ **Skip if users don't care** - network overhead not worth it

### Estimated Effort: 8-12 hours

---

## Optimization 3: Network Trash Cleanup

**Priority:** Low (May be obsolete)  
**File:** [BrowseViewModel.kt:2520](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt)

### Problem

```kotlin
// TODO: Implement network trash cleanup when needed
```

### Questions

1. **Does app support network trash?** (Recycle bin for SMB/FTP/SFTP)
2. **Is this feature planned?**
3. **Should TODO be removed if not planned?**

### If Implementing

**Requirements:**

- Track deleted network files
- Implement "empty trash" operation
- Add trash expiration (auto-delete after 30 days)
- UI for browsing trash

**Estimated Effort:** 15-20 hours

### Recommendation

🔍 **Review with user** - Is network trash supported/planned?

- If NO → Remove TODO
- If YES → Create separate spec

---

## Optimization 4: SFTP Passphrase Support

**Priority:** Low  
**File:** [SftpOperationStrategy.kt:313](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt)

### Problem

```kotlin
passphrase = null // TODO: Add passphrase support to NetworkCredentialsEntity
```

Current state is mixed:

- SFTP client already supports SSH private key + passphrase.
- `SftpMediaScanner` already passes passphrase (reusing password field for key passphrase).
- `SftpOperationStrategy` still forces `passphrase = null`, so behavior is inconsistent across flows.

So this item is now **consistency + data model clarity** task, not pure "feature missing" task.

### Proposed Implementation

**Phase 1: Update Data Model**

```kotlin
@Entity(tableName = "network_credentials")
data class NetworkCredentialsEntity(
    // ... existing fields ...
    
    val sshKeyPath: String? = null,        // NEW: Path to private key file
    val sshKeyPassphrase: String? = null   // NEW: Passphrase for key
)
```

**Phase 2: UI for Key Selection**

```
Add SFTP Connection
  ├─ Server: example.com
  ├─ Port: 22
  ├─ Username: user
  ├─ Authentication Method:
  │   ○ Password
  │   ● SSH Key
  ├─ Private Key: [Browse...]
  └─ Passphrase: ********
```

**Phase 3: Update SftpClient**

```kotlin
fun connect(
    connectionInfo: SftpConnectionInfo,
    sshKeyPath: String? = null,
    passphrase: String? = null
): Result<Unit> {
    return if (sshKeyPath != null) {
        connectWithKey(connectionInfo, sshKeyPath, passphrase)
    } else {
        connectWithPassword(connectionInfo)
    }
}
```

### Impact

- **Security:** SSH keys are more secure than passwords
- **Convenience:** Users can use existing SSH keys
- **Complexity:** Adds key management UI

### Estimated Effort: 10-15 hours

---

## Priority Summary

| Optimization | Priority | Effort | Implement When |
|--------------|----------|--------|----------------|
| FTP Pagination | Medium | 4-6h | Users report slow FTP browsing |
| SMB EXIF/Metadata | Low | 8-12h | Users request EXIF for network files |
| Network Trash | ? | 15-20h | If feature is planned |
| SFTP Passphrase | Low | 6-10h | Need unified passphrase handling (separate field or explicit policy) |

---

## Recommended Approach

### Do NOT Implement Prematurely

These are optimizations, not bugs. Only implement if:

1. Users report performance issues
2. Analytics show slow load times
3. Feature requests come in

### Measure First

Before implementing optimizations:

1. Add performance metrics (load time, memory usage)
2. Identify actual bottlenecks
3. Measure impact after optimization

### Implement Incrementally

1. Start with **FTP Pagination** (easiest, high impact)
2. Then **SMB Metadata** if requested
3. Defer **SFTP Passphrase** unless users ask
4. Decide on **Network Trash** based on requirements

---

## Success Metrics

### FTP Pagination

- ✅ First page loads in <1s (was 5s+)
- ✅ Memory usage <50MB (was 200MB+)

### SMB Metadata

- ✅ EXIF extraction in <500ms per file
- ✅ No impact on initial browsing speed

### SFTP Passphrase

- ✅ SSH key authentication works
- ✅ Passphrase stored securely
- ✅ UI is intuitive

---

## Next Steps

1. 🔍 **Get explicit decision** on Network Trash feature (planned vs not planned)
2. 🧩 **Align SFTP passphrase behavior** across Scanner/Strategy (remove inconsistency)
3. 📊 **Add analytics** to measure real bottlenecks before optimization work
4. ✅ Implement only requested/validated items
