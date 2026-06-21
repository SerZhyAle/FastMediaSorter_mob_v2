# /spec-prerelease - End-to-End Pre-Release Emulator Sweep

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry technical prose, no filler.
> 2. Autonomy over bureaucracy: don't block on minor issues; surface only critical findings.
> 3. Terse report: end with one line - verdict + report path.
> 4. Never auto-run the release: PASS proposes `/skill-release`, owner confirms (ADR-1, S0484).

Automates `dev/PRE_RELEASE_MANUAL_TESTS.md` as one gated sweep on an emulator: prepare a clean
standard-debug install with seeded media → configure resources + settings → drive the core
scenario (playback, standalone-player roundtrip, re-entry, network scroll, landscape-rotation
crash sweep) → measure perf → aggregate a machine PASS/FAIL verdict. PASS proposes
`/skill-release`; FAIL parks deduped
`/spec-draft` tickets and routes pending-test tickets through `/spec-check`.

It composes existing tools - `scripts/devtest/prerelease-prepare.ps1`,
`scripts/devtest/prerelease-configure.ps1`, `scripts/devtest/prerelease-measure.ps1`,
`scripts/devtest/prerelease-verdict.ps1`, `scripts/devtest/prerelease-log-audit.ps1`,
`scripts/utils/search-log.ps1`, mobile-mcp, `/skill-release`, `/spec-draft`, `/spec-check` -
and adds **no** app runtime code (S0484 ADR-2).

## Usage

```text
/spec-prerelease                      # use the single online emulator
/spec-prerelease --device <id>        # pin a specific adb id
/spec-prerelease --dry-run            # plan only - no build/install/UI/verdict
```

Hard requirement: **mobile-mcp** server reachable (same gate as `/spec-test-device`). The
standard-debug build must be built where `local.properties` sets `sza.owner.trigger`: resource
import is performed only through the OWNER_TRIGGER Settings-field path (S0492 - there is no
adb-scriptable import; the former `file://` intent-push always failed on minSdk 26).

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
language via `cmd locale`; resource import is delegated to the UI scenario below):

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 [-DeviceId <id>] -Json
```

The run config is `scripts/devtest/prerelease.config.psd1` (resource picks + reachability class +
setting channels). Then drive the UI via mobile-mcp for the parts adb cannot do (resolve every
target from `mobile_list_elements_on_screen`, never hard-coded coordinates):

- **Import resources:** open Settings, type the `sza.owner.trigger` value into the "Default User"
  field and commit it; this fires the import-confirm dialog reading the APK-bundled
  `res/xml/sza_resources.xml`. Tap confirm to complete the import (S0492 - the only working import
  path; there is no adb intent-push).
- **DataStore settings:** apply each `Channel='ui'` setting (theme DARK, sort DATE_DESC, grid on,
  trash on / confirm off, accept-shared on) through Settings; relaunch after the theme change.
- **Listing check:** open each `probe-and-list` resource and confirm its file list loaded via the
  `BrowseLoadingManager: COMPLETE - N files loaded and displayed` log marker (the `register-only`
  SMB resource is verified as registered only, not listed).

### 3 - Drive the core scenario

Start a background logcat capture into `temp/s0484_run_<TS>.log` first (clear, then
`adb logcat -v threadtime *:V`). **Use `threadtime`, not `time`** - `search-log.ps1` (and the
verdict's error count built on it) only parse the `threadtime` line shape (pid+tid+package
columns); a `-v time` capture parses to zero rows, so the verdict silently reads
`actionableErrors=0` and a screen full of red error toasts passes as a clean log. Walk the
scenario via mobile-mcp; resolve every target from
`mobile_list_elements_on_screen` immediately before acting; save evidence with
`mobile_save_screenshot` to `temp/s0484_screens/` (off-context). Token discipline mirrors
`/spec-test-device` - never screenshot to find an element.

Scenario steps and the perf checkpoint each captures:

1. **Cold start** - measured by the pre-flight launch:
   `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json`.
2. **In-app playback** - open and play several file types (video, image, audio, document) from
   the local resource; confirm each renders.
3. **List scroll** - `dumpsys gfxinfo <pkg> reset`, scroll a populated list, then
   `prerelease-measure.ps1 -Checkpoint list-scroll -Json`. On an emulator this record is emitted
   `advisory` (gfxinfo janky% is structurally inflated by software rendering): reported in the
   verdict breakdown but not release-gating. It gates only on physical devices.
4. **Standalone-player roundtrip** - close the app, launch the standalone player on a seeded file
   via `am start -a android.intent.action.VIEW -d file://<seeded> -n <pkg>/com.sza.fastmediasorter.ui.player.StandalonePlayerActivity`,
   then tap overflow → `menu_open_in_fms` to return into the in-app player; confirm `PlayerActivity`
   is foreground via `dumpsys activity top`. Pass the measured open time to
   `prerelease-measure.ps1 -Checkpoint player-open -ElapsedMs <n> -Json`.
