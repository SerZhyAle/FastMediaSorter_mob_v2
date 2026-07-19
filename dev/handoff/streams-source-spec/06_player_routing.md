# Streams Source Spec - 06 - Player Routing (radio vs video/RTSP, buffering, recovery, cast)

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file documents what
happens when a channel is **played**: the AUDIO (radio) vs VIDEO/RTSP split, the inline mini-player, the
fullscreen video/RTSP player, the ExoPlayer/Media3 setup, bandwidth-adaptive buffering, live-edge
recovery, the trimmed control profile, casting, and the protocol-support matrix.

The browse screen is in `05_ui_streams_screen.md`; the catalog/`media_kind` classification is in
`03_catalog_format.md`. Facts cite `path:line` (root `p:\ANDROID\FastMediaSorter_mob_v2`). Player module is
`app_v2` only (no stream playback exists in `wear`).

---

## 0. Naming-collision warning (read first)

The codebase uses "stream" for **two unrelated subsystems**:
- **(A) Internet stream channels** - the "Трансляции" catalog (`mediaKind` AUDIO/VIDEO/RTSP,
  `ResourceType.HTTP_STREAM`/`RTSP_STREAM`). **This document is subsystem A.**
- **(B) "Streaming playback" of a bounded SMB/SFTP/FTP/Cloud network file** without full pre-download, with
  an optional "offload to local cache" (ticket family S0116): `StreamOffloadUseCase`,
  `StreamOffloadOfferDialog`, `StreamingCacheCleanupHelper`, `di/StreamingModule` (`StreamingPipeline`).
  **Not** internet channels; ignore for the streams feature.

---

## 1. Launch decision: AUDIO -> inline, VIDEO/RTSP -> fullscreen

### 1.1 The routing predicate

`StreamsActivity.onPlay(source)` (`:485-520`):
```kotlin
if (source.mediaKind == "AUDIO" && inlineAudio.playingId == source.id) { inlineAudio.stop(); return }  // S0690 re-tap
if (!viewModel.hasNetworkForStream()) { toast(no_network); return }                                     // S0711 offline
if (source.mediaKind == "AUDIO") { inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled()); return }
// VIDEO / RTSP:
startActivity(PlayerActivity.createIntent(resourceId = SyntheticResourceIds.STREAM, // -200L
    initialFilePath = source.url, skipAvailabilityCheck = true, enterFullscreen = true))              // S0694
```
- A **bare string comparison** `mediaKind == "AUDIO"` (not an enum). Anything else (`VIDEO`/`RTSP`) -> the
  fullscreen player.
- The home-screen shortcut / `ACTION_PLAY_STREAM` deep-link funnels through the same predicate (resolves the
  URL to a `StreamSourceEntity`, emits `PlayRequested` -> `onPlay`).
- **Duplicated** in `MainStreamsInlineAudioManager.playChannel` (`:52-69`) for the main-window pinned panel
  (same AUDIO/non-AUDIO fork, same inline-audio engine). *(A reuse should centralize this decision once.)*

### 1.2 Player-side scheme classification (independent of `mediaKind`)

Inside the fullscreen player, protocol is re-derived from the URI scheme
(`PlayerMediaViewVisibilityHelper.determineResourceType`): `http://`/`https://` -> `HTTP_STREAM`,
`rtsp://` -> `RTSP_STREAM`. `VideoPlayerManager.playVideo` dispatches both to `playStreamVideo(path, ...)`.
A live stream sets `isDynamicStream = true`, which **suppresses saved-position restore/auto-save** (a live
stream has no meaningful position). A defensive fallback (`PlayerMediaFilesLoader`, S0592) re-derives
`MediaType` from the stored `mediaKind` so an AUDIO URL reaching the player via an external `ACTION_VIEW`
still plays as audio.

### 1.3 Channel navigation in the player (S0640)

