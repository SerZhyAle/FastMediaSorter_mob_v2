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
standard-debug install with seeded media → configure resources + settings → run the deterministic
Maestro capability suite, use mobile-mcp only for uncovered exploratory paths → measure perf →
aggregate a machine PASS/FAIL verdict. PASS proposes
`/skill-release`; FAIL parks deduped
`/spec-draft` tickets and routes pending-test tickets through `/spec-check`. Step 0 first refreshes
the downloadable stream-catalog delivery asset (content, no device, non-gating).

It composes existing tools - `scripts/devtest/prerelease-prepare.ps1`,
`scripts/devtest/prerelease-configure.ps1`, `scripts/devtest/prerelease-measure.ps1`,
`scripts/devtest/prerelease-verdict.ps1`, `scripts/utils/search-log.ps1`,
`maestro/run-tests.ps1`, `scripts/streams/collect-stream-candidates.ps1`, mobile-mcp,
`/skill-release`, `/spec-draft`, `/spec-check` - and adds **no** app runtime code (S0484 ADR-2).

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

### 0 - Refresh the stream-catalog delivery asset (content, no device, non-gating)

The curated stream catalog `delivery/stream-catalog/streams.csv` ships as a **mutable** GitHub
Release asset, independent of the app binary (S0588; `delivery/stream-catalog/README.md`). Refresh
it here so the release carries fresh, live streams. This step needs no device, does not gate the
emulator verdict, and its own non-zero exit is a finding on the stream-catalog report line - never a
sweep abort. On `--dry-run`, list it as planned and run nothing (no network, no writes).

1. Append newly-discovered alive streams. The run keeps only `alive` rows and writes a timestamped
   `temp/` backup before touching the CSV:

   ```powershell
   pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -PerQuery 30
   ```

2. Probe the whole catalog as a **non-destructive** health report - it prints `Would prune N row(s)`
   and deletes nothing:

   ```powershell
   pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly
   ```

**Never auto-prune in this sweep.** Pruning is a human-gated opt-in: a geo-restricted stream reads
`dead`/404 from the build machine yet plays on a user's device. Review
`temp/stream-catalog-liveness.csv`; only if a row is genuinely dead after review (ideally a
second-network re-probe) run `scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -PruneDead`
manually, outside this sweep.

If `streams.csv` changed (append, or a later manual prune), re-package and re-upload the asset or the
change never reaches users - the app fetches the release asset, not the repo file:

```powershell
Compress-Archive -Path delivery/stream-catalog/streams.csv -DestinationPath temp/stream-catalog.zip -Force
```

Upload `temp/stream-catalog.zip` to the `delivery-so-v1` release (Hosting in
`delivery/stream-catalog/README.md`).

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
`adb logcat -v threadtime *:V`). **Use `threadtime`, not `time`** - `search-log.ps1` and the
verdict error count only parse the `threadtime` line shape; a `-v time` capture parses to zero
rows, so the verdict silently reads `actionableErrors=0` and red error toasts pass as a clean
log. Run the revived Maestro capability suite as the deterministic regression layer:

```powershell
$ts = Get-Date -Format yyyyMMdd_HHmmss
$maestroResults = "temp/maestro_suite_$ts.json"
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all [-DeviceId <id>] -Json > $maestroResults
```

The suite output is off-context except for the compact JSON verdict. Treat non-zero runner exit as
a FAIL finding and keep `$maestroResults` as evidence for step 4. Use mobile-mcp only for new or
exploratory paths that do not yet have a Maestro flow; resolve every target from
`mobile_list_elements_on_screen` immediately before acting. Capture screenshots only on FAIL to
`temp/s0484_screens/` as evidence; screenshots are no longer a pass gate.

Perf checkpoints still run where the suite or exploratory path exercises the surface:

1. **Cold start** - measured by the pre-flight launch:
   `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json`.
2. **List scroll** - `dumpsys gfxinfo <pkg> reset`, scroll a populated list, then
   `prerelease-measure.ps1 -Checkpoint list-scroll -Json`. On an emulator this record is emitted
   `advisory` and does not release-gate.
3. **Player open** - pass the measured open time from a covered player flow or exploratory
   standalone-player roundtrip to
   `prerelease-measure.ps1 -Checkpoint player-open -ElapsedMs <n> -Json`.
4. **Network listing** - when a reachable network resource is part of the run, pass its measured
   listing open time to
   `prerelease-measure.ps1 -Checkpoint network-listing -ElapsedMs <n> -Json`.

Stop the background logcat capture at the end. Write each measure record to
`temp/s0484_metrics_<TS>.json` (array consumed by the verdict aggregator).

### 4 - Aggregate the verdict and branch on PASS

Run the verdict aggregator over the run window:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-verdict.ps1 `
    -LogFile temp/s0484_run_<TS>.log `
    -MetricsFile temp/s0484_metrics_<TS>.json `
    -MaestroResults temp/maestro_suite_<TS>.json `
    -ScreensDir temp/s0484_screens `
    -Json
```

Exit `0` = PASS, `1` = content FAIL, `2` = infrastructure abort. Write a timestamped report to
`temp/s0484_prerelease_<TS>.md` (device profile, per-stage results, verdict breakdown, evidence
paths). The aggregate verdict is `log AND perf AND maestro`; screenshots are evidence-only.

### 4.1 - Detailed log audit (mandatory, every run)

The verdict's log signal is a single count and gates only on crashes; red toasts and handled-but-loud
failures hide behind it. Run the deep audit over the same log:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.ps1 -LogFile temp/s0484_run_<TS>.log -Json
```

It folds stack traces into clusters and splits them into **benign** (known emulator/capability
fallbacks) vs **actionable**, and flags user-facing error surfaces (toast/snackbar/`showError`).
Exit `0` clean, `1` actionable clusters/error toasts present, `2` unreadable. Treat every actionable
cluster and error toast as a finding even on a PASS verdict; fold them into the report and the
FAIL-branch `/spec-draft` triage. Include the audit JSON path in the evidence pack.

**On PASS:** only after the audit is clean or its findings are triaged/parked, print the report path
and propose `/skill-release` as the next step. **Do not auto-run it** - the release starts only on
explicit owner confirmation (ADR-1). State this in the final line.

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
`/spec-check`. Append a `stream-catalog: +N appended, M would-prune, re-upload <done|n.a.>` segment
(or `stream-catalog: skipped (--dry-run)`).
