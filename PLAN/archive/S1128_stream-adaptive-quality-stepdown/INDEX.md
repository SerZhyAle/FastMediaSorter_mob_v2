# S1128 - Tactical plan: stream adaptive quality step-down

**Ticket:** S1128
**Strategic spec:** `PLAN/S1128_stream-adaptive-quality-stepdown.md`
**Status:** Tactical

## Goal (tactical)

On the http(s) stream branch, hold an explicit `DefaultTrackSelector`; on repeated post-first-frame stalls, force the video-quality ceiling down one rendition via `setMaxVideoSize`/`setMaxVideoBitrate`, driven by the S1127 stall signal. Detect single-quality playlists and skip step-down there.

## Phases

- **Phase 1** - `StreamQualityStepDownController` pure-Kotlin policy + unit tests. `phase-1-controller.md`
- **Phase 2** - Wire explicit `DefaultTrackSelector` + rendition inventory + stall-triggered cap into the stream player; teardown symmetry. `phase-2-wiring.md`

## Touched files (planned)

- NEW `ui/player/helpers/StreamQualityStepDownController.kt`
- NEW `test/.../ui/player/helpers/StreamQualityStepDownControllerTest.kt`
- EDIT `ui/player/helpers/StreamPlaybackHelper.kt`
- EDIT `ui/player/VideoPlayerManager.kt`
- EDIT `ui/player/VideoPlayerLifecycleHelper.kt` (only if teardown nulling not folded into `releaseStreamDiagnostics`)

## Non-goals

- RTSP branch, radio/audio, buffering/reconnect logic, manual quality picker, automatic quality raise-back.
