#requires -Version 7.0
<#
.SYNOPSIS
    Find - and optionally kill - pwsh processes running this repository's scripts that a caller
    started and never reaped (S2610).

.DESCRIPTION
    A foreign agent runtime fires a repository script, hands the child a stdin pipe and never closes
    it. If that call omitted a mandatory parameter, PowerShell PROMPTS for it and the prompt reads
    stdin, so the process blocks before the first line of the script body and stays alive for as long
    as the runtime lives. Measured 2026-09-05: 15 such pairs alive 14-17 hours on 0.3-0.7 s of CPU
    each, and the work they were meant to do - phase ticks, a dev-log row, a post-change closure -
    silently did not happen. Mechanism and the reproductions: docs/DEV_OPS.md "Abandoned script
    processes".

    A candidate here is deliberately narrow, because the cost of a false positive is killing live
    work. Age and idleness alone do NOT separate the two populations - measured on the same machine,
    three LIVE queue-runner windows sat at 150 minutes and 2.6-3.2 s of CPU while three ABANDONED
    ones sat at 475 minutes and 5.4-5.8 s, so the live ones were both younger and cheaper than the
    dead ones by exactly the signals a threshold reads. Three structural exclusions do the real work:

      -NoExit          a console window: a human's, or a queue runner's. Never reaped.
      -Loop / runner   a script whose job is to keep running (dev-monitor-writer, run-spec-queue).
      live child       a process supervising something that is not itself a candidate - the
                       detached dev-monitor wrapper sat at 1757 minutes and 1.1 s of CPU and is
                       caught by nothing else.

    Reporting is the default. -Kill is the explicit opt-in and kills the whole process tree, because
    the abandoned calls come in pairs - an outer `pwsh -Command` and the inner `-File` it spawned -
    and killing the inner alone leaves the outer waiting on a child that will never return.

.PARAMETER OlderThanMinutes
    Minimum age. A repository script that legitimately runs this long is a long job, not a chore.

.PARAMETER MaxCpuSeconds
    Maximum processor time. A process blocked at parameter binding has paid only for pwsh startup.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/reap-abandoned-script-processes.ps1
    pwsh -NoProfile -File scripts/utils/reap-abandoned-script-processes.ps1 -Json
    pwsh -NoProfile -File scripts/utils/reap-abandoned-script-processes.ps1 -Kill

Exit codes: 0 = census complete (candidates found or not - this is a status query);
            1 = candidates found, ONLY under -StrictExit;
            2 = could not determine (the process table could not be enumerated).
