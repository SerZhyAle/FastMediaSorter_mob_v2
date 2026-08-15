# S1222 - Videos played from the immersive VR browser always render flat mono

**Status:** Archived
**Priority:** 85

## 0. Raw capture

Found during the 2026-07-27 Quest 3 VR device-test session.

Owner observation, verbatim: "видео запущенные из VR-броузера все НЕ стерео".

## 1. Symptom

Any video opened by selecting a cell in the immersive VR browser plays flat and mono, whatever its stereo layout. The same file opened through the diagnostic/player entry renders correctly in 3D. The browser grid even draws the right badge on the tile - "SBS", "OU", "360" - and then plays the file ignoring exactly that.

## 2. Root cause

The browse package classifies stereo for the **grid pill only**, never for the renderer.

`ui/xr/browse/ImmersiveBrowseContentLoader.kt:54` maps `StereoMode` to a short badge string, and the result goes only to `ImmersiveBrowseCell.stereoBadge`, drawn by `ImmersiveBrowseGridRenderer`.

`VrStereoConfigResolver` - the class that turns a filename into the `RenderConfig(projection, layout)` the native side needs - lives at `ui/xr/helpers/VrStereoConfigResolver.kt` and is referenced **only** by `DiagnosticXrActivity`. A grep for `setRenderConfig` across `app_v2/src` returns four call sites, all in `DiagnosticXrActivity`; `ImmersiveBrowsePlaybackController` has none, so the session keeps whatever render config it had - the default flat mono quad.

The detection result exists, at the right moment, in the right object, and is thrown away for playback.

## 3. What the native side actually does with the config

Verified in `app_v2/src/vr/cpp/xr_session.cpp` before planning, because it constrains the fix:

- `renderProjection` selects the **media geometry**: 0 binds the sphere VAO, 1 the hemisphere VAO, 2 the flat quad at z=-5.
- `stereoLayout` feeds `u_stereoLayout` in the image program and the video program only.
- The HUD quad and the subtitle quad force `u_stereoLayout = 0` in `xr_hud_world.cpp`, so the browse grid can never be split by a stereo config.
- `xr_session_set_render_config` also resets zoom to 1.0.

Consequence the original fix direction missed: leaving a 360 config applied after playback puts the user **inside a sphere** carrying the last frame, with the browse grid floating in front of it. The reset is part of the fix, not an optional polish step.

## 4. Design decision - resolve at playback, not per cell

The obvious reading of "badge and render must agree by construction" is to resolve one `RenderConfig` per cell in `ImmersiveBrowseContentLoader` and derive the badge from it. Rejected:

- `VrStereoConfigResolver.resolve()` emits three `Timber.d` lines per call, two of them the live device-test probes for **S0771** and **S1112** (both currently `BlockNeedUserTest`).
- The loader runs once per directory entry, so a 500-file folder would fire those probes 500 times and bury the exact log lines those two tickets are verified against.

Resolving at playback time keeps one probe line per played file, matching the diagnostic path. Agreement is preserved where it is observable: both badge and render config derive from the same `StereoDetector.detectFromFilename` verdict on the same string (`ImmersiveBrowseCell.label`, which is `MediaFile.name`). They can differ only when the detector returns `UNKNOWN` - the badge is then null while the resolver's legacy token scan may still pick a projection. That asymmetry renders *more* correctly than the badge advertises and cannot mislead the user.

## Goal

Видео и картинки, открытые из иммерсивного браузера, должны рендериться в том же стерео-режиме, что и при запуске из плеера. Конфигурация проекции применяется на старте воспроизведения и сбрасывается при возврате в сетку, чтобы браузер не оставался внутри сферы от предыдущего 360-ролика.

## Phase 1 - Apply the render config on playback

- [x] Inject `StereoDetector` into `ImmersiveBrowsePlaybackController` and build a `VrStereoConfigResolver` from it.
- [x] Change `playVideo` to take the media display name alongside the uri, resolve the config from that name, and call `runtime.setRenderConfig` before enabling the video surface.
- [x] Change `playImage` to take the display name, resolve the config, and call `runtime.setRenderConfig` before `queueFrame`.
- [x] Reset the config to `ProjectionType.FLAT` / `StereoLayout.MONO` in `stop()` so returning to the grid cannot leave a sphere or a stereo split applied.
- **Verification:** `grep -n "setRenderConfig" app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowsePlaybackController.kt` returns two call sites - the `stop()` reset and the shared `applyRenderConfig` helper, which both play entry points call. (Written as "three" before the helper was extracted; corrected against the code.)

## Phase 2 - Pass the name the badge used

- [x] `ImmersiveBrowseActivity.selectMedia` passes `cell.label` to both playback entry points, so the render config and the grid badge resolve from one identical string.
- **Verification:** `.\a.ps1 fkn` (noLegal Kotlin compile - the `vr` source set is not in the standard variant) exits 0.

## Phase 3 - Device verification

- [x] Set `BlockNeedUserTest` with a status note naming the two checks: an SBS video and a 360 image opened from the immersive browser, then back-out to the grid.
- [x] Probe tag `Timber.d("S1222: browse applies ..")` in `applyRenderConfig` - one line per played file, the single entry point both media paths pass through.
- **Verification:** manual device test, deferred - no device attached this session (`device-ready.ps1` exit 2, "no online device").

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1218, S1221, S1233
- **UI scope:** no new UI element; existing immersive browse playback starts honouring the stereo layout the tile already advertises.
- **Flavor scope:** `noLegal` only. The whole `ui/xr/browse` package lives in `app_v2/src/vr/java`, and `app_v2/build.gradle.kts:611` mounts that directory under `getByName("noLegal")` and nowhere else.

## 5. Scope note

The immersive browser shipped in S0963 (`vr-cinema-immersive-browser`, `Archived`); this is a gap in it, not a regression from S1116 (readability) or S1132 (ray inversion), neither of which touches playback configuration.

## 6. Also seen in the same run, not part of this ticket

`ImmersiveBrowsePlaybackController$playVideo` logged two `ERROR_CODE_DECODING_FAILED` events at 22:42:34 and 22:42:58, both `Decoder failed: c2.qti.avc.decoder` on 1920x1080 AVC, with causes `CodecException: Error 0xffffffea` and `rendering to non-initialized(obsolete) surface`. Both coincide with a media switch / activity teardown and the metrics show the decoder had already rendered frames (`ttff 44ms .. rendering 9ms`), so they look like teardown races rather than an inability to play the file. Worth its own investigation if the owner reports visible playback failures; not evidenced as user-visible in this session.
