#requires -Version 7.0
<#
.SYNOPSIS
    S2326: the gate pool - start read-only gate children together, consume them in the original order.

.DESCRIPTION
    Every gate a closure runs is a child pwsh process, and most of them are pure reads: they open
    sources, resources and documents, write nothing, and none of them reads another's verdict.
    Running those one at a time left the machine idle for the length of the run. This library lets a
    caller START such a gate early and CONSUME it at the point the serial pipeline reaches it, so the
    printed verdict order, the exit codes, the telemetry rows and the fail-fast barrier are the same
    as the serial run; only the wall clock moves.

    A pooled entry is keyed by its own argument vector, so a call site that consumes one is running
    exactly the command it would have run inline, and a key that was never started simply runs inline.
    That is the property that makes a mistake here cost a job rather than a verdict.

    Two things must never be pooled: anything that WRITES (a formatter, a generator, a catalog sync,
    the changelog), and anything that reads a file another pooled or serial step is rewriting. The
    caller owns that judgement - it knows where its own writers sit in the sequence.

.NOTES
    Dot-source it, then use Start-PooledGate / Invoke-GateChild / Stop-GatePool. The caller must
    define $pwsh (the interpreter to launch) before the first call.

    Exit codes: none - this file defines functions and returns nothing. Invoke-GateChild does set
    $LASTEXITCODE for its caller, and adds one code of its own to whatever the child returned:
    2 when the join exceeded $script:GatePoolJoinTimeoutSeconds and the gate was stopped unjudged.
#>

$script:GatePool = @{}
$script:PooledElapsedMs = $null
$script:GatePoolEnabled = $null -ne (Get-Command Start-ThreadJob -ErrorAction SilentlyContinue)
# S2538: the join gets a ceiling because the child already has one and the join did not. Every
# gradle-backed gate bounds its own work (Invoke-ProcessWithTimeout, 600 s) and its own lock wait
# (900 s), so a child cannot exceed roughly 1500 s - yet a closure was journalled waiting 34 711 s
# here, an order of magnitude past anything the child can produce. 1800 s clears the child's own
# worst case with room to spare, so a healthy run never reaches it.
$script:GatePoolJoinTimeoutSeconds = 1800

function Get-GatePoolKey([string[]]$Argv) { return ($Argv -join [char]1) }

# Start a gate now so its result is ready when the pipeline reaches its call site. A no-op when
# ThreadJob is unavailable, which leaves every consumer running inline exactly as before.
function Start-PooledGate {
    # S3301: a pooled gate is started long before its call site, so a reuse decided upstream has to
    # be honoured here or the batch is paid for in threads while every call site reports a skip.
    # Read defensively: this library is dot-sourced by batch runners that never load the ledger,
    # and Set-StrictMode would make a bare reference to an undeclared variable throw.
    if (Get-Variable -Name ClosureReuseActive -Scope Script -ValueOnly -ErrorAction SilentlyContinue) { return }
    if (-not $script:GatePoolEnabled) { return }
    $argv = @($args)
    if ($argv.Count -eq 0) { return }
    $key = Get-GatePoolKey $argv
    if ($script:GatePool.ContainsKey($key)) { return }
    $script:GatePool[$key] = Start-ThreadJob -ThrottleLimit 6 -ScriptBlock {
        param($PwshExe, $A)
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $out = & $PwshExe @A 2>&1 | Out-String
        $sw.Stop()
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out; ElapsedMs = [int]$sw.Elapsed.TotalMilliseconds }
    } -ArgumentList $pwsh, $argv
}

