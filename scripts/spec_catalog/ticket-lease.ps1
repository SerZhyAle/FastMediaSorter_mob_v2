<#
.SYNOPSIS
    Ticket leases for parallel /spec-next and /spec-do sessions (S1437).

.DESCRIPTION
    A lease marks one spec ticket as being worked by one agent session, so a sibling session
    ranking the same queue is offered a different ticket instead of duplicating the work.

    One file per lease under temp/SPEC-TICKET.LEASES/<Sxxxx>.json. A claim is an atomic file
    creation, not a check followed by a write: the gap between those two disk calls is exactly
    how two sessions take one ticket. Losing a claim is therefore a normal outcome (exit 3), not
    an error - the caller re-ranks with that id excluded and takes the next one.

    Ownership is a session, not a process, so liveness is the write time of that session's
    transcript. The rule itself is NOT restated here: Get-AgentTicketLiveness in
    scripts/utils/agent-lock.ps1 owns it, and a third copy of it would drift from the two that
    already exist. Timings likewise come from $Script:AgentLockTimings.SpecTicket.

    A stale lease is swept by whoever reads next - List, Status and Claim all sweep first. There
    is no watchdog process, matching the queue design in S1432.

.PARAMETER Verb
    Claim   - take the ticket. Atomic; idempotent for a lease this session already owns.
    Release - give it back. Owner-checked: a live foreign lease is refused.
    List    - ids only, one per line (or a JSON array under -Json). Feeds an exclusion list.
    Status  - human-readable holder map: ticket, session, host, last seen, reason.
    Sweep   - drop stale leases and report how many went.

.PARAMETER Id
    Ticket id, Sxxxx. Required for Claim and Release.

.PARAMETER Reason
    Free text recorded in the lease, shown by Status. Defaults to the calling skill's name.

.PARAMETER Json
    Emit machine-readable output instead of console text.

.PARAMETER StaleMinutes
    Liveness window. Defaults to $Script:AgentLockTimings.SpecTicket.SessionStaleMinutes.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id S1234 -Reason "/spec-next"
    Takes S1234. Exit 0 on success, exit 3 when a live sibling got there first.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status
    Prints which session holds which ticket and how long ago each was last seen.

.EXIT CODES
    0 - done: claimed, released, or reported.
    1 - error: unreadable store, bad argument shape, write failure.
    3 - claim lost: a live foreign session already holds this ticket.
    4 - release refused: a live foreign session owns this lease.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Claim', 'Release', 'List', 'Status', 'Sweep')]
    [string]$Verb,

    [string]$Id,

    [string]$Reason = 'spec-picker',

    [switch]$Json,

    [int]$StaleMinutes = 0
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\utils\agent-lock.ps1')

$timings = Get-AgentLockTimings -Name SpecTicket
if ($StaleMinutes -le 0) { $StaleMinutes = $timings.SessionStaleMinutes }

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$leaseDir = Join-Path $root 'temp\SPEC-TICKET.LEASES'
if (-not (Test-Path -LiteralPath $leaseDir)) {
    New-Item -ItemType Directory -Path $leaseDir -Force | Out-Null
}

function Get-SessionId {
    $sid = $env:CLAUDE_CODE_SESSION_ID
    if ([string]::IsNullOrWhiteSpace($sid)) { return "pid-$PID" }
    return $sid
}

function Get-LeasePath {
    param([Parameter(Mandatory)][string]$TicketId)
    return (Join-Path $leaseDir "$TicketId.json")
}

function Read-Lease {
    param([Parameter(Mandatory)][string]$Path)
    try { return (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json) }
    catch { return $null }
}

function Get-LeaseAgeMinutes {
    param([Parameter(Mandatory)]$Lease)
    if (-not $Lease.claimedAt) { return [double]::MaxValue }
    $claimed = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Lease.claimedAt).LocalDateTime
    return ((Get-Date) - $claimed).TotalMinutes
}