When the fullscreen player is opened against `SyntheticResourceIds.STREAM`, the **top-panel** Previous/Next
buttons page the **video-only catalog** (`observeStreamSources().filter { mediaKind != "AUDIO" }`, in the
Streams-screen default order) and re-source the player - not single-item file navigation. If the launch URL
isn't in the catalog (ad-hoc/removed), it falls back to a single-item list.

---

## 2. Inline audio (radio) subsystem

`StreamInlineAudioManager` owns the sticky bottom mini-control (used by both the Streams screen and the
main-window panel). Two backends, chosen per-play by `useBackgroundService`:
- **ON (background)**: `AudioServiceController` -> `AudioPlaybackService` (a Media3 `MediaSessionService`)
  via `MediaController`; survives screen-off/Activity destroy. Gated by
  `isPersistentAudioSettingOn() && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` (S0577).
- **OFF (in-app)**: a locally-owned `ExoPlayer` (`CONTENT_TYPE_MUSIC`/`USAGE_MEDIA`, `handleAudioFocus`,
  `handleAudioBecomingNoisy`, S0896); **released** (not just stopped) on stop/re-play/screen-stop.

Both route http(s) through the **same** `StreamDataSourceFactoryProvider.create(context)` (section 3.2) -
the background-service path via `NetworkAwareMediaSourceFactory`'s http/https branch, the in-app path
directly. (Sharing it was a fix, S1015 - a duplicate one-off factory once dropped the cross-protocol
redirect + Basic-Auth handling and turned Icecast 30x redirects into fatal errors.)

### 2.1 Lifecycle & re-tap
- `play` tears down prior playback but keeps the service `MediaController` warm; `stop` releases the local
  player or quiesces the service player (`playWhenReady=false; stop(); clearMediaItems()`).
- **Re-tap to stop** (S0690) is implemented in the caller (checks `playingId == source.id`), not inside the
  manager. The mini-control button is stop-only (there is no pause; a play is a full re-`play()`).

### 2.2 ICY / Shoutcast now-playing metadata
Both inline and fullscreen parse ICY the same Media3-native way: `Player.Listener.onMetadata` picking
`IcyInfo` entries; the mini-control renders `"<title> - <track>"`. **Precondition**: the request header
`Icy-MetaData: 1` (section 3.2) - without it, servers emit no in-band metadata. The metadata is **UI-only**
for the inline path - it is **not** pushed into the `MediaSession`, so the lock-screen/notification keeps
the static title. The fullscreen path additionally rewrites the live `MediaItem` title in place
(`replaceMediaItem` on the same URI = metadata-only update, no re-buffer) and logs `IcyHeaders` once.

### 2.3 Background governance & exit (S0577)
`AudioExitBehaviorResolver.resolve(serviceAudioActive, player, behavior)`:
- OFF-mode (not service) audio always stops with the screen - no dialog.
- A user-paused service track (READY/ENDED, not playing, not buffering) is an implicit stop on exit; only a
  still-connecting (BUFFERING) track honors the preference.
- Else `ALWAYS_STOP` / `ALWAYS_CONTINUE` act immediately; `ASK` (default) shows a 4-choice dialog (stop /
  continue this time / always stop / always continue - the last two persist the setting).

---

## 3. Video / RTSP fullscreen playback

### 3.1 Entry - `StreamPlaybackHelper.playStreamVideo(path, playWhenReady)` (`:39-113`)
1. `releasePlayer()`; reset the stall-watchdog budget for the new session.
2. `isRtsp = path.startsWith("rtsp://")`.
3. `dataSourceFactory = StreamDataSourceFactoryProvider.create(context)` (section 3.2).
4. **RTSP**: `streamProtocolSupport.createRtspMediaSource(uri, factory)` - **null** means this flavor lacks
   the RTSP module -> show "This stream type is not supported in this app version." and return (no player).
5. Build a shared `DefaultBandwidthMeter` + a `BandwidthAdaptiveLoadControl` (section 4) on the same
   `ExoPlayer.Builder` (so measured throughput feeds the load control).
