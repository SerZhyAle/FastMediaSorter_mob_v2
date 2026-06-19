<#
.SYNOPSIS
  Capture raw store screenshots into temp/play-shots/<id>.png for S0497 Phase 03.

.DESCRIPTION
  Slot ids are read from play/listing/captions.json. Screenshots are taken through
  scripts/devtest/adb.ps1 (screencap-then-pull keeps PNG bytes intact). Navigation
  inside the app is manual: drive the standard-debug app to the target screen (any UI
  tool), then run this with -Slot <id> to snap the current screen into the slot file.
  -Launch (re)starts MainActivity first; -List prints the known slot ids.

  Per CLAUDE.md, the live device id and the per-slot exit code are printed by the caller.

.EXAMPLE
  pwsh -NoProfile -File scripts/release/capture-play-screenshots.ps1 -Launch -Slot browse
.EXAMPLE
  pwsh -NoProfile -File scripts/release/capture-play-screenshots.ps1 -Slot video-player -DeviceId emulator-5556
#>
param(
    [string]$Slot,
    [string]$DeviceId,
    [switch]$Launch,
    [switch]$List
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb      = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$captions = Join-Path $repoRoot 'play/listing/captions.json'
$outDir   = Join-Path $repoRoot 'temp/play-shots'
$pkg      = 'com.sza.fastmediasorter.debug'
$activity = 'com.sza.fastmediasorter.ui.main.MainActivity'

$slotIds = (Get-Content $captions -Raw | ConvertFrom-Json).slots.id

if ($List) { $slotIds; exit 0 }

$devArgs = @(); if ($DeviceId) { $devArgs = @('-DeviceId', $DeviceId) }

if ($Launch) {
    & pwsh -NoProfile -File $adb @devArgs shell -Cmd "am start -n $pkg/$activity" | Out-Null
    Start-Sleep -Seconds 2  # let the launched activity settle before the first snap
}

if (-not $Slot) {
    throw "Specify -Slot <id> (one of: $($slotIds -join ', ')) or -List."
}
if ($slotIds -notcontains $Slot) {
    throw "Unknown slot '$Slot'. Known: $($slotIds -join ', ')."
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$shotOut = & pwsh -NoProfile -File $adb @devArgs shot 2>&1
$line = $shotOut | Where-Object { $_ -match '^SHOT ' } | Select-Object -First 1
if (-not $line) {
    Write-Host ($shotOut -join "`n")
    throw "adb.ps1 shot produced no screenshot."
}
$src  = ($line -replace '^SHOT ', '').Trim()
$dest = Join-Path $outDir "$Slot.png"
Move-Item -Force -Path $src -Destination $dest

Write-Host "CAPTURED $Slot -> $dest"
