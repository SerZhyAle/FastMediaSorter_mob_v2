<#
.SYNOPSIS
    Cross-agent coordination locks: temp/BUILD.LOCK and temp/CODE.LOCK.

.DESCRIPTION
    Dot-source this file to get Enter-AgentLock / Exit-AgentLock / Get-AgentLockStatus /
    Enter-BuildLockOrExit. No top-level side effects other than resolving the repo root once.

    Two independent locks, deliberately different enforcement strength:
      - Build: a real OS process owns it for its whole lifetime, so staleness is judged by
        PID liveness (never by a guessed timeout while the process is actually alive - real
        builds legitimately run 10-25+ minutes). Enforced HARD - a leaf gradle-invoking script
        that can't acquire it must exit non-zero (see Enter-BuildLockOrExit).
      - Code: there is no persistent process to check (a Claude Code editing turn is not one
        continuous OS process - nothing runs between tool calls), so staleness is wall-clock
        only. Enforced SOFT by convention (CLAUDE.md rule + skills) - a build script that finds
        it fresh only warns, it never refuses, since nothing guarantees timely release.

    Lock file schema (single-line JSON, mirrors the existing .claude/scheduled_tasks.lock
    precedent):
      {"lockType":"Build","pid":12345,"procStart":<ticks>,"acquiredAt":<unix-ms>,
       "reason":"build-debug.PS1","host":"<COMPUTERNAME>"}

    S1432 adds a third state between free and held: QUEUED. Each lock has a queue directory
    temp/<NAME>.QUEUE holding one ticket file per waiter, named <seq:0000>__<sessionId>.json:
      {"schema":1,"seq":7,"lockType":"Build","sessionId":"<uuid>","host":"<COMPUTERNAME>",
       "pid":12345,"reason":"a.ps1 d","enqueuedAt":<unix-ms>,"transcriptPath":"<path|null>"}
    A ticket's owner is an agent SESSION, not a process: the waiter that holds a place exits
    the moment the turn arrives (its exit IS the "your turn" signal), so process liveness can
    never identify a live queue member. Liveness comes from the owning session's transcript
    write time - a live session appends to it every turn - with transcriptPath resolved once at
    enqueue time so a poll never rescans ~/.claude/projects. Timings per resource live in
    $Script:AgentLockTimings.

.EXAMPLE
    . "$PSScriptRoot\..\utils\agent-lock.ps1"
    Enter-BuildLockOrExit -Reason "build-debug.PS1"
    try {
        # ... existing gradle-invoking body ...
    } finally {
        Exit-AgentLock -Name Build
    }

.NOTES
    Exit codes: 0 dot-sourced successfully; 2 invoked as a script instead of being dot-sourced
    (see the direct-invocation guard below - this file exposes no command-line interface).
#>

# S1505: this file is a library, not a CLI - it has no param() block and no verb dispatch, so
# `pwsh -File agent-lock.ps1 -Name Code -Action Release` used to bind nothing, define the
# functions, print nothing and exit 0. That reads as a successful release while the lock stays
# held; observed holding CODE.LOCK for 479s across a whole implementation phase. A wrong call
# must fail loudly rather than look like a working one, so refuse anything that is not a
# dot-source. Dot-sourcing reports InvocationName '.' even when the dot-sourcing script was
# itself started with `pwsh -File`, which is how every real consumer loads this file.
if ($MyInvocation.InvocationName -ne '.') {
    $guardMessage = @(
        "agent-lock.ps1 is a dot-source library and has no command-line interface.",
        "Running it as a script does nothing at all - it cannot acquire or release any lock.",
        "",
        "Release the code lock:   pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1",
        "Acquire the code lock:   pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason '<why>'",
        "Inspect a lock:          pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name <Build|Code> -Queue",
        "Clear a stuck lock:      pwsh -NoProfile -File scripts/utils/clear-agent-lock.ps1 -Name <Build|Code> -Force",
        "",
        "From a script, load the functions instead:",
        "    . `"`$PSScriptRoot\..\utils\agent-lock.ps1`"",
        "    Exit-AgentLock -Name Code"
    ) -join [Environment]::NewLine
    Write-Error $guardMessage -ErrorAction Continue
    exit 2
}

function Resolve-AgentLockRepoRoot {
    # Prefer git's common-dir parent so every linked worktree shares ONE temp/BUILD.LOCK and
    # temp/CODE.LOCK. Fallback to the current checkout root when git is unavailable.
    $repoCandidate = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    try {
        $gitCommonDir = (& git -C $repoCandidate rev-parse --path-format=absolute --git-common-dir 2>$null)
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($gitCommonDir)) {
            $commonDir = "$gitCommonDir".Trim()
            if (Test-Path -LiteralPath $commonDir) {
                return Split-Path -Parent $commonDir
            }
        }
    }
    catch {
    }
    return $repoCandidate
}

# Captured once, at dot-source time, from THIS file's own location - not re-derived inside
# functions, where $PSScriptRoot would otherwise be ambiguous across a dot-sourcing boundary.
$Script:AgentLockRepoRoot = Resolve-AgentLockRepoRoot

# Codex exposes the same stable turn identity under a different environment name. Normalise it
# once for existing lock and spec scripts, whose child processes inherit this environment.
if ([string]::IsNullOrWhiteSpace($env:CLAUDE_CODE_SESSION_ID) -and
    -not [string]::IsNullOrWhiteSpace($env:CODEX_SESSION_ID)) {
    $env:CLAUDE_CODE_SESSION_ID = $env:CODEX_SESSION_ID
}

function Get-AgentLockPath {
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name)
    $tempDir = Join-Path $Script:AgentLockRepoRoot 'temp'
    if (-not (Test-Path -LiteralPath $tempDir)) {
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    }
    return Join-Path $tempDir "$($Name.ToUpper()).LOCK"
}

