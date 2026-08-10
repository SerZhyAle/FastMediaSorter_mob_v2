<#
.SYNOPSIS
    Fast per-flavor Gradle check - compile, resources, unit tests or assemble. Defaults to app_v2;
    -Module wear checks the Wear OS module, which has no product flavors.

.OUTPUTS
    Exit 0 - the check passed.
    Exit 1 - the check found a defect (compile error, red test, truncated suite).
    Exit 2 - the check could not run to a verdict (S1463: the unit-test worker JVM died, twice;
             or -Flavor was combined with -Module wear, which declares no flavors).
#>
param(
    [ValidateSet("Code", "Resources", "CodeAndResources", "Unit", "Assemble")]
    [string]$Mode = "CodeAndResources",
    # S0826: per-flavor fast compile check. Standard is the default; NoLegal needs its own
    # path because it bundles Python via Chaquopy (see flag handling below).
    # S0404: the capability-gated flavors (Lite / Photos / Legacy) compile the no-op source sets,
    # so a seam change needs a fast check on one of them too.
    # S0989: Vr compiles the src/vr source set (OpenXR immersive host) - needed when a change
    # lives only under src/vr, which the Standard/NoLegal checks never compile.
    [ValidateSet("Standard", "NoLegal", "Lite", "Photos", "Legacy", "Vr")]
    [string]$Flavor = "Standard",
    # S1496: wear is an active Gradle module with no fast check of its own, which is how its jsch
    # pin drifted nine minor versions behind app_v2 unnoticed. It declares no product flavors, so
    # -Flavor does not apply to it.
    [ValidateSet("app_v2", "wear")]
    [string]$Module = "app_v2",
    [string]$Tests,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\gradle-run-verdict.ps1"
Enter-BuildLockOrExit -Reason "check-standard-fast.ps1"
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot

if ($Module -eq 'wear' -and $Flavor -ne 'Standard') {
    Write-Error "check-standard-fast: -Flavor '$Flavor' is meaningless for the wear module - it declares no product flavors." -ErrorAction Continue
    exit 2
}

# The wear module declares no product flavors, so its task names carry no flavor segment.
$variant = if ($Module -eq 'wear') { '' } else { $Flavor }

function Get-GradleTaskList {
    switch ($Mode) {
        "Code" { return @(":${Module}:compile${variant}DebugKotlin") }
        "Resources" { return @(":${Module}:process${variant}DebugResources") }
        "CodeAndResources" { return @(":${Module}:compile${variant}DebugKotlin", ":${Module}:process${variant}DebugResources") }
        "Unit" { return @(":${Module}:test${variant}DebugUnitTest") }
        "Assemble" { return @(":${Module}:assemble${variant}Debug") }
        default { throw "Unsupported mode: $Mode" }
    }
}

$gradleArgs = New-Object System.Collections.Generic.List[string]
Get-GradleTaskList | ForEach-Object { $null = $gradleArgs.Add($_) }
# Flavors without Python: disable Chaquopy and use the configuration cache for speed.
# NoLegal bundles Python via Chaquopy, whose API must stay on the compile classpath and
# whose tasks are not configuration-cache serialisable - so keep it enabled and skip the cache.
if ($Flavor -ne "NoLegal") {
    $null = $gradleArgs.Add("-Pchaquopy.enabled=false")
    $null = $gradleArgs.Add("--configuration-cache")
}

if ($Tests) {
    if ($Mode -ne "Unit") {
        throw "-Tests is supported only with -Mode Unit"
    }
    # Gradle takes ONE pattern per --tests flag and does not split on commas: passing
    # "*ATest,*BTest" as a single argument makes it look for a class literally named that, and it
    # answers "No tests found for given includes" - which reads as "your tests are gone" rather
    # than "your filter was malformed" (S1449). Split here so a comma-separated list means what
    # every caller assumes it means.
    foreach ($pattern in ($Tests -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
        $null = $gradleArgs.Add("--tests")
        $null = $gradleArgs.Add($pattern)
    }
}

$checkLabel = if ($Module -eq 'wear') { 'wear' } else { $Flavor }
Write-Host "Fast $checkLabel check.." -ForegroundColor Cyan
Write-Host "Mode: $Mode" -ForegroundColor Yellow
if ($Tests) {
    Write-Host "Tests filter: $Tests" -ForegroundColor Yellow
}
Write-Host "Command: .\\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

$runOnce = {
    param([int]$Attempt)

    $collected = New-Object System.Collections.Generic.List[string]
    & "$projectRoot\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        $collected.Add($line)
        if ($Quiet -and ($line -match " UP-TO-DATE$" -or $line -match " NO-SOURCE$" -or $line -match " FROM-CACHE$")) {
            return
        }
        Write-Host $line
    }
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Lines = $collected.ToArray() }
}

# S1463: only a unit run can lose its worker, and only there is a repeat the right answer - repeating
# a failed compile would just double the wait for an error that is not going to change.
$run = if ($Mode -eq "Unit") {
    Invoke-GradleRunWithRetry -RunOnce $runOnce -MaxAttempts 2
} else {
    # Same shape on both branches on purpose: a caller that probes .WorkerDeath must not depend on
    # which branch produced the object (the strict-mode property probe that broke S1462).
    $single = & $runOnce 1
    [pscustomobject]@{ ExitCode = $single.ExitCode; Lines = $single.Lines; Attempts = 1; WorkerDeath = $false }
}

$gradleExit = $run.ExitCode

# S1244: a truncated unit run ALWAYS ends non-zero - the worker process dies. So the completeness
# check has to run BEFORE the exit-code bail-out below; placed after it, the one run it exists to
# catch would exit first and never reach it. Skipped for a --tests filter, where a handful of
# reports is the correct outcome rather than a truncation.
if ($Mode -eq "Unit" -and -not $Tests) {
    & "$PSScriptRoot\..\quality\assert-test-suite-complete.ps1" -TaskDir "test${variant}DebugUnitTest"
    $gateExit = $LASTEXITCODE
    if ($gateExit -eq 1 -and -not $run.WorkerDeath) {
        Write-Host "`nFast check failed - the unit run did not cover the whole suite." -ForegroundColor Red
        exit 1
    }
    # Exit 2 is "could not check" (no reports yet). Say so, but let a real Gradle failure below own
    # the verdict rather than masking it with a missing-reports message.
    if ($gateExit -eq 2) {
        Write-Host "Suite-completeness check could not run; coverage is unverified." -ForegroundColor Yellow
    }
}

# S1463: the worker JVM dying is not a test result. Reported as exit 1 it is indistinguishable from a
# red test, and the reader spends the next hour hunting a failure that never happened. Exit 2 is this
# repo's "could not verify" (S1338), and it is the honest answer: nothing was proven either way.
if ($run.WorkerDeath) {
    $msg = "check-standard-fast: the Gradle test worker died on both attempts - this run produced NO " +
    "verdict. Nothing was proven about the tests; do not read it as a failure. Usual cause is host " +
    "load (concurrent Gradle daemons or emulators). Re-run on a quiet host; see PLAN/S1463."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

if ($gradleExit -ne 0) {
    Write-Host "`nFast check failed." -ForegroundColor Red
    exit $gradleExit
}

Write-Host "`nFast check passed." -ForegroundColor Green

}
finally {
    Exit-AgentLock -Name Build
}
