# Phase 2 - Wire track selector + rendition inventory + stall-triggered cap

## Steps

1. `VideoPlayerManager.kt`: add two per-session fields next to the S1127 stream fields (~line 349):
   - `internal var activeStreamTrackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null`
   - `internal var activeStreamStepDownController: com.sza.fastmediasorter.ui.player.helpers.StreamQualityStepDownController? = null`
   - **Verification:** compiles.

2. `StreamPlaybackHelper.kt` `playStreamVideo`, http(s) branch only (`!isRtsp`):
   - Build `val trackSelector = DefaultTrackSelector(context)` and pass `.setTrackSelector(trackSelector)` to `ExoPlayer.Builder` only when `!isRtsp` (RTSP keeps the implicit default - no ladder).
   - After `exoPlayer = player`: `activeStreamTrackSelector = trackSelector` (or null for RTSP); `val stepDown = StreamQualityStepDownController(); activeStreamStepDownController = stepDown`.
   - **Verification:** `./a.ps1 fk` compiles.

3. `StreamPlaybackHelper.kt` stream `Player.Listener`:
   - Override `onTracksChanged(tracks: Tracks)`: collect video `Format`s from `tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }`, map each selectable format to `Rendition(width, height, bitrate)` (guard `Format.NO_VALUE`), call `stepDown.setRenditions(list)`; log `Timber.i("Stream quality: renditions=%d single=%b path=%s", ..)`. Only inventory once non-empty (avoid clobbering with an empty mid-transition list).
   - In `onPlaybackStateChanged` STATE_READY branch, after the existing reset block, add: if a stall just closed (track a local `hadFirstFrame` set in `onRenderedFirstFrame`, and a `wasBuffering` flag), call `val cap = activeStreamStepDownController?.registerStall()`; if non-null apply `activeStreamTrackSelector?.let { it.setParameters(it.buildUponParameters().setMaxVideoSize(cap.maxWidthPx, cap.maxHeightPx).setMaxVideoBitrate(cap.maxBitrateBps)) }` and log `Timber.i("Stream quality: stepped down to <=%dx%d @%dbps path=%s", ..)`.
   - Guard: register a stall only when `hadFirstFrame && wasBuffering` (rebuffer, not initial fill), matching S1127 stall semantics. Set `wasBuffering=true` in STATE_BUFFERING, clear on READY after evaluation.
   - **Verification:** `./a.ps1 fk` compiles; listener-symmetry gate green (no new register/remove pair added - track selector is not a listener).

4. `StreamPlaybackHelper.kt` `releaseStreamDiagnostics` (teardown, already called from both paths): null the two new fields:
   - `activeStreamTrackSelector = null`; `activeStreamStepDownController = null`.
   - Co-locate here so both `VideoPlayerLifecycleHelper` teardown paths clear them without a second edit site.
   - **Verification:** grep confirms both fields nulled in exactly one teardown helper reached by releasePlayer + onDestroy.

5. Debug probe (BlockNeedUserTest gate): add `Timber.d("S1128: ..")` at the step-down apply point as the final code edit before the last build.

## Phase-boundary audit

- `DefaultTrackSelector` set only for `!isRtsp`.
- Step-down applies via ceiling cap (`setMaxVideoBitrate/Size`), never a hard single-track lock.
- No listener added without symmetric removal (track selector is not a `Player.Listener`; controller is a plain field).
- Teardown nulls both fields on both lifecycle edges.
- Device test: multi-rendition HLS -> forced stalls step quality down (log + visible rendition change); single-quality stream -> logged inert, no step-down.
