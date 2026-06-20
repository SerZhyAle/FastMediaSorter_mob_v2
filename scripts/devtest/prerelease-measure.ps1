<#
.SYNOPSIS
  S0484 pre-release sweep - per-checkpoint performance measurement.

.DESCRIPTION
  Measures one named performance checkpoint and emits a verdict record comparing the
  measured value against the configured threshold (prerelease.config.psd1 Thresholds).
  Uses adb + dumpsys + log markers only - no app code (research/01).

  Checkpoints:
    cold-start       - `am start -W` TotalTime (ms) for MainActivity after force-stop.
    list-scroll      - `dumpsys gfxinfo` janky-frame %% (skill resets + scrolls first).
    player-open      - ms to first frame; skill supplies the wall-clock via -ElapsedMs.
    network-listing  - ms to BrowseLoadingManager COMPLETE; skill supplies -ElapsedMs.

  Exit codes:
    0  - measured and within threshold (pass)
    1  - bad arguments / config unreadable / adb missing
    11 - measured but over threshold (fail)

.PARAMETER DeviceId
  Specific adb device id.

.PARAMETER Checkpoint
  Checkpoint to measure: cold-start | list-scroll | player-open | network-listing.

.PARAMETER ElapsedMs
  Caller-supplied elapsed time (ms) for checkpoints the skill times around a UI action
  (player-open, network-listing).

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [Parameter(Mandatory)][ValidateSet('cold-start', 'list-scroll', 'player-open', 'network-listing')]
    [string]$Checkpoint,
    [int]$ElapsedMs = -1,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$RepoRoot     = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$DebugPackage = 'com.sza.fastmediasorter.debug'
$CodePackage  = 'com.sza.fastmediasorter'
$ConfigPath   = Join-Path $PSScriptRoot 'prerelease.config.psd1'

function Get-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $c = Join-Path $root 'platform-tools\adb.exe'; if (Test-Path $c) { return $c } }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $known) { return $known }
    return $null
}

# True for an Android emulator (qemu/ranchu/goldfish or an SDK image). On such targets the
# gfxinfo janky% scroll metric is structurally inflated by software/host-GPU rendering and is
# therefore reported but not release-gating (record marked advisory; the verdict aggregator skips
# advisory records). Physical devices return false and gate normally.
function Test-IsEmulator {
    param([string]$Adb, [string[]]$Target)
    $qemu  = "$(& $Adb @Target shell getprop ro.kernel.qemu 2>&1)".Trim()
    $bqemu = "$(& $Adb @Target shell getprop ro.boot.qemu 2>&1)".Trim()
    $hw    = "$(& $Adb @Target shell getprop ro.hardware 2>&1)".Trim()
    $model = "$(& $Adb @Target shell getprop ro.product.model 2>&1)".Trim()
    if ($qemu -eq '1' -or $bqemu -eq '1') { return $true }
    if ($hw -in @('ranchu', 'goldfish'))  { return $true }
    if ($model -match 'sdk|emulator|Android SDK') { return $true }
    return $false
}

$adb = Get-Adb
if (-not $adb) { if ($Json) { '{"error":"adb not found"}' } else { Write-Host 'adb not found' }; exit 1 }
$adbTarget = @(); if ($DeviceId) { $adbTarget = @('-s', $DeviceId) }

# Capture the raw measured value for the requested checkpoint.
$measured = $null
$detail   = ''
switch ($Checkpoint) {
    'cold-start' {
        & $adb @adbTarget shell am force-stop $DebugPackage *> $null
        $out = & $adb @adbTarget shell am start -W -n "$DebugPackage/$CodePackage.ui.main.MainActivity" 2>&1
        $m = [regex]::Match("$out", 'TotalTime:\s*(\d+)')
        if ($m.Success) { $measured = [int]$m.Groups[1].Value; $detail = "am start -W TotalTime" }
        else { $detail = "TotalTime not parsed" }
    }
    'list-scroll' {
        # gfxinfo reset + scroll are driven by the skill before this call; read the stats now.
        $gfx = & $adb @adbTarget shell dumpsys gfxinfo $DebugPackage 2>&1
        $m = [regex]::Match("$gfx", 'Janky frames:\s*\d+\s*\(([\d.]+)%\)')
        if ($m.Success) { $measured = [double]$m.Groups[1].Value; $detail = "gfxinfo janky %" }
        else { $detail = "janky frames not parsed" }
    }
    default {
        # player-open / network-listing: the skill times the UI action and passes -ElapsedMs.
        if ($ElapsedMs -ge 0) { $measured = $ElapsedMs; $detail = "caller-supplied elapsed ms" }
        else { $detail = "no -ElapsedMs supplied" }
    }
}

# ---------- threshold compare (step 03.3) ----------
if (-not (Test-Path $ConfigPath)) {
    if ($Json) { '{"error":"config not found"}' } else { Write-Host 'config not found' }; exit 1
}
$thresholds = (Import-PowerShellDataFile $ConfigPath).Thresholds
$keyMap = @{ 'cold-start' = 'ColdStart'; 'list-scroll' = 'ListScroll'; 'player-open' = 'PlayerOpen'; 'network-listing' = 'NetworkListing' }
$limit  = $thresholds[$keyMap[$Checkpoint]].Limit

# Unmeasured (null) fails closed; otherwise pass when measured value is within the limit.
$pass = ($null -ne $measured) -and ($limit -ne $null) -and ([double]$measured -le [double]$limit)

# Advisory: list-scroll gfxinfo janky% is not a valid release gate on an emulator. Keep the raw
# measured/pass for transparency but flag the record so the verdict aggregator does not gate on it.
$advisory = $false
if ($Checkpoint -eq 'list-scroll' -and (Test-IsEmulator -Adb $adb -Target $adbTarget)) {
    $advisory = $true
    $detail   = "$detail (advisory: emulator software render, not release-gating)"
}

$record = [ordered]@{
    checkpoint = $Checkpoint
    measured   = $measured
    limit      = $limit
    pass       = [bool]$pass
    advisory   = [bool]$advisory
    detail     = $detail
}
if ($Json) { $record | ConvertTo-Json -Compress }
else { Write-Host ("{0}: measured={1} limit={2} pass={3} advisory={4} ({5})" -f $Checkpoint, $measured, $limit, $pass, $advisory, $detail) }
# An advisory record never reports failure on a standalone call; the aggregator owns gating.
exit $(if ($pass -or $advisory) { 0 } else { 11 })
