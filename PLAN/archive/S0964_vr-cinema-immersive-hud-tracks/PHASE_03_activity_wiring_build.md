# S0964 PHASE 03 - Activity wiring, probes, build gate

**Goal:** `FILE_URI` launches render the interactive panel into the HUD quad; diagnostic playlist keeps the banner; single `nd` build validates code + probes.

## Steps

- [x] 03.1 `DiagnosticXrActivity.kt` - panel pipeline (FILE_URI only; `launchInput.launchMode == VrLaunchMode.FILE_URI`):
  - `renderPanelHud()`: draw `hudRenderer.render(hudCanvas)` into `hudBitmap` (dims follow renderer consts), copy via a DEDICATED `getReusablePanelHudBuffer()` (impl deviation from plan: `getReusableDirectBuffer` is shared with Dispatchers.IO image decodes and its content writes happen outside the @Synchronized getter - sharing would race), `runtime.queueHud(bytes, WIDTH, HEIGHT)`.
  - Instantiate `HudTrackController { exoPlayer }`; wire new dispatcher callbacks: cycle -> controller -> refresh labels -> `renderPanelHud()`.
  - Re-render triggers ONLY: session ready, play/pause toggle, volume change (from HUD), tracks changed, track cycle. No per-frame queueHud.
  - `Player.Listener.onTracksChanged` (added to the existing listener in `startVideoPlayback`): refresh `audioTrackLabel`/`subtitleTrackLabel`/enabled flags from `HudTrackController`, then `renderPanelHud()`.
  - Init HUD model from real state: `hudRenderer.volume = snapshot?.videoVolume ?: 1f`, `isPlaying` from `playWhenReady`; filename already set.
  - In `onRenderThreadSessionReady`: FILE_URI -> `runtime.setHudQuadSize(PANEL_QUAD_WIDTH_M, PANEL_QUAD_HEIGHT_M)` + `renderPanelHud()` instead of `queueFilenameHud`; DIAGNOSTIC_PLAYLIST -> banner path unchanged (no quad-size call, defaults apply).
  - Companion consts: `PANEL_QUAD_WIDTH_M = 0.48f`, `PANEL_QUAD_HEIGHT_M = 0.30f` (2:1 texture 1024x640 -> 1.6:1 quad keeps text legible at -1.5 m; flagged for owner on-device review).
  - Error path (`queueErrorHud`) unchanged - error banner overwrites panel texture; letterboxed on panel quad, acceptable.
- [x] 03.2 Localized captions: load `vr_hud_audio_label`/`vr_hud_subs_label`/`vr_hud_subs_off`/`vr_hud_no_tracks` in `proceedWithInitialization` and hand to renderer/controller.
- [x] 03.3 Probes (final code edits, spec -> BlockNeedUserTest): `Timber.d("S0964: ..")` at (a) panel queue entry in `renderPanelHud`, (b) track-cycle handlers. Kept <= 120 chars.
- [x] 03.4 Build gate: `.\a.ps1 nd` - BUILD SUCCESSFUL (2m 42s, then 1m 56s after detekt fixes). Standard flavors untouched (src/vr + vr-res only).
- [x] 03.5 Closure: `post-change.ps1 -ScopeToFile` (dev-log, catalog-sync, gates) + scoped `assert-detekt` PASS after fixes (companion spacing, ctor wrapping, ReturnCount/ComplexCondition in renderPanelHud, renderer layout consts, dispatcher click extraction); status `BlockNeedUserTest` + note via `update.ps1`. ALL_FEATURES ADD record deferred to Verified flip (S0962 precedent), noLegal-only -> `-NoLegal`.

## Verification

- Grep: `queueFilenameHud` still the only HUD visual on DIAGNOSTIC_PLAYLIST path.
- Grep: no `queueHud` call inside `onNativeRayInteraction`/render-tick paths.
- Build: `BUILD SUCCESSFUL` in nd log.

## On-device (Quest 3, gates Verified)

1. Browse -> video with 2+ audio tracks -> "Open in VR Cinema": panel visible (not banner, not grey), tracks rows populated.
2. Cycle AUDIO: audible track switch; label updates.
3. Cycle SUBS: OFF <-> tracks; player TEXT type toggles (probe log), cue text NOT expected on screen (follow-up ticket).
4. Video with single audio / no subs: rows dimmed, no crash.
5. Diagnostic VR test button: old banner behaviour intact.
