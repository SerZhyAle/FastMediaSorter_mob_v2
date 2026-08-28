<#
.SYNOPSIS
    S1782: measure what a Robolectric upgrade costs this project's unit suite, without moving the
    main checkout off the version it runs today.

.DESCRIPTION
    The whole measurement happens in a throwaway git worktree under temp/S1782/worktree. The main
    checkout is never written to, because sibling agent sessions share this machine and its Gradle
    daemon (CLAUDE.md Rule 23) and an unproven Robolectric left in the tree would break every one of
    their `fu` runs while the owner is still deciding whether to adopt it.

    Order of operations:
      1. Create the worktree at the current HEAD and copy the gitignored local.properties into it,
         because Gradle cannot find the Android SDK without it.
      2. Rewrite the org.robolectric:robolectric coordinate in the worktree's build file.
      3. Drop templates/NativeGraphicsProbeTest.kt.txt in as a real test class. That probe is the
         go/no-go signal: under legacy graphics the canvas records draw calls instead of
         rasterising, so a text-only bitmap comes back byte-identical to a blank one.
      4. Take temp/BUILD.LOCK, run :app_v2:testStandardDebugUnitTest, release the lock.
      5. Parse the JUnit XML into passed / failed / NOT RUN, and write a dated report.

    The three-way split is the point of the script, not a nicety. This suite has twice been
    truncated by a worker dying on Robolectric's per-class sandbox (S1244, S1253) while Gradle still
    printed a normal-looking completion line, so a class that never ran is indistinguishable from a
    class that passed unless something looks for it by name. The expected set is read from the main
    checkout's sources; anything in it that the XML never mentions is reported as NOT RUN.

    A red suite is a successful measurement. Exit 0 means "the number exists", never "the upgrade
    is fine" - reading the report is the point.

.PARAMETER Version
    Robolectric version to measure. Default 4.16.1, the first release known to carry a Windows
    native-graphics binary (nativeruntime-dist-compat 1.0.18).

.PARAMETER KeepWorktree
    Leave the worktree in place for inspection instead of removing it. Remove it later with
    `git worktree remove --force temp/S1782/worktree` followed by `git worktree prune`.

.NOTES
    Exit codes:
      0 - the suite ran and the report was written. The suite itself may be red; that is the
          measurement, not a failure of this script.
      1 - the measurement could not complete: no worktree, no Gradle run, or no JUnit XML at all.
          Nothing about the upgrade's price is known in this case.
      2 - bad invocation: malformed -Version, missing probe template, or the worktree path is
          already occupied.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string] $Version = '4.16.1',
    [switch] $KeepWorktree
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$worktreePath = Join-Path $repoRoot 'temp\S1782\worktree'
$templatePath = Join-Path $PSScriptRoot 'templates\NativeGraphicsProbeTest.kt.txt'
$reportDir = Join-Path $repoRoot 'temp\S1782'
$probeClass = 'com.sza.fastmediasorter.ocrbench.NativeGraphicsProbeTest'
$gradleTask = ':app_v2:testStandardDebugUnitTest'
$instrumentedCache = Join-Path $HOME '.m2\repository\org\robolectric\android-all-instrumented'

# Build inputs that are gitignored and therefore absent from a fresh worktree, which carries tracked files
# only. Enumerated 2026-08-25 with `git ls-files --others --ignored --exclude-standard`, after discovering
# them one build failure at a time: sdk.dir, the two release-plan files GenerateReleasedTicketsTask declares
# as required inputs, and the prebuilt FFmpeg AAR every flavor links against. The libs entries are globs
# because that directory is where a future prebuilt would land too.
$seedGlobs = @(
    'local.properties',
    'PLAN\RELEASE_QUEUE.md',
    'PLAN\RELEASE_READY.md',
    'app_v2\libs\*.aar',
    'wear\libs\*.aar'
)

function Get-DirectorySizeMb([string] $Path) {
    if (-not (Test-Path $Path)) { return 0 }
    $bytes = (Get-ChildItem -LiteralPath $Path -Recurse -File -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum).Sum
    return [math]::Round(($bytes / 1MB), 1)
}

# The expected set comes from the MAIN checkout, not from the run: a class the run never mentions
# is exactly what has to be visible, and a set derived from the run's own output cannot show it.
function Get-ExpectedRobolectricClasses([string] $Root) {
    $testRoot = Join-Path $Root 'app_v2\src\test'
    return Get-ChildItem -LiteralPath $testRoot -Recurse -Filter '*.kt' |
        Where-Object { (Get-Content -LiteralPath $_.FullName -Raw) -match 'RobolectricTestRunner|@Config\(' } |
        ForEach-Object { $_.BaseName } |
        Sort-Object -Unique
}

