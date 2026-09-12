#requires -Version 7.0
<#
.SYNOPSIS
    Keeps the batch runners working: reaps the jams that stop them, restarts an instance that died.

.DESCRIPTION
    Written 2026-09-05 after two build-domain jams in one morning, both cleared by hand and both the
    same shape. Six tickets were parked on that shape in a single day by six different sessions
    (S2577, S2578, S2580, S2582, S2584, S2585), which is the measurement that justifies a standing
    process rather than another manual sweep.

    THIS IS A STOPGAP, NOT THE FIX. The defect is in the canon harness's liveness layer, and by
    CLAUDE.md Rule 8 it is repaired in a canon session, never by editing a harness body here. What
    this script does is bound the damage until that lands: it detects the exact states measured on
    2026-09-05 and clears them, then keeps the runner instances alive so the queue keeps draining.

    Four reapers, each with its own evidence rule. Every one of them refuses to act on a process it
    cannot positively identify as build machinery - the IDE, a Claude session and this watchdog are
    never candidates, because the cost of killing the wrong process on the owner's workstation is far
    higher than one extra minute of a jam.

      1. Stalled lock holder. A Build.* lock whose holder and whole descendant tree burn ~no CPU
         across a sampling window, older than -StallMinutes. This is the state `processAlive: True`
         reports as healthy and the reason a 51-minute hold looked like work: liveness answers three
         ways, not two - working, blocked, dead - and only the CPU ratio separates the first two.
      2. Orphaned gradle client. The -Xmx64m launcher JVM whose wrapper process is gone. Killing a
         wrapper does not kill it (S2585), so it waits on a daemon forever holding nothing useful.
      3. Runaway worker. A gradle-spawned JVM burning CPU past -RunawayMinutes whose owning wrapper
         no longer exists. Measured instance: 5471 s of CPU in 102 min with no test timeout.
      4. Dead-pid queue tickets. A ticket whose enqueuing process exited is evictable by nothing
         (S2577), reaches the head of the queue and forfeits a reservation window for everyone.

    Leases are deliberately NOT reaped here. A lease held past any age can still belong to a live
    agent mid-phase, and killing that costs a ticket's work; the immortal-host-session case (S2578)
    needs the canon fix, not a guess by wall clock.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/agent-watchdog.ps1 -WhatIf
    One pass, reporting what it would reap and changing nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/agent-watchdog.ps1 -Instances a,b
    Supervise indefinitely, keeping runner instances a and b alive.

Exit codes: 0 a pass completed (or the supervised loop was stopped by its stop file), 2 the
repository root could not be resolved, 3 an argument is out of range.
#>
param(
    # Runner instances to keep alive. Empty means "reap only, supervise nothing".
    [string[]] $Instances = @(),

    # Seconds between passes.
    [int] $IntervalSeconds = 90,

    # A lock holder quiet for this long, with no gradle daemon working anywhere, is a stall. Raised
    # from 12 on 2026-09-05: at 12 the reaper fired on builds that were merely waiting for a daemon
    # to start, and daemon startup plus a cold configuration phase routinely exceeds that.
    [int] $StallMinutes = 25,

    # A gradle-spawned worker burning CPU past this, with no live wrapper, is a runaway.
    [int] $RunawayMinutes = 75,

    # Seconds of wall clock used to measure whether a process is actually computing.
    [int] $SampleSeconds = 6,

    # CPU seconds per sampling window below which a process counts as not working.
    [double] $IdleCpuThreshold = 0.4,

    # Stop after this many passes. 0 means "until the stop file appears".
    [int] $MaxPasses = 0,

    # Report and change nothing.
    [switch] $WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Written to the error stream directly rather than with Write-Error: $ErrorActionPreference is Stop
# above, so Write-Error terminates the script and the process exits 1, leaving the 2 and 3 this
# header promises unreachable. Caught by the contract suite, which is the point of having one.
if ($IntervalSeconds -lt 15 -or $SampleSeconds -lt 2 -or $StallMinutes -lt 2) {
    [Console]::Error.WriteLine('agent-watchdog: -IntervalSeconds >= 15, -SampleSeconds >= 2, -StallMinutes >= 2.')
    exit 3
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'a.ps1'))) {
    [Console]::Error.WriteLine("agent-watchdog: cannot resolve the repository root from $PSScriptRoot.")
    exit 2
}

