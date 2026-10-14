#requires -Version 7.0
<#
.SYNOPSIS
    Release-point check of both sweep verdicts - the phone one and the watch ones (S4249).

.DESCRIPTION
    The phone and the watch pre-release sweeps run side by side since S4249, each under its own
    freeze and fingerprint, and the release point reads both before the tag (strategic S4249 section
    5.2). This script is that read. A parallel branch that silently drops out of the verdict is the
    risk it exists for (strategic section 7), so every refusal below is a refusal, never a warning:

      - the file is missing or does not parse - exit 2;
      - pass is not true, or blocked is true - exit 1;
      - the file was written BEFORE the matching scope's fingerprint record (recordedAt in
        temp\RELEASE-FREEZE-FINGERPRINTS.json for the phone, temp\RELEASE-FREEZE-FINGERPRINTS-WEAR.json
        for the watch) - the verdict is about an older tree than the one the sweep froze, exit 1.
        A scope with no fingerprint record is "could not verify", exit 2.

    -NoWear skips the watch verdicts for a release that ships no watch build, and is refused without
    -NoWearReason: an unexplained absence is exactly the silent drop this check exists to catch.

    Exit codes:
      0 - every verdict exists, passed, is not blocked and is not older than its fingerprint
      1 - a verdict FAILED, is BLOCKED, or is stale (older than its scope's fingerprint record)
      2 - a verdict file or a fingerprint record is missing or unreadable, or the arguments are
          unusable (no watch verdict without -NoWear, -NoWear without a reason)

.PARAMETER PhoneVerdict
    The phone sweep's verdict JSON (prerelease-verdict.ps1 -Json output, step 4 of /spec-prerelease).

.PARAMETER WearVerdicts
    The watch sweep's verdict JSON files, one per reviewed shape (prerelease-wear-half.ps1 writes
    <OutRoot>\wear-prerelease-<shape>\verdict.json). A comma-separated string is accepted too.

.PARAMETER NoWear
    This release ships no watch build; skip the watch verdicts. Requires -NoWearReason.

.PARAMETER NoWearReason
    Why there is no watch verdict. Printed on the result line.

.PARAMETER RepoRoot
    Root the fingerprint records are read under (default: this repository). The contract suite
    points it at a fixture tree.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-sweep-verdicts.ps1 -PhoneVerdict temp/S0484/verdict_20261014.json -WearVerdicts temp/scratch/wear-prerelease-small-round/verdict.json,temp/scratch/wear-prerelease-large-round/verdict.json

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-sweep-verdicts.ps1 -PhoneVerdict temp/S0484/verdict_20261014.json -NoWear -NoWearReason 'phone-only hotfix'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PhoneVerdict,
    [string[]]$WearVerdicts = @(),
    [switch]$NoWear,
    [string]$NoWearReason,
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

$wearList = @($WearVerdicts | ForEach-Object { ([string]$_) -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })

if ($NoWear -and [string]::IsNullOrWhiteSpace($NoWearReason)) {
    Write-Host 'assert-sweep-verdicts: REFUSED - -NoWear needs -NoWearReason; an unexplained missing watch verdict is the silent drop this check exists to catch.' -ForegroundColor Red
    exit 2
}
if (-not $NoWear -and $wearList.Count -eq 0) {
    Write-Host 'assert-sweep-verdicts: REFUSED - no watch verdict given. Pass -WearVerdicts, or -NoWear -NoWearReason <why> for a release with no watch build.' -ForegroundColor Red
    exit 2
}

function Get-RecordedAt {
    param([string]$Scope)
    $name = if ($Scope -eq 'wear') { 'temp\RELEASE-FREEZE-FINGERPRINTS-WEAR.json' } else { 'temp\RELEASE-FREEZE-FINGERPRINTS.json' }
    $path = Join-Path $RepoRoot $name
    if (-not (Test-Path -LiteralPath $path)) { return [pscustomobject]@{ Ok = $false; Path = $path; At = $null } }
    try {
        $record = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
        $at = if ($record.recordedAt -is [datetime]) { $record.recordedAt } else { [datetime]::Parse([string]$record.recordedAt) }
        return [pscustomobject]@{ Ok = $true; Path = $path; At = $at }
    }
    catch {
        return [pscustomobject]@{ Ok = $false; Path = $path; At = $null }
    }
}

$checks = @([pscustomobject]@{ Scope = 'phone'; Path = $PhoneVerdict })
if (-not $NoWear) { $checks += @($wearList | ForEach-Object { [pscustomobject]@{ Scope = 'wear'; Path = $_ } }) }

$worst = 0
foreach ($check in $checks) {
    $path = $check.Path
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Host ("  {0,-5} MISSING   {1}" -f $check.Scope, $path) -ForegroundColor Red
        $worst = [Math]::Max($worst, 2)
        continue
    }
    $file = Get-Item -LiteralPath $path
    $verdict = $null
    try {
        $verdict = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        $verdict = $null
    }
    if ($null -eq $verdict -or $verdict.PSObject.Properties.Name -notcontains 'pass') {
        Write-Host ("  {0,-5} UNREADABLE {1}" -f $check.Scope, $path) -ForegroundColor Red
        $worst = [Math]::Max($worst, 2)
        continue
    }
    $fingerprint = Get-RecordedAt -Scope $check.Scope
    if (-not $fingerprint.Ok) {
        Write-Host ("  {0,-5} NO-FINGERPRINT {1} - the scope's record {2} is missing or unreadable" -f $check.Scope, $path, $fingerprint.Path) -ForegroundColor Red
        $worst = [Math]::Max($worst, 2)
        continue
    }
    $written = $file.LastWriteTime.ToString('s')
    $blocked = ($verdict.PSObject.Properties.Name -contains 'blocked') -and [bool]$verdict.blocked
    $result = if ($file.LastWriteTime -lt $fingerprint.At) { 'STALE' }
              elseif ($blocked) { 'BLOCKED' }
              elseif (-not [bool]$verdict.pass) { 'FAIL' }
              else { 'PASS' }
    $colour = if ($result -eq 'PASS') { 'Green' } else { 'Red' }
    $note = if ($result -eq 'STALE') { " - written before the fingerprint recorded at $($fingerprint.At.ToString('s'))" } else { '' }
    Write-Host ("  {0,-5} {1,-7} {2}  written {3}{4}" -f $check.Scope, $result, $path, $written, $note) -ForegroundColor $colour
    if ($result -ne 'PASS') { $worst = [Math]::Max($worst, 1) }
}
if ($NoWear) {
    Write-Host ("  wear  SKIPPED   -NoWear: {0}" -f $NoWearReason) -ForegroundColor Yellow
}

# 2 outranks 1: a verdict that could not be read says nothing, and "nothing" must not be ranked
# below a known failure that a fix could clear while the unread one stays unread.
switch ($worst) {
    0 { Write-Host 'assert-sweep-verdicts: PASS - every sweep verdict passed and is current.' -ForegroundColor Green }
    1 { Write-Host 'assert-sweep-verdicts: FAIL - a verdict failed, is blocked or is stale; the release does not proceed.' -ForegroundColor Red }
    default { Write-Host 'assert-sweep-verdicts: COULD NOT VERIFY - a verdict or a fingerprint record is missing or unreadable.' -ForegroundColor Red }
}
exit $worst
