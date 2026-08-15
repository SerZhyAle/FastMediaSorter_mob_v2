# PHASE 02 — Position Save Inside AudioPlaybackService

**Ticket:** S0172  
**Phase:** 02 of 04  
**Pillar:** B — SFTP audio position saving

---

## Goal

Persist the playback position of SFTP (and SMB/FTP) audio files during service playback so it survives crashes and restarts. Save periodically every 15 s and immediately on track change / service destroy.

---

## Context

`AudioPlaybackService` creates its own `ExoPlayer` instance. The Activity-side position-save loop (`PlaybackPositionHelper.startPositionSaving()`) operates only on `VideoPlayerManager`'s `ExoPlayer` — not on the service player. For SFTP audio, the service player is the only active player, so position is never saved.

`PlaybackPositionRepository` is injected in `PlayerActivity` and in `VideoPlayerManager` via Hilt. `AudioPlaybackService` currently has no access to it. This phase injects it and adds the save loop.

Key constants from `VideoPlayerManager`:
- `POSITION_SAVE_INTERVAL_MS = 15_000L` — reuse as the service save interval.

The **key** used for `savePosition` / `getPosition` must be the **original SFTP path** (e.g. `sftp://host:port/path/file.mp3`), not the local cache path. The original path is supplied to the service externally. Add a companion field to carry it.

---

## Steps

### Step 2.1 — Inject `PlaybackPositionRepository` into `AudioPlaybackService`

- [ ] Open `AudioPlaybackService.kt`.
- [ ] Add `@Inject` field:

```kotlin
@Inject
lateinit var playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
```

- [ ] Add `@AndroidEntryPoint` annotation to the class **if not already present**.
  - Check: `grep -n "AndroidEntryPoint" AudioPlaybackService.kt`
  - If missing, add `@AndroidEntryPoint` above the class declaration and add the import `dagger.hilt.android.AndroidEntryPoint`.
- [ ] Add import `javax.inject.Inject`.
- [ ] **Verification:** `grep -n "@Inject" AudioPlaybackService.kt` — `playbackPositionRepository` is listed.

### Step 2.2 — Add `currentOriginalPath` companion field

`AudioPlaybackService` needs to know the original SFTP URL for DB keying (the URI passed to ExoPlayer is a local `file://` cache path).

- [ ] In the `companion object` of `AudioPlaybackService`, add:

```kotlin
/** Original network path (sftp:// / smb:// / ftp://) of the currently playing file.
 *  Set by PlayerMediaLoaderManager before starting playback so position can be
 *  saved/restored using a stable, cache-path-independent key. Empty string = unknown. */
@Volatile
var currentOriginalPath: String = ""
```

- [ ] **Verification:** `grep -n "currentOriginalPath" AudioPlaybackService.kt` — one declaration in companion, no other occurrences yet.

### Step 2.3 — Add position-save infrastructure fields

- [ ] In the `AudioPlaybackService` class body (next to `autoStopHandler`), add:

```kotlin
private val positionSaveHandler = Handler(Looper.getMainLooper())
private var positionSaveRunnable: Runnable? = null
private var lastSavedPosition: Long = -1L

private companion object {
    const val POSITION_SAVE_INTERVAL_MS = 15_000L  // mirrors VideoPlayerManager
}
```

Note: if `companion object` already exists in the class body (separate from the public companion above), merge or add a private const elsewhere. Keep `POSITION_SAVE_INTERVAL_MS` as a private constant.

- [ ] **Verification:** file compiles.

### Step 2.4 — Implement `startPositionSaving()` / `stopPositionSaving()` / `saveCurrentPosition()`

- [ ] Add three private methods to `AudioPlaybackService`:

```kotlin
private fun startPositionSaving() {
    stopPositionSaving()
    positionSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPosition()
            positionSaveHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }
    positionSaveHandler.postDelayed(positionSaveRunnable!!, POSITION_SAVE_INTERVAL_MS)
    Timber.d("AudioPlaybackService: position auto-save started for path=$currentOriginalPath")
}

private fun stopPositionSaving() {
    positionSaveRunnable?.let {
        positionSaveHandler.removeCallbacks(it)
        positionSaveRunnable = null
        Timber.d("AudioPlaybackService: position auto-save stopped")
    }
}

private fun saveCurrentPosition() {
    val path = currentOriginalPath.takeIf { it.isNotEmpty() } ?: return
    val p = player ?: return
    val position = p.currentPosition
    val duration = p.duration
    // Skip unchanged position — avoids redundant DB writes while paused
    if (position == lastSavedPosition) return
    if (duration > 0 && position >= 0) {
        lastSavedPosition = position
        // IO dispatcher — never block main thread
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                playbackPositionRepository.savePosition(path, position, duration)
            } catch (e: Exception) {
                Timber.e(e, "AudioPlaybackService: failed to save position")
            }
        }
    }
}
```