# `pwsh -File` hands every argument through as one literal token, so `-Instances a,b` arrives as a
# single element named "a,b" and the supervisor would keep starting a runner by that name forever.
# Splitting here rather than asking every call site to use a wrapper script keeps the documented
# invocation working, which is the one the whole repository already uses.
$Instances = @($Instances |
    ForEach-Object { $_ -split '[,;\s]+' } |
    Where-Object { $_ -ne '' })

$tempDir = Join-Path $repoRoot 'temp'
$logDir = Join-Path $tempDir 'scratch/watchdog'
$null = New-Item -ItemType Directory -Force -Path $logDir
$logFile = Join-Path $logDir 'watchdog.log'
$stopFile = Join-Path $tempDir 'STOP-AGENT-WATCHDOG'

function Write-WatchdogLine {
    param([string] $Text)
    $stamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    $line = "$stamp  $Text"
    # Console, not Write-Output: the success stream is a function's RETURN value in PowerShell, so
    # logging through it made every reaper return its log lines alongside its count, and the caller's
    # `$acted += ..` then tried to add an object array to an int. Caught by the first live dry run.
    [Console]::Out.WriteLine($line)
    Add-Content -LiteralPath $logFile -Value $line -Encoding utf8
}

# Every process below this one, breadth first. A gradle build is a wrapper, a launcher JVM and one
# or more workers, and judging the wrapper alone reports "idle" for a tree that is compiling hard.
function Get-DescendantIds {
    param([int] $RootId)
    $all = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, Name, CommandLine)
    $byParent = @{}
    foreach ($p in $all) {
        $key = [int]$p.ParentProcessId
        if (-not $byParent.ContainsKey($key)) { $byParent[$key] = @() }
        $byParent[$key] += [int]$p.ProcessId
    }
    $seen = [System.Collections.Generic.HashSet[int]]::new()
    $queue = [System.Collections.Generic.Queue[int]]::new()
    $queue.Enqueue($RootId)
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        if (-not $seen.Add($current)) { continue }
        if ($byParent.ContainsKey($current)) {
            foreach ($child in $byParent[$current]) { $queue.Enqueue($child) }
        }
    }
    return @($seen)
}

# CPU seconds consumed across the window. The answer this whole script turns on: a process that is
# alive and one that is working are different states, and only this measurement separates them.
function Measure-CpuDelta {
    param([int[]] $Ids, [int] $Seconds)
    $before = @{}
    foreach ($id in $Ids) {
        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($p) { $before[$id] = $p.CPU }
    }
    if ($before.Count -eq 0) { return 0.0 }
    Start-Sleep -Seconds $Seconds
    $delta = 0.0
    foreach ($id in $before.Keys) {
        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($p -and $null -ne $p.CPU) { $delta += ($p.CPU - $before[$id]) }
    }
    return [math]::Round($delta, 2)
}

# Only build machinery is ever a candidate. Matching on the command line rather than on the name is
# what keeps the IDE, a Claude session and this watchdog out of the set: all three are pwsh or java
# like the targets are, and a name test would put them in range.
function Test-IsBuildMachinery {
    param([string] $CommandLine)
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return $false }
    if ($CommandLine -match 'agent-watchdog|claude\.exe|language-server') { return $false }
    return $CommandLine -match 'gradlew|org\.gradle|GradleDaemon|kotlin-compiler-embeddable|check-standard-fast|build-debug|a\.ps1'
}

