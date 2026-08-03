#requires -Version 7.0
<#
.SYNOPSIS
    Classify detekt-baseline entries that no longer match a fresh detekt run as DRIFTED (same rule
    still live in the same file, under a changed signature) or DEAD (no live finding of that rule
    remains in the file at all) - S1334.

.DESCRIPTION
    Read-only diagnostic. A detekt-baseline entry keys a suppression to the full text of the code
    element it froze (Rule:File$Class$snippet, or Rule:File$snippet for file-scoped rules). If that
    element's text changes shape (a parameter added, an import reordered), the entry silently stops
    matching and the finding it used to suppress resurfaces later against an unrelated file edit -
    see PLAN/S1334_browsestatesyncmanager-detekt-baseline-stale.md. This script never edits the
    baseline and never fails a build; it only prints classification.

    Baseline snippet text is detekt's PSI element text with whitespace runs collapsed to single
    spaces - it is NOT a byte-verbatim copy of the (often multi-line) source. The live file's text is
    normalized the same way before comparison, or every multi-line construct would false-positive as
    stale.

    Matching is a two-step diff against the baseline file, not a from-scratch detekt run:
      1. Snippet still found (whitespace-normalized) in the live file -> not stale, skip.
      2. Not found -> same (rule, file) pair present in the live report -> DRIFTED (the same rule is
         still live in this file, just under a shape this entry no longer covers).
      3. Not found, and no live report entry shares (rule, file) -> DEAD (prune candidate) - nothing
         under that rule is live in the file at all, so the original violation is most likely gone.
         Advisory only - a renamed-but-still-present symbol is a residual edge case worth a human
         glance before deleting the baseline line.

.PARAMETER BaselineFile
    Path to a detekt baseline XML file, relative to the repo root unless already absolute (default
    config/detekt/baseline-app_v2.xml).

.PARAMETER ReportFile
    Path to a detekt checkstyle-style XML report, relative to the repo root unless already absolute
    (default app_v2/build/reports/detekt/detekt.xml). Regenerate it first with a fresh
    `:app_v2:detekt --rerun-tasks` run - a stale cached report yields stale classification (see
    feedback_detekt_baseline_signature_resurface.md).

.PARAMETER All
    Also print entries that still match live source (default: only stale entries are printed).

.PARAMETER Json
    Optional path to write the full machine-readable result, relative to the repo root unless already
    absolute.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1 -BaselineFile config/detekt/baseline-wear.xml -ReportFile wear/build/reports/detekt/detekt.xml
#>
[CmdletBinding()]
param(
    [string]$BaselineFile = 'config/detekt/baseline-app_v2.xml',
    [string]$ReportFile = 'app_v2/build/reports/detekt/detekt.xml',
    [switch]$All,
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

function ConvertFrom-DetektEntities {
    param([string]$Text)
    return $Text.Replace('&lt;', '<').Replace('&gt;', '>').Replace('&quot;', '"').Replace('&apos;', "'").Replace('&amp;', '&')
}

function Get-NormalizedWhitespace {
    param([string]$Text)
    return ([regex]::Replace($Text, '\s+', ' ')).Trim()
}

function ConvertFrom-BaselineId {
    param([string]$Raw)

    $colonIdx = $Raw.IndexOf(':')
    if ($colonIdx -lt 0) { return $null }
    $rule = $Raw.Substring(0, $colonIdx)
    $afterRule = $Raw.Substring($colonIdx + 1)

    $fileDollarIdx = $afterRule.IndexOf('$')
    if ($fileDollarIdx -lt 0) { return $null }
    $file = $afterRule.Substring(0, $fileDollarIdx)
    $afterFile = $afterRule.Substring($fileDollarIdx + 1)

    # Class-scoped rules carry a second '$' separating class from snippet; file-scoped rules
    # (e.g. ImportOrdering) do not - the whole remainder IS the snippet. Detect by presence, not by
    # a fixed split count.
    $classDollarIdx = $afterFile.IndexOf('$')
    if ($classDollarIdx -ge 0) {
        $class = $afterFile.Substring(0, $classDollarIdx)
        $snippet = $afterFile.Substring($classDollarIdx + 1)
    } else {
        $class = ''
        $snippet = $afterFile
    }

    return [pscustomobject]@{
        Rule    = $rule
        File    = $file
        Class   = $class
        Snippet = Get-NormalizedWhitespace (ConvertFrom-DetektEntities $snippet)
    }
}

$baselinePath = Resolve-RepoPath $BaselineFile
$reportPath = Resolve-RepoPath $ReportFile

if (-not (Test-Path -LiteralPath $baselinePath)) {
    Write-Error "Baseline file not found: $baselinePath" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $reportPath)) {
    Write-Error "Report file not found: $reportPath - regenerate with a fresh detekt run first" -ErrorAction Continue
    exit 2
}

