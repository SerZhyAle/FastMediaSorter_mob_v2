# S0271 - Doc Drift Checker

## Purpose

This checker compares declared versions in `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, and `CLAUDE.md` against canonical Gradle sources so documentation drift is visible before it slows down research or review. Strategic context lives in `PLAN/S0271_truth_drift_detection.md`.

## Quick start

- Full run: `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1`
- Single-pin probe: `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin agp`
- Bootstrap mode: `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -AsBootstrapWarning`

## Output grammar

```text
FAIL | <pin> | gradle: <X> | <doc-path>: <Y>
WARN | <pin> | gradle: <X> | <doc-path>: <Y> (range)
INCONSISTENT | <pin> | <doc-path>: <Y1> vs <Y2>
MISSING | <pin> | <doc-path>: required mention not found
SKIP | <pin> | reason: <text>
PASS | <pin> | <X>
SUMMARY | total: N | pass: A | fail: B | warn: C | skip: D | inconsistent: E | missing: F
```

## Adding a new pin

Add one entry to `scripts/doc-drift/pins.psd1`.

```powershell
@{
    name = 'agp'
    gradleKey = 'agp'
    docs = @{
        'docs/TECH_STACK.md' = @{ required = $false; matcher = $null }
        'dev/TECH_REQUIREMENTS.md' = @{ required = $true; matcher = 'Android Gradle Plugin\s*\|\s*(?<v>[\d\.]+)' }
        'CLAUDE.md' = @{ required = $false; matcher = $null }
    }
    policy = 'allMustMatch'
    exclude = @()
}
```

Required-flag defaults in this repo:

- `dev/TECH_REQUIREMENTS.md`: required for the full inventory.
- `docs/TECH_STACK.md`: required only for the small cheat-sheet subset it explicitly owns.
- `CLAUDE.md`: required only for headline pins already mentioned in the prompt file.

## Tests

`pwsh -NoProfile -File scripts/doc-drift.tests/Run-Tests.ps1` returns `0` when all asserts pass.

## Limitations

- No auto-fix mode.
- No `wear/` coverage in this first iteration.
- No `libs.versions.toml` integration.
- No discovery of undocumented pins beyond the manifest.

## Exit codes

- `0`: no `FAIL`, `INCONSISTENT`, or `MISSING` records.
- `1`: at least one `FAIL`, `INCONSISTENT`, or `MISSING` record exists.
- `-AsBootstrapWarning`: forces exit `0` after printing the same report.
