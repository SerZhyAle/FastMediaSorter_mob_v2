#requires -Version 7.0
<#
.SYNOPSIS
    S2037: no self-cost estimate column (`Line budget`) in planning output.

.DESCRIPTION
    CLAUDE.md section 1 ("No self-cost estimates anywhere in planning output", owner
    ruling 2026-08-25) names the `Line budget` column of a phase file's Files Touched
    table as banned outright. The ban had nothing mechanical behind it: the column is in
    no template and in no script, so `/spec-tech` reproduced it from habit and nothing
    failed - S1954's tactical plan carried it in all four phase files, written the same
    day the rule came into force. Measured 2026-09-05: 49 live phase files carried it.

    Why the numbers are worth removing rather than improving: they feed no budget, and
    they are measurably wrong. S1954's PHASE_03 budgeted `<= 620` lines for a file that
    measured 621 after the change, the same class of miss CLAUDE.md already records for
    S1431 (three phases off by 4-23 lines). A size read off disk is untouched by this:
    Rule 2's 2000-line ceiling and Rule 5's 500-line backup threshold measure a real
    file, they do not predict one.

    Judged per TABLE CELL, never per line. The ban is about a column, and the sentence
    that names the column is legitimate prose - CLAUDE.md section 1 quotes it, and so
    does S2037's own captured-material section. A line-contains check would make the rule's
    own statement its first offender, which is how a gate gets switched off. So a finding
    is a markdown table row (trimmed, opens and closes with `|`) carrying a cell whose
    text is one of the forbidden headers below.

    Absolute, not a count ratchet, unlike assert-tactical-step-form.ps1 over the same
    corpus. That gate ratchets because hundreds of phase files predate the `**Why:**`
    form and cannot be brought to zero; here the corpus sweep in S2037 zeroed the live
    files in the same ticket, so a baseline would always read 0 - one more file to keep,
    and one more way to bury a regression under -UpdateBaseline.

    Scope is live planning output plus the skeletons that generate it: PLAN/**.md and
    .claude/templates/*.md. PLAN/archive/ is excluded on purpose (S2037 ADR-2): an
    archived phase file is the record of what a closed ticket planned, and rewriting it
    erases that record to satisfy a form nobody reproduces from there.

    Exit codes (S1070):
      0 - clean, or audit mode (findings printed, no -Gate).
      1 - substantive failure: at least one forbidden column, and -Gate was passed.
      2 - the gate itself cannot run (PLAN/ missing or unreadable). Distinct from 1
          on purpose: "did not look" and "found a defect" call for opposite reactions.

.PARAMETER Gate
    Fail-closed: exit 1 when a forbidden column is found.

.PARAMETER Quiet
    Print only the expected/actual summary, not the per-finding list.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-line-budget.ps1
    pwsh -NoProfile -File scripts/quality/assert-no-line-budget.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$planRoot = Join-Path $repoRoot 'PLAN'
$templateRoot = Join-Path $repoRoot '.claude/templates'

if (-not (Test-Path -LiteralPath $planRoot)) {
    Write-Error "PLAN/ not found at $planRoot" -ErrorAction Continue
    exit 2
}

# Every spelling of "how many lines will this cost" seen or plausible in a Files Touched
# table. A synonym is one row here, which is the extensibility point: the ban is on the
# estimate, not on the literal two words, and renaming the column must not defeat it.
$forbiddenHeaders = @(
    'line budget',
    'line budgets',
    'lines budget',
    'loc budget',
    'budget (loc)',
    'budget, loc',
    'line estimate',
    'estimated loc',
    'estimated lines',
    'est. loc',
    'est loc'
)

# Backticks and emphasis markers around a header cell are cosmetic, so `Line budget` and
# **Line budget** are the same column as Line budget.
function Get-NormalizedCell {
    param([string]$Cell)
    return ($Cell -replace '[`*_]', '').Trim().ToLowerInvariant()
}

function Get-OffendingRows {
    param([string[]]$Lines, [string]$RelPath)

    $found = [System.Collections.Generic.List[string]]::new()
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        $trimmed = $Lines[$i].Trim()
        if ($trimmed.Length -lt 2 -or -not $trimmed.StartsWith('|') -or -not $trimmed.EndsWith('|')) { continue }
        foreach ($cell in ($trimmed.Trim('|') -split '\|')) {
            $normalized = Get-NormalizedCell -Cell $cell
            if ($forbiddenHeaders -contains $normalized) {
                $found.Add("${RelPath}:$($i + 1): column '$($cell.Trim())'")
            }
        }
    }
    return , $found
}

$planFiles = @(Get-ChildItem -LiteralPath $planRoot -Recurse -Filter '*.md' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/]archive[\\/]' })
$templateFiles = @(Get-ChildItem -LiteralPath $templateRoot -Filter '*.md' -File -ErrorAction SilentlyContinue)
$files = @($planFiles) + @($templateFiles)

$findings = [System.Collections.Generic.List[string]]::new()
foreach ($file in $files) {
    $rel = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
    foreach ($f in (Get-OffendingRows -Lines @(Get-Content -LiteralPath $file.FullName) -RelPath $rel)) {
        $findings.Add($f)
    }
}
$actual = $findings.Count

Write-Host "assert-no-line-budget: scanned $($files.Count) planning file(s) under PLAN/ (archive excluded) and .claude/templates/."
if (-not $Quiet -and $actual -gt 0) {
    foreach ($f in $findings) { Write-Host "  $f" }
    Write-Host '  fix: delete the column from the table (header, separator and every row cell).'
    Write-Host '  CLAUDE.md section 1 - describe what a change IS, never what it will COST.'
}
Write-Host "expected: 0 cost-estimate column(s) | actual: $actual"

if ($actual -gt 0 -and $Gate) {
    Write-Host 'assert-no-line-budget: FAIL' -ForegroundColor Red
    exit 1
}
Write-Host 'assert-no-line-budget: PASS' -ForegroundColor Green
exit 0
