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
