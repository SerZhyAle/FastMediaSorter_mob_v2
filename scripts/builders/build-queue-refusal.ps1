<#
.SYNOPSIS
    Decides whether a foreground-scale build check should refuse a busy build domain instead of
    blocking in it, and renders the refusal an agent can act on.

.DESCRIPTION
    S2612. The foreground/background classification in docs/BUILD_TEST_FAST_PATH.md is measured on
    an EMPTY queue: a target's wall clock is what it costs once it already holds its Build.* domain,
    and the wait in front of it is in none of those numbers. CLAUDE.md Rule 6 pins the classification
    to the Bash tool's own 120 s foreground timeout, so a short target that spends the window queueing
    is killed before it runs - and a killed foreground check reports no verdict at all, which is
    indistinguishable from a check nobody ran.

    Measured 2026-09-06 over 1324 runs reconstructed from temp/check_fast_*.log (2026-09-03..09-06):
    of 972 short-class runs, 529 waited at all, 94 (9.7%) waited past 60 s, 82 (8.4%) past 90 s and
    66 (6.8%) past 120 s. The last group returns nothing today. A 60 s budget refuses 94, of which
    ~82 were already doomed, so the real price is ~12 runs (1.2%) paying one background-wait turn.

    The asymmetry this closes: enter-code-lock.ps1 EXITS 4 on a busy CODE domain and Rule 23 sends
    the agent to wait in the background, while Enter-BuildLockOrExit BLOCKS in-process on a busy
    BUILD domain. Same situation, opposite answers - and only one of them loses the verdict.

    Why the decision lives here and not in the lock. Enter-BuildLockOrExit ships with the sza canon
    plugin (S2402): editing its body is overwritten by the next plugin update and never reaches the
    other consumers. Its -WaitTimeoutSeconds is not a substitute either - on expiry it DELETES the
    caller's queue tickets, so a bounded wait surrenders the place and turns waiting into starvation,
    and it calls `exit` itself, so no caller can append the continuation command to its refusal.
    This file therefore answers BEFORE the lock is entered; the canon default is left untouched, and
    it stays correct for every caller that is not bounded by a 120 s window.

    Nothing here writes the queue, takes a lock or exits. The caller enqueues, prints and exits, in
    that order - the ticket has to exist before the report so the place survives the refusal (S2403:
    a refusal that drops its ticket leaves the retry queueing behind its own dead first one).

    Dot-source this file; it defines functions and never exits.
#>

# Deliberately no Set-StrictMode here: this file is dot-sourced, so it would impose strict mode on
# every consumer's whole scope. Same reasoning as gradle-run-verdict.ps1 next to it.

# The foreground budget is 120 s TOTAL and it is spent on wait plus run, not on wait alone. A short
# target's own measured wall clock is 14-32 s, so the survivable wait is 88-105 s depending on the
# target - not 120. 60 s sits below the tightest of those with margin, and above the 84th percentile
# of everything that waits at all (443 of 529 waiters clear 30 s), so the common case never reaches
# it. Deliberately ONE number rather than 120-minus-this-target's-runtime: the per-target form buys
# about twelve runs per three days and requires this file to carry a copy of the measured table,
# which would then drift from the document that owns it - the same drift CLAUDE.md Rule 8 refuses
# for the flavor matrix.
$script:BuildQueueForegroundBudgetSeconds = 60

# The mode list is the one docs/BUILD_TEST_FAST_PATH.md measures above the 120 s threshold, not a
# judgement made here. Derived from the mode ALONE and never downgraded when -Tests narrows a run:
# the filter's breadth is unknown at this point, and a hold that ends sooner than advertised costs a
# waiter nothing while a "seconds" promise that runs 40 minutes is the failure this classification
# exists to prevent (S2580).
$script:BuildLongHoldModes = @('Unit', 'ConnectedAndroidTest', 'Assemble')

function Get-BuildHoldClass {
    <#
    .SYNOPSIS
        'short' or 'long' for a check mode - the run's own scale, and therefore whether its caller
        is inside the 120 s foreground window.
    .DESCRIPTION
        S2612: this is also the foreground signal, and no second detector exists. Rule 6 sends the
        short set to the foreground and the long set to the background by the same measured table,
        so "short class" and "the caller is bounded by 120 s" are one statement made at two layers.
        The unattended runner is NOT an exception - run-spec-queue.ps1 launches `claude -p`, whose
        agent runs its checks through the same tool with the same timeout.
    #>
    param([Parameter(Mandatory)][string]$Mode)
    if ($script:BuildLongHoldModes -contains $Mode) { return 'long' }
    return 'short'
}