function Test-LeaseOwnerHoldsLock {
    <#
        S1448. A session that holds CODE.LOCK or BUILD.LOCK with a reason naming this ticket id is
        working this ticket right now - that is direct proof, stronger than any inference from a
        transcript timestamp. Observed live: preflight offered S1436 as unleased while the owning
        session held CODE.LOCK with reason '/spec-dev S1436 step 06.2', and a sibling trusting that
        would have written the same ticket concurrently - exactly what the lease exists to stop.
    #>
    param([Parameter(Mandatory)]$Lease)

    $ownerSessionId = [string]$Lease.sessionId
    $ticketId = [string]$Lease.id
    if ([string]::IsNullOrWhiteSpace($ownerSessionId) -or [string]::IsNullOrWhiteSpace($ticketId)) { return $false }

    foreach ($lockName in @('Code', 'Build')) {
        $lock = Get-AgentLockStatus -Name $lockName
        if (-not $lock.Exists -or $lock.Stale) { continue }
        if ([string]$lock.SessionId -ne $ownerSessionId) { continue }
        if ([string]$lock.Reason -match [regex]::Escape($ticketId)) { return $true }
    }
    return $false
}

function Get-LeaseLiveness {
    # Get-AgentTicketLiveness falls back to $Ticket.enqueuedAt when the transcript is unreachable.
    # The lease's own field is claimedAt, so shim it across rather than storing the value twice.
    # S1448 adds lastSeenAt as the strongest signal, matching the queue-ticket precedence order.
    param([Parameter(Mandatory)]$Lease)
    $shim = [pscustomobject]@{
        sessionId      = $Lease.sessionId
        transcriptPath = $Lease.transcriptPath
        lastSeenAt     = $Lease.lastSeenAt
        enqueuedAt     = $Lease.claimedAt
    }
    $verdict = Get-AgentTicketLiveness -Ticket $shim -StaleMinutes $StaleMinutes
    if ($verdict -eq 'foreign-stale' -and (Test-LeaseOwnerHoldsLock -Lease $Lease)) { return 'foreign-live' }
    return $verdict
}

function Invoke-LeaseSweep {
    <#
        Drops a lease whose owning session has gone quiet, or which passed the absolute ceiling
        regardless of liveness - the case where a transcript keeps being written but the ticket
        itself was abandoned. Never drops on 'undetermined': that means WE have no session id,
        so "mine" and "theirs" are indistinguishable and eviction would be a guess.
    #>
    $removed = @()
    foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $lease = Read-Lease -Path $file.FullName
        if ($null -eq $lease) {
            # Grace window against catching a file mid-write: the writer renames into place, but a
            # reader that arrives between create and rename would otherwise delete a valid lease.
            $ageSeconds = ((Get-Date) - $file.LastWriteTime).TotalSeconds
            if ($ageSeconds -gt 60) {
                Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
                $removed += $file.BaseName
            }
            continue
        }
        $liveness = Get-LeaseLiveness -Lease $lease
        if ($liveness -eq 'undetermined') { continue }
        $ageMinutes = Get-LeaseAgeMinutes -Lease $lease
        if ($liveness -eq 'foreign-stale' -or $ageMinutes -gt $timings.TicketCeilingMinutes) {
            Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
            $removed += $lease.id
        }
    }
    return $removed
}

function Get-LiveLeases {
    $out = @()
    foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $lease = Read-Lease -Path $file.FullName
        if ($null -eq $lease) { continue }
        $liveness = Get-LeaseLiveness -Lease $lease
        $lastSeen = $null
        if (-not [string]::IsNullOrWhiteSpace([string]$lease.transcriptPath) -and
            (Test-Path -LiteralPath ([string]$lease.transcriptPath))) {
            try { $lastSeen = (Get-Item -LiteralPath ([string]$lease.transcriptPath)).LastWriteTime } catch { $lastSeen = $null }
        }
        $lastSeenMinutes = if ($lastSeen) { [math]::Round(((Get-Date) - $lastSeen).TotalMinutes, 1) } else { $null }
        $out += [pscustomobject]@{
            id              = [string]$lease.id
            sessionId       = [string]$lease.sessionId
            host            = [string]$lease.host
            pid             = $lease.pid
            reason          = [string]$lease.reason
            claimedAt       = $lease.claimedAt
            ageMinutes      = [math]::Round((Get-LeaseAgeMinutes -Lease $lease), 1)
            lastSeenMinutes = $lastSeenMinutes
            liveness        = $liveness
            mine            = ($liveness -eq 'self')
        }
    }
    return ($out | Sort-Object id)
}

