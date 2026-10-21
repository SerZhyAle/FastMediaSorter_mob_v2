#requires -Version 7.0
<#
.SYNOPSIS
    Open, mark, report and close a release campaign (S4413).

.DESCRIPTION
    A campaign is one release run of /release, from Step 0 to Step 8. Start writes the campaign
    record (a fresh id, the package, the start) to temp/metrics/release-campaign.json; Phase appends
    one line carrying that id to temp/metrics/release-campaign-phases.jsonl; Report prints every
    phase's start, end and duration; End closes the record. Gates, suites and sweep steps journal
    into the same file through Add-ReleaseCampaignPhase in lib/release-campaign.ps1, so the report
    shows every measured duration in one place.

    Outside a campaign Phase records nothing and still exits 0: the release commands call it at
    their boundaries, and a /spec-prerelease run on its own is not an error.

    FMS_RELEASE_CAMPAIGN_ROOT overrides the directory both files live in (the contract suite
    sandboxes it).

.PARAMETER Verb
    Start  - open a campaign for -Package.
    Status - print the open campaign.
    Phase  - journal one -Name / -State event into the open campaign.
    Report - per phase: start, end, duration, for the open campaign or the last closed one.
    End    - close the open campaign.

.PARAMETER Package
    Start only. The release package number (the current-next-release marker).

.PARAMETER Force
    Start only. Open a new campaign even though one is open; the old id is recorded as replaced.

.PARAMETER Name
    Phase only. The phase name, e.g. sweep, content, device, documentation, publish, distribution.

.PARAMETER State
    Phase only. begin, end or point.

.PARAMETER ElapsedMs
    Phase only, optional. A duration the caller measured itself.

.PARAMETER ExitCode
    Phase only, optional. The exit code of what the phase ran.

.PARAMETER Detail
    Phase only, optional. One free-text line stored with the event.

.PARAMETER Json
    Status and Report. Emit one JSON document instead of prose.

.PARAMETER Help
    Print this help and exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/release-campaign.ps1 -Verb Start -Package 47

.EXAMPLE
    pwsh -NoProfile -File scripts/release/release-campaign.ps1 -Verb Phase -Name sweep -State begin

.EXAMPLE
    pwsh -NoProfile -File scripts/release/release-campaign.ps1 -Verb Report -Json

Exit codes (CLAUDE.md Rule 7):
  0  the verb succeeded (Phase also when no campaign is open - nothing recorded).
  2  unusable arguments, or the campaign record exists but could not be read or written.
  3  Status / End: no campaign is open. Report: no campaign record exists.
  4  Start refused: a campaign is already open (pass -Force to replace it).
#>

[CmdletBinding()]
param(
    [ValidateSet('Start', 'Status', 'Phase', 'Report', 'End')]
    [string] $Verb,

    [int] $Package = 0,

    [switch] $Force,

    [string] $Name = '',

    [ValidateSet('begin', 'end', 'point')]
    [string] $State = '',

    [long] $ElapsedMs = -1,

    [int] $ExitCode = -1,

    [string] $Detail = '',

    [switch] $Json,

    [switch] $Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $Verb) {
    Write-Host 'release-campaign: -Verb is required (Start, Status, Phase, Report, End); -Help prints the usage.' -ForegroundColor Red
    exit 2
}

. (Join-Path $PSScriptRoot 'lib\release-campaign.ps1')

function Read-RecordOrExit {
    try { return Read-ReleaseCampaignRecord }
    catch {
        Write-Host "release-campaign: record unreadable - $(Get-ReleaseCampaignRecordPath): $($_.Exception.Message)" -ForegroundColor Red
        exit 2
    }
}

function Format-Duration($Ms) {
    if ($null -eq $Ms) { return '-' }
    return ([TimeSpan]::FromMilliseconds([double]$Ms)).ToString('hh\:mm\:ss')
}