# Per-resource timings (S1432). Build keeps the pre-existing ceilings: a real build legitimately
# runs 10-25+ minutes, and a session waiting on one writes nothing to its transcript meanwhile.
# Code is deliberately shorter - an edit is a short burst of writes, not a long-running process,
# so a code ticket that has sat for 20 minutes is far more likely abandoned than working.
#
# S2098: TicketCeilingMinutes is declared for Build and Code below but read by NO queue consumer -
# only ticket-lease.ps1 and device-lease.ps1 apply the field. Queue eviction
# (Remove-StaleAgentLockTickets) judges the OWNER, never the ticket's age. That is deliberate, not
# an oversight: a legitimate wait behind one long build, or behind several queued builds, outlasts
# both numbers, so applying them would evict a session waiting exactly as the contract demands.
# The remedy for a ticket whose owner is alive but whose intent was dropped is therefore explicit,
# not a timer - scripts/utils/withdraw-lock-ticket.ps1. Do not wire these two values into the
# queue sweep without redoing that reasoning; an unannounced non-application reads as a working
# safety net, which is how the 2026-08-27 stall went unlooked-for.
$Script:AgentLockTimings = @{
    Build = [pscustomobject]@{
        LockStaleMinutes    = 60
        TicketCeilingMinutes = 60
        ReservationMinutes   = 5
        SessionStaleMinutes  = 45
    }
    Code  = [pscustomobject]@{
        LockStaleMinutes    = 10
        TicketCeilingMinutes = 20
        ReservationMinutes   = 3
        SessionStaleMinutes  = 15
    }
    # S1437: a spec-ticket lease has no lock file and no queue, so LockStaleMinutes and
    # ReservationMinutes do not apply and stay 0. SessionStaleMinutes matches the round-state
    # window rather than Code's, because a lease spans a whole ticket - research, spec, edits,
    # a release-scale build - and a session waiting on one writes nothing for tens of minutes.
    # Expiring it on Code's 15 would hand a working session's ticket to a sibling mid-edit.
    SpecTicket = [pscustomobject]@{
        LockStaleMinutes    = 0
        TicketCeilingMinutes = 480
        ReservationMinutes   = 0
        SessionStaleMinutes  = 45
    }
    # S1926: a device lease has the same shape as a spec-ticket lease - no lock file, no queue -
    # so LockStaleMinutes and ReservationMinutes stay 0 for the same reason.
    #
    # SessionStaleMinutes matches SpecTicket rather than Code: a session driving a device scenario
    # spends long stretches building and installing an APK without writing anything, and Code's
    # 15-minute window would evict it mid-install and hand its device to a sibling.
    #
    # TicketCeilingMinutes is far shorter than SpecTicket's 480 because the resource is held for a
    # scenario, not for a ticket's whole life - research, spec writing and gates need no device, so
    # a lease still standing after two hours is an abandoned one, not a slow one.
    Device = [pscustomobject]@{
        LockStaleMinutes    = 0
        TicketCeilingMinutes = 120
        ReservationMinutes   = 0
        SessionStaleMinutes  = 45
    }
}

function Get-AgentLockTimings {
    <#
    .SYNOPSIS
        Timings for one lock. Single source for every minute value in this file.
    #>
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code', 'SpecTicket', 'Device')][string]$Name)
    return $Script:AgentLockTimings[$Name]
}

function Get-AgentLockQueueDir {
    <#
    .SYNOPSIS
        Queue directory for a lock, created on demand. Shares the repo root with the lock file
        itself, so every linked worktree orders itself through ONE queue.
    #>
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name)
    $queueDir = Join-Path (Join-Path $Script:AgentLockRepoRoot 'temp') "$($Name.ToUpper()).QUEUE"
    if (-not (Test-Path -LiteralPath $queueDir)) {
        New-Item -ItemType Directory -Path $queueDir -Force | Out-Null
    }
    return $queueDir
}

function Get-AgentSessionId {
    <#
    .SYNOPSIS
        Identity of the calling agent session, never empty.
    .DESCRIPTION
        No session id in the environment - a plain shell, a cron run, a nested tool - falls back
        to a process-scoped identity so the caller is still distinguishable. Liveness for such an
        identity degrades to its wall-clock ceiling, which is why this returns the fallback rather
        than $null: a caller that must name someone is better served by "pid-1234" than by blank.

        Added by S1596 as the shared accessor for an idiom that had been inlined four times.
        Existing call sites are left as they are - converting them is a separate change.
    #>
    $sessionId = $env:CLAUDE_CODE_SESSION_ID
    if ([string]::IsNullOrWhiteSpace($sessionId)) { return "pid-$PID" }
    return $sessionId
}

function Get-AgentSessionTranscriptPath {
    <#
    .SYNOPSIS
        Full path of an agent session's transcript file, or $null when it cannot be located.
    .DESCRIPTION
        Resolved ONCE, at enqueue time, and stored in the ticket. A queue poll runs every few
        seconds and must read small files only (S1432 strategic 3.2) - rescanning the whole
        ~/.claude/projects tree on each poll would break that.
    #>
    param([string]$SessionId)

    if ([string]::IsNullOrWhiteSpace($SessionId)) { return $null }
    $projectsRoot = Join-Path $env:USERPROFILE '.claude\projects'
    if (-not (Test-Path -LiteralPath $projectsRoot)) { return $null }
    $match = Get-ChildItem -Path $projectsRoot -Recurse -Filter "$SessionId.jsonl" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($match) { return $match.FullName }
    return $null
}

function New-AgentLockTicket {
    <#
    .SYNOPSIS
        Take a place in the queue for a lock. Returns the ticket (with its file path attached).
    .DESCRIPTION
        The sequence number is claimed with FileMode.CreateNew - the same atomic test-and-set
        Enter-AgentLock uses - so two sessions enqueueing at the same instant cannot end up
        holding the same number. Losing the race is not an error: recompute and retry.
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
        [Parameter(Mandatory)][string]$Reason,
        # Take a second place in the same queue anyway. Only a test harness simulating several
        # independent agents from one session needs this.
        [switch]$ForceNew
    )

    $queueDir = Get-AgentLockQueueDir -Name $Name
    $sessionId = $env:CLAUDE_CODE_SESSION_ID
    # No session id in the environment: fall back to a process-scoped identity so the ticket is
    # still distinguishable. Liveness for such a ticket degrades to its wall-clock ceiling.
    if ([string]::IsNullOrWhiteSpace($sessionId)) { $sessionId = "pid-$PID" }
    $transcriptPath = Get-AgentSessionTranscriptPath -SessionId $env:CLAUDE_CODE_SESSION_ID

    # One session, one place. A skill that asks twice (checked, went off to do lock-free work,
    # came back and asked again) must keep the place it already earned, not take a second one -
    # observed live: a sibling session held two consecutive tickets in the Code queue, which
    # both inflates the queue and lets one agent occupy two turns.
    if (-not $ForceNew) {
        $existing = @(Get-AgentLockQueue -Name $Name | Where-Object { [string]$_.sessionId -eq $sessionId })
        if ($existing.Count -gt 0) {
            return $existing[0]
        }
    }

    for ($attempt = 1; $attempt -le 50; $attempt++) {
        $highest = 0
        foreach ($file in @(Get-ChildItem -LiteralPath $queueDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
            if ($file.Name -match '^(\d+)__') {
                $parsed = [int]$Matches[1]
                if ($parsed -gt $highest) { $highest = $parsed }
            }
        }
        $seq = $highest + 1
        $path = Join-Path $queueDir ('{0:0000}__{1}.json' -f $seq, $sessionId)

        try {
            $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
        }
        catch [System.IO.IOException] {
            continue
        }

        $ticket = [ordered]@{
            schema         = 1
            seq            = $seq
            lockType       = $Name
            sessionId      = $sessionId
            host           = $env:COMPUTERNAME
            pid            = $PID
            reason         = $Reason
            enqueuedAt     = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            transcriptPath = $transcriptPath
        }
        try {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes(($ticket | ConvertTo-Json -Compress))
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush()
        }
        finally {
            $stream.Close()
        }

        $issued = [pscustomobject]$ticket
        $issued | Add-Member -NotePropertyName 'path' -NotePropertyValue $path -Force
        return $issued
    }

    throw "agent-lock: could not claim a $Name queue sequence number after 50 attempts"
}

