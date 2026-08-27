#requires -Version 7.0
<#
.SYNOPSIS
    S2105 gate: generate/verify format-vs-signal view files derived from the operational detekt
    baseline, so the format and signal debt each have a cheap, separate count.

.DESCRIPTION
    detekt's Gradle plugin (1.23.8, this repo's pin) exposes exactly one `RegularFileProperty`
    baseline input per module - it cannot read two baseline files at once
    (PLAN/S2105_split_detekt_baseline_format_vs_signal/research/01__detekt-single-baseline-limit.md).
    So this script never changes what detekt or the existing S1356 absorption gate read. It treats
    `config/detekt/baseline-<module>.xml` as read-only truth and derives two committed VIEW files
    from it plus `config/detekt/rule-categories.txt` (the format/signal classification table):

      config/detekt/baseline-<module>-format.xml
      config/detekt/baseline-<module>-signal.xml

    Same `<SmellBaseline><CurrentIssues><ID>..</ID></CurrentIssues></SmellBaseline>` shape as the
    operational file, sorted, so each is diffable on its own. Their combined ID set always equals
    the operational baseline's ID set exactly - a rule name absent from the table is not silently
    dropped into either bucket, it fails the whole run closed.

    Contract mirrors scripts/quality/assert-detekt-baseline-absorption.ps1 (S1356) on purpose: same
    module selection, same -Update/-Reason acceptance flow, same S1070 exit codes, so a change to one
    is easy to reconcile with the other on review.

    Exit codes (S1070 contract):
      0  PASS - view files match what the operational baseline + table currently produce, or
         -Update completed, or drift found without -Gate.
      1  FAIL - -Gate and the committed view files are stale versus a fresh regeneration.
      2  Cannot verify - operational baseline, category table or (in check mode) a view file is
         missing/unparseable, or the operational baseline contains a rule name absent from
         config/detekt/rule-categories.txt. Never a PASS: "could not check" is a different fact
         from "checked and found nothing wrong".

.PARAMETER Module
    app_v2 or wear. Omit to check both.

.PARAMETER Gate
    Exit 1 when the committed view files are stale. Without it, drift is reported and exit stays 0.

.PARAMETER Update
    Regenerate the view file(s) from the current operational baseline + category table and exit 0.
    Requires -Reason.

.PARAMETER Reason
    Why the view files are being (re)generated now. Echoed in the run summary for the dev-log row.

.PARAMETER CategoriesFile
    Path override for the classification table. Defaults to config/detekt/rule-categories.txt.

.PARAMETER Json
    Write the per-module report (counts, drift) to this path as JSON.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/split-detekt-baseline.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/split-detekt-baseline.ps1 -Module app_v2 -Update -Reason 'S2105 initial split'
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [switch]$Gate,
    [switch]$Update,
    [string]$Reason,
    [string]$CategoriesFile,
    [string]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Resolve-RepoPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $repoRoot $Path)
}

# Reads the raw <ID> payloads out of a detekt baseline (operational or view file). Fail-closed on a
# wrapped entry, same reasoning as assert-detekt-baseline-absorption.ps1's Read-BaselineIds: a
# wrapped line would be silently read as absent, which here would misreport a real entry as dropped.
function Read-BaselineIds {
    param([string]$Path)

    $idLineRegex = [regex]'<ID>(?<id>.*?)</ID>'
    $ids = [System.Collections.Generic.List[string]]::new()
    $rawOpenTags = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $rawOpenTags += ([regex]::Matches($line, '<ID>')).Count
        foreach ($m in $idLineRegex.Matches($line)) {
            $ids.Add($m.Groups['id'].Value)
        }
    }
    if ($rawOpenTags -ne $ids.Count) {
        Write-Error ("split-detekt-baseline: cannot verify - $Path has $rawOpenTags <ID> tag(s) " +
            "but $($ids.Count) parsed on single lines. An entry spans lines; refusing to judge a " +
            'partially-read baseline.') -ErrorAction Continue
        return $null
    }
    return , $ids
}

# RuleName is the ID prefix up to the first colon - "Rule:File$signature".
function Get-RuleName {
    param([string]$Raw)
    $colonIdx = $Raw.IndexOf(':')
    if ($colonIdx -lt 0) { return $Raw }
    return $Raw.Substring(0, $colonIdx)
}

