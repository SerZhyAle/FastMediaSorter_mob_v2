#requires -Version 7.0
<#
.SYNOPSIS
    Holds the top level of temp/ to the inventory that declares it (S3030).

.DESCRIPTION
    Enumerates the top level of temp/ ONCE and resolves every entry to exactly one of four outcomes,
    reading scripts/utils/temp-root-inventory.ps1 as its only source of truth:

      declared-dir      - a directory the inventory names as fixed infrastructure.
      declared-file     - a file the inventory names, or one matching a fixed or retained pattern.
      ticket-scratch    - temp/Sxxxx/, a DIRECTORY whose name starts with a ticket id.
      unaccounted       - everything else. This is the only outcome that is reported.

    The ticket class matches directories only, deliberately. archive-temp.ps1 takes that branch
    inside its own container test, and a file called S2762_fw.txt is exactly the retired flat-scheme
    leftover this gate exists to surface - a pattern loose enough to admit it would pass over the
    files S3030 was opened to remove.

    Why this gate is release scope and not a per-change closure gate (Rule 33): between releases a
    stray file in a gitignored directory reaches no user; the subject is the directory rather than
    any changed file; each finding names its own location, so no attribution is needed; and clearing
    a batch costs no more than clearing one. The per-change placement fails Rule 33's attribution
    corollary outright - applied to one changed file it cannot tell a stray from a sibling session's
    lock file, and it would fail the closure of whoever happened to run it. That is not a
    prediction: S2998 widened a blacklist in this directory on 2026-09-11 after a sibling's gradle
    run failed a case, and S3025 was held by a Maestro trace the next day. The scope class is
    registered in scripts/quality/gate-placement.jsonl, which assert-gate-placement.ps1 checks
    against the wiring.

.PARAMETER Root
    Directory to judge. Default: temp/ at the repository root. The contract suite points this at a
    fixture so the gate never judges the root it is being tested on.

.PARAMETER Quiet
    Print only the verdict line. Findings are still printed - a silent failure is not a verdict.

.NOTES
    Exit codes:
      0 - every top-level entry is accounted for.
      1 - at least one unaccounted entry.
      2 - cannot verify: the root or the inventory library is missing.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = if ($env:FMS_REPO_ROOT) { $env:FMS_REPO_ROOT } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$inventoryLib = Join-Path $repoRoot 'scripts/utils/temp-root-inventory.ps1'
if (-not (Test-Path -LiteralPath $inventoryLib)) {
    Write-Error "assert-temp-root-inventory: inventory library not found at $inventoryLib" -ErrorAction Continue
    exit 2
}
. $inventoryLib

$target = if ($Root) { $Root } else { Join-Path $repoRoot 'temp' }
if (-not (Test-Path -LiteralPath $target -PathType Container)) {
    Write-Error "assert-temp-root-inventory: root not found at $target" -ErrorAction Continue
    exit 2
}

try {
    $inventory = Get-TempRootInventory -RepoRoot $repoRoot
} catch {
    Write-Error "assert-temp-root-inventory: the inventory could not be resolved - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$filePatterns = @($inventory.FixedFilePatterns) + @($inventory.RetainedFilePatterns)
$findings = [System.Collections.Generic.List[string]]::new()
$counts = [ordered]@{ 'declared-dir' = 0; 'declared-file' = 0; 'ticket-scratch' = 0 }

foreach ($entry in Get-ChildItem -LiteralPath $target -Force) {
    $name = $entry.Name

    if ($entry.PSIsContainer) {
        if ($inventory.FixedDirs -contains $name) { $counts['declared-dir']++; continue }
        # Judged by name only. Whether the ticket is still live is archive-temp.ps1's question,
        # because only a consumer that MOVES things needs to know; a scratch directory is a legal
        # inhabitant of the root either way.
        if ($name -match '^S\d{4}') { $counts['ticket-scratch']++; continue }
        $findings.Add("$name/ - unaccounted directory. Declare it in scripts/utils/temp-root-inventory.ps1 with the writer that creates it, or move its contents under temp/Sxxxx/ or temp/scratch/.")
        continue
    }

    if ($inventory.FixedFiles -contains $name) { $counts['declared-file']++; continue }
    $matched = $false
    foreach ($pattern in $filePatterns) {
        if ($name -like $pattern) { $matched = $true; break }
    }
    if ($matched) { $counts['declared-file']++; continue }
    # A ticket-shaped name earns the extra clause, because that shape is the one a reader is most
    # likely to believe is legal: temp/S3030/ is, temp/S3030_notes.txt never was.
    $hint = if ($name -match '^S\d{4}') {
        'a ticket-shaped FILE is not ticket scratch - only a DIRECTORY is'
    } else {
        'nothing in the repository writes this name'
    }
    $findings.Add("$name - unaccounted file ($hint). Name it in the inventory if a live script writes it, otherwise it is dead weight and Rule 10 puts it under temp/Sxxxx/ or temp/scratch/.")
}

$accounted = ($counts['declared-dir'] + $counts['declared-file'] + $counts['ticket-scratch'])
Write-Host "assert-temp-root-inventory: expected: 0 | actual: $($findings.Count) unaccounted entr$(if ($findings.Count -eq 1) { 'y' } else { 'ies' }) of $($accounted + $findings.Count) at $target"
if (-not $Quiet) {
    Write-Host "assert-temp-root-inventory: accounted - $($counts['declared-dir']) declared dir(s), $($counts['declared-file']) declared file(s), $($counts['ticket-scratch']) ticket scratch dir(s)"
}

if ($findings.Count -gt 0) {
    foreach ($finding in $findings) { Write-Host "  $finding" -ForegroundColor Yellow }
    Write-Error "assert-temp-root-inventory: FAIL - $($findings.Count) top-level entr$(if ($findings.Count -eq 1) { 'y is' } else { 'ies are' }) declared nowhere. CLAUDE.md Rule 10 keeps the root for fixed infrastructure; the inventory is the one place that says which names those are." -ErrorAction Continue
    exit 1
}

Write-Host 'assert-temp-root-inventory: PASS - every top-level entry is declared, patterned or ticket-bound.' -ForegroundColor Green
exit 0
