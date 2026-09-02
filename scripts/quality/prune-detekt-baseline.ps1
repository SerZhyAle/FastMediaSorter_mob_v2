#requires -Version 7.0
<#
.SYNOPSIS
    S2112: remove from the operational detekt baseline exactly the entries whose finding no longer
    exists in a named file set - and refuse outright when that set carries a finding the baseline
    does not already hold.

.DESCRIPTION
    detekt's own way to shed a dead baseline entry is `:<module>:detektBaseline`, a whole-module
    re-freeze. That call cannot distinguish "this finding was fixed" from "this finding is new": on
    2026-08-02 it silently absorbed the debt two open tickets were written about, which is the
    incident scripts/quality/assert-detekt-baseline-absorption.ps1 (S1356) exists to make loud.
    Batched autocorrect needs the opposite operation - shed the dead, accept nothing - so this script
    provides it.

    How it gets its truth: detekt's CLI accepts an explicit `--input` list together with
    `--create-baseline`, and the IDs it writes are byte-identical to the ones the operational
    baseline holds for the same code (measured on three files, 2026-08-27, 0 spurious differences -
    PLAN/S2112_shrink_detekt_format_baseline/research/01__scoped-baseline-prune-mechanism.md). So a
    scoped run yields, for its input set, precisely the subset of the operational baseline that is
    still live, and the prune is a set subtraction over exact strings:

      in operational (restricted to the set), absent from the fresh run  -> DEAD -> deleted
      in the fresh run, absent from operational                          -> NEW  -> refusal, exit 1

    The script has no code path that writes an `<ID>` it did not read out of the operational
    baseline. Absorbing debt is therefore impossible here rather than merely forbidden.

    Input-set expansion is load-bearing, not convenience. A baseline ID carries
    `Rule:FileName$signature` with no directory, and 329 of app_v2's 7693 format entries sit on file
    names that occur in more than one source set. Restricting the operational baseline by bare name
    while analysing only one source set's copy would classify the other copy's live entries as DEAD
    and delete them. So the analysed set is widened to every .kt under the module that shares a name
    with a named file, and the comparison is then honest on both sides.

    Takes no BUILD lock: no gradle process is involved. The caller owns the code-domain lock for the
    sources it autocorrected (CLAUDE.md Rule 23).

.PARAMETER Module
    app_v2 or wear. Selects config/detekt/baseline-<module>.xml and the source tree scanned for
    same-named files.

.PARAMETER Files
    Repo-relative or absolute .kt paths. Pass ONE comma-joined argument: `pwsh -File` binds a
    [string[]] parameter to its first element only, which is why every consumer in this repo
    comma-splits (S1184).

.PARAMETER Apply
    Rewrite the operational baseline, deleting the DEAD entries. Requires -Reason. Without it the
    script reports and writes nothing.

.PARAMETER Reason
    Why the prune is being applied now. Echoed in the run summary for the dev-log row.

.PARAMETER Json
    Write the report (counts, the DEAD and NEW id lists) to this path as JSON.

