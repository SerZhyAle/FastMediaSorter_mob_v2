#requires -Version 7.0
<#
.SYNOPSIS
  S3741 - the device self-test: provision one device, run the whole instrumentation suite on it in two
  passes, and print one verdict. `.\a.ps1 fst -DeviceId <serial>`.

.DESCRIPTION
  1. scripts/devtest/selftest-provision.ps1 - fixture folder and media, animations off, credentials file.
  2. Default pass - check-standard-fast.ps1 -Mode ConnectedAndroidTest under FmsAndroidTestRunner, which
     runs every test except @HiltAndroidTest classes, on the real application.
  3. Hilt pass - the same with -ProjectProperty fms.hiltTestRunner=true: FmsHiltTestRunner swaps in
     HiltTestApplication and runs only @HiltAndroidTest classes. One instrumentation process holds one
     Application, so the two kinds cannot share a pass.
  4. scripts/devtest/selftest-verdict.ps1 over both passes' JUnit XML.

  Everything lands in temp/selftest/<timestamp>/: provision.json, default/ and hilt/ (XML, the gradle
  log and testrunner.log - the runner's logcat lines, where the skip reasons live), verdict.json. The connected task uninstalls the app and the test APK when it finishes, so the
  device ends bare - run this after every step that still needs the installed app.

  Long (two device runs); background it (CLAUDE.md section 6).

  Exit codes:
    0 - both passes ran and no test failed (skips are listed, not counted as passes)
    1 - a test failed, or provisioning failed on the device
    2 - could not verify: provisioning could not run, or a pass produced no JUnit XML

.PARAMETER DeviceId
  adb serial. Required: the self-test never picks a device on its own.

.PARAMETER SkipHiltPass
  Run the default pass only.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/run-device-selftest.ps1 -DeviceId RFCR110NBQJ
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$DeviceId,
    [switch]$SkipHiltPass
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $RepoRoot
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$runDir = Join-Path $RepoRoot "temp/selftest/$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$xmlSource = Join-Path $RepoRoot 'app_v2/build/outputs/androidTest-results/connected'

Write-Host "selftest: device $DeviceId, artifacts $runDir"

$provisionJson = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'selftest-provision.ps1') -DeviceId $DeviceId -Json
$provisionCode = $LASTEXITCODE
[System.IO.File]::WriteAllText((Join-Path $runDir 'provision.json'), "$provisionJson", [System.Text.UTF8Encoding]::new($false))
if ($provisionCode -ne 0) {
    Write-Host "selftest: provisioning exit $provisionCode - see provision.json"
    exit $provisionCode
}
Write-Host 'selftest: provisioning OK'

. (Join-Path $PSScriptRoot 'lib/find-adb.ps1')
$adb = Find-Adb

function Invoke-Pass([string]$Name, [string[]]$ExtraArgs) {
    $passDir = Join-Path $runDir $Name
    New-Item -ItemType Directory -Force -Path $passDir | Out-Null
    # The gradle daemon can still hold the previous pass's crash-report file open, so a failed delete
    # is not fatal: only XML written after this pass started is copied below.
    $passStart = Get-Date
    if (Test-Path -LiteralPath $xmlSource) {
        try { Remove-Item -LiteralPath $xmlSource -Recurse -Force -ErrorAction Stop }
        catch { Write-Warning "selftest: could not clear $xmlSource ($($_.Exception.Message)); older XML is filtered by time" }
    }
    # The connected XML writes a skip as a bare <skipped/>; the reason survives only in the runner's own
    # "assumption failed" log lines, which selftest-verdict.ps1 reads back from testrunner.log.
    $logcat = $null
    if ($adb) {
        & $adb -s $DeviceId logcat -c 2>$null
        $logcat = Start-Process -FilePath $adb -ArgumentList @('-s', $DeviceId, 'logcat', '-v', 'brief', 'TestRunner:I', '*:S') `
            -RedirectStandardOutput (Join-Path $passDir 'testrunner.log') -NoNewWindow -PassThru
    }
    $cfArgs = @('-NoProfile', '-File', (Join-Path $RepoRoot 'scripts/builders/check-standard-fast.ps1'),
        '-Mode', 'ConnectedAndroidTest', '-DeviceId', $DeviceId, '-BlockThrough') + $ExtraArgs
    & pwsh @cfArgs *> (Join-Path $passDir 'gradle.log')
    $code = $LASTEXITCODE
    if ($logcat -and -not $logcat.HasExited) { Stop-Process -Id $logcat.Id -Force }
    if (Test-Path -LiteralPath $xmlSource) {
        Get-ChildItem -LiteralPath $xmlSource -Recurse -File -Filter '*.xml' |
            Where-Object { $_.LastWriteTime -ge $passStart } |
            ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $passDir }
    }
    Write-Host "selftest: $Name pass - gradle exit $code"
    return $passDir
}

$dirs = @(Invoke-Pass 'default' @())
if (-not $SkipHiltPass) {
    $dirs += Invoke-Pass 'hilt' @('-ProjectProperty', 'fms.hiltTestRunner=true')
}

# Called in-process, not through `pwsh -File`: -File cannot carry an array, so the two pass
# directories arrived as one string plus a stray positional and the verdict never ran.
& (Join-Path $PSScriptRoot 'selftest-verdict.ps1') -ResultsDir $dirs -OutFile (Join-Path $runDir 'verdict.json')
$verdictCode = $LASTEXITCODE
if ($null -eq $verdictCode) { $verdictCode = 2 }
Write-Host "selftest: verdict exit $verdictCode - $(Join-Path $runDir 'verdict.json')"
exit $verdictCode
