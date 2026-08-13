# Phase 04 - Video fullscreen gate + autoplay (#2, #4)

**Goal:** standalone video honors `openVideoInFullscreen`; system bars stay visible outside real fullscreen; bars and command panel move together.

Root cause: `PhotoVideoStandaloneActivity.setupVideoControls()` unconditionally calls `StandaloneFullscreenManager.enterFullscreen()` (hides bars) while keeping `topCommandPanel` visible - a half-immersive state, ignoring the setting.

## Steps

1. Read `openVideoInFullscreen` from `settingsRepository` on the video-open path in `PhotoVideoStandaloneActivity`.
   - Verification: grep shows `openVideoInFullscreen` referenced in the standalone host.

2. Replace the unconditional `fsManager.enterFullscreen()` in `setupVideoControls()`:
   - When `openVideoInFullscreen == true`: call `fsManager.enterFullscreenWithPanel(binding.topCommandPanel) { ... }` (bars + panel hidden together = true fullscreen).
   - When `false`: do NOT hide bars; leave `topCommandPanel` visible (commands mode).
   - Keep the existing user toggle (tap) path using the panel-aware toggle so bars and panel never desync.
   - Verification: `.\a.ps1 fk` PASS; no remaining bare `enterFullscreen()` on the video-open path.

3. Confirm autoplay: `StandaloneViewManager.playVideo()` already sets `playWhenReady = true`. No code change expected; if device test shows no autoplay, investigate audio-focus denial. Leave a `S0920:` probe at the video-open entry for device verification.

4. Do NOT touch image/text/document surfaces - they already keep bars visible (edge-to-edge + insets). Do NOT change `StandaloneFullscreenManager`'s `hideSystemUiInFullscreen` handling (parked follow-up).

## Verification predicates

- `.\a.ps1 fk` PASS.
- Video-open path branches on `openVideoInFullscreen`; uses `enterFullscreenWithPanel`.
- On-device (device gate): setting ON -> video opens fullscreen (no bars, no panel) and plays; setting OFF -> commands mode with visible bars.
