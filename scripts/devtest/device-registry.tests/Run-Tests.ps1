#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for the device registry (S2855).

.DESCRIPTION
    Drives the script's own verbs, like device-lease.tests does. The registry's contract the
    tests guard: a Record creates the store and a schema-1 record; the previous mark moves into
    the capped history; read-only verbs never create the store directory; a malformed serial is
    refused; Forget refuses without -Yes; Refresh rewrites the mark from a stubbed `adb` and
    stamps it `recordedBy: refresh`.

    The Refresh case puts a stub `adb.cmd` on PATH with ANDROID_HOME/ANDROID_SDK_ROOT blanked -
    the same hermetic trick adb.tests uses - so no device and no real adb is needed. The registry
    invokes adb as `-s <serial> shell dumpsys package <pkg>` and `-s <serial> shell getprop
    ro.product.model`, so the stub keys on arguments three and four. Test serials live in the
    real store (there is no root override, mirroring the lease store) and are removed in the
    finally block.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every case passed.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:CLAUDE_CODE_SESSION_ID)) {
    $env:CLAUDE_CODE_SESSION_ID = 'test-suite-session'
}

# Three levels up: device-registry.tests -> devtest -> scripts -> repo root.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$registryScript = Join-Path $repoRoot 'scripts/devtest/device-registry.ps1'
$registryDir = Join-Path $repoRoot 'temp/DEVICE.REGISTRY'
$fixture = Join-Path $PSScriptRoot 'fixtures/dumpsys-package.txt'

$testSerial = 'test-registry-device'

function Invoke-Registry {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & pwsh -NoProfile -File $registryScript @Arguments 2>&1 | Out-Null
    return $LASTEXITCODE
}

function Remove-TestRecord {
    Remove-Item -LiteralPath (Join-Path $registryDir "$testSerial.json") -Force -ErrorAction SilentlyContinue
}

function Assert-True {
    param([Parameter(Mandatory)][bool]$Condition, [Parameter(Mandatory)][string]$Message)
    if (-not $Condition) { throw "device-registry tests: FAIL - $Message" }
}

