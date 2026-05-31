# Run-Tests.ps1 (S0313) - fixture self-test for the flavor-isolation guard.
#
# Test seam: the guard's -ScanFile hook (documented in
# scripts/guard/flavor-isolation-guard.ps1). Because Get-ChangedMainKotlin only
# resolves files under app_v2/src/main/java, the fixtures (.kt.txt, outside that
# root) cannot be reached by diff/-Path mode. -ScanFile scans one arbitrary file
# in explicit mode (every match new-or-touched), or as legacy when combined with
# -LegacyAudit. Production behaviour is unchanged - -ScanFile is test-only.
#
# Each fixture is run through the guard as a child process with -Json -DryRun, so
# the runner asserts on the JSON object and writes NO artifact of its own. The
# guard's own report write is suppressed by -DryRun, so this runner touches
# nothing under scripts/, app_v2/, or the repo root.
#
# Hermetic / dirty-tree-safe: the runner does not read or depend on live
# working-tree git state, stages or modifies no tracked file, and any transient
# scratch goes only under temp/. It is safe to run on a dirty tree and its exit
# code is idempotent across repeated runs.
#
# Exit codes:
#   0   all fixture cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$testsRoot = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $testsRoot '..' '..')).Path
$guard = Join-Path $repoRoot 'scripts/guard/flavor-isolation-guard.ps1'
$fixtures = Join-Path $testsRoot 'fixtures'

$cleanFixture = Join-Path $fixtures 'clean_sample.kt.txt'
$newFixture = Join-Path $fixtures 'new_violation.kt.txt'
$legacyFixture = Join-Path $fixtures 'legacy_only.kt.txt'

$script:pass = 0
$script:fail = 0

function Invoke-Guard {
    # Runs the guard in a child pwsh with -Json -DryRun (no artifact write) and
    # returns the parsed JSON object plus the child exit code.
    param(
        [Parameter(Mandatory)][string[]] $GuardArgs
    )
    $allArgs = @('-NoProfile', '-File', $guard) + $GuardArgs + @('-Json', '-DryRun')
    $stdout = & pwsh @allArgs
    $code = $LASTEXITCODE
    $obj = $null
    if ($stdout) { $obj = ($stdout | Out-String).Trim() | ConvertFrom-Json }
    return [pscustomobject]@{ json = $obj; exit = $code }
}

function Assert-Case {
    # Asserts blocking flag and exit code (and optional legacyCount>0) for one
    # case, printing PASS or FAIL with an expected|actual pair.
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][bool] $ExpectBlocking,
        [Parameter(Mandatory)][int] $ExpectExit,
        [Parameter(Mandatory)][psobject] $Result,
        [switch] $ExpectLegacyPositive
    )
    $okBlock = ($Result.json.blocking -eq $ExpectBlocking)
    $okExit = ($Result.exit -eq $ExpectExit)
    $okLegacy = $true
    if ($ExpectLegacyPositive) { $okLegacy = ($Result.json.legacyCount -gt 0) }

    if ($okBlock -and $okExit -and $okLegacy) {
        Write-Host ("PASS | {0} | blocking={1} exit={2} legacyCount={3}" -f `
            $Name, $Result.json.blocking, $Result.exit, $Result.json.legacyCount)
        $script:pass++
    } else {
        Write-Host ("FAIL | {0} | expected: blocking={1} exit={2}{3} | actual: blocking={4} exit={5} legacyCount={6}" -f `
            $Name, $ExpectBlocking, $ExpectExit, ($(if ($ExpectLegacyPositive) { ' legacyCount>0' } else { '' })), `
            $Result.json.blocking, $Result.exit, $Result.json.legacyCount)
        $script:fail++
    }
}

# --- Case 1: clean fixture => not blocking, exit 0 --------------------------
# Expected exit code literal: 0
$r1 = Invoke-Guard -GuardArgs @('-ScanFile', $cleanFixture)
Assert-Case -Name 'clean_sample.kt.txt' -ExpectBlocking $false -ExpectExit 0 -Result $r1

# --- Case 2: new violation (added flavor gate) => blocking, exit 1 ----------
# Expected exit code literal: 1
$r2 = Invoke-Guard -GuardArgs @('-ScanFile', $newFixture)
Assert-Case -Name 'new_violation.kt.txt' -ExpectBlocking $true -ExpectExit 1 -Result $r2

# --- Case 3: legacy token under explicit -ScanFile => touched => blocking ---
# Explicit scan asserts intent, so an explicit legacy token IS treated as
# touched. Expected exit code literal: 1
$r3 = Invoke-Guard -GuardArgs @('-ScanFile', $legacyFixture)
Assert-Case -Name 'legacy_only.kt.txt (explicit)' -ExpectBlocking $true -ExpectExit 1 -Result $r3

# --- Case 4: legacy token under -LegacyAudit full-scan seam => non-blocking -
# Full-scan classifies the match as legacy: blocking=false, legacyCount>0.
# Expected exit code literal: 0
$r4 = Invoke-Guard -GuardArgs @('-ScanFile', $legacyFixture, '-LegacyAudit')
Assert-Case -Name 'legacy_only.kt.txt (-LegacyAudit)' -ExpectBlocking $false -ExpectExit 0 -Result $r4 -ExpectLegacyPositive

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