6. **Non-RTSP**: `DefaultMediaSourceFactory(context).setDataSourceFactory(factory)` (auto-detects
   progressive / HLS / DASH from whichever modules are present, section 9) + a `MediaItem` with a
   `LiveConfiguration` (target 10 s behind live, window 4-20 s, catch-up speed capped at 1.02x; honored only
   for live content, safe to attach unconditionally).
7. Attach the per-stream `Player.Listener` (tracked so `release()` can remove it, S0893); `prepare()`.

### 3.2 The HTTP data source - `StreamDataSourceFactoryProvider`
`DefaultHttpDataSource.Factory` with: UA `FastMediaSorter/<version> (Android)`, **cross-protocol redirects
allowed** (mandatory - Icecast/Shoutcast 30x across http<->https), **connect/read timeout 15,000 ms**,
default request header **`Icy-MetaData: 1`** (opt-in for ICY metadata). Wrapped in
`UserInfoBasicAuthDataSource` (S1015): lifts a `http://user:pass@host` userinfo into an
`Authorization: Basic base64(user:pass)` header and strips it from the requested URI (IP cameras / DVRs
commonly share credentials this way). Wrapped again in `DefaultDataSource.Factory` for `content://`/`file://`
fallback.

### 3.3 Per-stream `Player.Listener`
A dedicated listener (not the local-file video listener - no poster/watch-clock/decoder-failure tracking).
Closure state: `behindLiveRecoveries`, `transientRetries`, `reconnecting`. On `STATE_BUFFERING` it arms the
watchdog + shows the wait-phase label; on `STATE_READY` it resets both recovery budgets, starts the stall
watchdog, clears the label, and fires `onPlaybackReady`; `onPlayerError` runs the recovery classifier
(section 5).

---

## 4. Bandwidth-adaptive buffer (S0688) - `BandwidthAdaptiveLoadControl`

Wraps a `DefaultLoadControl` and overrides `shouldContinueLoading` to cap the cushion at a dynamic target
derived from measured throughput. Constants:
```
STREAM_ADAPTIVE_FLOOR_BUFFER_MS = 30_000   // floor AND the live ceiling
STREAM_ADAPTIVE_VOD_CEILING_MS  = 60_000   // weak-signal ceiling, non-live only
BUFFER_FOR_PLAYBACK_MS               = 2_500   // start threshold
BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 8_000  // post-rebuffer fill
STREAM_WEAK_BITRATE_BPS    = 1_500_000  // ~1.5 Mbps
STREAM_HEALTHY_BITRATE_BPS = 5_000_000  // ~5 Mbps
```
Sizing rule (linear interpolation): weak link (`<= 1.5 Mbps`) -> the ceiling; healthy (`>= 5 Mbps`) -> the
30 s floor; between -> interpolated. **Live-edge guard**: when the stream is live, the ceiling **is** the
30 s floor, so a live stream **never** deepens past 30 s regardless of bandwidth (a deeper backlog would pin
the playhead next to the expiring segment and revive `BehindLiveWindow` drops). Only non-live (VOD /
progressive / RTSP) deepen toward 60 s on a weak link. `isLive` defaults true (conservative during initial
fill) and is fixed per stream session. Radio (inline audio) never reaches this control (separate player).

---

## 5. Live-stream auto-recovery (S0634) + wait-phase label (S0685)

All in the per-stream `onPlayerError`. Two silently-recovered cases (delayed via a Main-dispatcher
coroutine, each guarded by an S0895 stale-`exoPlayer`-reference check so a mid-delay navigation doesn't
corrupt another player):

### 5.1 BehindLiveWindow (live-edge desync)
`errorCode == ERROR_CODE_BEHIND_LIVE_WINDOW && !isRtsp`, budget **3**, **linear** backoff
`attempt*1000ms` capped at 5 s (1 s, 2 s, 3 s). Recovery = `seekToDefaultPosition()` (re-anchor to the live
edge) + `prepare()`. Not a dead channel - the sliding window moved past the playhead.

