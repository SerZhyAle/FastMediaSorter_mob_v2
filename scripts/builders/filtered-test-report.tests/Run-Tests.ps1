# Run-Tests.ps1 (S2588) - regression suite for scripts/builders/filtered-test-report.ps1.
#
# The defect being guarded: the filtered unit-test report directory is shared by every session on the
# machine, so the path printed under a green "Fast check passed" could already hold a sibling's file
# by the time it was read - and the reader took those counts for their own.
#
# The case that matters most here is the negative one. A harvest that copies everything it finds
# would pass a naive suite and still reproduce the incident exactly, so the foreign-file case below
# plants a stale report from another class - the shape actually measured on 2026-09-05 - and asserts
# it is neither harvested nor copied. A suite that only ever asserted "my file was found" would stay
# green through the whole defect.
#
# Hermetic: no Gradle is invoked and no real build directory is touched. Ownership is a question
# about write times, so the sandbox sets them directly.
#
# Usage:  pwsh -NoProfile -File scripts/builders/filtered-test-report.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/filtered-test-report.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) "s2588-$([guid]::NewGuid().ToString('n'))"
$null = New-Item -ItemType Directory -Path $sandbox -Force

$module = 'app_v2'
$taskDir = 'testStandardDebugUnitTest'

function New-Report {
    <# Plants one report and pins its write time, which is the only property ownership rests on. #>
    param([string]$Dir, [string]$Class, [datetime]$WrittenAt)
    if (-not (Test-Path -LiteralPath $Dir)) { $null = New-Item -ItemType Directory -Path $Dir -Force }
    $path = Join-Path $Dir "TEST-$Class.xml"
    Set-Content -LiteralPath $path -Value "<testsuite name=`"$Class`" tests=`"1`" failures=`"0`"/>" -Encoding UTF8
    (Get-Item -LiteralPath $path).LastWriteTime = $WrittenAt
    return $path
}

