# S0933 - Tactical plan: crash-free stream grid capture engine

**Ticket:** S0933
**Status:** Tactical
**Strategic:** `PLAN/S0933_streams-grid-capture-engine-rework.md`
**Research:** `research/01__crash-free-frame-capture.md`

## Approach (from research 01)

Replace the offscreen-`ImageReader`-Surface capture inside `StreamFrameSnapshotManager.capture(url)` with a window-attached, invisible `TextureView` rendered by ExoPlayer; grab the first decoded frame via `TextureView.getBitmap()`. Only the frame-source changes - `StreamFrameCache` / `StreamFramePersistentStore` / grid repaint / S0712 / S0784 are untouched. Re-enable `CAPTURE_ENABLED`.

## Phases

### Phase 1 - Offscreen TextureView capture host

- Step 1.1: add `hostProvider: () -> ViewGroup?` constructor param to `StreamFrameSnapshotManager` (default `{ null }`). Null host -> `capture()` returns null (favicon stays), so non-Activity callers still compile.
- Step 1.2: `StreamsActivity` passes a provider returning a window-attached container. Add a `FrameLayout` (`id/streamCaptureHost`) to the activity layout root sized `wrap_content`, `alpha=0`, `translationX=-10000dp` (off-screen), `importantForAccessibility=noHideDescendants`, `isClickable=false`. It only hosts the transient capture `TextureView`.
  - Verification: grep - `StreamFrameSnapshotManager` has `hostProvider`; `activity_streams.xml` (and `-land`) contains `streamCaptureHost`; `StreamsActivity` wires the provider.

### Phase 2 - TextureView capture in `capture()`

- Step 2.1: rewrite `capture(url)` body: obtain host via `hostProvider()` (null -> return null); create a `TextureView` sized `CAPTURE_WIDTH_PX x CAPTURE_HEIGHT_PX`, add to host; build the muted `ExoPlayer` exactly as today (loadControl, dataSource, live config); `player.setVideoTextureView(tv)`; await first frame via `Player.Listener.onRenderedFirstFrame` (CompletableDeferred) bounded by `CAPTURE_TIMEOUT_MS`; on signal call `tv.getBitmap(CAPTURE_WIDTH_PX, CAPTURE_HEIGHT_PX)`; `cache.put` + persist as today.
- Step 2.2: teardown contract in `finally` on every path (success/timeout/cancel/error): `player.setVideoTextureView(null)`; `player.release()`; `tv.surfaceTextureListener = null`; host.removeView(tv). Keep `CancellationException` rethrow (S0900) and the drop-frame guards.
- Step 2.3: delete the `ImageReader`/`readerHandler`/`readFrame`/`PixelFormat` machinery and `POST_LAYOUT_SETTLE_DELAY_MS` (the timing-race hypothesis is dead per S0700 2026-07-04). Keep `MAX_CONCURRENT_CAPTURES`, `pending`, `CAPTURE_TIMEOUT_MS`, `CAPTURE_WIDTH/HEIGHT_PX`.
  - Verification: grep - no `ImageReader` / `PixelFormat` / `acquireLatestImage` remain in the file; `setVideoTextureView` present; teardown calls present.

### Phase 3 - Re-enable capture

- Step 3.1: `CAPTURE_ENABLED = true`. Update the companion comment to point at S0933 (TextureView path) instead of the disable note.
  - Verification: grep - `CAPTURE_ENABLED = true`.

### Phase 4 - Build + emulator smoke

- Step 4.1: `a.ps1 dq` (standard debug) PASS. Detekt-clean on the touched file (log lines <=120, no bare literals).
- Step 4.2: emulator smoke: open Streams -> GRID; expect NO `UnsupportedOperationException` / `ImageReader_JNI` / FATAL (the ImageReader path is gone); tiles either show a captured frame or stay favicon on timeout. The emulator proves the mechanism no longer throws the format exception; the Samsung native-kill fix is Phase 5 (device).
  - Verification: BUILD SUCCESSFUL; logcat has 0 `ImageReader_JNI` / 0 FATAL over a grid session.

### Phase 5 - Device verification (BlockNeedUserTest)

- Step 5.1: insert one `Timber.d("S0933: <entry>")` probe at the capture entry; set `BlockNeedUserTest`. Owner tests on the real device (Samsung Exynos / API36): open the video-streams grid with pinned channels -> NO native process kill; frames appear one-by-one for visible channels; last frame persists and shows on grid re-open (S0712/S0784 behavior restored).
  - Verification: device session with no crash; frames render; reopen shows last frame.

## Done criteria (mirror strategic §5)

1. Real device: grid entry does not native-crash; frames one-by-one.
2. Last frame persists low-res (S0712), shown on reopen.
3. No offscreen `ImageReader`-Surface; verified emulator + device.
4. `CAPTURE_ENABLED=true`; compiles; no FATAL/native-kill in a >60s grid session.
