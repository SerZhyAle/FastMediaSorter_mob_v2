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
    mtime, so a second writer there would make an unrelated gate blame the wrong ticket. Each pass
    below parses that report immediately, so the -Fix mode's three passes may share the one path.

    The detekt version is read from build.gradle.kts at run time rather than hardcoded, so this
    tracks the build's own pin instead of drifting away from it.

.PARAMETER ChangedFiles
    Repo-relative or absolute paths. Pass ONE comma-joined argument: `pwsh -File` binds a
    [string[]] parameter to its first element only, which is why every consumer in this repo
    comma-splits (S1184).

.PARAMETER Fix
    Housekeeping mode. See the "Fix mode" note below: it judges first, corrects only files that
    carry a finding, and restores any file the correction made worse.

.PARAMETER Json
    Emit the findings as JSON instead of human-readable lines.

.NOTES
    Exit codes:
      0 - the analyser ran and found nothing new in the named files (or there were none to check).
      1 - the analyser ran and found at least one new finding; each is printed. Never returned in
          -Fix mode: formatting is housekeeping, not a verdict, so a fix run always exits 0 and
          leaves the FAIL to the preflight that runs after it.
      2 - CANNOT VERIFY. java missing, classpath incomplete, config or baseline absent, a named
          file absent, or the analyser produced no readable report. Never reported as 0: "could
          not check" and "checked and found nothing" are different facts, and collapsing them
          certifies unchecked work.

    Fix mode (S2116). The housekeeping pass may only touch a file whose verdict it improves.
    Before this, -Fix ran ktlint auto-correct over every named file unconditionally and never
    judged what it left on disk, so it created findings the very next step then refused and the
    closure could not converge - measured 2026-08-27 on S2104, 74 findings over 54 files that a
    judge run had called clean seconds earlier, three runs running, and reproduced in isolation on
    one file (PASS with 0 findings, then FAIL with 4 after -Fix). Two properties made those
    findings unfixable by the formatter that made them: wrapping a baselined single-line call
    changes the declaration text the baseline id is addressed by, and the wrap leaves a bare
    string literal longer than the limit, which no further formatting can shorten.

    So -Fix is three passes: judge the whole set, hand the corrector ONLY the files that carry a
    finding, then re-judge those and restore, byte for byte, any file whose finding count grew.
    A clean set therefore costs exactly one analyser pass and leaves every file untouched.
