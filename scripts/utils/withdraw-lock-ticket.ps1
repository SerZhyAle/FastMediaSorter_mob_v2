<#
.SYNOPSIS
    Withdraw this session's own place in a lock queue: temp/BUILD.QUEUE or temp/CODE.QUEUE.

.DESCRIPTION
    S2098. The queue had operations for taking a place, waiting for it and evicting a ticket whose
    owner is judged gone - but none for "my intent is cancelled". A session that drops a queued
    request (the operator switched it to another ticket, the wait was interrupted, the phase
    collapsed) leaves a ticket that no sweep will ever take: the ticket is not stale, because the
    owning session is alive and its heartbeat keeps refreshing. It is the INTENT that was dropped,
    not the session. Observed 2026-08-27: one such ticket sat at the head of the Code queue with two
    other sessions waiting behind it, and the only way out was deleting the file by hand.

    Boundaries, all three deliberate:
      - Only tickets owned by the CALLING session are removed. Another session's place is never
        touched - clearing someone else's queue position is what clear-agent-lock.ps1 -Force does,
        and that also drops a lock which may belong to a third, actively working session.
      - The lock FILE is never read or written. Withdrawing is safe while another session works
        under the lock; releasing a lock is a different event with a different script
        (exit-code-lock.ps1).
      - No session identity in the environment is a refusal, not a quiet zero. Without an identity
        "my ticket" is indistinguishable from anyone else's, and a 0-removed report would read as
        "nothing of mine was queued" when the truth is "nothing could be judged".

.PARAMETER Name
    Which queue to withdraw from: Build or Code.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/withdraw-lock-ticket.ps1 -Name Code

.EXAMPLE
    .\a.ps1 uqc

.NOTES
    Exit codes:
      0 - withdrawal judged: this session's tickets, if any, are gone. Zero removed is a normal
          outcome and still exits 0.
      2 - ownership could not be established (no CLAUDE_CODE_SESSION_ID / CODEX_SESSION_ID in the
          environment), so nothing was removed.
#>
param(
    [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\agent-lock.ps1"

$sessionId = $env:CLAUDE_CODE_SESSION_ID
if ([string]::IsNullOrWhiteSpace($sessionId)) {
    Write-Host "$Name queue: cannot establish ownership - no session id in the environment." -ForegroundColor Red
    Write-Host "  Nothing was removed. A ticket is owned by a SESSION, so without an identity this" -ForegroundColor Yellow
    Write-Host "  command cannot tell your place from someone else's." -ForegroundColor Yellow
    Write-Host "  Inspect the queue instead:  pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name $Name -Queue" -ForegroundColor Gray
    exit 2
}

$removed = Remove-AgentSessionTickets -Name $Name -SessionId $sessionId
$remaining = @(Get-AgentLockQueue -Name $Name)

if ($removed -gt 0) {
    Write-Host "$Name queue: $removed ticket(s) withdrawn for session $sessionId; $($remaining.Count) still waiting." -ForegroundColor Green
}
else {
    Write-Host "$Name queue: nothing to withdraw - session $sessionId held no ticket; $($remaining.Count) still waiting." -ForegroundColor Gray
}

# Name the next session in line: the point of withdrawing is that someone else moves up, and saying
# who makes the effect checkable without a second call to the queue inspector.
if ($removed -gt 0 -and $remaining.Count -gt 0) {
    $head = $remaining[0]
    Write-Host "  Head of queue is now #$($head.seq) session $($head.sessionId) (reason: '$($head.reason)')." -ForegroundColor Gray
}

exit 0
