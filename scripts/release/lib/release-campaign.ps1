#requires -Version 7.0
<#
.SYNOPSIS
    Release-campaign record and append-only phase journal (S4413), dot-sourced by the CLI and by
    every gate, suite or sweep step that reports its duration into a campaign.

.DESCRIPTION
    A campaign is one release, from /release Step 0 to Step 8. Its record (id, package, start, end)
    lives in temp/metrics/release-campaign.json; its phase journal is one JSON line per event in
    temp/metrics/release-campaign-phases.jsonl, each line carrying the campaign id. Before this, a
    campaign's timeline could only be rebuilt from file timestamps after the fact.

    Get-ActiveReleaseCampaign and Add-ReleaseCampaignPhase are the two entry points consumers use.
    Both are no-ops without an open campaign, so a consumer outside a campaign keeps its old
    behaviour exactly, and a journal write that fails warns instead of failing the caller - the
    journal measures a gate, it never decides one.

    FMS_RELEASE_CAMPAIGN_ROOT overrides the directory both files live in; the contract suite points
    it at a per-PID fixture directory.

    Exit codes: none - this is a library; it returns values and throws, the CLI maps them to codes.
#>

function Get-ReleaseCampaignRoot {
    if ($env:FMS_RELEASE_CAMPAIGN_ROOT) { return $env:FMS_RELEASE_CAMPAIGN_ROOT }
    $repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return (Join-Path $repo 'temp\metrics')
}

function Get-ReleaseCampaignRecordPath { return (Join-Path (Get-ReleaseCampaignRoot) 'release-campaign.json') }

function Get-ReleaseCampaignJournalPath { return (Join-Path (Get-ReleaseCampaignRoot) 'release-campaign-phases.jsonl') }

function ConvertTo-ReleaseCampaignUnixMs([datetime] $Value) {
    return [DateTimeOffset]::new($Value.ToUniversalTime(), [TimeSpan]::Zero).ToUnixTimeMilliseconds()
}

# Returns the record on disk whatever its state, $null when there is none, and throws when the file
# exists but does not parse - the CLI must tell "no campaign" from "a campaign it cannot read".
function Read-ReleaseCampaignRecord {
    $path = Get-ReleaseCampaignRecordPath
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $text = [System.IO.File]::ReadAllText($path)
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return ($text | ConvertFrom-Json -DateKind String)
}

function Write-ReleaseCampaignRecord($Record) {
    $path = Get-ReleaseCampaignRecordPath
    $dir = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $tmp = "$path.tmp"
    [System.IO.File]::WriteAllText($tmp, ($Record | ConvertTo-Json -Depth 4), [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $tmp -Destination $path -Force
}

function Get-ActiveReleaseCampaign {
    $record = $null
    try { $record = Read-ReleaseCampaignRecord }
    catch {
        Write-Verbose "release-campaign: record unreadable, treated as no open campaign - $($_.Exception.Message)"
        return $null
    }
    if ($null -eq $record) { return $null }
    if ([string]$record.state -ne 'open') { return $null }
    return $record
}

function Start-ReleaseCampaign {
    param(
        [Parameter(Mandatory)][int] $Package,
        [switch] $Force
    )
    $open = Get-ActiveReleaseCampaign
    if ($open -and -not $Force) {
        throw [System.InvalidOperationException]::new("campaign $($open.id) is already open")
    }
    $now = Get-Date
    # The guid fragment is what makes two campaigns started in the same second distinct: the pass
    # cache keys on this id, and a key shared by two campaigns would carry a pass across them.
    $id = 'r{0}-{1}-{2}' -f $Package, $now.ToString('yyMMddHHmmss'), [guid]::NewGuid().ToString('n').Substring(0, 6)
    $record = [ordered]@{
        schema      = 1
        id          = $id
        package     = $Package
        state       = 'open'
        startedAt   = $now.ToString('o')
        startedAtMs = ConvertTo-ReleaseCampaignUnixMs $now
        endedAt     = $null
        endedAtMs   = $null
        replaced    = if ($open) { [string]$open.id } else { $null }
    }
    Write-ReleaseCampaignRecord $record
    return [pscustomobject]$record
}

function Stop-ReleaseCampaign {
    $open = Get-ActiveReleaseCampaign
    if ($null -eq $open) { return $null }
    $now = Get-Date
    $open.state = 'closed'
    $open.endedAt = $now.ToString('o')
    $open.endedAtMs = ConvertTo-ReleaseCampaignUnixMs $now
    Write-ReleaseCampaignRecord $open
    return $open
}

function Add-ReleaseCampaignPhase {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][ValidateSet('begin', 'end', 'point')][string] $State,
        [Nullable[long]] $ElapsedMs = $null,
        [Nullable[int]] $ExitCode = $null,
        [string] $Detail = ''
    )
    $open = Get-ActiveReleaseCampaign
    if ($null -eq $open) { return }
    $now = Get-Date
    $line = [ordered]@{
        campaignId = [string]$open.id
        name       = $Name
        state      = $State
        at         = $now.ToString('o')
        atMs       = ConvertTo-ReleaseCampaignUnixMs $now
    }
    if ($null -ne $ElapsedMs) { $line.elapsedMs = [long]$ElapsedMs }
    if ($null -ne $ExitCode) { $line.exitCode = [int]$ExitCode }
    if ($Detail) { $line.detail = $Detail }
    try {
        $path = Get-ReleaseCampaignJournalPath
        $dir = Split-Path -Parent $path
        if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        [System.IO.File]::AppendAllText($path, (($line | ConvertTo-Json -Compress -Depth 4) + "`n"), [System.Text.UTF8Encoding]::new($false))
    }
    catch {
        Write-Warning "release-campaign: phase '$Name' $State not journalled - $($_.Exception.Message)"
    }
}

