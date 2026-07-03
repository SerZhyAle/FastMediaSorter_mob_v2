---
name: adb-swiss-army
description: scripts/devtest/adb.ps1 + .\a.ps1 adb - quick ad-hoc device CLI for one-off emulator/device chores
type: reference
---

`scripts/devtest/adb.ps1 <verb>` (alias `.\a.ps1 adb <verb>`, shortcuts `adb-devices/-shot/-log/-current/-launch/-clear`) is the ad-hoc device swiss-army for quick manual work against a connected emulator/real device. Runs natively (~0 LLM tokens); auto-discovers adb (not on PATH); takes `-DeviceId`/`-Release`/`-Package`/`-Json`; stable exit codes (0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed / 7 adb-failed). Mirrors `device-ready.ps1` conventions.

Verbs: devices, props, current, launch (debug: explicit MainActivity, dodges LeakCanary), stop, clear, install (`-Apk`/`-Flavor`), uninstall, shot (-> temp/), log (`-Tail N -Grep <regex>`, full dump -> temp/), tap (`-X -Y`), text (`-Text`), key (`-Key`), prefs (run-as app_settings.xml), shell (`-Cmd`).

**Why:** filled the gap between purpose-bound scripts (device-ready=pre-flight, extract-device-logs=harvest, build-*-device=build+install, maestro-run=repeatable) - there was no generic "quickly poke the device" tool, forcing raw full-path adb. Added 2026-06-15.

**How to apply:** reach for this (not raw `adb`) for one-off device chores. Three-layer split: this = manual/ad-hoc work; `mobile-mcp` = agent-driven UI walks (re-enabled, in `.mcp.json`); Maestro (`scripts/devtest/maestro/`) = repeatable token-free flows. Documented in CLAUDE.md §9 + docs/DEV_OPS.md "DEVICE OPS"; wired into `/spec-test-device`, `/verify`, `/log-reader`. PowerShell gotcha baked in: avoid a local var whose name case-collides with a `[switch]` param (e.g. `$release` vs `[switch]$Release`), and `@()`-wrap single-element function returns before indexing.
