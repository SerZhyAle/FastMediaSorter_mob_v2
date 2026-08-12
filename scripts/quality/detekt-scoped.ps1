#requires -Version 7.0
<#
.SYNOPSIS
    S1595: run the REAL detekt over just the changed files, outside gradle.

.DESCRIPTION
    The expensive gate (assert-detekt.ps1) analyses whole modules through gradle and costs ~87 s
    per run. detekt's CLI accepts an explicit --input list, so the same analyser, the same
    config and the same baseline can judge only the files a change actually touched - measured at
    1.3-2.5 s.

    This exists because the alternative was tried and failed. detekt-preflight.ps1 reimplements
    three rules lexically; measured over the transcript corpus it fires on 35.7% of attributable
    gate failures and fully covers 13.9%, and a lexical reproduction of the size rules is not
    possible at all - the classes detekt flags and the classes it does not overlap by a 240-line
    band under every line metric tried (S1595 research 01 and 04). Approximating the analyser does
    not converge; running it does.

    NOT a replacement for the gate. This runs without type resolution on a narrowed input, so the
    gate stays the project-wide verdict. What this buys is that a finding in YOUR file is named
    seconds after you make it, not after a gradle round-trip.

    Deliberately does NOT take temp/BUILD.LOCK: no gradle process is involved, and a cheap step
    that queues on the build lock inherits exactly the wait it exists to avoid.

    Report path: temp/detekt-scoped/<module>.xml, never <module>/build/reports/detekt/detekt.xml.
    assert-detekt.ps1 narrows a project-wide failure against the latter and judges its staleness by
    mtime, so a second writer there would make an unrelated gate blame the wrong ticket.

    The detekt version is read from build.gradle.kts at run time rather than hardcoded, so this
    tracks the build's own pin instead of drifting away from it.

.PARAMETER ChangedFiles
    Repo-relative or absolute paths. Pass ONE comma-joined argument: `pwsh -File` binds a
    [string[]] parameter to its first element only, which is why every consumer in this repo
    comma-splits (S1184).

.PARAMETER Json
    Emit the findings as JSON instead of human-readable lines.

.NOTES
    Exit codes:
      0 - the analyser ran and found nothing new in the named files (or there were none to check).
      1 - the analyser ran and found at least one new finding; each is printed.
      2 - CANNOT VERIFY. java missing, classpath incomplete, config or baseline absent, a named
          file absent, or the analyser produced no readable report. Never reported as 0: "could
          not check" and "checked and found nothing" are different facts, and collapsing them
          certifies unchecked work.
#>
[CmdletBinding()]
param(
    [string[]] $ChangedFiles,
    [string]   $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]   $ConfigPath,
    [string]   $CacheRoot = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'),
    [switch]   $Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Exit-CannotVerify([string] $Message) {
    Write-Error "detekt-scoped: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

if (-not $ConfigPath) { $ConfigPath = Join-Path $RepoRoot 'config/detekt/detekt.yml' }
if (-not (Test-Path -LiteralPath $ConfigPath)) { Exit-CannotVerify "detekt config not found at $ConfigPath." }

# --- changed set ------------------------------------------------------------
$expanded = @()
foreach ($entry in ($ChangedFiles | Where-Object { $_ })) {
    $expanded += ($entry -split ',') | ForEach-Object { $_.Trim() }
}

# module -> list of absolute .kt paths. A wear file judged against the app_v2 baseline would report
# the whole of wear's known debt as new, so the grouping is load-bearing, not tidiness.
$byModule = [ordered]@{}
foreach ($f in ($expanded | Where-Object { $_ })) {
    $p = if ([System.IO.Path]::IsPathRooted($f)) { $f } else { Join-Path $RepoRoot $f }
    if (-not (Test-Path -LiteralPath $p)) {
        Exit-CannotVerify "file not found: $f - refusing to report a clean run over a file it could not read."
    }
    if ([System.IO.Path]::GetExtension($p) -ne '.kt') { continue }
    $abs = (Resolve-Path -LiteralPath $p).Path
    $rel = ($abs.Substring($RepoRoot.Length).TrimStart('\', '/')) -replace '\\', '/'
    $module = ($rel -split '/')[0]
    if ($module -notin @('app_v2', 'wear')) { continue }
    if (-not $byModule.Contains($module)) { $byModule[$module] = [System.Collections.Generic.List[string]]::new() }
    $byModule[$module].Add($abs)
}

if ($byModule.Count -eq 0) {
    Write-Host 'detekt-scoped: no analysable .kt file in the changed set - nothing to check.'
    exit 0
}

# --- analyser classpath -----------------------------------------------------
# Read the pin from the build file so this never drifts from what gradle resolves.
$buildFile = Join-Path $RepoRoot 'build.gradle.kts'
if (-not (Test-Path -LiteralPath $buildFile)) { Exit-CannotVerify "build.gradle.kts not found at $buildFile." }
$pin = [regex]::Match((Get-Content -LiteralPath $buildFile -Raw),
    'id\("io\.gitlab\.arturbosch\.detekt"\)\s+version\s+"([^"]+)"')
if (-not $pin.Success) { Exit-CannotVerify 'could not read the detekt version pin from build.gradle.kts.' }
$detektVersion = $pin.Groups[1].Value

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Exit-CannotVerify 'java is not on PATH.' }
if (-not (Test-Path -LiteralPath $CacheRoot)) { Exit-CannotVerify "gradle dependency cache not found at $CacheRoot." }

function Resolve-Jar([string] $Coordinate, [string] $Version) {
    $dir = Join-Path $CacheRoot $Coordinate
    if (-not (Test-Path -LiteralPath $dir)) { return @() }
    $versionDirs = @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue)
    if ($Version) { $versionDirs = @($versionDirs | Where-Object { $_.Name -eq $Version }) }
    $out = @()
    foreach ($vd in $versionDirs) {
        $jar = Get-ChildItem -LiteralPath $vd.FullName -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
            Select-Object -First 1
        if ($jar) { $out += $jar.FullName }
    }
    return $out
}

# The Kotlin artifacts are the fragile axis. detekt is compiled against one Kotlin version while
# this project builds with another, and BOTH sit in the shared cache - putting the wrong one first
# fails inside detekt's own rule-set wiring, not at load time, so it cannot be detected by
# inspecting the classpath. Rather than hardcode a version (which is exactly what made the
# archived precedent unreusable), try each candidate and keep the one that actually runs.
function Get-KotlinCandidates {
    $dir = Join-Path $CacheRoot 'org.jetbrains.kotlin/kotlin-compiler-embeddable'
    if (-not (Test-Path -LiteralPath $dir)) { return @() }
    return @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue |
            Sort-Object -Property Name | ForEach-Object { $_.Name })
}