$scanRoot = if ($BaselineFile -match 'wear') {
    Join-Path $repoRoot 'wear/src'
} else {
    Join-Path $repoRoot 'app_v2/src'
}

# --- Parse baseline entries ---
$idLineRegex = [regex]'<ID>(?<id>.*?)</ID>'
$baselineEntries = @()
foreach ($line in Get-Content -LiteralPath $baselinePath) {
    $lineMatch = $idLineRegex.Match($line)
    if (-not $lineMatch.Success) { continue }
    $entry = ConvertFrom-BaselineId -Raw $lineMatch.Groups['id'].Value
    if ($null -ne $entry) { $baselineEntries += $entry }
}

# --- Parse live report entries (rule, file) pairs only - line/message are not needed for the diff) ---
# S1350: SelectNodes (not the .checkstyle.file dot-property shortcut) - a fully-clean report (zero
# <file> elements, e.g. right after a debt-clearing ticket lands) has no 'file' property at all under
# PowerShell's XML adapter, and Set-StrictMode -Version Latest throws "property 'file' cannot be
# found" on that dot access. SelectNodes always returns a (possibly empty) XmlNodeList, never throws.
[xml]$reportXml = Get-Content -LiteralPath $reportPath -Raw
$livePairs = [System.Collections.Generic.HashSet[string]]::new()
foreach ($fileNode in $reportXml.SelectNodes('//file')) {
    $fileName = Split-Path -Leaf $fileNode.name
    foreach ($errorNode in $fileNode.SelectNodes('error')) {
        $rule = $errorNode.source -replace '^detekt\.', ''
        [void]$livePairs.Add("$rule|$fileName")
    }
}

# --- Classify ---
$fileCache = @{}
$results = @()
foreach ($entry in $baselineEntries) {
    if (-not $fileCache.ContainsKey($entry.File)) {
        $fileCache[$entry.File] = @(Get-ChildItem -Path $scanRoot -Recurse -Filter $entry.File -File -ErrorAction SilentlyContinue)
    }
    $matches = $fileCache[$entry.File]

    if (-not $matches -or $matches.Count -eq 0) {
        $results += [pscustomobject]@{ Classification = 'DEAD (file removed)'; Rule = $entry.Rule; File = $entry.File; Class = $entry.Class }
        continue
    }

    $stillMatches = $false
    foreach ($fileMatch in $matches) {
        if (-not $entry.Snippet) { continue }
        $liveText = Get-NormalizedWhitespace (Get-Content -LiteralPath $fileMatch.FullName -Raw)
        if ($liveText.Contains($entry.Snippet)) { $stillMatches = $true; break }
    }

    if ($stillMatches) {
        if ($All) {
            $results += [pscustomobject]@{ Classification = 'OK (still matches)'; Rule = $entry.Rule; File = $entry.File; Class = $entry.Class }
        }
        continue
    }

    $pairKey = "$($entry.Rule)|$($entry.File)"
    if ($livePairs.Contains($pairKey)) {
        $results += [pscustomobject]@{ Classification = 'DRIFTED'; Rule = $entry.Rule; File = $entry.File; Class = $entry.Class }
    } else {
        $results += [pscustomobject]@{ Classification = 'DEAD (prune candidate)'; Rule = $entry.Rule; File = $entry.File; Class = $entry.Class }
    }
}

foreach ($r in $results) {
    Write-Host "$($r.Classification) | $($r.Rule) | $($r.File) | $($r.Class)"
}

Write-Host "---"
Write-Host "Baseline entries: $($baselineEntries.Count). Reported: $($results.Count)."

if ($Json) {
    $jsonPath = Resolve-RepoPath $Json
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $jsonPath
    Write-Host "Written: $jsonPath"
}

exit 0