### 5.2 Classified transient / retryable-HTTP error
`isRecoverableStreamError(error) && transientRetries < 4`, budget **4**, **exponential** backoff
`2000 << (attempt-1)` (2 s, 4 s, 8 s, 16 s). On a live stream it re-anchors to the live edge before
re-preparing.

### 5.3 Recoverable classification (what heals vs hard-fails)
Recoverable: `ERROR_CODE_TIMEOUT`, `IO_UNSPECIFIED`, `IO_NETWORK_CONNECTION_FAILED`,
`IO_NETWORK_CONNECTION_TIMEOUT`, and `IO_BAD_HTTP_STATUS` **only** when the HTTP code is **429 or 5xx**.
Everything else - explicit **4xx** (not 429), malformed manifest, unsupported container, any other error
code - is a **hard, unrecoverable** failure.

### 5.4 Hard-fail -> dead-channel dialog (S0581)
On exhaustion / unrecoverable error, `onPlaybackError` surfaces a dialog: title "Stream unavailable",
message "<title> is not responding. Remove it from your list?", buttons **Retry** (re-play) / **Remove**
(delete the source + finish) / **Cancel** (finish). The inline-audio path has its own copy (Cancel just
dismisses, keeping the list open).

### 5.5 Wait-phase label (S0685)
A two-value label under the spinner: "Buffering.." for a plain fill; "Reconnecting.." whenever the buffering
coincides with an active S0634 recovery (`reconnecting`) or an active S0936 watchdog recovery
(`streamWatchdogReconnecting`). Cleared on `STATE_READY` and on hard-fail.

---

## 6. Stall watchdog (S0936/S0937) - silent-freeze detection

`onPlayerError` only fires on a thrown exception; a stream that **freezes without throwing** needs a
separate detector. Constants:
```
STALL_POLL_INTERVAL_MS   = 3_000   STALL_MIN_PROGRESS_MS = 500   STALL_MAX_POLLS = 3
BUFFERING_STALL_TIMEOUT_MS = 15_000   STREAM_MAX_WATCHDOG_RECOVERIES = 3
```
Two triggers sharing one runnable slot and one budget:
- **Position-freeze poll** (armed on `STATE_READY`): if position advances `< 500 ms` for 3 consecutive 3 s
  polls (~9 s frozen while nominally playing) -> recover.
- **Buffering-without-ready timeout** (armed on `STATE_BUFFERING`): if still buffering after 15 s **and** not
  legitimately still downloading -> recover (re-arms if the buffer is genuinely growing).

Recovery (synchronous, budget 3): `stop(); prepare()` (a bare `prepare()` is a no-op from BUFFERING/READY);
live -> re-anchor to the live edge, non-live -> `seekTo(resumePosition)`. Exhaustion synthesizes a
`PlaybackException(ERROR_CODE_TIMEOUT)` routed through the same hard-fail dialog. This budget is independent
of the S0634 counters.

---

## 7. Stream-tailored control profile (S0631 / S0640 / S0641 / S0642)

Single source of truth: `PlayerState.isLiveVideoStream = resource.id == STREAM && currentFile.type == VIDEO`
(so a defensively-fullscreened AUDIO stream keeps the ordinary audio controls).

### 7.1 Command panel (S0631)
For a live video stream, the command list short-circuits to exactly: **Send-to**, **Fullscreen**, **Edit**
(-> the video-control dialog, not a file editor), **Save-frame**, **Info**, **Rotation-toggle** (if the
device has rotation), **Cast** (if the build supports Cast AND on Wi-Fi). **PiP** stays available (its own
manager has no stream gating). Everything else (delete, favorite, rename, slideshow, sleep timer, crop,
compress, copy/move destination panels, open-in-separate-window, all per-type commands) is unreachable.

### 7.2 On-player overlay trim (S0641)
Hidden on the ExoPlayer overlay for a live stream: the **seek bar row** (position/progress/duration), the
**playback-order/repeat** toggle, the overlay-embedded **prev/next-file** buttons, and **rewind/forward**.
Kept: play/pause, the Control-dialog button, PiP. Re-applied on every controller-visibility change (the
PlayerView re-runs its own visibility pass).

