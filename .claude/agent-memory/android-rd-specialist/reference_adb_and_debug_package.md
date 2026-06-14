---
name: adb-and-debug-package
description: adb is not on PATH; lives in user SDK platform-tools. Debug builds install as com.sza.fastmediasorter.debug, not the bare release id. Quest3 logcat buffer is short.
metadata:
  type: reference
---

Device tooling facts (verified 2026-06-01 on Quest 3 `2G0YC5ZG5608DL`):

- **adb is NOT on the bash/PowerShell PATH.** It lives at `C:\Users\serzh\AppData\Local\Android\Sdk\platform-tools\adb.exe`. Scripts must resolve it explicitly (PATH → `$env:LOCALAPPDATA\Android\Sdk` → `$env:ANDROID_HOME` → `$env:ANDROID_SDK_ROOT`). `scripts/utils/extract-device-logs.ps1` now does this via `Resolve-Adb`. Note `ANDROID_HOME`/`ANDROID_SDK_ROOT` are usually unset on this machine — guard against null before `Join-Path`.
- **Debug builds install with the `.debug` applicationIdSuffix** → package id on device is `com.sza.fastmediasorter.debug`, not `com.sza.fastmediasorter`. Any logcat filter / `run-as` / `pm path` that hard-codes the bare release id silently yields empty output on a debug install. `extract-device-logs.ps1` now auto-detects the installed variant (`pm list packages` → match base or `base.*`).
- **Quest 3 logcat ring buffer is short** — a `-d` dump may only cover the last ~3 minutes; the app's own file logs under `logs/fastmediasorter_*.log` (+ crash report `fastmediasorter_crash_*.log`) are the durable per-session source, written by the in-app logger.
- **adb daemon cold-start drops the device transiently** — the first `adb devices` right after `* daemon started successfully` can return empty; the Quest may need a physical reconnect. Don't conclude "no device" from a single call made during daemon startup.
- **App launched under the IDE debugger breaks `/spec-sweep` probe harvest** — when the app is started from Android Studio in Debug (or with Developer Options "Wait for debugger"), it first freezes on a "Waiting For Debugger" dialog (looks like a hang — see [[feedback_frozen_app_check_tracerpid]]), and once attached the IDE holds/clears the logcat ring buffer so a `logcat -d | grep 'Sxxxx:'` harvest returns **0 probes** even though the `Timber.d` probes are firing. For probe-based device verification, launch the **installed APK normally** (mobile-mcp `mobile_launch_app` or `monkey -p <pkg> 1`), not via AS Debug. Verified 2026-06-07 on emulator-5554.
- **First run lands on Settings, not MainActivity** — with no resources configured, the app opens into Settings; the resource-list/browse window (MainActivity, where `S0319`/reorder/section tickets live) is only reachable after granting storage + adding a resource. On the emulator browse is empty (no media), so media-fixture tickets still need pushed AV files.

**How to apply:** when pulling device logs or driving the device, never assume `adb` on PATH and never assume the bare package id — use the SDK path and the `.debug` variant. For "today's logs" prefer the app's file logs in `logs/` over a logcat dump.
