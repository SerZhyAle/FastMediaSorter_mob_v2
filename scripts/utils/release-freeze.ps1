#requires -Version 7.0
<#
.SYNOPSIS
    Take, report and release the release-sweep tree freeze (S3010).

.DESCRIPTION
    A release sweep judges the repository as a whole - every gate at /spec-prerelease steps 0.4 and
    0.8 measures the tree rather than a change (CLAUDE.md Rule 33). Nothing stopped sibling sessions
    writing to that tree while the sweep ran, so a gate cleared at the start was red again by the end
    and the sweep had no terminating condition. Measured over the r37 sweep on 2026-09-12: 33
    changelog rows landed between the sweep's first and last write, and exactly 2 of them were the
    sweep's own.

    This is NOT a sixth lock domain. A domain is mutual exclusion over a path for the length of one
    edit; this is admission control - "no one may OPEN new work until I have a verdict" - with a
    different refusal set, and a domain held for a whole sweep would block every sibling from all
    work, which is both unacceptable and unenforceable since the sweep itself takes those domains to
    fix what it finds. The domain table is also repo configuration while its enforcement lives in the
    canon harness (Rule 8), so a new domain is a two-sided change this repository cannot land alone.

    What the freeze refuses is enforced by .claude/hooks/guard-release-freeze.ps1, not here: this
    script only owns the marker and the question "is a freeze held, and by whom".

    Liveness is deliberately NOT a pid test. The process running -Verb Take exits the moment Take
    returns - the same reason lock-status.ps1 prints "acquiring process - exits at acquire, not the
    holder" of its own pid - so a pid check would report every freeze dead within a second. The owner
    is a SESSION, and Get-AgentTicketLiveness is the one function that already decides that for every
    lock and lease in this repository; the marker therefore carries the field names it reads and is
    passed straight to it. Test-AgentIdentityProcessAlive alone is wrong here too: measured
    2026-09-12, it answers false for a session-guid owner by design, and a guid is what a Claude
    session has.

.PARAMETER Verb
    Take    - claim the freeze for this session.
    Status  - report whether a freeze is held, and by whom.
    Release - drop the freeze this session holds.

.PARAMETER Reason
    Take only. What the tree is being judged for, shown to every session the hook refuses.

.PARAMETER Hours
    Take only. Absolute expiry, default 4. Refused above the SpecTicket ticket ceiling.

.PARAMETER Json
    Status only. Emit one JSON object instead of prose - the shape the guard hook reads.

.PARAMETER Force
    Release only. Drop a freeze whose owner is still live. For an owner that demonstrably exited
    without releasing; the expiry covers the ordinary case on its own.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/release-freeze.ps1 -Verb Take -Reason 'r37 pre-release sweep'

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/release-freeze.ps1 -Verb Status -Json

.NOTES
    FMS_REPO_ROOT overrides the repository root, against which the marker and the queue stop file
    resolve; the contract suite points it at a fixture tree.

Exit codes (CLAUDE.md Rule 7):
  0  the verb succeeded - freeze taken, released, or reported (held or not).
  2  the marker exists but could not be read or written.
  4  refused - a live foreign session holds the freeze.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Take', 'Status', 'Release')]
    [string] $Verb,

    [string] $Reason = '',

    [ValidateRange(1, 24)]
    [int] $Hours = 4,

    [switch] $Json,

    [switch] $Force
)

$ErrorActionPreference = 'Stop'

# One dot-source supplies Get-AgentTicketLiveness, Get-AgentSessionId, $Script:AgentLockTimings and,
# transitively, Get-AgentIdentity and Get-SzaPath. Verified 2026-09-12 - four separate dot-sources
# would only give this file four chances to diverge from the locks it must agree with.
. (Join-Path $PSScriptRoot 'agent-lock.ps1')

$RepoRoot = if ($env:FMS_REPO_ROOT) { $env:FMS_REPO_ROOT } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$MarkerPath = Join-Path $RepoRoot 'temp\RELEASE-FREEZE.json'

