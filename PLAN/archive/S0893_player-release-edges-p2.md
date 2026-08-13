# S0893 - Player release edges: codecs retained in background, asymmetric release (P2 cluster)

**Ticket:** S0893
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: release-контракт плеер-хостов - удержание кодеков в фоне и асимметричный teardown (CODE_AUDIT_PROTOCOL contract items 2/9, Rule 18).

- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:120 - Video decode can start/resume while the host activity is invisible - start paths are gated only on audio isPlaying, driven by a Player.Listener that is not lifecycle-unbound
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:154 - No onStop release edge - prepared video MediaPlayer (hardware codec) is retained from onPause until onDestroy while the activity is backgrounded
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:242 - SurfaceTextureListener asymmetry: hide() leaves the listener installed, and onSurfaceTextureDestroyed returns false while no code path ever calls SurfaceTexture.release()
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:310 - startMediaPlayer catch block orphans the just-constructed MediaPlayer when the apply{} initializer throws - only the Surface is released
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:20 - releasePlayer() does not stop the positionSaveLoop - idle 15 s Handler tick survives video-to-document switches
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:34 - Only playerListener is removed at release - PauseAwareLoadControl and the per-stream listener are never removed
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:35 - releasePlayer() releases the ExoPlayer while it is still attached to PlayerView - no setVideoSurface(null)/player detach before release (asymmetric with onDestroy)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:52 - Player never released on the background edge - onPause only pauses, no onStop release/onStart recreate, so codecs + buffered media are retained while the app is backgrounded
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt:445 - Video ExoPlayer released only in onDestroy - prepared player and codecs held for unbounded time while the activity is stopped in background (contract item 2)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt:1013 - Player released on onDestroy instead of the API24+ onStop edge, with no documenting comment for the deviation - codecs held while the host sits stopped in background

## Related

- S0878 (audit tail container - triage source).
- Player family glue: shared engine propagates, per-host mirrored manually - apply the release contract to EVERY host touched (CLAUDE.md 13 "Player/Glide ownership").

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878, S0894 (sibling standalone-player cluster from the same triage, Verified)

**Tactical plan:** `PLAN/S0893_player-release-edges-p2/INDEX.md`

## Last Audit

### Manual (device test) - 2026-07-09, emulator-5554 (Android 13, x86_64, 1080x2400), STANDARD debug

Probe tags present in installed build and fired live. Evidence: `temp/S0893/EVIDENCE.md`, screenshots + logs under `temp/S0893/`.

- Sub-check 1 (PlayerActivity video, background/foreground) - PASS. Backgrounded video_large.mp4 at 01:04 via HOME: `S0893: VideoPlayerManager onStop - releasing player while backgrounded (API24+)`, ExoPlayer `Release`, `abandonAudioFocus()`. On return: ExoPlayer re-`Init` (new instance), `Restored position 63699ms` (== 01:04), `onRenderedFirstFrame`, real frame rendered. expected: resume at same position, no black/crash/ANR | actual: resumed at 63699ms, frame rendered, no FATAL/ANR.

- Sub-check 2 (PhotoVideoStandaloneActivity via external Open-with VIEW intent) - PASS. Reached via `.StandaloneVideoPlayer` alias (enabled per-launch with `pm enable`; app re-disables it on lifecycle since isPrimaryMediaPlayer=false). Background: `StandaloneViewManager: onStopVideo - releasing backgrounded video player` + `abandonAudioFocus` + ExoPlayer `Release`. Foreground (same instance): `S0893: PhotoVideoStandalone onStart - rebuilding video released on background`. expected: resume cleanly, no black/crash/ANR | actual: release+rebuild contract fired, no FATAL/ANR; resumed player was paused at 0:00 (pre-first-frame black while paused, not a dead surface - fresh launch rendered video correctly). Note: emulator killed the standalone process on longer backgrounds (native-heap-low); the in-place onStop/onStart cycle was captured on a tight cycle.

- Sub-check 3 (audio-empty-state muted background video) - INCONCLUSIVE. `audioEmptyStateMode` is `CANVAS_WAVES`; switching to `VISUALIZATION` (video) requires the in-app `rowAudioEmptyStateMode` picker (its selection triggers an `AUDIO_VISUALIZATIONS` deliverable download), the row sits in a collapsed settings section, and the mcp harness cannot type Cyrillic to use settings search - so the muted-background-video path could not be triggered on-device. Partial evidence: `AudioEmptyStateController` self-registers as a lifecycle observer and its `S0893: AudioEmptyStateController onStop()` release edge fired during sub-check 1.

- Overall: no FATAL EXCEPTION, no crash-buffer entries for `sza.fastmediasorter`, no new ANR across the session. Status left BlockNeedUserTest (owner to exercise sub-check 3: set audio empty-state style to Visualization, play a cover-art-less audio, background/foreground).
