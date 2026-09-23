#requires -Version 7.0
<#
.SYNOPSIS
    S3254: focus-traversal parity between a portrait layout and its landscape counterpart.

.DESCRIPTION
    A portrait layout that declares any android:nextFocus* attribute promises a D-pad traversal
    order (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md 3.2 and 5.3). When a layout-land/ counterpart of
    that layout exists, it must carry the same promise: rotation re-inflates from the counterpart,
    and a chain that exists in portrait but not rotated strands the focus the moment the user turns
    the phone - a defect invisible to a finger, which is exactly why it survives review.

    Preference order from the strategic spec (S3254 section 5.1): NO counterpart is preferred to a
    stale one. A portrait layout whose landscape counterpart does not exist is therefore an INFO
    line, never a failure - this gate fires only when the counterpart exists but dropped the
    nextFocus* declarations. The four S3254 dialog pairs were brought into parity in Phase 01
    before this gate shipped, so it entered green by construction.

.OUTPUTS
    Exit 0 - every existing layout-land counterpart carries nextFocus* (INFO lines allowed).
    Exit 1 - at least one counterpart exists without any nextFocus* declaration.
    Exit 2 - cannot verify: the portrait layout directory does not exist.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-focus-parity.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$portraitDir = Join-Path $repoRoot 'app_v2/src/main/res/layout'
$landDir = Join-Path $repoRoot 'app_v2/src/main/res/layout-land'
$nextFocusPattern = 'android:nextFocus(?:Down|Up|Left|Right|Forward)='

if (-not (Test-Path $portraitDir)) {
    Write-Host 'assert-focus-parity: CANNOT VERIFY - app_v2/src/main/res/layout does not exist.'
    exit 2
}

$fails = [System.Collections.Generic.List[string]]::new()
$infos = [System.Collections.Generic.List[string]]::new()

foreach ($file in (Get-ChildItem $portraitDir -Filter *.xml | Sort-Object Name)) {
    $portraitText = Get-Content $file.FullName -Raw
    if ($portraitText -notmatch $nextFocusPattern) { continue }

    $counterpart = Join-Path $landDir $file.Name
    if (-not (Test-Path $counterpart)) {
        $infos.Add("$($file.Name) declares nextFocus* with no layout-land counterpart (allowed: prefer no counterpart to a stale one)")
        continue
    }
    if ((Get-Content $counterpart -Raw) -notmatch $nextFocusPattern) {
        $fails.Add("$($file.Name) declares nextFocus* in layout/ but its layout-land/ counterpart carries no nextFocus* declaration")
    }
}

if ($fails.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-focus-parity: PASS (every existing layout-land counterpart carries the portrait focus chain).'
        foreach ($i in $infos) { Write-Host "  INFO: $i" -ForegroundColor DarkGray }
    }
    exit 0
}

Write-Host 'assert-focus-parity: FAIL - landscape counterparts dropped the focus chain:'
foreach ($f in $fails) { Write-Host "  FAIL: $f" }
Write-Host '  Fix: carry the portrait nextFocus* declarations into the landscape counterpart (PHONE_UI_COMPONENT_PATTERNS.md 5.3); if the landscape copy no longer warrants the chain, delete the counterpart instead of leaving it stale.'
exit 1
