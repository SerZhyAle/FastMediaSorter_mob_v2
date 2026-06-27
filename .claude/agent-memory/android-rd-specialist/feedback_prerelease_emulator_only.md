---
name: prerelease-emulator-only
description: /spec-prerelease is built for a clean emulator; on a real device it wipes config and machine-FAILs on system noise
type: feedback
metadata:
  type: feedback
---

`/spec-prerelease` is designed for a clean emulator. Running it on a real/personal device has two traps:

1. `prerelease-prepare.ps1` does a clean **uninstall+reinstall** - this WIPES the device's configured app resources (Google Drive, SMB/SFTP rows, settings). On a personal device confirm with the owner first (owner approved the wipe on 2026-06-27 for the Galaxy S21+ test device, but flag it every time).
2. On a real device the run log (`logcat *:V`) is full of OTHER apps' system errors (RegisteredAidCache/NFC, Finsky/Play, keystore2, msys/Meta, SurfaceComposerClient). `prerelease-log-audit.ps1` benign-allowlist is emulator-tuned, so it returns exit 1 / hundreds of "actionable" clusters that are NOT our app. Likewise Maestro image flows fail on seed-not-MediaStore-indexed (S0747). Net: machine verdict reads FAIL even when the app is clean.

**Why:** the verdict aggregator + audit assume an emulator with only our app's noise; a real device defeats both the benign allowlist and the seed fixtures.

**How to apply:** for a trustworthy machine-green gate before `/skill-release`, run `/spec-prerelease` on a clean **emulator**. If forced onto a real device, isolate the app signal manually: grep the log for `FATAL EXCEPTION`, `ANR in com.sza.fastmediasorter`, app-pid AndroidRuntime, and `toastCount` - those, plus perf, are the real release signal. A FAIL whose only causes are maestro fixture-seeding + foreign-process log noise is environment, not an app blocker. Related: [[project_prerelease_maestro_harness_flaky]], [[reference_test_device_galaxy_s21]].
