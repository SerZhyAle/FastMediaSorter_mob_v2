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

## Cross-module rule (S1496)

A library coordinate declared in **both** `app_v2/build.gradle.kts` and `wear/build.gradle.kts` must carry the same version. `Get-GradlePins` throws when it does not, in the same shape `Get-SharedModulePin` already used for `compileSdk` / `targetSdk`:

```text
GradleParser: lib.<group>:<artifact> differs between app_v2 (<X>) and wear (<Y>)
```

A coordinate declared in only one module has nothing to compare against and is never an error. The returned pin values still come from `app_v2`, so no `pins.psd1` record changes meaning.

There is **no allowed-divergence registry**, deliberately. When the rule was introduced, exactly one of the 19 coordinates shared by the two modules diverged - `jsch`, at `0.2.26` against `0.2.17` - and that one was aligned in the same ticket, so the registry would have held zero entries. The first genuine case of a version that must differ between modules is the point at which to design one, with the reason recorded next to it. Deleting the check is not the fix.

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

---

# Rule/prompt executable drift (S0315)

A sibling audit that finds executable mismatch between `CLAUDE.md`, prompt skills, agent profiles, `AGENTS.md`, copilot instructions, workflow docs, and the scripts on disk (missing scripts, route conflicts, missing `-NoProfile`, abolished artefacts).

- Quick start: `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1`
- Full reference: [`RULE_PROMPT_DRIFT.md`](RULE_PROMPT_DRIFT.md)
- Scope: executable mismatch only; version-pin drift stays in `check-doc-vs-gradle.ps1` (S0271 above), so the two checkers in this directory are not confused.
