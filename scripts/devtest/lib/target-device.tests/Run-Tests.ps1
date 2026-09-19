#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/devtest/lib/target-device.ps1 (S3169).

.DESCRIPTION
    The resolver's whole job is to refuse where `adb` would guess, so the cases that matter are the
    ambiguous ones - and they are unreachable on a workstation with one device attached. The suite
    therefore drives a fake `adb` script whose `devices` table is read from an environment variable,
    which makes the two-device state of the incident reproducible with no hardware at all.

    E6 is the case the incident is named after: a phone and a watch online, a phone build, and no
    -DeviceId. Before S3169 that combination produced `more than one device/emulator` on every step
    and a success line anyway.

    The fake lives under this ticket's own scratch directory (CLAUDE.md Rule 10) and is removed by
    the suite.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$lib = Join-Path $PSScriptRoot '..\target-device.ps1'
if (-not (Test-Path -LiteralPath $lib)) {
    Write-Error "declaration not found: $lib" -ErrorAction Continue
    exit 1
}
. $lib

# Four levels up: target-device.tests -> lib -> devtest -> scripts -> repo root.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$fixture = Join-Path $repoRoot 'temp/S3169/adb-fixture'
$fakeAdb = Join-Path $fixture 'fake-adb.ps1'
$failures = 0

if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
New-Item -ItemType Directory -Path $fixture -Force | Out-Null

# No param block: every argument, `-s` included, lands in $args untouched.
Set-Content -LiteralPath $fakeAdb -Encoding utf8 -Value @'
$table = $env:FMS_FAKE_ADB_DEVICES
if ($args[0] -eq 'devices') {
    Write-Output 'List of devices attached'
    foreach ($row in ($table -split ';' | Where-Object { $_ })) {
        $cells = $row -split ','
        Write-Output ("{0}`t{1}" -f $cells[0], $cells[1])
    }
    exit 0
}
if ($args[0] -eq '-s' -and $args[2] -eq 'shell') {
    $serial = $args[1]
    foreach ($row in ($table -split ';' | Where-Object { $_ })) {
        $cells = $row -split ','
        # Characteristics are a comma-separated list of their own ("emulator,nosdcard,watch"), so
        # everything past the state cell is rejoined rather than read as one element.
        if ($cells[0] -eq $serial) { Write-Output (($cells[2..($cells.Count - 1)]) -join ','); exit 0 }
    }
    exit 1
}
exit 0
'@

function Set-Devices {
    param([string] $Table)
    $env:FMS_FAKE_ADB_DEVICES = $Table
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "ok    $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

Assert-Case 'E1 enumeration keeps only rows in state device' {
    Set-Devices 'RFCR110NBQJ,device,emulator;BADONE,unauthorized,emulator'
    $devices = @(Get-OnlineAdbDevices -Adb $fakeAdb)
    if ($devices.Count -ne 1 -or $devices[0] -ne 'RFCR110NBQJ') {
        "expected only RFCR110NBQJ, got: $($devices -join ', ')"
    }
}

Assert-Case 'E2 one online device resolves without being named' {
    Set-Devices 'RFCR110NBQJ,device,emulator'
    $serial = Resolve-TargetDevice -Adb $fakeAdb
    if ($serial -ne 'RFCR110NBQJ') { "expected RFCR110NBQJ, got '$serial'" }
}

Assert-Case 'E3 no online device throws' {
    Set-Devices ''
    try {
        $null = Resolve-TargetDevice -Adb $fakeAdb
        'expected a throw, got a value'
    }
    catch { if ($_.Exception.Message -notmatch 'No online device') { "wrong message: $($_.Exception.Message)" } }
}

Assert-Case 'E4 an explicit online DeviceId resolves' {
    Set-Devices 'RFCR110NBQJ,device,emulator;192.168.1.166:46551,device,watch'
    $serial = Resolve-TargetDevice -Adb $fakeAdb -DeviceId '192.168.1.166:46551'
    if ($serial -ne '192.168.1.166:46551') { "expected the named watch, got '$serial'" }
}

Assert-Case 'E5 an explicit offline DeviceId throws and lists what is online' {
    Set-Devices 'RFCR110NBQJ,device,emulator'
    try {
        $null = Resolve-TargetDevice -Adb $fakeAdb -DeviceId 'GONE123'
        'expected a throw, got a value'
    }
    catch {
        if ($_.Exception.Message -notmatch 'RFCR110NBQJ') { "message does not name the online device: $($_.Exception.Message)" }
    }
}

Assert-Case 'E6 a phone beside a watch resolves the phone for a phone build' {
    Set-Devices 'RFCR110NBQJ,device,emulator;192.168.1.166:46551,device,emulator,nosdcard,watch'
    $serial = Resolve-TargetDevice -Adb $fakeAdb -Module 'app_v2'
    if ($serial -ne 'RFCR110NBQJ') { "expected the phone, got '$serial'" }
}

Assert-Case 'E7 two phones refuse and the message names both' {
    Set-Devices 'RFCR110NBQJ,device,emulator;emulator-5554,device,emulator'
    try {
        $null = Resolve-TargetDevice -Adb $fakeAdb
        'expected a throw, got a value'
    }
    catch {
        $message = $_.Exception.Message
        if ($message -notmatch 'RFCR110NBQJ' -or $message -notmatch 'emulator-5554') { "message does not name both: $message" }
    }
}

$env:FMS_FAKE_ADB_DEVICES = $null
if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }

if ($failures -gt 0) {
    Write-Host "target-device: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'target-device: all cases passed.' -ForegroundColor Green
exit 0
