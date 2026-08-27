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

.NOTES
    Exit codes:
    0 - the domains are acquired, start editing. Also returned when this session ALREADY holds
        every domain of the requested set (re-entrant call): nothing is enqueued and the existing
        locks stay yours (S1448). Holding only part of the set still queues for the rest.
    2 - the resource name is not an accepted domain or bare type (S2109); nothing was enqueued.
    4 - queued: another session holds one of your domains, or you are not its queue head. Your
        ticket is in the queue; wait for your turn with scripts/utils/wait-for-lock-turn.ps1 (or
        re-run with -Wait). Do not edit sources yet.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S0900: refactor BrowseViewModel"

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S0900" -Wait -WaitTimeoutSeconds 600
#>
param(
    [Parameter(Mandatory)][string]$Reason,
    # S2109: the changed file set. The domains are DERIVED from it, so a wear edit, a phone edit
    # and a scripts edit no longer wait for each other. Naming no files keeps the pre-split
    # behaviour - the full code set - which is the safe default, not an oversight.
    [string[]]$Files,
    # Escape hatch for a caller that knows its domain but not its paths yet. A declared domain is
    # deliberately second-class (ADR-1): getting it wrong removes protection quietly.
    [string]$Domain,
    [switch]$Wait,
    [int]$WaitTimeoutSeconds = 1200
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

if ($Domain) {
    $lockName = $Domain
    $domains = @(Resolve-AgentLockDomains -Name $Domain)
    $derivedFrom = "declared -Domain $Domain"
}
elseif ($Files) {
    $domains = @(Resolve-CodeDomainsForPaths -Path $Files)
    $lockName = $null
    $derivedFrom = "derived from $(@($Files).Count) changed path(s)"
}
else {
    $lockName = 'Code'
    $domains = @(Resolve-AgentLockDomains -Name 'Code')
    $derivedFrom = 'no file set given - taking the full code set'
}
Write-Host "Code domains: $($domains -join ', ')  ($derivedFrom)" -ForegroundColor Cyan

foreach ($buildDomain in @(Resolve-AgentLockDomains -Name 'Build')) {
    $buildStatus = Get-AgentLockStatus -Name $buildDomain
    if ($buildStatus.Exists -and -not $buildStatus.Stale) {
        Write-Host "Notice: $($buildDomain.ToUpper()).LOCK is live (PID $($buildStatus.Pid), age $([int]$buildStatus.AgeSeconds)s, reason: '$($buildStatus.Reason)')." -ForegroundColor Yellow
        Write-Host "  A gradle build is running elsewhere - it may compile a half-written state if you edit now." -ForegroundColor Yellow
    }
}

# S1448 re-entrancy guard, mirroring the one Enter-BuildLockOrExit applies to BUILD.LOCK. A
# session that already holds the lock and asks again must not enqueue behind itself: it would
# exit 4, never be granted, and leave that ticket on the queue head - the very state this ticket
# exists to remove, reached through a second door.
$mySessionId = $env:CLAUDE_CODE_SESSION_ID
# Re-entrant only when EVERY domain of the requested set is already ours: holding two of three and
# calling again still has to queue for the third, or the caller would edit under a domain it never
# took.
$heldByMe = @($domains | Where-Object {
    $s = Get-AgentLockStatus -Name $_
    $s.Exists -and -not $s.Stale -and -not [string]::IsNullOrWhiteSpace($mySessionId) -and
        [string]$s.SessionId -eq $mySessionId
})
if ($heldByMe.Count -eq $domains.Count) {
    Write-Host "Code domains $($domains -join ', ') already held by this session - reusing them, nothing queued." -ForegroundColor Green
    exit 0
}

# S1448: take the place in the queue BEFORE asking for the lock, exactly as
# Enter-BuildLockOrExit does. Two things depend on it. The ticket the acquire retires is then
# this session's own, so nothing of ours is left sitting on the queue head; and a session that
# released the lock and immediately wants it back queues BEHIND whoever was already waiting
# instead of stepping over them. The issuer below reuses this session's existing ticket, so
# asking twice keeps the place already earned rather than taking a second one.
$tickets = New-AgentLockTicketSet -Name 'Code' -Reason $Reason -Domains $domains
$ticket = $tickets[$domains[0]]

$result = Enter-AgentLock -Name 'Code' -Reason $Reason -Domains $domains -Tickets $tickets
if ($result.Acquired) {
    Write-Host "Code domains acquired: $($domains -join ', ') (reason: '$Reason')." -ForegroundColor Green
    exit 0
}

if ($Wait) {
    Write-Host "Code domains unavailable - queued at position $((Test-AgentLockTurnSet -Name 'Code' -Tickets $tickets -Domains $domains).Position), waiting up to ${WaitTimeoutSeconds}s.." -ForegroundColor DarkGray
    $waited = Enter-AgentLock -Name 'Code' -Reason $Reason -Domains $domains -Wait -WaitTimeoutSeconds $WaitTimeoutSeconds -Tickets $tickets
    if ($waited.Acquired) {
        Write-Host "Code domains acquired: $($domains -join ', ') (reason: '$Reason')." -ForegroundColor Green
        exit 0
    }
    foreach ($d in $domains) { Remove-Item -LiteralPath $tickets[$d].path -Force -ErrorAction SilentlyContinue }
}

$turn = Test-AgentLockTurnSet -Name 'Code' -Tickets $tickets -Domains $domains
$blocked = if ($turn.BlockingDomain) { $turn.BlockingDomain } else { $domains[0] }
$holder = Get-AgentLockStatus -Name $blocked

# S1448: name the blocker that actually exists. The message used to claim "held by another
# session" unconditionally and print a Holder line built from an absent lock file - which read
# as `Holder: session  (age 0s, reason: '')` and sent whoever read it looking for a holder that
# was not there. A free lock with a foreign queue head is a different fact and says so.
if ($holder.Exists -and -not $holder.Stale) {
    Write-Error "enter-code-lock: $blocked is held by another session - queued at position $($turn.Position), not yet your turn." -ErrorAction Continue
    Write-Host "  Holder: session $($holder.SessionId) (age $([int]$holder.AgeSeconds)s, reason: '$($holder.Reason)')." -ForegroundColor Yellow
}
else {
    $head = @(Get-AgentLockQueue -Name $blocked)[0]
    $reservationMinutes = (Get-AgentLockTimings -Name $blocked).ReservationMinutes
    $headWaitedMinutes = if ($head -and $head.enqueuedAt) {
        [int](([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - [int64]$head.enqueuedAt) / 60000)
    }
    else { 0 }
    Write-Error "enter-code-lock: $blocked is free, but this session is not the queue head - queued at position $($turn.Position), not yet your turn." -ErrorAction Continue
    Write-Host "  Queue head: session $($head.sessionId) (ticket #$($head.seq), waited ${headWaitedMinutes}m, reason: '$($head.reason)')." -ForegroundColor Yellow
    Write-Host "  The head keeps the turn for up to ${reservationMinutes} min after the lock frees; after that the next live ticket may take it." -ForegroundColor Yellow
}
Write-Host "  Your ticket: #$($ticket.seq). Wait for the signal in the background:" -ForegroundColor Yellow
Write-Host "    pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name $blocked -Reason '$Reason'" -ForegroundColor Gray
Write-Host "  Meanwhile do lock-free work: reading, research, specs, catalog, docs, log analysis." -ForegroundColor Gray
exit 4