#>
[CmdletBinding()]
param(
    [string[]] $ChangedFiles,
    [string]   $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]   $ConfigPath,
    [string]   $CacheRoot = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'),
    # S1949-follow-up: rewrite what the formatting ruleset can fix by itself instead of reporting it.
    # Import order, a blank line before a brace, a missing trailing newline - findings that cost a
    # full closure round-trip to report and one keystroke to fix. With -Fix this script corrects them
    # in place and always exits 0: formatting is never a verdict, it is housekeeping done before one.
    [switch]   $Fix,
    # -Fix only: where to record that the judging pass found the whole set clean. The caller can
    # then skip the separate preflight, which re-runs this same analyser over these same files.
    [string]   $VerdictPath,
    [switch]   $Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Exit-CannotVerify([string] $Message) {
    Write-Error "detekt-scoped: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

# The analyser reports a lowercased, forward-slashed path; the changed set carries native ones.
# One normaliser for both sides, so a finding can be attributed back to the file it came from.
function ConvertTo-FindingKey([string] $Path) {
    return (($Path -replace '\\', '/').ToLower())
}

function Group-FindingCountByFile([object[]] $Findings) {
    $counts = @{}
    foreach ($f in @($Findings)) {
        if (-not $counts.ContainsKey($f.File)) { $counts[$f.File] = 0 }
        $counts[$f.File]++
    }
    return $counts
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
# S2112: assembly lives in lib/detekt-classpath.ps1 so the prune tool cannot build a different one.
. (Join-Path $PSScriptRoot 'lib/detekt-classpath.ps1')
# Relative to this script, not to -RepoRoot: the parser is part of the tooling, while -RepoRoot
# names the tree under analysis, and the two are different directories under test.
. (Join-Path $PSScriptRoot 'lib/detekt-report.ps1')

$cli = Initialize-DetektCli -RepoRoot $RepoRoot -CacheRoot $CacheRoot
if (-not $cli.Ok) { Exit-CannotVerify $cli.Reason }

$detektVersion = $cli.DetektVersion
$plugin = @($cli.PluginJar)
$cpCachePath = $cli.MemoPath
# Script scope: the memo of which cached Kotlin version actually works is learned on the first pass
# and must survive into the second and third, or -Fix pays the fallback search three times.
$script:KotlinOrder = @($cli.KotlinCandidates)
$script:PreferredKotlin = if ($script:KotlinOrder.Count -gt 0) { $script:KotlinOrder[0] } else { $null }

$reportDir = Join-Path $RepoRoot 'temp/detekt-scoped'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }

<#
.SYNOPSIS
    One analyser pass over a module -> files map. Returns the parsed findings.
#>
function Invoke-DetektPass {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [System.Collections.IDictionary] $Files,

        [switch] $AutoCorrect
    )

    $reports = [ordered]@{}
    Push-Location $RepoRoot
    try {
        foreach ($module in @($Files.Keys)) {
            $inputs = @($Files[$module])
            if ($inputs.Count -eq 0) { continue }

            $baseline = Join-Path $RepoRoot "config/detekt/baseline-$module.xml"
            if (-not (Test-Path -LiteralPath $baseline)) { Exit-CannotVerify "baseline for $module not found at $baseline." }
            $report = Join-Path $reportDir "$module.xml"
            if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report -Force }

            $argv = @(
                '--input', ($inputs -join ',')
                '--config', $ConfigPath
                '--build-upon-default-config'
                '--plugins', $plugin[0]
                '--baseline', $baseline
                '--report', "xml:$report"
            )
            if ($AutoCorrect) {
                # detekt only auto-corrects rules that declare themselves correctable, which in this
                # configuration is the ktlint formatting ruleset and nothing else. No semantic rule is
                # rewritten, so a fix run cannot change what the code does.
                #
                # The main config pins formatting.autoCorrect to false so the GATE can never rewrite what
                # it is judging; --auto-correct alone is therefore silently inert. The overlay flips that
                # single flag and is merged after the main config, so every threshold and exclusion still
                # comes from detekt.yml.
                $argv += '--auto-correct'
                $overlay = Join-Path $RepoRoot 'config/detekt/format-autocorrect.yml'
                if (Test-Path -LiteralPath $overlay) {
                    $configIndex = [array]::IndexOf($argv, '--config')
                    $argv[$configIndex + 1] = "$ConfigPath,$overlay"
                }
            }

            # Keep the analyser's own output. Exit 2 is only actionable if it says what went wrong -
            # a wrong Kotlin version shows up as a stack trace inside detekt's rule-set wiring and
            # nowhere else, so a swallowed message makes "cannot verify" undiagnosable.
            $lastOutput = @()
            foreach ($kotlinVersion in @($script:KotlinOrder)) {
                $jars = Build-DetektClasspath -CacheRoot $cli.CacheRoot -DetektVersion $detektVersion -KotlinVersion $kotlinVersion
                if (-not ($jars | Where-Object { $_ -match 'detekt-cli' })) {
                    Exit-CannotVerify "detekt-cli $detektVersion is not in the dependency cache (looked under $CacheRoot)."
                }
                $lastOutput = @(& java -cp ($jars -join ';') io.gitlab.arturbosch.detekt.cli.Main @argv 2>&1 |
                        Where-Object { $_ -notmatch '^SLF4J:' })
                if (Test-Path -LiteralPath $report) {
                    if ($kotlinVersion -ne $script:PreferredKotlin) {
                        $script:PreferredKotlin = $kotlinVersion
                        Save-DetektClasspathMemo -MemoPath $cpCachePath -DetektVersion $detektVersion -KotlinVersion $kotlinVersion
                        $script:KotlinOrder = @($kotlinVersion) + @($script:KotlinOrder | Where-Object { $_ -ne $kotlinVersion })
                    }
                    break
                }
            }
            if (-not (Test-Path -LiteralPath $report)) {
                $tail = (@($lastOutput) | Select-Object -Last 6) -join "`n  "
                Exit-CannotVerify ("the analyser wrote no report for $module under any cached Kotlin version " +
                    "($($script:KotlinOrder -join ', ')). Its output was:`n  $tail")
            }
            $reports[$module] = $report
        }
    }
    finally { Pop-Location }

    $parsed = Get-DetektFindingsFromReports -Reports $reports
    if (-not $parsed.Ok) {
        Exit-CannotVerify "the analyser produced no readable report ($($parsed.Reason)). Refusing to report clean without reading one."
    }
    # Unary comma: a bare `return @()` emits nothing at all, so the caller gets $null and its very
    # next `@($result)` becomes a one-element array holding $null - which under StrictMode throws on
    # the first property access. The wrap makes "no findings" survive as an empty array.
    return , @($parsed.Findings)
}

