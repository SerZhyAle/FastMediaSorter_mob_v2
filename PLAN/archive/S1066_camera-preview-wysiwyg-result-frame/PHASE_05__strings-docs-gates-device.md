# Phase 05 - strings-docs-gates-device

**Goal:** Finalize S1066 - confirm the string/settings-doc surface, record the delivered capability, run the static gates, then insert the device-verification debug tags and hand off for the measurable on-device file-vs-frame check.

**Depends on:** 02, 03, 04

## Context

- The feature work (Phases 01-04) is code-complete and compiles on `standard` + `noLegal`. What remains is the closure surface: strings/localization, settings-doc sync (Rule 22), feature inventory, the static gate batch, and the on-device acceptance gate (strategic §11: measurable file-vs-frame overlay per ratio, photo and video).
- S1066 touched a layout (`activity_camera_capture.xml`, the `ResultFrameOverlayView`), a colour (`@color/camera_result_frame_scrim`), the shared camera session/manager code, `CaptureSettingsStore`/`AppSettings`/`SettingsRepositoryImpl` (persisted aspect ratio), and `app_v2/build.gradle.kts` (media3-transformer). No new `<string>` resources were introduced.

## Steps

- [x] **5.1** Strings audit: confirm S1066 added no new user-visible `<string>` needing EN/RU/UK. The result frame is decorative (contour + dim, no label) and the aspect picker reuses existing labels, so nothing new to localize.
  - Verify: grep `res/values*/strings.xml` for any S1066-added key -> none; note the decorative frame has no `contentDescription` string.
  - Done 2026-07-16: `res/values*/strings.xml` grep for `result_frame|resultframe|wysiwyg|camera_result` -> no hits. `ResultFrameOverlayView.kt` has no `R.string` / `contentDescription` / `setText` (decorative, `clickable=false` + `focusable=false` in the layout). Only new resource is `@color/camera_result_frame_scrim` in `values/colors.xml` (hex belongs there; Rule 19 bans hex in `res/layout*` only). Rule 11 land-counterpart check: no `res/layout-land/activity_camera_capture.xml` exists and the manifest pins `android:screenOrientation="portrait"` (S0918/S0754/S0924), so portrait-only is correct by design, not an omission.
- [x] **5.2** Settings-doc sync (Rule 22): the persisted camera aspect ratio (Phase 03) lives in DataStore behind the in-capture camera settings dialog, not on the Settings screen, so it needs no `settings-manifest.json` / `SETTINGS_REFERENCE*` row. Confirm `assert-settings-doc-sync` stays green (no regen required).
  - Verify: reasoning note + gate result.
  - Done 2026-07-16: `scripts/quality/assert-settings-doc-sync.ps1` -> exit 0, `settings-doc-sync: OK - catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync` (202 annotation keys en/ru/uk, 0 orphans; 49 HOW_TO recipes resolve). Green without regen confirms the premise: the aspect ratio never reaches the Settings screen surface the manifest tracks.
- [ ] **5.3** Feature inventory: record S1066 as a CHANGE to the in-app camera capability - the preview now equals the saved file (WYSIWYG), a result frame marks the file bounds at 16:9-in-4:3, the selected aspect persists, and digital-zoom video is honestly cropped. Flavors: `standard,lite,photos,legacy`.
  - Verify: record present in `docs/ALL_FEATURES.jsonl`.
  - **Flavor correction (2026-07-16):** this step originally read `standard, noLegal, vr`, which is wrong on both ends. The `cameracapture` package carries no `BuildConfig.IS_*/ENABLE_*` gate and lives in `src/main`, so every flavor compiling `src/main` ships it - `lite`/`photos`/`legacy` cannot be omitted. Conversely `vr`/`noLegal` are not how this area is recorded: the three sibling records for the very same screen (`camera.camera-zoom-presets-zoom-slider-night-mode` S0753, `camera.fixed-controls-send-to-and-settings-dialog` S0754, `camera.photo-gps-geotag` S0766) all use `standard,lite,photos,legacy`. Matching them keeps area `Camera` internally consistent.
- [x] **5.4** Static gates: run the fast gate batch (neuroslop, detekt scoped-to-file, deprecated-pm, listener-symmetry, flavor-flag, ticket-log) over the touched files via `post-change.ps1 -ScopeToFile` / `fg`; all green (or advisory on the always-dirty tree).
  - Verify: gate output PASS/advisory.
  - Done 2026-07-16: `.\a.ps1 fg` -> exit 0, all 7 gates PASS - `assert-no-ticket-logs`, `assert-flavor-flags-not-growing`, `assert-neuroslop` (every dimension at or below baseline), `assert-public-mutable-flow`, `assert-deprecated-pm-flags`, `assert-listener-symmetry` (133/133 delta 0), `assert-orientation-implied-feature` (portrait lock covered by the not-required override, i.e. no S0918/S0934 device-reach regression from this screen). Detekt is gradle-backed and re-run after the status flip (see 5.5) so `assert-no-ticket-logs` sees the probe tags under the status that permits them.
- [x] **5.5** Device debug tags + handoff: insert `Timber.d("S1066: ...")` at the changed-flow entry points (shared ViewPort bind, result-frame render, video digital-zoom re-encode), build once (`standard`), then flip the ticket to `BlockNeedUserTest` with a note describing the measurable file-vs-frame overlay check per ratio for photo and video.
  - Verify: `.\a.ps1 fk` PASS with tags in place; journal status `BlockNeedUserTest`.
  - Done 2026-07-16: 3 probe tags inserted, one per changed flow - `CameraCaptureSessionManager.bindToLifecycle` L621 (shared ViewPort bind, placed after the no-camera guard so the early return stays silent), `ResultFrameOverlayView.setRatios` L62 (frame render entry; `onDraw` deliberately avoided - it fires per frame), `VideoDigitalZoomProcessor.crop` L54 (placed after the `NO_ZOOM` guard so it only fires on a real re-encode). All lines <=108 chars (S0826 budget 120). `ResultFrameOverlayView` needed a `timber.log.Timber` import (ktlint order: after `com.sza.*`). `.\a.ps1 fk` -> `BUILD SUCCESSFUL in 23s`, `Fast check passed`.

## Done criteria
- No new untranslated user-visible strings.
- Settings-doc gate green; no manifest row required for the in-capture aspect setting.
- `docs/ALL_FEATURES.jsonl` records the delivered WYSIWYG capability.
- Static gate batch green (or advisory on a dirty tree).
- `S1066:` debug tags present at the changed-flow entry points; `standard` compile PASS; ticket in `BlockNeedUserTest` awaiting the on-device measurement.
