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

    Exit codes: none - this file defines functions and returns nothing.
#>

$script:GatePool = @{}
$script:PooledElapsedMs = $null
$script:GatePoolEnabled = $null -ne (Get-Command Start-ThreadJob -ErrorAction SilentlyContinue)

function Get-GatePoolKey([string[]]$Argv) { return ($Argv -join [char]1) }

# Start a gate now so its result is ready when the pipeline reaches its call site. A no-op when
# ThreadJob is unavailable, which leaves every consumer running inline exactly as before.
function Start-PooledGate {
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
        $r = Receive-Job -Job $job -Wait -AutoRemoveJob
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

# A gate whose call site was never reached - the run ended early - still owns a running child.
function Stop-GatePool {
    foreach ($key in @($script:GatePool.Keys)) {
        $job = $script:GatePool[$key]
        if (-not $job) { continue }
        try { Stop-Job -Job $job -ErrorAction SilentlyContinue } catch { }
        try { Remove-Job -Job $job -Force -ErrorAction SilentlyContinue } catch { }
    }
    $script:GatePool.Clear()
}