try {
    Remove-TestRecord

    # 1. A read-only List on a missing store must not create the directory.
    if (-not (Test-Path -LiteralPath $registryDir)) {
        $code = Invoke-Registry -Arguments @('-Verb', 'List')
        Assert-True ($code -eq 0) "List on a missing store returned exit $code, expected 0."
        Assert-True (-not (Test-Path -LiteralPath $registryDir)) 'List created the store directory - read-only verbs must never write.'
    }

    # 2. Record creates the store and a schema-1 record; the mark carries the caller's fields.
    $code = Invoke-Registry -Arguments @('-Verb', 'Record', '-Id', $testSerial, '-Package', 'com.sza.fastmediasorter.debug',
        '-Module', 'app_v2', '-Flavor', 'standard', '-BuildType', 'debug', '-VersionName', '1.0.0-test',
        '-VersionCode', '100', '-Artifact', 'app-standard-debug.apk', '-Ticket', 'S2855', '-RecordedBy', 'tests')
    Assert-True ($code -eq 0) "Record returned exit $code, expected 0."
    Assert-True (Test-Path -LiteralPath $registryDir) 'Record did not create the store directory.'
    $record = Get-Content -LiteralPath (Join-Path $registryDir "$testSerial.json") -Raw | ConvertFrom-Json
    Assert-True ($record.schema -eq 1) "Record schema is '$($record.schema)', expected 1."
    Assert-True ($record.lastInstall.package -eq 'com.sza.fastmediasorter.debug') 'Record lost the package.'
    Assert-True ($record.lastInstall.byTicket -eq 'S2855') 'Record lost the ticket.'
    Assert-True ($null -ne $record.lastInstall.bySession -and $record.lastInstall.bySession -ne '') 'Record carries no session id.'

    # 3. A second Record moves the previous mark into history.
    $code = Invoke-Registry -Arguments @('-Verb', 'Record', '-Id', $testSerial, '-Package', 'com.sza.fastmediasorter',
        '-Module', 'app_v2', '-Flavor', 'noLegal', '-BuildType', 'release', '-VersionName', '2.0.0-test',
        '-VersionCode', '200', '-Artifact', 'app-nolegal-release.apk', '-RecordedBy', 'tests')
    Assert-True ($code -eq 0) "second Record returned exit $code, expected 0."
    $record = Get-Content -LiteralPath (Join-Path $registryDir "$testSerial.json") -Raw | ConvertFrom-Json
    Assert-True ($record.lastInstall.versionName -eq '2.0.0-test') 'second Record did not become the last mark.'
    Assert-True ($record.history.Count -eq 1) "history holds $($record.history.Count) entries, expected 1."
    Assert-True ($record.history[0].versionName -eq '1.0.0-test') 'history lost the previous mark.'

    # 4. The history cap holds.
    foreach ($n in 1..7) {
        $code = Invoke-Registry -Arguments @('-Verb', 'Record', '-Id', $testSerial, '-Package', 'com.sza.fastmediasorter.debug',
            '-VersionName', "3.$n.0", '-RecordedBy', 'tests')
        Assert-True ($code -eq 0) "cap-seed Record $n returned exit $code."
    }
    $record = Get-Content -LiteralPath (Join-Path $registryDir "$testSerial.json") -Raw | ConvertFrom-Json
    Assert-True ($record.history.Count -le 5) "history grew to $($record.history.Count) entries, cap is 5."

    # 5. A malformed serial is refused.
    $code = Invoke-Registry -Arguments @('-Verb', 'Record', '-Id', 'bad serial!', '-Package', 'x', '-RecordedBy', 'tests')
    Assert-True ($code -eq 1) "malformed serial returned exit $code, expected 1."

    # 6. Forget refuses without -Yes, removes with it.
    $code = Invoke-Registry -Arguments @('-Verb', 'Forget', '-Id', $testSerial)
    Assert-True ($code -eq 1) "Forget without -Yes returned exit $code, expected 1."
    Assert-True (Test-Path -LiteralPath (Join-Path $registryDir "$testSerial.json")) 'Forget without -Yes removed the record anyway.'
    $code = Invoke-Registry -Arguments @('-Verb', 'Forget', '-Id', $testSerial, '-Yes')
    Assert-True ($code -eq 0) "Forget -Yes returned exit $code, expected 0."
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $registryDir "$testSerial.json"))) 'Forget -Yes left the record on disk.'

    # 7. Refresh rewrites the mark from a stubbed adb and stamps it 'refresh'.
    $stubDir = Join-Path $env:TEMP "device-registry-tests-$PID"
    New-Item -ItemType Directory -Path $stubDir -Force | Out-Null
    # The registry's adb shape: -s <serial> shell dumpsys package <pkg> / -s <serial> shell getprop ...
    @"
@echo off
if "%3"=="shell" if "%4"=="dumpsys" type "$fixture"
if "%3"=="shell" if "%4"=="getprop" echo Test Model X
"@ | Set-Content -LiteralPath (Join-Path $stubDir 'adb.cmd') -Encoding Ascii
    $priorPath = $env:PATH
    $priorAndroidHome = $env:ANDROID_HOME
    $priorSdkRoot = $env:ANDROID_SDK_ROOT
    try {
        $env:ANDROID_HOME = ''
        $env:ANDROID_SDK_ROOT = ''
        $env:PATH = "$stubDir" + [System.IO.Path]::PathSeparator + $priorPath
        $code = Invoke-Registry -Arguments @('-Verb', 'Refresh', '-Id', $testSerial)
        Assert-True ($code -eq 0) "Refresh returned exit $code, expected 0."
        $record = Get-Content -LiteralPath (Join-Path $registryDir "$testSerial.json") -Raw | ConvertFrom-Json
        Assert-True ($record.lastInstall.recordedBy -eq 'refresh') "Refresh stamp is '$($record.lastInstall.recordedBy)', expected 'refresh'."
        Assert-True ($record.lastInstall.versionName -eq '9.9.9-stub') "Refresh parsed versionName '$($record.lastInstall.versionName)', expected '9.9.9-stub'."
        Assert-True ($record.lastInstall.package -eq 'com.sza.fastmediasorter.debug') "Refresh picked package '$($record.lastInstall.package)', expected the debug package."
        Assert-True ($record.model -eq 'Test Model X') "Refresh model is '$($record.model)', expected 'Test Model X'."
    }
    finally {
        $env:PATH = $priorPath
        $env:ANDROID_HOME = $priorAndroidHome
        $env:ANDROID_SDK_ROOT = $priorSdkRoot
        Remove-Item -LiteralPath $stubDir -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Output 'device-registry tests: PASS (record+history+cap, read-only never creates, serial grammar, Forget gate, stubbed refresh)'
    exit 0
}
catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
finally {
    Remove-TestRecord
}
