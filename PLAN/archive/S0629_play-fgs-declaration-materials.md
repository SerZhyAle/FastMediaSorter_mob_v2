# S0629 - Google Play declaration materials for MEDIA_PROJECTION + SPECIAL_USE FGS

**Status:** Archived

> Created on owner request after the v2.60.6221.755 release was blocked by the Play
> Foreground-service-permissions gate. These materials unblock the future "Release B" that
> re-enables the screen-capture features in the standard Play build.

## 0. Context

The standard bundle ships two NEW foreground-service types that the current approved Play
declaration (MEDIA_PLAYBACK + MICROPHONE, edited 2026-06-05) does not cover, so the production
release commit returns HTTP 403 (`You must let us know whether your app uses any Foreground Service
permissions`):

- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - service `screencapture.ScreenCaptureService`
  (`foregroundServiceType="mediaProjection"`). Backs the menu/Operations screenshot (S0559).
- `FOREGROUND_SERVICE_SPECIAL_USE` - service `screencapture.OverlayHostService`
  (`foregroundServiceType="specialUse"`, subtype `persistent edge overlay strip host for screenshot
  gesture trigger`). Backs the left-edge screenshot gesture overlay (S0621).

Release sequencing decided by owner: ship a fast "Release A" with both features hidden/disabled and
NO new FGS in the manifest, then a "Release B" that re-enables them and submits this declaration.
This ticket produces the declaration package Release B needs.

## 2. Goals

Assemble a complete, submit-ready Play Console "Foreground service permissions" declaration package
for both new types, so Release B can fill App content -> Foreground service permissions in one pass.

### 2.1 MEDIA_PROJECTION (menu/Operations screenshot)

- Use-case category for the form: screen recording / screen capture.
- Description text: user-initiated screenshot of the device screen from the app's Operations
  settings; the Android system MediaProjection consent dialog is shown each time; the captured PNG
  is saved to the user-chosen folder. No background/automatic capture, no streaming off-device.
- Demo video (required by Google for this type): ~30-60s screen recording showing - open Operations
  settings -> tap the screenshot action -> system "Start recording or casting?" consent -> screenshot
  saved -> result visible in the chosen folder. Upload as unlisted YouTube; record the link.

### 2.2 SPECIAL_USE (left-edge gesture overlay)

- Subtype string (already in manifest, reuse verbatim): `persistent edge overlay strip host for
  screenshot gesture trigger`.
- Justification text: why a persistent foreground service is required - it hosts a small always-on
  edge strip the user can swipe to trigger a screenshot from anywhere, which must survive while other
  apps are foregrounded; and why no standard FGS type fits (it is a UI-overlay host, not media
  playback / data sync / location / etc.).
- Demo video: ~30-60s showing the edge strip, the gesture, the resulting screenshot.
- Honest risk note: SPECIAL_USE is the most-rejected FGS type and overlay+FGS is heavily scrutinised.
  Treat approval as uncertain.

### 2.3 Fallback package (in case SPECIAL_USE is rejected)

- A Play-compliant alternative trigger that needs no SPECIAL_USE: a Quick Settings tile and/or a
  persistent-notification action and/or a home-screen shortcut to invoke the screenshot from outside
  the app (the QS-tile infra already exists - see `AudioToggleTileService`).
- Decision to capture if rejected: gate `OverlayHostService` / the edge-overlay strip back to noLegal
  (sideload), keep the QS-tile/notification trigger for the standard Play build. Cross-ref S0621.

## 3. Deliverables / storage

- Two unlisted demo-video links (MEDIA_PROJECTION, SPECIAL_USE).
- The exact form text for each declaration (description + justification), kept in this ticket folder
  so Release B can paste them.
- A short checklist mapping each declared type -> service -> spec (S0559, S0621) -> video link.

## 4. Acceptance

- Materials complete and pasted/linked, ready to drop into the Foreground service permissions
  declaration when Release B is submitted.
- No code or manifest change in this ticket - it is a content/ops deliverable only.

## 5. Ready-to-paste declaration text (Release B)

