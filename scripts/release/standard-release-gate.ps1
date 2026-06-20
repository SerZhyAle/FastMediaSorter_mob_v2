<#
.SYNOPSIS
  S0553 - standard production release gate: single PASS/FAIL/WAIVED verdict.

.DESCRIPTION
  Folds four signals into one verdict using the tiered blocker policy (S0553 §3.3),
  modelled on scripts/devtest/prerelease-verdict.ps1:

    1. surface  : standard-surface-snapshot.ps1 -CheckRegressions
                  (regression candidate => hard-stop, §5.2).
    2. smoke    : standard-release-smoke.ps1 -Json
                  (R8/shrink breakage => hard-stop, §5.3; no-device/infra exit 2 =>
                   recorded coverage gap, waiver-eligible, NOT a FAIL).
    3. coverage : docs/release/standard-coverage-matrix.json parses; `not`/`partial`
                  groups and `coverageGaps` are waiver-eligible (§5.4).
    4. waiver   : store_assets/release_waivers/<VersionName>.md presence resolves
                  waiver-eligible gaps to WAIVED instead of FAIL.

  Verdict:
    - hard-stop signal present                        -> FAIL
    - waiver-eligible gap(s) present, waiver file set  -> WAIVED
    - waiver-eligible gap(s) present, no waiver file   -> FAIL (gaps unaddressed; ADR-2: no silent PASS)
    - nothing outstanding                             -> PASS

  Exit codes: 0 PASS · 1 FAIL · 2 infra abort (own inputs unreadable) · 3 WAIVED.

.PARAMETER VersionName
  Release versionName - locates store_assets/release_waivers/<VersionName>.md.

.PARAMETER SkipSmoke
  Skip the device smoke (treated as a recorded coverage gap, waiver-eligible).

.PARAMETER Json
  Emit a single JSON verdict object.

.EXAMPLE
  pwsh -NoProfile -File scripts/release/standard-release-gate.ps1 -VersionName 2.62.0501.151 -Json
#>
[CmdletBinding()]
param(
    [string]$VersionName,
    [switch]$SkipSmoke,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
trap { Write-Host "ERROR: $_" -ForegroundColor Red; exit 2 }

$repoRoot     = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$snapshotTool = Join-Path $repoRoot 'scripts/release/standard-surface-snapshot.ps1'
$smokeTool    = Join-Path $repoRoot 'scripts/release/standard-release-smoke.ps1'
$matrixFile   = Join-Path $repoRoot 'docs/release/standard-coverage-matrix.json'

foreach ($p in @($snapshotTool, $smokeTool, $matrixFile)) {
    if (-not (Test-Path $p)) { Write-Host "gate input missing: $p" -ForegroundColor Red; exit 2 }
}

# 1. surface regression check
& pwsh -NoProfile -File $snapshotTool -CheckRegressions *> $null
$surfaceExit = $LASTEXITCODE
if ($surfaceExit -eq 2) { Write-Host 'surface snapshot infra abort' -ForegroundColor Red; exit 2 }
$surfaceHardFail = ($surfaceExit -eq 1)

# 2. release smoke (R8/shrink)
$smokeStatus = 'skipped'
$smokeHardFail = $false
$smokeGap = $true   # no successful on-device proof => recorded coverage gap by default
if (-not $SkipSmoke) {
    & pwsh -NoProfile -File $smokeTool -Json *> $null
    switch ($LASTEXITCODE) {
        0 { $smokeStatus = 'clean'; $smokeGap = $false }
        1 { $smokeStatus = 'r8-breakage'; $smokeHardFail = $true; $smokeGap = $false }
        default { $smokeStatus = 'no-device/infra (coverage gap)'; $smokeGap = $true }
    }
}

# 3. coverage manifest
$matrix = Get-Content -Raw $matrixFile | ConvertFrom-Json
$notCovered = @($matrix.groups | Where-Object { $_.coverage -eq 'not' })
$partial    = @($matrix.groups | Where-Object { $_.coverage -eq 'partial' })
$gaps       = @($matrix.coverageGaps)

# 4. waiver file
$waiverFile = if ($VersionName) { Join-Path $repoRoot "store_assets/release_waivers/$VersionName.md" } else { $null }
$hasWaiver  = [bool]($waiverFile -and (Test-Path $waiverFile))

# ---- fold into verdict --------------------------------------------------
$hardStop = $surfaceHardFail -or $smokeHardFail
$waiverEligible = $smokeGap -or ($notCovered.Count -gt 0) -or ($partial.Count -gt 0) -or ($gaps.Count -gt 0)

if ($hardStop)                              { $verdict = 'FAIL';   $code = 1 }
elseif ($waiverEligible -and $hasWaiver)    { $verdict = 'WAIVED'; $code = 3 }
elseif ($waiverEligible -and -not $hasWaiver){ $verdict = 'FAIL';  $code = 1 }
else                                        { $verdict = 'PASS';   $code = 0 }

$breakdown = [ordered]@{
    surface  = [ordered]@{ exit = $surfaceExit; hardFail = $surfaceHardFail }
    smoke    = [ordered]@{ status = $smokeStatus; hardFail = $smokeHardFail; coverageGap = $smokeGap }
    coverage = [ordered]@{ notCovered = $notCovered.Count; partial = $partial.Count; gaps = $gaps.Count }
    waiver   = [ordered]@{ file = $(if ($waiverFile) { "store_assets/release_waivers/$VersionName.md" } else { '' }); present = $hasWaiver }
}

if ($Json) {
    ([ordered]@{ verdict = $verdict; breakdown = $breakdown } | ConvertTo-Json -Depth 6 -Compress)
} else {
    Write-Host "VERDICT $verdict" -ForegroundColor $(if ($verdict -eq 'PASS') { 'Green' } elseif ($verdict -eq 'WAIVED') { 'Yellow' } else { 'Red' })
    Write-Host ("  surface={0} smoke={1} partial={2} not={3} gaps={4} waiver={5}" -f `
        $surfaceExit, $smokeStatus, $partial.Count, $notCovered.Count, $gaps.Count, $hasWaiver)
    if (-not $hasWaiver -and $waiverEligible -and -not $hardStop) {
        Write-Host "  waiver-eligible gaps present - create store_assets/release_waivers/<versionName>.md to record acceptance" -ForegroundColor Yellow
    }
}
exit $code
