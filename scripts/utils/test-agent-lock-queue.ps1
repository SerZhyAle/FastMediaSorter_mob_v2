<#
.SYNOPSIS
    Scenario check for the agent-lock queue: fairness, ticket retirement, liveness, compatibility.

.DESCRIPTION
    S1448. Every assertion here is a strategic readiness criterion of that ticket restated as
    something executable, because the defects it fixes are all invisible in normal use - a starved
    session looks slow, not broken, and reports no error at all.

    The whole run happens in a throwaway sandbox: agent-lock.ps1 resolves its lock and queue paths
    from $Script:AgentLockRepoRoot, which this script overrides after dot-sourcing. Nothing under
    the repository's own temp/CODE.LOCK, temp/CODE.QUEUE or temp/BUILD.* is read or written, so the
    check is safe to run while sibling agent sessions hold real tickets - which is exactly when the
    machinery most needs checking.

    A second session is simulated by swapping CLAUDE_CODE_SESSION_ID around each call; the caller's
    own value is restored in a finally, since every other lock-aware script in the tree reads it.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1

.NOTES
    Exit codes:
      0 - every assertion passed; the sandbox is deleted.
      1 - at least one assertion failed; the sandbox is kept for inspection and its path is printed.
      2 - the sandbox could not be prepared, so nothing was checked.
#>

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\agent-lock.ps1"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandbox = Join-Path $repoRoot "temp/S1448/sandbox-$stamp"