Play Console -> Policy -> App content -> Foreground service permissions -> Manage. The form lists
each detected `FOREGROUND_SERVICE_*` type and asks for a use description + (for some) a video link.

### 5.1 FOREGROUND_SERVICE_MEDIA_PROJECTION

- App functionality (paste): "User-initiated screenshot. From the app's Operations settings the user
  taps a Screenshot action; Android shows the system screen-capture consent dialog; on approval the
  app captures a single still of the current screen and saves it as a PNG to the user-chosen folder.
  Capture is one-shot and always user-initiated - no background, automatic, or continuous capture, and
  nothing is streamed or sent off the device."
- Which option/category: screen recording / screen capture (single-frame capture is the closest fit).
- Video link: <unlisted YouTube URL - upload `temp/s0629_demo_media_projection.mp4` then paste here>.
  Recorded emulator demo (~51s, 1280x800, en-US): Management settings -> tap "Screenshot test" ->
  system "Share your screen" consent -> pick "Share entire screen" -> "Share screen" -> one-shot
  capture -> toast "Saved to Screenshots: screenshot_<timestamp>.png". Capture verified end-to-end
  (PNG written to Pictures/Screenshots via MediaProvider).

### 5.2 FOREGROUND_SERVICE_SPECIAL_USE

- Subtype (already in manifest, paste verbatim): `persistent edge overlay strip host for screenshot
  gesture trigger`.
- Justification (paste): "The service hosts a thin, always-available edge strip drawn over other apps
  (SYSTEM_ALERT_WINDOW). The user can swipe the strip at any time to trigger a one-shot screenshot of
  whatever is on screen, then the standard system MediaProjection consent runs. The service must stay
  foreground so the strip remains responsive while other apps are in the foreground. No standard
  foreground-service type applies: it is not media playback, data sync, location, microphone, camera,
  phone call, connected device, health, or remote messaging - it is a persistent user-triggered UI
  overlay host. It performs no background work beyond keeping the strip available; capture itself is
  one-shot, user-initiated, and runs under the separate mediaProjection service."
- Video link: <unlisted YouTube URL - upload `temp/s0629_demo_special_use.mp4` then paste here>.
  Recorded emulator demo (~49s, 1280x800, 2.5x speed, en-US): enable "Gesture overlay" (starts the
  `OverlayHostService` foreground service) -> in-app help text describing the edge-swipe gesture ->
  leave the app to the home screen -> notification shade showing the persistent FGS notification
  "Screen capture" plus the system "..is displaying over other apps" entry plus the "1 app is active"
  indicator (the strip and foreground service survive while another app is foregrounded - the exact
  reason the persistent FGS is required).
- Emulator limitation (state honestly to Google if asked): the live physical edge-swipe is NOT in
  this clip. Injected touch events (`adb input` / uiautomator) are not delivered to the
  TYPE_APPLICATION_OVERLAY strip window on the emulator, so the swipe-to-trigger must be filmed on a
  real device (this is why S0621 is `BlockNeedUserTest`). The capture the swipe performs is the
  identical user-consented MediaProjection pipeline demonstrated in the 6.1 video.
- Expectation: highest rejection risk of the two. If rejected, do NOT block Release B - drop this
  type and ship the fallback (section 2.3): keep the menu screenshot (MEDIA_PROJECTION) + add a Quick
  Settings tile / notification trigger, and gate the edge-overlay strip back to noLegal.

## 6. Demo-video shot-lists

Record on a device running a screen-capture-enabled build (noLegal debug via `a.ps1 nd`, or a
standard build with `-Pfms.screenCapture=on`). 20-45s each, no narration needed, show the full flow.

### 6.1 MEDIA_PROJECTION video
1. App open on the Operations settings screen.
2. Tap the Screenshot action.
3. The system "Start recording or casting?" consent dialog appears; tap Start now.
4. A screenshot is taken; show the confirmation / the saved PNG in the target folder.

