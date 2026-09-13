#requires -Version 7.0
<#
.SYNOPSIS
    S2615 - the Code.* twin of Enter-BuildLockOrExit: let a script that rewrites a render target
    take that target's code domain in one line and give back exactly what it took.

.DESCRIPTION
    Dot-source this file, then wrap the write:

        . "$PSScriptRoot\..\utils\code-lock-scope.ps1"
        $scope = Enter-CodeLockOrExit -Path $targets -Reason 'render-icon-legend.ps1'
        try   { ..write.. }
        finally { Exit-CodeLockScope -Scope $scope }

    Why this exists at all. `Enter-BuildLockOrExit` gives a gradle script its domain in one line;
    the code domains had no such entry point, only the enter-code-lock.ps1 / exit-code-lock.ps1
    CLI pair - two child processes plus a release obligation on every exit branch, including the
    exception one. Measured 2026-09-06: ten scripts rewrite a path the domain table assigns to a
    Code.* domain and NONE of them takes it, while a hand-run enter-code-lock.ps1 on the very same
    path is queued. That is not ten oversights, it is a missing mechanism - and the one script
    that noticed (prune-detekt-baseline.ps1) resolved it by writing in its own header that the
    CALLER owns the lock, a convention nothing checks and no caller obeys.

    Three properties, each of which the strategic spec requires and each of which fails silently
    if dropped:

      - RELEASE ONLY WHAT THIS SCOPE TOOK. exit-code-lock.ps1 releases the whole `Code` set by
        design, so a nested script using it would hand away a domain its own caller still holds.
        The scope object carries the acquired subset and Exit-CodeLockScope passes exactly that
        subset to Exit-AgentLock, which is also why the pre-split legacy lock file is left alone
        (Exit-AgentLock only touches it for a BARE name).
      - RE-ENTRY IS NOT AN ERROR. assert-icon-inventory-sync.ps1 calls render-icon-legend.ps1 and
        export-icon-svgs.ps1; reindex-settings.ps1 calls render-settings-reference.ps1. The inner
        call finds the domain already held by its own session, acquires nothing, and releases
        nothing - so the outer script keeps the lock it owns for the rest of its work.
      - A BUSY DOMAIN IS EXIT 4, NOT A WAIT. Same answer enter-code-lock.ps1 gives for a busy code
        domain and check-standard-fast.ps1 for a busy Build.* (S2612): the place in the queue is
        taken and survives the exit, so the caller waits in the background and reruns. Blocking
        instead would run a foreground generator past its caller's 120 s ceiling and return no
        verdict at all, which reads exactly like a generator nobody ran.

    Acquisition ORDER is not this file's to choose. Domains carry a rank (Build.Phone 1 ..
    Code.Scripts 5) and Enter-AgentLock sorts every set into it, so a script already holding
    Build.Phone may add Code.Scripts (1 -> 5, ascending) but never the reverse; Resolve-AgentLockTopUp
    refuses the descending direction outright as the AB-BA shape canonical order exists to rule out.
    In practice that fixes where the call goes in a gradle-backed generator: AFTER its
    Enter-BuildLockOrExit, never before it.

    Take the lock on the WRITE path only. Every generator in this family is also called in a
    -Check / verify mode that writes nothing, and .\a.ps1 fg runs its gate children concurrently
    (S2451) - a lock taken in verify mode would serialise that battery for no protection at all.

    TWO ENTRY POINTS, and the test that picks between them (S2635). Enter-CodeLockOrExit exits 4
    on a busy domain; Enter-CodeLockOrSkip returns a scope with Skipped = $true and never exits.
    Choose by what NOT writing costs: work left undone -> OrExit, so the caller reports it;
    staleness a later run repairs -> OrSkip. The second exists because assert-source-gates.ps1
    rewrites a ratchet baseline from INSIDE the concurrent fg battery in ordinary gate mode, where
    the gate's verdict is already PASS and only the bookkeeping is blocked.

    Acquire LAZILY when the write is conditional. A lock taken at the top of a gate that writes
    only on a rare branch serialises the battery on every run for a write that almost never
    happens; taken immediately around the write, it costs nothing on the common path.

