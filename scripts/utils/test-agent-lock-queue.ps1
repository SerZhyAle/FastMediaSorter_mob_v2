<#
.SYNOPSIS
    Scenario check for the agent-lock queue: fairness, ticket retirement, liveness, compatibility.

.DESCRIPTION
    S1448. Every assertion here is a strategic readiness criterion of that ticket restated as
    something executable, because the defects it fixes are all invisible in normal use - a starved
    session looks slow, not broken, and reports no error at all.

    The whole run happens in a throwaway sandbox, redirected through the profile: this script writes
    a sandbox copy of .sza-profile.json whose every paths.* entry is an ABSOLUTE path inside the
    sandbox, and points $env:SZA_PROFILE_PATH at it after dot-sourcing agent-lock.ps1. The harness
    resolves every coordination path through Get-SzaPath, so all of them land in the sandbox and
    nothing under the repository's own temp/CODE.*.LOCK, temp/*.QUEUE or temp/BUILD.*.LOCK is read
    or written - which matters most while sibling agent sessions hold real tickets, exactly when the
    machinery needs checking.

    S2426: the override used to be a script-scoped repo-root variable, and after the mechanism moved
    into the canon (S2402) that variable decided no lock path any more - the suite silently began taking,
    releasing and sweeping the REAL locks of live sessions. Absolute paths in the profile are what
    makes the redirect survive the forwarder, which re-stamps $env:SZA_PROJECT_ROOT at every
    dot-source; SZA_PROFILE_PATH it does not touch. The guard below refuses to run at all unless
    every resolved paths.* entry lies inside the sandbox, so the next such move cannot pass silently.

    A second session is simulated by swapping CLAUDE_CODE_SESSION_ID around each call; the caller's
    own value is restored in a finally, since every other lock-aware script in the tree reads it.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1

.NOTES
    Exit codes:
      0 - every assertion that ran passed; the sandbox is deleted. S2421: a case whose behaviour
          the resolved harness does not carry yet prints SKIP and is counted in the summary line,
          so 0 means "nothing observed was wrong", never "everything was observed".
      1 - at least one assertion failed; the sandbox is kept for inspection and its path is printed.
      2 - the sandbox could not be prepared, or its isolation could not be confirmed (some paths.*
          entry resolved outside the sandbox), so nothing was checked.
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

$callerProfilePath = $env:SZA_PROFILE_PATH

function Restore-CallerProfilePath {
    # Named rather than inlined because both the guard's refusal and the run's finally must undo the
    # redirect the same way, and an empty string is not the same absence as an unset variable.
    if ([string]::IsNullOrEmpty($callerProfilePath)) { Remove-Item Env:\SZA_PROFILE_PATH -ErrorAction SilentlyContinue }
    else { $env:SZA_PROFILE_PATH = $callerProfilePath }
}

