# Phase 02: Fail-fast + State Integration in SmbDataSource

**Goal:** wire `SmbPlaybackConnectionTracker` into `SmbDataSource.open()` / `read()` to implement the double-watchdog fail-fast and track PLAYER connection state.

## Steps

- [ ] 2.1 Add imports to `SmbDataSource.kt`
  - `import com.sza.fastmediasorter.data.network.model.ConnectionKey`

- [ ] 2.2 Add `connectionKey()` private helper
  ```kotlin
  private fun connectionKey() = ConnectionKey(
      server = connectionInfo.server, port = connectionInfo.port,
      shareName = connectionInfo.shareName, username = connectionInfo.username,
      domain = connectionInfo.domain
  )
  ```

- [ ] 2.3 Remove local `isTransportOrBrokenPipe()` companion method (lines 77–93)
  - Replace call-site in `openInternal()` with `SmbErrorClassifier.isTransportOrBrokenPipe(e)`

- [ ] 2.4 Update `open()`:
  - At top: build `key = connectionKey()`; check `smbClient.playbackConnectionTracker.isRecentWatchdog(key)` → throw `IOException("SMB playback fail-fast: watchdog timeout on previous attempt")` with `[SMB-PLAY]` log
  - Call `tracker.onConnectionCreated(key)` before submitting future
  - In watchdog `catch (te: TimeoutException)`: call `tracker.recordWatchdog(key)` BEFORE `invalidateExoPlayerConnection()`; log state name via `tracker.getStateName(key)`

- [ ] 2.5 Update `read()` watchdog `catch (te: TimeoutException)`:
  - Call `tracker.recordWatchdog(connectionKey())` before `invalidateExoPlayerConnection()`

- [ ] 2.6 Update `openInternal()` — on success (just before final `return`):
  - Call `tracker.clearWatchdog(key)` + `tracker.onConnectionValidated(key)`

## Verification Predicates

- [ ] `open()` throws `IOException` with message "SMB playback fail-fast" when `isRecentWatchdog` returns true
- [ ] `open()` watchdog timeout calls `recordWatchdog()` before `invalidateExoPlayerConnection()`
- [ ] `read()` watchdog timeout calls `recordWatchdog()`
- [ ] `openInternal()` success calls `clearWatchdog()` and `onConnectionValidated()`
- [ ] Local `isTransportOrBrokenPipe()` companion method is absent from `SmbDataSource`
- [ ] `SmbDataSource.kt` LOC < 1000
