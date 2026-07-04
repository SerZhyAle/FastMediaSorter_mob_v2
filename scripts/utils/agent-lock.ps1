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

.EXAMPLE
    . "$PSScriptRoot\..\utils\agent-lock.ps1"
    Enter-BuildLockOrExit -Reason "build-debug.PS1"
    try {
        # ... existing gradle-invoking body ...
    } finally {
        Exit-AgentLock -Name Build
    }
#>

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

function Get-AgentLockPath {
    param([Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name)
    $tempDir = Join-Path $Script:AgentLockRepoRoot 'temp'
    if (-not (Test-Path -LiteralPath $tempDir)) {
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    }
    return Join-Path $tempDir "$($Name.ToUpper()).LOCK"
}

function Get-AgentLockStatus {
    <#
    .SYNOPSIS
        Read-only status query. Never deletes anything - staleness is only ever reclaimed by
        Enter-AgentLock, at the moment a caller actually wants the lock.
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
        # 0 = use the type default (Build: 60 min safety-net behind PID-liveness; Code: 10 min).
        [int]$StaleMinutes = 0
    )
    if ($StaleMinutes -le 0) {
        $StaleMinutes = if ($Name -eq 'Build') { 60 } else { 10 }
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
        # Code lock: no process to check, wall-clock only.
        $result.ProcessAlive = $null
        $result.Stale = $ageSeconds -gt ($StaleMinutes * 60)
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
        [Parameter(Mandatory)][string]$Reason
    )

    $status = Get-AgentLockStatus -Name $Name
    if ($status.Exists -and -not $status.Stale) {
        return [pscustomobject]@{ Acquired = $false; Status = $status }
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
        $body = [ordered]@{
            lockType   = $Name
            pid        = $PID
            procStart  = $proc.StartTime.Ticks
            acquiredAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            reason     = $Reason
            host       = $env:COMPUTERNAME
        } | ConvertTo-Json -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
    }
    finally {
        $stream.Close()
    }

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
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        return
    }

    try {
        $raw = Get-Content -LiteralPath $path -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
        if ([int]$raw.pid -eq $PID) {
            Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        }
    }
    catch {
        # Corrupt/unreadable content - nothing legible depends on it, safe to drop.
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
    }
}

function Enter-BuildLockOrExit {
    <#
    .SYNOPSIS
        Convenience for leaf gradle-invoking scripts: warn (never block) on a fresh CODE.LOCK,
        then hard-acquire BUILD.LOCK or exit 1 with the current holder's PID/age/reason.
    #>
    param([Parameter(Mandatory)][string]$Reason)

    $codeStatus = Get-AgentLockStatus -Name Code
    if ($codeStatus.Exists -and -not $codeStatus.Stale) {
        Write-Host "Warning: CODE.LOCK present (age $([int]$codeStatus.AgeSeconds)s, reason: '$($codeStatus.Reason)') - a code edit may be in progress elsewhere. This build may reflect a half-written state." -ForegroundColor Yellow
    }

    $result = Enter-AgentLock -Name Build -Reason $Reason
    if (-not $result.Acquired) {
        $s = $result.Status
        Write-Host "BUILD.LOCK held - refusing to start a second gradle build." -ForegroundColor Red
        Write-Host "  Holder PID: $($s.Pid)  age: $([int]$s.AgeSeconds)s  reason: '$($s.Reason)'  host: $($s.Host)" -ForegroundColor Red
        Write-Host "  Never run two gradle builds concurrently (daemon OOM / cache corruption - see CLAUDE.md)." -ForegroundColor Gray
        Write-Host "  Check status: pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build" -ForegroundColor Gray
        exit 1
    }
}
