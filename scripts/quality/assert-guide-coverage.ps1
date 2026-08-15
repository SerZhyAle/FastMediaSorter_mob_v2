#requires -Version 7.0
<#
.SYNOPSIS
    Report shipped capabilities that no user guide mentions (S1653).

.DESCRIPTION
    Step 4 of the release campaign used to be a human reading five guides in three
    languages against docs/ALL_FEATURES.jsonl. It was the only optional step, so it
    was the first one skipped, and S1395 found seven shipped capabilities documented
    nowhere - one gap having accumulated across 20 inventory records and several
    releases. This makes that reading one call.

    Inputs, both resolved at run time rather than hardcoded:
      - capabilities: docs/ALL_FEATURES.jsonl (records with status 'active').
      - guides:       the 'user-guides' record in docs/DOCUMENT_REGISTRY.jsonl, whose
                      'paths' globs are expanded here. Only the English files are read;
                      the inventory is EN-only, so matching it against a Russian or
                      Ukrainian translation would compare two different vocabularies.

    Coverage signal. Guides are human prose and a capability 'name' is not, so an exact
    phrase match would report everything as missing. A record counts as mentioned when a
    MAJORITY of its name's content words occur somewhere in the guide corpus - at least
    half, rounded up, and never fewer than two once the name has two - each word folded to
    a crude stem so that "styling" and "style" are one token. Stopwords and words under
    three letters are dropped before the test.

    Both extremes were tried and rejected against the real corpus (spec section 11 asks for
    exactly this eyeball pass). Requiring ANY single word passes the whole inventory, since
    "player" and "video" appear in every guide - the false "no gaps" that section 7 warns
    about. Requiring EVERY word reported 252 of 697 records, and the sample was dominated by
    one abstract noun the prose had simply phrased differently: "Device profile
    auto-detection and selection" failed on "detection" alone while device profiles are
    documented at length. Prose paraphrases nominalizations and keeps the concrete subject,
    so a majority rule tracks what a human reviewer would call "mentioned".

    Flavor caveat (spec section 3): a record shipping only in noLegal is documented in the
    gitignored docs/FEATURES_noLegal*.md, never in the public guides, so it is skipped.

    Ratchet. Today's gap is wide, so a gate failing on all of it would be turned off on
    day one. scripts/quality/guide-coverage-baseline.txt lists the ids already known to be
    undocumented; only an id outside that list fails -Gate. Shrink the file as guides get
    written; never grow it without saying why in the commit.

    Exit codes (S1070):
      0 - clean, or audit mode (no -Gate).
      1 - -Gate and at least one uncovered id is not in the baseline.
      2 - the gate cannot run: the inventory, the registry, or the guide set is missing.
          Distinct from 1 on purpose - "did not look" is not "found nothing".

.PARAMETER Gate
    Fail-closed: exit 1 when an uncovered id is not in the baseline.

.PARAMETER UpdateBaseline
    Rewrite the baseline with the ids currently uncovered, then exit 0.

.PARAMETER Json
    Emit the full verdict as JSON instead of the text report.

.PARAMETER Quiet
    Print only the expected/actual summary line.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-guide-coverage.ps1
    pwsh -NoProfile -File scripts/quality/assert-guide-coverage.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-guide-coverage.ps1 -UpdateBaseline
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$Json,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$inventoryPath = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'
$registryPath = Join-Path $repoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
$baselinePath = Join-Path $PSScriptRoot 'guide-coverage-baseline.txt'

function Stop-CannotRun([string]$why) {
    Write-Error "assert-guide-coverage: cannot verify - $why" -ErrorAction Continue
    exit 2
}

if (-not (Test-Path $inventoryPath)) { Stop-CannotRun "docs/ALL_FEATURES.jsonl not found" }
if (-not (Test-Path $registryPath)) { Stop-CannotRun "docs/DOCUMENT_REGISTRY.jsonl not found" }

# Words that carry no capability meaning. Kept short on purpose: every word dropped here
# is one the coverage test stops demanding, so a long list quietly weakens the signal.
$stopWords = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]@(
        'and', 'the', 'for', 'with', 'from', 'into', 'over', 'per', 'via', 'its', 'that',
        'this', 'all', 'any', 'each', 'when', 'while', 'onto', 'off', 'out', 'not', 'you',
        'your', 'are', 'was', 'has', 'had', 'can', 'may', 'new', 'one', 'two', 'own', 'use'
    ),
    [System.StringComparer]::OrdinalIgnoreCase
)

# A crude stemmer, deliberately not a real one: it only has to make a guide's "styles" and an
# inventory name's "styling" the same token. Order matters - the longest suffix wins first.
function Get-Stem([string]$word) {
    $w = $word.ToLowerInvariant()
    if ($w.Length -gt 4 -and $w.EndsWith('ing')) { $w = $w.Substring(0, $w.Length - 3) }
    elseif ($w.Length -gt 4 -and $w.EndsWith('ies')) { $w = $w.Substring(0, $w.Length - 3) + 'y' }
    elseif ($w.Length -gt 4 -and $w.EndsWith('ed')) { $w = $w.Substring(0, $w.Length - 2) }
    elseif ($w.Length -gt 4 -and $w.EndsWith('es')) { $w = $w.Substring(0, $w.Length - 2) }
    elseif ($w.Length -gt 3 -and $w.EndsWith('s')) { $w = $w.Substring(0, $w.Length - 1) }
    if ($w.Length -gt 4 -and $w.EndsWith('e')) { $w = $w.Substring(0, $w.Length - 1) }
    return $w
}