**Two distinct prev/next affordances** *(a reuse must preserve this)*: the **overlay** prev/next is hidden
for live; the separate **top-command-panel** prev/next stays visible and pages video channels in catalog
order (S0640, section 1.3).

### 7.3 Control dialog trim (S0631/S0642)
The same video Control dialog, narrowed for a stream: **Hue/Brightness hidden** (color adjustment is
meaningless on a live decode); **Speed hidden** for a **live** stream (live HLS/DASH/RTSP can't be sped) but
kept for a non-live progressive HTTP stream; Volume, and Audio/Subtitle track sections (if multi-track), stay.

### 7.4 Send-to shares the URL as text (S0631)
For a live stream, Send-to builds a text-only `ShareableContent(text = url, mime = "text/plain",
sourcePath = null)` - it never downloads the live stream to a file; only text-capable receivers (SMS, email
body, clipboard) are offered.

### 7.5 Gesture trim (S0694)
A live stream uses a dedicated single-tap-toggles-chrome detector; the normal 9-zone/3-zone gesture grid
(double-tap-seek, swipe brightness/volume, managed-file zones) is bypassed.

---

## 8. Casting a live stream - `CastStreamResolver`

Pure, unit-tested (`CastStreamResolverTest`, 9 cases). Reuses the http/https/rtsp scheme allowlist.
```
resolve(path):
  not a supported scheme        -> NotAStream       (fall through to local/network file cast: download + proxy)
  rtsp://...                     -> UnsupportedProtocol  ("This stream cannot be cast to Chromecast")
  else                           -> Direct(contentType)  (hand the URL to the receiver)
contentType: m3u8 -> application/x-mpegurl; mpd -> application/dash+xml; mp4/mov -> video/mp4;
             webm -> video/webm; ts -> video/mp2t; else -> application/x-mpegurl (HLS default)
```
A `Direct` decision builds a `MediaInfo` with **`STREAM_TYPE_LIVE`** and the content type, and calls
`remoteClient.load(...)` directly - **no local download, no proxy** (a live stream is unbounded). This is the
opposite of a normal file cast (download to `cacheDir`, capped at 50 MB for video, served via
`LocalCastProxyServer`). **RTSP is never castable** on any flavor.

---

## 9. Protocol support - `StreamProtocolSupport`

```kotlin
interface StreamProtocolSupport {
    val supportsSegmentedStreaming: Boolean   // HLS/DASH modules present
    val supportsRtsp: Boolean
    fun createRtspMediaSource(uri, dataSourceFactory): MediaSource?
}
```
Flavor-selected by source set (never both compiled):
- `FullStreamProtocolSupport` (`streamingEnabled`): both flags true; RTSP via
  `RtspMediaSource.Factory().setForceUseRtpTcp(true)` (**RTP-over-RTSP/TCP**; UDP deferred).
- `ProgressiveOnlyStreamProtocolSupport` (`streamingDisabled`, lite/photos): both false;
  `createRtspMediaSource` returns null.

**Gap** *(code-verified fact)*: `supportsSegmentedStreaming`/`supportsRtsp` are set per flavor but **never
read** anywhere in `src/main`. RTSP is guarded only by `createRtspMediaSource` returning null (-> friendly
message). HLS/DASH has **no** explicit guard - on lite/photos an `.m3u8`/`.mpd` URL is still handed to
`DefaultMediaSourceFactory` with the HLS/DASH artifacts absent, so Media3's internal module-detection
fallback governs the outcome. (A reuse should decide this explicitly.)

### 9.1 Protocol -> flavor matrix

| Protocol | standard | noLegal | legacy | vr | lite | photos |
|---|---|---|---|---|---|---|
| Progressive HTTP(S) audio (radio) | Yes | Yes | Yes | Yes | Yes* | entry hidden |
| Progressive HTTP(S) video | Yes | Yes | Yes | Yes | Yes* | entry hidden |
| HLS (.m3u8) | Yes | Yes | Yes | Yes | module absent | module absent |
| DASH (.mpd) | Yes | Yes | Yes | Yes | module absent | module absent |
| RTSP | Yes (RTP/TCP) | Yes | Yes | Yes | not-supported message | not-supported message |
| Cast a live stream (HLS/DASH/progressive) | Yes | Yes | Yes | No (`SUPPORT_CAST=false`) | Yes | Yes |
| Cast RTSP | never on any flavor | | | | | |

\* The Streams catalog **screen** is hidden on lite/photos (`SUPPORT_STREAMS=false`), but the player-level
protocol classification still compiles and applies if an http(s)/rtsp URL reaches the player another way
(external `ACTION_VIEW`, a previously-pinned shortcut).

---

## 10. Record-play-outcome (S0593) - the green/red/amber bullet

`RecordStreamPlayOutcomeUseCase`: `invoke(id, ok)` writes `"OK"`/`"FAIL"` for a **real play** (and, on OK,
records a `StreamPlayed` stat split by stored `mediaKind`); `recordProbe(id, reachable)` writes
`"OK"`/`"UNKNOWN"` (never FAIL) for a reachability probe / grid-frame capture. Persisted on
`lastPlayOutcome`/`lastPlayOutcomeAt`.

**Both surfaces feed the same field** (this is the reconciliation of the two views in `05` §9.2):
- **Inline audio (AUDIO)**: `StreamInlineAudioManager`'s listener records OK on first `isPlaying` (once per
  play) and FAIL when the "Stream unavailable" dialog is shown.
