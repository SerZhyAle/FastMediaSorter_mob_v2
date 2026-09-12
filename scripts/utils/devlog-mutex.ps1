#requires -Version 7.0
<#
.SYNOPSIS
    S3022: takes the canon dev-log writer's mutex so a repo-side WHOLE-FILE rewrite of
    dev/CHANGELOG.md is serialised against the appends every other session is making.

.DESCRIPTION
    dev/CHANGELOG.md is appended by every closure in every session and is covered by no code-domain
    lock - dev/ is in Code.Scripts' prefix list, but a closure that touched only PLAN/ takes no lock
    at all, and 55% of them do exactly that (S2338). What actually serialises the file is a system
    mutex inside the canon writer, tools/harness/devlog/add_to_dev_log.ps1: it is named per checkout,
    it dies with its holder, and it wraps the writer's read-decide-append as one critical section.

    Repo-side code that REWRITES the file whole - a contract suite dropping its own probe rows - did
    not take that mutex, and both halves of that gap were measured on 2026-09-12 while reproducing
    S3022. [System.IO.File]::WriteAllLines opens the file exclusively, so the rewrite collided with a
    live closure's Add-Content and killed its dev-log step outright ("The process cannot access the
    file .. because it is being used by another process"); and read-filter-write around a file a
    sibling appends to drops that sibling's row with no error anywhere.

    Dot-source this, then hold the lock across the read AND the write. Taking it only for the write
    leaves the same lost update with a smaller window, which is the shape that survived review twice.

    S2441 naming: this file is dot-sourced, so every name it defines lands in the CALLER's scope.
    The one variable it keeps carries an Sza prefix for that reason, and $ErrorActionPreference is
    deliberately NOT set at top level - that would silently rewrite the preference of every caller.

.NOTES
    A dot-sourced library. It returns no exit code and calls no exit; Enter-DevLogWriteLock throws
    when the mutex cannot be taken, so the caller's own contract decides what that costs.
#>

# Declared before first use because a caller running under Set-StrictMode treats reading an unset
# variable as an error, and the re-entrancy check below reads it before anything assigns it.
$script:SzaDevLogWriteMutex = $null

function Get-DevLogProjectRoot {
    <#
    .SYNOPSIS
        The same root the canon writer resolves, so both derive the same mutex name.
    #>
    if (-not [string]::IsNullOrWhiteSpace($env:SZA_PROJECT_ROOT)) {
        return (Resolve-Path -LiteralPath $env:SZA_PROJECT_ROOT).Path
    }
    # Mirrors Get-SzaProjectRoot's marker walk. Starting at this file rather than the working
    # directory keeps the answer stable for a caller invoked from anywhere in the tree.
    $dir = $PSScriptRoot
    while ($dir) {
        if (Test-Path -LiteralPath (Join-Path $dir '.sza-profile.json')) { return (Resolve-Path -LiteralPath $dir).Path }
        $parent = Split-Path -Parent $dir
        if ($parent -eq $dir) { break }
        $dir = $parent
    }
    throw "devlog-mutex: no .sza-profile.json above '$PSScriptRoot' - cannot resolve the project root."
}

function Get-DevLogMutexName {
    <#
    .SYNOPSIS
        The canon writer's mutex name for one checkout.
    .DESCRIPTION
        Per-checkout, so two clones on one machine do not serialize against each other, and hashed
        because a mutex name may not contain '\' beyond the Global\ prefix. The formula is the
        writer's, restated here because the writer keeps it in a private function of a script that
        appends a row when it is dot-sourced. scripts/utils/devlog-mutex.tests/ compares the two
        derivations against the INSTALLED writer's source, so a canon-side rename fails loudly
        instead of silently unserialising this file.
    #>
    param([Parameter(Mandatory = $true)][string]$RepoRoot)
    $hash = [System.BitConverter]::ToString(
        [System.Security.Cryptography.MD5]::HashData([System.Text.Encoding]::UTF8.GetBytes($RepoRoot.ToLowerInvariant()))
    ).Replace('-', '')
    return "Global\FMS-DevLog-$hash"
}

function Enter-DevLogWriteLock {
    <#
    .SYNOPSIS
        Blocks until this process owns the changelog, or throws.
    #>
    param(
        [string]$RepoRoot,
        [int]$TimeoutSeconds = 30
    )
    if ($script:SzaDevLogWriteMutex) { return }   # re-entrant within one process
    if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Get-DevLogProjectRoot }
    $mutex = New-Object System.Threading.Mutex($false, (Get-DevLogMutexName -RepoRoot $RepoRoot))
    $acquired = $false
    try {
        $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
    }
    catch [System.Threading.AbandonedMutexException] {
        # The previous holder died mid-write. The mutex is ours, and the file is intact because
        # every writer either wrote a whole line or none. Proceeding is correct - refusing would
        # wedge every closure in the repository until a reboot.
        $acquired = $true
    }
    if (-not $acquired) {
        $mutex.Dispose()
        throw "dev/CHANGELOG.md is locked by another process (waited ${TimeoutSeconds}s)."
    }
    $script:SzaDevLogWriteMutex = $mutex
}

function Exit-DevLogWriteLock {
    <#
    .SYNOPSIS
        Releases the changelog. Safe to call from a finally when the lock was never taken.
    #>
    if (-not $script:SzaDevLogWriteMutex) { return }
    try { $script:SzaDevLogWriteMutex.ReleaseMutex() } catch { }
    $script:SzaDevLogWriteMutex.Dispose()
    $script:SzaDevLogWriteMutex = $null
}
