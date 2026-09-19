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

    S3320 qualifies what "passed straight to it" may mean. Get-AgentTicketLiveness ranks the ticket's
    own lastSeenAt heartbeat ABOVE the owning session's transcript, because a heartbeat is written by
    the polling waiter that owns the ticket. A freeze has no waiter: Take stamped lastSeenAt once and
    nothing refreshed it, so that dead heartbeat shadowed the live transcript this script resolves and
    writes, and every freeze became evictable 45 minutes after Take - whatever -Hours said, and however
    alive its owner was. Any reader evicts, including the -Verb Status -Json child that
    guard-release-freeze.ps1 spawns in a SIBLING session, so the freeze was deleted by a stranger's
    claim on the way to being allowed. Measured 2026-09-19 against a fixture tree: heartbeat 60 minutes
    old, expiry three hours out, owner transcript written one second earlier -> held=false,
    endedBy=dead-owner, marker gone. The repair is on this side of the canon boundary, where the defect
    is: the marker now presents the NEWEST of its heartbeat and its owner's transcript (Resolve-Heartbeat),
    and the owner's own reads refresh the stamp on disk.

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
# Deliberately NOT the marker path with a field added (S3320). guard-release-freeze.ps1's hot path is
# one Test-Path on the marker name, and it runs on every Bash and PowerShell call in every session; a
# tombstone living there would spawn its child process forever after the first sweep. A second name the
# hook never looks at costs that hook nothing. Declared in temp-root-inventory.ps1 beside the two names
# the freeze already owns, so a temp/ sweep cannot carry it off.
$EndedPath = Join-Path $RepoRoot 'temp\RELEASE-FREEZE-ENDED.json'

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

function ConvertFrom-UnixMs($Value) {
    if (-not $Value) { return $null }
    try { return [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Value).LocalDateTime } catch { return $null }
}

# The newest moment the owner is known to have existed, as a unix stamp - the marker's own heartbeat or
# its session's transcript, whichever is later. See the S3320 paragraph in the header: the heartbeat
# outranks the transcript inside Get-AgentTicketLiveness, so a marker that presents a stale one hides a
# live session behind it. Raising the value can only ever keep a freeze alive, never evict one earlier,
# which is what makes it safe to do to a record the canon function will judge.
function Resolve-Heartbeat($Record) {
    $newest = ConvertFrom-UnixMs $Record.lastSeenAt
    $transcript = [string]$Record.transcriptPath
    if (-not [string]::IsNullOrWhiteSpace($transcript)) {
        $written = $null
        try { $written = Get-AgentSessionTranscriptLastWrite -TranscriptPath $transcript } catch { $written = $null }
        if ($written -and ($null -eq $newest -or $written -gt $newest)) { $newest = $written }
    }
    if ($null -eq $newest) { return $null }
    return ConvertTo-UnixMs $newest
}

# A freeze that ends early used to leave nothing at all, so its owner's next Status read was the same
# sentence as a tree that had never been frozen - which is how the package-39 sweep went on believing it
# was protected (S3320 section 0). One small record, read by Status and removed by both Take and Release,
# turns "no freeze held" into "the freeze you took at X ended at Y because Z".
function Write-FreezeEnded($Record, [string] $EndedBy) {
    if (-not $Record) { return }
    $me = try { Get-AgentIdentity } catch { $null }
    $tomb = [ordered]@{
        schema    = 1
        sessionId = [string]$Record.sessionId
        ownerName = [string]$Record.ownerName
        reason    = [string]$Record.reason
        takenAt   = Format-Stamp $Record.takenAt
        expiresAt = Format-Stamp $Record.expiresAt
        endedAt   = (Get-Date).ToString('s')
        endedBy   = $EndedBy
        # Who noticed, which is rarely the owner: an eviction happens in whichever process asks.
        clearedBy = if ($me) { [string]$me.name } else { '' }
    }
    try { Set-Content -LiteralPath $EndedPath -Value ($tomb | ConvertTo-Json -Depth 4) -Encoding UTF8 } catch { }
}