function Write-Findings([object[]] $Findings) {
    if ($Json) {
        @($Findings) | ConvertTo-Json -Depth 4
        return
    }
    # S1600: prefixed with this script's own tag so a caller's line filter keeps the findings, not
    # just the verdict. An unprefixed indented line is dropped by every `Select-String` pattern the
    # callers actually write, which turns a named finding back into an unattributable failure.
    foreach ($f in @($Findings | Where-Object { $null -ne $_ })) {
        Write-Host ("detekt-scoped:   {0}:{1}:{2} - {3} - {4}" -f $f.File, $f.Line, $f.Column, $f.RuleId, $f.Message) -ForegroundColor Yellow
    }
}

$scope = (@($byModule.Keys) -join ' + ')
$fileCount = @($byModule.Values | ForEach-Object { $_ }).Count
$sw = [System.Diagnostics.Stopwatch]::StartNew()

# --- judge mode -------------------------------------------------------------
if (-not $Fix) {
    $findings = Invoke-DetektPass -Files $byModule
    $sw.Stop()
    Write-Findings $findings
    if ($findings.Count -eq 0) {
        Write-Host ("detekt-scoped: PASS [{0}] - {1} file(s), no new finding under the full configured rule set ({2:N1}s)." -f `
                $scope, $fileCount, $sw.Elapsed.TotalSeconds) -ForegroundColor Green
        exit 0
    }
    $byRule = ($findings | Group-Object RuleId | ForEach-Object { "$($_.Name) $($_.Count)" }) -join ', '
    Write-Error ("detekt-scoped: FAIL [{0}] - {1} new finding(s) in {2} changed file(s) - {3}. Fix the lines above." -f `
            $scope, $findings.Count, $fileCount, $byRule) -ErrorAction Continue
    exit 1
}

# --- fix mode: judge, correct only what is broken, undo what got worse ------
$before = Invoke-DetektPass -Files $byModule
$beforeByFile = Group-FindingCountByFile $before

# A finding whose path does not match any file of the set cannot be attributed, and an
# unattributable finding must not be read as "some other file is clean". Fall back to treating the
# whole set as one unit: correct all of it, and judge the regression on the total.
$keyed = @{}
foreach ($module in @($byModule.Keys)) {
    foreach ($abs in @($byModule[$module])) { $keyed[(ConvertTo-FindingKey $abs)] = $abs }
}
$attributed = $true
foreach ($f in @($before)) { if (-not $keyed.ContainsKey($f.File)) { $attributed = $false } }

$targets = [ordered]@{}
foreach ($module in @($byModule.Keys)) {
    foreach ($abs in @($byModule[$module])) {
        if ($attributed -and -not $beforeByFile.ContainsKey((ConvertTo-FindingKey $abs))) { continue }
        if (-not $targets.Contains($module)) { $targets[$module] = [System.Collections.Generic.List[string]]::new() }
        $targets[$module].Add($abs)
    }
}

if ($targets.Count -eq 0) {
    $sw.Stop()
    # The judging pass above ran the full configured rule set over the whole set and found nothing,
    # and nothing was rewritten, so a second analyser run over the same files has nothing to find.
    if ($VerdictPath) {
        try {
            [System.IO.File]::WriteAllText($VerdictPath, (@{
                        status   = 'clean'
                        files    = $fileCount
                        findings = 0
                    } | ConvertTo-Json -Compress))
        }
        catch {
            # A verdict that cannot be recorded only costs the caller the preflight it would
            # otherwise skip. It must never turn housekeeping into a failure.
        }
    }
    Write-Host ("detekt-scoped: FIXED [{0}] - {1} file(s) judged clean, nothing to correct - source untouched ({2:N1}s)." -f `
            $scope, $fileCount, $sw.Elapsed.TotalSeconds) -ForegroundColor Green
    exit 0
}

$targetCount = @($targets.Values | ForEach-Object { $_ }).Count
$snapshots = [ordered]@{}
foreach ($module in @($targets.Keys)) {
    foreach ($abs in @($targets[$module])) { $snapshots[$abs] = [System.IO.File]::ReadAllBytes($abs) }
}

Invoke-DetektPass -Files $targets -AutoCorrect | Out-Null
$after = Invoke-DetektPass -Files $targets
$afterByFile = Group-FindingCountByFile $after

$reverted = [System.Collections.Generic.List[string]]::new()
$revertRules = [System.Collections.Generic.List[string]]::new()
if ($attributed) {
    foreach ($abs in @($snapshots.Keys)) {
        $key = ConvertTo-FindingKey $abs
        $was = if ($beforeByFile.ContainsKey($key)) { $beforeByFile[$key] } else { 0 }
        $now = if ($afterByFile.ContainsKey($key)) { $afterByFile[$key] } else { 0 }
        if ($now -le $was) { continue }
        [System.IO.File]::WriteAllBytes($abs, $snapshots[$abs])
        $reverted.Add((Split-Path -Leaf $abs))
        foreach ($r in (@($after) | Where-Object { $_.File -eq $key } | ForEach-Object { $_.RuleId } | Sort-Object -Unique)) {
            if (-not $revertRules.Contains($r)) { $revertRules.Add($r) }
        }
    }
}
elseif (@($after).Count -gt @($before).Count) {
    foreach ($abs in @($snapshots.Keys)) {
        [System.IO.File]::WriteAllBytes($abs, $snapshots[$abs])
        $reverted.Add((Split-Path -Leaf $abs))
    }
    foreach ($r in (@($after) | ForEach-Object { $_.RuleId } | Sort-Object -Unique)) {
        if (-not $revertRules.Contains($r)) { $revertRules.Add($r) }
    }
}

$sw.Stop()
Write-Findings $before

$byRule = ((@($before) | Group-Object RuleId | ForEach-Object { "$($_.Name) $($_.Count)" }) -join ', ')

# Housekeeping never renders a verdict. Whatever the formatting ruleset could rewrite is already
# rewritten on disk; whatever it could not is left for the preflight that runs straight after,
# which is the step that owns the FAIL. Returning 1 here would abort a closure over a finding
# this run may have just fixed.
if ($reverted.Count -gt 0) {
    Write-Host ("detekt-scoped: FIXED [{0}] - {1} of {2} file(s) offered to the corrector; {3} restored because the correction " +
        "added findings it cannot fix ({4}) - {5}; {6} finding(s) judged before correction - {7} ({8:N1}s)." -f `
            $scope, $targetCount, $fileCount, $reverted.Count, ($revertRules -join ', '), ($reverted -join ', '), `
            @($before).Count, $byRule, $sw.Elapsed.TotalSeconds) -ForegroundColor Yellow
}
else {
    Write-Host ("detekt-scoped: FIXED [{0}] - {1} of {2} file(s) corrected, none made worse; {3} finding(s) judged before correction - {4} ({5:N1}s)." -f `
            $scope, $targetCount, $fileCount, @($before).Count, $byRule, $sw.Elapsed.TotalSeconds) -ForegroundColor Yellow
}
exit 0