.NOTES
    Exit codes imposed on the CALLER by Enter-CodeLockOrExit:
      4 - queued, not your turn: nothing was written and nothing is wrong. The place in the queue
          is held. Wait for it in the background with the printed wait-for-lock-turn.ps1 command,
          then rerun. Pass -BlockThrough, or set FMS_LOCK_BLOCK=1, to block instead.
    Enter-CodeLockOrSkip imposes NO exit code - a busy domain is reported and execution continues.
    This file is a dot-source library and returns no exit code of its own; running it as a script
    exits 2 (see the guard below).
#>

# Same guard as agent-lock-domains.ps1 (S1505): a library invoked with `pwsh -File` binds nothing,
# defines nothing the caller can see and exits 0, which reads as a working call.
if ($MyInvocation.InvocationName -ne '.') {
    # Built into a variable first, then printed on the line before the exit, exactly as
    # agent-lock-domains.ps1 does: assert-exit-contract.ps1 reads the statement ADJACENT to an exit
    # to decide whether it explains itself, so a multi-line Write-Error argument reads as silent.
    $codeLockGuardMessage = @(
        "code-lock-scope.ps1 is a dot-source library and has no command-line interface.",
        "Running it as a script does nothing at all - it takes no lock.",
        "",
        "From a script, load the functions instead:",
        "    . `"`$PSScriptRoot\..\utils\code-lock-scope.ps1`"",
        "    `$scope = Enter-CodeLockOrExit -Path `$targets -Reason 'my-generator.ps1'"
    ) -join [Environment]::NewLine
    Write-Error $codeLockGuardMessage -ErrorAction Continue
    exit 2
}

. (Join-Path $PSScriptRoot 'agent-lock.ps1')

function ConvertTo-CodeLockRelativePath {
    <#
    .SYNOPSIS
        Repo-relative, forward-slashed forms of the caller's target paths, for the domain resolver.
    .DESCRIPTION
        Resolve-CodeDomainsForPaths matches anchored patterns - '^docs/', '^app_v2/', '^PLAN/' - so
        it only ever sees a path as repo-relative. An ABSOLUTE path matches no rule and takes the
        fail-closed branch, which returns EVERY code domain. That is the safe direction for a path
        that might belong to a module, but it is the wrong answer here and it is silent: measured
        2026-09-06 by this file's own suite, `Join-Path $RepoRoot 'docs/ICON_LEGEND.md'` acquired
        Code.Phone, Code.Wear and Code.Scripts for a docs-only render. Every caller in this family
        builds its targets with Join-Path against a repo root, so without this the whole adoption
        would serialise phone and wear code edits behind a documentation render.

        A rooted path OUTSIDE the project is deliberately left alone, so the resolver still fails
        closed on it - "I do not know what this is" must not become "this is nothing".
    #>
    param([Parameter(Mandatory)][string[]]$Path)

    $root = ((Get-SzaProjectRoot) -replace '\\', '/').TrimEnd('/')
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($raw in $Path) {
        # Split on commas for the same reason the resolver does: `pwsh -File` hands a comma list to
        # a [string[]] parameter as one element.
        foreach ($piece in (([string]$raw) -split ',')) {
            $trimmed = $piece.Trim()
            if (-not $trimmed) { continue }
            $slashed = $trimmed -replace '\\', '/'
            if ([System.IO.Path]::IsPathRooted($trimmed) -and
                $slashed.StartsWith("$root/", [System.StringComparison]::OrdinalIgnoreCase)) {
                $out.Add($slashed.Substring($root.Length + 1))
            }
            else {
                $out.Add($slashed)
            }
        }
    }
    return $out.ToArray()
}