function Get-AgentTicketLiveness {
    <#
    .SYNOPSIS
        Verdict on a ticket's owner: self | foreign-live | foreign-stale | undetermined.
    .DESCRIPTION
        Same vocabulary as scripts/spec_catalog/spec-next-session.ps1, and for the same reason:
        the owner is a session, not a process, so the closest thing to a heartbeat is the write
        time of that session's transcript. Falls back to the ticket's own enqueue time when the
        transcript is unreachable (different machine, pruned history).

        'undetermined' means WE have no session id, so "mine" and "theirs" are indistinguishable -
        it must never be treated as grounds for eviction.

        S1448 - liveness signal precedence, strongest first:
          1. the ticket's own lastSeenAt heartbeat, written by the polling waiter that owns it;
          2. the owning session's transcript write time;
          3. the ticket's enqueuedAt, when neither of the above is readable.
        The heartbeat leads because a session that waits by the contract - background waiter plus
        lock-free work - produces no transcript writes, and was therefore being evicted for
        obeying the rules.
    #>
    param(
        [Parameter(Mandatory)]$Ticket,
        # Mandatory on purpose: the threshold belongs to the resource, and every caller reads it
        # from $Script:AgentLockTimings - a default here would be a second source of truth.
        [Parameter(Mandatory)][int]$StaleMinutes
    )

    $owner = if ($Ticket) { [string]$Ticket.sessionId } else { $null }
    if ([string]::IsNullOrWhiteSpace($owner)) { return 'foreign-stale' }

    $sessionId = $env:CLAUDE_CODE_SESSION_ID
    if ([string]::IsNullOrWhiteSpace($sessionId)) { return 'undetermined' }
    if ($owner -eq $sessionId) { return 'self' }

    $lastSeen = $null
    if ($Ticket.PSObject.Properties.Name -contains 'lastSeenAt' -and $Ticket.lastSeenAt) {
        $lastSeen = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Ticket.lastSeenAt).LocalDateTime
    }
    $transcript = [string]$Ticket.transcriptPath
    if ($null -eq $lastSeen -and -not [string]::IsNullOrWhiteSpace($transcript) -and (Test-Path -LiteralPath $transcript)) {
        try { $lastSeen = (Get-Item -LiteralPath $transcript).LastWriteTime }
        catch { $lastSeen = $null }
    }
    if ($null -eq $lastSeen -and $Ticket.enqueuedAt) {
        $lastSeen = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Ticket.enqueuedAt).LocalDateTime
    }
    if ($null -eq $lastSeen) { return 'foreign-stale' }

    $ageMinutes = ((Get-Date) - $lastSeen).TotalMinutes
    if ($ageMinutes -le $StaleMinutes) { return 'foreign-live' }
    return 'foreign-stale'
}

function Remove-StaleAgentLockTickets {
    <#
    .SYNOPSIS
        Drop tickets whose owner is no longer live. Returns the count.
    .DESCRIPTION
        Without this, one closed session parks itself at the head of the queue and every other
        agent waits forever - a worse failure than the contention the queue exists to fix.
        A malformed ticket file is deleted, mirroring how a torn lock file is already treated.
    #>
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name)

    $timings = Get-AgentLockTimings -Name $Name
    $queueDir = Get-AgentLockQueueDir -Name $Name
    $removed = 0

    foreach ($file in @(Get-ChildItem -LiteralPath $queueDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $ticket = $null
        try { $ticket = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop }
        catch { $ticket = $null }

        if ($null -eq $ticket) {
            # Unreadable, but a ticket being rewritten right now looks identical to a corrupt
            # one. Only a file that has stayed unreadable past a grace window is really corrupt;
            # deleting a fresh one would drop a live agent's place in the queue.
            $unreadableForSeconds = ((Get-Date) - $file.LastWriteTime).TotalSeconds
            if ($unreadableForSeconds -gt 60) {
                Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
                $removed++
            }
            continue
        }

        $liveness = Get-AgentTicketLiveness -Ticket $ticket -StaleMinutes $timings.SessionStaleMinutes
        if ($liveness -eq 'foreign-stale') {
            Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
            $removed++
        }
    }

    return $removed
}

function Remove-AgentSessionTickets {
    <#
    .SYNOPSIS
        Drop every queue ticket owned by one session. Returns the count removed.
    .DESCRIPTION
        S1448. Called the instant a session takes the lock. Without it a session that works step
        by step - take lock, close step, immediately queue for the next one - leaves the previous
        step's ticket sitting on the queue head while it holds the lock, and every sibling behind
        that ticket waits for a head that belongs to the current holder. Reads the directory
        directly rather than going through Get-AgentLockQueue: this runs on the acquire path,
        where the eviction sweep's extra work buys nothing.
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
        [Parameter(Mandatory)][string]$SessionId
    )

    if ([string]::IsNullOrWhiteSpace($SessionId)) { return 0 }
    $queueDir = Get-AgentLockQueueDir -Name $Name
    $removed = 0

    foreach ($file in @(Get-ChildItem -LiteralPath $queueDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $ticket = $null
        try { $ticket = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop }
        catch { continue }
        if ([string]$ticket.sessionId -ne $SessionId) { continue }
        Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
        $removed++
    }

    return $removed
}

function Get-AgentLockQueue {
    <#
    .SYNOPSIS
        Surviving tickets for a lock, ordered by sequence number. Evicts first, so every reader
        sees a self-cleaning queue.
    #>
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name)

    [void](Remove-StaleAgentLockTickets -Name $Name)
    $queueDir = Get-AgentLockQueueDir -Name $Name
    $tickets = @()

    foreach ($file in @(Get-ChildItem -LiteralPath $queueDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        try { $ticket = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop }
        catch { continue }
        $ticket | Add-Member -NotePropertyName 'path' -NotePropertyValue $file.FullName -Force
        $tickets += $ticket
    }

    return @($tickets | Sort-Object { [int]$_.seq })
}

