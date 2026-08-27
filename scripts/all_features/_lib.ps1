# _lib.ps1 (S1537) - shared state for the ALL_FEATURES inventory mutators.
#
# Dot-source it; it has no side effects of its own and never opens an inventory file on load.
#
# Cross-process serialization. The critical section is not the write - it is the whole
# read -> mutate -> write path. Two processes call Read-FeatureLines, both hold the same
# snapshot, and the second Write-FeatureLines replaces the file with its own stale base plus
# its own change; the first record vanishes with no error and exit code 0 on both sides.
# Measured on the unlocked code: eight concurrent add.ps1 calls landed four records out of
# eight. So the lock is taken by the CALLER around its whole section - Write-FeatureLines
# cannot do it alone, however atomic it is. Same conclusion, same shape and same reason as
# scripts/spec_catalog/_lib.ps1 (S1437); the two CLIs stay independent, each with its own
# mutex, so a spec-catalog write never waits on an inventory write.
#
# A system mutex, not a lock file: an inventory rewrite is milliseconds, while the BUILD/CODE
# lock family is sized for edits and builds (3-60 min windows, queue directories,
# reservations) and would be absurd here. The mutex also dies with its process, so a crashed
# holder cannot wedge the inventory - that is what the AbandonedMutexException branch is for.

$script:FeatureMutex = $null

function Resolve-FeatureRepoRoot {
    <#
    .SYNOPSIS
        Repo root for a script living in scripts/all_features/, with CWD as the fallback.
    #>
    param([string]$ScriptDir)

    if (-not $ScriptDir) { $ScriptDir = $PSScriptRoot }
    $root = Split-Path -Parent (Split-Path -Parent $ScriptDir)
    if (-not $root -or -not (Test-Path (Join-Path $root 'settings.gradle.kts'))) {
        $root = (Get-Location).Path
    }
    return $root
}

function Get-FlavorMatrixColumns {
    <#
    .SYNOPSIS
        The phone flavor names, read from the generated flavor matrix header (S1392).
    .DESCRIPTION
        The three CLIs here each carried the same six names as a literal, so a seventh flavor
        declared in productFlavors was rejected by every one of them while validate.ps1's own gate
        check - which reads docs/FLAVOR_MATRIX.md - was already demanding it. The matrix is
        generated from that same productFlavors block, so its header is the one list that cannot
        go stale silently.

        Returns an empty array when the matrix cannot be read or carries no header, so the caller
        keeps its own fallback instead of having one guessed here.
    #>
    param([Parameter(Mandatory)][string]$RepoRoot)

    $path = Join-Path (Join-Path $RepoRoot 'docs') 'FLAVOR_MATRIX.md'
    if (-not (Test-Path -LiteralPath $path)) { return @() }

    foreach ($line in (Get-Content -LiteralPath $path -Encoding UTF8)) {
        if ($line -notmatch '^\s*\|') { continue }
        $cells = @($line.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
        if ($cells.Count -lt 2 -or $cells[0] -ne 'Flag') { continue }
        return @($cells[1..($cells.Count - 1)] | Where-Object { $_ })
    }
    return @()
}

function Get-FeatureInventoryPath {
    <#
    .SYNOPSIS
        Path of the inventory file - the tracked one, or the gitignored noLegal variant.
    #>
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [switch]$NoLegal
    )

    $fileName = if ($NoLegal) { 'ALL_FEATURES_noLegal.jsonl' } else { 'ALL_FEATURES.jsonl' }
    return (Join-Path (Join-Path $RepoRoot 'docs') $fileName)
}

function Get-FeatureMutexName {
    # Per-checkout, so two clones on one machine do not serialize against each other. Mutex
    # names cannot contain '\' beyond the Global\ prefix, hence the hash rather than the path.
    param([Parameter(Mandatory)][string]$RepoRoot)

    $hash = [System.BitConverter]::ToString(
        [System.Security.Cryptography.MD5]::HashData([System.Text.Encoding]::UTF8.GetBytes($RepoRoot.ToLowerInvariant()))
    ).Replace('-', '')
    return "Global\FMS-AllFeatures-$hash"
}

function Enter-FeatureLock {
    <#
    .SYNOPSIS
        Take the inventory write lock. Pair with Exit-FeatureLock in a finally.
    .DESCRIPTION
        One lock covers BOTH inventory files. A rewrite is milliseconds, so keying the mutex
        per file would buy nothing and would leave a caller that switches files mid-run
        holding the wrong lock. Do not split it.
    #>
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [int]$TimeoutSeconds = 30
    )

    if ($script:FeatureMutex) { return }   # re-entrant within one process
    $mutex = New-Object System.Threading.Mutex($false, (Get-FeatureMutexName -RepoRoot $RepoRoot))
    $acquired = $false
    try {
        $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
    }
    catch [System.Threading.AbandonedMutexException] {
        # The previous holder died mid-write. The mutex is ours; the inventory itself is intact
        # because every write lands by rename. Proceeding is correct - refusing would wedge the
        # inventory until a reboot.
        $acquired = $true
    }
    if (-not $acquired) {
        $mutex.Dispose()
        throw "ALL_FEATURES inventory is locked by another process (waited ${TimeoutSeconds}s)."
    }
    $script:FeatureMutex = $mutex
}

function Exit-FeatureLock {
    # Safe to call unconditionally from a finally, including when the lock was never taken.
    if (-not $script:FeatureMutex) { return }
    try { $script:FeatureMutex.ReleaseMutex() } catch { }
    $script:FeatureMutex.Dispose()
    $script:FeatureMutex = $null
}

function Read-FeatureLines {
    <#
    .SYNOPSIS
        Non-blank record lines of an inventory file, or an empty array when it does not exist.
    #>
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path $Path)) { return @() }
    return @(Get-Content -LiteralPath $Path -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })
}

function Write-FeatureLines {
    <#
    .SYNOPSIS
        Atomic write of pre-formatted JSONL lines: temp file + Move-Item -Force, UTF-8 no BOM.
    .DESCRIPTION
        Rename is what keeps a concurrent reader - validate.ps1, diff.ps1, /skill-release -
        from ever seeing a half-written inventory. It does NOT protect against a lost update:
        that needs the caller to hold Enter-FeatureLock across its read and its write.
    #>
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][AllowEmptyCollection()]$Lines
    )

    $payload = (@($Lines) -join "`n")
    if ($payload.Length -gt 0) { $payload += "`n" }
    $tmp = "$Path.tmp"
    [System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}