# Redirect every coordination path into the sandbox by handing the harness its own profile. Only
# paths.* is rewritten - grammar, statuses and lock domains must stay exactly as the repository
# declares them, since they are part of what is under test.
try {
    $repoProfilePath = Join-Path $repoRoot '.sza-profile.json'
    $sandboxProfile = Get-Content -LiteralPath $repoProfilePath -Raw -Encoding utf8 | ConvertFrom-Json
    foreach ($entry in $sandboxProfile.paths.PSObject.Properties) {
        if ([System.IO.Path]::IsPathRooted([string]$entry.Value)) { continue }
        $entry.Value = (Join-Path $sandbox ([string]$entry.Value)).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    }
    $sandboxProfilePath = Join-Path $sandbox 'sandbox-profile.json'
    $sandboxProfile | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $sandboxProfilePath -Encoding utf8NoBOM
}
catch {
    Write-Error "test-agent-lock-queue: cannot write the sandbox profile - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}
$env:SZA_PROFILE_PATH = $sandboxProfilePath

# The isolation guard. Enumerating what the profile actually declares, rather than checking the
# three names this suite happens to use, is the point: the next path the canon adds is covered by
# construction, and S2426 exists because a silent move of the redirect lever went unnoticed until a
# fake lock under a dead pid was found blocking three real tickets.
$sandboxFull = [System.IO.Path]::GetFullPath($sandbox)
$escaped = @()
foreach ($key in ($sandboxProfile.paths.PSObject.Properties.Name)) {
    $resolved = [System.IO.Path]::GetFullPath((Get-SzaPath $key))
    if (-not $resolved.StartsWith($sandboxFull, [StringComparison]::OrdinalIgnoreCase)) {
        $escaped += "    paths.$key -> $resolved"
    }
}
if ($escaped.Count -gt 0) {
    Write-Host "test-agent-lock-queue: isolation not confirmed - these profile paths resolve outside the sandbox:" -ForegroundColor Red
    foreach ($line in $escaped) { Write-Host $line -ForegroundColor Red }
    Write-Host "  sandbox: $sandboxFull" -ForegroundColor Gray
    Restore-CallerProfilePath
    exit 2
}

# S2109: every assertion below is about ONE resource's fairness - retirement, ordering, liveness,
# withdrawal - and each must go on holding per domain now that a bare 'Code' names the whole set of
# three. They are therefore stated against a concrete domain; the set-level properties the split
# introduces (opposite-order acquisition, disjoint domains, partial heads) are asserted separately
# further down, because they are a different question.
$domain = 'Code.Scripts'

$callerSessionId = $env:CLAUDE_CODE_SESSION_ID
# S2422: the explicit id is step ONE of the identity chain and outranks CLAUDE_CODE_SESSION_ID, so a
# caller that carries it collapses sessions A and B into one identity and the whole two-session
# simulation below stops meaning anything - measured 2026-09-03, 11 of 38 assertions fail that way,
# taking the queue, withdraw, forfeit and top-up cases with them. An assertion about one step of a
# chain must silence every step above it; both variables come back in the same finally.
$callerAgentId = Get-SzaEnv 'AGENT_ID'
Set-SzaEnv 'AGENT_ID' $null
# Captured here rather than beside the block that silences it: the finally restores both, and a run
# that dies before reaching the S2371 block would otherwise restore a value it never read.
$callerHostWalk = Get-SzaEnv 'AGENT_HOST_WALK'
$sessionA = 'sandbox-session-A'
$sessionB = 'sandbox-session-B'
# S2421: a holder that never queues, which is the shape of the session that took the lock four
# seconds after the victim's eviction. Judged live off the lock's own acquire time, so the case
# checks the exemption rather than a stale-lock path.
$thirdHolder = 'sandbox-session-third-holder'
$failures = 0
$passes = 0
$skips = 0

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

function Write-Skipped {
    <#
        S2421. The suite checks a mechanism this repository CONSUMES rather than owns (S2402), so
        the harness it resolves can legitimately be older than the fix a case was written for -
        between a canon edit and the owner's deploy, every consumer would otherwise go red over
        work no session running it can do. A skip states which behaviour went unobserved and names
        the ticket, so it can never be read as a pass; the run's exit code is unaffected.
    #>
    param([Parameter(Mandatory)][string]$Label, [Parameter(Mandatory)][string]$Reason)
    $script:skips++
    Write-Host "SKIP - $Label ($Reason)" -ForegroundColor Yellow
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
    #     removal runs through the library function rather than the CLI because a CLI child would
    #     re-derive its own paths, and this assertion is about the in-process queue state the rest of
    #     the case just built. S2426: the redirect itself is now an environment variable a child does
    #     inherit, so the reason is scope, no longer isolation.
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

    # ---- S2421: the forfeit must not fire while a THIRD session holds the lock ------------------
    #
    # The head of these three cases is shaped exactly like case 18's - expired stamp, fresh
    # heartbeat - so the ONLY difference is the held lock. A revert of either half of S2421 turns
    # the survivor count below into case 18's zero: measured 2026-09-03 against a harness without
    # the fix, all three fail, and case 24 reports the incident's own survivors=1.
    #
    # The presence probe is the FUNCTION added by the fix's second half. Its first half adds no
    # symbol to ask about, but the two ship as one ticket, so one probe answers for both; a harness
    # carrying only half of it fails these cases rather than skipping them, which is the right way
    # round.
    $s2421Present = [bool](Get-Command Clear-AgentTicketTurnGranted -ErrorAction SilentlyContinue)
    if (-not $s2421Present) {
        $s2421Why = 'the resolved harness predates S2421 - deploy the canon, then re-run'
        Write-Skipped -Label 'held lock: an expired head is not forfeited while a third session holds the lock' -Reason $s2421Why
        Write-Skipped -Label 'held lock: the head and the waiter behind it both keep their place' -Reason $s2421Why
        Write-Skipped -Label 'window restart: a stamp observed under a held lock is cleared, so the head survives the release' -Reason $s2421Why
    }
    else {

        # 23 - The incident of S2421 §0: a waiter that had polled every 5 s for 292 s lost its place to
        #      a session that was never in the queue. The forfeit asserts "the turn was granted and
        #      never taken", and that is false while somebody else holds the lock - the head could not
        #      have entered however hard it tried.
        Reset-Sandbox
        $env:CLAUDE_CODE_SESSION_ID = $thirdHolder
        [void](Enter-AgentLock -Name $domain -Reason 'a third session holds it, never queued')
        # Asserted, not assumed: a holder judged Stale makes the exemption correctly not fire, so
        # without this the three cases below could pass while observing nothing at all.
        $heldStatus = Get-AgentLockStatus -Name $domain
        if (-not $heldStatus.Exists -or $heldStatus.Stale) {
            Write-Error "test-agent-lock-queue: precondition failed - the sandbox lock is not a live foreign hold (Exists=$($heldStatus.Exists), Stale=$($heldStatus.Stale))." -ErrorAction Continue
            exit 2
        }
        [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-victim' -AgeMinutes $forfeited `
                -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $forfeited -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
        $env:CLAUDE_CODE_SESSION_ID = $sessionA
        $underForeignLock = @(Get-AgentLockQueue -Name $domain)
        Write-Verdict -Label 'held lock: an expired head is not forfeited while a third session holds the lock' `
            -Ok ($underForeignLock.Count -eq 1) `
            -Detail "(survivors=$($underForeignLock.Count), granted ${forfeited}m ago vs ReservationMinutes=$($timings.ReservationMinutes))"

        # 24 - The incident lost BOTH tickets, not just the head: each poll is another sweep, so the
        #      promoted second ticket is stamped in the next free window and forfeited in its turn. The
        #      waiter behind the head is the one whose place the queue exists to protect.
        Reset-Sandbox
        $env:CLAUDE_CODE_SESSION_ID = $thirdHolder
        [void](Enter-AgentLock -Name $domain -Reason 'a third session holds it, never queued')
        [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-victim' -AgeMinutes $forfeited `
                -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $forfeited -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
        [void](New-SyntheticTicket -Seq 2 -SessionId 'sandbox-session-behind' -AgeMinutes 2 `
                -HeartbeatAgeMinutes 0 -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
        $env:CLAUDE_CODE_SESSION_ID = $sessionA
        $bothKeepPlace = @(Get-AgentLockQueue -Name $domain)
        Write-Verdict -Label 'held lock: the head and the waiter behind it both keep their place' `
            -Ok ($bothKeepPlace.Count -eq 2) `
            -Detail "(survivors=$($bothKeepPlace.Count), expected 2 - the incident lost both)"

        # 25 - The other half of the fix, and the only case that fails when the stamp reset alone is
        #      reverted. Without it case 23's exemption gives the head a zero-length window: the stamp
        #      is already older than ReservationMinutes when the lock frees, so the first foreign sweep
        #      after the release evicts the ticket before its owner's next 5-second poll.
        Reset-Sandbox
        $env:CLAUDE_CODE_SESSION_ID = $thirdHolder
        [void](Enter-AgentLock -Name $domain -Reason 'a third session holds it, never queued')
        [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-victim' -AgeMinutes $forfeited `
                -HeartbeatAgeMinutes 0 -TurnGrantedMinutesAgo $forfeited -TranscriptPath 'Z:\no-such-transcript\missing.jsonl')
        $env:CLAUDE_CODE_SESSION_ID = $sessionA
        $observedHeld = Test-AgentLockTurn -Name $domain
        $env:CLAUDE_CODE_SESSION_ID = $thirdHolder
        Exit-AgentLock -Name $domain
        $env:CLAUDE_CODE_SESSION_ID = $sessionA
        $afterRelease = @(Get-AgentLockQueue -Name $domain)
        Write-Verdict -Label 'window restart: a stamp observed under a held lock is cleared, so the head survives the release' `
            -Ok (($observedHeld.Reason -eq 'lock held') -and ($afterRelease.Count -eq 1)) `
            -Detail "(observedReason='$($observedHeld.Reason)', survivors=$($afterRelease.Count) - expected 'lock held' and 1)"

    }

    # ---- S2200: a superset request must never queue a session behind its own held domain --------

    # 26 - The captured incident itself: holding the HIGHER-ranked domain (Code.Wear) and asking for
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

    # 27 - The mirror case: holding the LOWER-ranked domain (Code.Phone) and asking to add the
    #      higher-ranked one (Code.Wear) only continues the same canonical order a fresh acquirer
    #      would already be following - safe to top up without releasing what is held.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name 'Code.Phone' -Reason 'A already mid-edit in phone')
    $ascending = Resolve-AgentLockTopUp -Domains @('Code.Phone', 'Code.Wear')
    Write-Verdict -Label 'top-up direction: holding the lower-ranked domain and missing the higher one is reported safe' `
        -Ok ($ascending.AscendingSafe -and ($ascending.Held -contains 'Code.Phone') -and ($ascending.Missing -contains 'Code.Wear')) `
        -Detail "(ascendingSafe=$($ascending.AscendingSafe), held=$($ascending.Held -join ','), missing=$($ascending.Missing -join ','))"

    # 28 - A safe top-up must never touch the domain already held, only acquire what is missing -
    #      the whole point is that the in-progress edit under Code.Phone is never interrupted.
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $topUpAcquire = Enter-AgentLock -Name 'Code' -Reason 'A tops up to the full set' -Domains $ascending.Missing
    $phoneStillMine = Get-AgentLockStatus -Name 'Code.Phone'
    $wearNowMine = Get-AgentLockStatus -Name 'Code.Wear'
    Write-Verdict -Label 'safe top-up: acquiring only the missing domain leaves the held one untouched and adds the other' `
        -Ok ($topUpAcquire.Acquired -and $phoneStillMine.Exists -and ([string]$phoneStillMine.SessionId -eq $sessionA) -and `
            $wearNowMine.Exists -and ([string]$wearNowMine.SessionId -eq $sessionA)) `
        -Detail "(acquired=$($topUpAcquire.Acquired), phone held=$($phoneStillMine.Exists), wear held=$($wearNowMine.Exists))"

    # 29 - Full re-entrancy stays a no-op: every domain of the requested set already held by this
    #      session must not enqueue anything (S1448's original guard, unchanged by S2200).
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name 'Code' -Reason 'A holds the whole set' -Domains @('Code.Phone', 'Code.Wear'))
    $fullMatch = Resolve-AgentLockTopUp -Domains @('Code.Phone', 'Code.Wear')
    Write-Verdict -Label 'full re-entrancy: every requested domain already held reports nothing missing' `
        -Ok ($fullMatch.Missing.Count -eq 0) `
        -Detail "(held=$($fullMatch.Held -join ','), missing=$($fullMatch.Missing -join ','))"

    # ---- S2371: one identity for the lock owner and its queue ticket ----------------------------

    # S2422: these three cases are about the LAST step of the identity chain - the pid- fallback -
    # and removing CLAUDE_CODE_SESSION_ID alone does not reach it: S2408 put the ancestor walk
    # between them, so the answer becomes host-<name>-<pid>-<ticks> and is decided by the process
    # tree of whoever launched the suite rather than by the code under test. Under a resolving tree
    # (forfiles -> cmd -> pwsh) exactly these three failed and no others; in an opaque one all three
    # passed. The walk is therefore turned off for the block that checks the step below it, through
    # the documented escape rather than by rewriting the expectations to host- - the pid- path is
    # reachable in production (opaque parent, this switch, a walk that ends on a machine-wide
    # process), so expecting host- here would delete the coverage instead of repairing it.
    Set-SzaEnv 'AGENT_HOST_WALK' '0'

    # 30 - The defect itself: acquiring without CLAUDE_CODE_SESSION_ID stamps the SAME identity
    #      on the queue ticket and the lock file. Pre-fix the ticket said pid-NNNN while the
    #      lock said null - one acquisition recorded as two different holders, so the session
    #      could neither recognise its own lock nor match it in the self checks.
    Reset-Sandbox
    Remove-Item Env:\CLAUDE_CODE_SESSION_ID -ErrorAction SilentlyContinue
    $pidTicket = New-AgentLockTicket -Name $domain -Reason 'unnamed session takes a place'
    $pidEnter = Enter-AgentLock -Name $domain -Reason 'unnamed session acquires' -Ticket $pidTicket
    $pidLock = Get-AgentLockStatus -Name $domain
    $expectedPidIdentity = "pid-$PID"
    Write-Verdict -Label 'identity: acquiring without a session id stamps the same pid identity on ticket and lock' `
        -Ok ($pidEnter.Acquired -and
            ([string]$pidTicket.sessionId -eq $expectedPidIdentity) -and
            ([string]$pidLock.SessionId -eq $expectedPidIdentity)) `
        -Detail "(acquired=$($pidEnter.Acquired), ticket=$($pidTicket.sessionId), lock=$($pidLock.SessionId), expected=$expectedPidIdentity)"

    # 31 - Self-recognition: an unnamed caller reads its own pid-owned hold as live past
    #      LockStaleMinutes. Pre-fix the raw env read in the liveness check collapsed every
    #      unnamed caller to 'undetermined' before the self comparison, so the wall clock
    #      judged the caller's own live lock stale.
    $pidLockPath = Join-Path $sandbox "temp/$($domain.ToUpper()).LOCK"
    $backdatedMinutes = $timings.LockStaleMinutes + 20
    $pidLockBody = [ordered]@{
        schema = 2; lockType = $domain; pid = $PID
        procStart = (Get-Process -Id $PID).StartTime.Ticks
        acquiredAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - [int64]($backdatedMinutes * 60000)
        reason = 'unnamed holder, backdated acquire'; host = $env:COMPUTERNAME
        sessionId = "pid-$PID"; transcriptPath = $null
    } | ConvertTo-Json -Compress
    Set-Content -LiteralPath $pidLockPath -Value $pidLockBody -Encoding utf8NoBOM
    $ownBackdatedLock = Get-AgentLockStatus -Name $domain
    Write-Verdict -Label 'self-recognition: an unnamed caller own pid-owned lock stays live past LockStaleMinutes' `
        -Ok ($ownBackdatedLock.Exists -and -not $ownBackdatedLock.Stale) `
        -Detail "(exists=$($ownBackdatedLock.Exists), stale=$($ownBackdatedLock.Stale), owner=$($ownBackdatedLock.SessionId), backdated ${backdatedMinutes}m vs LockStaleMinutes=$($timings.LockStaleMinutes))"

    # 32 - The liveness answer behind case 31, stated directly: an unnamed caller is told
    #      'self' about its own pid identity, never 'undetermined'.
    $ownPidLiveness = Get-AgentTicketLiveness -Ticket ([pscustomobject]@{ sessionId = "pid-$PID" }) `
        -StaleMinutes $timings.SessionStaleMinutes
    Write-Verdict -Label 'liveness: an unnamed caller is told self about its own pid identity' `
        -Ok ($ownPidLiveness -eq 'self') `
        -Detail "(answer=$ownPidLiveness, expected=self)"

    # S2422: the pid- block is over - every case below is about a shape the suite writes by hand or
    # about a named session, so it must see the identity the caller really has.
    Set-SzaEnv 'AGENT_HOST_WALK' $callerHostWalk

    # 33 - Release across the process boundary: acquire and release are different pwsh
    #      processes, so a pid- owner can never be pid-matched by its own closure. An unnamed
    #      releaser must free an unnamed owner (pre-fix parity: a null owner was releasable by
    #      anyone), while a NAMED releaser must leave that live lock in place.
    Reset-Sandbox
    $foreignPidLockPath = Join-Path $sandbox "temp/$($domain.ToUpper()).LOCK"
    $foreignPidBody = [ordered]@{
        schema = 2; lockType = $domain; pid = 424242
        procStart = 0
        acquiredAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        reason = 'foreign unnamed holder'; host = $env:COMPUTERNAME
        sessionId = 'pid-424242'; transcriptPath = $null
    } | ConvertTo-Json -Compress
    Set-Content -LiteralPath $foreignPidLockPath -Value $foreignPidBody -Encoding utf8NoBOM
    Exit-AgentLock -Name $domain
    $unnamedReleaseWorked = -not (Test-Path -LiteralPath $foreignPidLockPath)
    # Restore the holder only when the first half removed it, so the named-release half has a
    # live lock to be refused on; if the first half already failed the file is still there.
    if ($unnamedReleaseWorked) {
        Set-Content -LiteralPath $foreignPidLockPath -Value $foreignPidBody -Encoding utf8NoBOM
    }
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    Exit-AgentLock -Name $domain
    $namedReleaseRefused = Test-Path -LiteralPath $foreignPidLockPath
    Write-Verdict -Label 'release: an unnamed closure frees an unnamed owner, a named session leaves it held' `
        -Ok ($unnamedReleaseWorked -and $namedReleaseRefused) `
        -Detail "(unnamed released=$unnamedReleaseWorked, named left it in place=$namedReleaseRefused)"

    # ---- S2403: one intent, one ticket - handoff across identities ------------------------------

    # 34 - Save/Read round trip across identities: a handoff written by one identity is adopted
    #      by another without creating a second ticket. Pre-fix the instructed waiter could not
    #      see the first ticket (pid identities never match across processes), so it enqueued a
    #      ticket of its own beside it.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $handoffTickets = New-AgentLockTicketSet -Name $domain -Reason 'S2403 case 34 intent'
    $handoffPath = Save-AgentLockTicketHandoff -Tickets $handoffTickets -Reason 'S2403 case 34 intent'
    Remove-Item Env:\CLAUDE_CODE_SESSION_ID -ErrorAction SilentlyContinue
    $adopted = Read-AgentLockTicketHandoff -Path $handoffPath -Domains @($domain)
    $queueAfterAdopt = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'handoff: another identity adopts the ticket without enqueueing a second one' `
        -Ok ($adopted -and $adopted.ContainsKey($domain) -and
            ([int]$adopted[$domain].seq -eq [int]$handoffTickets[$domain].seq) -and
            ($queueAfterAdopt.Count -eq 1)) `
        -Detail "(adopted seq=$($adopted[$domain].seq), original seq=$($handoffTickets[$domain].seq), queue length=$($queueAfterAdopt.Count))"

    # 35 - A partially consumed handoff: the consumed domain's seq is not adopted, the live one
    #      is - the caller enqueues only what is actually missing.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $twoDomainTickets = New-AgentLockTicketSet -Name 'Code' -Reason 'S2403 case 35 intent'
    Remove-Item -LiteralPath $twoDomainTickets['Code.Phone'].path -Force
    $twoHandoffPath = Save-AgentLockTicketHandoff -Tickets $twoDomainTickets -Reason 'S2403 case 35 intent'
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $partialAdopt = Read-AgentLockTicketHandoff -Path $twoHandoffPath -Domains @('Code.Phone', 'Code.Scripts')
    Write-Verdict -Label 'handoff: a consumed seq is not adopted, a live one is' `
        -Ok ($partialAdopt -and (-not $partialAdopt.ContainsKey('Code.Phone')) -and $partialAdopt.ContainsKey('Code.Scripts')) `
        -Detail "(adopted domains: $(if ($partialAdopt) { $partialAdopt.Keys -join ',' } else { 'none' }))"

    # 36 - Absent and expired handoffs read as $null: the caller falls back to enqueuing, which
    #      is the pre-S2403 behaviour - the file can only make things better, never worse.
    Reset-Sandbox
    $missingRead = Read-AgentLockTicketHandoff -Path (Join-Path $sandbox 'temp/LOCK-HANDOFF/absent.json') -Domains @($domain)
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $expiredTickets = New-AgentLockTicketSet -Name $domain -Reason 'S2403 case 36 intent'
    $expiredPath = Save-AgentLockTicketHandoff -Tickets $expiredTickets -Reason 'S2403 case 36 intent'
    $expiredRaw = Get-Content -LiteralPath $expiredPath -Raw | ConvertFrom-Json
    $expiredRaw.createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - [int64](60 * 60000)
    $expiredRaw | ConvertTo-Json -Compress -Depth 4 | Set-Content -LiteralPath $expiredPath -Encoding utf8NoBOM
    $expiredRead = Read-AgentLockTicketHandoff -Path $expiredPath -Domains @($domain)
    Write-Verdict -Label 'handoff: absent and expired files read as null (fresh-enqueue fallback)' `
        -Ok (($null -eq $missingRead) -and ($null -eq $expiredRead)) `
        -Detail "(absent read=$($null -eq $missingRead), expired read=$($null -eq $expiredRead))"

    # 37 - The incident end to end: a pid-owned ticket from a dead enter-code-lock process at
    #      the head, a handoff naming it, and a DIFFERENT identity waiting on it. One ticket, a
    #      head-of-queue grant with no reservation-window self-wait, the lock taken and the
    #      queue left empty.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = 'pid-424242'
    $intentTickets = New-AgentLockTicketSet -Name $domain -Reason 'S2403 case 37 intent'
    $intentHandoff = Save-AgentLockTicketHandoff -Tickets $intentTickets -Reason 'S2403 case 37 intent'
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    $waiterAdopt = Read-AgentLockTicketHandoff -Path $intentHandoff -Domains @($domain)
    $waiterTurn = Test-AgentLockTurn -Name $domain -Ticket $waiterAdopt[$domain]
    $took = Enter-AgentLock -Name $domain -Reason 'S2403 case 37 waiter takes it' -Ticket $waiterAdopt[$domain]
    $queueEnd = @(Get-AgentLockQueue -Name $domain)
    Write-Verdict -Label 'handoff incident: one ticket across the process boundary, no self-wait, acquire retires it' `
        -Ok ($waiterAdopt -and $waiterTurn.IsMyTurn -and ($waiterTurn.Reason -eq 'head of queue') -and
            $took.Acquired -and ($queueEnd.Count -eq 0)) `
        -Detail "(turn reason='$($waiterTurn.Reason)', acquired=$($took.Acquired), queue after acquire=$($queueEnd.Count))"

    # ---- S2410: the refusal must name the domain that actually holds the set --------------------

    # 38 - The defect itself. A is head in every Code queue, so Test-AgentLockTurnSet reports
    #      BlockingDomain = $null - correctly, it answers about queues. The set is blocked all the
    #      same, by B's LOCK on Code.Scripts. The old fallback answered $acquireDomains[0], which
    #      is Code.Phone, and the same refusal went on to call Code.Phone free.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](Enter-AgentLock -Name 'Code' -Reason 'B edits scripts' -Domains @('Code.Scripts'))
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $s2410Set = New-AgentLockTicketSet -Name 'Code' -Reason 'A wants the whole set'
    $s2410Turn = Test-AgentLockTurnSet -Name 'Code' -Tickets $s2410Set
    $s2410Held = Get-AgentLockBlockingDomain -Domains @('Code.Phone', 'Code.Wear', 'Code.Scripts') -Turn $s2410Turn
    Write-Verdict -Label 'blocking domain: the held domain is named and the free first domain is not' `
        -Ok (($s2410Held -eq 'Code.Scripts') -and ($s2410Held -ne 'Code.Phone') -and $s2410Turn.IsMyTurn) `
        -Detail "(answer=$s2410Held, turn BlockingDomain=$($s2410Turn.BlockingDomain), head everywhere=$($s2410Turn.IsMyTurn), expected=Code.Scripts)"

    # 39 - No lock anywhere, but a foreign ticket sits ahead of A's in one queue. The lock walk
    #      finds nothing, so the answer comes from the turn object - the second of the three
    #      outcomes, and the only one the old fallback could reach correctly.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](New-AgentLockTicket -Name 'Code.Scripts' -Reason 'B queued first')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $s2410Behind = New-AgentLockTicketSet -Name 'Code' -Reason 'A queues behind B'
    $s2410BehindTurn = Test-AgentLockTurnSet -Name 'Code' -Tickets $s2410Behind
    $s2410Queued = Get-AgentLockBlockingDomain -Domains @('Code.Phone', 'Code.Wear', 'Code.Scripts') -Turn $s2410BehindTurn
    Write-Verdict -Label 'blocking domain: with no lock held the foreign queue head names the domain' `
        -Ok (($s2410Queued -eq 'Code.Scripts') -and (-not $s2410BehindTurn.IsMyTurn)) `
        -Detail "(answer=$s2410Queued, turn BlockingDomain=$($s2410BehindTurn.BlockingDomain), IsMyTurn=$($s2410BehindTurn.IsMyTurn))"

    # 40 - The third outcome, the one with no domain to name: nothing is held and A is head in
    #      every queue. $null is the honest answer - the caller must name the whole set instead of
    #      inventing a domain, which is what produced the all-empty 'Queue head:' line pre-fix.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $s2410Free = New-AgentLockTicketSet -Name 'Code' -Reason 'A alone in every queue'
    $s2410FreeTurn = Test-AgentLockTurnSet -Name 'Code' -Tickets $s2410Free
    $s2410Answer = Get-AgentLockBlockingDomain -Domains @('Code.Phone', 'Code.Wear', 'Code.Scripts') -Turn $s2410FreeTurn
    Write-Verdict -Label 'blocking domain: nothing held and head everywhere answers null, not a domain' `
        -Ok ($null -eq $s2410Answer) `
        -Detail "(answer=$(if ($null -eq $s2410Answer) { 'null' } else { $s2410Answer }), IsMyTurn=$($s2410FreeTurn.IsMyTurn))"
}
finally {
    if ($null -eq $callerSessionId) { Remove-Item Env:\CLAUDE_CODE_SESSION_ID -ErrorAction SilentlyContinue }
    else { $env:CLAUDE_CODE_SESSION_ID = $callerSessionId }
    # S2422: the same restore for the two identity steps the run silences. Set-SzaEnv with $null
    # removes the variable, so an absent caller value stays absent rather than becoming empty - a
    # blank FMS_AGENT_HOST_WALK would not read as 'disabled', but a blank FMS_AGENT_ID would still
    # be an unset id only by accident of the whitespace check.
    Set-SzaEnv 'AGENT_ID' $callerAgentId
    Set-SzaEnv 'AGENT_HOST_WALK' $callerHostWalk
    Restore-CallerProfilePath
}

Write-Host ""
if ($failures -eq 0) {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    # S2421: the skip count rides in the green line rather than a separate one, because the summary
    # is the part that gets quoted - a run that observed three fewer behaviours must not be
    # quotable as if it had observed them all.
    $skipNote = if ($skips -gt 0) { " $skips skipped - see the SKIP lines above." } else { '' }
    Write-Host "test-agent-lock-queue: expected: 0 | actual: 0 failures ($passes assertions passed).$skipNote Sandbox removed." -ForegroundColor Green
    exit 0
}

Write-Error "test-agent-lock-queue: $failures of $($passes + $failures) assertions FAILED. Sandbox kept: $sandbox" -ErrorAction Continue
exit 1
