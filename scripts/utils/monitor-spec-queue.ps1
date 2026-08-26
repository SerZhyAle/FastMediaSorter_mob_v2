<#
.SYNOPSIS
    Show what the unattended queue runners are doing right now.

.DESCRIPTION
    Answers the question an operator actually has after leaving the machine working: is anything
    still running, what is it on, what has it finished, and is something stuck.

    Four sources, each of which knows something the others do not:

      - Headless claude children (`claude.exe` started with -p). Their existence is the only proof
        that a run is still alive; a journal cannot say whether the process behind its last row died.
        Each one is listed with the age of the newest file its ticket has written, because existence
        alone does not separate a working run from a wedged one: a child waiting on the API sits at
        about 2% of one core, so pid age and CPU time read the same either way (measured 2026-08-25,
        two runs looked hung at 19 and 16 minutes and were both mid-plan).
      - Ticket leases (scripts/spec_catalog/ticket-lease.ps1). A live lease names the ticket a run is
        working *now* - the journal only gets its row when the ticket is finished.
      - The per-instance journals under temp/spec-queue/runs-<instance>.jsonl - what finished, how it
        ended, on which model, and how long it took.
      - temp/BUILD.LOCK and temp/CODE.LOCK - who is building or editing, and who is queued behind
        them. Two parallel instances spend real time here, and a run that looks idle is usually just
        waiting for the other one's gradle.

    Read-only. It starts nothing, stops nothing and writes nothing.

    Exit codes: 0 - the report was produced (including "nothing is running", which is an answer),
                2 - the repository layout could not be read (temp/ missing, journals unreadable).

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/monitor-spec-queue.ps1
    One snapshot.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/monitor-spec-queue.ps1 -Watch
    Refresh every 30 seconds until Ctrl+C.
#>
[CmdletBinding()]
param(
    # How many finished tickets to list per instance. One by default: the operator's question between
    # refreshes is "what changed since I last looked", and the full history is in the journal file.
    [int] $Tail = 1,

    # Refresh until interrupted instead of printing one snapshot.
    [switch] $Watch,

    # Seconds between refreshes in -Watch mode.
    [int] $IntervalSeconds = 30,

    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot 'help.ps1') -Name 'scripts/utils/monitor-spec-queue.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'

$tempDir = Join-Path $RepoRoot 'temp'
if (-not (Test-Path -LiteralPath $tempDir)) {
    Write-Host "monitor-spec-queue: temp/ not found under $RepoRoot." -ForegroundColor Red
    exit 2
}
$runDir = Join-Path $tempDir 'spec-queue'

function Write-Section([string] $Title) {
    Write-Host ''
    Write-Host ("-- {0} " -f $Title).PadRight(78, '-') -ForegroundColor DarkGray
}