function Get-FreezeEnded {
    if (-not (Test-Path -LiteralPath $EndedPath)) { return $null }
    try { return Get-Content -LiteralPath $EndedPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { return $null }
}

function Clear-FreezeEnded {
    Remove-Item -LiteralPath $EndedPath -Force -ErrorAction SilentlyContinue
}

# A freeze ends three ways and only one of them used to lift the stand-down it imposed. Take writes
# the queue stop flag and records stoppedQueue; Release removes it; expiry and a dead owner deleted the
# marker and left the flag on disk with nobody left to own it. Measured 2026-09-19: the package-39
# sweep's marker carried stoppedQueue = true and expired at 08:39, and the flag was still there at
# 11:32 - run-spec-queue.ps1 reads it before its first ranking and, with any headless child alive,
# refuses the start (exit 3, "a stop is in progress") having written no journal row, so every queue
# lane started and did nothing for six hours. A freeze that cannot outlive its sweep has to take its
# effects with it, not only its marker.
function Clear-StandDown($Record) {
    if (-not $Record) { return }
    if ($Record.PSObject.Properties.Name -notcontains 'stoppedQueue') { return }
    if (-not $Record.stoppedQueue) { return }
    if (-not (Test-Path -LiteralPath $StopPath)) { return }
    Remove-Item -LiteralPath $StopPath -Force -ErrorAction SilentlyContinue
    Write-Line "release-freeze: the stand-down this freeze requested is lifted - $StopRelative removed." 'Yellow'
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
        Clear-StandDown $record
        Write-FreezeEnded $record 'expiry'
        return [pscustomobject]@{ Held = $false; Record = $record; EndedBy = 'expiry' }
    }

    # The record handed to the canon function is a COPY carrying the raised heartbeat (S3320). The one
    # on disk keeps what Take or the owner's last read wrote, so nothing here can make a freeze look
    # younger to the next reader than it really is.
    $probe = $record | ConvertTo-Json -Depth 4 | ConvertFrom-Json
    $heartbeat = Resolve-Heartbeat $record
    if ($null -ne $heartbeat) { $probe.lastSeenAt = $heartbeat }

    # 'undetermined' means THIS caller has no session id, so mine and theirs are indistinguishable -
    # the function's own contract says it must never be grounds for eviction, so it counts as held.
    $liveness = Get-AgentTicketLiveness -Ticket $probe -StaleMinutes $StaleMinutes
    if ($liveness -eq 'foreign-stale') {
        Remove-Item -LiteralPath $MarkerPath -Force -ErrorAction SilentlyContinue
        Write-Line "release-freeze: the session that took the freeze ($($record.ownerName)) is gone - cleared." 'Yellow'
        Clear-StandDown $record
        Write-FreezeEnded $record 'dead-owner'
        return [pscustomobject]@{ Held = $false; Record = $record; EndedBy = 'dead-owner' }
    }

    # The owner's own traffic IS the heartbeat where no transcript can be resolved - and the Take site
    # records that CLAUDE_TRANSCRIPT_PATH is unset in this runtime, so that case is the normal one, not
    # the exotic one. Costs one write per read by the holder, and only by the holder.
    if ($liveness -eq 'self') {
        $record.lastSeenAt = ConvertTo-UnixMs (Get-Date)
        try { Set-Content -LiteralPath $MarkerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8 } catch { }
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

        # Nothing held has two meanings and they are not the same news: a tree that was never frozen,
        # and a sweep whose freeze ended under it. The second one is what the owner has to be told.
        $ended = if ($state.Held) { $null } else { Get-FreezeEnded }
        $endedBy = if ($state.EndedBy) { [string]$state.EndedBy } elseif ($ended) { [string]$ended.endedBy } else { '' }
        $wasMine = $false
        if ($ended) {
            $meId = try { Get-AgentSessionId } catch { $null }
            $wasMine = -not [string]::IsNullOrWhiteSpace($meId) -and ([string]$ended.sessionId -eq [string]$meId)
        }

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
                [ordered]@{
                    held      = $false
                    endedBy   = $endedBy
                    endedAt   = if ($ended) { Format-Stamp $ended.endedAt } else { '' }
                    ownerName = if ($ended) { [string]$ended.ownerName } else { '' }
                    wasMine   = $wasMine
                }
            }
            Write-Output ($payload | ConvertTo-Json -Depth 4 -Compress)
        }
        elseif ($state.Held) {
            Write-Host "release-freeze: HELD by $(Format-Holder $state.Record)" -ForegroundColor Cyan
        }
        else {
            Write-Host 'release-freeze: no freeze held - the tree is not being judged.' -ForegroundColor Green
            if ($ended) {
                $whose = if ($wasMine) { 'the freeze YOU took' } else { "a freeze taken by $($ended.ownerName)" }
                Write-Host "  $whose at $(Format-Stamp $ended.takenAt) ended at $(Format-Stamp $ended.endedAt) - $($ended.endedBy)." -ForegroundColor Yellow
                if ($wasMine) {
                    Write-Host '  Every gate cleared since then was measured on an unprotected tree. Re-take the freeze before trusting one.' -ForegroundColor Yellow
                }
            }
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

        # A live freeze supersedes whatever the last one ended as; keeping the record would make the
        # next Status announce an end that this Take has already answered.
        Clear-FreezeEnded

        $what = if ($state.Held) { 'refreshed' } else { 'taken' }
        Write-Host "release-freeze: $what by $($me.name) until $($record.expiresAt) - reason '$($record.reason)'." -ForegroundColor Green
        exit 0
    }

    'Release' {
        $state = Get-Freeze
        if (-not $state.Held) {
            Write-Host 'release-freeze: nothing held - nothing to release.' -ForegroundColor Green
            # The sweep's closing Release is the likeliest moment it finds out, and the likeliest moment
            # the news still matters: everything it measured after this stamp was measured unprotected.
            $ended = Get-FreezeEnded
            if ($ended) {
                Write-Host "  a freeze taken by $($ended.ownerName) at $(Format-Stamp $ended.takenAt) had already ended at $(Format-Stamp $ended.endedAt) - $($ended.endedBy)." -ForegroundColor Yellow
                Clear-FreezeEnded
            }
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
        # An explicit Release is the one end nobody needs to be told about - its owner is the caller.
        Clear-FreezeEnded
        Write-Host "release-freeze: released - the tree is no longer being judged." -ForegroundColor Green
        exit 0
    }
}
