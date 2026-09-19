#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/devtest/lib/logcat-snapshot.ps1 (S3297).

.DESCRIPTION
    The property this suite exists to hold is a negative one: the snapshot must terminate on its
    own and leave no process behind. That is the exact property the leaking Start-Process block it
    replaces appeared to have when read, so it is asserted mechanically here - C5 fails the suite
    the moment a background handle reappears in the library.

    A fake adb stands in for the device: it records the argument list it was called with and emits
    fixture lines, which makes the dump shape and the non-zero-exit path reproducible with no
    hardware at all.

    The fake lives under this ticket's own scratch directory (CLAUDE.md Rule 10) and is removed by
    the suite.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$lib = Join-Path $PSScriptRoot '..\logcat-snapshot.ps1'
if (-not (Test-Path -LiteralPath $lib)) {
    Write-Error "declaration not found: $lib" -ErrorAction Continue
    exit 1
}
. $lib

# Four levels up: logcat-snapshot.tests -> lib -> devtest -> scripts -> repo root.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$fixture = Join-Path $repoRoot 'temp/S3297/adb-fixture'
$fakeAdb = Join-Path $fixture 'fake-adb.ps1'
$argsFile = Join-Path $fixture 'last-args.txt'
$failures = 0

if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
New-Item -ItemType Directory -Path $fixture -Force | Out-Null

# No param block: every argument, `-s` included, lands in $args untouched.
Set-Content -LiteralPath $fakeAdb -Encoding utf8 -Value @'
Set-Content -LiteralPath $env:FMS_FAKE_ADB_ARGS -Value ($args -join ' ') -Encoding utf8
$code = [int]$env:FMS_FAKE_ADB_EXIT
if ($code -ne 0) { exit $code }
Write-Output '09-19 00:41:02.111  1234  1234 I ActivityTaskManager: START u0 {cmp=com.sza.fastmediasorter.debug/.ui.main.MainActivity}'
Write-Output '09-19 00:41:02.777  4321  4321 D FastMediaSorter: cold start complete'
exit 0
'@

$env:FMS_FAKE_ADB_ARGS = $argsFile

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

Assert-Case 'C1 a snapshot writes a non-empty file and reports its line count' {
    $env:FMS_FAKE_ADB_EXIT = '0'
    $out = Join-Path $fixture 'logcat_standard_c1.log'
    $snap = Save-DeviceLogcatSnapshot -Adb $fakeAdb -Serial 'RFCR110NBQJ' -Path $out -SettleSeconds 0
    if (-not $snap.Ok) { return "expected Ok, got ExitCode $($snap.ExitCode)" }
    if (-not (Test-Path -LiteralPath $out)) { return "no file at $out" }
    if ($snap.Lines -ne 2) { return "expected 2 lines, got $($snap.Lines)" }
    if ((Get-Item -LiteralPath $out).Length -le 0) { 'file is empty' }
}

Assert-Case 'C2 the call is a bounded dump, not a stream' {
    $env:FMS_FAKE_ADB_EXIT = '0'
    $out = Join-Path $fixture 'logcat_standard_c2.log'
    $null = Save-DeviceLogcatSnapshot -Adb $fakeAdb -Serial 'RFCR110NBQJ' -Path $out -Lines 1500 -SettleSeconds 0
    $seen = Get-Content -LiteralPath $argsFile -Raw
    if ($seen -notmatch '(^|\s)-d(\s|$)') { return "argument list carries no -d: $seen" }
    if ($seen -notmatch '(^|\s)-t\s+1500(\s|$)') { return "argument list carries no -t 1500: $seen" }
    if ($seen -notmatch '(^|\s)-s\s+RFCR110NBQJ(\s|$)') { "argument list does not target the serial: $seen" }
}

Assert-Case 'C3 a missing parent directory is created' {
    $env:FMS_FAKE_ADB_EXIT = '0'
    $out = Join-Path $fixture 'nested/deeper/logcat_standard_c3.log'
    $snap = Save-DeviceLogcatSnapshot -Adb $fakeAdb -Serial 'RFCR110NBQJ' -Path $out -SettleSeconds 0
    if (-not $snap.Ok) { return "expected Ok, got ExitCode $($snap.ExitCode)" }
    if (-not (Test-Path -LiteralPath $out)) { "no file at $out" }
}

Assert-Case 'C4 a refusing device returns a failure instead of throwing' {
    $env:FMS_FAKE_ADB_EXIT = '7'
    $out = Join-Path $fixture 'logcat_standard_c4.log'
    $snap = Save-DeviceLogcatSnapshot -Adb $fakeAdb -Serial 'RFCR110NBQJ' -Path $out -SettleSeconds 0
    if ($snap.Ok) { return 'expected Ok to be false on a non-zero adb exit' }
    if ($snap.ExitCode -ne 7) { return "expected ExitCode 7, got $($snap.ExitCode)" }
    if (-not $snap.Message) { 'a failure with no message tells the caller nothing' }
}

Assert-Case 'C5 the library starts no background process' {
    $source = Get-Content -LiteralPath $lib -Raw
    # Comments are stripped before the search because the library's own header explains the
    # Start-Process block it replaces, and a check that cannot tell a warning from a call would
    # fail on the very documentation that records the defect.
    $code = [regex]::Replace($source, '(?s)<#.*?#>', '')
    $code = [regex]::Replace($code, '(?m)#.*$', '')
    if ($code -match 'Start-Process') { return 'the library calls Start-Process' }
    $env:FMS_FAKE_ADB_EXIT = '0'
    $out = Join-Path $fixture 'logcat_standard_c5.log'
    $snap = Save-DeviceLogcatSnapshot -Adb $fakeAdb -Serial 'RFCR110NBQJ' -Path $out -SettleSeconds 0
    if ($null -ne $snap.PSObject.Properties['Id']) { 'the return value carries a process handle' }
}

$env:FMS_FAKE_ADB_ARGS = $null
$env:FMS_FAKE_ADB_EXIT = $null
if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }

if ($failures -gt 0) {
    Write-Host "logcat-snapshot: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'logcat-snapshot: all cases passed.' -ForegroundColor Green
exit 0
