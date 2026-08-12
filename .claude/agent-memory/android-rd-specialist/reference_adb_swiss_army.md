---
name: adb-swiss-army
description: scripts/devtest/adb.ps1 + .\a.ps1 adb - quick ad-hoc device CLI for one-off emulator/device chores
type: reference
---

`scripts/devtest/adb.ps1 <verb>` (alias `.\a.ps1 adb <verb>`, shortcuts `adb-devices/-shot/-log/-current/-launch/-logcat-clear`) is the ad-hoc device swiss-army for quick manual work against a connected emulator/real device. Runs natively (~0 LLM tokens); auto-discovers adb (not on PATH); takes `-DeviceId`/`-Release`/`-Package`/`-Json`; stable exit codes (0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed / 5 destructive-verb-refused / 7 adb-failed). Mirrors `device-ready.ps1` conventions.

Verbs: devices, props, current, launch (debug: explicit MainActivity, dodges LeakCanary), stop, logcat-clear (alias log-clear), wipe-data (needs `-Yes`), install (`-Apk`/`-Flavor`), uninstall (needs `-Yes`), shot (-> temp/), log (`-Tail N -Grep <regex>`, full dump -> temp/), tap (`-X -Y`), text (`-Text`), key (`-Key`), prefs (run-as app_settings.xml), shell (`-Cmd`).

**Why:** filled the gap between purpose-bound scripts (device-ready=pre-flight, extract-device-logs=harvest, build-*-device=build+install, maestro-run=repeatable) - there was no generic "quickly poke the device" tool, forcing raw full-path adb. Added 2026-06-15.

**Trap - `launch` is not a launcher intent.** The `launch` verb starts the explicit MainActivity component and sends
no `ACTION_MAIN`/`CATEGORY_LAUNCHER`. Any behaviour gated on a genuine cold *launcher* start - resume-on-launch,
first-run onboarding, launcher-role paths - is therefore unreachable through it and looks broken. Hit on 2026-07-26
(S1152 stream-resume): the resume branch never ran until the run built a real launcher intent by hand
(`am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n <pkg>/<MainActivity>`).
**How to apply:** for a *resume / cold-start / onboarding* criterion, do not use `adb.ps1 launch` - send an explicit
launcher intent, or a no-resume result is a false FAIL, not a defect.

**The `clear` trap is now closed mechanically (S1572, 2026-08-11).** History: the `clear` verb ran `pm clear <pkg>`
and sat two lines from `log` in the verb list, so "clear the logcat buffer" grabbed it by association - twice.
2026-07-26 (S1167) it revoked a just-granted accessibility service; 2026-08-11 (S1569) a device-operator subagent
wiped `com.sza.fastmediasorter.debug` on the owner's phone. Prose did not hold across two occurrences, which is why
the remedy went into the script.
**Current shape:** `clear` is gone and *refuses* with exit 5, naming both replacements. `logcat-clear` (alias
`log-clear`) empties the buffer and touches no app state. `wipe-data` is the destructive one and refuses without
`-Yes`; so does `uninstall`. Refusal happens after device and package resolution, so the flag waives the
confirmation, never the checks.
**How to apply:** say `logcat-clear` in a device brief, not "clear the log" - and never hand a subagent an *intent*
where a destructive-adjacent verb exists, hand it the literal verb. The residual lesson survives the fix: a subagent
inherits none of the caller's caution.

**How to apply:** reach for this (not raw `adb`) for one-off device chores. Three-layer split: this = manual/ad-hoc work; `mobile-mcp` = agent-driven UI walks (re-enabled, in `.mcp.json`); Maestro (`scripts/devtest/maestro/`) = repeatable token-free flows. Documented in CLAUDE.md §9 + docs/DEV_OPS.md "DEVICE OPS"; wired into `/spec-test-device`, `/verify`, `/log-reader`. PowerShell gotcha baked in: avoid a local var whose name case-collides with a `[switch]` param (e.g. `$release` vs `[switch]$Release`), and `@()`-wrap single-element function returns before indexing.
