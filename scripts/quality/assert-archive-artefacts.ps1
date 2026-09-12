#requires -Version 7.0
<#
.SYNOPSIS
    S2592: every record in the spec archive journal must point at a file that exists, or be a
    listed known loss.

.DESCRIPTION
    An Archived ticket is the only durable record of WHY a closed decision was made, and live
    tickets cite it - S1728's "watch on its own Wi-Fi or relayed through the phone" was cited by
    S1699, S2068 and again by S2551. The catalog record survives independently of its file, so a
    lost artefact is invisible: `select.ps1 -Id Sxxxx` answers, the `file` field is populated, and
    the absence shows only when someone opens the path. That is how 1893 of 2283 records came to
    dangle for three weeks without anyone noticing (measured 2026-09-05).

    This gate reads PLAN/spec-catalog-archive.jsonl, resolves every `file` against the repository
    root, and fails on any that does not resolve unless the id is listed in
    scripts/quality/archive-artefacts-baseline.txt.

    A baseline line whose record now resolves also fails. The fix is deleting that one line, and
    it can only go stale when a human restored the file - a baseline that only ever grows stops
    measuring anything.

    Rule 33 places this in RELEASE scope. Its subject is the whole archive journal against the
    whole archive directory: no changed file can be blamed for a dangling record, a lost spec text
    reaches no user between releases, every finding names its own id and path, and repairing them
    is one batch whenever it is done. Runner: scripts/quality/assert-release-scope-gates.ps1.

.PARAMETER Gate
    Accepted for the release-scope runner, which passes it to every child. This gate has no
    advisory mode - a dangling record is a defect at any callsite - so the switch changes nothing.

.PARAMETER Quiet
    Print only the verdict line, not the per-record findings.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-archive-artefacts.ps1

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every archive record resolves, or is a listed known loss, and no baseline line is stale.
      1 - at least one record dangles unlisted, or a listed record has come back.
      2 - could not verify: the archive journal or the baseline file is absent.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$journal = Join-Path $repoRoot 'PLAN\spec-catalog-archive.jsonl'
$baselineFile = Join-Path $PSScriptRoot 'archive-artefacts-baseline.txt'

Write-Host 'assert-archive-artefacts: spec archive journal vs PLAN/archive (module: none - repository scope)'

foreach ($required in @($journal, $baselineFile)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "assert-archive-artefacts: cannot verify - not found: $required" -ErrorAction Continue
        exit 2
    }
}

$baseline = @{}
foreach ($line in (Get-Content -LiteralPath $baselineFile -Encoding utf8)) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $id = ($trimmed -split '\s+')[0]
    if ($id) { $baseline[$id] = $trimmed }
}

$records = @(Get-Content -LiteralPath $journal -Encoding utf8 |
    Where-Object { $_.Trim() -ne '' } | ForEach-Object { $_ | ConvertFrom-Json })

$dangling = [System.Collections.Generic.List[object]]::new()
$resolvedIds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($r in $records) {
    $rel = [string]$r.file
    if ($rel -and (Test-Path -LiteralPath (Join-Path $repoRoot ($rel -replace '/', '\')))) {
        [void]$resolvedIds.Add([string]$r.id)
    }
    else {
        $dangling.Add($r)
    }
}

$unlisted = @($dangling | Where-Object { -not $baseline.ContainsKey([string]$_.id) })
$stale = @($baseline.Keys | Where-Object { $resolvedIds.Contains($_) } | Sort-Object)

if (-not $Quiet) {
    foreach ($r in ($unlisted | Sort-Object { [string]$_.id })) {
        Write-Host ("  DANGLING  {0}  {1}" -f $r.id, $r.file) -ForegroundColor Red
    }
    foreach ($id in $stale) {
        Write-Host ("  STALE BASELINE  {0} - the file is back; delete this line" -f $id) -ForegroundColor Yellow
    }
}

Write-Host ("  records: {0}   resolved: {1}   known loss: {2}" -f `
    $records.Count, $resolvedIds.Count, $baseline.Count)

if ($unlisted.Count -gt 0 -or $stale.Count -gt 0) {
    Write-Error ("assert-archive-artefacts: FAIL - {0} unlisted dangling record(s), {1} stale baseline line(s). A fresh dangling record may still be recoverable: pwsh -NoProfile -File scripts/utils/recover-archived-specs.ps1 -DryRun" -f `
        $unlisted.Count, $stale.Count) -ErrorAction Continue
    exit 1
}

Write-Host 'assert-archive-artefacts: PASS' -ForegroundColor Green
exit 0