function Get-TicketLastWrite {
    param([string] $Id)

    # The lease heartbeat is deliberately NOT counted here. The runner refreshes it on a timer whether
    # or not the pipeline is producing anything, so a lease proves the process is scheduled - only a
    # file it wrote proves it is working. Two places carry that evidence: the ticket's spec and
    # tactical folder under PLAN/, and its scratch dir under temp/.
    if ($Id -notmatch '^S\d{4}$') { return $null }

    $files = @()

    $planDir = Join-Path $RepoRoot 'PLAN'
    if (Test-Path -LiteralPath $planDir) {
        # Two passes on purpose: -Filter applies at every level of -Recurse, so a single recursive call
        # filtered by the id prefix would miss INDEX.md and the phase files inside PLAN/Sxxxx_<slug>/.
        foreach ($top in @(Get-ChildItem -LiteralPath $planDir -Filter ($Id + '_*') -ErrorAction SilentlyContinue)) {
            if ($top.PSIsContainer) {
                $files += @(Get-ChildItem -LiteralPath $top.FullName -Recurse -File -ErrorAction SilentlyContinue)
            } else {
                $files += $top
            }
        }
    }

    $scratch = Join-Path $tempDir $Id
    if (Test-Path -LiteralPath $scratch) {
        $files += @(Get-ChildItem -LiteralPath $scratch -Recurse -File -ErrorAction SilentlyContinue)
    }

    if ($files.Count -eq 0) { return $null }
    $newest = $files | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $relative = $newest.FullName
    if ($relative.StartsWith($RepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $relative = $relative.Substring($RepoRoot.Length).TrimStart('\', '/')
    }
    return [pscustomobject]@{
        Minutes = ((Get-Date) - $newest.LastWriteTime).TotalMinutes
        Path    = $relative.Replace('\', '/')
    }
}

function Format-LockAge {
    param($AcquiredAt)

    if ($null -eq $AcquiredAt) { return 'for an unknown time' }
    try {
        $when = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$AcquiredAt).LocalDateTime
    } catch {
        return ("since {0}" -f $AcquiredAt)
    }
    $minutes = ((Get-Date) - $when).TotalMinutes
    if ($minutes -lt 1) {
        return ("{0,3:N0} sec   (since {1:HH:mm:ss})" -f ($minutes * 60), $when)
    }
    return ("{0,3:N0} min   (since {1:HH:mm:ss})" -f $minutes, $when)
}

function Show-Snapshot {

    Write-Host ''
    Write-Host ("spec-queue monitor   {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) -ForegroundColor Cyan

    # The child list is gathered first because the stop-flag section below needs it, but it is
    # PRINTED last: it is the block the operator reads on every refresh, so it belongs where the
    # cursor already is instead of scrolled off the top by the journals.
    $children = @(Get-CimInstance Win32_Process -Filter "Name = 'claude.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -match '\s-p\s' })

    # --- leases: what is being worked on right now ----------------------------------------------
    Write-Section 'ticket leases (what is claimed now)'
    $leaseScript = Join-Path $RepoRoot 'scripts\spec_catalog\ticket-lease.ps1'
    if (Test-Path -LiteralPath $leaseScript) {
        $leaseOut = & pwsh -NoProfile -File $leaseScript -Verb List 2>&1 | Out-String
        # One id per line costs a screen-height per five tickets and says nothing a comma cannot. The
        # list is short by construction - it is bounded by the number of parallel instances.
        $leaseIds = @($leaseOut -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^S\d{4}$' })
        if ($leaseIds.Count -eq 0) {
            Write-Host '  nothing leased.' -ForegroundColor DarkYellow
        } else {
            Write-Host ("  " + ($leaseIds -join ', '))
        }
    } else {
        Write-Host '  ticket-lease.ps1 not found.' -ForegroundColor DarkYellow
    }

    # --- locks -----------------------------------------------------------------------------------
    Write-Section 'locks (who is building or editing)'
    foreach ($lock in @('BUILD.LOCK', 'CODE.LOCK')) {
        $path = Join-Path $tempDir $lock
        if (-not (Test-Path -LiteralPath $path)) {
            Write-Host ("  {0,-11} free" -f $lock) -ForegroundColor DarkGray
            continue
        }
        $held = '(unreadable)'
        try {
            $body = Get-Content -LiteralPath $path -Raw -ErrorAction Stop | ConvertFrom-Json
            # The file stores epoch milliseconds. Printed raw it answers nothing an operator asks: the
            # question is "how long has this been held", not "at which millisecond did it start".
            $held = "{0}  held {1}" -f $body.Reason, (Format-LockAge $body.AcquiredAt)
        } catch {
            $held = (Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue).Trim()
        }
        Write-Host ("  {0,-11} HELD  {1}" -f $lock, $held) -ForegroundColor Yellow
    }

    # --- journals ---------------------------------------------------------------------------------
    Write-Section ("finished tickets (last {0} per instance)" -f $Tail)
    $journals = @()
    if (Test-Path -LiteralPath $runDir) {
        $journals = @(Get-ChildItem -LiteralPath $runDir -Filter 'runs-*.jsonl' -ErrorAction SilentlyContinue)
    }
    if ($journals.Count -eq 0) {
        Write-Host '  no run journal yet.' -ForegroundColor DarkYellow
    } else {
        foreach ($j in $journals) {
            $instance = $j.BaseName -replace '^runs-', ''
            $rows = @()
            foreach ($line in (Get-Content -LiteralPath $j.FullName -ErrorAction SilentlyContinue)) {
                if ([string]::IsNullOrWhiteSpace($line)) { continue }
                try { $rows += ($line | ConvertFrom-Json) } catch { continue }
            }
            Write-Host ''
            Write-Host ("  instance '{0}' - {1} ticket(s) recorded" -f $instance, $rows.Count) -ForegroundColor Cyan
            if ($rows.Count -eq 0) { continue }
            foreach ($r in ($rows | Select-Object -Last $Tail)) {
                $colour = if ($r.moved) { 'Green' } elseif ($r.outcome -ne 'ok') { 'Red' } else { 'Yellow' }
                Write-Host ("    {0,-6} {1,-14} -> {2,-18} {3,-26} {4,3} min  {5}" -f `
                        $r.id, $r.statusBefore, $r.statusAfter, $r.outcome, $r.minutes, $r.model) -ForegroundColor $colour
            }
            $moved = @($rows | Where-Object { $_.moved }).Count
            Write-Host ("    total: {0} run, {1} moved, {2} stayed put" -f $rows.Count, $moved, ($rows.Count - $moved)) -ForegroundColor DarkGray
        }
    }

    # --- stop flags ---------------------------------------------------------------------------------
    $stopFiles = @(Get-ChildItem -LiteralPath $tempDir -Filter 'STOP-SPEC-QUEUE*' -ErrorAction SilentlyContinue)
    if ($stopFiles.Count -gt 0) {
        Write-Section 'stop requested'
        foreach ($f in $stopFiles) {
            $pending = '{0:N0}' -f ((Get-Date) - $f.LastWriteTime).TotalMinutes
            Write-Host ("  {0}  requested {1} min ago" -f $f.Name, $pending) -ForegroundColor Yellow
        }
        # The pending age is the whole point of this section. A stop is read only between tickets, so
        # one that has been pending for half an hour is waiting on the run listed below, not failing.
        if ($children.Count -gt 0) {
            Write-Host '  waiting for the ticket(s) listed under "running" to end.' -ForegroundColor DarkGray
            Write-Host '  To stop without waiting:  .\a.ps1 rs -Kill' -ForegroundColor DarkGray
        } else {
            Write-Host '  nothing is running - the next start clears the flag and proceeds.' -ForegroundColor DarkGray
        }
    }

    # --- running children (last on purpose - see the gathering note above) ----------------------
    Write-Section 'running'
    if ($children.Count -eq 0) {
        Write-Host '  no headless claude child is running.' -ForegroundColor DarkYellow
    } else {
        foreach ($c in $children) {
            $started = $null
            try { $started = $c.CreationDate } catch { $started = $null }
            $ageMinutes = if ($started) { ((Get-Date) - $started).TotalMinutes } else { $null }
            $age = if ($null -ne $ageMinutes) { '{0,4:N0} min' -f $ageMinutes } else { '   ? min' }
            # The ticket id is in the prompt the parent handed it, which is the whole point of showing
            # the command line rather than just the pid.
            $ticket = if ($c.CommandLine -match '(S\d{4})') { $Matches[1] } else { '?' }
            $model = if ($c.CommandLine -match '--model\s+(\S+)') { $Matches[1] } else { 'default' }
            Write-Host ("  pid {0,-7} {1}  ticket {2}  model {3}" -f $c.ProcessId, $age, $ticket, $model) -ForegroundColor Green

            $write = Get-TicketLastWrite -Id $ticket

            # Judge the silence against this run's own age, never against the file's absolute date. A
            # ticket whose spec was last edited four days ago is not a stalled run, it is a run that has
            # not written anything yet - and a five-minute-old child cannot have been quiet for longer
            # than five minutes. Without this cap the first minutes of every run reported days of silence.
            $quietMinutes = if ($null -eq $write) { $ageMinutes }
                elseif ($null -eq $ageMinutes) { $write.Minutes }
                else { [math]::Min($write.Minutes, $ageMinutes) }
            $writeColour = if ($null -eq $quietMinutes) { 'DarkGray' }
                elseif ($quietMinutes -lt 5) { 'Green' }
                elseif ($quietMinutes -lt 15) { 'Yellow' }
                else { 'Red' }

            if ($null -eq $write) {
                Write-Host '        last write   nothing on disk for this ticket yet' -ForegroundColor $writeColour
            } elseif ($null -ne $ageMinutes -and $write.Minutes -gt ($ageMinutes + 1)) {
                Write-Host ("        last write   nothing since this run started; newest is {0}" -f $write.Path) -ForegroundColor $writeColour
            } else {
                Write-Host ("        last write {0,4:N0} min ago   {1}" -f $write.Minutes, $write.Path) -ForegroundColor $writeColour
            }

            if ($null -ne $quietMinutes -and $quietMinutes -ge 15) {
                # Silence this long is worth explaining rather than alarming: the two quiet stretches of
                # a pipeline are a gradle build (see the locks section) and a single long model turn, and
                # neither writes anything until it ends.
                Write-Host '        quiet for a while - check the locks above; a queued or running build writes nothing.' -ForegroundColor DarkGray
            }
        }
    }

    Write-Host ''
}

if ($Watch) {
    Write-Host 'monitor-spec-queue: refreshing until Ctrl+C.' -ForegroundColor DarkGray
    while ($true) {
        Clear-Host
        Show-Snapshot
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Show-Snapshot
exit 0
