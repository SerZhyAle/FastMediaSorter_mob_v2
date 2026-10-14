#requires -Version 7.0
<#
.SYNOPSIS
    S4249 contract suite for the verdict's Maestro policy: advisory flows and flaky retries.

# Subject: scripts/devtest/prerelease-verdict.ps1

.DESCRIPTION
    Hermetic: runs scripts/devtest/prerelease-verdict.ps1 on fixture Maestro JSON and a fixture log
    written to a scratch folder under the system temp directory. No adb call, no device.

    The four cases pin the owner decision of 2026-10-14 (strategic S4249 section 3.2): a flow marked
    advisory that fails without an application crash in the log does not fail the verdict and is
    listed; the same failure with a crash of the application in the log fails it through the log
    dimension; a flow that passed only on its retry passes and is listed as flaky; a failure of an
    unmarked flow fails the verdict as before.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/devtest/prerelease-verdict.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$verdictScript = Join-Path $repoRoot 'scripts/devtest/prerelease-verdict.ps1'
if (-not (Test-Path -LiteralPath $verdictScript)) {
    Write-Host "FAIL | verdict script not found: $verdictScript" -ForegroundColor Red
    exit 1
}

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ('s4249-verdict-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null

$cleanLog = @('10-14 12:00:00.000  1000  1000 I ActivityManager: no findings in this capture')
$crashLog = @(
    '10-14 12:00:00.000  4321  4321 E AndroidRuntime: FATAL EXCEPTION: main',
    '10-14 12:00:00.001  4321  4321 E AndroidRuntime: Process: com.sza.fastmediasorter.debug, PID: 4321',
    '10-14 12:00:00.002  4321  4321 E AndroidRuntime: java.lang.IllegalStateException: fixture'
)

function New-Flow {
    param([string]$Name, [string]$Status = 'pass', [bool]$Flaky = $false, [string]$Advisory)
    return [ordered]@{ flow = $Name; pass = ($Status -eq 'pass'); status = $Status; skipReason = $null; log = $null
                       attempts = $(if ($Flaky -or $Status -ne 'pass') { 2 } else { 1 })
                       firstAttemptStatus = $(if ($Flaky) { 'fail' } else { $Status }); firstAttemptLog = $null
                       flaky = $Flaky; advisory = $(if ($Advisory) { $Advisory } else { $null }) }
}

function Invoke-Verdict {
    param([object[]]$Flows, [string[]]$LogLines, [string]$Tag)
    $suitePath = Join-Path $scratch "$Tag.json"
    $logPath = Join-Path $scratch "$Tag.log"
    [ordered]@{ pass = $true; total = $Flows.Count; failed = 0; skipped = 0; reason = $null; launcherMode = 'off'; flows = $Flows } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $suitePath -Encoding utf8NoBOM
    Set-Content -LiteralPath $logPath -Value $LogLines -Encoding utf8NoBOM
    $out = & pwsh -NoProfile -File $verdictScript -LogFile $logPath -MaestroResults $suitePath -Json 2>&1
    $code = $LASTEXITCODE
    $json = $null
    try { $json = ($out | Where-Object { $_ -match '^\s*\{' } | Select-Object -First 1) | ConvertFrom-Json } catch { $json = $null }
    $text = & pwsh -NoProfile -File $verdictScript -LogFile $logPath -MaestroResults $suitePath 2>&1 | Out-String
    return [pscustomobject]@{ Code = $code; Json = $json; Text = $text }
}

try {
    $advisoryFail = New-Flow -Name 'stream_playback.yaml' -Status 'fail' -Advisory 'network'

    # Case 1: advisory failure without a crash.
    $noCrash = Invoke-Verdict -Flows @((New-Flow -Name 'app_launch.yaml'), $advisoryFail) -LogLines $cleanLog -Tag 'advisory-no-crash'
    Assert-Equal -Label 'advisory failure without a crash exits 0' -Expected 0 -Actual $noCrash.Code
    Assert-Equal -Label 'advisory failure without a crash is listed under advisoryFailures' `
        -Expected 'stream_playback.yaml (network)' -Actual (@($noCrash.Json.breakdown.maestro.advisoryFailures) -join ',')
    Assert-Equal -Label 'advisory failure has its own console line' `
        -Expected $true -Actual ($noCrash.Text -match 'maestro advisory failure .*stream_playback\.yaml')

    # Case 2: the same advisory failure with an application crash in the log.
    $withCrash = Invoke-Verdict -Flows @((New-Flow -Name 'app_launch.yaml'), $advisoryFail) -LogLines $crashLog -Tag 'advisory-crash'
    Assert-Equal -Label 'advisory failure with an app crash in the log exits 1' -Expected 1 -Actual $withCrash.Code
    Assert-Equal -Label 'advisory failure with a crash fails through the log dimension' `
        -Expected 'False/True' -Actual ("{0}/{1}" -f $withCrash.Json.breakdown.log.pass, $withCrash.Json.breakdown.maestro.pass)

    # Case 3: a flaky pass.
    $flaky = Invoke-Verdict -Flows @((New-Flow -Name 'launcher_home_smoke.yaml' -Flaky $true)) -LogLines $cleanLog -Tag 'flaky'
    Assert-Equal -Label 'flaky pass exits 0' -Expected 0 -Actual $flaky.Code
    Assert-Equal -Label 'flaky pass lists the flow under flaky' `
        -Expected 'launcher_home_smoke.yaml' -Actual (@($flaky.Json.breakdown.maestro.flaky) -join ',')
    Assert-Equal -Label 'flaky pass has its own console line' `
        -Expected $true -Actual ($flaky.Text -match 'maestro flaky .*launcher_home_smoke\.yaml')

    # Case 4: a non-advisory failure.
    $hard = Invoke-Verdict -Flows @((New-Flow -Name 'settings.yaml' -Status 'fail')) -LogLines $cleanLog -Tag 'non-advisory'
    Assert-Equal -Label 'non-advisory failure exits 1' -Expected 1 -Actual $hard.Code
    Assert-Equal -Label 'non-advisory failure is a maestro failure, not advisory' `
        -Expected 'settings.yaml/' -Actual ("{0}/{1}" -f (@($hard.Json.breakdown.maestro.failures) -join ','), (@($hard.Json.breakdown.maestro.advisoryFailures) -join ','))
}
finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "prerelease-verdict.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