function Read-JUnitResults([string] $ResultsDir) {
    $results = @{}
    if (-not (Test-Path $ResultsDir)) { return $results }
    foreach ($file in Get-ChildItem -LiteralPath $ResultsDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        [xml] $xml = Get-Content -LiteralPath $file.FullName -Raw
        $suite = $xml.testsuite
        if (-not $suite) { continue }
        $simpleName = ($suite.name -split '\.')[-1]
        $failureText = ''
        foreach ($case in @($suite.testcase)) {
            $problem = $case.failure ?? $case.error
            if ($problem -and -not $failureText) {
                $raw = if ($problem.message) { $problem.message } else { "$($problem.'#text')" }
                $failureText = (($raw -split "`n") | Where-Object { $_.Trim() } | Select-Object -First 1).Trim()
            }
        }
        $results[$simpleName] = [pscustomobject]@{
            FullName = $suite.name
            Tests    = [int] $suite.tests
            Failures = [int] $suite.failures + [int] $suite.errors
            FirstProblem = $failureText
        }
    }
    return $results
}

if ($Version -notmatch '^\d+\.\d+(\.\d+)?$') {
    Write-Error "Invalid -Version '$Version' (expected a form like 4.16.1)." -ErrorAction Continue
    exit 2
}
if (-not (Test-Path $templatePath)) {
    Write-Error "Probe template not found at $templatePath - step 01.1 of S1782 creates it." -ErrorAction Continue
    exit 2
}
if (Test-Path $worktreePath) {
    Write-Error "Worktree path already occupied: $worktreePath. Remove it with 'git worktree remove --force' first." -ErrorAction Continue
    exit 2
}

$baselineCacheMb = Get-DirectorySizeMb $instrumentedCache
$expected = Get-ExpectedRobolectricClasses $repoRoot

if ($WhatIfPreference) {
    Write-Host "measure-robolectric-upgrade: plan only, nothing created."
    Write-Host "  version under test : $Version"
    Write-Host "  worktree           : $worktreePath"
    Write-Host "  gradle task        : $gradleTask"
    Write-Host "  probe class        : $probeClass"
    Write-Host "  expected classes   : $($expected.Count) carrying a Robolectric runner or @Config"
    Write-Host "  instrumented cache : $baselineCacheMb MB before the run"
    Write-Host "  report             : $reportDir\robolectric-upgrade-price-<date>.md"
    exit 0
}

New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$gradleLog = Join-Path $reportDir "gradle-$Version-$(Get-Date -Format 'yyyy-MM-dd-HHmmss').log"

Write-Host "measure-robolectric-upgrade: creating worktree at $worktreePath"
& git -C $repoRoot worktree add --detach $worktreePath HEAD 2>&1 | Write-Host
if ($LASTEXITCODE -ne 0) {
    Write-Error "git worktree add failed with exit $LASTEXITCODE - nothing was measured." -ErrorAction Continue
    exit 1
}

