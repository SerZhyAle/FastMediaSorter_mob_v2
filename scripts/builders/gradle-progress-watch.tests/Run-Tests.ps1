# Run-Tests.ps1 (S3290) - regression suite for scripts/builders/gradle-progress-watch.ps1.
#
# The defect being guarded: a device build that is working normally is indistinguishable from a dead
# one, because Gradle announces a task at its start and prints nothing until it ends. Two runs were
# abandoned on that silence, and the CPU total cited as proof of death belonged to a daemon that is
# idle outside the two Kotlin tasks. So three things get asserted:
#   * a heartbeat NAMES the running task and the silence, so the wait is readable,
#   * a JVM sample reports a CPU DELTA between two samples, never a lifetime total,
#   * the ceiling ends a run that never exits, and spares one that finishes inside it.
#
# Hermetic: no Gradle is invoked. Invoke-GradleWithProgress takes its launcher, killer and clock as
# script blocks exactly so the ceiling can be driven without waiting for it.
#
# Usage:  pwsh -NoProfile -File scripts/builders/gradle-progress-watch.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/gradle-progress-watch.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# A stand-in for System.Diagnostics.Process: the three members the watcher reads. HasExited is a
# scripted property so a case can say "exit on the Nth poll" without the watcher knowing.
function New-FakeProcess([int]$exitCode, [int]$pollsBeforeExit) {
    $state = [pscustomobject]@{ Id = 4242; ExitCode = $exitCode; Polls = 0; PollsBeforeExit = $pollsBeforeExit }
    $state | Add-Member -MemberType ScriptProperty -Name HasExited -Value {
        $this.Polls++
        return $this.Polls -gt $this.PollsBeforeExit
    }
    return $state
}

# --- task recognition ---------------------------------------------------------------------------
# The heartbeat is only useful if it can name the task, and only task lines announce one.
Assert-That 'a task line yields the task path' `
((Get-GradleTaskFromLine -Line '> Task :app_v2:dexBuilderStandardDebug') -eq ':app_v2:dexBuilderStandardDebug') `
"got '$(Get-GradleTaskFromLine -Line '> Task :app_v2:dexBuilderStandardDebug')'"

Assert-That 'an ordinary line yields no task' `
($null -eq (Get-GradleTaskFromLine -Line 'Note: Some input files use or override a deprecated API.')) `
'a non-task line was read as a task'

Assert-That 'an empty line yields no task' `
($null -eq (Get-GradleTaskFromLine -Line '')) 'an empty line was read as a task'

# --- JVM sampling -------------------------------------------------------------------------------
# The S3290 capture read a lifetime CPU total as a progress signal. The sample must therefore carry
# per-process values a caller can diff, and must drop processes too small to be a build JVM.
$snapshot = @(
    [pscustomobject]@{ Id = 30536; ProcessName = 'java'; CPU = 3554.4; WorkingSet64 = 6.4GB },
    [pscustomobject]@{ Id = 48576; ProcessName = 'java'; CPU = 4147.9; WorkingSet64 = 6.0GB },
    [pscustomobject]@{ Id = 41332; ProcessName = 'java'; CPU = 6.0; WorkingSet64 = 220MB }
)
$sample = Get-GradleJvmSample -Snapshot $snapshot

Assert-That 'the sample drops JVMs under the reporting threshold' `
($sample.Count -eq 2 -and -not $sample.ContainsKey(41332)) "keys=$($sample.Keys -join ',')"

Assert-That 'the sample rounds CPU to whole seconds' `
($sample[30536].CpuSeconds -eq 3554) "cpu=$($sample[30536].CpuSeconds)"

# --- heartbeat wording --------------------------------------------------------------------------
$laterSnapshot = @(
    [pscustomobject]@{ Id = 30536; ProcessName = 'java'; CPU = 3554.4; WorkingSet64 = 6.4GB },
    [pscustomobject]@{ Id = 48576; ProcessName = 'java'; CPU = 4265.2; WorkingSet64 = 6.0GB }
)
$later = Get-GradleJvmSample -Snapshot $laterSnapshot
$heartbeat = Format-GradleHeartbeat -LastTask ':app_v2:dexBuilderStandardDebug' -SilentSeconds 75 `
    -ElapsedSeconds 240 -Current $later -Previous $sample

