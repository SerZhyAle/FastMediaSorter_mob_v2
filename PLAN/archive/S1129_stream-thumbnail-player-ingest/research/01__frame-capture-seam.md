# Research: player frame capture and stream-thumbnail ingest seam

**Ticket:** S1129
**Item:** strategic section 6
**Status:** Resolved

---

## Existing implementation

- `activity_player_unified.xml` and its landscape counterpart configure `PlayerView` with
  `app:surface_type="texture_view"`.
- `SaveVideoFrameManager` already captures the current `TextureView` content with `getBitmap()`.
- `StreamPlaybackHelper` installs its own listener and does not install
  `VideoPlayerManager.playerListener`, so the existing local-poster `onRenderedFirstFrame()` callback
  does not run for stream playback.
- `StreamFrameSnapshotManager` is the only current writer to `StreamFrameCache` and
  `StreamFramePersistentStore`; player code has no ingest seam.

## Platform findings

- Media3 `PlayerView.getVideoSurfaceView()` returns the actual `TextureView` when `surface_type` is
  `texture_view`: https://developer.android.com/reference/androidx/media3/ui/PlayerView#getVideoSurfaceView()
- `TextureView.getBitmap(width, height)` copies and scales the current surface content, and requires an
  available surface: https://developer.android.com/reference/android/view/TextureView#getBitmap(int,int)
- `Player.Listener.onRenderedFirstFrame()` can fire again after a surface/renderer/stream reset, so it is
  a trigger rather than a once-per-open guarantee:
  https://developer.android.com/reference/androidx/media3/common/Player.Listener#onRenderedFirstFrame()
- PixelCopy is unnecessary for the current player because the render target is already a `TextureView`.
  A `SurfaceView` migration would require a different capture path and is outside this ticket.

## Decision

1. Extract one reusable `PlayerTextureFrameCapture` helper and reuse it from both the manual Save Frame
   command and stream player-ingest.
2. Trigger player-ingest from the stream listener's `onRenderedFirstFrame()`, wait 750 ms for a stable
   decoded frame, then capture one 640x360 bitmap. Guard the attempt per playback session because the
   callback may repeat after recovery.
3. Introduce a singleton `StreamFrameIngestor` domain contract. Its implementation rejects empty or
   nearly-black frames, updates `StreamFrameCache`, and persists through `StreamFramePersistentStore`.
4. Route both the existing headless snapshot path and the new fullscreen-player path through the same
   ingestor so cache/write/quality policy has one owner.
5. Return the ingested URL from `PlayerActivity` to `StreamsActivity`; repaint that URL on result so the
   still-bound tile updates immediately after Back, while the singleton cache supplies the bitmap.

## Verification implications

- Unit-test the black-frame gate and cache/persistent-store adoption contract.
- Compile-check constructor/DI changes and listener wiring.
- Device acceptance still requires opening a video/RTSP channel, waiting for a rendered frame, returning
  to grid mode, and confirming immediate plus cold-start persistence.