function Set-AgentTicketTurnGranted {
    <#
    .SYNOPSIS
        Stamp the moment a ticket became eligible to take the lock. Best-effort, idempotent.
    .DESCRIPTION
        The reservation window must start when the turn ARRIVES, not when the ticket was issued -
        a waiter that legitimately queued behind a 20-minute build would otherwise have its
        reservation expire before it was ever offered the resource. Any process that observes
        "lock free, this ticket is head" stamps it, so no central arbiter is needed. Two
        processes racing here write the same fact, so a lost write costs nothing.
    #>
    param([Parameter(Mandatory)]$Ticket)

    # Property-bag probe, not a direct read: a ticket written before this field existed has no
    # such property, and a caller running under Set-StrictMode turns that read into a terminating
    # error - which took the whole lock queue down with it instead of stamping the ticket.
    if ($Ticket.PSObject.Properties['turnGrantedAt'] -and $Ticket.turnGrantedAt) { return $Ticket }
    $Ticket | Add-Member -NotePropertyName 'turnGrantedAt' -NotePropertyValue ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) -Force
    try {
        $body = $Ticket | Select-Object -ExcludeProperty path | ConvertTo-Json -Compress
        # Write-then-rename, never write in place: a reader that caught a half-written ticket
        # would see malformed JSON, and the sweeper deletes malformed tickets - so an in-place
        # write could evict the very queue head it was stamping. Move-Item -Force is an atomic
        # replace on NTFS, so a reader sees either the old ticket or the new one.
        $staging = "$($Ticket.path).tmp-$PID"
        Set-Content -LiteralPath $staging -Value $body -Encoding utf8NoBOM -ErrorAction Stop
        Move-Item -LiteralPath $staging -Destination $Ticket.path -Force -ErrorAction Stop
    }
    catch {
        # The queue survives a failed stamp: the next observer retries, and the ticket ceiling
        # still bounds how long a head can sit there.
    }
    return $Ticket
}

function Set-AgentTicketHeartbeat {
    <#
    .SYNOPSIS
        Stamp lastSeenAt on a ticket. Best-effort; overwrites any previous value.
    .DESCRIPTION
        S1448. A ticket's owner is a session, and the only heartbeat available for a session used
        to be its transcript write time - which punishes exactly the behaviour the waiting
        contract demands: take a ticket, run the background waiter, do lock-free work. A session
        that waits quietly writes nothing, looks dead at SessionStaleMinutes, and gets evicted
        from a place it earned. The poll loop is proof the waiter is alive, so it records that
        proof here. Unlike Set-AgentTicketTurnGranted this rewrites on every call - it is a
        heartbeat, not a one-shot fact. This stamp deliberately does not extend TicketCeilingMinutes
        (S1448 ADR-3) - but that is moot for a queue, because S2098 found no queue consumer reads
        that field at all: an abandoned head does NOT age out, and the remedy is the owning session
        calling withdraw-lock-ticket.ps1. See the timings table for why applying it would be worse.
    #>
    param([Parameter(Mandatory)]$Ticket)

    if (-not $Ticket -or -not $Ticket.path) { return $Ticket }
    $Ticket | Add-Member -NotePropertyName 'lastSeenAt' -NotePropertyValue ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) -Force
    try {
        $body = $Ticket | Select-Object -ExcludeProperty path | ConvertTo-Json -Compress
        # Write-then-rename for the same reason Set-AgentTicketTurnGranted does it: the sweeper
        # deletes a ticket it cannot parse, so an in-place write could evict the ticket it is
        # stamping. Move-Item -Force is an atomic replace on NTFS.
        $staging = "$($Ticket.path).tmp-$PID"
        Set-Content -LiteralPath $staging -Value $body -Encoding utf8NoBOM -ErrorAction Stop
        Move-Item -LiteralPath $staging -Destination $Ticket.path -Force -ErrorAction Stop
    }
    catch {
        # A missed heartbeat costs nothing: the next poll writes another one, and the ticket
        # ceiling still bounds how long any ticket can sit in the queue.
    }
    return $Ticket
}

function Test-AgentLockTurn {
    <#
    .SYNOPSIS
        Whose turn is it. Returns IsMyTurn / Position / HeadSessionId / Reason.
    .DESCRIPTION
        Ownership of a queued resource belongs to the head of the queue, not to whoever polls
        fastest. A free lock is therefore NOT enough to acquire: a live head that has not yet
        used its reservation window still owns the turn. That window is what survives the gap
        between the "your turn" signal and the moment gradle actually starts.

        S1448: the turn is decided by TICKET identity alone. A caller with no ticket is answered
        from the lock and the head's reservation, never from a session-id match against the head.
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
        $Ticket
    )

    $timings = Get-AgentLockTimings -Name $Name
    $queue = @(Get-AgentLockQueue -Name $Name)

    $position = 0
    if ($Ticket) {
        for ($i = 0; $i -lt $queue.Count; $i++) {
            if ([int]$queue[$i].seq -eq [int]$Ticket.seq) { $position = $i + 1; break }
        }
    }

    if ($queue.Count -eq 0) {
        return [pscustomobject]@{ IsMyTurn = $true; Position = 0; HeadSessionId = $null; Reason = 'queue is empty' }
    }

    $head = $queue[0]
    if ($Ticket) {
        # A ticket is the precise question: is THIS ticket the head. Comparing session ids
        # instead would tell two processes of the SAME session that both of them are next.
        if ([int]$head.seq -eq [int]$Ticket.seq) {
            return [pscustomobject]@{ IsMyTurn = $true; Position = 1; HeadSessionId = $head.sessionId; Reason = 'head of queue' }
        }
    }
    # S1448: a caller WITHOUT a ticket used to inherit the turn whenever the head happened to
    # belong to its own session. That is precisely the starvation shape - the head was the
    # caller's own abandoned ticket from a previous step, so the session handed itself every
    # turn and nobody behind it ever advanced. The turn belongs to a ticket, never to a session.

    $lockStatus = Get-AgentLockStatus -Name $Name
    if ($lockStatus.Exists -and -not $lockStatus.Stale) {
        return [pscustomobject]@{ IsMyTurn = $false; Position = $position; HeadSessionId = $head.sessionId; Reason = 'lock held' }
    }

    [void](Set-AgentTicketTurnGranted -Ticket $head)
    $grantedAt = if ($head.PSObject.Properties['turnGrantedAt'] -and $head.turnGrantedAt) {
        [int64]$head.turnGrantedAt
    } else {
        [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    }
    $reservedForMinutes = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $grantedAt) / 60000.0
    if ($reservedForMinutes -gt $timings.ReservationMinutes) {
        return [pscustomobject]@{ IsMyTurn = $true; Position = $position; HeadSessionId = $head.sessionId; Reason = 'head reservation expired' }
    }

    return [pscustomobject]@{ IsMyTurn = $false; Position = $position; HeadSessionId = $head.sessionId; Reason = 'reserved for queue head' }
}