function Write-LeaseFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$TicketId,
        [Parameter(Mandatory)][string]$SessionId
    )
    $payload = [ordered]@{
        schema         = 1
        id             = $TicketId
        sessionId      = $SessionId
        host           = $env:COMPUTERNAME
        pid            = $PID
        reason         = $Reason
        claimedAt      = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        # S1448: refreshed by Update-LeaseHeartbeat on every verb this session runs against its
        # own lease. claimedAt stays frozen so the 480-minute ceiling cannot be extended.
        lastSeenAt     = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        transcriptPath = (Get-AgentSessionTranscriptPath -SessionId $SessionId)
    }
    $text = ($payload | ConvertTo-Json -Depth 4 -Compress)
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    # CreateNew makes "does it exist" and "create it" one filesystem call, so two sessions racing
    # for one ticket cannot both win. The IOException below is the loser, not a fault.
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        $bytes = $utf8NoBom.GetBytes($text)
        $stream.Write($bytes, 0, $bytes.Length)
    }
    finally { $stream.Dispose() }
}

function Update-LeaseHeartbeat {
    <#
        S1448. Refresh lastSeenAt on every lease this session owns, so a session that is plainly
        active - it just ran a lease verb - is never judged gone. Best-effort and write-then-rename:
        a reader that caught a half-written lease would treat it as unreadable.
    #>
    param([Parameter(Mandatory)][string]$SessionId)

    foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $lease = Read-Lease -Path $file.FullName
        if ($null -eq $lease -or [string]$lease.sessionId -ne $SessionId) { continue }
        try {
            $lease | Add-Member -NotePropertyName 'lastSeenAt' -NotePropertyValue ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) -Force
            $staging = "$($file.FullName).tmp-$PID"
            Set-Content -LiteralPath $staging -Value ($lease | ConvertTo-Json -Depth 4 -Compress) -Encoding utf8NoBOM -ErrorAction Stop
            Move-Item -LiteralPath $staging -Destination $file.FullName -Force -ErrorAction Stop
        }
        catch {
            # A missed refresh costs a delay, never a stuck ticket - the next verb retries.
        }
    }
}

$sessionId = Get-SessionId
Update-LeaseHeartbeat -SessionId $sessionId