#>
[CmdletBinding()]
param(
    [int]$OlderThanMinutes = 60,
    [double]$MaxCpuSeconds = 5,
    [switch]$Kill,
    [switch]$Json,
    [switch]$StrictExit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# A command line names a repository script either absolutely, or relatively because the runtime set
# the working directory to the repo root. The relative form is what the abandoned calls actually
# carry, so matching only the absolute path would find none of them.
$scriptPathPatterns = @(
    [regex]::Escape($repoRoot)
    '(^|[\s"''])(\.[\\/])?scripts[\\/][^\s"'']+\.ps1'
    '(^|[\s"''])(\.[\\/])?dev[\\/]CATALOG[\\/]scripts[\\/][^\s"'']+\.ps1'
    '(^|[\s"''])(\.[\\/])?a\.ps1(\s|$)'
)

# Long-lived BY DESIGN. Each of these is supposed to outlive any threshold this script uses.
$neverReapPatterns = @(
    '-NoExit'
    '-Loop\b'
    'dev-monitor-writer'
    'run-spec-queue'
    'wait-for-lock-turn'
)

try {
    $allProcesses = @(Get-CimInstance Win32_Process -ErrorAction Stop)
}
catch {
    Write-Host "reap-abandoned-script-processes: cannot enumerate the process table - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

# Windows reuses process ids and never clears ParentProcessId when a parent dies, so a pid alone
# does not establish parentage: an unrelated process can name a long-dead parent whose number has
# since been handed to something else. Caught by this suite's own first run, where a stranger was
# attributed as the hung fixture's child and spared it. A real child cannot predate its parent, so
# the creation times are what make the link safe.
$creationByPid = @{}
foreach ($proc in $allProcesses) { $creationByPid[[int]$proc.ProcessId] = $proc.CreationDate }

$childrenByParent = @{}
foreach ($proc in $allProcesses) {
    $parent = [int]$proc.ParentProcessId
    if (-not $creationByPid.ContainsKey($parent)) { continue }
    if ($null -ne $proc.CreationDate -and $null -ne $creationByPid[$parent] -and
        $proc.CreationDate -lt $creationByPid[$parent]) { continue }
    if (-not $childrenByParent.ContainsKey($parent)) { $childrenByParent[$parent] = @() }
    $childrenByParent[$parent] += [int]$proc.ProcessId
}

$now = Get-Date
$examined = @{}

foreach ($proc in $allProcesses) {
    if ($proc.Name -notin @('pwsh.exe', 'powershell.exe')) { continue }
    $commandLine = [string]$proc.CommandLine
    if ([string]::IsNullOrWhiteSpace($commandLine)) { continue }

    $namesRepoScript = $false
    foreach ($pattern in $scriptPathPatterns) {
        if ($commandLine -match $pattern) { $namesRepoScript = $true; break }
    }
    if (-not $namesRepoScript) { continue }

    $protectedBy = $null
    foreach ($pattern in $neverReapPatterns) {
        if ($commandLine -match $pattern) { $protectedBy = $pattern; break }
    }

    $live = Get-Process -Id ([int]$proc.ProcessId) -ErrorAction SilentlyContinue
    if (-not $live) { continue }
    $ageMinutes = [math]::Round(($now - $live.StartTime).TotalMinutes, 0)
    $cpuSeconds = [math]::Round($live.CPU, 2)

    $examined[[int]$proc.ProcessId] = [pscustomobject]@{
        pid         = [int]$proc.ProcessId
        parentPid   = [int]$proc.ParentProcessId
        ageMinutes  = $ageMinutes
        cpuSeconds  = $cpuSeconds
        protectedBy = $protectedBy
        commandLine = $commandLine
        candidate   = $false
        reason      = ''
    }
}

# Second pass. "A live child that is not itself a candidate" can only be judged once every process
# has been scored on its own, so idleness is decided first and supervision afterwards.
foreach ($row in $examined.Values) {
    if ($row.protectedBy) { $row.reason = "protected: $($row.protectedBy)"; continue }
    if ($row.ageMinutes -lt $OlderThanMinutes) { $row.reason = "younger than $OlderThanMinutes min"; continue }
    if ($row.cpuSeconds -gt $MaxCpuSeconds) { $row.reason = "spent more than $MaxCpuSeconds s of CPU"; continue }
    $row.candidate = $true
    $row.reason = 'idle past the threshold and running a repository script'
}

# Owning a console is not supervising work. A process started with its own window gets a
# conhost.exe child from Windows, and treating that as "supervising live work" spared every such
# process - measured here, where the abandoned processes on this machine own no child at all
# because they inherited the calling runtime's console, while a freshly started one always does.
$notWork = @('conhost.exe', 'WerFault.exe')

foreach ($row in $examined.Values) {
    if (-not $row.candidate) { continue }
    $childPids = @()
    if ($childrenByParent.ContainsKey($row.pid)) { $childPids = @($childrenByParent[$row.pid]) }
    foreach ($childPid in $childPids) {
        $child = Get-Process -Id $childPid -ErrorAction SilentlyContinue
        if (-not $child) { continue }
        if (("$($child.ProcessName).exe") -in $notWork) { continue }
        $childIsCandidate = $examined.ContainsKey($childPid) -and $examined[$childPid].candidate
        if (-not $childIsCandidate) {
            $row.candidate = $false
            $row.reason = "supervising live pid $childPid, which is not itself abandoned"
            break
        }
    }
}

$candidates = @($examined.Values | Where-Object { $_.candidate } | Sort-Object ageMinutes -Descending)
$protected = @($examined.Values | Where-Object { -not $_.candidate } | Sort-Object ageMinutes -Descending)

$killed = @()
if ($Kill -and $candidates.Count -gt 0) {
    # Outermost first: killing a tree takes the inner process with it, so an inner pid reached later
    # is already gone and its failure to die is not an error worth reporting.
    foreach ($row in ($candidates | Sort-Object { $_.parentPid -in $candidates.pid } )) {
        $victim = Get-Process -Id $row.pid -ErrorAction SilentlyContinue
        if (-not $victim) { continue }
        try { $victim.Kill($true); $killed += $row.pid }
        catch { Write-Host "  could not kill pid $($row.pid): $($_.Exception.Message)" -ForegroundColor Yellow }
    }
}

if ($Json) {
    [pscustomobject]@{
        repoRoot          = $repoRoot
        olderThanMinutes  = $OlderThanMinutes
        maxCpuSeconds     = $MaxCpuSeconds
        candidates        = $candidates
        protected         = $protected
        killed            = $killed
    } | ConvertTo-Json -Depth 5 -Compress
}
else {
    if ($candidates.Count -eq 0) {
        Write-Host "reap-abandoned-script-processes: no abandoned repository-script processes." -ForegroundColor Green
    }
    else {
        Write-Host "reap-abandoned-script-processes: $($candidates.Count) abandoned process(es)." -ForegroundColor Yellow
        foreach ($row in $candidates) {
            Write-Host ("  pid {0,-6} parent {1,-6} age {2,5} min  cpu {3,6} s" -f $row.pid, $row.parentPid, $row.ageMinutes, $row.cpuSeconds)
            Write-Host ("      {0}" -f ($row.commandLine -replace '\s+', ' ')) -ForegroundColor Gray
        }
        if (-not $Kill) {
            Write-Host "  Nothing was killed. Re-run with -Kill to end these process trees." -ForegroundColor Cyan
        }
    }
    if ($killed.Count -gt 0) {
        Write-Host "  killed $($killed.Count) process tree(s): $($killed -join ', ')" -ForegroundColor Green
    }
    if ($protected.Count -gt 0) {
        Write-Host "  $($protected.Count) repository-script process(es) left alone:" -ForegroundColor DarkGray
        foreach ($row in $protected) {
            Write-Host ("      pid {0,-6} age {1,5} min - {2}" -f $row.pid, $row.ageMinutes, $row.reason) -ForegroundColor DarkGray
        }
    }
}

if ($candidates.Count -gt 0 -and $StrictExit) {
    Write-Error ("reap-abandoned-script-processes: $($candidates.Count) abandoned repository-script " +
        "process(es) are still running - re-run with -Kill to end them, or drop -StrictExit to treat " +
        "this as a status query.") -ErrorAction Continue
    exit 1
}
exit 0
