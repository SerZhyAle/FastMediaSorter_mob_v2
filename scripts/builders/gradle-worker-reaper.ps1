<#
.SYNOPSIS
    Finds and stops the Gradle unit-test workers a run spawned, so none of them outlives its wrapper.

.DESCRIPTION
    S2585: `& gradlew.bat` is not a process group on Windows. The launcher client and the forked test
    worker are independent processes, so the wrapper exiting - normally, by exception, or by being
    killed outright - leaves them running. A worker that outlives its build keeps the variant's R.jar
    open, and every later phone `standardDebug` entry point begins by rewriting that jar, so one
    survivor fails `fc`, `fk`, `fu` and `d` for every session on the machine with
    `IOException: Couldn't delete .. R.jar`. Measured twice on 2026-09-05, once for 102 minutes.

    The worker is identified by the directory Gradle handed it - not by a resemblance to build
    machinery. `org.gradle.internal.worker.tmpdir=<root>\<module>\build\tmp\<task>\work` names the
    tree, the module and the task, so a match is "a worker of THIS task of THIS module in THIS
    checkout" rather than "some java that looks like gradle". That precision is what lets the caller
    stop it immediately: `scripts/utils/agent-watchdog.ps1` has to reach the same conclusion from
    parent-is-gone plus an age threshold plus a CPU sample, and therefore cannot act for 25 minutes.

    S2584 is the other half and does not overlap: it detects a FOREIGN holder of R.jar and reports it
    as "not your code". This file stops the caller from becoming that holder in the first place.

    Dot-source this file; it defines functions and never exits.
#>

# Deliberately no Set-StrictMode here: this file is dot-sourced, so it would impose strict mode on
# every consumer's whole scope - a side effect none of them asked for. Same reasoning as
# gradle-run-verdict.ps1 and build-output-holder.ps1 next to it.

function Get-GradleWorkerIds {
    <#
    .SYNOPSIS
        Process ids of the Gradle test workers running under one module's task tmpdir.
    #>
    param(
        [Parameter(Mandatory)][string]$ProjectRoot,
        [Parameter(Mandatory)][string]$Module,
        # The task's own directory name, e.g. testStandardDebugUnitTest.
        [Parameter(Mandatory)][string]$TaskDir,
        # Injection seam for the contract set: objects carrying ProcessId and CommandLine. Left unset,
        # the live process table is read. A real java worker cannot be used as a fixture - the filter
        # below is by image name, so the set would depend on java being on PATH, and a test that
        # leaves a spinning process behind creates the very orphan this file exists to prevent.
        [AllowNull()][AllowEmptyCollection()][object[]]$Processes
    )

    $needle = [IO.Path]::Combine($ProjectRoot, $Module, 'build', 'tmp', $TaskDir, 'work')

    $candidates = $Processes
    if ($null -eq $candidates) {
        try {
            $candidates = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" `
                    -Property ProcessId, CommandLine -ErrorAction Stop)
        }
        catch {
            # Losing the process table is not a verdict about the build. The caller is in a finally
            # block on its way out; throwing here would replace a real exit code with this one.
            Write-Host "Could not read the process table to look for Gradle test workers: $($_.Exception.Message)" -ForegroundColor DarkYellow
            return , @()
        }
    }

    $ids = [System.Collections.Generic.List[int]]::new()
    foreach ($proc in $candidates) {
        if (-not $proc) { continue }
        $commandLine = [string]$proc.CommandLine
        if ([string]::IsNullOrWhiteSpace($commandLine)) { continue }
        # Ordinal-ignore-case Contains rather than -like or -match: the needle is a literal Windows
        # path, and both wildcard and regex matching would treat its separators and any bracket in a
        # checkout name as syntax. Escaping a path into two different mini-languages is how this kind
        # of filter silently matches nothing.
        if ($commandLine.IndexOf('org.gradle.internal.worker.tmpdir=', [StringComparison]::OrdinalIgnoreCase) -lt 0) { continue }
        if ($commandLine.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase) -lt 0) { continue }
        $ids.Add([int]$proc.ProcessId)
    }

    # Leading comma: PowerShell unrolls a one-element array on return, and a caller doing
    # `foreach ($id in Get-GradleWorkerIds ..)` over a scalar still works, but `.Count` on it does not.
    return , $ids.ToArray()
}

function Stop-GradleWorkerOrphan {
    <#
    .SYNOPSIS
        Stops one surviving worker, reporting the outcome. True only when it was actually stopped.
    #>
    param(
        [Parameter(Mandatory)][int]$Id,
        [Parameter(Mandatory)][string]$Why
    )

    $proc = Get-Process -Id $Id -ErrorAction SilentlyContinue
    if (-not $proc) { return $false }

    try {
        Stop-Process -Id $Id -Force -ErrorAction Stop
        Write-Host "Reaped orphaned Gradle test worker $Id - $Why" -ForegroundColor Yellow
        return $true
    }
    catch {
        # Reported, never fatal: the caller is releasing its build domain and a failure to reap is
        # worth reading about, but it is not the verdict of the check that just ran.
        Write-Host "Could not stop Gradle test worker ${Id}: $($_.Exception.Message)" -ForegroundColor DarkYellow
        return $false
    }
}