- **Fullscreen player (VIDEO/RTSP)**: `PlayerPlaybackCallbackImpl.onPlaybackReady` records OK on every
  `STATE_READY` (incl. after a silent recovery) for a stream URL; `onPlaybackError` ->
  `PlayerViewModel.onStreamPlaybackFailed` records FAIL - both guarded by `isStreamUrl` and resolve-by-URL
  (a no-op for an http URL not in the catalog).

So an AUDIO row goes red on inline failure, and a **VIDEO/RTSP row goes red after a failed fullscreen play**;
a probe/capture can only promote to green or amber. Red = a real failed play from either surface.

---

## 11. Flavor / BuildConfig matrix (playback-relevant)

| Flavor | minSdk | SUPPORT_STREAMS | ENABLE_PERSISTENT_AUDIO_PLAYBACK | SUPPORT_CAST | media3 hls/dash/rtsp |
|---|---|---|---|---|---|
| standard | 26 | true | true | true | yes |
| noLegal | 26 | true | true | true | yes |
| legacy | 23 | true | true | true | yes |
| vr | 26 | true | true | false | yes |
| lite | 26 | false | false | true | no |
| photos | 26 | false | false | true | no |

Media3/ExoPlayer pinned at 1.2.1. `vr` compiles the Cast impl but reports `SUPPORT_CAST=false` (Horizon OS
has no GMS Cast), so Cast is gated off at runtime.

---

## 12. Ticket index for this file

S0565 (feature + protocol support), S0575 (lite gate), S0577 (background audio + exit), S0581 (unavailable
dialog), S0590 (channel-name title), S0592 (kind fallback), S0593 (outcome bullet), S0631/S0640/S0641/S0642
(control profile), S0632 (cast; behavior under S0403 seam), S0634 (live recovery), S0685 (wait label + live
buffer), S0688 (adaptive buffer), S0690 (re-tap stop), S0691 (title dedup), S0694 (fullscreen/gesture),
S0700 (probe), S0711 (offline gate), S0874/S0893/S0895/S0896 (lifecycle guards), S0936/S0937 (stall
watchdog), S1015 (basic-auth + shared factory), S0403 (cast seam), S0116 (subsystem B - unrelated). The
stale `docs/ARCHITECTURE.md:209` protocol-selection line is parked as **S1109**.