### 6.2 SPECIAL_USE video
1. Enable the left-edge gesture in settings; grant draw-over-apps if prompted.
2. Leave the app; open any other app so the edge strip is visible over it.
3. Swipe the edge strip to trigger capture; the MediaProjection consent runs; screenshot is saved.
4. Show the strip persisting across app switches (why it must be a foreground service).

### 6.3 Recorded materials (2026-06-22 run)

- Build: standard debug with `-Pfms.screenCapture=on` (the Release B feature set), installed on a
  Pixel Tablet AVD (Android 17 / API 37, emulator-5556), app locale forced to en-US for legibility.
- `temp/s0629_demo_media_projection.mp4` - MEDIA_PROJECTION (6.1), ~51s, ready to upload.
- `temp/s0629_demo_special_use.mp4` - SPECIAL_USE (6.2), ~49s (2.5x), ready to upload.
- `temp/s0629_demo_special_use_raw.mp4` - 6.2 at real speed (122s), backup only.
- Capture method: raw `adb screenrecord` (mobile-mcp's own recorder is broken on this AVD); the app's
  one-shot MediaProjection bitmap capture coexists with `screenrecord` without conflict.
- Open gap for Google: a real-device clip of the physical edge-swipe triggering the capture (see 5.2
  limitation). Film it on the owner's phone/headset when S0621 is device-tested, if Google requests it.

## 7. Release B build switch (reminder)

Re-enable both features: remove `fms.screenCapture=off` from `gradle.properties` (or set `on`); no
code change - verified revert-clean by the S0630 on-build. Then submit with sections 5-6 filled.

## 8. Publish plan (owner, 2026-06-25)

Escalation ladder for shipping the standard MediaProjection capture suite (S0671) to Play:

1. Release `2.60.6242.232` is in review (2026-06-25); expected rollout ~2026-06-26.
2. After rollout, if no serious bugs surface, attempt to publish the new standard with the capture suite re-enabled (`fms.screenCapture=on`).
3. If Play rejects the FGS `mediaProjection` declaration with the existing demo videos, record new videos and resubmit.
4. If it still fails, roll back: drop the in-app menu trigger and invoke capture from a notification-shade icon (Quick Settings tile / notification action - section 2.3 fallback, `AudioToggleTileService` infra exists).

Scope note: this publish declares `FOREGROUND_SERVICE_MEDIA_PROJECTION` only (MEDIA_PLAYBACK + MICROPHONE already approved). SPECIAL_USE stays out (`fms.edgeGestureOverlay=off`, S0672 deferred). The shade-icon fallback changes the trigger, not the declaration - it does not avoid the mediaProjection FGS declaration itself.

## 9. Release readiness check (2026-06-25)

Confirmed with owner (2026-06-25): production `2.60.6242.232` (versionCode 260624223) is live on Play and was **FGS-free**; the **next** standard release is the one that re-enables the capture suite and declares `FOREGROUND_SERVICE_MEDIA_PROJECTION` for the first time (no Play policy review of it yet).

Verified the build switches + manifest gating produce exactly the intended declaration set:

- `gradle.properties`: `fms.screenCapture=on`, `fms.edgeGestureOverlay=off` (working tree).
- `fms.screenCapture=on` -> AGP injects `src/screenCapture/AndroidManifest.xml` -> declares `FOREGROUND_SERVICE_MEDIA_PROJECTION` + `ScreenCaptureService` (`foregroundServiceType="mediaProjection"`). IN.
- `fms.edgeGestureOverlay=off` -> `src/standardScreenCapture/AndroidManifest.xml` (the `FOREGROUND_SERVICE_SPECIAL_USE` + `OverlayHostService` source) is NOT injected. OUT.
- No `SPECIAL_USE` / `OverlayHostService` in `src/main` or `src/standard` - SPECIAL_USE can only enter via the gated `standardScreenCapture` set, so it stays out. Confirmed by grep.
- Net: the upcoming standard release declares `MEDIA_PROJECTION` only (plus the already-approved `MEDIA_PLAYBACK` + `MICROPHONE`). Matches §8 scope.

Confirmed on the ACTUAL merged release manifest (not just source): ran `:app_v2:processStandardReleaseMainManifest -Pfms.screenCapture=on -Pfms.edgeGestureOverlay=off` -> merged `standardRelease` manifest declares `FOREGROUND_SERVICE_MEDIA_PROJECTION` + `ScreenCaptureService` (`foregroundServiceType="mediaProjection"`), alongside the existing `MEDIA_PLAYBACK` + `MICROPHONE`; `SPECIAL_USE` / `OverlayHostService` count = 0. So the Play "Foreground service permissions" form will detect exactly those three types, with MEDIA_PROJECTION the only new one needing §5.1 + a demo video.

Release AAB build note: `a.ps1 r` builds from the release worktree (`../FastMediaSorter_release`, branch `main`), which currently carries the S0630 "Release A, FGS-free" config (`fms.screenCapture=off`). The capture-on flip lives only in the working tree. So a correctly-configured capture-on release AAB requires the capture-on release config (and version bump) landed on `main` first - i.e. driven through `/skill-release`, not a raw `a.ps1 r`. Building locally with `-Pfms.screenCapture=on` (as done above for the manifest) verifies the variant, but the signed Play bundle must come from the release worktree once `main` carries the release config.

Declaration text status:

- §5.1 `FOREGROUND_SERVICE_MEDIA_PROJECTION` app-functionality + category text - **submit-ready** (paste as-is).
- §5.2 `FOREGROUND_SERVICE_SPECIAL_USE` - **out of this release** (edge overlay gated off; S0672 deferred). Keep for a future submission.

Remaining gap before the Foreground-service form can be filled end-to-end:

- **MEDIA_PROJECTION demo video link is still a placeholder.** The recorded `temp/s0629_demo_media_projection.mp4` is gone (temp/ is gitignored and was cleared), and no unlisted-YouTube link was recorded. To complete the §5.1 form, the ~30-60s MEDIA_PROJECTION demo must be re-recorded (shot-list §6.1) and uploaded; then paste the link into §5.1. Not done in this pass (owner deferred re-recording).

## 10. Release submitted to Play review (2026-06-25)

The §8 plan executed. The capture-on release that first carries `FOREGROUND_SERVICE_MEDIA_PROJECTION` onto a reviewed track is built and submitted.

- Release `2.60.6251.711` (versionCode `260625171`) built 2026-06-25 via `/skill-release` (full pipeline minus publication). `DEBUG-v018` (carrying committed `fms.screenCapture=on`) merged to `main`, tag `release/v2.60.6251.711`, signed standard AAB in `DOWNLOADS/FastMediaSorter_standard_release.aab` (55.7 MB). This resolves the §9 build-note constraint - `main` now carries the capture-on release config, so the bundle is a correctly-configured capture-on build.
- Declares `MEDIA_PROJECTION` only (`MEDIA_PLAYBACK` + `MICROPHONE` already approved); `SPECIAL_USE` / `OverlayHostService` absent (`fms.edgeGestureOverlay=off`). So only §5.1 applies this round; §5.2 SPECIAL_USE stays deferred (S0672). Lower rejection risk than the old Release B which declared both.
- Owner published manually: AAB upload to production + Foreground-service-permissions declaration. Release is **in Play review as of 2026-06-25**; expected adjudication ~1 day.
- §9 open gap still applies at submission time: the MEDIA_PROJECTION demo video must be (re-)recorded per shot-list §6.1 and its link pasted into the §5.1 form.
- Next-step branch on the review verdict:
  - Approved -> complete the remaining distribution channels (GitHub Release / 4pda / IzzyOnDroid) per `/skill-release` Step 12a; this ticket can then move to Verified.
  - Rejected -> §8 step 4 / §2.3 fallback: keep MEDIA_PROJECTION but move the trigger off the in-app menu to a Quick Settings tile / notification action (`AudioToggleTileService` infra exists), resubmit.

---

## Related

- Release A (FGS-free fast build) - separate ticket / release-engineering task.
- S0559 (menu screenshot, MEDIA_PROJECTION), S0621 (left-edge gesture overlay, SPECIAL_USE).
- S0628 (publisher attach-existing-bundle), play-console FGS gate.