function Stop-Candidate {
    param([int] $Id, [string] $Why)
    $p = Get-Process -Id $Id -ErrorAction SilentlyContinue
    if (-not $p) { return $false }
    if ($WhatIf) { Write-WatchdogLine "WOULD KILL $Id ($($p.ProcessName)) - $Why"; return $false }
    try {
        Stop-Process -Id $Id -Force -ErrorAction Stop
        Write-WatchdogLine "KILLED $Id ($($p.ProcessName)) - $Why"
        return $true
    } catch {
        Write-WatchdogLine "FAILED to kill $Id - $($_.Exception.Message)"
        return $false
    }
}

# THE correction of 2026-09-05, and the reason the stall reaper cannot judge a process tree alone.
# A gradle daemon DETACHES: its parent is gone by design, so it is never a descendant of the wrapper
# that holds the lock. The wrapper, its cmd shim and the -Xmx64m launcher client all sit at ~0 CPU
# while the daemon compiles at full speed somewhere else in the process table. Measuring only the
# tree therefore reads every healthy build as a stall - and did: 24 live builds were killed in 105
# minutes, always at the threshold, always "tree of 3, 0s CPU", while exactly ONE build in that
# window reached BUILD SUCCESSFUL. A busy daemon anywhere on the box means the domain is progressing,
# so no holder is reaped while one is working. Conservative on purpose: a jam costs minutes, a killed
# build costs a ticket's work.
function Test-AnyGradleDaemonBusy {
    param([int] $Seconds, [double] $BusyFloor)
    $daemons = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -Property ProcessId, CommandLine |
        Where-Object { $_.CommandLine -match 'GradleDaemon|gradle-launcher' -and $_.CommandLine -notmatch '-Xmx64m' })
    if ($daemons.Count -eq 0) { return $false }
    $delta = Measure-CpuDelta -Ids @($daemons.ProcessId) -Seconds $Seconds
    return ($delta -ge $BusyFloor)
}

function Invoke-StalledHolderReaper {
    $reaped = 0
    foreach ($domain in 'BUILD.PHONE.LOCK', 'BUILD.WEAR.LOCK') {
        $lockPath = Join-Path $tempDir $domain
        if (-not (Test-Path -LiteralPath $lockPath)) { continue }
        try { $lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json } catch { continue }
        if (-not $lock.pid) { continue }
        $holder = Get-Process -Id $lock.pid -ErrorAction SilentlyContinue
        if (-not $holder) { continue }

        $ageMin = [int]((Get-Date) - $holder.StartTime).TotalMinutes
        if ($ageMin -lt $StallMinutes) { continue }

        $tree = Get-DescendantIds -RootId ([int]$lock.pid)
        $delta = Measure-CpuDelta -Ids $tree -Seconds $SampleSeconds
        if ($delta -gt $IdleCpuThreshold) { continue }

        if (Test-AnyGradleDaemonBusy -Seconds $SampleSeconds -BusyFloor ($SampleSeconds * 0.5)) {
            Write-WatchdogLine "NOT A STALL $domain held $ageMin min by pid $($lock.pid) - tree quiet but a gradle daemon is working; the daemon is detached and never a descendant"
            continue
        }

        Write-WatchdogLine "STALL $domain held $ageMin min by pid $($lock.pid), tree of $($tree.Count) burned ${delta}s CPU in ${SampleSeconds}s, no gradle daemon busy - reaping"
        foreach ($id in ($tree | Sort-Object -Descending)) {
            $info = Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
            if ($id -eq $lock.pid -or ($info -and (Test-IsBuildMachinery $info.CommandLine))) {
                if (Stop-Candidate -Id $id -Why "stalled $domain tree, ${delta}s CPU in ${SampleSeconds}s over $ageMin min") { $reaped++ }
            }
        }
    }
    return $reaped
}

