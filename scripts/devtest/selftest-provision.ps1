#requires -Version 7.0
<#
.SYNOPSIS
  S3741 - bring one device to the device-self-test state: fixture folder and media, animations off,
  network credentials file. Idempotent; identical on an emulator and on the test phone.

.DESCRIPTION
  Only the state that SURVIVES the reinstall the connected-test task performs is set here - files in
  shared storage, global system settings and a file under /data/local/tmp. App-private state and
  runtime grants are wiped by that reinstall, so the test process sets them itself
  (SelfTestBaselineRule in app_v2/src/androidTest).

  Stages:
    fleet        - the serial must be a free-hand device per docs/DEVICE_FLEET.md (an emulator or the
                   section titled as the test phone); anything else is refused before adb is touched.
    fixture-dir  - create /storage/emulated/0/TestMedia.
    fixture-media- push the androidTest fixture media (s0116_fixtures/*.ts, *.webm) and request a scan.
    animations   - window, transition and animator duration scales set to 0.
    network      - push the credentials file named by FMS_SELFTEST_NETWORK_FILE (default
                   $HOME/.fms/selftest-network.properties) to /data/local/tmp; remove a stale copy when
                   the workstation has none. Values are never printed.

  Exit codes:
    0 - every stage passed
    1 - a stage failed on the device (named in the output)
    2 - could not run: adb missing, the serial is not online, or the serial is not a free-hand device

.PARAMETER DeviceId
  adb serial of the target device. Required - the self-test never picks a device on its own.

.PARAMETER Json
  Emit one JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/selftest-provision.ps1 -DeviceId RFCR110NBQJ -Json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$DeviceId,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/find-adb.ps1')

$DeviceFixtureDir = '/storage/emulated/0/TestMedia'
$DeviceNetworkFile = '/data/local/tmp/fms-selftest-network.properties'
$FixtureSource = Join-Path $RepoRoot 'app_v2/src/androidTest/assets/s0116_fixtures'

$result = [ordered]@{ device = $DeviceId; exitCode = 0; stages = @() }

function Add-Stage([string]$Name, [string]$Status, [string]$Detail) {
    $script:result.stages += [ordered]@{ name = $Name; status = $Status; detail = $Detail }
}

function Complete-Run([int]$Code) {
    $script:result.exitCode = $Code
    if ($Json) {
        $script:result | ConvertTo-Json -Depth 5 -Compress
    }
    else {
        foreach ($s in $script:result.stages) { Write-Host ("{0,-14} {1,-5} {2}" -f $s.name, $s.status, $s.detail) }
        Write-Host "selftest-provision: exit $Code"
    }
    exit $Code
}

function Test-FreeHandDevice([string]$Serial) {
    if ($Serial -like 'emulator-*') { return $true }
    $fleet = Join-Path $RepoRoot 'docs/DEVICE_FLEET.md'
    if (-not (Test-Path $fleet)) { return $false }
    $heading = ''
    foreach ($line in Get-Content -LiteralPath $fleet) {
        if ($line -match '^###\s+(.+)$') { $heading = $Matches[1] }
        elseif ($line.Contains($Serial)) { return ($heading -match 'test phone') }
    }
    return $false
}

$adb = Find-Adb
if (-not $adb) {
    Add-Stage 'fleet' 'FAIL' 'adb not found'
    Complete-Run 2
}
if (-not (Test-FreeHandDevice $DeviceId)) {
    Add-Stage 'fleet' 'FAIL' "$DeviceId is not a free-hand device in docs/DEVICE_FLEET.md (emulator or the test phone)"
    Complete-Run 2
}
$state = "$(& $adb -s $DeviceId get-state 2>$null)".Trim()
if ($state -ne 'device') {
    Add-Stage 'fleet' 'FAIL' "$DeviceId is not online (state '$state')"
    Complete-Run 2
}
Add-Stage 'fleet' 'OK' "$DeviceId online, free-hand"

function Invoke-DeviceShell([string]$Command) {
    $out = & $adb -s $DeviceId shell $Command 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = "$out".Trim() }
}

$mk = Invoke-DeviceShell "mkdir -p $DeviceFixtureDir"
if ($mk.Code -ne 0) {
    Add-Stage 'fixture-dir' 'FAIL' $mk.Text
    Complete-Run 1
}
Add-Stage 'fixture-dir' 'OK' $DeviceFixtureDir

$fixtures = @(Get-ChildItem -LiteralPath $FixtureSource -File | Where-Object { $_.Extension -in '.ts', '.webm' })
if ($fixtures.Count -eq 0) {
    Add-Stage 'fixture-media' 'FAIL' "no fixture media under $FixtureSource"
    Complete-Run 1
}
foreach ($f in $fixtures) {
    $null = & $adb -s $DeviceId push $f.FullName "$DeviceFixtureDir/$($f.Name)" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Add-Stage 'fixture-media' 'FAIL' "push $($f.Name) exit $LASTEXITCODE"
        Complete-Run 1
    }
}
$null = Invoke-DeviceShell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$DeviceFixtureDir"
Add-Stage 'fixture-media' 'OK' "$($fixtures.Count) file(s) pushed"

foreach ($scale in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
    $set = Invoke-DeviceShell "settings put global $scale 0"
    if ($set.Code -ne 0) {
        Add-Stage 'animations' 'FAIL' "$scale : $($set.Text)"
        Complete-Run 1
    }
}
$readBack = (Invoke-DeviceShell 'settings get global animator_duration_scale').Text
if ($readBack -notin '0', '0.0') {
    Add-Stage 'animations' 'FAIL' "animator_duration_scale reads back '$readBack'"
    Complete-Run 1
}
Add-Stage 'animations' 'OK' 'three scales at 0'

$networkFile = if ($env:FMS_SELFTEST_NETWORK_FILE) { $env:FMS_SELFTEST_NETWORK_FILE } else { Join-Path $HOME '.fms/selftest-network.properties' }
if (Test-Path -LiteralPath $networkFile -PathType Leaf) {
    $null = & $adb -s $DeviceId push $networkFile $DeviceNetworkFile 2>&1
    if ($LASTEXITCODE -ne 0) {
        Add-Stage 'network' 'FAIL' "push of the credentials file exit $LASTEXITCODE"
        Complete-Run 1
    }
    $null = Invoke-DeviceShell "chmod 600 $DeviceNetworkFile"
    Add-Stage 'network' 'OK' 'credentials file pushed'
}
else {
    $null = Invoke-DeviceShell "rm -f $DeviceNetworkFile"
    Add-Stage 'network' 'SKIP' 'no workstation credentials file - network tests will skip as not provisioned'
}

Complete-Run 0
