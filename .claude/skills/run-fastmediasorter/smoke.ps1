<#
.SYNOPSIS
  run-fastmediasorter smoke driver - launch the installed standard-debug build on a
  connected emulator/device, screenshot it, and scan the launch window for crashes.

.DESCRIPTION
  The agent-facing handle for "is the app alive and rendering?". Composes the project's
  own device tooling (no new device logic):
    1. device-ready.ps1 pre-flight (adb + online device + package installed).
    2. adb.ps1 launch (explicit MainActivity - bypasses the LeakCanary launcher trap).
    3. adb.ps1 shot (screenshot to temp/scratch/).
    4. adb.ps1 log scan for FATAL / crash / ANR in the launch window.

  Assumes the build is already installed (run scripts/builders/build-standard-device.ps1
  first). Use -Build to install first.

  Exit codes: 0 = launched + no crash; non-zero = pre-flight or launch-window failure.

.PARAMETER DeviceId
  Specific adb device id (required when multiple devices are online).

.PARAMETER Build
  Build + install the standard-debug APK before launching.

.EXAMPLE
  pwsh -NoProfile -File .claude/skills/run-fastmediasorter/smoke.ps1
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$Pkg      = 'com.sza.fastmediasorter.debug'

function Invoke-Adb { param([string[]]$A)
    $args = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1") + $A
    if ($DeviceId) { $args += @('-DeviceId', $DeviceId) }
    & pwsh @args
}

if ($Build) {
    Write-Host '== build + install standard-debug =='
    if ($DeviceId) { $env:ANDROID_SERIAL = $DeviceId }
    & pwsh -NoProfile -File "$RepoRoot/scripts/builders/build-standard-device.ps1"
    if ($LASTEXITCODE -ne 0) { Write-Host "FAIL build/install exit $LASTEXITCODE"; exit 10 }
}

Write-Host '== pre-flight =='
$drArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/device-ready.ps1", '-Package', $Pkg)
if ($DeviceId) { $drArgs += @('-DeviceId', $DeviceId) }
& pwsh @drArgs
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL device-ready exit $LASTEXITCODE"; exit $LASTEXITCODE }

Write-Host '== launch =='
Invoke-Adb @('launch')
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL launch exit $LASTEXITCODE"; exit 11 }
Start-Sleep -Seconds 3

Write-Host '== screenshot =='
Invoke-Adb @('shot')

Write-Host '== launch-window crash scan =='
$log = Invoke-Adb @('log', '-Tail', '200', '-Grep', 'FATAL|beginning of crash|ANR in')
if ("$log" -match 'FATAL|beginning of crash|ANR in') {
    Write-Host 'FAIL crash/FATAL/ANR detected in launch window'; exit 12
}
Write-Host "SMOKE OK - $Pkg launched on $(if ($DeviceId) { $DeviceId } else { 'the online device' }), no crash. Screenshot under temp/scratch/."
exit 0
