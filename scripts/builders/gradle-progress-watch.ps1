<#
.SYNOPSIS
    Runs a Gradle invocation that prints signs of life while a long task says nothing.

.DESCRIPTION
    S3290: Gradle's plain console prints `> Task :x` when a task STARTS and nothing at all until it
    ends. A device build runs with the S3094 reuse-disabling flags, so every task executes from
    scratch and several of them are silent for minutes - measured 2026-09-18 on a run that ended
    BUILD SUCCESSFUL in 4m 51s, 49 actionable tasks, 49 executed. Two such runs were abandoned as
    hung, and the evidence cited for the diagnosis was a flat Kotlin-daemon CPU, which is the normal
    state of that daemon outside the two Kotlin tasks (3418 -> 3554 s on ksp + compile, then exactly
    3554 s through hilt, ASM and dex).

    So the wait itself is reported here: which task is running, how long it has been silent, and
    whether the JVMs are burning CPU. A wall-clock ceiling ends a run that really is stuck, the way
    fms.unitTestTimeoutMinutes (S2585) ends a hung unit task - the caller stops waiting and reaches
    its finally, instead of holding a build domain forever.

    Rule 35: the ceiling kills the Gradle CLIENT this call started, never a daemon. The daemon is not
    a child of the client, it is shared with every other session on the host, and it cancels the build
    on its own once the client disconnects.

    Dot-source this file; it defines functions and never exits.
#>

# Deliberately no Set-StrictMode here: this file is dot-sourced, so it would impose strict mode on
# every consumer's whole scope. Same reasoning as gradle-run-verdict.ps1 next to it.

# Returned by Invoke-GradleWithProgress when the ceiling fired. 124 is what timeout(1) returns, so a
# reader who has never opened this file still recognises it.
$script:GradleProgressTimeoutExitCode = 124

function Get-GradleProgressTimeoutExitCode {
    <#
    .SYNOPSIS
        The exit code a timed-out run reports. A function so callers cannot drift from the value.
    #>
    return $script:GradleProgressTimeoutExitCode
}

function Get-GradleTaskFromLine {
    <#
    .SYNOPSIS
        The task path a Gradle console line announces, or $null when the line announces none.
    #>
    param(
        [AllowNull()][AllowEmptyString()][string]$Line
    )

    if ([string]::IsNullOrWhiteSpace($Line)) { return $null }
    if ($Line -match '^>\s+Task\s+(\S+)') { return $Matches[1] }
    return $null
}

function Get-GradleJvmSample {
    <#
    .SYNOPSIS
        CPU seconds and resident size of every heavyweight JVM on the host, keyed by process id.

    .DESCRIPTION
        Reported as a sample rather than a verdict: the caller diffs two samples, because an absolute
        CPU total says nothing about whether work is happening now. That was the exact misreading in
        the S3290 capture.

    .PARAMETER Snapshot
        Injection seam for the contract suite: objects carrying Id, ProcessName, CPU and
        WorkingSet64. Left unset, the live java processes are read.
    #>
    param(
        [int]$MinimumWorkingSetMb = 500,
        [AllowNull()][object[]]$Snapshot
    )

    if ($null -eq $Snapshot) {
        $Snapshot = @(Get-Process -Name java -ErrorAction SilentlyContinue)
    }

    $sample = @{}
    foreach ($process in $Snapshot) {
        $workingSetMb = [math]::Round($process.WorkingSet64 / 1MB)
        if ($workingSetMb -lt $MinimumWorkingSetMb) { continue }
        $sample[[int]$process.Id] = [pscustomobject]@{
            Id           = [int]$process.Id
            Name         = [string]$process.ProcessName
            CpuSeconds   = [math]::Round([double]$process.CPU)
            WorkingSetMb = $workingSetMb
        }
    }
    return $sample
}

