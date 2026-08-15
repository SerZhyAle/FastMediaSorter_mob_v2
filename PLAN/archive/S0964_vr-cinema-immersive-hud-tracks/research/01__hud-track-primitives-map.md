# S0964 research 01 - HUD pipeline and track-selection primitives (as-is map)

Date: 2026-07-11. Sources: live working tree (`app_v2/src/vr`, `app_v2/src/main`), S0961 static analysis.

## Track-selection primitives (ADR-3 target, reuse as-is)

- `src/main/.../ui/player/VideoTrackSelectionManager.kt` (212 LOC) - plain class, constructor takes `(getPlayer: () -> ExoPlayer?, getPlayerView: () -> PlayerView?)` lambdas; NOT Hilt-bound. Directly constructible from VR code with `{ exoPlayer }` and `{ null }`.
  - `getAvailableAudioTracks(): List<TrackInfo>` - label composed as `Track N (LANG, codec, channels)` + `⚠ Unsupported` marker; `isSelected` flag included.
  - `getAvailableSubtitleTracks(): List<TrackInfo>` - label `DisplayLanguage (Track N)`.
  - `selectAudioTrack(groupIndex, trackIndex)` - `TrackSelectionOverride` via `trackSelectionParameters`.
  - `selectSubtitleTrack(groupIndex, trackIndex)` - `trackIndex < 0` disables TEXT type (subtitles OFF).
  - `applySubtitleStyle` requires PlayerView - NOT usable in VR (no PlayerView); do not call.
- `TrackInfo(groupIndex, trackIndex, label, isSelected)` - sufficient as HUD row model.
- `ui/player/helpers/VideoPlayerTracksObserver.kt` - NOT reusable: hard-wired to `VideoPlayerManager` internals (stereo detection, M2TS toast). VR path needs its own `Player.Listener.onTracksChanged`.

## Immersive HUD pipeline (as-is)

- Visual owner today: 1024x128 filename banner (`queueFilenameHud` -> `runtime.queueHud`) - S0290 decision. Full panel `HudCanvasRenderer` (1024x512: PREV/PLAY/NEXT buttons, VOLUME + STEREO DEPTH sliders, FILE header, FPS) exists ONLY as interaction model; it is never rendered to the quad. Ray clicks are dispatched against invisible 1024x512 rects while the quad displays the 1024x128 banner - blind-click mismatch.
- S0290 "invisible panel" root cause was the column-major MVP bug in `xr_hud_world.cpp::multiply_matrices` - FIXED since (see comment at line ~74). The byte pipeline (direct ByteBuffer -> `queueHud` -> `glTexImage2D`) is proven by the working banner. Nothing blocks rendering the full panel today.
- `xr_session_queue_hud` accepts arbitrary WxH (`glTexImage2D` with actual dims) - texture size is not fixed to 1024x128.
- Quad geometry is fixed in `xr_hud_world.cpp::xr_hud_init`: 0.3m x 0.113m at head-forward -1.5m, head-locked. No runtime size API - needs a native setter for panel mode (banner letterboxing 8:1-into-2.65:1 was an S0291 owner decision; do not auto-derive quad from texture aspect).
- S0961 (fixed 2026-07-11): quad hidden until first real `hud upload` (`hudContentUploaded` gate, render call passes `hudTex=0`). Panel path composes cleanly: first `queueHud` upload reveals the quad.
- Per-frame `queueHud` is banned (S0290 history: overwrote banner, wasted uploads). Re-render + re-queue only on state changes. Native already draws low-latency hover cursor (dot + ray line) in `xr_hud_render` - Canvas-side hover cursor re-render is NOT needed.
- Reusable buffers in `DiagnosticXrActivity`: `getReusableHudBuffer()` is hard-sized to banner 1024x128x4; panel must use the generic `getReusableDirectBuffer(size)`.

## Launch-mode gate (panel vs banner)

- Real user launches use `VrLaunchMode.FILE_URI`: `BrowseVrCinemaLaunchManager` (S0962), `PlayerVrLaunchManager` (warm entry from player).
- Diagnostic path uses `VrLaunchMode.DIAGNOSTIC_PLAYLIST` (settings VR test button, `VrSettingsBlockFragment`). Epic non-goal: do not change diagnostic behaviour -> banner stays for DIAGNOSTIC_PLAYLIST; the full panel ships on FILE_URI only.
- Playback state to mirror at init: `launchInput.snapshot` carries `videoVolume`, `videoIsPlaying`, speed, position (see `startVideoPlayback`). HUD model must initialise from the actual player state, not defaults (current `HudCanvasRenderer.volume = 1.0f` default diverges from snapshot volume).

## Composition parity with the 2D video dialog

- Strategic §2/epic §6.1: HUD mirrors the modern video dialog composition: volume (HUD has), audio tracks (new), subtitle tracks (new), stereo (HUD has STEREO DEPTH slider - parallax). Navigation (prev/play/next) already present.
- Track row UX: cycle control (`◀ label ▶`) per track type instead of list - panel space is limited; list UI belongs to Pillar 2 browser style, out of scope.

## Constraints and traps

- Localization: static HUD labels (AUDIO/SUBS/OFF etc.) are canvas-drawn - must come from string resources with EN/RU/UK parity. VR-only strings live in `src/vr/res/values*/strings.xml` - hand-edit (project string tools cover `src/main/res` only), verify parity by grep.
- Track labels themselves come from `VideoTrackSelectionManager` (EN-composed) - identical to 2D dialog behaviour, accepted parity.
- Subtitle CUE RENDERING in immersive does not exist (no PlayerView/SubtitleView on the video surface): selecting a text track has no visible output yet. Out of scope for S0964 (composition parity only) - park follow-up ticket.
- `HudCanvasRenderer.WIDTH/HEIGHT` consts drive the UV->pixel mapping in `HudInteractionDispatcher` - canvas resize changes hit zones globally (incl. diagnostic blind-click layout; acceptable, panel becomes visible on user paths and diagnostic never showed the panel anyway).