5. **Re-entry without reinstall** - relaunch the app; confirm warm start, no crash.
6. **Network scroll + playback** - open the SFTP resource, scroll the listing, play a file; time
   the listing open and pass it to `prerelease-measure.ps1 -Checkpoint network-listing -ElapsedMs <n> -Json`.
7. **Landscape rotation crash sweep** - for each of the three primary surfaces in turn - the main
   browse Activity, the Settings screen, and the in-app `PlayerActivity` with a file playing -
   rotate to landscape (`mobile_set_orientation landscape`), confirm the surface re-renders and is
   still foreground (`dumpsys activity top`), then rotate back to portrait
   (`mobile_set_orientation portrait`) and confirm again. An orientation change destroys and
   recreates the Activity, so a missing `layout-land/*.xml` view id, an unchecked binding cast, or
   a non-config-safe ViewModel surfaces here as a crash / `ActivityThread` exception in the same
   logcat capture that step 4/4.1 reads. Treat any crash, ANR, or new actionable cluster bound to a
   rotation as a FAIL finding. Restore portrait before stopping the capture.

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

The verdict is a coarse gate: it produces one error count and hard-stops only on crashes/ANR.
It does **not** enumerate which app errors fired, so a green verdict alone never proves the run
was clean. Always run the detailed log audit below before trusting a PASS.

### 4.1 - Detailed log audit (mandatory, every run)

The verdict's log signal is a single number; red toasts and handled-but-loud failures hide
behind it. Run the deep audit over the same log:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.ps1 -LogFile temp/s0484_run_<TS>.log -Json
```

It parses both logcat formats, keeps app-process lines, folds stack traces into their throwing
cluster, then splits clusters into **benign** (known emulator/capability fallbacks - Cast/Dynamite
absent, `WifiRequiredException`, emulator GPU noise) and **actionable**, and separately flags
user-facing error surfaces (toast / snackbar / `showError`). Exit `0` = clean, `1` = actionable
clusters and/or error toasts present (triage), `2` = log unreadable.

Treat every **actionable cluster** and every **error toast** as a finding even when the machine
verdict is PASS - a working stream that still throws a red toast (e.g. FTP active-mode fallback
NPE during otherwise-fine audio playback) is a real defect. Fold the audit's actionable clusters
into the report's verdict breakdown and into the FAIL-branch `/spec-draft` triage below; an
emulator-only benign cluster that recurs every sweep is a candidate for the audit's benign
allowlist, not a ticket. Include the audit JSON path in the evidence pack.

**On PASS:** only after the audit is clean (or its findings are triaged/parked) print the report
path and propose `/skill-release` as the next step. **Do not auto-run it** - the release starts
only on explicit owner confirmation (ADR-1). State this in the final line.

### 5 - Branch on FAIL

For each distinct defect the verdict **or the step-4.1 audit** surfaced (research/06):

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