function Build-Classpath([string] $KotlinVersion) {
    $list = [System.Collections.Generic.List[string]]::new()
    foreach ($a in (Get-ChildItem -LiteralPath (Join-Path $CacheRoot 'io.gitlab.arturbosch.detekt') -Directory -ErrorAction SilentlyContinue)) {
        if ($a.Name -in @('detekt-formatting', 'detekt-gradle-plugin')) { continue }
        foreach ($j in (Resolve-Jar "io.gitlab.arturbosch.detekt/$($a.Name)" $detektVersion)) { $list.Add($j) }
    }
    foreach ($a in @('kotlin-stdlib', 'kotlin-compiler-embeddable', 'kotlin-reflect', 'kotlin-script-runtime')) {
        foreach ($j in (Resolve-Jar "org.jetbrains.kotlin/$a" $KotlinVersion)) { $list.Add($j) }
    }
    foreach ($c in @('org.jcommander/jcommander', 'com.beust/jcommander',
            'org.jetbrains.intellij.deps/trove4j', 'org.jetbrains/annotations',
            'org.snakeyaml/snakeyaml-engine', 'org.yaml/snakeyaml',
            'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm',
            'io.github.davidburstrom.contester/contester-breakpoint',
            'org.ec4j.core/ec4j-core', 'io.github.microutils/kotlin-logging-jvm',
            'org.slf4j/slf4j-api', 'org.slf4j/slf4j-simple')) {
        foreach ($j in (Resolve-Jar $c $null)) { $list.Add($j) }
    }
    foreach ($k in (Get-ChildItem -LiteralPath (Join-Path $CacheRoot 'com.pinterest.ktlint') -Directory -ErrorAction SilentlyContinue)) {
        foreach ($j in (Resolve-Jar "com.pinterest.ktlint/$($k.Name)" $null)) { $list.Add($j) }
    }
    return @($list)
}

$plugin = @(Resolve-Jar 'io.gitlab.arturbosch.detekt/detekt-formatting' $detektVersion)
if ($plugin.Count -eq 0) {
    Exit-CannotVerify "detekt-formatting $detektVersion is not in the dependency cache - the formatting rules would be silently skipped."
}

$kotlinCandidates = @(Get-KotlinCandidates)
if ($kotlinCandidates.Count -eq 0) { Exit-CannotVerify "no kotlin-compiler-embeddable in the dependency cache under $CacheRoot." }

# Remember the combination that worked, keyed on the detekt pin: a later run then costs one JVM
# start, not several. A pin change invalidates the entry by construction.
$cpCachePath = Join-Path $RepoRoot 'temp/detekt-scoped/classpath.json'
$preferredKotlin = $null
if (Test-Path -LiteralPath $cpCachePath) {
    try {
        $cached = Get-Content -LiteralPath $cpCachePath -Raw | ConvertFrom-Json
        if ($cached.detekt -eq $detektVersion -and ($kotlinCandidates -contains $cached.kotlin)) {
            $preferredKotlin = $cached.kotlin
        }
    }
    catch { $preferredKotlin = $null }
}
$ordered = @()
if ($preferredKotlin) { $ordered += $preferredKotlin }
$ordered += @($kotlinCandidates | Where-Object { $_ -ne $preferredKotlin })

