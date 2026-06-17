---
agent: "agent"
description: "Use when: running the end-to-end pre-release sweep on an emulator (clean install, resources, settings, scenario, perf, verdict) that gates /skill-release, or asked to run /spec-prerelease. Triggers on: spec prerelease, pre-release sweep, spec-prerelease, prerelease emulator."
---

# /spec-prerelease - End-to-End Pre-Release Emulator Sweep

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry technical prose, no filler.
> 2. Autonomy over bureaucracy: don't block on minor issues; surface only critical findings.
> 3. Terse report: end with one line - verdict + report path.
> 4. Never auto-run the release: PASS proposes `/skill-release`, owner confirms (ADR-1, S0484).

Automates `dev/PRE_RELEASE_MANUAL_TESTS.md` as one gated sweep on an emulator: prepare a clean
standard-debug install with seeded media → configure resources + settings → drive the core
scenario (playback, standalone-player roundtrip, re-entry, network scroll) → measure perf →
aggregate a machine PASS/FAIL verdict. PASS proposes `/skill-release`; FAIL parks deduped
`/spec-draft` tickets and routes pending-test tickets through `/spec-check`.

It composes existing tools - `scripts/devtest/prerelease-prepare.ps1`,
`scripts/devtest/prerelease-configure.ps1`, `scripts/devtest/prerelease-measure.ps1`,
`scripts/devtest/prerelease-verdict.ps1`, `scripts/utils/search-log.ps1`, mobile-mcp,
`/skill-release`, `/spec-draft`, `/spec-check` - and adds **no** app runtime code (S0484 ADR-2).

## Usage

```text
/spec-prerelease                      # use the single online emulator
/spec-prerelease --device <id>        # pin a specific adb id
/spec-prerelease --dry-run            # plan only - no build/install/UI/verdict
```

Hard requirement: **mobile-mcp** server reachable (same gate as `/spec-test-device`). The
standard-debug build must be built where `local.properties` sets `sza.owner.trigger` only if the
OWNER_TRIGGER import fallback is used; the default import path (intent-push) needs no trigger.

## Process

### 1 - Pre-flight: prepare the emulator

Run the environment preparation (clean uninstall → install standard-debug → seed media when
absent → launch verify):

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-prepare.ps1 [-DeviceId <id>] -Json
```

Exit codes are abort signals: `1` adb missing, `2` no online device, `3` multiple devices
(pass `--device`), `10` a prepare stage failed. On any non-zero, abort with the failing stage
from the JSON `stages` array. On `--dry-run`, print the planned stages and stop here.

### 2 - Configure resources + settings

Run the adb-scriptable configuration (reachability pre-check honouring per-resource SKIP,
intent-push import trigger, language via `cmd locale`):

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 [-DeviceId <id>] -Json
```

The run config is `scripts/devtest/prerelease.config.psd1` (resource picks + reachability class +
setting channels). Then drive the UI via mobile-mcp for the parts adb cannot do (resolve every
target from `mobile_list_elements_on_screen`, never hard-coded coordinates):

- **Import confirm:** the `import-launch` stage opened `ResourceImportActivity`; tap its preview
  confirm button to complete the import. If the intent-push import did not surface (scoped-storage
  `file://` read), fall back to the OWNER_TRIGGER Settings-field path.
- **DataStore settings:** apply each `Channel='ui'` setting (theme DARK, sort DATE_DESC, grid on,
  trash on / confirm off, accept-shared on) through Settings; relaunch after the theme change.
- **Listing check:** open each `probe-and-list` resource and confirm its file list loaded via the
  `BrowseLoadingManager: COMPLETE - N files loaded and displayed` log marker (the `register-only`
  SMB resource is verified as registered only, not listed).

### 3 - Drive the core scenario

Start a background logcat capture into `temp/s0484_run_<TS>.log` first (clear, then
`adb logcat -v time *:V`). Walk the scenario via mobile-mcp; resolve every target from
`mobile_list_elements_on_screen` immediately before acting; save evidence with
`mobile_save_screenshot` to `temp/s0484_screens/` (off-context). Token discipline mirrors
`/spec-test-device` - never screenshot to find an element.

Scenario steps and the perf checkpoint each captures:

1. **Cold start** - measured by the pre-flight launch:
   `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json`.
2. **In-app playback** - open and play several file types (video, image, audio, document) from
   the local resource; confirm each renders.
3. **List scroll** - `dumpsys gfxinfo <pkg> reset`, scroll a populated list, then
   `prerelease-measure.ps1 -Checkpoint list-scroll -Json`.
4. **Standalone-player roundtrip** - close the app, launch the standalone player on a seeded file
   via `am start -a android.intent.action.VIEW -d file://<seeded> -n <pkg>/com.sza.fastmediasorter.ui.player.StandalonePlayerActivity`,
   then tap overflow → `menu_open_in_fms` to return into the in-app player; confirm `PlayerActivity`
   is foreground via `dumpsys activity top`. Pass the measured open time to
   `prerelease-measure.ps1 -Checkpoint player-open -ElapsedMs <n> -Json`.
5. **Re-entry without reinstall** - relaunch the app; confirm warm start, no crash.
6. **Network scroll + playback** - open the SFTP resource, scroll the listing, play a file; time
   the listing open and pass it to `prerelease-measure.ps1 -Checkpoint network-listing -ElapsedMs <n> -Json`.

Stop the background logcat capture at the end. Write each measure record to
`temp/s0484_metrics_<TS>.json` (array consumed by the verdict aggregator).

### 4 - Aggregate the verdict and branch on PASS

Run the verdict aggregator over the run window:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-verdict.ps1 `
    -LogFile temp/s0484_run_<TS>.log `
    -MetricsFile temp/s0484_metrics_<TS>.json `
    -ScreensDir temp/s0484_screens `
    -Json
```

Exit `0` = PASS, `1` = content FAIL, `2` = infrastructure abort. Write a timestamped report to
`temp/s0484_prerelease_<TS>.md` (device profile, per-stage results, verdict breakdown, evidence
paths).

**On PASS:** print the report path and propose `/skill-release` as the next step. **Do not
auto-run it** - the release starts only on explicit owner confirmation (ADR-1). State this in the
final line.

### 5 - Branch on FAIL

For each distinct defect the verdict surfaced (research/06):

- **Dedup first** by symptom via `scripts/spec_catalog/search.ps1` (error code / class / subsystem
  keyword + same-day created). If an open ticket already covers it, reference that id; do not draft
  a duplicate.
- Otherwise park one `/spec-draft` per distinct defect, capturing symptom + evidence (log lines,
  screenshot path under `temp/`) into §0.

For pending-test tickets (`BlockNeedUserTest`) whose flow this sweep exercised, route the evidence
through `/spec-check <Sxxxx>` - it owns the status flip (`Verified` / `Partial` / `Broken`) and, on
any transition leaving `BlockNeedUserTest`, the `Timber.d("Sxxxx:` tag removal. The sweep never
flips status by guess and never deletes a tag for a ticket that stays `BlockNeedUserTest`. Tickets
the sweep did not exercise stay untouched.

### Final report

One line: `spec-prerelease: device <id>, verdict PASS/FAIL, report temp/s0484_prerelease_<TS>.md`
- on PASS append the `/skill-release` proposal; on FAIL append the parked ids + tickets routed to
`/spec-check`.
