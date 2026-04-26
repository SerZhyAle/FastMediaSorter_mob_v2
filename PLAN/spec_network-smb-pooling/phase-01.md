# Phase 01: SmbPlaybackConnectionTracker

**Goal:** introduce a singleton that tracks PLAYER connection lifecycle state and watchdog timestamps across `SmbDataSource` instances (ExoPlayer creates a new instance per retry).

## Steps

- [ ] 1.1 CREATE `data/network/SmbPlaybackConnectionTracker.kt`
  - `@Singleton @Inject constructor()`
  - `PlaybackConnectionState` enum: `FRESH`, `VALIDATED`, `SUSPECT`, `INVALIDATED`
  - `ConcurrentHashMap<ConnectionKey, PlaybackConnectionState>` for state
  - `ConcurrentHashMap<ConnectionKey, Long>` for watchdog timestamps
  - API: `onConnectionCreated(key)`, `onConnectionValidated(key)`, `onConnectionInvalidated(key)`
  - API: `recordWatchdog(key)`, `clearWatchdog(key)`, `isRecentWatchdog(key): Boolean`
  - API: `getStateName(key): String` — returns state name for log context without exposing enum externally
  - API: `clearAll()` — called on network reset
  - Predicate: `WATCHDOG_WINDOW_MS = 60_000L`

- [ ] 1.2 EDIT `SmbConnectionManager.kt`
  - Constructor: add `private val playbackTracker: SmbPlaybackConnectionTracker`
  - `resetAllConnections()`: add `playbackTracker.clearAll()`
  - `handleNetworkReconnect()`: add `playbackTracker.clearAll()`
  - Fix garbled comment on `close()` method (lines 992–995) → single-line KDoc

- [ ] 1.3 EDIT `SmbClient.kt`
  - Constructor: add `internal val playbackConnectionTracker: SmbPlaybackConnectionTracker`

## Verification Predicates

- [ ] `SmbPlaybackConnectionTracker.kt` compiles (Hilt singleton, no-arg constructor)
- [ ] `SmbClient.playbackConnectionTracker` field exists and is accessible from `SmbDataSource`
- [ ] `SmbConnectionManager` constructor has `SmbPlaybackConnectionTracker` param
- [ ] `resetAllConnections()` calls `playbackTracker.clearAll()`
- [ ] `handleNetworkReconnect()` calls `playbackTracker.clearAll()`
- [ ] `SmbConnectionManager.kt` LOC ≤ 1000