# The call site's replacement for `& $pwsh @argv`. Consumes the pooled run when there is one and
# runs the same command inline when there is not, so a gate is never skipped for want of a job.
function Invoke-GateChild {
    $argv = @($args)
    $key = Get-GatePoolKey $argv
    $job = $script:GatePool[$key]
    if ($job) {
        $script:GatePool.Remove($key)
        # Receive-Job carries no -Timeout on pwsh 7, so the bounded form is Wait-Job first. A job
        # that outlives the ceiling is stopped and reported as exit 2 - the closure did not verify
        # this gate, and saying PASS or FAIL would both be claims it did not earn.
        $finished = Wait-Job -Job $job -Timeout $script:GatePoolJoinTimeoutSeconds
        if (-not $finished) {
            Write-Host ("gate-pool: CANNOT VERIFY - joining '{0}' exceeded {1}s; the gate was stopped unjudged." -f `
                    ($argv -join ' '), $script:GatePoolJoinTimeoutSeconds) -ForegroundColor Yellow
            # S3341: the branch's own wait needs the same bound the join has. Stop-Job waits for
            # the job's thread and the thread waits for the child process it spawned, so a child
            # that cannot die makes this wait unbounded. Kill the child tree, settle briefly,
            # abandon the job if the stop still cannot land.
            $markerScript = $argv | Where-Object { $_ -like '*.ps1' } | Select-Object -First 1
            $cleanupMarker = if ($markerScript) { [System.IO.Path]::GetFileName($markerScript) } else { '' }
            Invoke-ClosureJobCleanup -Job $job -ChildProcessMarker $cleanupMarker
            $script:PooledElapsedMs = [int]($script:GatePoolJoinTimeoutSeconds * 1000)
            $global:LASTEXITCODE = 2
            return
        }
        # S3266: the receive carries no -Wait, for the reason the ceiling above exists at all - Wait-Job
        # has already proved a terminal state, and a second join would be unbounded, undoing the ceiling
        # one line below it. -AutoRemoveJob is legal only beside -Wait, so the removal is explicit.
        $r = Receive-Job -Job $job
        try { Remove-Job -Job $job -Force -ErrorAction SilentlyContinue } catch { }
        if ($r -and -not [string]::IsNullOrWhiteSpace($r.Output)) { Write-Host ($r.Output.TrimEnd()) }
        # A caller's own stopwatch would record how long the WAIT took, not what the gate cost, and
        # scripts/quality/measure-gate-frequency.ps1 reads that number to rank the gates. The child
        # times itself instead, and the caller reads it back through Get-PooledElapsedMs.
        if ($r) { $script:PooledElapsedMs = [int]$r.ElapsedMs }
        $global:LASTEXITCODE = if ($r) { [int]$r.ExitCode } else { 1 }
        return
    }
    & $pwsh @argv
}

# $null unless the step just run came out of the pool. Cleared by Reset-PooledElapsedMs.
function Get-PooledElapsedMs { return $script:PooledElapsedMs }
function Reset-PooledElapsedMs { $script:PooledElapsedMs = $null }
# S2538: for a caller that runs its own thread job outside this pool - detekt is the one - and so
# owns the same problem: its wrapper's stopwatch measures the join, and only the child knows what
# the work cost.
function Set-PooledElapsedMs([int]$ElapsedMs) { $script:PooledElapsedMs = $ElapsedMs }

# S3341: bounded teardown for a gate job that outlived its join ceiling. Stop-Job waits for the
# job's thread, and the thread waits for the child process it spawned - a child that cannot die
# (a process stuck in uninterruptible kernel I/O) turns every unbounded wait on the job into a
# closure hang; the detekt join's timeout branch was journalled blocking 4.5 h past a 1800 s
# ceiling on 2026-09-19. Three stages, each bounded: kill the child process tree, give the job a
# short window to settle, abandon it if the stop still cannot land. The abandoned job dies with
# this process; whatever the child acquired (a build lock) is recovered by the staleness window.
function Invoke-ClosureJobCleanup {
    param(
        [Parameter(Mandatory)]$Job,
        # A fragment of the child's command line, normally its script file name - it narrows the
        # kill to THIS job's child, never to a sibling gate's. Empty skips the kill.
        [string]$ChildProcessMarker = '',
        [int]$SettleSeconds = 2
    )
    try { Stop-Job -Job $Job -ErrorAction SilentlyContinue | Out-Null } catch { }
    if ($ChildProcessMarker) {
        # Thread jobs run in-process, so the child each job spawned is a direct child of $PID.
        $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $PID" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -like "*$ChildProcessMarker*" })
        foreach ($child in $children) {
            try { taskkill.exe /PID $child.ProcessId /T /F 2>&1 | Out-Null } catch { }
        }
    }
    if (Wait-Job -Job $Job -Timeout $SettleSeconds) {
        try { Remove-Job -Job $Job -Force -ErrorAction SilentlyContinue } catch { }
        return
    }
    try { Remove-Job -Job $Job -Force -ErrorAction SilentlyContinue } catch { }
}

# A gate whose call site was never reached - the run ended early - still owns a running child.
function Stop-GatePool {
    foreach ($key in @($script:GatePool.Keys)) {
        $job = $script:GatePool[$key]
        if (-not $job) { continue }
        # S3341: the teardown waits on the same thread the join waited on - bound it the same way.
        $markerScript = ($key -split [char]1) | Where-Object { $_ -like '*.ps1' } | Select-Object -First 1
        $cleanupMarker = if ($markerScript) { [System.IO.Path]::GetFileName($markerScript) } else { '' }
        Invoke-ClosureJobCleanup -Job $job -ChildProcessMarker $cleanupMarker
    }
    $script:GatePool.Clear()
}
