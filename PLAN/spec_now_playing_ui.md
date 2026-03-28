# Specification: III.13 — Now Playing UI

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 3 — Moderate (4–8h, medium risk)
**Roadmap entry:** Audio queue, bottom sheet or notification with track info

---

## 1. Problem Statement

`AudioPlaybackService` provides background audio playback, but once the user leaves `PlayerActivity` there is no in-app UI to see what is playing, control it, or inspect/navigate the queue. The system notification (via `DefaultMediaNotificationProvider`) shows play/pause + skip but has no cover art and no queue visibility. The "background audio" feature is functionally incomplete without a rich Now Playing surface.

---

## 2. Goals

1. **Now Playing bottom sheet** — album art, title, artist, seek bar, play/pause, prev/next, queue count.
2. **Queue panel** (inside the sheet) — ordered list of upcoming tracks, tap to jump.
3. **Mini Now Playing bar** — persistent 56 dp bar attached to `PlayerActivity` and optionally `BrowseFragment`/`MainActivity`, visible only while `AudioPlaybackService.isRunning`.
4. **Enriched notification** — show album art in the system media notification.
5. **Full playlist passthrough** — `PlayerActivity` passes the entire audio file list as a playlist to the service instead of one file at a time, enabling native next/prev navigation.

Non-goals for this spec: shuffle toggle (separate future item), crossfade, equaliser, sleep-timer changes (already exists in `SleepTimerManager`).

---

## 3. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `AudioPlaybackService` | `ui/player/AudioPlaybackService.kt` | `MediaSessionService`; owns `ExoPlayer` + `MediaSession`; `ForwardingPlayer` handles single-file next/prev |
| `AudioServiceController` | `ui/player/helpers/AudioServiceController.kt` | Creates `MediaController`; exposes `Player` to `PlayerActivity` |
| `MediaNotificationManager` | `ui/player/MediaNotificationManager.kt` | `DefaultMediaNotificationProvider` with custom channel; no album art yet |
| `PlayerViewModel.PlayerState` | `ui/player/PlayerViewModel.kt` | `enablePersistentAudioPlayback` flag; file list + current index |
| `BackgroundMusicManager` | `ui/player/helpers/BackgroundMusicManager.kt` | Slideshow background music — separate ExoPlayer, separate concern |
| `SleepTimerManager` | `ui/player/helpers/SleepTimerManager.kt` | Vinyl animation + countdown timer |

**Key limitation:** `AudioPlaybackService.playAudio(uri)` sets a single `MediaItem` without `MediaMetadata` (title, artist, album art). The `ForwardingPlayer` simulates next/prev by seeking to end, which notifies `PlayerActivity` to advance. This works but means the service queue is always size 1.

---

## 4. Proposed Architecture

### 4.1 Playlist passthrough

Change the audio playback entry point from single-file to full-playlist:

```
PlayerActivity (audio file)
  → AudioServiceController.playAudioPlaylistWithMetadata(
        items: List<MediaItemWithMeta>,   // title, artist, artUri per file
        startIndex: Int
     )
  → AudioPlaybackService: ExoPlayer.setMediaItems(items)  // N items
  → ForwardingPlayer: seekToNext/seekToPrevious now work natively (N > 1)
```

`MediaItemWithMeta` data class (new, in `ui/player/model/`):
```kotlin
data class MediaItemWithMeta(
    val uri: Uri,
    val title: String,         // file name without extension, or ID3 tag
    val artist: String?,       // ID3 tag or null
    val albumArtUri: Uri?      // cached cover art URI or null
)
```

`MediaItem` is built with `MediaItem.Builder().setUri(uri).setMediaMetadata(...)` so `MediaController.mediaMetadata` is populated for the notification and the Now Playing sheet.

**Backward compatibility:** the existing `playAudio(uri)` / `playAudioPlaylist(uris)` methods stay unchanged (still used by `StandalonePlayerActivity` and tests). New method added alongside.

### 4.2 New classes