switch ($Verb) {

    'Claim' {
        if ([string]::IsNullOrWhiteSpace($Id)) {
            Write-Error 'ticket-lease: -Id is required for Claim.' -ErrorAction Continue
            exit 1
        }
        [void](Invoke-LeaseSweep)
        $path = Get-LeasePath -TicketId $Id

        if (Test-Path -LiteralPath $path) {
            $existing = Read-Lease -Path $path
            if ($null -ne $existing -and [string]$existing.sessionId -eq $sessionId) {
                # Re-claiming our own lease is a no-op, so a resumed round does not fight itself.
                if ($Json) { [pscustomobject]@{ outcome = 'already-mine'; id = $Id; sessionId = $sessionId } | ConvertTo-Json -Compress }
                else { Write-Host "ticket-lease: $Id already held by this session." -ForegroundColor DarkGray }
                exit 0
            }
        }

        try {
            Write-LeaseFile -Path $path -TicketId $Id -SessionId $sessionId
        }
        catch [System.IO.IOException] {
            $holder = Read-Lease -Path $path
            $holderId = if ($holder) { [string]$holder.sessionId } else { 'unknown' }
            $holderHost = if ($holder) { [string]$holder.host } else { 'unknown' }
            if ($Json) {
                [pscustomobject]@{ outcome = 'claim-lost'; id = $Id; heldBy = $holderId; host = $holderHost } | ConvertTo-Json -Compress
            }
            else {
                Write-Host "ticket-lease: $Id already claimed by session $holderId on $holderHost." -ForegroundColor Yellow
            }
            exit 3
        }

        if ($Json) { [pscustomobject]@{ outcome = 'claimed'; id = $Id; sessionId = $sessionId } | ConvertTo-Json -Compress }
        else { Write-Host "ticket-lease: claimed $Id (session $sessionId)." -ForegroundColor Green }
        exit 0
    }

    'Release' {
        if ([string]::IsNullOrWhiteSpace($Id)) {
            Write-Error 'ticket-lease: -Id is required for Release.' -ErrorAction Continue
            exit 1
        }
        $path = Get-LeasePath -TicketId $Id
        if (-not (Test-Path -LiteralPath $path)) {
            if ($Json) { [pscustomobject]@{ outcome = 'absent'; id = $Id } | ConvertTo-Json -Compress }
            else { Write-Host "ticket-lease: $Id holds no lease." -ForegroundColor DarkGray }
            exit 0
        }

        $lease = Read-Lease -Path $path
        $liveness = if ($null -ne $lease) { Get-LeaseLiveness -Lease $lease } else { 'foreign-stale' }
        if ($liveness -eq 'foreign-live') {
            $holderId = [string]$lease.sessionId
            if ($Json) { [pscustomobject]@{ outcome = 'release-refused'; id = $Id; heldBy = $holderId } | ConvertTo-Json -Compress }
            else { Write-Host "ticket-lease: refusing to release $Id - live session $holderId owns it." -ForegroundColor Yellow }
            exit 4
        }

        Remove-Item -LiteralPath $path -Force
        if ($Json) { [pscustomobject]@{ outcome = 'released'; id = $Id } | ConvertTo-Json -Compress }
        else { Write-Host "ticket-lease: released $Id." -ForegroundColor Green }
        exit 0
    }

    'List' {
        [void](Invoke-LeaseSweep)
        $ids = @(@(Get-LiveLeases) | ForEach-Object { $_.id })
        if ($Json) { ConvertTo-Json -InputObject $ids -Compress }
        elseif ($ids.Count -eq 0) { Write-Host 'no leases held' }
        else { $ids | ForEach-Object { Write-Output $_ } }
        exit 0
    }

    'Status' {
        [void](Invoke-LeaseSweep)
        $leases = @(Get-LiveLeases)
        if ($Json) {
            ConvertTo-Json -InputObject @($leases) -Depth 5 -Compress
            exit 0
        }
        if ($leases.Count -eq 0) {
            Write-Host 'no leases held'
            exit 0
        }
        Write-Host "Ticket leases ($($leases.Count)):" -ForegroundColor Cyan
        foreach ($l in $leases) {
            $marker = if ($l.mine) { '>' } else { ' ' }
            $seen = if ($null -ne $l.lastSeenMinutes) { "last seen $($l.lastSeenMinutes) min ago" } else { 'last seen unknown' }
            $color = if ($l.mine) { 'Green' } else { 'Gray' }
            Write-Host ("  {0} {1}  {2} on {3}  ({4}, held {5} min)  {6}" -f `
                    $marker, $l.id, $l.sessionId, $l.host, $seen, $l.ageMinutes, $l.reason) -ForegroundColor $color
        }
        exit 0
    }

    'Sweep' {
        $removed = @(Invoke-LeaseSweep)
        if ($Json) { [pscustomobject]@{ outcome = 'swept'; removed = @($removed) } | ConvertTo-Json -Compress }
        elseif ($removed.Count -eq 0) { Write-Host 'ticket-lease: nothing stale.' }
        else { Write-Host "ticket-lease: swept $($removed.Count) stale lease(s): $($removed -join ', ')" -ForegroundColor Yellow }
        exit 0
    }
}
