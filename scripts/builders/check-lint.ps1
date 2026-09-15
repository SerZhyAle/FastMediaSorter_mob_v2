<#
.SYNOPSIS
    Runs Android lint for one module: :app_v2:lintStandardDebug or :wear:lintStandardDebug.

.DESCRIPTION
    S3155: lint ran nowhere on the workstation. No a.ps1 target invoked it, so fk / fkn / fc / fr /
    fg / fu all exited 0 without a single lint task, and CI was the only place the check executed.
    Hundreds of errors accumulated unseen that way - 479 in app_v2 and 116 in wear by the time the
    gate was read - because the only feedback loop was a 58-minute CI round trip.

    This is that loop brought local. It prints the module it checked in its banner, because a
    verdict quoted against the wrong module is this repository's most-repeated measurement error
    (CLAUDE.md section 9), and it names the XML report so the findings can be read as data.

    Rule 23: acquires the module's own build domain - Build.Phone for app_v2, Build.Wear for wear -
    and releases it on both the success and the failure path. The two modules are separate domains
    since S2109, so a phone lint and a watch lint may genuinely run at once.

.PARAMETER Module
    Which module to lint: app_v2 or wear.

.PARAMETER Regenerate
    Delete the module's lint-baseline.xml first, so the run rewrites it from the current tree.
    Only ever run this AFTER the real defects are fixed - a baseline regenerated first records
    them as accepted, which is the blanket regeneration S3155 exists to prevent.

.PARAMETER Quiet
    Suppress UP-TO-DATE / NO-SOURCE / FROM-CACHE noise lines.

.EXITCODES
    0 - lint ran and found no error above the baseline; or, under -Regenerate, the baseline was
        written (which gradle itself reports as a failure, on purpose - see the branch below).
    1 - the build domain is held by a live build, or lint failed (gradle's own exit code is
        propagated verbatim; gradle reports 1 when lint aborts on errors).
    2 - lint reported success but produced no XML report, so nothing was actually verified; or
        -Regenerate wrote no baseline. Distinguished from a real failure because the fix is
        different: a silently absent artifact is a harness defect, not a source defect.
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,

    [switch]$Regenerate,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$domain = if ($Module -eq 'wear') { 'Build.Wear' } else { 'Build.Phone' }

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "check-lint.ps1 -Module $Module" -Domain $domain
try {

    $projectRoot = Resolve-Path "$PSScriptRoot\..\.."
    Set-Location $projectRoot

    $baseline = Join-Path $projectRoot "$Module\lint-baseline.xml"
    if ($Regenerate) {
        if (Test-Path -LiteralPath $baseline) {
            Remove-Item -LiteralPath $baseline -Force
            Write-Host "Deleted $Module\lint-baseline.xml - this run rewrites it." -ForegroundColor Yellow
        }
        else {
            Write-Host "No $Module\lint-baseline.xml to delete - this run creates it." -ForegroundColor Yellow
        }
    }

    $gradleArgs = New-Object System.Collections.Generic.List[string]
    $null = $gradleArgs.Add(":${Module}:lintStandardDebug")
    if ($Regenerate) {
        # Overrides the repository default in gradle.properties, which makes a MISSING baseline mean
        # an EMPTY one so an ordinary run reports everything and writes nothing. Without this
        # override -Regenerate would delete the file and then never write it back.
        $null = $gradleArgs.Add("-Pandroid.experimental.lint.missingBaselineIsEmptyBaseline=false")
    }

    Write-Host "Android lint - module: $Module" -ForegroundColor Cyan
    Write-Host "Command: .\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

    & "$projectRoot\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        if ($Quiet -and ($line -match " UP-TO-DATE$" -or $line -match " NO-SOURCE$" -or $line -match " FROM-CACHE$")) {
            return
        }
        Write-Host $line
    }

    $gradleExit = $LASTEXITCODE
    $xmlReport = Join-Path $projectRoot "$Module\build\reports\lint-results.xml"

    # A successful regeneration FAILS the gradle build on purpose: lint writes the file and then
    # reports "Aborting build since new baseline file was created", so the developer cannot mistake a
    # freshly recorded finding for a passing check. The file existing is the verdict that matters
    # here, so only a regeneration that produced nothing is a failure.
    if ($Regenerate) {
        if (Test-Path -LiteralPath $baseline) {
            Write-Host "`nBaseline regenerated: $Module\lint-baseline.xml" -ForegroundColor Green
            Write-Host "Read it before committing - every row in it is a finding you are accepting." -ForegroundColor Yellow
            exit 0
        }
        Write-Error "Regeneration wrote no $Module\lint-baseline.xml (gradle exit $gradleExit)." -ErrorAction Continue
        exit 2
    }

    if ($gradleExit -ne 0) {
        Write-Host "`nLint FAILED for $Module (gradle exit $gradleExit)." -ForegroundColor Red
        if (Test-Path -LiteralPath $xmlReport) {
            Write-Host "Report: $Module\build\reports\lint-results.xml" -ForegroundColor Gray
        }
        exit $gradleExit
    }

    if (-not (Test-Path -LiteralPath $xmlReport)) {
        Write-Error "No lint XML report at $xmlReport - lint reported success without producing one." -ErrorAction Continue
        exit 2
    }

    Write-Host "`nLint passed for $Module." -ForegroundColor Green
    Write-Host "Report: $Module\build\reports\lint-results.xml" -ForegroundColor Gray

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @($domain)
}