function Get-AgentLockStatus {
    <#
    .SYNOPSIS
        Read-only status query. Never deletes anything - staleness is only ever reclaimed by
        Enter-AgentLock, at the moment a caller actually wants the lock.
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
        # 0 = use the per-resource default from $Script:AgentLockTimings.
        [int]$StaleMinutes = 0
    )
    if ($StaleMinutes -le 0) {
        $StaleMinutes = (Get-AgentLockTimings -Name $Name).LockStaleMinutes
    }

    $path = Get-AgentLockPath -Name $Name
    $result = [ordered]@{
        Name          = $Name
        Path          = $path
        Exists        = $false
        AgeSeconds    = $null
        Pid           = $null
        ProcessAlive  = $null
        Reason        = $null
        Host          = $null
        AcquiredAtIso = $null
        SessionId     = $null
        Stale         = $false
    }

    if (-not (Test-Path -LiteralPath $path)) {
        return [pscustomobject]$result
    }
    $result.Exists = $true

    try {
        $raw = Get-Content -LiteralPath $path -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        # Corrupt or mid-write content (torn write from a crash) - treat as stale so a
        # legitimate acquirer is never blocked forever by unreadable leftovers.
        $result.Stale = $true
        return [pscustomobject]$result
    }

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $ageSeconds = [math]::Max(0, ($nowMs - [int64]$raw.acquiredAt) / 1000.0)
    $result.AgeSeconds = $ageSeconds
    $result.Pid = [int]$raw.pid
    $result.Reason = [string]$raw.reason
    $result.Host = [string]$raw.host
    $result.AcquiredAtIso = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$raw.acquiredAt).ToLocalTime().ToString('yyyy-MM-ddTHH:mm:ss')

    if ($Name -eq 'Build') {
        $proc = Get-Process -Id ([int]$raw.pid) -ErrorAction SilentlyContinue
        $alive = $false
        if ($proc) {
            try {
                # StartTime match defends against PID reuse (a dead build's PID recycled by an
                # unrelated later process would otherwise look "alive").
                $alive = ($proc.StartTime.Ticks -eq [int64]$raw.procStart)
            }
            catch {
                # Some processes deny StartTime (cross-session/elevated) - treat as alive rather
                # than force-clearing a lock we can't actually disprove.
                $alive = $true
            }
        }
        $result.ProcessAlive = $alive
        $result.Stale = (-not $alive) -or ($ageSeconds -gt ($StaleMinutes * 60))
    }
    else {
        # Code lock: no process to check. S1432 - when the holder stamped a session id, judge it
        # by that session's liveness and let the wall clock be the backstop. A queue behind this
        # lock makes the difference matter: a blindly-expiring lock stalls everyone waiting.
        $result.ProcessAlive = $null
        $result.SessionId = [string]$raw.sessionId
        if ([string]::IsNullOrWhiteSpace($result.SessionId)) {
            # schema 1 - nothing to ask about the owner, so the wall clock is all there is.
            $result.Stale = $ageSeconds -gt ($StaleMinutes * 60)
        }
        else {
            # schema 2 - the owner answers for itself. A LIVE owner keeps its lock however long
            # the edit takes: expiring a working session by the clock would hand its turn to the
            # next agent mid-edit, which is precisely the collision this ticket exists to stop.
            # A dead owner needs no clock either - its transcript stops moving and it goes stale
            # on its own, which is what bounds the wait.
            $liveness = Get-AgentTicketLiveness -Ticket ([pscustomobject]@{
                    sessionId      = $raw.sessionId
                    transcriptPath = $raw.transcriptPath
                    enqueuedAt     = $raw.acquiredAt
                }) -StaleMinutes (Get-AgentLockTimings -Name $Name).SessionStaleMinutes
            $result.Stale = switch ($liveness) {
                'foreign-stale' { $true }
                'undetermined' { $ageSeconds -gt ($StaleMinutes * 60) }
                default { $false }
            }
        }
    }

    return [pscustomobject]$result
}

function Enter-AgentLock {
    <#
    .SYNOPSIS
        Reclaim-if-stale, then atomically acquire. Returns @{ Acquired; Status }.
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
        [Parameter(Mandatory)][string]$Reason,
        # S1338: block until the holder releases instead of refusing. 793 lock-status polls and
        # 48 hand-rolled `until` loops existed only because this function had no way to wait.
        # Default stays single-shot: a caller that cannot afford to block must still fail fast.
        [switch]$Wait,
        [int]$WaitTimeoutSeconds = 900,
        [int]$PollSeconds = 2,
        # S1432: the caller's queue ticket, when it took one. Supplying it makes this acquire
        # obey the queue order; omitting it keeps the historical behaviour for an empty queue.
        $Ticket
    )

    $deadline = (Get-Date).AddSeconds($WaitTimeoutSeconds)
    while ($true) {
        $status = Get-AgentLockStatus -Name $Name
        $lockBusy = ($status.Exists -and -not $status.Stale)
        # A free lock is not enough: a live queue head that has not yet spent its reservation
        # window still owns the turn (S1432). With an empty queue this is always true, so a
        # caller that never enqueues behaves exactly as it did before.
        $turn = Test-AgentLockTurn -Name $Name -Ticket $Ticket
        if (-not $lockBusy -and $turn.IsMyTurn) { break }
        # Staleness is re-judged every iteration, and Get-AgentLockStatus judges Build by PID
        # liveness - so a holder that dies mid-wait is reclaimed on the next poll rather than
        # waited out to the timeout.
        $blockedBy = if ($lockBusy) { 'lock' } else { 'queue-head' }
        if (-not $Wait) {
            return [pscustomobject]@{ Acquired = $false; Status = $status; BlockedBy = $blockedBy; Turn = $turn }
        }
        if ((Get-Date) -ge $deadline) {
            return [pscustomobject]@{ Acquired = $false; Status = $status; WaitTimedOut = $true; BlockedBy = $blockedBy; Turn = $turn }
        }
        Start-Sleep -Seconds $PollSeconds
    }
    if ($status.Exists -and $status.Stale) {
        # Best-effort reclaim of a dead/expired lock before attempting to acquire.
        Remove-Item -LiteralPath $status.Path -Force -ErrorAction SilentlyContinue
    }

    $path = Get-AgentLockPath -Name $Name
    try {
        # FileMode.CreateNew is the atomic test-and-set: it throws IOException if the file
        # already exists, so "does it exist" and "create it" happen as one filesystem call -
        # no Test-Path-then-Write race window.
        $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
    }
    catch [System.IO.IOException] {
        # Lost the race to another process between the staleness check above and this create.
        return [pscustomobject]@{ Acquired = $false; Status = (Get-AgentLockStatus -Name $Name) }
    }

    try {
        $proc = Get-Process -Id $PID
        $ownerSessionId = $env:CLAUDE_CODE_SESSION_ID
        $body = [ordered]@{
            # schema 2 (S1432) adds the session fields. A schema-1 file (no sessionId) still
            # reads correctly and still expires - it just falls back to wall-clock staleness.
            schema         = 2
            lockType       = $Name
            pid            = $PID
            procStart      = $proc.StartTime.Ticks
            acquiredAt     = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            reason         = $Reason
            host           = $env:COMPUTERNAME
            sessionId      = $ownerSessionId
            transcriptPath = (Get-AgentSessionTranscriptPath -SessionId $ownerSessionId)
        } | ConvertTo-Json -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
    }
    finally {
        $stream.Close()
    }

    # The ticket has done its job the moment the lock is held - leaving it in the queue would
    # keep this session at the head and stall everyone behind it (S1432). S1448: sweep EVERY
    # ticket of this session, not only the one handed in - the observed starvation was a ticket
    # from the session's previous step still holding the head while it held the lock. The
    # explicit removal below stays for a ticket whose session id differs from ours.
    $acquiringSessionId = $env:CLAUDE_CODE_SESSION_ID
    if ([string]::IsNullOrWhiteSpace($acquiringSessionId)) { $acquiringSessionId = "pid-$PID" }
    [void](Remove-AgentSessionTickets -Name $Name -SessionId $acquiringSessionId)
    if ($Ticket -and $Ticket.path -and (Test-Path -LiteralPath $Ticket.path)) {
        Remove-Item -LiteralPath $Ticket.path -Force -ErrorAction SilentlyContinue
    }

    # Child processes inherit this, which is how a nested gradle-invoking script recognises that
    # its own ancestor already holds the lock (see the re-entrancy guard in Enter-BuildLockOrExit).
    # S2058: carries the acquirer's process-start ticks alongside its PID, not the PID alone - a
    # bare PID match let a process that merely INHERITED this variable from a long-dead ancestor
    # mistake an unrelated, later, coincidentally-same-PID holder for itself and `return` as if it
    # already held BUILD.LOCK, without queueing or building anything - the same PID-reuse hazard
    # Get-AgentLockStatus already guards against for the lock file's own liveness check below.
    if ($Name -eq 'Build') { $env:FMS_BUILD_LOCK_HELD_BY = "$PID`:$($proc.StartTime.Ticks)" }

    return [pscustomobject]@{ Acquired = $true; Status = (Get-AgentLockStatus -Name $Name) }
}