function Resolve-CodeLockAcquisition {
    <#
    .SYNOPSIS
        Shared worker behind Enter-CodeLockOrExit and Enter-CodeLockOrSkip: resolve the domains,
        take the queue place, try the acquire. Decides nothing about what a BUSY domain means.
    .DESCRIPTION
        The two public entry points differ in exactly one respect - what a busy domain costs the
        caller - and in nothing else. Keeping the resolve, the fail-closed advisory, the top-up
        split and the ticket handling in one worker is the S1621 rule applied to this file: two
        copies of this logic could disagree about whether a domain is held, and then the gate that
        skips and the script that exits 4 would be answering different questions about one tree.

        Returns a result object, never exits: Outcome is 'none' (no domain owns the paths),
        'held' (this session already has them), 'acquired' (Acquired names what was taken) or
        'busy' (Blocking names the domain, Handoff the saved ticket path - $null under
        -NoQueuePlace, which takes no place and so has nothing to hand off).
    .PARAMETER NoQueuePlace
        Attempt the acquire WITHOUT enqueueing, and leave no ticket behind on a refusal. For a
        caller that will not come back for this write: it skips and the next run re-attempts from
        scratch. A ticket taken by such a caller is never retired by anybody, so it both inflates
        the queue and - through the head-of-queue reservation - can hold the domain against a
        session that really is waiting. Observed 2026-09-06 as a ghost place left in the
        Code.Scripts queue by a run that had already exited.
    #>
    param(
        [Parameter(Mandatory)][string[]]$Path,
        [Parameter(Mandatory)][string]$Reason,
        [switch]$BlockThrough,
        [switch]$NoQueuePlace,
        [int]$WaitTimeoutSeconds = 3600
    )

    $relative = @(ConvertTo-CodeLockRelativePath -Path $Path)
    $domains = @(Resolve-CodeDomainsForPaths -Path $relative)
    if ($domains.Count -eq 0) {
        return [pscustomobject]@{ Outcome = 'none'; Acquired = @(); Blocking = $null; Handoff = $null }
    }

    # Say so when the answer is the fail-closed one. Every path rule is an anchored PREFIX, so a
    # target that is off by a character - most easily the containing directory, 'docs' instead of
    # 'docs/X.md' - matches no rule and widens to every code domain instead of narrowing to one.
    # That is safe but wrong, and it is invisible: the run still prints "acquired". Measured
    # 2026-09-06, it made two docs renderers queue behind a sibling's Code.Phone and exit 4 with
    # the docs tree free. A writer that really does span all three domains gets one advisory line.
    $everyCodeDomain = @(Get-AgentLockDomainTable | Where-Object { $_.Type -eq 'Code' } | ForEach-Object { $_.Domain })
    if ($domains.Count -eq $everyCodeDomain.Count) {
        Write-Host "code-lock: the target(s) resolved to EVERY code domain ($($domains -join ', ')) - the domain table's fail-closed answer for a path it does not recognise. Paths as resolved: $($relative -join ', ')" -ForegroundColor Yellow
    }

    # Split into "this session already holds it" and "still missing", exactly as enter-code-lock.ps1
    # does. Without this an inner call would enqueue behind its OWN outer lock, and that ticket can
    # never be granted from outside - a livelock, not a delay (S2200).
    $topUp = Resolve-AgentLockTopUp -Domains $domains
    if ($topUp.Missing.Count -eq 0) {
        Write-Host "code-lock: $($domains -join ', ') already held by this session - reusing, nothing queued." -ForegroundColor Green
        return [pscustomobject]@{ Outcome = 'held'; Acquired = @(); Blocking = $null; Handoff = $null }
    }
    if ($topUp.Held.Count -gt 0 -and -not $topUp.AscendingSafe) {
        # Descending top-up: this session holds a domain that outranks one it still needs. Granting
        # it here would acquire out of canonical order, which a symmetric session could deadlock
        # against. Refuse before any ticket exists for the colliding domain.
        Write-Error "code-lock: this session holds $($topUp.Held -join ', '), which outranks missing domain(s) $($topUp.Missing -join ', ') - taking them together risks a cross-session deadlock (S2200). Release first, then take the full set in one call." -ErrorAction Continue
        exit 4
    }

    $acquire = @($topUp.Missing)
    $wait = [bool]$BlockThrough -or $env:FMS_LOCK_BLOCK -eq '1'

    if ($NoQueuePlace) {
        # Ticketless on purpose. A ticketless attempt loses to any session holding a place ahead of
        # it, which is the right outcome for a caller that would rather skip than wait: it can
        # never barge past a real waiter, and it leaves nothing behind when it loses.
        $result = Enter-AgentLock -Name 'Code' -Domains $acquire -Reason $Reason
        if (-not $result.Acquired) {
            $blocking = if ($result.Domain) { $result.Domain } else { $acquire[0] }
            return [pscustomobject]@{ Outcome = 'busy'; Acquired = @(); Blocking = $blocking; Handoff = $null }
        }
        return [pscustomobject]@{ Outcome = 'acquired'; Acquired = $acquire; Blocking = $null; Handoff = $null }
    }

    # The place in the queue is taken BEFORE the acquire, so the ticket this acquire retires is our
    # own and a refusal below leaves us queued rather than starving behind later arrivals.
    $tickets = New-AgentLockTicketSet -Name 'Code' -Reason $Reason -Domains $acquire
    $result = Enter-AgentLock -Name 'Code' -Domains $acquire -Reason $Reason -Tickets $tickets `
        -Wait:$wait -WaitTimeoutSeconds $WaitTimeoutSeconds

    if (-not $result.Acquired) {
        # Deliberately NOT removing the tickets: a refusal that drops its place turns waiting into
        # starvation, and the rerun after the background wait adopts the same place.
        # S2697: the handoff carries the resolved path set so queue waits can be grouped by subtree.
        $handoff = Save-AgentLockTicketHandoff -Tickets $tickets -Reason $Reason -Paths $relative
        $blocking = if ($result.Domain) { $result.Domain } else { $acquire[0] }
        return [pscustomobject]@{ Outcome = 'busy'; Acquired = @(); Blocking = $blocking; Handoff = $handoff }
    }

    return [pscustomobject]@{ Outcome = 'acquired'; Acquired = $acquire; Blocking = $null; Handoff = $null }
}

function Enter-CodeLockOrExit {
    <#
    .SYNOPSIS
        Acquire the code domains a set of target paths belongs to. Returns a scope to release.
        A busy domain exits 4 - for a writer whose skipped write would be work left undone.
    .DESCRIPTION
        The domain is DERIVED from the paths, never declared (the harness resolver's ADR-1): a
        declared domain that is wrong removes protection silently and still looks like working
        coordination, while a derived one is wrong only if the path set is wrong - and the path
        set is the thing the caller is about to write, so it cannot drift from what it protects.

        Returns a scope object even when nothing was acquired, so the caller's `finally` never has
        to test for null. Two cases produce an empty scope and both are normal: a path set that
        resolves to no domain at all (a PLAN/ path is exempt - it is already serialised per ticket
        by the lease and per journal by the catalog mutex), and a set this session already holds
        in full.

        Pick this entry point when NOT writing is a failure the caller must report. When the write
        is bookkeeping the caller can safely skip - a ratchet baseline moving down - use
        Enter-CodeLockOrSkip instead; S2635 has the test that separates the two.
    .PARAMETER Path
        The concrete files this caller is about to write. Repo-relative or absolute both work -
        see ConvertTo-CodeLockRelativePath for why an absolute one cannot be passed through raw.
    .PARAMETER Reason
        Who is writing and why, as it will appear to a sibling refused on this domain.
    .PARAMETER BlockThrough
        Block until the domain frees instead of exiting 4. FMS_LOCK_BLOCK=1 does the same globally.
        For a caller with no foreground timeout above it.
    #>
    param(
        [Parameter(Mandatory)][string[]]$Path,
        [Parameter(Mandatory)][string]$Reason,
        [switch]$BlockThrough,
        [int]$WaitTimeoutSeconds = 3600
    )

    $r = Resolve-CodeLockAcquisition -Path $Path -Reason $Reason -BlockThrough:$BlockThrough `
        -WaitTimeoutSeconds $WaitTimeoutSeconds

    if ($r.Outcome -eq 'none') {
        Write-Host "code-lock: no code domain owns the target path(s) - nothing to take." -ForegroundColor DarkGray
    }
    if ($r.Outcome -eq 'busy') {
        $holder = Get-AgentLockStatus -Name $r.Blocking
        Write-Host "code-lock: $($r.Blocking) is busy - queued, not yet your turn. Nothing was written." -ForegroundColor Yellow
        if ($holder.Exists) {
            Write-Host "  Holder: session $($holder.SessionId) (age $([int]$holder.AgeSeconds)s, reason: '$($holder.Reason)')." -ForegroundColor Yellow
        }
        Write-Host "  Wait for the turn in the BACKGROUND, then rerun this command:" -ForegroundColor Yellow
        Write-Host "    pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name $($r.Blocking) -Reason '$Reason' -Acquire -Handoff `"$($r.Handoff)`"" -ForegroundColor Gray
        exit 4
    }

    if ($r.Outcome -eq 'acquired') {
        Write-Host "code-lock: acquired $($r.Acquired -join ', ') for $Reason" -ForegroundColor Cyan
    }
    return [pscustomobject]@{ Acquired = @($r.Acquired); Reason = $Reason; Skipped = $false }
}

function Enter-CodeLockOrSkip {
    <#
    .SYNOPSIS
        Same acquisition as Enter-CodeLockOrExit, but a busy domain sets Skipped and returns
        instead of exiting - for a bookkeeping write inside a gate that has already passed.
    .DESCRIPTION
        S2635. `.\a.ps1 fg` runs its gate children concurrently (S2451), and one of them -
        assert-source-gates.ps1 - rewrites a ratchet baseline DOWN in ordinary gate mode, with no
        write switch passed (S1338). Measured 2026-09-06: a baseline seeded at 5 against a live
        count of 0 was rewritten to 0 by a plain `-Gate -Only globalscope` run that exited 0. So
        the battery already writes into Code.Scripts from concurrent children, unlocked.

        Exiting 4 from there would be wrong twice over. The gate's VERDICT is already computed and
        it is PASS - the count is at or below baseline - so the only thing a busy domain blocks is
        the bookkeeping. Exit 4 would turn that passing gate into a red `fg` over a housekeeping
        write nobody asked for. And the cost of skipping is one run of a baseline that stays higher
        than reality, which is precisely the state the repository lived in before S1338 added the
        auto-ratchet: harmless, and healed by the next run that finds the domain free.

        The caller tests $scope.Skipped and leaves its write undone. The scope is still safe to
        pass to Exit-CodeLockScope - it acquired nothing, so it releases nothing.

        The test for choosing between the two entry points: if NOT writing is work left undone,
        use Enter-CodeLockOrExit; if not writing costs only staleness a later run repairs, use
        this one.
    .PARAMETER Path
        The concrete files this caller is about to write.
    .PARAMETER Reason
        Who is writing and why, as it will appear to a sibling refused on this domain.
    #>
    param(
        [Parameter(Mandatory)][string[]]$Path,
        [Parameter(Mandatory)][string]$Reason,
        [int]$WaitTimeoutSeconds = 3600
    )

    # No -BlockThrough here on purpose: this entry point exists BECAUSE its caller must not stall.
    # A gate child that blocks holds a slot in the fg battery and defeats the concurrency S2451
    # bought, which is the same wall-clock damage the skip is designed to avoid.
    # -NoQueuePlace for the reason given on that parameter: this caller never returns for the write.
    $r = Resolve-CodeLockAcquisition -Path $Path -Reason $Reason -NoQueuePlace `
        -WaitTimeoutSeconds $WaitTimeoutSeconds

    if ($r.Outcome -eq 'none') {
        Write-Host "code-lock: no code domain owns the target path(s) - nothing to take." -ForegroundColor DarkGray
    }
    if ($r.Outcome -eq 'busy') {
        $holder = Get-AgentLockStatus -Name $r.Blocking
        $who = if ($holder.Exists) { " (held by session $($holder.SessionId), reason: '$($holder.Reason)')" } else { '' }
        Write-Host "code-lock: $($r.Blocking) is busy$who - SKIPPING the bookkeeping write, verdict unaffected. The next run on a free domain repeats it." -ForegroundColor DarkYellow
        return [pscustomobject]@{ Acquired = @(); Reason = $Reason; Skipped = $true }
    }

    if ($r.Outcome -eq 'acquired') {
        Write-Host "code-lock: acquired $($r.Acquired -join ', ') for $Reason" -ForegroundColor Cyan
    }
    return [pscustomobject]@{ Acquired = @($r.Acquired); Reason = $Reason; Skipped = $false }
}

function Exit-CodeLockScope {
    <#
    .SYNOPSIS
        Release exactly the domains the matching Enter-CodeLockOrExit acquired. Safe in a `finally`.
    .DESCRIPTION
        An empty scope releases nothing, which is what makes a nested call harmless: the inner
        scope acquired nothing, so it gives nothing back and the outer scope's lock survives to
        cover the rest of the outer script's work.

        $null is accepted for the same reason - and it is the shape a `finally` actually sees. The
        caller declares its scope variable before the try, so a failure BEFORE Enter-CodeLockOrExit
        returns (a missing input file, a throw in the argument expression) reaches the finally with
        the variable still null. A Mandatory parameter would prompt for it there, which in a
        non-interactive gate is an error raised from inside the cleanup path - the one place an
        error hides the real failure that got us there.
    #>
    param($Scope)

    if (-not $Scope -or @($Scope.Acquired).Count -eq 0) { return }
    Exit-AgentLock -Name 'Code' -Domains @($Scope.Acquired)
}
