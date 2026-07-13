---
name: verify-build-on-device-before-diagnosing
description: When a device fix "doesn't work", first confirm the phone runs the NEW build - same-version debug APKs can silently keep the old one
type: feedback
---

When the owner reports an on-device fix "doesn't work", **confirm the running build is actually the new one before analyzing behavior**. Debug APKs all share the same `versionName`/`versionCode` (e.g. `v2.60.7122.153`), so a reinstall over the top can silently keep the OLD build and the owner unknowingly tests stale code.

**Why:** cost two diagnostic round-trips on S1042. The device log had zero `S1042:` probe tags and showed `CameraCaptureActivity` opening WITHOUT the new `CameraOcrTranslateActivity` wrapper - proof the phone was on a pre-S1042 APK, not a code bug. I'd assumed the new build was installed and chased phantom causes (BAL/task-affinity).

**How to apply:**
- Key device diagnosis off a **build-unique marker** first: a `Timber.d("Sxxxx: ..")` probe on the changed path, or the version string. If the expected new marker/activity is absent from the log, suspect a stale install before suspecting the code.
- For device hand-offs, build with `a.ps1 dav` (timestamped app version) so the owner can visually confirm a distinct version in About / the installer, instead of same-versioned `a.ps1 d`.
- Emulator can't drive edge gestures / MediaProjection overlay (see [[reference_emulator_capture_family_testing]]); the owner tests on the real Galaxy S21+ - so a fresh post-gesture log is the only signal. Ask for the log captured **right after** the gesture, not before.