function Exit-AgentLock {
    <#
    .SYNOPSIS
        Best-effort release. Safe to call unconditionally from a `finally` block, even if the
        lock was never acquired in this process (no-op).

    .DESCRIPTION
        Build and Code have different ownership models, so release differs:
          - Build: acquire and release always happen in the SAME long-running OS process (one
            leaf script's lifetime), so only remove when the recorded pid matches the current
            process - this is what makes it safe to call unconditionally from a `finally`
            without risking deleting a DIFFERENT live build's lock.
          - Code: acquire (scripts/utils/enter-code-lock.ps1) and release
            (scripts/post-change.ps1's finally, or scripts/utils/exit-code-lock.ps1) are
            deliberately DIFFERENT OS processes - each is its own short-lived pwsh invocation,
            with no single process spanning a whole editing turn. A pid-match check here would
            never fire and the lock would never actually be released before its wall-clock
            staleness window. So Code is released unconditionally (best-effort remove) - it is
            already Tier-2/advisory (see agent-lock.ps1 header), so the small risk of racing an
            unrelated concurrent release is an acceptable soft failure mode.
    #>
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name)

    $path = Get-AgentLockPath -Name $Name
    if (-not (Test-Path -LiteralPath $path)) { return }

    if ($Name -eq 'Code') {
        # S1432: release only what this session owns. With a queue behind the lock, an
        # unconditional delete would hand the turn to the wrong session - not merely drop an
        # advisory hint, as it did before. A schema-1 file (no session id) and a lock whose
        # owner is already stale are still removed, so nothing can get permanently stuck.
        $status = Get-AgentLockStatus -Name Code
        if (-not $status.Exists) { return }
        $mySessionId = $env:CLAUDE_CODE_SESSION_ID
        $ownerSessionId = [string]$status.SessionId
        $isMine = [string]::IsNullOrWhiteSpace($ownerSessionId) -or
                  ($mySessionId -and $ownerSessionId -eq $mySessionId) -or
                  $status.Stale
        if (-not $isMine) {
            Write-Host "CODE.LOCK belongs to session $ownerSessionId (reason: '$($status.Reason)') - leaving it in place." -ForegroundColor Yellow
            return
        }
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        return
    }

    try {
        $raw = Get-Content -LiteralPath $path -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
        if ([int]$raw.pid -eq $PID) {
            Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
            # S2058: match the "pid:startTicks" shape Enter-AgentLock now writes - a bare "$PID"
            # comparison here would never match it, leaving the variable set after release and
            # available for a later, unrelated process to inherit and misread as still-live.
            if ($env:FMS_BUILD_LOCK_HELD_BY -match '^(\d+):') {
                if ([int]$Matches[1] -eq $PID) { $env:FMS_BUILD_LOCK_HELD_BY = $null }
            }
        }
    }
    catch {
        # Corrupt/unreadable content - nothing legible depends on it, safe to drop.
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
    }
}

function Resolve-GradleJvmHome {
    <#
    .SYNOPSIS
        Resolve the JVM Gradle will run on, in Gradle's own precedence order.
    .DESCRIPTION
        org.gradle.java.home from the user-level gradle.properties, then the repository one,
        then JAVA_HOME. Returns $null when nothing names a JVM - that is not an error, it
        leaves the choice to Gradle. Reads text only; never launches a JVM.
    #>
    $gradleUserHome = if (-not [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        $env:GRADLE_USER_HOME
    }
    else {
        Join-Path $env:USERPROFILE '.gradle'
    }

    $propertyFiles = @(
        (Join-Path $gradleUserHome 'gradle.properties'),
        (Join-Path $Script:AgentLockRepoRoot 'gradle.properties')
    )

    foreach ($file in $propertyFiles) {
        if (-not (Test-Path -LiteralPath $file)) { continue }
        foreach ($line in (Get-Content -LiteralPath $file -ErrorAction SilentlyContinue)) {
            $trimmed = $line.Trim()
            if ($trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
            if ($trimmed -match '^org\.gradle\.java\.home\s*=\s*(.+)$') {
                return [pscustomobject]@{ Path = $Matches[1].Trim(); Source = $file }
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        return [pscustomobject]@{ Path = $env:JAVA_HOME; Source = 'JAVA_HOME environment variable' }
    }
    return $null
}

function Test-JvmHomeMissingParts {
    <#
    .SYNOPSIS
        Return the names of the JVM parts missing from a candidate JVM home. Empty means usable.
    .DESCRIPTION
        S1425 checks files rather than launching a JVM, because proving it by spawning a process
        would cost that spawn on every build. S1896 gave the same two probes a second caller - the
        launcher JVM - so they live here instead of being written twice and drifting apart.
    #>
    param([Parameter(Mandatory)][string]$JvmHome)

    $missing = @()
    if (-not (@('bin\java.exe', 'bin/java') | Where-Object { Test-Path -LiteralPath (Join-Path $JvmHome $_) })) {
        $missing += 'bin/java(.exe)'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $JvmHome 'lib/jvm.cfg'))) {
        $missing += 'lib/jvm.cfg'
    }
    return , $missing
}

function Resolve-PersistedJavaHomeRepair {
    <#
    .SYNOPSIS
        The persisted JAVA_HOME, for when this process's snapshot of it has gone stale (S1928).
    .DESCRIPTION
        An environment variable inside a running process is a snapshot taken when that process
        started. A long-lived agent session carries the snapshot for hours, so a JDK point-update
        on the machine leaves it naming a directory that no longer exists while the machine's own
        persisted value is already correct. Observed 2026-08-21: every gradle target of a session
        failed identically for the rest of that session, and the fix was to restart the process.

        This is deliberately NOT a fallback-JDK search. It never looks at the disk, never considers
        the Android Studio jbr, and can only ever return a value the operator persisted themselves -
        the very value the stale snapshot is a snapshot OF. Picking a JVM nobody configured is what
        the guard below is right to refuse; refreshing a snapshot is a different act.

        All three conditions are required. A persisted value equal to the snapshot has nothing to
        refresh - the JDK really is gone. An unusable one is not worth taking. An absent one leaves
        the refusal exactly as it was.

        Returns the usable persisted value with the scope it came from, or $null.
    #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$CurrentValue)

    foreach ($scope in @('User', 'Machine')) {
        $candidate = $null
        # Reading a scope is unsupported off Windows and simply yields nothing there.
        try { $candidate = [Environment]::GetEnvironmentVariable('JAVA_HOME', $scope) } catch { continue }
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        if ($candidate -eq $CurrentValue) { continue }
        if ((Test-JvmHomeMissingParts -JvmHome $candidate).Count -gt 0) { continue }
        return [pscustomobject]@{ Path = $candidate; Scope = $scope }
    }
    return $null
}

function Assert-GradleToolchainOrExit {
    <#
    .SYNOPSIS
        Refuse to start gradle when the configured JVM cannot launch. Exits 3.
    .DESCRIPTION
        S1425: a partial Android Studio uninstall left bin/java.exe in place but deleted
        lib/jvm.cfg, so every forked JVM died with "could not open jvm.cfg" while the daemon
        already running kept compiling from memory. Compilation stayed green and the whole
        unit-test tier was down for hours before anything said so. Two Test-Path calls at the
        first gradle call buy that signal back; launching the JVM to prove it would cost a
        process spawn on every build, which is why this checks files only.
    #>
    # S1896: the LAUNCHER JVM first, and separately from the daemon one below. gradlew.bat starts
    # on JAVA_HOME; only after that does Gradle read org.gradle.java.home. Resolve-GradleJvmHome
    # returns the first of (user gradle.properties, repo gradle.properties, JAVA_HOME), so a valid
    # org.gradle.java.home made this function pass while gradlew.bat died on a stale JAVA_HOME -
    # the wrapper printed its own error and nothing here ever looked at that variable.
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $launcherMissing = Test-JvmHomeMissingParts -JvmHome $env:JAVA_HOME
        # S1928: before refusing, ask whether the machine is actually misconfigured or whether only
        # this process's snapshot of JAVA_HOME went stale. The repair is loud on purpose - a silent
        # JVM swap is worse than stopping, and the line below is what makes this a refresh rather
        # than a swap.
        $javaHomeRepair = if ($launcherMissing.Count -gt 0) {
            Resolve-PersistedJavaHomeRepair -CurrentValue $env:JAVA_HOME
        }
        else { $null }
        if ($null -ne $javaHomeRepair) {
            Write-Host "JAVA_HOME snapshot was stale - refreshed from the persisted $($javaHomeRepair.Scope) value." -ForegroundColor Yellow
            Write-Host "  was: $env:JAVA_HOME (missing $($launcherMissing -join ', '))" -ForegroundColor Yellow
            Write-Host "  now: $($javaHomeRepair.Path)" -ForegroundColor Yellow
            Write-Host "  Only this process was changed. Fix the environment your session inherits, or the next one starts stale too." -ForegroundColor Gray
            $env:JAVA_HOME = $javaHomeRepair.Path
            $launcherMissing = @()
        }
        if ($launcherMissing.Count -gt 0) {
            Write-Host "Launcher JVM unusable - refusing to start gradle. Nothing was built." -ForegroundColor Red
            Write-Host "  JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Red
            Write-Host "  Missing: $($launcherMissing -join ', ')" -ForegroundColor Red
            Write-Host "  gradlew.bat launches on JAVA_HOME, before Gradle ever reads org.gradle.java.home," -ForegroundColor Gray
            Write-Host "  so a valid org.gradle.java.home does not rescue this." -ForegroundColor Gray
            Write-Host "  Point JAVA_HOME at a JDK 17, 21, or 25 install, or clear it to let the wrapper search." -ForegroundColor Gray
            exit 3
        }
    }

    $resolved = Resolve-GradleJvmHome
    # Nothing configured anywhere - Gradle picks its own JVM, and there is nothing to verify.
    if ($null -eq $resolved) { return }

    $jvmHome = $resolved.Path
    $missing = Test-JvmHomeMissingParts -JvmHome $jvmHome
    if ($missing.Count -eq 0) { return }

    Write-Host "Toolchain JVM unusable - refusing to start gradle. Nothing was built." -ForegroundColor Red
    Write-Host "  Resolved JVM home: $jvmHome" -ForegroundColor Red
    Write-Host "  Missing: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "  Configured by: $($resolved.Source)" -ForegroundColor Red
    Write-Host "  Point it at a JDK 17, 21, or 25 install (JDK 26 is incompatible with Gradle 9.4.1 / AGP 9.2.1)." -ForegroundColor Gray
    Write-Host "  See the header of gradle.properties and PLAN/S1425_bugfix-unit-tests-broken-gradle-java-home.md." -ForegroundColor Gray
    exit 3
}

function Enter-BuildLockOrExit {
    <#
    .SYNOPSIS
        Convenience for leaf gradle-invoking scripts: verify the toolchain JVM, warn (never
        block) on a fresh CODE.LOCK, then queue for BUILD.LOCK and acquire it in turn.
    .DESCRIPTION
        S1432: waiting is the DEFAULT. A busy lock puts this caller in the queue and it starts
        when its turn comes, because a refusal here surfaces to an agent as a completed
        background task - "the build passed" has repeatedly meant "the build never ran". Pass
        -NoWait (or set FMS_LOCK_NO_WAIT=1) where an immediate answer matters more.

        Exit codes this function can impose on its caller:
          1 - BUILD.LOCK held and fail-fast was requested (-NoWait / FMS_LOCK_NO_WAIT=1).
          2 - the wait ran out of time; nothing was inspected and nothing was built.
          3 - the launcher JVM (JAVA_HOME) or the configured toolchain JVM cannot launch
              (see Assert-GradleToolchainOrExit - it checks both, and they can differ).
        The toolchain check runs FIRST, so a broken environment never takes the lock or a place
        in the queue.
    #>
    param(
        [Parameter(Mandatory)][string]$Reason,
        # S1338 introduced this switch. S1432 made waiting the DEFAULT, so it is accepted and
        # ignored - every existing call site keeps working and now queues instead of refusing.
        [switch]$Wait,
        # S1432: opt out of the queue for a caller that must answer immediately. The environment
        # variable FMS_LOCK_NO_WAIT=1 does the same globally, for unattended runs.
        [switch]$NoWait,
        [int]$WaitTimeoutSeconds = 3600
    )

    Assert-GradleToolchainOrExit

    # Re-entrancy guard (S1432). Several gates run a nested script while already holding
    # BUILD.LOCK, and `& other.ps1` runs in the SAME process - so the nested acquire would now
    # wait for a lock this very run owns and deadlock. Before waiting became the default this
    # was a fast, visible refusal; it must not silently become a hang.
    #
    # S2058: the inherited half of this check is PID plus process-start ticks, not PID alone. A
    # spawned child process inherits FMS_BUILD_LOCK_HELD_BY from whatever ancestor last acquired
    # the lock in-process; a bare PID match against the CURRENT lock file's holder is therefore
    # only safe as long as PIDs are never reused. Windows reuses them quickly, and a long-lived
    # ancestor spawning many short-lived children makes the collision realistic - when it lands,
    # the child mistook an unrelated, later holder for itself and returned as though it already
    # held BUILD.LOCK, without queueing or actually holding it (observed exit 0 on a refusal that
    # should have queued or failed fast). Start ticks are what Get-AgentLockStatus already uses
    # to defend the SAME lock file against PID reuse a few lines below - this mirrors it rather
    # than inventing a second defense.
    $selfCheck = Get-AgentLockStatus -Name Build
    if ($selfCheck.Exists -and -not $selfCheck.Stale) {
        $holderPid = [int]$selfCheck.Pid
        $holderStartTicks = 0
        try {
            $rawLock = Get-Content -LiteralPath $selfCheck.Path -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
            if ($rawLock.PSObject.Properties['procStart']) { $holderStartTicks = [int64]$rawLock.procStart }
        }
        catch {
            # Torn/mid-write lock file - $holderStartTicks stays 0, which never matches a real
            # inherited value below, so the guard falls through to the normal queue/refuse path.
        }
        $inheritedPid = 0
        $inheritedStartTicks = 0
        if ($env:FMS_BUILD_LOCK_HELD_BY -match '^(\d+):(\d+)$') {
            $inheritedPid = [int]$Matches[1]
            $inheritedStartTicks = [int64]$Matches[2]
        }
        $inheritedMatchesHolder = ($inheritedPid -ne 0) -and ($inheritedPid -eq $holderPid) -and
            ($holderStartTicks -ne 0) -and ($inheritedStartTicks -eq $holderStartTicks)
        if ($holderPid -eq $PID -or $inheritedMatchesHolder) {
            Write-Host "BUILD.LOCK already held by this run (pid $holderPid) - reusing it instead of queueing behind ourselves." -ForegroundColor DarkGray
            return
        }
    }

    $codeStatus = Get-AgentLockStatus -Name Code
    if ($codeStatus.Exists -and -not $codeStatus.Stale) {
        Write-Host "Warning: CODE.LOCK present (age $([int]$codeStatus.AgeSeconds)s, reason: '$($codeStatus.Reason)') - a code edit may be in progress elsewhere. This build may reflect a half-written state." -ForegroundColor Yellow
    }

    $failFast = $NoWait -or ($env:FMS_LOCK_NO_WAIT -eq '1')
    $enterArgs = @{ Name = 'Build'; Reason = $Reason }
    $ticket = $null

    if (-not $failFast) {
        # Take a place in the queue BEFORE looking at the lock: ordering is what turns "whoever
        # polls first" into "whoever asked first" (S1432).
        $ticket = New-AgentLockTicket -Name Build -Reason $Reason
        $enterArgs.Wait = $true
        $enterArgs.WaitTimeoutSeconds = $WaitTimeoutSeconds
        $enterArgs.Ticket = $ticket

        $turn = Test-AgentLockTurn -Name Build -Ticket $ticket
        $lockNow = Get-AgentLockStatus -Name Build
        if (-not $turn.IsMyTurn -or ($lockNow.Exists -and -not $lockNow.Stale)) {
            $holder = if ($lockNow.Exists) { "holder pid $($lockNow.Pid), age $([int]$lockNow.AgeSeconds)s, reason '$($lockNow.Reason)'" } else { $turn.Reason }
            Write-Host "BUILD.LOCK busy - queued at position $($turn.Position) (ticket #$($ticket.seq)). Waiting up to ${WaitTimeoutSeconds}s. $holder" -ForegroundColor DarkGray
        }
    }

    $waitStartedAt = Get-Date
    $result = Enter-AgentLock @enterArgs
    if (-not $result.Acquired) {
        $s = $result.Status
        $waitTimedOut = ($result.PSObject.Properties.Name -contains 'WaitTimedOut' -and $result.WaitTimedOut)
        if ($ticket -and $ticket.path) { Remove-Item -LiteralPath $ticket.path -Force -ErrorAction SilentlyContinue }
        if ($waitTimedOut) {
            Write-Host "BUILD.LOCK still not ours after ${WaitTimeoutSeconds}s - giving up without building." -ForegroundColor Red
        }
        else {
            Write-Host "BUILD.LOCK held - refusing to start a second gradle build (fail-fast requested)." -ForegroundColor Red
        }
        Write-Host "  Holder PID: $($s.Pid)  age: $([int]$s.AgeSeconds)s  reason: '$($s.Reason)'  host: $($s.Host)" -ForegroundColor Red
        Write-Host "  Never run two gradle builds concurrently (daemon OOM / cache corruption - see CLAUDE.md)." -ForegroundColor Gray
        Write-Host "  Check status: pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build" -ForegroundColor Gray
        # A wait that ran out of time inspected nothing - exit 2 "cannot verify", not 1 "failed".
        if ($waitTimedOut) { exit 2 }
        exit 1
    }

    $waitedSeconds = [int]((Get-Date) - $waitStartedAt).TotalSeconds
    if ($waitedSeconds -ge 3) {
        Write-Host "BUILD.LOCK acquired after ${waitedSeconds}s in the queue - starting." -ForegroundColor DarkGray
    }
}