$suiteRan = $false
try {
    # A fresh worktree holds tracked files only, and several build inputs are gitignored: local.properties
    # carries sdk.dir, and GenerateReleasedTicketsTask declares the two release-plan files as required
    # inputs. Without them Gradle fails during configuration, long before a single test runs - measured
    # 2026-08-25, the first run of this script died in 34s with nothing about Robolectric exercised.
    foreach ($glob in $seedGlobs) {
        foreach ($source in @(Get-ChildItem -Path (Join-Path $repoRoot $glob) -File -ErrorAction SilentlyContinue)) {
            $relative = $source.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
            $target = Join-Path $worktreePath $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
            Copy-Item $source.FullName $target -Force
            Write-Host "  seeded: $relative"
        }
    }

    $buildFile = Join-Path $worktreePath 'app_v2\build.gradle.kts'
    $buildText = Get-Content -LiteralPath $buildFile -Raw
    $bumped = $buildText -replace 'org\.robolectric:robolectric:[\d.]+', "org.robolectric:robolectric:$Version"
    if ($bumped -eq $buildText) {
        Write-Error "No org.robolectric:robolectric coordinate found in $buildFile - nothing to measure." -ErrorAction Continue
        exit 1
    }
    Set-Content -LiteralPath $buildFile -Value $bumped -NoNewline

    $probeDir = Join-Path $worktreePath 'app_v2\src\test\java\com\sza\fastmediasorter\ocrbench'
    New-Item -ItemType Directory -Path $probeDir -Force | Out-Null
    Copy-Item $templatePath (Join-Path $probeDir 'NativeGraphicsProbeTest.kt') -Force

    . (Join-Path $repoRoot 'scripts\utils\agent-lock.ps1')
    Enter-BuildLockOrExit -Reason "measure-robolectric-upgrade.ps1 ($Version)" -Domain Build.Phone
    try {
        Write-Host "measure-robolectric-upgrade: running $gradleTask on robolectric $Version .."
        Push-Location $worktreePath
        try {
            & .\gradlew.bat $gradleTask --no-daemon --continue 2>&1 | Tee-Object -FilePath $gradleLog | Write-Host
            $gradleExit = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        $suiteRan = $true
    } finally {
        Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
    }

    $resultsDir = Join-Path $worktreePath 'app_v2\build\test-results\testStandardDebugUnitTest'
    $observed = Read-JUnitResults $resultsDir
    if ($observed.Count -eq 0) {
        # Name the cause when Gradle died on a build input the worktree does not carry, rather than leaving
        # the operator to re-read a 60-line stack. Every such file belongs in $seedFiles above.
        # Print Gradle's own reason rather than matching one error shape. The first two failures here were a
        # missing task input and a missing prebuilt AAR - different messages, one cause: a build input the
        # worktree does not carry. Anything named below that lives in the main checkout belongs in $seedGlobs.
        if (Test-Path $gradleLog) {
            $logLines = Get-Content -LiteralPath $gradleLog
            for ($i = 0; $i -lt $logLines.Count; $i++) {
                if ($logLines[$i] -notmatch '^\* What went wrong:') { continue }
                Write-Host "  --- gradle said ---"
                for ($j = $i + 1; $j -lt $logLines.Count -and $logLines[$j] -notmatch '^\* Try:'; $j++) {
                    if ($logLines[$j].Trim()) { Write-Host "  $($logLines[$j])" }
                }
            }
        }
        Write-Error "Gradle exited $gradleExit and produced no JUnit XML under $resultsDir - the upgrade's price is unknown. Full log: $gradleLog" -ErrorAction Continue
        exit 1
    }

    $probeSimple = ($probeClass -split '\.')[-1]
    $probeResult = $observed[$probeSimple]
    $probeVerdict = if (-not $probeResult) { 'NOT RUN - the probe class never reported' }
        elseif ($probeResult.Failures -eq 0) { 'PASS - native graphics rasterises on this host' }
        else { "FAIL - $($probeResult.FirstProblem)" }

    $failed = $observed.GetEnumerator() |
        Where-Object { $_.Value.Failures -gt 0 } | Sort-Object Key
    $notRun = $expected | Where-Object { -not $observed.ContainsKey($_) } | Sort-Object
    $addedCacheMb = [math]::Round((Get-DirectorySizeMb $instrumentedCache) - $baselineCacheMb, 1)
    $stamp = Get-Date -Format 'yyyy-MM-dd'

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# S1782 - price of the Robolectric $Version upgrade")
    $lines.Add("")
    $lines.Add("Measured $stamp in a throwaway worktree. The main checkout was not modified.")
    $lines.Add("")
    $lines.Add("- Gradle exit: $gradleExit")
    $lines.Add("- Native-graphics probe: $probeVerdict")
    $lines.Add("- Classes expected (Robolectric runner or @Config in the main checkout): $($expected.Count)")
    $lines.Add("- Classes that reported: $($observed.Count)")
    $lines.Add("- Classes with failures: $(@($failed).Count)")
    $lines.Add("- Classes that NEVER RAN: $(@($notRun).Count)")
    $lines.Add("- Disk added under android-all-instrumented: $addedCacheMb MB")
    $lines.Add("")
    $lines.Add("## Failed")
    $lines.Add("")
    if (@($failed).Count -eq 0) { $lines.Add("None.") }
    else { foreach ($entry in $failed) { $lines.Add("- ``$($entry.Value.FullName)`` - $($entry.Value.FirstProblem)") } }
    $lines.Add("")
    $lines.Add("## Never ran")
    $lines.Add("")
    $lines.Add("A class here is NOT a passing class. It is a class this run cannot say anything about -")
    $lines.Add("usually a worker that died mid-suite, which Gradle still reports as a normal completion.")
    $lines.Add("")
    if (@($notRun).Count -eq 0) { $lines.Add("None - every expected class reported.") }
    else { foreach ($name in $notRun) { $lines.Add("- ``$name``") } }

    $reportPath = Join-Path $reportDir "robolectric-upgrade-price-$stamp.md"
    Set-Content -LiteralPath $reportPath -Value ($lines -join "`n") -Encoding utf8
    Write-Host ""
    Write-Host "measure-robolectric-upgrade: report written to $reportPath"
    Write-Host "  probe: $probeVerdict"
    Write-Host "  failed: $(@($failed).Count) | never ran: $(@($notRun).Count) | disk added: $addedCacheMb MB"
} finally {
    if (-not $KeepWorktree) {
        & git -C $repoRoot worktree remove --force $worktreePath 2>&1 | Write-Host
        # Gradle's intermediates nest deep enough to exceed Windows' path limit, and git then deregisters the
        # worktree but leaves ~1 GB of files behind (measured 2026-08-25: "Filename too long"). robocopy
        # addresses paths natively and is the only reliable emptier here; its exit codes 0-7 are all success.
        if (Test-Path $worktreePath) {
            $emptyDir = Join-Path $env:TEMP "ocrbench-empty-$PID"
            New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
            robocopy $emptyDir $worktreePath /MIR /NFL /NDL /NJH /NJS /NC /NS | Out-Null
            Remove-Item -LiteralPath $worktreePath -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $emptyDir -Recurse -Force -ErrorAction SilentlyContinue
            if (Test-Path $worktreePath) { Write-Host "  worktree could not be removed: $worktreePath" }
        }
        & git -C $repoRoot worktree prune 2>&1 | Write-Host
    } else {
        Write-Host "measure-robolectric-upgrade: worktree kept at $worktreePath (-KeepWorktree)"
    }
}

if (-not $suiteRan) {
    Write-Error "The suite never ran - no measurement was taken." -ErrorAction Continue
    exit 1
}
exit 0
