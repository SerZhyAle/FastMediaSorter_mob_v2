#requires -Version 7.0
<#
.SYNOPSIS
    post-change.ps1 library: release exactly the code-lock domains this closure acquired, once.

.DESCRIPTION
    Dot-sourced by scripts/post-change.ps1, never invoked - the function reads $root, $changedFiles
    and $deletedFiles from the caller's scope and writes $script:codeDomainsReleased there, so the
    facade behaves exactly as it did when this block was inline.

    S2419: the code lock covers the EDIT, never the checks that only read the tree afterwards. It used
    to be released in the facade's trailing finally, so the whole gate batch - roughly seven hundred
    lines - ran while holding. Measured over temp/AGENT-CHAT for 2026-09-02 21:30 .. 2026-09-03 01:20:
    a closure's own gate batch took 19.0-48.8 s inside the lock against a 95 s median Code.Scripts
    hold, every one of the 51 queue waits in that window was on that one domain, and the queue reached
    ten deep. The release therefore sits at the facade's own call site - after the changed set and
    every gate argv are resolved, before the first pooled child reads anything. Nothing between the
    top of the facade and that line mutates the tree, and the two mutating steps (catalog-sync, dev
    log) already ran after the old release point, so they are unaffected: catalog-sync rewrites a
    gitignored local index and add_to_dev_log.ps1 serialises itself on a system mutex (S1537), which
    is the finer mechanism Rule 23 defers to.

    S3386 moved the block here for no reason but the Rule 2 ceiling - the facade crossed 2000 lines,
    and the trigger assignments above it cannot move (post-change.tests/Run-Tests.ps1 reads them
    statically at the facade's top level). Nothing about the behaviour changed with the move.

.NOTES
    Exit codes (CLAUDE.md Rule 7): none - this file is dot-sourced and defines a function. It never
    exits, and a failure to release is reported as a warning by design: a closure whose gates all
    passed must not be turned red by a lock the staleness window would have reclaimed anyway.
#>

$script:codeDomainsReleased = $false

function Exit-AcquiredCodeDomains {
    if ($script:codeDomainsReleased) { return }
    $script:codeDomainsReleased = $true
    # S2109: release exactly the domains this change set maps to, never the whole code side.
    # Research artifact 04 found the unconditional release was blind to the module, so under
    # domains a wear-only closure would have freed the phone domain a sibling was holding and
    # handed its turn away mid-edit - the facade would have become the thing that breaks the
    # split it is meant to use.
    try {
        . (Join-Path $root "scripts/utils/agent-lock.ps1")
        # "Exactly the domains ACQUIRED" - which is not the same as "the domains this change set
        # maps to". A session that took the full set and then closes a scripts-only change would
        # otherwise release Code.Scripts and keep Code.Phone and Code.Wear held for nobody, which
        # is a leak that outlives the run: measured here, two domains survived a green closure.
        # Ownership is recorded in the lock files, so the acquired set can simply be read back, and
        # Exit-AgentLockDomain owner-checks each one - a domain held by a sibling is never touched.
        $derivedDomains = @(Resolve-CodeDomainsForPaths -Path ($changedFiles + $deletedFiles))
        # S2371: same accessor the lock was written with - a pid-fallback session must still
        # count the domains it holds, or its closure hands them to the staleness window.
        $mySession = Get-AgentSessionId
        $heldByThisSession = @(Resolve-AgentLockDomains -Name 'Code' | Where-Object {
            $s = Get-AgentLockStatus -Name $_
            $s.Exists -and -not [string]::IsNullOrWhiteSpace($mySession) -and
                [string]$s.SessionId -eq $mySession
        })
        $releaseDomains = @(@($derivedDomains + $heldByThisSession) | Select-Object -Unique)
        Exit-AgentLock -Name 'Code' -Domains $releaseDomains

        # A closure is the deliberate boundary of one logical change. The following edit needs a
        # fresh lock, so make that state transition explicit before a later phase can write unlocked.
        $releasedDomains = @($heldByThisSession | Where-Object {
            $status = Get-AgentLockStatus -Name $_
            -not ($status.Exists -and [string]$status.SessionId -eq $mySession)
        })
        if ($releasedDomains.Count -gt 0) {
            Write-Host ("  [code-lock-release] RELEASED: $($releasedDomains -join ', '). " +
                'Before the next repository source, resource, build, or script edit, acquire its derived code lock.') `
                -ForegroundColor Yellow
        }
    }
    catch {
        Write-Host "  [code-lock-release] WARN - could not release the code lock: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}
