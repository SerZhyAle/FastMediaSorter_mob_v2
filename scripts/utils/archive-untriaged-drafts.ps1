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

    S4268: the expiry never drops a finding about a crash, a release or security. Those are the
    classes whose silent loss costs most - S4263 (a release build stripping the FGS dataSync
    permission, a likely crash of a store build on Android 14+) was parked and would have expired
    untouched. The class is read from the ticket name and its section 0 (the captured text) with
    one keyword pattern; a ticket that matches is reported as EXEMPT and stays in BlockQuestions
    until /spec-sweep Phase D promotes or archives it. A match is deliberately generous: a false
    hit only leaves one ticket for the sweep, a false miss silently loses a defect.

    Ran by a.ps1 before every runner start (r0-r3) and by /spec-sweep Phase D.

.PARAMETER Days
    Age in days, counted from the 'parked:' date, after which an untriaged ticket is archived.
    Default 14.

.PARAMETER List
    Print every parked ticket with its age and archive nothing. A crash, release or security
    finding is marked EXEMPT with the keyword that matched (S4268).

.PARAMETER RepoRoot
    Project root whose catalog is read. Defaults to this repository; the contract suite passes a
    fixture together with -ArchiveScript.

.PARAMETER ArchiveScript
    The archive entry point. Defaults to scripts/spec_catalog/archive.ps1; the suite passes a stub.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/archive-untriaged-drafts.ps1 -List

.NOTES
    Exit codes:
      0 - listed, or every expired non-exempt ticket archived (including none); exempt tickets
          past the deadline are named and left in place.
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

# S4268: crash, release and security vocabulary, English and Russian. A stem ending in \w* is
# prefix-matched; the rest are whole words. Deliberately generous - see .DESCRIPTION.
$SevereTopics = '(?i)\b(?:crash\w*|anr|oom|out[- ]of[- ]memory|fatal|force[- ]clos\w*|uncaught|' +
    'release|aab|signing|keystore|proguard|r8|play[- ]console|store[- ]build|' +
    'secur\w*|vulnerab\w*|exploit\w*|cve-\d+|secrets?|credentials?|' +
    'паден\w*|краш\w*|вылет\w*|релиз\w*|безопасн\w*|уязвим\w*)\b'

function Get-SevereTopic {
    param([string]$Name, [string]$Relative)
    $haystack = $Name
    if ($Relative) {
        $path = Join-Path $RepoRoot $Relative
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            try {
                $body = Get-Content -LiteralPath $path -Raw -Encoding UTF8
                # Section 0 only: the template's own boilerplate is not the finding.
                $inbox = [regex]::Match($body, '(?ms)^## 0\.[^\r\n]*\r?\n(.*?)(?=^## |^---\s*$|\z)')
                if ($inbox.Success) { $haystack += "`n" + $inbox.Groups[1].Value }
            }
            catch {
                # An unreadable spec is judged by its name alone; the expiry must not stall on one file.
                $haystack = $Name
            }
        }
    }
    $hit = [regex]::Match($haystack, $SevereTopics)
    if ($hit.Success) { return $hit.Value.ToLowerInvariant() }
    return ''
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
    $relative = if ($record.PSObject.Properties.Name -contains 'file') { [string]$record.file } else { '' }
    $parked += [pscustomobject]@{
        Id    = [string]$record.id
        Name  = [string]$record.name
        Age   = [int]($today - $parkedOn).TotalDays
        Topic = Get-SevereTopic -Name ([string]$record.name) -Relative $relative
    }
}
$parked = @($parked | Sort-Object -Property Id)
$pastDeadline = @($parked | Where-Object { $_.Age -ge $Days })
$expired = @($pastDeadline | Where-Object { -not $_.Topic })
$held = @($pastDeadline | Where-Object { $_.Topic })
$heldLine = if ($held.Count) {
    "$($held.Count) crash/release/security finding(s) past $Days day(s) kept for triage: $(($held | ForEach-Object { $_.Id }) -join ',')"
}
else { '' }

if ($List) {
    Write-Host "archive-untriaged-drafts: $($parked.Count) parked, $($expired.Count) past $Days day(s), $($held.Count) exempt past $Days day(s)."
    foreach ($p in $parked) {
        $flag = if ($p.Topic) { " - EXEMPT ($($p.Topic)): crash/release/security finding, triage it" }
        elseif ($p.Age -ge $Days) { ' - EXPIRED' }
        else { '' }
        Write-Host ("  {0} {1} - parked {2} day(s){3}" -f $p.Id, $p.Name, $p.Age, $flag)
    }
    exit 0
}

if ($heldLine) { Write-Host "archive-untriaged-drafts: $heldLine" }

if ($expired.Count -eq 0) {
    Write-Host "archive-untriaged-drafts: nothing to archive past $Days day(s) ($($parked.Count) parked)."
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
