#requires -Version 7.0
<#
.SYNOPSIS
    S4190: list, and archive past their deadline, the agent-parked findings nobody triaged.

.DESCRIPTION
    An out-of-scope finding an agent parks through /spec-draft (CLAUDE.md 3.1) is captured with
    capture-draft.ps1 -Parked, which leaves it in BlockQuestions with a status note carrying the
    marker 'Parked finding, untriaged' and the date 'parked: yy-MM-dd'. BlockQuestions is outside the
    runner's eligible statuses, so the ticket waits for /spec-sweep Phase D instead of being picked
    within minutes - measured 2026-10-13, three such drafts were attempted by the runner within half an
    hour of capture, and 27% of the 1745 tickets created in September left no dev-log row at all.

    Triage either promotes a ticket (a '**Triage:**' line, then back to Draft - which clears the note
    and so the marker) or archives it. This script owns the third outcome: a parked ticket still
    carrying the marker past -Days is archived through archive.ps1. The archive moves the spec to
    PLAN/archive/, so the captured text stays on disk.

    Ran by a.ps1 before every runner start (r0-r3) and by /spec-sweep Phase D.

.PARAMETER Days
    Age in days, counted from the 'parked:' date, after which an untriaged ticket is archived.
    Default 14.

.PARAMETER List
    Print every parked ticket with its age and archive nothing.

.PARAMETER RepoRoot
    Project root whose catalog is read. Defaults to this repository; the contract suite passes a
    fixture together with -ArchiveScript.

.PARAMETER ArchiveScript
    The archive entry point. Defaults to scripts/spec_catalog/archive.ps1; the suite passes a stub.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/archive-untriaged-drafts.ps1 -List

.NOTES
    Exit codes:
      0 - listed, or every expired ticket archived (including none).
      1 - archive.ps1 refused at least one id; its output is printed.
      2 - could not verify: the catalog journal is absent or unreadable.
#>
[CmdletBinding()]
param(
    [int]$Days = 14,
    [switch]$List,
    [string]$RepoRoot,
    [string]$ArchiveScript
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
if (-not $ArchiveScript) { $ArchiveScript = Join-Path $RepoRoot 'scripts/spec_catalog/archive.ps1' }

$Marker = 'Parked finding, untriaged'
$catalogPath = Join-Path $RepoRoot 'PLAN/spec-catalog.jsonl'
if (-not (Test-Path -LiteralPath $catalogPath)) {
    Write-Host "archive-untriaged-drafts: COULD NOT VERIFY - $catalogPath is absent."
    exit 2
}

$latest = @{}
try {
    foreach ($line in (Get-Content -LiteralPath $catalogPath -Encoding UTF8)) {
        if (-not $line.Trim()) { continue }
        $record = $line | ConvertFrom-Json
        if ($record.id) { $latest[[string]$record.id] = $record }
    }
}
catch {
    Write-Host "archive-untriaged-drafts: COULD NOT VERIFY - $catalogPath is unreadable: $($_.Exception.Message)"
    exit 2
}

$today = (Get-Date).Date
$parked = @()
foreach ($record in $latest.Values) {
    if ([string]$record.status -ne 'BlockQuestions') { continue }
    $note = [string]$record.statusNote
    if (-not $note.Contains($Marker)) { continue }
    $m = [regex]::Match($note, 'parked: (\d{2})-(\d{2})-(\d{2})')
    $parkedOn = if ($m.Success) {
        [datetime]::new(2000 + [int]$m.Groups[1].Value, [int]$m.Groups[2].Value, [int]$m.Groups[3].Value)
    }
    elseif ($record.created) { ([datetime]$record.created).Date }
    else { $today }
    $parked += [pscustomobject]@{ Id = [string]$record.id; Name = [string]$record.name; Age = [int]($today - $parkedOn).TotalDays }
}
$parked = @($parked | Sort-Object -Property Id)
$expired = @($parked | Where-Object { $_.Age -ge $Days })

if ($List) {
    Write-Host "archive-untriaged-drafts: $($parked.Count) parked, $($expired.Count) past $Days day(s)."
    foreach ($p in $parked) {
        $flag = if ($p.Age -ge $Days) { ' - EXPIRED' } else { '' }
        Write-Host ("  {0} {1} - parked {2} day(s){3}" -f $p.Id, $p.Name, $p.Age, $flag)
    }
    exit 0
}

if ($expired.Count -eq 0) {
    Write-Host "archive-untriaged-drafts: nothing past $Days day(s) ($($parked.Count) parked)."
    exit 0
}

$ids = ($expired | ForEach-Object { $_.Id }) -join ','
$out = & pwsh -NoProfile -File $ArchiveScript -Id $ids 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host "archive-untriaged-drafts: archive.ps1 refused (exit $LASTEXITCODE) for $ids"
    Write-Host $out.Trim()
    exit 1
}
Write-Host "archive-untriaged-drafts: archived $($expired.Count) untriaged parked ticket(s) past $Days day(s): $ids"
exit 0