try {
    $resultsRoot = Join-Path $sandbox "$module\build\test-results"
    $filteredDir = Join-Path $resultsRoot "$taskDir-filtered"
    $plainDir = Join-Path $resultsRoot $taskDir

    $mark = Get-Date

    Write-Host "`nCase 1 - candidate directories" -ForegroundColor Cyan
    $candidates = @(Get-FilteredTestReportCandidateDir -ProjectRoot $sandbox -Module $module -TaskDir $taskDir)
    Assert-That 'both the filtered and the plain directory are candidates' `
        ($candidates.Count -eq 2 -and $candidates[0] -like '*-filtered' -and $candidates[1] -notlike '*-filtered') `
        "got: $($candidates -join ' | ')"

    Write-Host "`nCase 2 - a run that wrote nothing" -ForegroundColor Cyan
    $none = Save-FilteredTestReport -ProjectRoot $sandbox -Module $module -TaskDir $taskDir `
        -Since $mark -RunId 'case2'
    Assert-That 'absent directories report NoneWritten, not a crash' `
        ($none.Outcome -eq 'NoneWritten' -and $none.Files -eq 0) "got: $($none.Outcome)/$($none.Files)"
    Assert-That 'the message says the counts on disk are not this run''s' `
        ($none.Message -match 'earlier run') "got: $($none.Message)"

    Write-Host "`nCase 3 - the S2588 incident: a sibling's stale report is present" -ForegroundColor Cyan
    $foreign = New-Report -Dir $filteredDir -Class 'com.sza.fastmediasorter.ui.settings.search.SettingsManifestExportTest' `
        -WrittenAt $mark.AddMinutes(-1)
    $stale = Save-FilteredTestReport -ProjectRoot $sandbox -Module $module -TaskDir $taskDir `
        -Since $mark -RunId 'case3'
    Assert-That 'a report written before this run is not claimed as its own' `
        ($stale.Outcome -eq 'NoneWritten' -and $stale.Files -eq 0) "got: $($stale.Outcome)/$($stale.Files)"
    Assert-That 'nothing was copied for a run that produced nothing' `
        (-not (Test-Path -LiteralPath (Join-Path $sandbox 'temp\TEST-REPORTS\case3'))) 'a directory was created'

    Write-Host "`nCase 4 - this run's own report, beside the foreign one" -ForegroundColor Cyan
    $null = New-Report -Dir $filteredDir -Class 'com.sza.fastmediasorter.ui.player.views.TranslationOverlayViewTest' `
        -WrittenAt $mark.AddSeconds(30)
    $mine = Save-FilteredTestReport -ProjectRoot $sandbox -Module $module -TaskDir $taskDir `
        -Since $mark -RunId 'case4'
    Assert-That 'exactly this run''s report is harvested' `
        ($mine.Outcome -eq 'Harvested' -and $mine.Files -eq 1) "got: $($mine.Outcome)/$($mine.Files)"
    $copied = @(Get-ChildItem -LiteralPath $mine.Path -Filter 'TEST-*.xml' -File)
    Assert-That 'the copy holds the requested class' `
        ($copied.Count -eq 1 -and $copied[0].Name -like '*TranslationOverlayViewTest.xml') `
        "got: $($copied.Name -join ', ')"
    Assert-That 'the sibling''s class is absent from the copy' `
        (-not ($copied.Name -like '*SettingsManifestExportTest*')) 'the foreign report was copied'

    Write-Host "`nCase 5 - the copy survives the source being overwritten" -ForegroundColor Cyan
    # The whole point of copying: after the harvest the shared directory may become anything at all.
    Remove-Item -LiteralPath $filteredDir -Recurse -Force
    $survivors = @(Get-ChildItem -LiteralPath $mine.Path -Filter 'TEST-*.xml' -File)
    Assert-That 'the harvested report outlives the shared directory' `
        ($survivors.Count -eq 1) "got: $($survivors.Count) file(s)"

    Write-Host "`nCase 6 - the module whose filtered run reports into the plain directory (S1946)" -ForegroundColor Cyan
    $null = New-Report -Dir $plainDir -Class 'com.sza.fastmediasorter.WearBridgeTest' -WrittenAt $mark.AddSeconds(45)
    $plain = Save-FilteredTestReport -ProjectRoot $sandbox -Module $module -TaskDir $taskDir `
        -Since $mark -RunId 'case6'
    Assert-That 'the plain directory is harvested when the filtered one does not exist' `
        ($plain.Outcome -eq 'Harvested' -and $plain.Files -eq 1) "got: $($plain.Outcome)/$($plain.Files)"

    Write-Host "`nCase 7 - old harvests are pruned" -ForegroundColor Cyan
    # temp/TEST-REPORTS is invisible to archive-temp.ps1's age rule (its root entry is refreshed by
    # every harvest), so without this the directory grows for the life of the checkout.
    $harvestRoot = Join-Path $sandbox 'temp\TEST-REPORTS'
    $old = Join-Path $harvestRoot 'app_v2-StandardDebug-19700101_000000-1'
    $null = New-Item -ItemType Directory -Path $old -Force
    (Get-Item -LiteralPath $old).LastWriteTime = (Get-Date).AddDays(-30)
    $fresh = Join-Path $harvestRoot 'app_v2-StandardDebug-20260905_000000-2'
    $null = New-Item -ItemType Directory -Path $fresh -Force
    $null = New-Report -Dir $plainDir -Class 'com.sza.fastmediasorter.PruneTest' -WrittenAt $mark.AddSeconds(50)
    $null = Save-FilteredTestReport -ProjectRoot $sandbox -Module $module -TaskDir $taskDir `
        -Since $mark -RunId 'case7' -RetentionDays 7
    Assert-That 'a harvest past the retention window is removed' `
        (-not (Test-Path -LiteralPath $old)) 'the stale harvest survived'
    Assert-That 'a harvest inside the window is kept' `
        (Test-Path -LiteralPath $fresh) 'a fresh harvest was pruned'

    Write-Host "`nCase 8 - a copy that cannot be made" -ForegroundColor Cyan
    # An illegal path character forces the failure; the verdict must survive it, because a filesystem
    # refusal has observed nothing about the code under test.
    $broken = Save-FilteredTestReport -ProjectRoot $sandbox -Module $module -TaskDir $taskDir `
        -Since $mark -RunId "case8|illegal"
    Assert-That 'a failed copy reports CopyFailed instead of throwing' `
        ($broken.Outcome -eq 'CopyFailed') "got: $($broken.Outcome)"
    Assert-That 'the failure names how many reports it could not copy' `
        ($broken.Message -match 'could not copy') "got: $($broken.Message)"
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "`n$script:pass passed, $script:fail failed." -ForegroundColor $(if ($script:fail) { 'Red' } else { 'Green' })
if ($script:fail) { exit 1 }
exit 0
