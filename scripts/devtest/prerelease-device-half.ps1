<#
.SYNOPSIS
  S4249 pre-release sweep - the phone's device half on several emulators at once.

.DESCRIPTION
  Replaces steps 1.1, 1.4, 2, 3 and 3.5 of /spec-prerelease when two or more emulators are
  available. One build, the shard emulators prepared and configured in parallel, then the Maestro
  suite split into shards that run concurrently, one per shard emulator, while a separate
  self-test emulator runs the Room migration tests (.\a.ps1 fam) followed by the device self-test
  (run-device-selftest.ps1). The results come back in the shapes step 4 already reads: one merged
  maestro_suite_<stamp>.json (maestro/merge-shard-results.ps1) and one run_<stamp>.log.

  Roles: shard i on -DeviceIds[i], the self-test on -SelfTestDeviceId. Every id must be a sweep
  emulator (^emulator-\d+$) and the self-test device must not be a shard device - the migration run
  and the self-test end with the app uninstalled (lib/device-half-plan.ps1).

  Everything else runs as concurrent lanes (lib/device-half-lanes.ps1), each step starting the moment
  the steps it requires have passed:
    - shard lane i: prerelease-prepare.ps1 (shard 1 with the build; every other shard waits for that
      build and installs it with -SkipBuild - one Gradle build, never one per emulator), then
      prerelease-configure.ps1, then adb logcat -v threadtime *:V into run_<stamp>_<serial>.log
      (cleared first), then maestro/run-tests.ps1 -ShardIndex i -ShardCount n -Json into
      maestro_shard<i>_<stamp>.json. No shard starts before the one clear of the per-user Maestro
      session directory, which waits for every configure: a shard never clears it itself, because
      it would delete a sibling's live session.
    - self-test lane: .\a.ps1 fam then run-device-selftest.ps1. It waits only for every prepare,
      since fam rebuilds the APK the -SkipBuild prepares install. The self-test device gets no
      prepare or configure: fam installs its own build and the self-test provisions the device.
  Then the captures stop, the shards are merged, the shard captures are concatenated in shard order
  into run_<stamp>.log - the self-test device is left out, its instrumented tests throw by design and
  the single-device sweep never captured them - and the stage timings go to stages_<stamp>.json.

  A failed step skips every step that requires it (recorded with no exit code, folded as "could not
  verify"); independent lanes run on. Each child is given its device explicitly: nothing relies on
  single-device auto-detect.

  Step 2's UI checklist (.claude/reference/spec-prerelease.md "UI checklist") cannot run inside this
  script: run -SetupOnly, apply it on each shard device through the Maestro MCP server, then run
  again with -SkipSetup and the same -DeviceIds.

  Exit codes:
    0 - every shard flow passed (advisory and flaky outcomes are the verdict's business), the
        migration run and the self-test passed
    1 - content failure: a flow failed (runner/merge 3 - an advisory flow included; whether it
        blocks is the verdict's decision, step 4), a migration test failed (fam 1) or a self-test
        failed (1)
    2 - could not verify: bad device roles, a setup stage failed, a runner or merge could not judge
        (runner 1/2/4/5, merge 2/4), the migration or self-test could not run, or a branch never ran

.PARAMETER DeviceIds
  Shard emulator serials, one shard per id (comma-separated string accepted).

.PARAMETER SelfTestDeviceId
  The emulator that runs the migration tests and the device self-test. Never a shard device.

.PARAMETER Suite
  maestro/run-tests.ps1 selection (default all).

.PARAMETER OutDir
  Directory for the merged result, the run log, the per-device captures, the child outputs and the
  stage timings (default temp/S0484, the sweep's evidence directory).

.PARAMETER Stamp
  File-name stamp shared by every artifact of this run (default yyyyMMdd_HHmmss now).

.PARAMETER SetupOnly
  Prepare and configure the shard devices, then exit (0 = every setup step passed, 2 = one did not).
  Nothing is judged and the self-test lane does not run.

.PARAMETER SkipSetup
  Skip prepare and configure: the shard devices were set up by an earlier -SetupOnly run. The
  self-test lane starts at once.

.PARAMETER Json
  Emit one JSON summary object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-device-half.ps1 -DeviceIds emulator-5554,emulator-5556 -SelfTestDeviceId emulator-5558
.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-device-half.ps1 -DeviceIds emulator-5554,emulator-5556 -SelfTestDeviceId emulator-5558 -SetupOnly
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$DeviceIds,
    [Parameter(Mandatory)][string]$SelfTestDeviceId,
    [string]$Suite = 'all',
    [string]$OutDir,
    [string]$Stamp,
    [switch]$SetupOnly,
    [switch]$SkipSetup,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/device-half-plan.ps1')
. (Join-Path $PSScriptRoot 'lib/device-half-lanes.ps1')

if (-not $OutDir) { $OutDir = Join-Path $RepoRoot 'temp/S0484' }
if (-not $Stamp) { $Stamp = (Get-Date).ToString('yyyyMMdd_HHmmss') }
$ChildDir = Join-Path $OutDir "device-half_$Stamp"
New-Item -ItemType Directory -Path $ChildDir -Force | Out-Null

$pwshExe = (Get-Process -Id $PID).Path
$stages = [System.Collections.Generic.List[object]]::new()
$timingsPath = Join-Path $OutDir "stages_$Stamp.json"

function Write-Info {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Get-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $c = Join-Path $root 'platform-tools\adb.exe'; if (Test-Path $c) { return $c } }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $known) { return $known }
    return $null
}

function Get-PwshArgs {
    param([string]$Script, [string[]]$Rest)
    return @('-NoProfile', '-File', (Join-Path $RepoRoot $Script)) + $Rest
}

function Invoke-HalfLanes {
    param([object[]]$Lanes)
    return Invoke-Lanes -Lanes $Lanes -ChildDir $ChildDir -WorkingDirectory $RepoRoot -Stages $stages -Quiet:$Json
}
function Clear-MaestroSessions {
    foreach ($root in @($env:USERPROFILE, $env:HOME) | Where-Object { $_ } | ForEach-Object { Join-Path $_ '.maestro' }) {
        $sessions = Join-Path $root 'sessions'
        if (Test-Path -Path $sessions -PathType Container) {
            Remove-Item -LiteralPath $sessions -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

$summary = [ordered]@{
    exitCode       = $null
    stamp          = $Stamp
    shards         = @()
    selfTestDevice = $SelfTestDeviceId
    mergedResult   = $null
    runLog         = $null
    timings        = $timingsPath
    mergeExit      = $null
    migrationExit  = $null
    selfTestExit   = $null
}

function Complete-Half {
    param([int]$Code, [string]$Reason)
    Write-StageTimings -Stages @($stages) -Path $timingsPath | Out-Null
    $summary.exitCode = $Code
    if ($Reason) { $summary['reason'] = $Reason }
    if ($Json) { $summary | ConvertTo-Json -Depth 5 -Compress }
    else {
        Write-Host ("DEVICE-HALF exit {0} - merged={1} log={2} timings={3} migration={4} selftest={5}{6}" -f `
            $Code, $summary.mergedResult, $summary.runLog, $timingsPath, $summary.migrationExit, $summary.selfTestExit,
            $(if ($Reason) { " - $Reason" } else { '' }))
    }
    exit $Code
}

# ---------- 1. roles ----------
try {
    $roles = Resolve-DeviceRoles -DeviceIds $DeviceIds -SelfTestDeviceId $SelfTestDeviceId
} catch {
    Complete-Half -Code 2 -Reason $_.Exception.Message
}
if ($SetupOnly -and $SkipSetup) { Complete-Half -Code 2 -Reason '-SetupOnly and -SkipSetup exclude each other' }
$summary.shards = @($roles.Shards | ForEach-Object { [ordered]@{ index = $_.Index; device = $_.DeviceId } })
Write-Info ("ROLES shards: {0} | self-test: {1}" -f (($roles.Shards | ForEach-Object { "$($_.Index)=$($_.DeviceId)" }) -join ' '), $roles.SelfTestDeviceId) 'Cyan'

# ---------- 2. the lanes ----------
# One call, so independent work overlaps instead of waiting at stage boundaries:
#   shard lane i : prepare (shard 1 builds, the rest wait for that build and -SkipBuild) -> configure
#                  -> start this device's capture -> its Maestro shard, which waits for the one
#                  session clear, which waits for every configure.
#   self-test    : the migration run, then the device self-test. It waits only for every prepare,
#                  because fam rebuilds the debug APK the -SkipBuild prepares are installing. The
#                  self-test device needs no prepare or configure of its own: fam installs its own
#                  build and run-device-selftest.ps1 provisions the device itself.
$adb = Get-Adb
if (-not $adb) { Complete-Half -Code 2 -Reason 'adb not found - no log capture, so no verdict could rest on this run' }

$shardDevices = @($roles.Shards | ForEach-Object { $_.DeviceId })
$firstDevice = $shardDevices[0]
$prepareNames = @($shardDevices | ForEach-Object { "prepare-$_" })
$configureNames = @($shardDevices | ForEach-Object { "configure-$_" })
$captures = [System.Collections.Generic.List[object]]::new()
$shardOut = @{}

function New-CaptureAction {
    param([string]$Device)
    $adbExe = $adb
    $out = $OutDir
    $child = $ChildDir
    $stampText = $Stamp
    $sink = $captures
    return {
        & $adbExe -s $Device logcat -c *> $null
        $capFile = Join-Path $out ("run_{0}_{1}.log" -f $stampText, $Device)
        $capErr = Join-Path $child ("logcat_{0}.err" -f $Device)
        $cap = Start-Process -FilePath $adbExe -ArgumentList @('-s', $Device, 'logcat', '-v', 'threadtime', '*:V') -NoNewWindow -PassThru `
            -RedirectStandardOutput $capFile -RedirectStandardError $capErr
        $sink.Add([pscustomobject]@{ Device = $Device; Process = $cap; File = $capFile })
        return 0
    }.GetNewClosure()
}

$lanes = @()
foreach ($shard in $roles.Shards) {
    $d = $shard.DeviceId
    $steps = @()
    if (-not $SkipSetup) {
        if ($d -eq $firstDevice) {
            $steps += [pscustomobject]@{ Name = "prepare-$d"; Device = $d
                Arguments = (Get-PwshArgs 'scripts/devtest/prerelease-prepare.ps1' @('-DeviceId', $d, '-Json')) }
        }
        else {
            $steps += [pscustomobject]@{ Name = "prepare-$d"; Device = $d; Requires = @("prepare-$firstDevice")
                Arguments = (Get-PwshArgs 'scripts/devtest/prerelease-prepare.ps1' @('-DeviceId', $d, '-SkipBuild', '-Json')) }
        }
        $steps += [pscustomobject]@{ Name = "configure-$d"; Device = $d; Requires = @("prepare-$d")
            Arguments = (Get-PwshArgs 'scripts/devtest/prerelease-configure.ps1' @('-DeviceId', $d, '-Json')) }
    }
    if (-not $SetupOnly) {
        if ($d -eq $firstDevice) {
            # A shard never clears the shared Maestro session directory (it would delete a sibling's
            # live session), so it is cleared once here, before any shard starts.
            $steps += [pscustomobject]@{ Name = 'sessions-clear'; Device = $d
                Requires = $(if ($SkipSetup) { $null } else { $configureNames })
                Action = { Clear-MaestroSessions; return 0 } }
        }
        $steps += [pscustomobject]@{ Name = "capture-$d"; Device = $d
            Requires = $(if ($SkipSetup) { $null } else { @("configure-$d") })
            Action = (New-CaptureAction -Device $d) }
        $resultFile = Join-Path $OutDir ("maestro_shard{0}_{1}.json" -f $shard.Index, $Stamp)
        $shardOut[$shard.Index] = $resultFile
        # The runner prints its -Json object on stdout; this wrapper writes it to the shard file so
        # the child's console stream and its result stay apart.
        $wrapper = "& '{0}' -Suite '{1}' -DeviceId '{2}' -ShardIndex {3} -ShardCount {4} -Json | Set-Content -LiteralPath '{5}' -Encoding utf8NoBOM; exit `$LASTEXITCODE" -f `
            (Join-Path $RepoRoot 'maestro/run-tests.ps1'), $Suite, $d, $shard.Index, $shard.Count, $resultFile
        $steps += [pscustomobject]@{ Name = "shard$($shard.Index)"; Device = $d; Requires = @('sessions-clear', "capture-$d")
            Arguments = @('-NoProfile', '-Command', $wrapper) }
    }
    $lanes += , $steps
}
if (-not $SetupOnly) {
    $selfTest = $roles.SelfTestDeviceId
    $lanes += , @(
        # FMS_LOCK_BLOCK: fam is gradle-backed; the build prepare may still hold Build.Phone, and a
        # busy domain must queue it, not refuse it.
        [pscustomobject]@{ Name = 'migration'; Device = $selfTest; Env = @{ FMS_LOCK_BLOCK = '1' }
            Requires = $(if ($SkipSetup) { $null } else { $prepareNames })
            Arguments = (Get-PwshArgs 'a.ps1' @('fam', '-DeviceId', $selfTest)) },
        [pscustomobject]@{ Name = 'selftest'; Device = $selfTest
            Arguments = (Get-PwshArgs 'scripts/devtest/run-device-selftest.ps1' @('-DeviceId', $selfTest)) }
    )
}

$laneStart = Get-Date
try {
    $codes = Invoke-HalfLanes -Lanes $lanes
}
finally {
    foreach ($c in $captures) {
        if (-not $c.Process.HasExited) { Stop-Process -Id $c.Process.Id -Force -ErrorAction SilentlyContinue }
    }
}
$stages.Add((New-StageRecord -Name 'device-half-total' -Device $null -Start $laneStart -End (Get-Date) -Exit $null))

$setupCodes = @()
if (-not $SkipSetup) {
    $setupCodes = @(@($prepareNames) + @($configureNames) | ForEach-Object { $codes[$_] })
}
if ($SetupOnly) {
    $setupCode = Get-HalfExitCode -SetupCodes $setupCodes -RunnerCodes @(0) -MergeCode 0 -MigrationCode 0 -SelfTestCode 0
    Complete-Half -Code $setupCode -Reason $(if ($setupCode -eq 0) { 'setup only - shard devices prepared and configured, nothing judged' } else { 'setup failed' })
}

# ---------- 3. merge, one run log ----------
$runnerCodes = @($roles.Shards | ForEach-Object { $codes["shard$($_.Index)"] })
$summary.migrationExit = $codes['migration']
$summary.selfTestExit = $codes['selftest']

$merged = Join-Path $OutDir "maestro_suite_$Stamp.json"
$mergeStart = Get-Date
$mergeInputs = @($roles.Shards | ForEach-Object { $shardOut[$_.Index] }) -join ','
& $pwshExe -NoProfile -File (Join-Path $RepoRoot 'maestro/merge-shard-results.ps1') -Inputs $mergeInputs -Output $merged -ExpectedCount $roles.Shards.Count *> (Join-Path $ChildDir 'merge.out')
$mergeCode = $LASTEXITCODE
$stages.Add((New-StageRecord -Name 'merge' -Device $null -Start $mergeStart -End (Get-Date) -Exit $mergeCode))
$summary.mergeExit = $mergeCode
if (Test-Path -LiteralPath $merged) { $summary.mergedResult = $merged }

# The run log step 4's verdict and log audit already read: the shard devices in shard order. The
# self-test device is left out on purpose - its instrumented tests throw by design, and the
# single-device sweep never captured them either. A pid shared by two shard devices can only add
# findings to the audit, never hide one.
$runLog = Join-Path $OutDir "run_$Stamp.log"
Set-Content -LiteralPath $runLog -Value $null -Encoding utf8NoBOM
foreach ($device in $shardDevices) {
    $capFile = Join-Path $OutDir ("run_{0}_{1}.log" -f $Stamp, $device)
    if (Test-Path -LiteralPath $capFile) {
        Get-Content -LiteralPath $capFile -ReadCount 5000 | Add-Content -LiteralPath $runLog -Encoding utf8NoBOM
    }
}
$summary.runLog = $runLog

$code = Get-HalfExitCode -SetupCodes $setupCodes -RunnerCodes $runnerCodes -MergeCode $mergeCode -MigrationCode $codes['migration'] -SelfTestCode $codes['selftest']
Complete-Half -Code $code