function Format-GradleHeartbeat {
    <#
    .SYNOPSIS
        One line saying what is running, for how long it has been silent, and who is burning CPU.

    .PARAMETER Previous
        The sample taken at the previous heartbeat. Absent for the first one, where every JVM reports
        a delta of 0 rather than its lifetime total.
    #>
    param(
        [AllowNull()][AllowEmptyString()][string]$LastTask,
        [Parameter(Mandatory)][int]$SilentSeconds,
        [Parameter(Mandatory)][int]$ElapsedSeconds,
        [Parameter(Mandatory)][hashtable]$Current,
        [AllowNull()][hashtable]$Previous
    )

    $taskText = if ([string]::IsNullOrWhiteSpace($LastTask)) { '<no task announced yet>' } else { $LastTask }

    $jvmParts = @()
    foreach ($id in ($Current.Keys | Sort-Object)) {
        $now = $Current[$id]
        $was = if ($Previous -and $Previous.ContainsKey($id)) { $Previous[$id].CpuSeconds } else { $now.CpuSeconds }
        $jvmParts += "pid $id +$($now.CpuSeconds - $was)s cpu, $($now.WorkingSetMb) MB"
    }
    $jvmText = if ($jvmParts.Count -gt 0) { $jvmParts -join '; ' } else { 'no JVM over the reporting threshold' }

    return "[still running] $taskText - silent ${SilentSeconds}s, build elapsed ${ElapsedSeconds}s | $jvmText"
}