.NOTES
    Exit codes (S1070 contract):
      0 - reported, or pruned. No entry in the fresh scoped run is missing from the operational
          baseline.
      1 - at least one NEW entry: the named files carry a finding the operational baseline does not
          hold. Every one is printed. Nothing is written, with or without -Apply.
      2 - CANNOT VERIFY. java missing, classpath incomplete, config or baseline absent, a named file
          absent or outside the module, a wrapped <ID> line in a baseline, or the analyser produced
          no baseline. Never reported as 0: "could not check" and "checked and found nothing" are
          different facts, and collapsing them certifies unchecked work.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/prune-detekt-baseline.ps1 -Module app_v2 -Files "app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt"

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/prune-detekt-baseline.ps1 -Module app_v2 -Files "a.kt,b.kt" -Apply -Reason 'S2112 measurement package'
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module = 'app_v2',

    [string[]]$Files,

    [switch]$Apply,

    [string]$Reason,

    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    [string]$BaselinePath,

    [string]$SourceRoot,

    [string]$ConfigPath,

    [string]$CacheRoot = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'),

    [string]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Exit-CannotVerify([string]$Message) {
    Write-Error "prune-detekt-baseline: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

if ($Apply -and -not $Reason) {
    Exit-CannotVerify '-Apply requires -Reason: a baseline edit with no recorded motive is the thing this gate family exists to prevent.'
}

if (-not $BaselinePath) { $BaselinePath = Join-Path $RepoRoot "config/detekt/baseline-$Module.xml" }
if (-not $SourceRoot) { $SourceRoot = Join-Path $RepoRoot "$Module/src" }
if (-not $ConfigPath) { $ConfigPath = Join-Path $RepoRoot 'config/detekt/detekt.yml' }

foreach ($p in @($BaselinePath, $SourceRoot, $ConfigPath)) {
    if (-not (Test-Path -LiteralPath $p)) { Exit-CannotVerify "required input not found: $p" }
}

# --- named set ---------------------------------------------------------------
$named = @()
foreach ($entry in ($Files | Where-Object { $_ })) {
    $named += ($entry -split ',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
}
if ($named.Count -eq 0) { Exit-CannotVerify 'no file named - refusing to prune a baseline against an empty set.' }

$sourceRootAbs = (Resolve-Path -LiteralPath $SourceRoot).Path
$namedNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($f in $named) {
    $p = if ([System.IO.Path]::IsPathRooted($f)) { $f } else { Join-Path $RepoRoot $f }
    if (-not (Test-Path -LiteralPath $p)) {
        Exit-CannotVerify "file not found: $f - refusing to report a clean prune over a file it could not read."
    }
    $abs = (Resolve-Path -LiteralPath $p).Path
    if ([System.IO.Path]::GetExtension($abs) -ne '.kt') { Exit-CannotVerify "not a Kotlin source: $f" }
    if (-not $abs.StartsWith($sourceRootAbs, [System.StringComparison]::OrdinalIgnoreCase)) {
        Exit-CannotVerify "file is outside module '$Module' ($sourceRootAbs): $f"
    }
    [void]$namedNames.Add([System.IO.Path]::GetFileName($abs))
}

# Widen to every same-named file in the module, or a sibling source set's live entries would be
# read as dead and deleted (see .DESCRIPTION).
$analysed = @(Get-ChildItem -LiteralPath $sourceRootAbs -Recurse -Filter *.kt -File -ErrorAction SilentlyContinue |
        Where-Object { $namedNames.Contains($_.Name) } |
        ForEach-Object { $_.FullName } | Sort-Object -Unique)
if ($analysed.Count -eq 0) { Exit-CannotVerify 'the expanded input set is empty - nothing to analyse.' }

# --- baseline reader ---------------------------------------------------------
# Fail closed on a wrapped entry, same reasoning as split-detekt-baseline.ps1's Read-BaselineIds: a
# wrapped line reads as absent, which here would delete a live suppression.
function Read-BaselineIdLine {
    param([string]$Path)

    $rx = [regex]'<ID>(?<id>.*?)</ID>'
    $lines = [System.IO.File]::ReadAllLines($Path)
    $result = [System.Collections.Generic.List[object]]::new()
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $m = $rx.Matches($lines[$i])
        if ($m.Count -eq 0) {
            if ($lines[$i] -match '<ID>|</ID>') {
                Exit-CannotVerify "wrapped or malformed <ID> at $Path line $($i + 1) - refusing to edit a baseline it cannot read line by line."
            }
            continue
        }
        if ($m.Count -gt 1) {
            Exit-CannotVerify "more than one <ID> on $Path line $($i + 1) - refusing to edit a baseline whose entries are not one per line."
        }
        $result.Add([pscustomobject]@{ Index = $i; Id = $m[0].Groups['id'].Value })
    }
    return $result
}

$baselineEntries = @(Read-BaselineIdLine -Path (Resolve-Path -LiteralPath $BaselinePath).Path)
if ($baselineEntries.Count -eq 0) { Exit-CannotVerify "no <ID> entry read from $BaselinePath." }

function Get-IdFileName([string]$Id) {
    $rest = ($Id -split ':', 2)
    if ($rest.Count -lt 2) { return '' }
    return ($rest[1] -split '\$', 2)[0]
}

$scoped = @($baselineEntries | Where-Object { $namedNames.Contains((Get-IdFileName $_.Id)) })

# --- fresh scoped run --------------------------------------------------------
. (Join-Path $PSScriptRoot 'lib/detekt-classpath.ps1')

$cli = Initialize-DetektCli -RepoRoot $RepoRoot -CacheRoot $CacheRoot
if (-not $cli.Ok) { Exit-CannotVerify $cli.Reason }

$workDir = Join-Path $RepoRoot 'temp/detekt-scoped'
if (-not (Test-Path -LiteralPath $workDir)) { New-Item -ItemType Directory -Path $workDir -Force | Out-Null }
$freshPath = Join-Path $workDir "prune-$Module-fresh.xml"
if (Test-Path -LiteralPath $freshPath) { Remove-Item -LiteralPath $freshPath -Force }
# The report is the "did it run" observable, and it is load-bearing. detekt writes NO baseline file
# at all when the input set has zero findings, so an absent baseline is ambiguous between "clean"
# and "the analyser died in its rule-set wiring under the wrong Kotlin version" - and reading the
# second as the first would delete every entry the baseline holds for these files. The Checkstyle
# report is written either way, including as an empty <checkstyle/>, so its presence separates them.
$freshReport = Join-Path $workDir "prune-$Module-report.xml"
if (Test-Path -LiteralPath $freshReport) { Remove-Item -LiteralPath $freshReport -Force }

$argv = @(
    '--input', ($analysed -join ',')
    '--config', $ConfigPath
    '--build-upon-default-config'
    '--plugins', $cli.PluginJar
    '--create-baseline'
    '--baseline', $freshPath
    '--report', "xml:$freshReport"
)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$lastOutput = @()
$ordered = @($cli.KotlinCandidates)
$preferred = $ordered[0]
Push-Location $RepoRoot
try {
    foreach ($kotlinVersion in $ordered) {
        $jars = Build-DetektClasspath -CacheRoot $cli.CacheRoot -DetektVersion $cli.DetektVersion -KotlinVersion $kotlinVersion
        if (-not ($jars | Where-Object { $_ -match 'detekt-cli' })) {
            Exit-CannotVerify "detekt-cli $($cli.DetektVersion) is not in the dependency cache (looked under $($cli.CacheRoot))."
        }
        $lastOutput = @(& java -cp ($jars -join ';') io.gitlab.arturbosch.detekt.cli.Main @argv 2>&1 |
                Where-Object { $_ -notmatch '^SLF4J:' })
        if (Test-Path -LiteralPath $freshReport) {
            if ($kotlinVersion -ne $preferred) {
                Save-DetektClasspathMemo -MemoPath $cli.MemoPath -DetektVersion $cli.DetektVersion -KotlinVersion $kotlinVersion
            }
            break
        }
    }
}
finally { Pop-Location }
$sw.Stop()

if (-not (Test-Path -LiteralPath $freshReport)) {
    $tail = (@($lastOutput) | Select-Object -Last 6) -join "`n  "
    Exit-CannotVerify ("the analyser produced no report for $Module under any cached Kotlin version " +
        "($($ordered -join ', ')). Its output was:`n  $tail")
}

# Baseline absent while the report exists means the analyser ran and found nothing - a legitimate
# empty fresh set, not a failure.
# Assigned then conditionally overwritten rather than through an if-expression: a block whose value
# is @() emits nothing, so the variable would be $null and the HashSet constructor below would throw
# on exactly the clean-input case this branch exists to serve.
$freshIds = @()
if (Test-Path -LiteralPath $freshPath) {
    $freshIds = @((Read-BaselineIdLine -Path $freshPath) | ForEach-Object { $_.Id })
}

# --- the two sets ------------------------------------------------------------
$freshSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$freshIds, [System.StringComparer]::Ordinal)
$scopedSet = [System.Collections.Generic.HashSet[string]]::new([string[]]@($scoped | ForEach-Object { $_.Id }), [System.StringComparer]::Ordinal)

