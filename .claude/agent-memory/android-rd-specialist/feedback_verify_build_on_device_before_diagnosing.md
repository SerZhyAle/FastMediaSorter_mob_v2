---
name: verify-build-on-device-before-diagnosing
description: When a device fix "doesn't work", first confirm the phone runs the NEW build - same-version debug APKs can silently keep the old one
type: feedback
---

When the owner reports an on-device fix "doesn't work", **confirm the running build is actually the new one before analyzing behavior**. Debug APKs all share the same `versionName`/`versionCode` (e.g. `v2.60.7122.153`), so a reinstall over the top can silently keep the OLD build and the owner unknowingly tests stale code.

**Why:** cost two diagnostic round-trips on S1042. The device log had zero `S1042:` probe tags and showed `CameraCaptureActivity` opening WITHOUT the new `CameraOcrTranslateActivity` wrapper - proof the phone was on a pre-S1042 APK, not a code bug. I'd assumed the new build was installed and chased phantom causes (BAL/task-affinity).

**How to apply:**
- Key device diagnosis off a **build-unique marker** first: a `Timber.d("Sxxxx: ..")` probe on the changed path, or the version string. If the expected new marker/activity is absent from the log, suspect a stale install before suspecting the code.
- **Prove presence positively, do not infer it from a missing log line** - "no probe fired" is equally consistent with a stale APK and with a genuine behaviour failure, and the two lead to opposite conclusions. Pull the installed APK (`adb pull $(adb shell pm path <pkg> | sed s/package://)`), extract every `classes*.dex`, and count symbol hits for a class the change introduced, **alongside a pre-existing class as a positive control**. 0 for the new symbol and non-zero for the control = stale install, proven.
- **A raw `grep` over the `.apk` is not a valid presence test** - dex entries are compressed, so it returns 0 even for an APK that certainly contains the code. Verified by running the same grep against a freshly built host APK as a control; it also returned 0. Unzip the dex first, or the "evidence" is a false negative. The on-device `/data/app/.../oat/` vdex is not a way around it either - permission denied without root.
- Install time versus source mtime is a fast pre-screen: an APK installed before the feature's `.kt` files were written cannot contain them.
- **Never hand a subagent "the build on that device is current" as a fact when the only evidence is `versionName`.** On 2026-07-29 both emulators reported `2.60.7262.102-DEBUG`; 5554 carried the S1276 code and 5556 did not, and the run lost a criterion to it. If you are telling an agent not to rebuild, either prove the install by dex symbol count first or tell the agent to prove it before trusting the device.
- For device hand-offs, build with `a.ps1 dav` (timestamped app version) so the owner can visually confirm a distinct version in About / the installer, instead of same-versioned `a.ps1 d`.
- Emulator can't drive edge gestures / MediaProjection overlay (see [[reference_emulator_capture_family_testing]]); the owner tests on the real Galaxy S21+ - so a fresh post-gesture log is the only signal. Ask for the log captured **right after** the gesture, not before.