function Get-ContentStems([string]$text) {
    $stems = [System.Collections.Generic.List[string]]::new()
    foreach ($m in [regex]::Matches($text, '[A-Za-z][A-Za-z0-9]*')) {
        $w = $m.Value
        if ($w.Length -lt 3) { continue }
        if ($stopWords.Contains($w)) { continue }
        $stems.Add((Get-Stem $w))
    }
    return $stems
}

# --- guides -----------------------------------------------------------------------------

$registryRecord = $null
foreach ($line in Get-Content $registryPath) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $record = $line | ConvertFrom-Json
    if ($record.id -eq 'user-guides') { $registryRecord = $record; break }
}
if (-not $registryRecord) { Stop-CannotRun "no 'user-guides' record in docs/DOCUMENT_REGISTRY.jsonl" }

$guideFiles = [System.Collections.Generic.List[string]]::new()
foreach ($glob in $registryRecord.paths) {
    $full = Join-Path $repoRoot ($glob -replace '/', [IO.Path]::DirectorySeparatorChar)
    foreach ($file in (Get-ChildItem -Path $full -File -ErrorAction SilentlyContinue)) {
        # The inventory is EN-only; a translated guide would be matched against the wrong vocabulary.
        if ($file.Name -match '_(RU|UK)\.md$') { continue }
        $guideFiles.Add($file.FullName)
    }
}
if ($guideFiles.Count -eq 0) { Stop-CannotRun "the 'user-guides' globs matched no English file" }

$corpus = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($file in $guideFiles) {
    foreach ($stem in (Get-ContentStems (Get-Content $file -Raw))) { [void]$corpus.Add($stem) }
}

# --- inventory --------------------------------------------------------------------------

$uncovered = [System.Collections.Generic.List[object]]::new()
$consideredCount = 0
$skippedCount = 0

foreach ($line in Get-Content $inventoryPath) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $record = $line | ConvertFrom-Json
    if ($record.status -ne 'active') { $skippedCount++; continue }
    if ($record.flavors -and @($record.flavors).Count -eq 1 -and $record.flavors[0] -eq 'noLegal') {
        $skippedCount++
        continue
    }
    # The tooling.* namespace holds developer machinery - detekt gates, the benchmark harness,
    # leak instrumentation. A user guide will never mention them and should not, so reporting
    # them would put six permanent entries in the baseline and teach the reader to ignore it.
    if ($record.id -like 'tooling.*') { $skippedCount++; continue }
    $consideredCount++
    # Unique: "Picture-in-Picture mode" folds to one repeated stem, and counting it twice
    # would raise the bar for a name that carries a single distinct word.
    $stems = @(Get-ContentStems $record.name | Select-Object -Unique)
    if ($stems.Count -eq 0) { continue }
    $missing = @($stems | Where-Object { -not $corpus.Contains($_) })
    $matched = $stems.Count - $missing.Count
    # Half the words, rounded up, and at least two once there are two to have.
    $needed = [math]::Max([math]::Ceiling($stems.Count / 2.0), [math]::Min(2, $stems.Count))
    if ($matched -lt $needed) {
        $uncovered.Add([pscustomobject]@{
            id      = $record.id
            area    = $record.area
            name    = $record.name
            matched = $matched
            needed  = [int]$needed
            missing = $missing
        })
    }
}

# --- baseline ---------------------------------------------------------------------------

if ($UpdateBaseline) {
    $header = @(
        '# Capability ids no user guide mentions yet (S1653).',
        '# Regenerate: pwsh -NoProfile -File scripts/quality/assert-guide-coverage.ps1 -UpdateBaseline',
        '# Shrink this file as guides get written. Growing it means a capability shipped undocumented.'
    )
    $body = $uncovered | ForEach-Object { $_.id } | Sort-Object
    Set-Content -Path $baselinePath -Value ($header + $body) -Encoding utf8NoBOM
    Write-Output "assert-guide-coverage: baseline rewritten with $($body.Count) id(s)."
    exit 0
}

$baseline = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
if (Test-Path $baselinePath) {
    foreach ($line in Get-Content $baselinePath) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith('#')) { [void]$baseline.Add($trimmed) }
    }
}

$fresh = @($uncovered | Where-Object { -not $baseline.Contains($_.id) })

if ($Json) {
    [pscustomobject]@{
        considered = $consideredCount
        skipped    = $skippedCount
        guides     = $guideFiles.Count
        uncovered  = $uncovered
        baselined  = $baseline.Count
        fresh      = $fresh
    } | ConvertTo-Json -Depth 5
    if ($Gate -and $fresh.Count -gt 0) { exit 1 }
    exit 0
}

if (-not $Quiet) {
    foreach ($entry in $fresh) {
        Write-Output ("  UNDOCUMENTED $($entry.id) - $($entry.name) [$($entry.area)] " +
            "(matched $($entry.matched)/$($entry.matched + $entry.missing.Count), need $($entry.needed); " +
            "missing: $($entry.missing -join ', '))")
    }
}
Write-Output ("assert-guide-coverage: expected: 0 | actual: $($fresh.Count) new undocumented capability(ies) " +
    "(considered $consideredCount, baselined $($baseline.Count), guides $($guideFiles.Count))")

if ($Gate -and $fresh.Count -gt 0) {
    Write-Error "assert-guide-coverage: $($fresh.Count) shipped capability(ies) appear in no user guide." -ErrorAction Continue
    exit 1
}

Write-Output 'assert-guide-coverage: PASS'
exit 0