function Invoke-OrphanAndRunawayReaper {
    $reaped = 0
    $procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -Property ProcessId, ParentProcessId, CommandLine)
    foreach ($p in $procs) {
        if (-not (Test-IsBuildMachinery $p.CommandLine)) { continue }
        $live = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
        if (-not $live) { continue }
        $parentAlive = [bool](Get-Process -Id $p.ParentProcessId -ErrorAction SilentlyContinue)
        $ageMin = [int]((Get-Date) - $live.StartTime).TotalMinutes

        # A gradle DAEMON is orphaned by design - it detaches and outlives its client on purpose, so
        # "no parent" says nothing about it. Only the launcher client and forked workers are judged.
        $isDaemon = $p.CommandLine -match 'GradleDaemon|gradle-launcher.*daemon'
        if ($isDaemon) { continue }

        $isLauncherClient = $p.CommandLine -match '-Xmx64m' -and $p.CommandLine -match 'org\.gradle\.appname=gradlew'
        if ($isLauncherClient -and -not $parentAlive -and $ageMin -ge $StallMinutes) {
            $delta = Measure-CpuDelta -Ids @($p.ProcessId) -Seconds $SampleSeconds
            if ($delta -le $IdleCpuThreshold) {
                if (Stop-Candidate -Id $p.ProcessId -Why "orphaned gradle client, wrapper gone, ${delta}s CPU in ${SampleSeconds}s over $ageMin min") { $reaped++ }
            }
            continue
        }

        # A runaway must actually be RUNNING away. Without the busy test this branch reaped any
        # orphaned non-daemon JVM past the age, and the first live dry run proposed killing an idle
        # 3.5 GB process while calling it "still burning 0s CPU" - a verdict that contradicted itself
        # in its own sentence. An orphan that consumes nothing costs memory, not throughput, and
        # gradle expires its own idle daemons; only the CPU burner blocks other agents.
        if (-not $parentAlive -and $ageMin -ge $RunawayMinutes) {
            $delta = Measure-CpuDelta -Ids @($p.ProcessId) -Seconds $SampleSeconds
            $busyFloor = $SampleSeconds * 0.5
            if ($delta -ge $busyFloor) {
                if (Stop-Candidate -Id $p.ProcessId -Why "runaway worker, wrapper gone, running $ageMin min, still burning ${delta}s CPU per ${SampleSeconds}s") { $reaped++ }
            }
        }
    }
    return $reaped
}

function Invoke-DeadTicketReaper {
    $reaped = 0
    $queueDirs = @(Get-ChildItem -LiteralPath $tempDir -Directory -Filter '*.QUEUE' -ErrorAction SilentlyContinue)
    foreach ($dir in $queueDirs) {
        foreach ($file in @(Get-ChildItem -LiteralPath $dir.FullName -File -ErrorAction SilentlyContinue)) {
            try { $ticket = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json } catch { continue }
            if (-not $ticket.pid) { continue }
            if (Get-Process -Id $ticket.pid -ErrorAction SilentlyContinue) { continue }
            if ($WhatIf) { Write-WatchdogLine "WOULD DROP $($dir.Name)/$($file.Name) - pid $($ticket.pid) gone"; continue }
            Remove-Item -LiteralPath $file.FullName -Force
            Write-WatchdogLine "DROPPED $($dir.Name)/$($file.Name) - enqueuing pid $($ticket.pid) no longer exists"
            $reaped++
        }
    }
    return $reaped
}

