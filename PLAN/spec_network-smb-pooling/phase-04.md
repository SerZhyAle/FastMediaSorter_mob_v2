# Phase 04: SmbPlaybackErrorCategory + Log Tagging

**Goal:** add `SmbPlaybackErrorCategory` enum to `SmbErrorClassifier` and ensure all playback-path watchdog logs are tagged `[SMB-PLAY]` with enough context to distinguish failure types.

## Steps

- [ ] 4.1 Add to `SmbErrorClassifier.kt`:
  ```kotlin
  enum class SmbPlaybackErrorCategory {
      STALE_POOL_CONNECTION,   // watchdog on VALIDATED state — TCP was silently dropped
      NEW_CONNECTION_TIMEOUT,  // watchdog on FRESH state — network loss or very slow NAS
      TRANSPORT_FAILURE,       // transport/broken-pipe error during openFile
      AUTH_CONFIG,             // auth/access/share-not-found
      UNKNOWN
  }
  ```

- [ ] 4.2 Verify `[SMB-PLAY]` prefix is present in all new Timber log lines added in Phases 01–03
  - `SmbPlaybackConnectionTracker` logs
  - `open()` fail-fast log
  - `open()` watchdog log (with state name)
  - `read()` watchdog log
  - `reopenConnection()` start/end logs

## Verification Predicates

- [ ] `SmbPlaybackErrorCategory` enum exists in `SmbErrorClassifier.kt`
- [ ] `grep -c "\[SMB-PLAY\]"` in `SmbDataSource.kt` > 3
- [ ] `SmbErrorClassifier.kt` LOC < 200