| Class | Location | Lines budget |
|-------|----------|-------------|
| `NowPlayingViewModel` | `ui/player/NowPlayingViewModel.kt` | ≤ 200 |
| `NowPlayingBottomSheetFragment` | `ui/player/NowPlayingBottomSheetFragment.kt` | ≤ 300 |
| `NowPlayingManager` | `ui/player/helpers/NowPlayingManager.kt` | ≤ 250 |
| `QueueTrackAdapter` | `ui/player/helpers/QueueTrackAdapter.kt` | ≤ 150 |
| `MediaItemWithMeta` | `ui/player/model/MediaItemWithMeta.kt` | ≤ 30 |
| `bottom_sheet_now_playing.xml` | `res/layout/` | — |
| `item_queue_track.xml` | `res/layout/` | — |

### 4.3 `NowPlayingViewModel`

```kotlin
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class NowPlayingState(
        val isPlaying: Boolean = false,
        val title: String = "",
        val artist: String? = null,
        val albumArtUri: Uri? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val queueItems: List<QueueItem> = emptyList(),
        val currentQueueIndex: Int = 0,
        val serviceRunning: Boolean = false
    )

    data class QueueItem(
        val index: Int,
        val title: String,
        val artist: String?
    )

    val state: StateFlow<NowPlayingState>

    // Connect/disconnect lifecycle matches Fragment/Activity lifecycle
    fun connect()
    fun disconnect()
    fun togglePlayPause()
    fun seekToNext()
    fun seekToPrevious()
    fun seekTo(positionMs: Long)
    fun jumpToQueueItem(index: Int)
}
```

`NowPlayingViewModel` creates its own `MediaController` connection to `AudioPlaybackService` using `SessionToken`. It polls position every 500 ms via `viewModelScope` while playing (no polling when paused/stopped). On `Player.Listener.onMediaItemTransition` it updates `title`, `artist`, `albumArtUri`, `currentQueueIndex`. On `onPlaybackStateChanged` / `onIsPlayingChanged` it updates `isPlaying` / `serviceRunning`.

Position polling replaces a Flow because `MediaController.currentPosition` is a direct property, not a Flow.

### 4.4 `NowPlayingManager`

Injected into `PlayerActivity` (alongside existing managers). Responsibilities:

- Shows/hides the **mini Now Playing bar** (a `View` stub anchored at the bottom of `activity_player_unified.xml`).
- Launches `NowPlayingBottomSheetFragment` when the bar is tapped.
- Observes `AudioPlaybackService.isRunning` to show/hide the bar.
- Calls `AudioServiceController.playAudioPlaylistWithMetadata(...)` when the Activity starts audio playback.

The manager holds no ExoPlayer reference — all playback commands go through `NowPlayingViewModel` → `MediaController`.

### 4.5 `NowPlayingBottomSheetFragment`

`BottomSheetDialogFragment` with two panels toggled by a "Queue" button:

**Panel A — Now Playing:**
- `ShapeableImageView` (album art, 120 dp, rounded corners)
- `TextView` title (single line, marquee if overflow)
- `TextView` artist (single line, secondary style)
- `SeekBar` + position/duration `TextViews`
- Play/pause `ImageButton`, prev/next `ImageButton`s (standard media icons)
- "Queue (N)" button → switches to Panel B

**Panel B — Queue:**
- `RecyclerView` + `QueueTrackAdapter`
- Current track highlighted; tap any row → `NowPlayingViewModel.jumpToQueueItem(index)`
- "Now Playing" button → switches back to Panel A

Both panels share the same `NowPlayingBottomSheetFragment`; visibility toggled in code (no fragment transactions).

### 4.6 Mini Now Playing bar

Added to `activity_player_unified.xml` as a `ConstraintLayout` (height 56 dp) placed above the existing bottom controls, hidden by default (`visibility="gone"`):

```xml
<include layout="@layout/view_mini_now_playing"
         android:id="@+id/miniNowPlayingBar"
         ... />
```

Contents: small thumbnail (32 dp), title `TextView`, play/pause `ImageButton`. Tapping anywhere → open `NowPlayingBottomSheetFragment`.

### 4.7 Enriched notification (album art)

`MediaNotificationManager.createNotificationProvider()` currently returns `DefaultMediaNotificationProvider` which automatically shows `MediaMetadata.artworkUri` if it resolves to a loadable bitmap. No code change required here — the art appears automatically once `MediaItem.mediaMetadata.artworkUri` is set.

