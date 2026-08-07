<#
.SYNOPSIS
    Fast, read-only check of temp/BUILD.LOCK or temp/CODE.LOCK - existence, age, holder.

.DESCRIPTION
    Never mutates the lock file (staleness is only ever reclaimed inside Enter-AgentLock, at
    the moment a caller actually wants the lock). Use this before starting a gradle-backed
    command or a multi-file code edit, to see whether another agent session currently holds
    the lock and how stale it looks.

    This is a STATUS QUERY (S1338 phase 09). "Held" is a normal answer to it, not a failure
    of the query, so the verdict travels in the payload (`status` = held | stale | free) and
    the process exits 0 either way. Non-zero is reserved for "could not determine".
    Pass -StrictExit for the legacy contract where held exits 1.

    -Wait blocks until the lock is free (or reclaimable) instead of reporting once. It exists
    because 793 polls and 48 hand-rolled `until .. sleep` loops were written against the
    single-shot form (S1338). Staleness is re-judged every poll, so a holder that dies is
    reported free immediately rather than waited out.

    -Queue also answers the question the raw listing could not (S1448): each ticket carries
    `heldByLockHolder`, and the JSON payload carries `headOwnedByHolder`. True means the queue
    head is the current lock holder's own leftover ticket - the holder owns both the lock and the
    turn, so nobody behind it can advance no matter how long they wait. In text mode such a row
    is suffixed `<- holds the lock`.

    Exit code: 0 = status determined and reported (free, stale, or held).
               2 = could not determine (lock file unreadable), or -Wait ran out of time.
               1 = held, ONLY under -StrictExit.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build
    pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Json
    pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build -Wait -WaitTimeoutSeconds 300
#>
param(
    [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
    [switch]$Json,
    [switch]$StrictExit,
    # S1432: also report the queue behind the lock - who is waiting and in what order.
    [switch]$Queue,
    [switch]$Wait,
    [int]$WaitTimeoutSeconds = 900,
    [int]$PollSeconds = 2
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

try {
    $status = Get-AgentLockStatus -Name $Name
} catch {
    Write-Error "lock-status: cannot read $Name.LOCK - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if ($Wait) {
    $deadline = (Get-Date).AddSeconds($WaitTimeoutSeconds)
    while ($status.Exists -and -not $status.Stale) {
        if ((Get-Date) -ge $deadline) {
            $msg = "lock-status: {0}.LOCK still held after {1}s (holder pid {2}) - cannot verify it will free." -f
                $Name, $WaitTimeoutSeconds, $status.Pid
            Write-Error $msg -ErrorAction Continue
            exit 2
        }
        Start-Sleep -Seconds $PollSeconds
        try { $status = Get-AgentLockStatus -Name $Name }
        catch {
            Write-Error "lock-status: cannot read $Name.LOCK - $($_.Exception.Message)" -ErrorAction Continue
            exit 2
        }
    }
}

$held = ($status.Exists -and -not $status.Stale)
# The verdict is part of the payload so a caller never has to infer it from an exit code.
$state = if (-not $status.Exists) { 'free' } elseif ($status.Stale) { 'stale' } else { 'held' }

$queueTickets = @()
if ($Queue) {
    $mySessionId = $env:CLAUDE_CODE_SESSION_ID
    $position = 0
    $index = 0
    # S1448: a ticket owned by the CURRENT lock holder is the starvation shape - the holder's own
    # abandoned ticket parked on the head, so nobody behind it can ever advance. It used to be
    # visible only by matching session guids by eye.
    $holderSessionId = [string]$status.SessionId
    foreach ($ticket in @(Get-AgentLockQueue -Name $Name)) {
        $index++
        $heldByLockHolder = $held -and -not [string]::IsNullOrWhiteSpace($holderSessionId) -and
            ([string]$ticket.sessionId -eq $holderSessionId)
        $ticket | Add-Member -NotePropertyName 'position' -NotePropertyValue $index -Force
        $ticket | Add-Member -NotePropertyName 'mine' -NotePropertyValue ([string]$ticket.sessionId -eq $mySessionId) -Force
        $ticket | Add-Member -NotePropertyName 'heldByLockHolder' -NotePropertyValue $heldByLockHolder -Force
        if ($ticket.mine -and $position -eq 0) { $position = $index }
        $queueTickets += $ticket
    }
}

if ($Json) {
    $status | Add-Member -NotePropertyName 'status' -NotePropertyValue $state -Force
    $status | Add-Member -NotePropertyName 'held' -NotePropertyValue $held -Force
    if ($Queue) {
        $headOwnedByHolder = ($queueTickets.Count -gt 0) -and [bool]$queueTickets[0].heldByLockHolder
        $status | Add-Member -NotePropertyName 'queue' -NotePropertyValue $queueTickets -Force
        $status | Add-Member -NotePropertyName 'myPosition' -NotePropertyValue $position -Force
        $status | Add-Member -NotePropertyName 'headOwnedByHolder' -NotePropertyValue $headOwnedByHolder -Force
    }
    $status | ConvertTo-Json -Compress -Depth 4
}
else {
    if (-not $status.Exists) {
        Write-Host "$Name.LOCK: absent (free)" -ForegroundColor Green
    }
    else {
        $label = if ($status.Stale) { "STALE (reclaimable)" } else { "HELD" }
        $color = if ($status.Stale) { "Yellow" } else { "Red" }
        Write-Host "$Name.LOCK: $label" -ForegroundColor $color
        Write-Host "  path:       $($status.Path)"
        Write-Host "  pid:        $($status.Pid)"
        if ($Name -eq 'Build') {
            Write-Host "  processAlive: $($status.ProcessAlive)"
        }
        Write-Host "  age:        $([int]$status.AgeSeconds)s"
        Write-Host "  acquiredAt: $($status.AcquiredAtIso)"
        Write-Host "  reason:     $($status.Reason)"
        Write-Host "  host:       $($status.Host)"
    }

    if ($Queue) {
        if ($queueTickets.Count -eq 0) {
            Write-Host "$Name queue: empty" -ForegroundColor Green
        }
        else {
            Write-Host "$Name queue: $($queueTickets.Count) waiting" -ForegroundColor Yellow
            foreach ($ticket in $queueTickets) {
                $marker = if ($ticket.mine) { '>' } else { ' ' }
                $waitedMinutes = [int](([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - [int64]$ticket.enqueuedAt) / 60000)
                $suffix = if ($ticket.heldByLockHolder) { '  <- holds the lock' } else { '' }
                Write-Host ("  {0} #{1} pos {2}  session {3}  waited {4}m  reason '{5}'{6}" -f
                    $marker, $ticket.seq, $ticket.position, $ticket.sessionId, $waitedMinutes, $ticket.reason, $suffix)
            }
        }
    }
}

if ($held -and $StrictExit) {
    Write-Host "$Name.LOCK: held - exiting 1 as requested by -StrictExit." -ForegroundColor Red
    exit 1
}
exit 0