try {
    New-Item -ItemType Directory -Force -Path (Join-Path $sandbox 'temp') | Out-Null
}
catch {
    Write-Error "test-agent-lock-queue: cannot prepare sandbox at $sandbox - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$Script:AgentLockRepoRoot = $sandbox

# S2109: every assertion below is about ONE resource's fairness - retirement, ordering, liveness,
# withdrawal - and each must go on holding per domain now that a bare 'Code' names the whole set of
# three. They are therefore stated against a concrete domain; the set-level properties the split
# introduces (opposite-order acquisition, disjoint domains, partial heads) are asserted separately
# further down, because they are a different question.
$domain = 'Code.Scripts'

$callerSessionId = $env:CLAUDE_CODE_SESSION_ID
$sessionA = 'sandbox-session-A'
$sessionB = 'sandbox-session-B'
$failures = 0
$passes = 0

function Write-Verdict {
    param([Parameter(Mandatory)][string]$Label, [Parameter(Mandatory)][bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        $script:passes++
        Write-Host "PASS - $Label" -ForegroundColor Green
    }
    else {
        $script:failures++
        Write-Host "FAIL - $Label $Detail" -ForegroundColor Red
    }
}

function Reset-Sandbox {
    # Each assertion starts from an empty queue and a free lock, so one assertion's leftovers can
    # never decide the next one's verdict. S2109: every domain plus the two pre-split names, since
    # a set-level case touches all of them and a legacy file left behind would be honoured by the
    # next case as a live holder.
    foreach ($name in @('Build.Phone', 'Build.Wear', 'Code.Phone', 'Code.Wear', 'Code.Scripts', 'Build', 'Code')) {
        $lockPath = Join-Path $sandbox "temp/$($name.ToUpper()).LOCK"
        if (Test-Path -LiteralPath $lockPath) { Remove-Item -LiteralPath $lockPath -Force }
        $queueDir = Join-Path $sandbox "temp/$($name.ToUpper()).QUEUE"
        if (Test-Path -LiteralPath $queueDir) { Get-ChildItem -LiteralPath $queueDir -Filter '*.json' | Remove-Item -Force }
    }
}

function New-SyntheticTicket {
    <#
        Writes a ticket file by hand so a specific age / liveness shape can be checked. Going
        through New-AgentLockTicket cannot express "enqueued 25 minutes ago".
    #>
    param(
        [Parameter(Mandatory)][int]$Seq,
        [Parameter(Mandatory)][string]$SessionId,
        [Parameter(Mandatory)][double]$AgeMinutes,
        [Nullable[double]]$HeartbeatAgeMinutes = $null,
        # S2194: stamps turnGrantedAt at a chosen age, which is how a head that was told to go and
        # never went is expressed. Going through Test-AgentLockTurn cannot express "granted 9
        # minutes ago" - it always stamps the present moment.
        [Nullable[double]]$TurnGrantedMinutesAgo = $null,
        [string]$TranscriptPath = $null,
        [switch]$OldShape
    )

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $ticket = [ordered]@{
        schema         = 1
        seq            = $Seq
        lockType       = $domain
        sessionId      = $SessionId
        host           = $env:COMPUTERNAME
        pid            = $PID
        reason         = "synthetic #$Seq"
        enqueuedAt     = $nowMs - [int64]($AgeMinutes * 60000)
        transcriptPath = $TranscriptPath
    }
    if (-not $OldShape -and $null -ne $HeartbeatAgeMinutes) {
        $ticket['lastSeenAt'] = $nowMs - [int64]($HeartbeatAgeMinutes * 60000)
    }
    if (-not $OldShape -and $null -ne $TurnGrantedMinutesAgo) {
        $ticket['turnGrantedAt'] = $nowMs - [int64]($TurnGrantedMinutesAgo * 60000)
    }

    $queueDir = Get-AgentLockQueueDir -Name $domain
    $path = Join-Path $queueDir ('{0:0000}__{1}.json' -f $Seq, $SessionId)
    Set-Content -LiteralPath $path -Value ($ticket | ConvertTo-Json -Compress) -Encoding utf8NoBOM
    return $path
}

try {
    $timings = Get-AgentLockTimings -Name $domain

    # 1 - Acquiring the lock retires every ticket of the acquiring session (strategic §11.1).
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    # The pre-fix queue really did accumulate several tickets for one session (strategic §0 records
    # #1, #3 and #5), so the check acquires with the HEAD ticket and demands the siblings go too -
    # retiring only the ticket handed in is precisely the defect.
    $headTicket = New-AgentLockTicket -Name $domain -Reason 'previous step, abandoned' -ForceNew
    $extraTicket = New-AgentLockTicket -Name $domain -Reason 'another abandoned step' -ForceNew
    $acquired = Enter-AgentLock -Name $domain -Reason 'this step' -Ticket $headTicket
    $remaining = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'ticket retirement: acquiring the lock leaves no ticket of the acquiring session' `
        -Ok ($acquired.Acquired -and $remaining.Count -eq 0) `
        -Detail "(acquired=$($acquired.Acquired), tickets left=$($remaining.Count), had #$($headTicket.seq) and #$($extraTicket.seq))"

    # 2 - A session releasing and immediately re-requesting does not step over an existing waiter
    #     (strategic §11.2). This is the starvation loop itself, stated as a check.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name $domain -Reason 'A holds it')
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](New-AgentLockTicket -Name $domain -Reason 'B waits')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    Exit-AgentLock -Name $domain
    $aTicket = New-AgentLockTicket -Name $domain -Reason 'A wants it back'
    $aRetry = Enter-AgentLock -Name $domain -Reason 'A wants it back' -Ticket $aTicket
    Write-Verdict -Label 'queue order: a released-and-re-requesting session does not jump the waiter ahead of it' `
        -Ok (-not $aRetry.Acquired) `
        -Detail "(A re-acquired=$($aRetry.Acquired), blockedBy=$($aRetry.BlockedBy), turn=$($aRetry.Turn.Reason))"

    # 3 - A fresh heartbeat keeps a ticket alive even with an unreadable transcript (strategic §11.3).
    #     Without lastSeenAt this ticket falls back to enqueuedAt, passes SessionStaleMinutes and is
    #     evicted - which is what happened to a session that waited exactly as the contract demands.
    Reset-Sandbox
    $staleByTranscript = $timings.SessionStaleMinutes + 2
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-quiet' -AgeMinutes $staleByTranscript `
            -HeartbeatAgeMinutes 0 -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $survivors = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'heartbeat survival: a quietly waiting ticket with a fresh lastSeenAt is not evicted' `
        -Ok ($survivors.Count -eq 1) `
        -Detail "(survivors=$($survivors.Count), ticket age=${staleByTranscript}m vs SessionStaleMinutes=$($timings.SessionStaleMinutes))"

    # 4 - A live waiter survives beyond the former ceiling. Its waiting time is controlled by the
    #     current holder, so treating that age as abandonment reverses the queue's fairness.
    Reset-Sandbox
    $pastCeiling = $timings.TicketCeilingMinutes + 5
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-long-wait' -AgeMinutes $pastCeiling `
            -HeartbeatAgeMinutes 0 -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $afterCeiling = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'live long-wait survival: a fresh heartbeat preserves a ticket past TicketCeilingMinutes' `
        -Ok ($afterCeiling.Count -eq 1) `
        -Detail "(survivors=$($afterCeiling.Count), ticket age=${pastCeiling}m vs ceiling=$($timings.TicketCeilingMinutes))"

    # 5 - Stale ownership, not elapsed queue age, remains the eviction criterion. A dead session
    #     must still stop blocking the queue even if its ticket was created recently.
    Reset-Sandbox
    $staleHeartbeat = $timings.SessionStaleMinutes + 2
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-abandoned' -AgeMinutes 1 `
            -HeartbeatAgeMinutes $staleHeartbeat -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $afterStaleOwner = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'stale-owner eviction: an expired heartbeat removes a ticket before it blocks the queue' `
        -Ok ($afterStaleOwner.Count -eq 0) `
        -Detail "(survivors=$($afterStaleOwner.Count), heartbeat age=${staleHeartbeat}m vs SessionStaleMinutes=$($timings.SessionStaleMinutes))"

    # 6 - A ticket written before this change still reads, orders and survives (strategic §11.9,
    #     ADR-5). The fix lands in a tree where sibling sessions already hold old-shape tickets.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-legacy' -AgeMinutes 1 -OldShape)
    [void](New-SyntheticTicket -Seq 2 -SessionId 'sandbox-session-modern' -AgeMinutes 0 -HeartbeatAgeMinutes 0)
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $mixed = @(Get-AgentLockQueue -Name $domain)
    $legacyKept = ($mixed.Count -eq 2) -and ([int]$mixed[0].seq -eq 1) -and
        ($mixed[0].PSObject.Properties.Name -notcontains 'lastSeenAt')
    Write-Verdict -Label 'backwards compatibility: a pre-S1448 ticket is read, ordered by seq and kept' `
        -Ok $legacyKept `
        -Detail "(tickets=$($mixed.Count), head seq=$(if ($mixed.Count) { $mixed[0].seq } else { 'n/a' }))"

    # 7 - S1462. Reading an absent property is only an error under Set-StrictMode, and the callers
    #     that wait on this queue (post-change.ps1 and the gates it drives) all set it. So the guard
    #     that lets a pre-S1448 head be stamped can regress without any un-strict test noticing:
    #     assertion 5 above passes either way. The strict scope here is the whole point of the case -
    #     without it this asserts nothing. Observed cost when it broke: the wait aborted and the
    #     calling gate reported exit 1, "found a defect", for a check that never ran.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-legacy' -AgeMinutes 1 -OldShape)
    [void](New-SyntheticTicket -Seq 2 -SessionId $sessionA -AgeMinutes 0 -HeartbeatAgeMinutes 0)
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $strictError = ''
    try {
        # A child scope with StrictMode on: the functions it calls inherit it, exactly as they do
        # from a gate that set it before entering the wait.
        & {
            Set-StrictMode -Version Latest
            $null = Test-AgentLockTurn -Name $domain
        }
    }
    catch {
        $strictError = $_.Exception.Message
    }
    Write-Verdict -Label 'strict mode: a pre-S1448 queue head is stamped without a property-probe error' `
        -Ok ([string]::IsNullOrEmpty($strictError)) `
        -Detail $(if ($strictError) { "(threw: $strictError)" } else { '(no error under Set-StrictMode -Version Latest)' })

    # 8 - S2098. Withdrawing drops the caller's own ticket and promotes whoever was behind it. The
    #     removal runs through the library function rather than the CLI because the sandbox is a
    #     $Script:AgentLockRepoRoot override, which a separate pwsh process would not inherit - it
    #     would read the repository's real queue and delete a sibling session's ticket.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](New-AgentLockTicket -Name $domain -Reason 'A, intent later abandoned')
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](New-AgentLockTicket -Name $domain -Reason 'B, waiting behind A')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $withdrawn = Remove-AgentSessionTickets -Name $domain -SessionId $sessionA
    $afterWithdraw = @(Get-AgentLockQueue -Name $domain)
    $promoted = ($afterWithdraw.Count -eq 1) -and ([string]$afterWithdraw[0].sessionId -eq $sessionB)
    Write-Verdict -Label 'withdraw: dropping an abandoned intent removes only the caller ticket and promotes the waiter' `
        -Ok (($withdrawn -eq 1) -and $promoted) `
        -Detail "(withdrawn=$withdrawn, left=$($afterWithdraw.Count), head=$(if ($afterWithdraw.Count) { $afterWithdraw[0].sessionId } else { 'n/a' }))"

    # 9 - The same call must be inert against another session's place. This is the boundary that
    #     separates withdraw from clear-agent-lock.ps1 -Force, which drops the whole queue.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](New-AgentLockTicket -Name $domain -Reason 'B, working normally')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $foreignWithdrawn = Remove-AgentSessionTickets -Name $domain -SessionId $sessionA
    $foreignSurvivors = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'withdraw boundary: a session with no ticket removes nothing and leaves the other session queued' `
        -Ok (($foreignWithdrawn -eq 0) -and ($foreignSurvivors.Count -eq 1)) `
        -Detail "(withdrawn=$foreignWithdrawn, survivors=$($foreignSurvivors.Count))"

    # 10 - Withdraw is an operation on the QUEUE, never on the lock. A held lock - here another
    #      session's - must still be there afterwards, which is what makes the call safe to run at
    #      any moment during someone else's edit.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](Enter-AgentLock -Name $domain -Reason 'B is mid-edit')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](New-AgentLockTicket -Name $domain -Reason 'A, queued then abandoned')
    [void](Remove-AgentSessionTickets -Name $domain -SessionId $sessionA)
    $lockAfter = Get-AgentLockStatus -Name $domain
    Write-Verdict -Label 'withdraw safety: another session lock survives a withdraw untouched' `
        -Ok ($lockAfter.Exists -and ([string]$lockAfter.SessionId -eq $sessionB)) `
        -Detail "(lock exists=$($lockAfter.Exists), owner=$($lockAfter.SessionId))"

    # ---- S2109: the properties the domain split itself introduces -------------------------------

    # 11 - Disjoint domains do not serialise. This is the whole point of the ticket: the watch
    #      module and the phone module share no file, so a session editing one must not wait for a
    #      session editing the other.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $aWear = Enter-AgentLock -Name 'Code.Wear' -Reason 'A edits the watch module'
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $bScripts = Enter-AgentLock -Name 'Code.Scripts' -Reason 'B edits repository tooling'
    Write-Verdict -Label 'disjoint domains: a session holding one domain does not block a session taking another' `
        -Ok ($aWear.Acquired -and $bScripts.Acquired) `
        -Detail "(A took Code.Wear=$($aWear.Acquired), B took Code.Scripts=$($bScripts.Acquired))"

    # 12 - All-or-nothing. A set that cannot be completed must leave NOTHING held: a refusal that
    #      kept the domains it managed to take would block every overlapping session for the whole
    #      length of its own wait, which is worse than the contention the split removes.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name 'Code.Wear' -Reason 'A holds one domain of the set')
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $bSet = Enter-AgentLock -Name 'Code' -Reason 'B wants the whole code side'
    $phoneAfterRollback = Get-AgentLockStatus -Name 'Code.Phone'
    $scriptsAfterRollback = Get-AgentLockStatus -Name 'Code.Scripts'
    Write-Verdict -Label 'all-or-nothing: a refused set rolls back every domain it had already taken' `
        -Ok ((-not $bSet.Acquired) -and (-not $phoneAfterRollback.Exists) -and (-not $scriptsAfterRollback.Exists)) `
        -Detail "(B acquired=$($bSet.Acquired), blocked on=$($bSet.Domain), Code.Phone still held=$($phoneAfterRollback.Exists))"

    # 13 - Opposite-order counter-attempt. Two sessions whose sets overlap, each asking from its own
    #      side: one must win and the other must merely wait. Both acquisitions go through the same
    #      canonical rank, which is what makes this resolvable at all - hand-ordered acquisition is
    #      the mutual block the risk table names.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $aWide = Enter-AgentLock -Name 'Code' -Reason 'A takes the whole set'
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $bNarrow = Enter-AgentLock -Name 'Code.Wear' -Reason 'B comes from the other side'
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    Exit-AgentLock -Name 'Code'
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $bAfterRelease = Enter-AgentLock -Name 'Code.Wear' -Reason 'B retries once A is done'
    Write-Verdict -Label 'opposite order: the counter-attempt waits rather than deadlocking, and completes once the holder releases' `
        -Ok ($aWide.Acquired -and (-not $bNarrow.Acquired) -and $bAfterRelease.Acquired) `
        -Detail "(A set=$($aWide.Acquired), B while held=$($bNarrow.Acquired), B after release=$($bAfterRelease.Acquired))"

    # 14 - A multi-domain waiter is granted only when it is head EVERYWHERE. Head in one domain and
    #      second in another is not partial progress - it is the state where two overlapping waiters
    #      each hold the head the other needs.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](New-AgentLockTicket -Name 'Code.Scripts' -Reason 'B is ahead in one domain only')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $aTickets = New-AgentLockTicketSet -Name 'Code' -Reason 'A wants the whole set'
    $aTurn = Test-AgentLockTurnSet -Name 'Code' -Tickets $aTickets
    Write-Verdict -Label 'set turn: a waiter that is head in only some of its domains is not granted' `
        -Ok ((-not $aTurn.IsMyTurn) -and ($aTurn.BlockingDomain -eq 'Code.Scripts')) `
        -Detail "(granted=$($aTurn.IsMyTurn), blocking domain=$($aTurn.BlockingDomain))"

    # 14b - A ONE-element explicit set. PowerShell enumerates the output of an assigned `if`
    #       statement, so `$set = if (..) { @(x) } else { @(y) }` collapses a single-element array
    #       into a bare string - and every [0] index after that reads a CHARACTER. Observed here:
    #       a single-domain build acquire failed with "Unknown coordination resource name 'B'".
    #       Multi-element sets hide the bug completely, so only a one-element case catches it.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $single = Enter-AgentLock -Name 'Code' -Reason 'one-element explicit set' -Domains @('Code.Wear')
    $singleHeld = (Get-AgentLockStatus -Name 'Code.Wear').Exists
    Exit-AgentLock -Name 'Code' -Domains @('Code.Wear')
    Write-Verdict -Label 'one-element explicit set acquires and releases without collapsing to a string' `
        -Ok ($single.Acquired -and $singleHeld -and -not (Get-AgentLockStatus -Name 'Code.Wear').Exists) `
        -Detail "(acquired=$($single.Acquired), held=$singleHeld)"

    # 15 - Transition. A pre-split lock file names no domain, so it must be honoured as holding
    #      every domain of its type. Without this a sibling that took temp/CODE.LOCK before the
    #      split becomes invisible and two sessions edit one tree believing they are alone.
    Reset-Sandbox
    $legacyLock = Join-Path $sandbox 'temp/CODE.LOCK'
    $legacyBody = [ordered]@{
        schema = 2; lockType = 'Code'; pid = $PID
        acquiredAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        reason = 'pre-split holder'; host = $env:COMPUTERNAME
        sessionId = $sessionB; transcriptPath = $null
    } | ConvertTo-Json -Compress
    Set-Content -LiteralPath $legacyLock -Value $legacyBody -Encoding utf8NoBOM
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $againstLegacy = Enter-AgentLock -Name 'Code.Wear' -Reason 'A tries one domain under a legacy lock'
    Write-Verdict -Label 'legacy lock: a pre-split CODE.LOCK blocks an acquire of a single domain' `
        -Ok (-not $againstLegacy.Acquired) `
        -Detail "(acquired=$($againstLegacy.Acquired) - a pre-split holder must cover every domain of its type)"

    # 16 - The other half of the transition, and the one that was missing: a pre-split lock must be
    #      RELEASABLE by its owner, not merely visible. Observed live while implementing this phase -
    #      the implementing session held temp/CODE.LOCK from before the split, its release touched
    #      only the three domain files, and the lock it actually held survived with a sibling queued
    #      behind it. Adoption that blocks without releasing converts every in-flight holder into a
    #      stall that only the staleness window ends.
    Reset-Sandbox
    Set-Content -LiteralPath $legacyLock -Value ($legacyBody -replace [regex]::Escape($sessionB), $sessionA) -Encoding utf8NoBOM
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    Exit-AgentLock -Name 'Code'
    $legacyGone = -not (Test-Path -LiteralPath $legacyLock)
    Write-Verdict -Label 'legacy release: the owner of a pre-split CODE.LOCK can actually release it' `
        -Ok $legacyGone `
        -Detail "(file still present=$(-not $legacyGone) - a lock that blocks but cannot be released stalls everyone behind it)"

    # 17 - ..and releasing ONE domain must not drop the pre-split file, which covers the other two.
    #      Dropping it there would hand two domains this caller never held to whoever polls next.
    Reset-Sandbox
    Set-Content -LiteralPath $legacyLock -Value ($legacyBody -replace [regex]::Escape($sessionB), $sessionA) -Encoding utf8NoBOM
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    Exit-AgentLock -Name 'Code.Wear'
    Write-Verdict -Label 'legacy release boundary: releasing one domain leaves the pre-split lock covering the others' `
        -Ok (Test-Path -LiteralPath $legacyLock) `
        -Detail '(a single-domain release dropped a file that covers every domain of its type)'

    # ---- S2194: a head that was granted its turn and never took it ------------------------------
    #
    # Every case below gives the head a FRESH heartbeat, so the pre-S2194 eviction rule judges it
    # live. Whatever removes it can therefore only be the forfeit branch, which is the point: the
    # two reasons must stay separable.

    $forfeited = $timings.ReservationMinutes + 4
    $withinReservation = [math]::Max(0.2, $timings.ReservationMinutes / 2.0)

    # 18 - The defect itself. A head whose reservation expired without the lock being taken holds no
    #      privilege any more - Test-AgentLockTurn already hands the turn to whoever asks - so
    #      leaving it in place only breaks the ordering for everyone behind and lies to every
    #      inspector about who is waiting.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-forfeiting' -AgeMinutes $forfeited `
            -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $forfeited -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $afterForfeit = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'forfeit: a head granted its turn more than ReservationMinutes ago and never entered is dropped' `
        -Ok ($afterForfeit.Count -eq 0) `
        -Detail "(survivors=$($afterForfeit.Count), granted ${forfeited}m ago vs ReservationMinutes=$($timings.ReservationMinutes))"

    # 19 - The near boundary. Inside its reservation window the head still owns the turn outright,
    #      so removing it there would hand the lock to a session that arrived later.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-just-granted' -AgeMinutes 1 `
            -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $withinReservation -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $insideWindow = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'forfeit boundary: a head still inside its reservation window is kept' `
        -Ok ($insideWindow.Count -eq 1) `
        -Detail "(survivors=$($insideWindow.Count), granted ${withinReservation}m ago vs ReservationMinutes=$($timings.ReservationMinutes))"

    # 20 - The far boundary, and the one the timings table forbids crossing: a ticket that was never
    #      granted a turn is WAITING, and its wait is controlled by whoever holds the lock. No
    #      elapsed time may evict it.
    Reset-Sandbox
    $longWait = $timings.TicketCeilingMinutes + 10
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-never-granted' -AgeMinutes $longWait `
            -HeartbeatAgeMinutes 0 -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $neverGranted = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'forfeit boundary: a ticket with no turnGrantedAt is never dropped, however long it waits' `
        -Ok ($neverGranted.Count -eq 1) `
        -Detail "(survivors=$($neverGranted.Count), waited ${longWait}m - forfeit must not become a ticket-age timer)"

    # 21 - The sweep runs on the acquire path, so a caller that did not exclude its own ticket would
    #      delete the very turn it came to take.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId $sessionA -AgeMinutes $forfeited `
            -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $forfeited -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $ownHead = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'forfeit safety: the caller own expired head is not dropped by its own sweep' `
        -Ok ($ownHead.Count -eq 1) `
        -Detail "(survivors=$($ownHead.Count) - a session must not sweep away the turn it came to take)"

    # 22 - What the removal is FOR. With the forfeited head gone the next waiter is the head and is
    #      told so; before the fix it was told 'head reservation expired', the same answer every
    #      other waiter got at the same moment, which is a race rather than a queue.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-forfeiting' -AgeMinutes $forfeited `
            -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $forfeited -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $bTicket = New-AgentLockTicket -Name $domain -Reason 'B waits behind a forfeited head'
    $bTurn = Test-AgentLockTurn -Name $domain -Ticket $bTicket
    Write-Verdict -Label 'forfeit restores FIFO: the waiter behind a forfeited head becomes the head, not a racer' `
        -Ok ($bTurn.IsMyTurn -and ($bTurn.Reason -eq 'head of queue')) `
        -Detail "(isMyTurn=$($bTurn.IsMyTurn), reason='$($bTurn.Reason)', position=$($bTurn.Position))"

    # ---- S2200: a superset request must never queue a session behind its own held domain --------

    # 23 - The captured incident itself: holding the HIGHER-ranked domain (Code.Wear) and asking for
    #      a set that also needs the LOWER-ranked one (Code.Phone) is the descending direction -
    #      granting it directly would let a symmetric session deadlock against this one, so it must
    #      be reported unsafe rather than silently topped up.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name 'Code.Wear' -Reason 'A already mid-edit in wear')
    $descending = Resolve-AgentLockTopUp -Domains @('Code.Phone', 'Code.Wear')
    Write-Verdict -Label 'top-up direction: holding the higher-ranked domain and missing the lower one is reported unsafe' `
        -Ok ((-not $descending.AscendingSafe) -and ($descending.Held -contains 'Code.Wear') -and ($descending.Missing -contains 'Code.Phone')) `
        -Detail "(ascendingSafe=$($descending.AscendingSafe), held=$($descending.Held -join ','), missing=$($descending.Missing -join ','))"

    # 24 - The mirror case: holding the LOWER-ranked domain (Code.Phone) and asking to add the
    #      higher-ranked one (Code.Wear) only continues the same canonical order a fresh acquirer
    #      would already be following - safe to top up without releasing what is held.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name 'Code.Phone' -Reason 'A already mid-edit in phone')
    $ascending = Resolve-AgentLockTopUp -Domains @('Code.Phone', 'Code.Wear')
    Write-Verdict -Label 'top-up direction: holding the lower-ranked domain and missing the higher one is reported safe' `
        -Ok ($ascending.AscendingSafe -and ($ascending.Held -contains 'Code.Phone') -and ($ascending.Missing -contains 'Code.Wear')) `
        -Detail "(ascendingSafe=$($ascending.AscendingSafe), held=$($ascending.Held -join ','), missing=$($ascending.Missing -join ','))"

    # 25 - A safe top-up must never touch the domain already held, only acquire what is missing -
    #      the whole point is that the in-progress edit under Code.Phone is never interrupted.
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $topUpAcquire = Enter-AgentLock -Name 'Code' -Reason 'A tops up to the full set' -Domains $ascending.Missing
    $phoneStillMine = Get-AgentLockStatus -Name 'Code.Phone'
    $wearNowMine = Get-AgentLockStatus -Name 'Code.Wear'
    Write-Verdict -Label 'safe top-up: acquiring only the missing domain leaves the held one untouched and adds the other' `
        -Ok ($topUpAcquire.Acquired -and $phoneStillMine.Exists -and ([string]$phoneStillMine.SessionId -eq $sessionA) -and `
            $wearNowMine.Exists -and ([string]$wearNowMine.SessionId -eq $sessionA)) `
        -Detail "(acquired=$($topUpAcquire.Acquired), phone held=$($phoneStillMine.Exists), wear held=$($wearNowMine.Exists))"

    # 26 - Full re-entrancy stays a no-op: every domain of the requested set already held by this
    #      session must not enqueue anything (S1448's original guard, unchanged by S2200).
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name 'Code' -Reason 'A holds the whole set' -Domains @('Code.Phone', 'Code.Wear'))
    $fullMatch = Resolve-AgentLockTopUp -Domains @('Code.Phone', 'Code.Wear')
    Write-Verdict -Label 'full re-entrancy: every requested domain already held reports nothing missing' `
        -Ok ($fullMatch.Missing.Count -eq 0) `
        -Detail "(held=$($fullMatch.Held -join ','), missing=$($fullMatch.Missing -join ','))"
}
finally {
    if ($null -eq $callerSessionId) { Remove-Item Env:\CLAUDE_CODE_SESSION_ID -ErrorAction SilentlyContinue }
    else { $env:CLAUDE_CODE_SESSION_ID = $callerSessionId }
}

Write-Host ""
if ($failures -eq 0) {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "test-agent-lock-queue: expected: 0 | actual: 0 failures ($passes assertions passed). Sandbox removed." -ForegroundColor Green
    exit 0
}

Write-Error "test-agent-lock-queue: $failures of $($passes + $failures) assertions FAILED. Sandbox kept: $sandbox" -ErrorAction Continue
exit 1
