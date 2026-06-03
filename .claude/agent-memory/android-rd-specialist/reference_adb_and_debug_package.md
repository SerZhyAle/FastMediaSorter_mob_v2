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

**How to apply:** when pulling device logs or driving the device, never assume `adb` on PATH and never assume the bare package id — use the SDK path and the `.debug` variant. For "today's logs" prefer the app's file logs in `logs/` over a logcat dump.
