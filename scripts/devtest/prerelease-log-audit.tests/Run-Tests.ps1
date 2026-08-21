#requires -Version 7.0
<#
.SYNOPSIS
    S1859 regression suite for the pre-release log audit's process attribution.

.DESCRIPTION
    Hermetic: drives scripts/devtest/prerelease-log-audit.ps1 against two recorded threadtime
    fixtures. No adb call, no device, no network, no writes outside this folder.

    The case that matters is the foreign-pid one. It replays the 2026-08-20 sweep finding -
    two E/A clusters emitted by com.google.android.googlequicksearchbox:interactor - and
    asserts they no longer reach the actionable list, so the suite goes red the moment anyone
    makes the tag denylists the deciding filter again.

    The second fixture drops the `Start proc` announcement and asserts the audit says
    `heuristic` and does NOT hide the foreign cluster. That is the documented weakness of the
    fallback, not a defect: a capture that never saw the app start cannot attribute a line, and
    an audit that silently claimed otherwise is what this ticket fixed.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$auditScript = Join-Path $repoRoot 'scripts/devtest/prerelease-log-audit.ps1'

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    $e = ($Expected | Out-String).Trim()
    $a = ($Actual   | Out-String).Trim()
    if ($e -eq $a) {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $e | actual: $a"
        $script:failed++
    }
}

function Invoke-Audit {
    param([string]$FixtureName)
    $fixture = Join-Path $PSScriptRoot "fixtures/$FixtureName"
    $stdout = & pwsh -NoProfile -File $auditScript -LogFile $fixture -Json
    return ($stdout | ConvertFrom-Json)
}

function Get-ActionableTags {
    param($Result)
    return @($Result.actionable | ForEach-Object { $_.tag })
}

# Case 1 - the capture announces the app process: pid decides.
$withStartProc = Invoke-Audit 'logcat_foreign_pid_sample.txt'
$tags = Get-ActionableTags $withStartProc

Assert-Equal 'pid' $withStartProc.attribution 'attribution is pid when Start proc is present'
Assert-Equal 1 $withStartProc.appPidCount 'exactly one app process id recovered'
Assert-Equal $true ($tags -contains 'ResourceScanUseCase') 'app-pid error stays actionable'
Assert-Equal $false ($tags -contains 'A') 'S1859: foreign-pid E/A cluster is not actionable'
Assert-Equal $false ($tags -contains 'GsaVoiceInteraction') 'foreign-pid cluster under an unlisted tag is not actionable'
Assert-Equal 1 $withStartProc.actionableCount 'the app line is the only actionable cluster'
Assert-Equal 1 $withStartProc.benignCount 'the app WifiRequiredException line is still classified benign'
Assert-Equal 1 $withStartProc.exitCode 'a real app error still exits 1'

# Case 2 - no announcement to attribute against: the audit falls back and says so.
$noStartProc = Invoke-Audit 'logcat_no_start_proc_sample.txt'
$fallbackTags = Get-ActionableTags $noStartProc

Assert-Equal 'heuristic' $noStartProc.attribution 'attribution is heuristic without Start proc'
Assert-Equal 0 $noStartProc.appPidCount 'no app process id recovered'
Assert-Equal $true ($fallbackTags -contains 'A') 'fallback admits the foreign cluster - the mode the report must disclose'

# Case 3 - the same decision in the other capture format. `-v time` puts the pid in parentheses
# after the tag instead of in its own column, so it needs its own case: the audit parses both
# formats and a regex that stopped capturing the pid there would fail silently, in heuristic
# mode, on exactly the sweeps that captured in the older format.
$timeFormat = Invoke-Audit 'logcat_time_format_sample.txt'
$timeTags = Get-ActionableTags $timeFormat

Assert-Equal 'pid' $timeFormat.attribution 'attribution is pid in -v time captures too'
Assert-Equal 1 $timeFormat.appPidCount '-v time: app process id recovered'
Assert-Equal $true ($timeTags -contains 'ResourceScanUseCase') '-v time: app-pid error stays actionable'
Assert-Equal $false ($timeTags -contains 'A') '-v time: foreign-pid E/A cluster is not actionable'

Write-Host ''
Write-Host ("passed: {0} | failed: {1}" -f $script:passed, $script:failed)
if ($script:failed -gt 0) { exit 1 }
exit 0
