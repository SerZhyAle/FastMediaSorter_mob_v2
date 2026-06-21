---
name: vr-immersive-logcat-capture-trap
description: Android Studio "package:mine" logcat export drops the VR immersive session - use raw adb logcat -b all during repro
metadata:
  type: reference
---

When debugging the VR immersive engine on Quest, an Android Studio logcat export with filter `package:mine` does NOT contain the app's immersive session: the native engine tag (`S0249.XrSession` / `S0249.JniBridge` / `S0249.DiagXrRenderThread`) and even `DiagnosticXrActivity` Timber lines are absent, because immersive runs in native threads and the per-entry Activity is finish()-ed (a dead/native pid is dropped by the package filter). The app's own file logs in `logs/fastmediasorter_*.log` only carry Kotlin Timber (tag `App`), never the native `S0249.*` lines, and that dir self-rotates mid-session.

To actually capture a re-entry repro, drive it yourself: `scripts/devtest/adb.ps1 shell -Cmd "logcat -b all -c"` to clear, then run a continuous raw `adb logcat -b all -v threadtime` to a `temp/*.txt` file while the owner does enter/exit/enter in the headset, then stop it. adb is at `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` (not on PATH). Gotchas seen live: TaskStop on the background logcat can orphan the child adb (it keeps the output file locked - kill stray `adb.exe ... logcat` via CIM before restarting, and write each run to a fresh filename); the Quest also drops offline when the owner removes the headset, which fails the capture.