$dead = @($scoped | Where-Object { -not $freshSet.Contains($_.Id) })
$new = @($freshIds | Where-Object { -not $scopedSet.Contains($_) } | Sort-Object -Unique)

$scope = "$Module, $($namedNames.Count) name(s), $($analysed.Count) file(s) analysed"
Write-Host ("prune-detekt-baseline: [{0}] baseline holds {1} entr(ies) for these names; the fresh run found {2} ({3:N1}s)." -f `
        $scope, $scoped.Count, $freshIds.Count, $sw.Elapsed.TotalSeconds)

if ($Json) {
    $jsonPath = if ([System.IO.Path]::IsPathRooted($Json)) { $Json } else { Join-Path $RepoRoot $Json }
    $jsonDir = Split-Path -Parent $jsonPath
    if ($jsonDir -and -not (Test-Path -LiteralPath $jsonDir)) { New-Item -ItemType Directory -Path $jsonDir -Force | Out-Null }
    [pscustomobject]@{
        module        = $Module
        names         = @($namedNames)
        analysedFiles = @($analysed)
        scopedCount   = $scoped.Count
        freshCount    = $freshIds.Count
        dead          = @($dead | ForEach-Object { $_.Id })
        new           = @($new)
        applied       = [bool]$Apply -and $new.Count -eq 0
        reason        = $Reason
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
}

if ($new.Count -gt 0) {
    foreach ($id in $new) { Write-Host ("prune-detekt-baseline:   NEW {0}" -f $id) -ForegroundColor Yellow }
    Write-Error ("prune-detekt-baseline: FAIL [{0}] - {1} finding(s) in the named files are absent from the operational baseline. " -f $scope, $new.Count +
        'Nothing was written. Fix them, or accept them deliberately through the baseline re-freeze path - this script never adds an entry.') -ErrorAction Continue
    exit 1
}

if ($dead.Count -eq 0) {
    Write-Host ("prune-detekt-baseline: PASS [{0}] - nothing dead, nothing new; baseline unchanged." -f $scope) -ForegroundColor Green
    exit 0
}

$byRule = ($dead | Group-Object { ($_.Id -split ':', 2)[0] } | Sort-Object Count -Descending |
        ForEach-Object { "$($_.Name) $($_.Count)" }) -join ', '

if (-not $Apply) {
    Write-Host ("prune-detekt-baseline: {0} dead entr(ies) would be removed - {1}. Re-run with -Apply -Reason to write." -f $dead.Count, $byRule) -ForegroundColor Yellow
    exit 0
}

# --- write: deletion only ----------------------------------------------------
# Line-indexed deletion, so every surviving line is copied verbatim - element order, indentation and
# escaping all stay exactly as detekt wrote them.
$deadIndex = [System.Collections.Generic.HashSet[int]]::new([int[]]@($dead | ForEach-Object { $_.Index }))
$original = [System.IO.File]::ReadAllLines($BaselinePath)
$kept = [System.Collections.Generic.List[string]]::new()
for ($i = 0; $i -lt $original.Length; $i++) {
    if ($deadIndex.Contains($i)) { continue }
    $kept.Add($original[$i])
}

# The file detekt writes is LF-terminated with no BOM and ends in a newline; reproduce that rather
# than letting the platform default turn a prune into a whole-file diff.
$text = ($kept -join "`n") + "`n"
[System.IO.File]::WriteAllText($BaselinePath, $text, [System.Text.UTF8Encoding]::new($false))

Write-Host ("prune-detekt-baseline: PRUNED [{0}] - removed {1} dead entr(ies) - {2}." -f $scope, $dead.Count, $byRule) -ForegroundColor Green
Write-Host ("prune-detekt-baseline: reason - {0}" -f $Reason)
Write-Host 'prune-detekt-baseline: regenerate the derived artifacts in the same wave - split-detekt-baseline.ps1 -Update and assert-detekt-baseline-absorption.ps1 -Update.'
exit 0
