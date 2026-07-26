---
name: adb-swiss-army
description: scripts/devtest/adb.ps1 + .\a.ps1 adb - quick ad-hoc device CLI for one-off emulator/device chores
type: reference
---

`scripts/devtest/adb.ps1 <verb>` (alias `.\a.ps1 adb <verb>`, shortcuts `adb-devices/-shot/-log/-current/-launch/-clear`) is the ad-hoc device swiss-army for quick manual work against a connected emulator/real device. Runs natively (~0 LLM tokens); auto-discovers adb (not on PATH); takes `-DeviceId`/`-Release`/`-Package`/`-Json`; stable exit codes (0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed / 7 adb-failed). Mirrors `device-ready.ps1` conventions.

Verbs: devices, props, current, launch (debug: explicit MainActivity, dodges LeakCanary), stop, clear, install (`-Apk`/`-Flavor`), uninstall, shot (-> temp/), log (`-Tail N -Grep <regex>`, full dump -> temp/), tap (`-X -Y`), text (`-Text`), key (`-Key`), prefs (run-as app_settings.xml), shell (`-Cmd`).

**Why:** filled the gap between purpose-bound scripts (device-ready=pre-flight, extract-device-logs=harvest, build-*-device=build+install, maestro-run=repeatable) - there was no generic "quickly poke the device" tool, forcing raw full-path adb. Added 2026-06-15.

**Trap - `launch` is not a launcher intent.** The `launch` verb starts the explicit MainActivity component and sends
no `ACTION_MAIN`/`CATEGORY_LAUNCHER`. Any behaviour gated on a genuine cold *launcher* start - resume-on-launch,
first-run onboarding, launcher-role paths - is therefore unreachable through it and looks broken. Hit on 2026-07-26
(S1152 stream-resume): the resume branch never ran until the run built a real launcher intent by hand
(`am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n <pkg>/<MainActivity>`).
**How to apply:** for a *resume / cold-start / onboarding* criterion, do not use `adb.ps1 launch` - send an explicit
launcher intent, or a no-resume result is a false FAIL, not a defect.

**Trap - `clear` wipes app data, it is not a logcat clear.** The `clear` verb runs `pm clear <pkg>`: app data gone,
runtime permissions revoked, onboarding reset. Its help text says so, but it sits next to `log` in the verb list, so
an agent reaching for "clear the logcat buffer" grabs it by association. Hit on 2026-07-26 mid-S1167: it revoked a
just-granted accessibility service and cost a full re-onboarding to recover the device state.
**How to apply:** to clear logs use `adb logcat -c` (or just `log -Tail N`, which reads without clearing). Treat
`clear` as destructive - never run it mid-scenario once device state (permissions, grants, seeded settings) is set up.

**How to apply:** reach for this (not raw `adb`) for one-off device chores. Three-layer split: this = manual/ad-hoc work; `mobile-mcp` = agent-driven UI walks (re-enabled, in `.mcp.json`); Maestro (`scripts/devtest/maestro/`) = repeatable token-free flows. Documented in CLAUDE.md §9 + docs/DEV_OPS.md "DEVICE OPS"; wired into `/spec-test-device`, `/verify`, `/log-reader`. PowerShell gotcha baked in: avoid a local var whose name case-collides with a `[switch]` param (e.g. `$release` vs `[switch]$Release`), and `@()`-wrap single-element function returns before indexing.