> Note on `GlobalScope`: `AudioPlaybackService` does not have a `lifecycleScope`. Using `GlobalScope.launch(Dispatchers.IO)` is acceptable for fire-and-forget DB writes here. Add `@OptIn(DelicateCoroutinesApi::class)` or suppress if Lint flags it.

- [ ] Add imports: `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.GlobalScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.DelicateCoroutinesApi` (if needed).
- [ ] **Verification:** file compiles.

### Step 2.5 — Hook `startPositionSaving` / `stopPositionSaving` into Player listener

- [ ] In the existing `exoPlayer.addListener(object : Player.Listener { ... })` block inside `onCreate()`, update `onPlaybackStateChanged`:

```kotlin
override fun onPlaybackStateChanged(playbackState: Int) {
    Timber.d("AudioPlaybackService: playbackState=$playbackState")
    when (playbackState) {
        Player.STATE_READY -> {
            autoStopHandler.removeCallbacks(autoStopRunnable)
            // S0172: start periodic position save when player is ready
            startPositionSaving()
        }
        Player.STATE_BUFFERING -> {
            autoStopHandler.removeCallbacks(autoStopRunnable)
            // Don't start saving yet — wait for STATE_READY
        }
        Player.STATE_ENDED -> {
            // S0172: stop save loop; save final position before track ends
            stopPositionSaving()
            saveCurrentPosition()
            autoStopHandler.removeCallbacks(autoStopRunnable)
            autoStopHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
        }
        Player.STATE_IDLE -> {
            stopPositionSaving()
        }
    }
}
```

Also add:

```kotlin
override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (!isPlaying) {
        // Save position on pause so it survives a kill
        saveCurrentPosition()
    }
}
```

- [ ] **Verification:** `grep -n "startPositionSaving\|stopPositionSaving" AudioPlaybackService.kt` — both appear inside the listener.

### Step 2.6 — Save final position in `onDestroy`

- [ ] In `onDestroy()`, before `mediaSession?.run { player.release(); release() }`, add:

```kotlin
// S0172: persist final position before ExoPlayer is released
stopPositionSaving()
saveCurrentPosition()
```

Because `saveCurrentPosition()` dispatches to IO and returns immediately, the DB write may race with `player.release()`. To avoid reading from a released player: capture position/duration before releasing.

Refactor the save to capture synchronously:

```kotlin
// S0172: capture position before release
val p = player
val path = currentOriginalPath.takeIf { it.isNotEmpty() }
val finalPos = p?.currentPosition ?: -1L
val finalDur = p?.duration ?: -1L
stopPositionSaving()

if (path != null && finalDur > 0 && finalPos >= 0 && finalPos != lastSavedPosition) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            playbackPositionRepository.savePosition(path, finalPos, finalDur)
        } catch (e: Exception) {
            Timber.e(e, "AudioPlaybackService: onDestroy save position failed")
        }
    }
}
```

Place this block before the `mediaSession?.run { ... }` release block.

- [ ] **Verification:** `grep -n "onDestroy" AudioPlaybackService.kt` — position capture block appears before `player.release()` line.

### Step 2.7 — Set `currentOriginalPath` from `PlayerMediaLoaderManager`

- [ ] Open `PlayerMediaLoaderManager.kt`.
- [ ] In the SFTP/SMB/FTP branch of `playAudioViaService()`, right before `controller.playAudioWithMetadata(uri, netTitle) { ... }`, add:

```kotlin
// S0172: store original network path so service can key position DB correctly
AudioPlaybackService.currentOriginalPath = path
```

- [ ] For the cloud branch (if applicable), set `currentOriginalPath = path` similarly.
- [ ] Reset on local audio path: in `playLocalAudioViaService()`, add:

```kotlin
AudioPlaybackService.currentOriginalPath = ""  // local: position keyed by local path in VideoPlayerManager
```

- [ ] **Verification:** `grep -n "currentOriginalPath" PlayerMediaLoaderManager.kt` — appears in SFTP branch and local-reset branch.

### Step 2.8 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt" "AudioPlaybackService" "S0172 Phase 02: inject PlaybackPositionRepository, add periodic position save for SFTP audio"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt" "PlayerMediaLoaderManager" "S0172 Phase 02: set AudioPlaybackService.currentOriginalPath before SFTP playback"
```

---

## Verification summary

| Check | Command / signal |
|-------|-----------------|
| `@Inject playbackPositionRepository` in service | `grep -n "@Inject" AudioPlaybackService.kt` |
| `currentOriginalPath` companion field | `grep -n "currentOriginalPath" AudioPlaybackService.kt` |
| Save loop started on `STATE_READY` | `grep -n "startPositionSaving" AudioPlaybackService.kt` inside listener |
| Final save in `onDestroy` before release | `grep -n "finalPos\|onDestroy" AudioPlaybackService.kt` — ordering correct |
| Path set in `PlayerMediaLoaderManager` | `grep -n "currentOriginalPath" PlayerMediaLoaderManager.kt` |