function Get-ReleaseCampaignPhases {
    param([Parameter(Mandatory)][string] $CampaignId)
    $path = Get-ReleaseCampaignJournalPath
    if (-not (Test-Path -LiteralPath $path)) { return @() }
    $rows = foreach ($text in [System.IO.File]::ReadAllLines($path)) {
        if ([string]::IsNullOrWhiteSpace($text)) { continue }
        $row = $null
        try { $row = $text | ConvertFrom-Json -DateKind String }
        catch {
            Write-Verbose "release-campaign: skipped an unparseable journal line - $($_.Exception.Message)"
            continue
        }
        if ([string]$row.campaignId -eq $CampaignId) { $row }
    }
    return @($rows)
}

# One row per phase name, in order of first appearance: the first begin, the last end, and the
# duration between them; a phase recorded only as points or ends with elapsedMs sums those instead.
function Get-ReleaseCampaignReport {
    param([Parameter(Mandatory)][string] $CampaignId)
    $phases = Get-ReleaseCampaignPhases -CampaignId $CampaignId
    $order = [System.Collections.Generic.List[string]]::new()
    foreach ($row in $phases) { if (-not $order.Contains([string]$row.name)) { $order.Add([string]$row.name) } }
    $report = foreach ($name in $order) {
        $mine = @($phases | Where-Object { [string]$_.name -eq $name })
        $begin = $mine | Where-Object { $_.state -eq 'begin' } | Select-Object -First 1
        $end = $mine | Where-Object { $_.state -eq 'end' } | Select-Object -Last 1
        $duration = $null
        if ($begin -and $end) { $duration = [long]$end.atMs - [long]$begin.atMs }
        else {
            $timed = @($mine | Where-Object { $_.PSObject.Properties.Name -contains 'elapsedMs' })
            if ($timed.Count -gt 0) { $duration = [long](($timed | Measure-Object -Property elapsedMs -Sum).Sum) }
        }
        $coded = @($mine | Where-Object { $_.PSObject.Properties.Name -contains 'exitCode' })
        [pscustomobject][ordered]@{
            name       = $name
            start      = if ($begin) { [string]$begin.at } else { [string]$mine[0].at }
            end        = if ($end) { [string]$end.at } else { '' }
            durationMs = $duration
            exitCode   = if ($coded.Count -gt 0) { [int]$coded[-1].exitCode } else { $null }
            events     = $mine.Count
        }
    }
    return @($report)
}