For **network/cache art URIs** (not directly loadable by the system): convert to a `file://` URI pointing to the cached file before building `MediaItemWithMeta.albumArtUri`. The Media3 notification system resolves `file://` URIs natively.

---

## 5. Data Flow

```
PlayerActivity.loadAudio(file, index)
  ↓
NowPlayingManager.startPlayback(files, currentIndex)
  ↓
AudioServiceController.playAudioPlaylistWithMetadata(items, startIndex)
  ↓  (starts service if needed)
AudioPlaybackService.ExoPlayer.setMediaItems(mediaItemsWithMeta)
  ↓
MediaSession exposes state
  ↓
NowPlayingViewModel.MediaController observes ←—— NowPlayingBottomSheetFragment
  ↓
NowPlayingManager.miniBar.update(state)
```

---

## 6. Files to Modify

| File | Change |
|------|--------|
| `AudioPlaybackService.kt` | No logic changes; `ForwardingPlayer` single-item fallback can be removed when N>1 items in queue — keep it as fallback for single-file usage |
| `AudioServiceController.kt` | Add `playAudioPlaylistWithMetadata(items: List<MediaItemWithMeta>, startIndex: Int, onPlayerReady: (Player) -> Unit)` |
| `PlayerActivity.kt` | Inject `NowPlayingManager`; call `NowPlayingManager.startPlayback(...)` instead of direct `AudioServiceController.playAudio(...)` when background playback is ON |
| `activity_player_unified.xml` | Add `<include>` for `view_mini_now_playing.xml` above bottom controls |

---

## 7. Risk Analysis

| Risk | Mitigation |
|------|-----------|
| `MediaController` connection race (sheet opened before service starts) | `NowPlayingViewModel.connect()` handles `null` controller gracefully; shows loading state |
| Position polling battery impact | Only poll while `isPlaying == true`; 500 ms interval is standard (ExoPlayer UI uses 200 ms); cancel via `viewModelScope` on `disconnect()` |
| ForwardingPlayer interaction with N-item playlist | `ForwardingPlayer` override checks `mediaItemCount <= 1` before intercepting; N > 1 falls through to native ExoPlayer next/prev — no conflict |
| Cover art for network files | Resolved to cached `file://` URI before building `MediaItem`; fallback to `null` (system notification shows placeholder) |
| Activity re-entry after background navigation | `NowPlayingManager.connect()` called in `onStart`, `disconnect()` in `onStop`; `ViewModel` survives config changes |
| `BackgroundMusicManager` conflict | That manager uses its own private ExoPlayer; completely independent — no conflict |

---

## 8. Implementation Steps

1. **Create `MediaItemWithMeta`** data class.
2. **Add `playAudioPlaylistWithMetadata()`** to `AudioServiceController`.
3. **Create `NowPlayingViewModel`** — `MediaController` connection + state flow + position poll.
4. **Create `QueueTrackAdapter`** — simple `ListAdapter<QueueItem>`.
5. **Create layouts**: `bottom_sheet_now_playing.xml`, `item_queue_track.xml`, `view_mini_now_playing.xml`.
6. **Create `NowPlayingBottomSheetFragment`** — binds to `NowPlayingViewModel`.
7. **Create `NowPlayingManager`** — injects into `PlayerActivity`; mini-bar show/hide; sheet launch.
8. **Modify `PlayerActivity`** — replace direct `AudioServiceController.playAudio()` call with `NowPlayingManager.startPlayback()` when background mode is ON.
9. **Modify `activity_player_unified.xml`** — include mini-bar stub.
10. **Add string resources** for EN/RU/UK (`now_playing`, `queue_label`, `track_count`).
11. Run `.\scripts\add_to_dev_log.ps1` after each modified file.

---

## 9. Out of Scope (future items)

- Shuffle button in the queue panel (depends on `AudioPlaybackService` repeat/shuffle state — separate change)
- Now Playing bar in `BrowseFragment` / `MainActivity` (requires passing `NowPlayingManager` across screen boundaries; deferred)
- Drag-to-reorder queue (depends on III.10 Drag-and-drop)
- Sleep timer control from the sheet (already available in PlayerActivity; can be added later)