switch ($Verb) {

    'Start' {
        if ($Package -le 0) {
            Write-Host 'release-campaign: Start needs -Package <N> (the current-next-release number).' -ForegroundColor Red
            exit 2
        }
        $record = Read-RecordOrExit
        if ($record -and [string]$record.state -eq 'open' -and -not $Force) {
            Write-Host "release-campaign: REFUSED - campaign $($record.id) (package $($record.package)) is open since $($record.startedAt). End it, or pass -Force." -ForegroundColor Red
            exit 4
        }
        try { $new = Start-ReleaseCampaign -Package $Package -Force:$Force }
        catch {
            Write-Host "release-campaign: could not write the record - $($_.Exception.Message)" -ForegroundColor Red
            exit 2
        }
        Write-Host "release-campaign: started $($new.id) for package $Package." -ForegroundColor Green
        exit 0
    }

    'Status' {
        $record = Read-RecordOrExit
        if ($null -eq $record -or [string]$record.state -ne 'open') {
            if ($Json) { Write-Output (@{ open = $false } | ConvertTo-Json -Compress) }
            else { Write-Host 'release-campaign: no campaign open.' -ForegroundColor Yellow }
            exit 3
        }
        if ($Json) { Write-Output ($record | ConvertTo-Json -Depth 4 -Compress) }
        else { Write-Host "release-campaign: OPEN $($record.id), package $($record.package), since $($record.startedAt)." -ForegroundColor Cyan }
        exit 0
    }

    'Phase' {
        if ([string]::IsNullOrWhiteSpace($Name) -or -not $State) {
            Write-Host 'release-campaign: Phase needs -Name <phase> and -State begin|end|point.' -ForegroundColor Red
            exit 2
        }
        $open = Read-RecordOrExit
        if ($null -eq $open -or [string]$open.state -ne 'open') {
            Write-Host "release-campaign: no campaign open - phase '$Name' $State not recorded." -ForegroundColor DarkGray
            exit 0
        }
        $phaseArgs = @{ Name = $Name; State = $State; Detail = $Detail }
        if ($ElapsedMs -ge 0) { $phaseArgs.ElapsedMs = $ElapsedMs }
        if ($ExitCode -ge 0) { $phaseArgs.ExitCode = $ExitCode }
        Add-ReleaseCampaignPhase @phaseArgs
        Write-Host "release-campaign: $($open.id) phase '$Name' $State." -ForegroundColor Green
        exit 0
    }

    'Report' {
        $record = Read-RecordOrExit
        if ($null -eq $record) {
            Write-Host 'release-campaign: no campaign record - nothing to report.' -ForegroundColor Yellow
            exit 3
        }
        $rows = Get-ReleaseCampaignReport -CampaignId ([string]$record.id)
        if ($Json) {
            $payload = [ordered]@{
                id        = [string]$record.id
                package   = $record.package
                state     = [string]$record.state
                startedAt = [string]$record.startedAt
                endedAt   = if ($record.endedAt) { [string]$record.endedAt } else { '' }
                phases    = @($rows)
            }
            Write-Output ($payload | ConvertTo-Json -Depth 5)
            exit 0
        }
        Write-Host "release-campaign: $($record.id), package $($record.package), $($record.state), started $($record.startedAt)" -ForegroundColor Cyan
        if ($rows.Count -eq 0) { Write-Host '  no phases journalled.' -ForegroundColor DarkGray }
        foreach ($row in $rows) {
            $code = if ($null -ne $row.exitCode) { " exit $($row.exitCode)" } else { '' }
            Write-Host ("  {0,-24} {1,-34} {2,-34} {3}{4}" -f $row.name, $row.start, $row.end, (Format-Duration $row.durationMs), $code)
        }
        exit 0
    }

    'End' {
        $record = Read-RecordOrExit
        if ($null -eq $record -or [string]$record.state -ne 'open') {
            Write-Host 'release-campaign: no campaign open - nothing to end.' -ForegroundColor Yellow
            exit 3
        }
        try { $closed = Stop-ReleaseCampaign }
        catch {
            Write-Host "release-campaign: could not write the record - $($_.Exception.Message)" -ForegroundColor Red
            exit 2
        }
        Write-Host "release-campaign: ended $($closed.id) at $($closed.endedAt)." -ForegroundColor Green
        exit 0
    }
}