# A runner that exited leaves the queue undrained, which is the idling the owner asked to end. The
# check is by command line rather than by remembered pid so a watchdog restart re-adopts instances
# it did not launch.
# S2696: the model policy is per instance, because the shape rule is enabled as a MEASUREMENT - one
# instance on it, the others on the rule it is being compared against - and a measurement that only
# survives until the watchdog restarts the instance is not one. Read from the profile rather than
# passed as a parameter: the watchdog is itself restarted by hand and by the machine, so a policy
# living in an argument would be re-typed correctly or not at all. Absent key -> 'tiered', which is
# the runner's own default, so a profile with no such block behaves exactly as before.
#
# S2698 absorbed that narrow key: the policy is now one field of the instance's record in
# runner.instances, beside the command and the argument template it launches with, so an instance is
# declared in one place instead of in a separate map per key - two maps drift silently the moment a
# name is added to one and forgotten in the other.
function Get-InstanceModelPolicy {
    param([string] $Instance)

    $default = 'tiered'
    $profilePath = Join-Path $repoRoot '.sza-profile.json'
    if (-not (Test-Path -LiteralPath $profilePath)) { return $default }
    try {
        $map = (Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json).runner.instances
    } catch {
        # A profile that does not parse is a repository-wide failure other scripts report loudly;
        # the watchdog's job is to keep the queue draining, so it falls back rather than exiting.
        return $default
    }
    if ($null -eq $map) { return $default }
    $record = $map.PSObject.Properties[$Instance]
    if ($null -eq $record -or $null -eq $record.Value) { return $default }
    $field = $record.Value.PSObject.Properties['modelPolicy']
    if ($null -eq $field -or [string]::IsNullOrWhiteSpace([string]$field.Value)) { return $default }
    return [string]$field.Value
}

function Invoke-RunnerSupervisor {
    $started = 0
    foreach ($instance in $Instances) {
        $running = @(Get-CimInstance Win32_Process -Filter "Name='pwsh.exe'" -Property ProcessId, CommandLine |
            Where-Object { $_.CommandLine -match 'run-spec-queue' -and $_.CommandLine -match "-Instance\s+$instance\b" })
        if ($running.Count -gt 0) { continue }
        $policy = Get-InstanceModelPolicy -Instance $instance
        if ($WhatIf) { Write-WatchdogLine "WOULD START runner instance $instance with model policy $policy"; continue }
        $out = Join-Path $logDir "queue-$instance.out.txt"
        $err = Join-Path $logDir "queue-$instance.err.txt"
        $proc = Start-Process -FilePath 'pwsh' `
            -ArgumentList @('-NoProfile', '-File', 'scripts/utils/run-spec-queue.ps1',
                '-ModelPolicy', $policy, '-Instance', $instance, '-TimeoutMinutes', '60') `
            -WorkingDirectory $repoRoot -RedirectStandardOutput $out -RedirectStandardError $err `
            -WindowStyle Hidden -PassThru
        # The policy is named in the line because the journal it produces is compared against another
        # instance's, and "which rule was this instance on" has to be answerable from the log alone.
        Write-WatchdogLine "STARTED runner instance $instance as pid $($proc.Id) with model policy $policy"
        $started++
    }
    return $started
}

Write-WatchdogLine "watchdog up - interval ${IntervalSeconds}s, stall ${StallMinutes}m, runaway ${RunawayMinutes}m, instances '$($Instances -join ',')', whatIf=$WhatIf"
Write-WatchdogLine "stop file: $stopFile"

$pass = 0
while ($true) {
    $pass++
    if (Test-Path -LiteralPath $stopFile) {
        Write-WatchdogLine 'stop file present - exiting'
        break
    }

    $acted = 0
    try {
        $acted += Invoke-StalledHolderReaper
        $acted += Invoke-OrphanAndRunawayReaper
        $acted += Invoke-DeadTicketReaper
        $acted += Invoke-RunnerSupervisor
    } catch {
        # A watchdog that dies on one bad pass is worse than no watchdog: the jam it exists to clear
        # outlives it silently. Record and keep going.
        Write-WatchdogLine "pass $pass error: $($_.Exception.Message)"
    }

    if ($acted -gt 0) { Write-WatchdogLine "pass $pass - $acted action(s)" }

    if ($MaxPasses -gt 0 -and $pass -ge $MaxPasses) { break }
    Start-Sleep -Seconds $IntervalSeconds
}

exit 0
