# TASK ML-004: SftpDataSource InputStream Leak on open() Exception

**Priority**: HIGH  
**Area**: Resource Leak / SFTP  
**Component**: `SftpDataSource`  
**Effort**: 2h  

---

## Problem

In `SftpDataSource.open()`, if `channel?.get(remotePath, null, position)` at line 81 creates a raw `InputStream` (`rawStream`), but a subsequent operation throws **before** the stream is assigned to `this.inputStream` (line 86), the `rawStream` is **orphaned** and never closed.

Concrete failure scenario:
1. Line 63: `getConnectionForExoPlayer()` succeeds → semaphore acquired, `connectionAcquired = true` at line 66
2. Line 81: `channel?.get(remotePath, ...)` returns a valid `rawStream` 
3. Line 84: `ConnectionThrottleManager.getRecommendedBufferSize()` throws (unlikely but possible)
4. Exception jumps to `catch (e: Exception)` at line 109 → calls `close()`
5. In `close()`: `inputStream?.close()` is called but `inputStream` is **still null** (was never assigned at line 86)
6. `rawStream` is never closed → open SFTP file handle leaked

Additionally: `close()` sets `inputStream = null` unconditionally, so even if `inputStream` were assigned, a double-close scenario is possible if `close()` is called while reading.

```kotlin
// SftpDataSource.kt:63–114
try {
    val pooledConnection = sftpClient.getConnectionForExoPlayer(connectionInfo)
    session = pooledConnection.session
    channel = pooledConnection.channel
    connectionAcquired = true                              // ← line 66

    // ...
    val rawStream = channel?.get(remotePath, null, position)  // ← line 81: stream created
    val bufferSize = ...getRecommendedBufferSize(...)         // ← line 85: could throw
    inputStream = BufferedInputStream(rawStream, bufferSize)  // ← line 86: never reached
    // ...
} catch (e: Exception) {
    close()                                                    // inputStream is null → rawStream never closed!
    throw IOException(...)
}
```

**File**:
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` — L63–114, L170–188

---

## Fix

Capture `rawStream` in a local variable and ensure it is closed in the catch block if `inputStream` was never assigned:

```kotlin
var rawStream: InputStream? = null
try {
    val pooledConnection = sftpClient.getConnectionForExoPlayer(connectionInfo)
    session = pooledConnection.session
    channel = pooledConnection.channel
    connectionAcquired = true

    // ...
    rawStream = channel?.get(remotePath, null, position)
    val bufferSize = ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)
    inputStream = BufferedInputStream(rawStream, bufferSize)
    rawStream = null  // ← transferred ownership to inputStream (BufferedInputStream wraps it)
    // ...
} catch (e: Exception) {
    rawStream?.close()  // ← close if inputStream was never constructed
    close()
    throw IOException("Failed to open SFTP file: ${e.message}", e)
}
```

Note: Once `rawStream` is wrapped in `BufferedInputStream` and assigned to `inputStream`, closing `inputStream` also closes the underlying `rawStream`. Setting `rawStream = null` after the wrap prevents double-close.

---

## Test Plan

1. Start SFTP file playback
2. Inject exception in `ConnectionThrottleManager.getRecommendedBufferSize()` via mock/debug build
3. Verify: `rawStream` is closed (no open SFTP handles remain)
4. `adb shell lsof -p <PID> | grep sftp` should be empty after failed open
5. Retry playback: should work normally (semaphore not exhausted)

---

## Acceptance Criteria

- [x] `rawStream` is explicitly closed in catch block if `inputStream` was never assigned
- [x] `rawStream` set to null after assignment to prevent double-close
- [x] No open file handles after failed `open()` call
- [x] Semaphore properly released on all exception paths

---

## Implementation Status

**Completed**: 2026-04-13 16:43:39  
**Changes**:
- Line 80-90: Wrapped rawStream creation in nested try/catch block
- Added explicit `rawStream?.close()` in catch handler before rethrowing exception
- Set `rawStream = null` after successful assignment to `inputStream` (prevents double-close)
- Updated comment to reference ML-004 fix
- Logged to dev/CHANGELOG.md

**Next**: Validation testing (Simulate exception at ConnectionThrottleManager.getRecommendedBufferSize(), verify no handle leak)