function Start-GradleClientProcess {
    <#
    .SYNOPSIS
        Starts gradlew with its output redirected to files, and returns the process object.
    #>
    param(
        [Parameter(Mandatory)][string]$GradleWrapper,
        [AllowEmptyCollection()][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogPath,
        [Parameter(Mandatory)][string]$ErrorLogPath
    )

    $startArgs = @{
        FilePath               = $GradleWrapper
        RedirectStandardOutput = $LogPath
        RedirectStandardError  = $ErrorLogPath
        NoNewWindow            = $true
        PassThru               = $true
    }
    if ($Arguments -and $Arguments.Count -gt 0) { $startArgs['ArgumentList'] = $Arguments }
    return Start-Process @startArgs
}

function Stop-GradleClientProcess {
    <#
    .SYNOPSIS
        Kills the Gradle client this run started, and the cmd shell gradlew.bat runs inside.

    .DESCRIPTION
        /T reaches the client JVM that gradlew.bat spawned - a child of this call and nothing else.
        The daemon is deliberately out of reach: it is shared, it is nobody's child, and it cancels
        the build itself once the client socket drops.
    #>
    param(
        [Parameter(Mandatory)][int]$ProcessId
    )

    & taskkill.exe /T /F /PID $ProcessId 2>&1 | Out-Null
}

function Invoke-GradleWithProgress {
    <#
    .SYNOPSIS
        Runs Gradle, echoes its output live, reports the silence, and ends the run at a ceiling.

    .PARAMETER Launcher
        Injection seam: returns an object exposing Id, HasExited and ExitCode. Left unset, the real
        Gradle client is started.

    .PARAMETER Killer
        Injection seam taking the process id. Left unset, the real client tree is killed.

    .PARAMETER Clock
        Injection seam returning the current [datetime]. Left unset, Get-Date is used. The contract
        suite drives the ceiling through this rather than by waiting for it.

    .PARAMETER Sampler
        Injection seam returning a JVM sample. Left unset, the live one is taken.

    .OUTPUTS
        ExitCode       - the client's, or the timeout code when the ceiling fired.
        Lines          - every line read from the run's stdout log.
        Heartbeats     - the lines printed while the run was silent, in order.
        ElapsedSeconds - wall clock of the run.
        TimedOut       - true when the ceiling ended it.
        LastTask       - the last task Gradle announced, which is where a timed-out run stopped.
    #>
    param(
        [Parameter(Mandatory)][string]$GradleWrapper,
        [AllowEmptyCollection()][string[]]$Arguments = @(),
        [Parameter(Mandatory)][string]$LogPath,
        [int]$QuietSeconds = 60,
        [int]$TimeoutMinutes = 45,
        [double]$PollSeconds = 1,
        [scriptblock]$Launcher,
        [scriptblock]$Killer,
        [scriptblock]$Clock,
        [scriptblock]$Sampler
    )

    if (-not $Clock) { $Clock = { Get-Date } }
    if (-not $Sampler) { $Sampler = { Get-GradleJvmSample } }
    if (-not $Killer) { $Killer = { param([int]$Id) Stop-GradleClientProcess -ProcessId $Id } }

    if ($Launcher) {
        # A caller supplying its own launcher owns the log file too - creating or truncating it here
        # would destroy the transcript that caller is feeding in.
        $process = & $Launcher
    } else {
        $errorLogPath = "$LogPath.err"
        foreach ($path in @($LogPath, $errorLogPath)) {
            $directory = Split-Path -Parent $path
            if ($directory -and -not (Test-Path -LiteralPath $directory)) {
                New-Item -ItemType Directory -Path $directory -Force | Out-Null
            }
            Set-Content -LiteralPath $path -Value '' -NoNewline
        }
        $process = Start-GradleClientProcess -GradleWrapper $GradleWrapper -Arguments $Arguments `
            -LogPath $LogPath -ErrorLogPath $errorLogPath
    }

    $startedAt = & $Clock
    $lastOutputAt = $startedAt
    $lastHeartbeatAt = $startedAt
    $lines = New-Object System.Collections.Generic.List[string]
    $heartbeats = New-Object System.Collections.Generic.List[string]
    $lastTask = $null
    # Primed at launch, not left empty: with no baseline the FIRST heartbeat can only report +0s for
    # every JVM, which reads as "nothing is happening" - the exact misreading this watcher exists to
    # prevent. Measured 2026-09-18 on the first real run, which printed `+0s cpu` for both daemons
    # while compileStandardDebugKotlin was burning a core.
    $previousSample = & $Sampler
    $timedOut = $false

    # FileShare::ReadWrite is mandatory - the writing process holds the same file, and a plain open
    # would fail on Windows for the whole run.
    $stream = [System.IO.FileStream]::new(
        $LogPath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete)
    $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)

    try {
        while ($true) {
            $sawOutput = $false
            while ($null -ne ($line = $reader.ReadLine())) {
                $sawOutput = $true
                $lines.Add($line)
                Write-Host $line
                $task = Get-GradleTaskFromLine -Line $line
                if ($task) { $lastTask = $task }
            }

            $now = & $Clock
            if ($sawOutput) {
                $lastOutputAt = $now
                $lastHeartbeatAt = $now
            }

            if ($process.HasExited) { break }

            if (($now - $startedAt).TotalMinutes -ge $TimeoutMinutes) {
                $timedOut = $true
                break
            }

            $silentSeconds = [int]($now - $lastOutputAt).TotalSeconds
            if ($silentSeconds -ge $QuietSeconds -and ($now - $lastHeartbeatAt).TotalSeconds -ge $QuietSeconds) {
                $currentSample = & $Sampler
                $heartbeat = Format-GradleHeartbeat -LastTask $lastTask -SilentSeconds $silentSeconds `
                    -ElapsedSeconds ([int]($now - $startedAt).TotalSeconds) `
                    -Current $currentSample -Previous $previousSample
                $heartbeats.Add($heartbeat)
                Write-Host $heartbeat -ForegroundColor DarkGray
                $previousSample = $currentSample
                $lastHeartbeatAt = $now
            }

            Start-Sleep -Seconds $PollSeconds
        }

        if ($timedOut) {
            Write-Host ("Gradle passed the ${TimeoutMinutes}-minute ceiling inside $lastTask and was " +
                'stopped. The Gradle daemon is shared and was left alone; it cancels the build on ' +
                "its own. Full output: $LogPath") -ForegroundColor Red
            # Out-Null, because whatever the killer writes is its own business - letting it reach the
            # pipeline would return an array here instead of the verdict object.
            & $Killer $process.Id | Out-Null
        } else {
            # A process that has exited may still have output buffered behind the reader.
            while ($null -ne ($line = $reader.ReadLine())) {
                $lines.Add($line)
                Write-Host $line
            }
        }
    }
    finally {
        $reader.Dispose()
        $stream.Dispose()
    }

    $exitCode = if ($timedOut) { $script:GradleProgressTimeoutExitCode } else { [int]$process.ExitCode }

    return [pscustomobject]@{
        ExitCode       = $exitCode
        Lines          = @($lines)
        Heartbeats     = @($heartbeats)
        ElapsedSeconds = [int]((& $Clock) - $startedAt).TotalSeconds
        TimedOut       = $timedOut
        LastTask       = $lastTask
    }
}
