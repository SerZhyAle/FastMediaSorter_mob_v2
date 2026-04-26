# Phase 03: Fix reopenConnection() — Use Pool Manager

**Goal:** replace the raw `SMBClient` creation in `SmbDataSource.reopenConnection()` with pool manager calls, eliminating the bypass of `SmbConnectionManager`.

## Steps

- [ ] 3.1 Extract `resolveSmbPath(uri: Uri): String` private method from `openInternal()`
  - Logic: decode `uri.encodedPath`, strip leading slash, strip share prefix if present
  - Used in both `openInternal()` and `reopenConnection()`

- [ ] 3.2 Rewrite `reopenConnection()`:
  - Close only `file` (pool-managed share/session/connection must NOT be closed here)
  - Call `smbClient.connectionManager.invalidateExoPlayerConnection(connectionInfo)` to purge stale entry
  - Call `smbClient.connectionManager.getConnectionForExoPlayer(connectionInfo)` to get fresh pooled conn
  - Update `share` reference from new pooled connection
  - Re-open `file` using `resolveSmbPath(uri!!)`
  - Reset `internalBufferPosition` + `internalBufferValidBytes`
  - Log with `[SMB-PLAY]` prefix
  - Remove all: raw `SMBClient` construction, `SmbConfig.builder()`, `Connection`/`Session` local vars, `Thread.sleep()` calls

- [ ] 3.3 Update `openInternal()` path resolution to use `resolveSmbPath()`:
  - Replace inline path resolution (lines 184–206) with `resolveSmbPath(uri)` call
  - Keep existing Timber.d log lines (add Timber call for finalPath if needed)

## Verification Predicates

- [ ] `reopenConnection()` contains NO `SMBClient(`, `SmbConfig.builder()`, `connection =`, `session =` assignments to locally created instances
- [ ] `reopenConnection()` calls `invalidateExoPlayerConnection()` before `getConnectionForExoPlayer()`
- [ ] `resolveSmbPath()` method exists
- [ ] `openInternal()` uses `resolveSmbPath()` (no inline path resolution duplication)
- [ ] `reopenConnection()` contains NO `Thread.sleep()`