Assert-That 'the heartbeat names the running task' `
($heartbeat -match ':app_v2:dexBuilderStandardDebug') "line='$heartbeat'"

Assert-That 'the heartbeat names the silence and the elapsed time' `
($heartbeat -match 'silent 75s' -and $heartbeat -match 'elapsed 240s') "line='$heartbeat'"

Assert-That 'the heartbeat reports a CPU delta, not a lifetime total' `
($heartbeat -match 'pid 48576 \+117s cpu' -and $heartbeat -notmatch '\+4265s') "line='$heartbeat'"

Assert-That 'an idle JVM is reported as zero rather than omitted' `
($heartbeat -match 'pid 30536 \+0s cpu') "line='$heartbeat'"

$firstHeartbeat = Format-GradleHeartbeat -LastTask $null -SilentSeconds 60 -ElapsedSeconds 60 `
    -Current $sample -Previous $null

Assert-That 'the first heartbeat has no deltas to show and says so with zeros' `
($firstHeartbeat -match '\+0s cpu' -and $firstHeartbeat -notmatch '\+3554s') "line='$firstHeartbeat'"

Assert-That 'a heartbeat before the first task line still reads as a sentence' `
($firstHeartbeat -match '<no task announced yet>') "line='$firstHeartbeat'"

# --- the run loop -------------------------------------------------------------------------------
$sandbox = Join-Path $env:TEMP "gradle-progress-watch.tests.$PID"
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null

try {
    # A run that finishes inside the ceiling: its exit code is passed straight through, its output is
    # returned, and the last task it announced is remembered.
    $logPath = Join-Path $sandbox 'finished.log'
    [System.IO.File]::WriteAllText($logPath, "> Task :app_v2:compileStandardDebugKotlin`r`nBUILD SUCCESSFUL in 4m 51s`r`n")
    $finished = New-FakeProcess -exitCode 0 -pollsBeforeExit 0
    $result = Invoke-GradleWithProgress -GradleWrapper 'gradlew.bat' -LogPath $logPath -PollSeconds 0 `
        -Launcher { $finished } -Clock { Get-Date } -Killer { param($id) throw "killed a run that finished: $id" }

    Assert-That 'a finished run passes its exit code through' `
    ($result.ExitCode -eq 0 -and -not $result.TimedOut) "exit=$($result.ExitCode) timedOut=$($result.TimedOut)"

    Assert-That 'a finished run returns the output it read' `
    (@($result.Lines) -contains 'BUILD SUCCESSFUL in 4m 51s') "lines=$(@($result.Lines).Count)"

    Assert-That 'a finished run remembers the last task it announced' `
    ($result.LastTask -eq ':app_v2:compileStandardDebugKotlin') "lastTask=$($result.LastTask)"

    # A red run is not a stall: its own exit code must reach the caller unchanged, never the ceiling's.
    $redLog = Join-Path $sandbox 'red.log'
    [System.IO.File]::WriteAllText($redLog, "> Task :app_v2:compileStandardDebugKotlin FAILED`r`nBUILD FAILED in 41s`r`n")
    $red = New-FakeProcess -exitCode 1 -pollsBeforeExit 0
    $redResult = Invoke-GradleWithProgress -GradleWrapper 'gradlew.bat' -LogPath $redLog -PollSeconds 0 `
        -Launcher { $red } -Clock { Get-Date } -Killer { param($id) throw "killed a failed run: $id" }

    Assert-That 'a failed run reports its own exit code, not the timeout code' `
    ($redResult.ExitCode -eq 1 -and -not $redResult.TimedOut) "exit=$($redResult.ExitCode)"

    # A run that never exits: the clock is advanced past the ceiling, so the case costs no waiting.
    $stuckLog = Join-Path $sandbox 'stuck.log'
    [System.IO.File]::WriteAllText($stuckLog, "> Task :app_v2:dexBuilderStandardDebug`r`n")
    $stuck = New-FakeProcess -exitCode 0 -pollsBeforeExit 999
    $script:ticks = 0
    $script:killedId = 0
    $clock = {
        $script:ticks++
        return ([datetime]'2026-09-18T20:00:00').AddMinutes($script:ticks * 10)
    }
    $stuckResult = Invoke-GradleWithProgress -GradleWrapper 'gradlew.bat' -LogPath $stuckLog -PollSeconds 0 `
        -TimeoutMinutes 45 -Launcher { $stuck } -Clock $clock -Killer { param($id) $script:killedId = $id }

    Assert-That 'a run that never exits is ended at the ceiling' `
    ($stuckResult.TimedOut -and $stuckResult.ExitCode -eq (Get-GradleProgressTimeoutExitCode)) `
    "timedOut=$($stuckResult.TimedOut) exit=$($stuckResult.ExitCode)"

    Assert-That 'the ceiling kills the client it was given' `
    ($script:killedId -eq 4242) "killedId=$script:killedId"

    Assert-That 'a timed-out run names the task it stopped inside' `
    ($stuckResult.LastTask -eq ':app_v2:dexBuilderStandardDebug') "lastTask=$($stuckResult.LastTask)"

    # The first heartbeat of the first real run printed +0s for both daemons while the Kotlin daemon
    # was burning a core, because nothing had been sampled yet - a line that reads as proof of death.
    # A baseline taken at launch is what makes the very first heartbeat carry a real delta.
    $silentLog = Join-Path $sandbox 'silent.log'
    [System.IO.File]::WriteAllText($silentLog, "> Task :app_v2:compileStandardDebugKotlin`r`n")
    $silent = New-FakeProcess -exitCode 0 -pollsBeforeExit 999
    $script:sampleIndex = 0
    $sampler = {
        $script:sampleIndex++
        return @{ 30536 = [pscustomobject]@{ Id = 30536; Name = 'java'; CpuSeconds = 3400 + (40 * $script:sampleIndex); WorkingSetMb = 6400 } }
    }
    $script:silentTicks = 0
    $silentClock = {
        $script:silentTicks++
        return ([datetime]'2026-09-18T20:00:00').AddMinutes($script:silentTicks * 10)
    }
    $silentResult = Invoke-GradleWithProgress -GradleWrapper 'gradlew.bat' -LogPath $silentLog -PollSeconds 0 `
        -TimeoutMinutes 25 -Launcher { $silent } -Clock $silentClock -Sampler $sampler -Killer { param($id) $id }

    Assert-That 'the run emits a heartbeat while it is silent' `
    (@($silentResult.Heartbeats).Count -ge 1) "heartbeats=$(@($silentResult.Heartbeats).Count)"

    Assert-That 'the first heartbeat already carries a CPU delta, not +0s' `
    (@($silentResult.Heartbeats)[0] -match 'pid 30536 \+40s cpu') "line='$(@($silentResult.Heartbeats)[0])'"
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

# --- the caller ---------------------------------------------------------------------------------
# The helper is worth nothing if the device builder still calls Gradle directly.
$deviceBuilder = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/builders/build-standard-device.ps1') -Raw

Assert-That 'the device builder runs Gradle through the watcher' `
($deviceBuilder -match 'Invoke-GradleWithProgress') 'build-standard-device.ps1 does not use the watcher'

Assert-That 'the device builder still passes the S3094 fresh-artifact args on a full rebuild' `
($deviceBuilder -match 'Get-FreshGeneratedArtifactBuildArgs' -and $deviceBuilder -match '\$baseGradleArgs \+ \$freshArtifactArgs') `
'the reuse-disabling flags were lost in the rewiring'

Assert-That 'the device builder documents the timeout exit code' `
($deviceBuilder -match '124') 'the EXIT CODES header does not list 124'

Write-Host ""
Write-Host "passed: $script:pass   failed: $script:fail"
if ($script:fail -gt 0) { exit 1 }
exit 0
