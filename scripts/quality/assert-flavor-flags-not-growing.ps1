#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: the number of flavor-flag reads in src/main must never grow.

.DESCRIPTION
    CLAUDE.md Rule 15 forbids new `BuildConfig.SUPPORT_* / ENABLE_* / IS_*` flavor
    guards in `src/main/java/**`. The existing reads are technical debt to be
    refactored out incrementally (interface-in-main + impl-in-flavor, e.g. the
    MediaCapabilities surface). This gate makes the rule mechanical instead of
    relying on reviewer vigilance: it counts the occurrences and compares against a
    committed baseline. The count may only go DOWN (refactor removes a read) - any
    growth fails the gate.

    Baseline lives in scripts/quality/flavor-flag-baseline.txt (a single integer).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only: if current < baseline, rewrite the
                       baseline to current. Refuses to raise it.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-flavor-flags-not-growing.ps1
    pwsh -NoProfile -File scripts/quality/assert-flavor-flags-not-growing.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-flavor-flags-not-growing.ps1 -UpdateBaseline
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main/java'
$baselineFile = Join-Path $PSScriptRoot 'flavor-flag-baseline.txt'

if (-not (Test-Path $baselineFile)) { Write-Error "Baseline file missing: $baselineFile"; exit 2 }
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())

$rx = [regex]'BuildConfig\.(?:SUPPORT_|ENABLE_|IS_)[A-Za-z0-9_]+'
$current = 0
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
foreach ($file in $files) {
    foreach ($line in Get-Content -LiteralPath $file.FullName) {
        $current += $rx.Matches($line).Count
    }
}

switch ($PSCmdlet.ParameterSetName) {
    'Update' {
        if ($current -lt $baseline) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "flavor-flag baseline ratcheted DOWN: $baseline -> $current"
        }
        elseif ($current -eq $baseline) {
            Write-Host "flavor-flag baseline unchanged ($baseline)"
        }
        else {
            Write-Error "Refusing to RAISE baseline ($baseline -> $current). Flavor flags grew - refactor instead of widening the cap."
            exit 1
        }
        exit 0
    }
    default {
        $delta = $current - $baseline
        Write-Host ("flavor-flags in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
        if ($Gate -and $current -gt $baseline) {
            Write-Host "FAIL: flavor-flag reads grew above baseline. Use the capability-surface pattern (MediaCapabilities) instead of a new BuildConfig guard in src/main; refactor an existing read to offset, or fix the new one."
            exit 1
        }
        if ($current -lt $baseline) {
            Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
        }
        exit 0
    }
}
