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

.EXIT CODES
    0 - every assertion passed; the sandbox is deleted.
    1 - at least one assertion failed; the sandbox is kept for inspection and its path is printed.
    2 - the sandbox could not be prepared, so nothing was checked.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1
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
    # never decide the next one's verdict.
    $lockPath = Join-Path $sandbox 'temp/CODE.LOCK'
    if (Test-Path -LiteralPath $lockPath) { Remove-Item -LiteralPath $lockPath -Force }
    $queueDir = Join-Path $sandbox 'temp/CODE.QUEUE'
    if (Test-Path -LiteralPath $queueDir) { Get-ChildItem -LiteralPath $queueDir -Filter '*.json' | Remove-Item -Force }
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
        [string]$TranscriptPath = $null,
        [switch]$OldShape
    )

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $ticket = [ordered]@{
        schema         = 1
        seq            = $Seq
        lockType       = 'Code'
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

    $queueDir = Get-AgentLockQueueDir -Name Code
    $path = Join-Path $queueDir ('{0:0000}__{1}.json' -f $Seq, $SessionId)
    Set-Content -LiteralPath $path -Value ($ticket | ConvertTo-Json -Compress) -Encoding utf8NoBOM
    return $path
}

try {
    $timings = Get-AgentLockTimings -Name Code

    # 1 - Acquiring the lock retires every ticket of the acquiring session (strategic §11.1).
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    # The pre-fix queue really did accumulate several tickets for one session (strategic §0 records
    # #1, #3 and #5), so the check acquires with the HEAD ticket and demands the siblings go too -
    # retiring only the ticket handed in is precisely the defect.
    $headTicket = New-AgentLockTicket -Name Code -Reason 'previous step, abandoned' -ForceNew
    $extraTicket = New-AgentLockTicket -Name Code -Reason 'another abandoned step' -ForceNew
    $acquired = Enter-AgentLock -Name Code -Reason 'this step' -Ticket $headTicket
    $remaining = @(Get-AgentLockQueue -Name Code)
    Write-Verdict -Label 'ticket retirement: acquiring the lock leaves no ticket of the acquiring session' `
        -Ok ($acquired.Acquired -and $remaining.Count -eq 0) `
        -Detail "(acquired=$($acquired.Acquired), tickets left=$($remaining.Count), had #$($headTicket.seq) and #$($extraTicket.seq))"

    # 2 - A session releasing and immediately re-requesting does not step over an existing waiter
    #     (strategic §11.2). This is the starvation loop itself, stated as a check.
    Reset-Sandbox
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    [void](Enter-AgentLock -Name Code -Reason 'A holds it')
    $env:CLAUDE_CODE_SESSION_ID = $sessionB
    [void](New-AgentLockTicket -Name Code -Reason 'B waits')
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    Exit-AgentLock -Name Code
    $aTicket = New-AgentLockTicket -Name Code -Reason 'A wants it back'
    $aRetry = Enter-AgentLock -Name Code -Reason 'A wants it back' -Ticket $aTicket
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
    $survivors = @(Get-AgentLockQueue -Name Code)
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
    $afterCeiling = @(Get-AgentLockQueue -Name Code)
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
    $afterStaleOwner = @(Get-AgentLockQueue -Name Code)
    Write-Verdict -Label 'stale-owner eviction: an expired heartbeat removes a ticket before it blocks the queue' `
        -Ok ($afterStaleOwner.Count -eq 0) `
        -Detail "(survivors=$($afterStaleOwner.Count), heartbeat age=${staleHeartbeat}m vs SessionStaleMinutes=$($timings.SessionStaleMinutes))"

    # 6 - A ticket written before this change still reads, orders and survives (strategic §11.9,
    #     ADR-5). The fix lands in a tree where sibling sessions already hold old-shape tickets.
    Reset-Sandbox
    [void](New-SyntheticTicket -Seq 1 -SessionId 'sandbox-session-legacy' -AgeMinutes 1 -OldShape)
    [void](New-SyntheticTicket -Seq 2 -SessionId 'sandbox-session-modern' -AgeMinutes 0 -HeartbeatAgeMinutes 0)
    $env:CLAUDE_CODE_SESSION_ID = $sessionA
    $mixed = @(Get-AgentLockQueue -Name Code)
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
            $null = Test-AgentLockTurn -Name Code
        }
    }
    catch {
        $strictError = $_.Exception.Message
    }
    Write-Verdict -Label 'strict mode: a pre-S1448 queue head is stamped without a property-probe error' `
        -Ok ([string]::IsNullOrEmpty($strictError)) `
        -Detail $(if ($strictError) { "(threw: $strictError)" } else { '(no error under Set-StrictMode -Version Latest)' })
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
