<#
.SYNOPSIS
    Acquire temp/CODE.LOCK before starting a multi-file source (Kotlin/XML/build-file) edit.

.DESCRIPTION
    S1432: the lock is ordered. When another session is mid-edit this no longer shrugs and lets
    you edit anyway - it takes a place in the queue and tells you where you stand. Editing the
    same tree from two sessions is what produces the divergence a single shared checkout exists
    to avoid, and serialised editing is the accepted price of not having separate trees.

    Queued is not idle: while you wait, do the work that needs no lock - reading, research,
    specs, catalog, documentation, log analysis. Take the lock immediately before an edit and
    release it right after, never for a whole ticket.

    Release is automatic via scripts/post-change.ps1 (its finally block calls Exit-AgentLock),
    and that release is owner-checked, so it can never take a lock belonging to another session.
    Skills that skip post-change.ps1 (e.g. /skill-fix) must call scripts/utils/exit-code-lock.ps1.

.PARAMETER Reason
    What the lock is being taken for - shown to whoever inspects the lock or the queue.

.PARAMETER Wait
    Block here until the turn arrives instead of returning immediately. Prefer the background
    waiter (scripts/utils/wait-for-lock-turn.ps1) - blocking here spends the agent's turn.

.EXIT CODES
    0 - lock acquired, start editing.
    4 - queued: another session holds it. Your ticket is in the queue; wait for your turn with
        scripts/utils/wait-for-lock-turn.ps1 (or re-run with -Wait). Do not edit sources yet.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S0900: refactor BrowseViewModel"

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S0900" -Wait -WaitTimeoutSeconds 600
#>
param(
    [Parameter(Mandatory)][string]$Reason,
    [switch]$Wait,
    [int]$WaitTimeoutSeconds = 1200
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

$buildStatus = Get-AgentLockStatus -Name Build
if ($buildStatus.Exists -and -not $buildStatus.Stale) {
    Write-Host "Notice: BUILD.LOCK is live (PID $($buildStatus.Pid), age $([int]$buildStatus.AgeSeconds)s, reason: '$($buildStatus.Reason)')." -ForegroundColor Yellow
    Write-Host "  A gradle build is running elsewhere - it may compile a half-written state if you edit now." -ForegroundColor Yellow
}

$result = Enter-AgentLock -Name Code -Reason $Reason
if ($result.Acquired) {
    Write-Host "CODE.LOCK acquired (reason: '$Reason')." -ForegroundColor Green
    exit 0
}

$ticket = New-AgentLockTicket -Name Code -Reason $Reason

if ($Wait) {
    Write-Host "CODE.LOCK held - queued at position $((Test-AgentLockTurn -Name Code -Ticket $ticket).Position), waiting up to ${WaitTimeoutSeconds}s.." -ForegroundColor DarkGray
    $waited = Enter-AgentLock -Name Code -Reason $Reason -Wait -WaitTimeoutSeconds $WaitTimeoutSeconds -Ticket $ticket
    if ($waited.Acquired) {
        Write-Host "CODE.LOCK acquired (reason: '$Reason')." -ForegroundColor Green
        exit 0
    }
    Remove-Item -LiteralPath $ticket.path -Force -ErrorAction SilentlyContinue
}

$turn = Test-AgentLockTurn -Name Code -Ticket $ticket
$holder = Get-AgentLockStatus -Name Code
Write-Error "enter-code-lock: CODE.LOCK is held by another session - queued at position $($turn.Position), not yet your turn." -ErrorAction Continue
Write-Host "  Holder: session $($holder.SessionId) (age $([int]$holder.AgeSeconds)s, reason: '$($holder.Reason)')." -ForegroundColor Yellow
Write-Host "  Your ticket: #$($ticket.seq). Wait for the signal in the background:" -ForegroundColor Yellow
Write-Host "    pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code -Reason '$Reason'" -ForegroundColor Gray
Write-Host "  Meanwhile do lock-free work: reading, research, specs, catalog, docs, log analysis." -ForegroundColor Gray
exit 4