function Read-CategoryTable {
    param([string]$Path)
    $table = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $parts = $line -split "`t"
        if ($parts.Count -ne 2) {
            Write-Error "split-detekt-baseline: cannot verify - malformed line in ${Path}: '$line'" -ErrorAction Continue
            return $null
        }
        $table[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $table
}

function Write-ViewFile {
    param([string]$Path, [string[]]$Ids)
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('<?xml version="1.0" ?>')
    $lines.Add('<SmellBaseline>')
    $lines.Add('  <ManuallySuppressedIssues/>')
    $lines.Add('  <CurrentIssues>')
    foreach ($id in ($Ids | Sort-Object)) {
        $lines.Add("    <ID>$id</ID>")
    }
    $lines.Add('  </CurrentIssues>')
    $lines.Add('</SmellBaseline>')
    Set-Content -LiteralPath $Path -Value $lines -Encoding utf8NoBOM
}

if ($Update -and -not $Reason) {
    # One line on purpose: assert-exit-contract.ps1 scans line by line, so an -ErrorAction sitting
    # on a continuation line reads as a bare terminating Write-Error and the exit 2 below is
    # reported unreachable (S2122).
    $msg = 'split-detekt-baseline: -Update requires -Reason. The view files are committed artifacts; an unexplained regeneration is unreviewable in a diff.'
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$categoriesPath = if ($CategoriesFile) { Resolve-RepoPath $CategoriesFile } else { Resolve-RepoPath 'config/detekt/rule-categories.txt' }
if (-not (Test-Path -LiteralPath $categoriesPath)) {
    Write-Error "split-detekt-baseline: cannot verify - category table not found: $categoriesPath" -ErrorAction Continue
    exit 2
}
$categories = Read-CategoryTable -Path $categoriesPath
if ($null -eq $categories) { exit 2 }

$modules = if ($PSBoundParameters.ContainsKey('Module')) { @($Module) } else { @('app_v2', 'wear') }

$anyDrift = $false
$cannotVerify = $false
$report = @()

foreach ($m in $modules) {
    $baselinePath = Resolve-RepoPath "config/detekt/baseline-$m.xml"
    $formatPath = Resolve-RepoPath "config/detekt/baseline-$m-format.xml"
    $signalPath = Resolve-RepoPath "config/detekt/baseline-$m-signal.xml"

    if (-not (Test-Path -LiteralPath $baselinePath)) {
        Write-Error "split-detekt-baseline: cannot verify - baseline not found: $baselinePath" -ErrorAction Continue
        $cannotVerify = $true
        continue
    }

    $baselineIds = Read-BaselineIds -Path $baselinePath
    if ($null -eq $baselineIds) { $cannotVerify = $true; continue }

    $unclassified = [System.Collections.Generic.SortedSet[string]]::new()
    $formatIds = [System.Collections.Generic.List[string]]::new()
    $signalIds = [System.Collections.Generic.List[string]]::new()
    foreach ($id in $baselineIds) {
        $rule = Get-RuleName -Raw $id
        if (-not $categories.ContainsKey($rule)) {
            [void]$unclassified.Add($rule)
            continue
        }
        if ($categories[$rule] -eq 'format') { $formatIds.Add($id) } else { $signalIds.Add($id) }
    }

    if ($unclassified.Count -gt 0) {
        Write-Error ("split-detekt-baseline: cannot verify [$m] - $($unclassified.Count) rule name(s) " +
            "absent from $categoriesPath : $($unclassified -join ', '). Add each to the table before " +
            're-running - refusing to guess a category.') -ErrorAction Continue
        $cannotVerify = $true
        continue
    }

    if ($Update) {
        Write-ViewFile -Path $formatPath -Ids $formatIds
        Write-ViewFile -Path $signalPath -Ids $signalIds
        Write-Host ("split-detekt-baseline: [$m] regenerated - format $($formatIds.Count), " +
            "signal $($signalIds.Count) (reason: $Reason).") -ForegroundColor Green
        $report += [pscustomobject]@{ Module = $m; FormatCount = $formatIds.Count; SignalCount = $signalIds.Count; Updated = $true }
        continue
    }

    $formatOk = Test-Path -LiteralPath $formatPath
    $signalOk = Test-Path -LiteralPath $signalPath
    if (-not $formatOk -or -not $signalOk) {
        Write-Error ("split-detekt-baseline: cannot verify [$m] - view file(s) missing. Seed with " +
            "-Module $m -Update -Reason '<why>'.") -ErrorAction Continue
        $cannotVerify = $true
        continue
    }

    $committedFormatIds = Read-BaselineIds -Path $formatPath
    $committedSignalIds = Read-BaselineIds -Path $signalPath
    if ($null -eq $committedFormatIds -or $null -eq $committedSignalIds) { $cannotVerify = $true; continue }

    $expectedFormatSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$formatIds)
    $expectedSignalSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$signalIds)
    $committedFormatSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$committedFormatIds)
    $committedSignalSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$committedSignalIds)

    $drift = (-not $expectedFormatSet.SetEquals($committedFormatSet)) -or (-not $expectedSignalSet.SetEquals($committedSignalSet))

    $report += [pscustomobject]@{
        Module      = $m
        FormatCount = $formatIds.Count
        SignalCount = $signalIds.Count
        Drift       = $drift
    }

    if ($drift) {
        $anyDrift = $true
        Write-Host ("split-detekt-baseline: DRIFT [$m] - committed view files do not match a fresh " +
            "regeneration from the operational baseline. Re-run with -Update -Reason '<why>'.") -ForegroundColor Red
    } else {
        Write-Host ("split-detekt-baseline: PASS [$m] - format $($formatIds.Count), signal " +
            "$($signalIds.Count) (baseline $($baselineIds.Count) total).") -ForegroundColor Green
    }
}

if ($Json) {
    $jsonPath = Resolve-RepoPath $Json
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM
    Write-Host "Written: $jsonPath"
}

if ($cannotVerify) { exit 2 }
if ($Update) { exit 0 }

if ($anyDrift) {
    if ($Gate) {
        $driftMsg = "split-detekt-baseline: FAIL - a committed view file is stale against the operational baseline; regenerate with -Update -Reason '<why>' on the module named above."
        Write-Error $driftMsg -ErrorAction Continue
        exit 1
    }
}

exit 0