function Get-BuildHoldClassLabel {
    <#
    .SYNOPSIS
        The human-readable hold class for the lock reason string (S2580's wording, unchanged).
    #>
    param([Parameter(Mandatory)][string]$Mode)
    if ((Get-BuildHoldClass -Mode $Mode) -eq 'long') { return 'LONG hold (background-scale)' }
    return 'short hold (foreground-scale)'
}

function Get-BuildQueueBudgetSeconds {
    return $script:BuildQueueForegroundBudgetSeconds
}

function Get-BuildDomainState {
    <#
    .SYNOPSIS
        Per-domain occupancy for a set of build domains: is it busy, and who is in front.
    .DESCRIPTION
        Busy means either a live foreign holder, or a free lock whose queue head belongs to another
        session - the second case is what S1448 added, and skipping it would let a refusal report
        "free" for a domain already promised to somebody else.

        Both lookups are injectable so the decision can be tested without a lock, a queue or a
        second process. Defaults resolve the harness functions the caller has already dot-sourced.
    #>
    param(
        [Parameter(Mandatory)][string[]]$Domains,
        [scriptblock]$StatusProvider,
        [scriptblock]$QueueProvider,
        [string]$SessionId,
        [int]$SelfPid = $PID,
        # '' = this process inherited no build-lock token. Anything else means an ancestor holds a
        # build domain and we are running underneath it.
        [string]$InheritedHolder = '__unset__'
    )

    if (-not $StatusProvider) { $StatusProvider = { param($d) Get-AgentLockStatus -Name $d } }
    if (-not $QueueProvider) { $QueueProvider = { param($d) Get-AgentLockQueue -Name $d } }
    if ($InheritedHolder -eq '__unset__') {
        $InheritedHolder = try { [string](Get-SzaEnv 'BUILD_LOCK_HELD_BY') } catch { '' }
    }
    if (-not $SessionId) {
        # A session id that cannot be resolved must not make every domain look free: an unknown
        # identity is treated as "not the head", which errs towards refusing rather than towards
        # blocking, and blocking is the outcome that loses the verdict.
        $SessionId = try { Get-AgentSessionId } catch { '' }
    }

    $state = @()
    foreach ($domain in $Domains) {
        $status = & $StatusProvider $domain
        $holderLive = $false
        if ($status -and $status.Exists) {
            # A stale lock is not a holder. Judging it busy would refuse a run that could have
            # started immediately, which is the one way this feature can make things slower.
            $holderLive = -not $status.Stale
        }

        # Re-entrancy. Enter-BuildLockOrExit has a guard for the run that ALREADY holds this domain
        # and reuses the lock instead of queueing behind itself - but that guard lives inside the
        # function, and this check runs before it. Without the exemption below, an orchestrator that
        # holds a build domain and then spawns this check as a child would be refused exit 4 for a
        # lock it owns: post-change.ps1 launches check-standard-fast.ps1 as a child process in two
        # places, and FMS_BUILD_LOCK_HELD_BY is inherited exactly so a descendant can recognise it.
        #
        # Deliberately coarser than the harness's own test, which matches pid plus process-start
        # ticks (S2058, against PID reuse). Any inherited token at all is enough to stop refusing,
        # because the two mistakes are not symmetric: refusing a lock we own breaks a working
        # closure, while declining to refuse only restores the blocking behaviour that shipped
        # before this file existed, and the harness's precise guard then decides correctly.
        $selfHeld = $holderLive -and (([int]$status.Pid -eq $SelfPid) -or ($InheritedHolder -ne ''))
        if ($selfHeld) { $holderLive = $false }

        $headForeign = $false
        $headSession = ''
        # A domain this run already holds needs no queue lookup: whoever is queued for it is queued
        # BEHIND us, so reading the head would report our own successor as the thing blocking us.
        if (-not $holderLive -and -not $selfHeld) {
            $queue = @(& $QueueProvider $domain)
            if ($queue.Count -gt 0) {
                $head = $queue[0]
                $headSession = [string]$head.sessionId
                $headForeign = ($headSession -ne '') -and ($headSession -ne $SessionId)
            }
        }

        $state += [pscustomobject]@{
            Domain        = $domain
            Busy          = ($holderLive -or $headForeign)
            SelfHeld      = $selfHeld
            HolderPid     = if ($holderLive) { [int]$status.Pid } else { 0 }
            HolderAge     = if ($holderLive) { [int]$status.AgeSeconds } else { 0 }
            HolderReason  = if ($holderLive) { [string]$status.Reason } else { '' }
            HolderSession = if ($holderLive) { [string]$status.SessionId } else { '' }
            HeadSession   = $headSession
            BlockedBy     = if ($holderLive) { 'holder' } elseif ($headForeign) { 'queue-head' } else { 'none' }
        }
    }
    return $state
}

function Test-BuildQueueRefusal {
    <#
    .SYNOPSIS
        Should this run refuse rather than block. Pure decision over an already-measured state.
    .DESCRIPTION
        Refuse only when all three hold: the run is short class, at least one requested domain is
        busy, and the block-through opt-out is absent. Any one of them missing means the old
        blocking behaviour, so a caller that never sets the opt-out and never runs short checks
        cannot notice this file exists.

        BlockingDomain is the FIRST busy domain in the caller's canonical order, and it is reported
        rather than the first requested one: S2109 records that a refusal about Build.Wear read as a
        refusal about the phone build twice on the two modules' fast checks.
    #>
    param(
        [Parameter(Mandatory)][string]$HoldClass,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$DomainState,
        [switch]$BlockThrough,
        [scriptblock]$EnvProvider
    )

    if (-not $EnvProvider) { $EnvProvider = { param($s) Get-SzaEnv $s } }

    $optedOut = $BlockThrough -or ((& $EnvProvider 'LOCK_BLOCK') -eq '1')
    $busy = @($DomainState | Where-Object { $_.Busy })

    $shouldRefuse = ($HoldClass -eq 'short') -and ($busy.Count -gt 0) -and (-not $optedOut)

    return [pscustomobject]@{
        ShouldRefuse   = $shouldRefuse
        BlockingDomain = if ($busy.Count -gt 0) { $busy[0].Domain } else { '' }
        BlockingState  = if ($busy.Count -gt 0) { $busy[0] } else { $null }
        OptedOut       = [bool]$optedOut
        HoldClass      = $HoldClass
    }
}

function Format-BuildQueueRefusalReport {
    <#
    .SYNOPSIS
        The lines a refused run prints: who is in front, and the exact command that resumes the work.
    .DESCRIPTION
        The continuation command is the whole point. enter-code-lock.ps1's exit 4 prints one, and
        Rule 23 tells the agent to run it in the background and keep doing lock-free work; without
        it a refusal is only a failure the agent has to research. -Acquire is deliberately NOT
        offered for a build domain: the lock's staleness is judged by the acquiring PID and the
        waiter exits immediately, so the lock would read as dead on arrival - the build flow is
        wait-then-rerun, and the rerun adopts the surviving ticket.
    #>
    param(
        [Parameter(Mandatory)][string]$BlockingDomain,
        [object]$BlockingState,
        [Parameter(Mandatory)][string]$Reason,
        [string]$HandoffPath,
        [int]$BudgetSeconds = 0
    )

    if ($BudgetSeconds -le 0) { $BudgetSeconds = Get-BuildQueueBudgetSeconds }

    $lines = @()
    $lines += "$($BlockingDomain.ToUpper()) busy - queued and exiting instead of blocking (foreground-scale check, budget ${BudgetSeconds}s)."
    if ($BlockingState -and $BlockingState.BlockedBy -eq 'holder') {
        $lines += "  Holder PID: $($BlockingState.HolderPid)  age: $($BlockingState.HolderAge)s  reason: '$($BlockingState.HolderReason)'"
    }
    elseif ($BlockingState -and $BlockingState.BlockedBy -eq 'queue-head') {
        $lines += "  $BlockingDomain is free; the turn belongs to session $($BlockingState.HeadSession)."
    }
    $lines += "  Your place in the queue is TAKEN and survives this exit - the rerun adopts it."
    $lines += "  Wait for the turn in the BACKGROUND, then rerun this check:"

    $waitCmd = "pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name $BlockingDomain -Reason `"$Reason`""
    if ($HandoffPath) { $waitCmd += " -Handoff `"$HandoffPath`"" }
    $lines += "    $waitCmd"
    $lines += "  Keep doing lock-free work while it waits (reading, research, specs, catalog) - CLAUDE.md Rule 23."
    $lines += "  Block through instead: -BlockThrough on this call, or FMS_LOCK_BLOCK=1 for a terminal session."
    return $lines
}
