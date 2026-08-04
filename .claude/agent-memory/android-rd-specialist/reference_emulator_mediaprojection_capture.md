---
name: emulator-verifies-mediaprojection-screenshot
description: A standard non-VR emulator CAN fully verify the MediaProjection menu-screenshot path end-to-end (consent + capture + MediaStore save)
metadata:
  type: reference
---

The standard (non-VR) AVD can verify the whole MediaProjection menu-screenshot flow end-to-end - no real device needed for that path (S0559, verified 2026-06-21 on emulator-5556).

**What works on the emulator:**
- Tapping the action launches `ScreenCaptureConsentActivity` -> the system `com.android.systemui/.media.MediaProjectionPermissionActivity` consent dialog ("…will start capturing everything…", CANCEL / START NOW).
- Granting -> `ScreenCaptureService` captures a frame via `ImageReader` and `MediaStoreLocalDestinationWriter` saves a real PNG to `/storage/emulated/0/Pictures/Screenshots/` (confirmed 215 KB file). Service releases on `onDestroy()`.

**How to apply:** when deciding `/spec-test-device` feasibility, treat MediaProjection screen-capture + MediaStore-save as emulator-verifiable (unlike the AVD-media-not-indexed and OpenXR/VR-immersive cases, which are not). The `BufferQueue has been abandoned` E-logs after the single frame are normal teardown, not a failure. Contrast with [[avd-mediastore-not-indexed]] (reading seeded media) and the Quest3-only VR items in [[project-vr-hud-quirks]].

**Caveat:** debug builds expose two launcher icons (app + LeakCanary); `monkey -c LAUNCHER` can resolve to `LeakLauncherActivity`. Start `com.sza.fastmediasorter.ui.main.MainActivity` explicitly instead.
