#requires -Version 7.0
<#
.SYNOPSIS
    Contract set for gradle-worker-reaper.ps1 - the selection rule and the stop.

.DESCRIPTION
    S2585: the reaper's verdict rests entirely on matching one worker command line against one task
    tmpdir, and an error there is silent in both directions - too narrow leaves the orphan that jams
    every later build, too wide stops a process that belongs to someone else. Neither shows up as a
    red build, so the next incident would be the first report.

    Selection is exercised on synthetic process records through the -Processes seam; the stop is
    exercised on a real short-lived child, because that half is generic and worth proving for real.

.OUTPUTS
    Exit 0 - every case passed.
    Exit 1 - at least one case failed.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\..\gradle-worker-reaper.ps1"

$script:failed = 0

function Assert-That {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][bool]$Condition,
        [string]$Detail = ''
    )
    if ($Condition) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    else {
        Write-Host "  FAIL  $Name$(if ($Detail) { " - $Detail" })" -ForegroundColor Red
        $script:failed++
    }
}

function New-ProcessRecord {
    param([int]$Id, [string]$CommandLine)
    return [pscustomobject]@{ ProcessId = $Id; CommandLine = $CommandLine }
}

$root = 'P:\ANDROID\FastMediaSorter_mob_v2'
$task = 'testStandardDebugUnitTest'
$ourTmpDir = "$root\app_v2\build\tmp\$task\work"

Write-Host 'gradle-worker-reaper contract set' -ForegroundColor Cyan
Write-Host 'Selection' -ForegroundColor Cyan

$fixtures = @(
    New-ProcessRecord 1001 "java.exe -Dorg.gradle.internal.worker.tmpdir=$ourTmpDir -jar worker.jar"
    # Right task, wrong module - the module segment is inside the needle, so this must not match.
    New-ProcessRecord 1002 "java.exe -Dorg.gradle.internal.worker.tmpdir=$root\wear\build\tmp\$task\work -jar worker.jar"
    # Right module, wrong task.
    New-ProcessRecord 1003 "java.exe -Dorg.gradle.internal.worker.tmpdir=$root\app_v2\build\tmp\testNoLegalDebugUnitTest\work -jar worker.jar"
    # A gradle daemon: same tree, no worker tmpdir at all.
    New-ProcessRecord 1004 'java.exe -Xmx6g org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.4.1'
    New-ProcessRecord 1005 $null
)

$selected = Get-GradleWorkerIds -ProjectRoot $root -Module 'app_v2' -TaskDir $task -Processes $fixtures
Assert-That 'the worker of this module and this task is selected' ($selected -contains 1001)
Assert-That 'a worker of another module is not selected' (-not ($selected -contains 1002))
Assert-That 'a worker of another task is not selected' (-not ($selected -contains 1003))
Assert-That 'a gradle daemon is not selected' (-not ($selected -contains 1004))
Assert-That 'a null command line does not throw and is not selected' (-not ($selected -contains 1005))
Assert-That 'exactly one fixture matched' ($selected.Count -eq 1) "got $($selected.Count)"

# Windows paths are case-insensitive, and the wrapper composes this path from parameters whose case
# comes from a caller. A case-sensitive match here would silently reap nothing.
$mixedCase = @(New-ProcessRecord 2001 "java.exe -Dorg.gradle.internal.worker.tmpdir=$($ourTmpDir.ToUpperInvariant()) -jar worker.jar")
$selectedMixed = Get-GradleWorkerIds -ProjectRoot $root -Module 'app_v2' -TaskDir $task -Processes $mixedCase
Assert-That 'path case does not affect the match' ($selectedMixed.Count -eq 1)

$empty = Get-GradleWorkerIds -ProjectRoot $root -Module 'app_v2' -TaskDir $task -Processes @()
Assert-That 'no candidates returns an empty array, not null' ($null -ne $empty -and $empty.Count -eq 0)

# The caller reads .Count on the result. A single match returned as a scalar would make that read
# report the pid's digit count instead of one, which is the shape the leading comma in the helper
# exists to prevent.
Assert-That 'a single match is still an array' ($selected -is [array])

Write-Host 'Stop' -ForegroundColor Cyan

$child = Start-Process -FilePath 'pwsh' -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 120' -PassThru
try {
    Assert-That 'a live process is stopped and reported true' (Stop-GradleWorkerOrphan -Id $child.Id -Why 'contract set')
    $child.WaitForExit(5000) | Out-Null
    Assert-That 'the process is gone afterwards' ($null -eq (Get-Process -Id $child.Id -ErrorAction SilentlyContinue))
    Assert-That 'a pid that is already gone reports false and does not throw' (-not (Stop-GradleWorkerOrphan -Id $child.Id -Why 'contract set'))
}
finally {
    # The set must never leave behind the very thing the ticket is about.
    if (-not $child.HasExited) { Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue }
}

if ($script:failed -gt 0) {
    Write-Host "`ngradle-worker-reaper: $script:failed case(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host "`ngradle-worker-reaper: all cases passed." -ForegroundColor Green
exit 0