# --- run --------------------------------------------------------------------
$reportDir = Join-Path $RepoRoot 'temp/detekt-scoped'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }

$reports = [ordered]@{}
$sw = [System.Diagnostics.Stopwatch]::StartNew()
Push-Location $RepoRoot
try {
    foreach ($module in @($byModule.Keys)) {
        $baseline = Join-Path $RepoRoot "config/detekt/baseline-$module.xml"
        if (-not (Test-Path -LiteralPath $baseline)) { Exit-CannotVerify "baseline for $module not found at $baseline." }
        $report = Join-Path $reportDir "$module.xml"
        if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report -Force }

        $argv = @(
            '--input', (($byModule[$module]) -join ',')
            '--config', $ConfigPath
            '--build-upon-default-config'
            '--plugins', $plugin[0]
            '--baseline', $baseline
            '--report', "xml:$report"
        )
        # Keep the analyser's own output. Exit 2 is only actionable if it says what went wrong -
        # a wrong Kotlin version shows up as a stack trace inside detekt's rule-set wiring and
        # nowhere else, so a swallowed message makes "cannot verify" undiagnosable.
        $lastOutput = @()
        foreach ($kotlinVersion in $ordered) {
            $jars = Build-Classpath $kotlinVersion
            if (-not ($jars | Where-Object { $_ -match 'detekt-cli' })) {
                Exit-CannotVerify "detekt-cli $detektVersion is not in the dependency cache (looked under $CacheRoot)."
            }
            $lastOutput = @(& java -cp ($jars -join ';') io.gitlab.arturbosch.detekt.cli.Main @argv 2>&1 |
                    Where-Object { $_ -notmatch '^SLF4J:' })
            if (Test-Path -LiteralPath $report) {
                if ($kotlinVersion -ne $preferredKotlin) {
                    $preferredKotlin = $kotlinVersion
                    [PSCustomObject]@{ detekt = $detektVersion; kotlin = $kotlinVersion } |
                        ConvertTo-Json | Set-Content -LiteralPath $cpCachePath -Encoding UTF8
                    $ordered = @($kotlinVersion) + @($kotlinCandidates | Where-Object { $_ -ne $kotlinVersion })
                }
                break
            }
        }
        if (-not (Test-Path -LiteralPath $report)) {
            $tail = (@($lastOutput) | Select-Object -Last 6) -join "`n  "
            Exit-CannotVerify ("the analyser wrote no report for $module under any cached Kotlin version " +
                "($($kotlinCandidates -join ', ')). Its output was:`n  $tail")
        }
        $reports[$module] = $report
    }
}
finally { Pop-Location }
$sw.Stop()

# Relative to this script, not to -RepoRoot: the parser is part of the tooling, while -RepoRoot
# names the tree under analysis, and the two are different directories under test.
. (Join-Path $PSScriptRoot 'lib/detekt-report.ps1')
$parsed = Get-DetektFindingsFromReports -Reports $reports
if (-not $parsed.Ok) {
    Exit-CannotVerify "the analyser produced no readable report ($($parsed.Reason)). Refusing to report clean without reading one."
}

$findings = @($parsed.Findings)
if ($Json) { $findings | ConvertTo-Json -Depth 4 }
else {
    # S1600: prefixed with this script's own tag so a caller's line filter keeps the findings, not
    # just the verdict. An unprefixed indented line is dropped by every `Select-String` pattern the
    # callers actually write, which turns a named finding back into an unattributable failure.
    foreach ($f in $findings) {
        Write-Host ("detekt-scoped:   {0}:{1}:{2} - {3} - {4}" -f $f.File, $f.Line, $f.Column, $f.RuleId, $f.Message) -ForegroundColor Yellow
    }
}

$scope = (@($byModule.Keys) -join ' + ')
$fileCount = @($byModule.Values | ForEach-Object { $_ }).Count
if ($findings.Count -eq 0) {
    Write-Host ("detekt-scoped: PASS [{0}] - {1} file(s), no new finding under the full configured rule set ({2:N1}s)." -f `
            $scope, $fileCount, $sw.Elapsed.TotalSeconds) -ForegroundColor Green
    exit 0
}

$byRule = ($findings | Group-Object RuleId | ForEach-Object { "$($_.Name) $($_.Count)" }) -join ', '
Write-Error ("detekt-scoped: FAIL [{0}] - {1} new finding(s) in {2} changed file(s) - {3}. Fix the lines above." -f `
        $scope, $findings.Count, $fileCount, $byRule) -ErrorAction Continue
exit 1