# The stop file is canon-owned profile data, so it is read from the profile rather than spelled out:
# a literal here would be a second declaration of a path run-spec-queue.ps1 already owns.
$StopRelative = try { Get-SzaPath 'queueStopFile' -Relative } catch { 'temp/STOP-SPEC-QUEUE' }
$StopPath = Join-Path $RepoRoot ($StopRelative -replace '/', '\')

$StaleMinutes = $Script:AgentLockTimings.SpecTicket.SessionStaleMinutes
$CeilingMinutes = $Script:AgentLockTimings.SpecTicket.TicketCeilingMinutes

function Write-Line([string] $Text, [string] $Colour = 'Gray') {
    if (-not $Json) { Write-Host $Text -ForegroundColor $Colour }
}

# ConvertFrom-Json parses an ISO stamp into a DateTime, and casting that back to a string uses the
# host's culture - so the marker on disk said 2026-09-12T11:48:50 while -Json emitted 09/12/2026
# 11:48:50. The hook shows these to a refused session, and a date whose format depends on the reader's
# machine is one nobody can compare against the marker.
function Format-Stamp($Value) {
    if ($null -eq $Value) { return '' }
    if ($Value -is [datetime]) { return $Value.ToString('s') }
    $parsed = [datetime]::MinValue
    if ([datetime]::TryParse([string]$Value, [ref]$parsed)) { return $parsed.ToString('s') }
    return [string]$Value
}

function ConvertTo-UnixMs([datetime] $Value) {
    return [DateTimeOffset]::new($Value.ToUniversalTime(), [TimeSpan]::Zero).ToUnixTimeMilliseconds()
}

# Every verb loads the marker through here, so "held" has exactly one definition. A marker whose
# owner is gone or whose expiry has passed is reported as ABSENT and deleted in the same call: an
# unreleased freeze that outlives its session is worse than no freeze at all, and S2761 records that
# exact failure mode for the code lock. Deleting it here rather than warning about it is what keeps
# the cost to the next session at one line of output instead of a standoff.
function Get-Freeze {
    if (-not (Test-Path -LiteralPath $MarkerPath)) {
        return [pscustomobject]@{ Held = $false; Record = $null; EndedBy = '' }
    }

    $record = $null
    try {
        $record = Get-Content -LiteralPath $MarkerPath -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        Write-Line "release-freeze: marker unreadable - $MarkerPath" 'Red'
        exit 2
    }

    $expired = $false
    if ($record.PSObject.Properties.Name -contains 'expiresAt' -and $record.expiresAt) {
        try { $expired = ([datetime]$record.expiresAt) -lt (Get-Date) } catch { $expired = $false }
    }
    if ($expired) {
        Remove-Item -LiteralPath $MarkerPath -Force -ErrorAction SilentlyContinue
        Write-Line "release-freeze: a freeze taken by $($record.ownerName) expired at $(Format-Stamp $record.expiresAt) - cleared." 'Yellow'
        return [pscustomobject]@{ Held = $false; Record = $record; EndedBy = 'expiry' }
    }

    # 'undetermined' means THIS caller has no session id, so mine and theirs are indistinguishable -
    # the function's own contract says it must never be grounds for eviction, so it counts as held.
    $liveness = Get-AgentTicketLiveness -Ticket $record -StaleMinutes $StaleMinutes
    if ($liveness -eq 'foreign-stale') {
        Remove-Item -LiteralPath $MarkerPath -Force -ErrorAction SilentlyContinue
        Write-Line "release-freeze: the session that took the freeze ($($record.ownerName)) is gone - cleared." 'Yellow'
        return [pscustomobject]@{ Held = $false; Record = $record; EndedBy = 'dead-owner' }
    }

    return [pscustomobject]@{ Held = $true; Record = $record; EndedBy = ''; Liveness = $liveness }
}

function Format-Holder($Record) {
    return "{0} (session {1}), reason '{2}', expires {3}" -f `
        $Record.ownerName, $Record.sessionId, $Record.reason, (Format-Stamp $Record.expiresAt)
}

switch ($Verb) {

    'Status' {
        $state = Get-Freeze
        if ($Json) {
            $payload = if ($state.Held) {
                [ordered]@{
                    held      = $true
                    sessionId = [string]$state.Record.sessionId
                    ownerName = [string]$state.Record.ownerName
                    reason    = [string]$state.Record.reason
                    takenAt   = Format-Stamp $state.Record.takenAt
                    expiresAt = Format-Stamp $state.Record.expiresAt
                    liveness  = [string]$state.Liveness
                }
            }
            else {
                [ordered]@{ held = $false; endedBy = [string]$state.EndedBy }
            }
            Write-Output ($payload | ConvertTo-Json -Depth 4 -Compress)
        }
        elseif ($state.Held) {
            Write-Host "release-freeze: HELD by $(Format-Holder $state.Record)" -ForegroundColor Cyan
        }
        else {
            Write-Host 'release-freeze: no freeze held - the tree is not being judged.' -ForegroundColor Green
        }
        exit 0
    }

    'Take' {
        if ($Hours * 60 -gt $CeilingMinutes) {
            Write-Host "release-freeze: -Hours $Hours exceeds the SpecTicket ceiling of $CeilingMinutes minutes." -ForegroundColor Red
            exit 2
        }

        $me = Get-AgentIdentity
        $state = Get-Freeze
        if ($state.Held -and [string]$state.Record.sessionId -ne [string]$me.id) {
            Write-Host "release-freeze: REFUSED - $(Format-Holder $state.Record)" -ForegroundColor Red
            Write-Host '  A sweep is judging the whole tree. Finish the ticket you are on; do not open a new one.' -ForegroundColor Yellow
            exit 4
        }

        # Standing the unattended runner down costs nothing and closes a hole that was simply not
        # open during r37 - the runner had been stopped for five hours before that sweep began and
        # contributed zero of its 31 sibling rows, so this is insurance, never the fix. A stop the
        # owner had already requested for their own reasons must survive the freeze, hence the flag.
        $stoppedQueue = $false
        if (-not (Test-Path -LiteralPath $StopPath)) {
            Set-Content -LiteralPath $StopPath -Value ("stop requested {0}" -f (Get-Date -Format 's')) -Encoding UTF8
            $stoppedQueue = $true
            Write-Host "release-freeze: queue runner asked to stand down - $StopRelative" -ForegroundColor Yellow
        }
        else {
            Write-Host "release-freeze: queue runner was already stopped - leaving $StopRelative alone." -ForegroundColor DarkGray
        }

        $now = Get-Date
        $record = [ordered]@{
            schema         = 1
            # The four field names below are Get-AgentTicketLiveness's, not this script's. They are
            # spelled its way so the marker can be handed to it unchanged.
            sessionId      = $me.id
            enqueuedAt     = ConvertTo-UnixMs $now
            lastSeenAt     = ConvertTo-UnixMs $now
            # Resolved through the harness's own resolver, not read from an environment variable:
            # CLAUDE_TRANSCRIPT_PATH is not set in this runtime, and a null here would drop the
            # strongest clock-based liveness signal - leaving a four-hour freeze to be judged stale
            # after 45 idle minutes and cleared under a sweep that was still running.
            transcriptPath = Get-AgentSessionTranscriptPath -SessionId $env:CLAUDE_CODE_SESSION_ID
            ownerName      = $me.name
            host           = $me.host
            reason         = if ($Reason) { $Reason } else { 'release sweep' }
            takenAt        = $now.ToString('s')
            expiresAt      = $now.AddHours($Hours).ToString('s')
            stoppedQueue   = $stoppedQueue
        }

        try {
            $dir = Split-Path -Parent $MarkerPath
            if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            Set-Content -LiteralPath $MarkerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        }
        catch {
            Write-Host "release-freeze: could not write the marker - $MarkerPath" -ForegroundColor Red
            exit 2
        }

        $what = if ($state.Held) { 'refreshed' } else { 'taken' }
        Write-Host "release-freeze: $what by $($me.name) until $($record.expiresAt) - reason '$($record.reason)'." -ForegroundColor Green
        exit 0
    }

    'Release' {
        $state = Get-Freeze
        if (-not $state.Held) {
            Write-Host 'release-freeze: nothing held - nothing to release.' -ForegroundColor Green
            exit 0
        }

        $me = Get-AgentIdentity
        if ([string]$state.Record.sessionId -ne [string]$me.id -and -not $Force) {
            Write-Host "release-freeze: REFUSED - $(Format-Holder $state.Record) still holds it." -ForegroundColor Red
            Write-Host '  Use -Force only for an owner that demonstrably exited; otherwise the expiry clears it.' -ForegroundColor Yellow
            exit 4
        }

        if ($state.Record.stoppedQueue) {
            Remove-Item -LiteralPath $StopPath -Force -ErrorAction SilentlyContinue
            Write-Host "release-freeze: queue runner released - $StopRelative removed." -ForegroundColor Green
        }
        else {
            Write-Host "release-freeze: $StopRelative was not this freeze's to remove - left in place." -ForegroundColor DarkGray
        }

        Remove-Item -LiteralPath $MarkerPath -Force -ErrorAction SilentlyContinue
        Write-Host "release-freeze: released - the tree is no longer being judged." -ForegroundColor Green
        exit 0
    }
}
