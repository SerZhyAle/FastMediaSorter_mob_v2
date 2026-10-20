<#
.SYNOPSIS
  Pure planning functions of the parallel pre-release halves (S4249): device roles, stage
  timing records and the exit-code fold of the phone and watch halves.

.DESCRIPTION
  Dot-sourced by scripts/devtest/prerelease-device-half.ps1, scripts/devtest/prerelease-wear-half.ps1
  and their hermetic contract suites. Nothing here calls adb or starts a process; the one writer,
  Write-StageTimings, writes only the file it is given.

  The two decisions that must not drift are made here, where a suite can pin them without a device:
  which serial may take part (only emulators created for the sweep - strategic S4249 section 3.2,
  physical devices never), and how the children's exit codes fold into one verdict code, where a
  branch that did not run is "could not verify", never a pass (strategic section 7).

  Exit codes: none - a library defines functions and returns to its caller.
#>

$script:SweepEmulatorPattern = '^emulator-\d+$'

function Test-SweepEmulatorId {
    param([string]$Id)
    return ([string]$Id -match $script:SweepEmulatorPattern)
}

# Shard i runs on DeviceIds[i-1]. The self-test device is never a shard: the migration run and the
# self-test both end with the app uninstalled, which would pull the app out from under a flow.
function Resolve-DeviceRoles {
    param([string[]]$DeviceIds, [string]$SelfTestDeviceId)
    $ids = @($DeviceIds | ForEach-Object { ([string]$_) -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($ids.Count -lt 1) { throw 'at least one shard device id is required' }
    if (-not $SelfTestDeviceId) { throw 'a self-test device id is required' }
    $seen = @{}
    foreach ($id in @($ids + $SelfTestDeviceId)) {
        if (-not (Test-SweepEmulatorId $id)) {
            throw "device '$id' is not a sweep emulator (^emulator-\d+$); physical devices never take part (strategic S4249 section 3.2)"
        }
        if ($seen.ContainsKey($id)) {
            if ($id -eq $SelfTestDeviceId) { throw "self-test device '$id' is also a shard device; it must be a separate emulator" }
            throw "device '$id' is listed twice"
        }
        $seen[$id] = $true
    }
    $shards = @()
    for ($i = 0; $i -lt $ids.Count; $i++) {
        $shards += [pscustomobject]@{ Index = $i + 1; Count = $ids.Count; DeviceId = $ids[$i] }
    }
    return [pscustomobject]@{ Shards = $shards; SelfTestDeviceId = $SelfTestDeviceId; AllDeviceIds = @($ids + $SelfTestDeviceId) }
}

# ---------- stage timings ----------
function New-StageRecord {
    param([string]$Name, [string]$Device, [datetime]$Start, [datetime]$End, [Nullable[int]]$Exit)
    return [ordered]@{
        name    = $Name
        device  = $(if ($Device) { $Device } else { $null })
        start   = $Start.ToString('o')
        end     = $End.ToString('o')
        seconds = [Math]::Round(($End - $Start).TotalSeconds, 1)
        exit    = $Exit
    }
}

function Write-StageTimings {
    param([object[]]$Stages, [string]$Path)
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Set-Content -LiteralPath $Path -Encoding utf8NoBOM -Value (ConvertTo-Json -InputObject @($Stages) -Depth 4)
    return $Path
}

# ---------- exit-code folds ----------
# A $null code means that branch never ran. That is "could not verify" (2), never a pass: a branch
# that silently drops out of the verdict is the failure this fold exists to refuse.
#   Setup    - prepare/configure children: any non-zero is 2 (the stand was not established).
#   Runners  - maestro/run-tests.ps1 per shard: 3 is content, 0 is clean, anything else is 2.
#   Merge    - maestro/merge-shard-results.ps1: 3 content, 0 clean, 2 and 4 (and anything else) 2.
#   Migration- .\a.ps1 fam: 1 content, 0 clean, anything else 2.
#   SelfTest - run-device-selftest.ps1: 1 content, 0 clean, anything else 2.
function Get-HalfExitCode {
    param(
        [object[]]$SetupCodes = @(),
        [object[]]$RunnerCodes = @(),
        $MergeCode,
        $MigrationCode,
        $SelfTestCode
    )
    $couldNotVerify = $false
    $content = $false
    foreach ($c in @($SetupCodes)) { if ($null -eq $c -or [int]$c -ne 0) { $couldNotVerify = $true } }
    if (@($RunnerCodes).Count -eq 0) { $couldNotVerify = $true }
    foreach ($c in @($RunnerCodes)) {
        if ($null -eq $c) { $couldNotVerify = $true }
        elseif ([int]$c -eq 3) { $content = $true }
        elseif ([int]$c -ne 0) { $couldNotVerify = $true }
    }
    foreach ($pair in @(@($MergeCode, 3), @($MigrationCode, 1), @($SelfTestCode, 1))) {
        $code = $pair[0]
        $contentCode = $pair[1]
        if ($null -eq $code) { $couldNotVerify = $true }
        elseif ([int]$code -eq $contentCode) { $content = $true }
        elseif ([int]$code -ne 0) { $couldNotVerify = $true }
    }
    if ($couldNotVerify) { return 2 }
    if ($content) { return 1 }
    return 0
}

# The watch half: a list of stage results, each { kind; exit }, plus the parsed verdicts per shape.
#   kind 'gate'     - content gate / probe gate: 1 content, 0 clean, else 2.
#   kind 'setup'    - prepare / migration-free setup: non-zero is 2.
#   kind 'migration'- .\a.ps1 fwm: 1 content, 0 clean, else 2.
#   kind 'bench'    - wear-shape-bench -Assert: 1 content, 0 clean, else 2.
#   kind 'walk'     - wear-prerelease-walk: 1 content, 0 clean, else 2.
#   kind 'verdict'  - prerelease-verdict: 1 content (FAIL or BLOCKED), 0 clean, else 2.
# A verdict object that is missing, or whose blocked flag is set, also decides: missing is 2, blocked
# is 1 - a blocked watch verdict is not a release-ready one.
function Get-WearHalfExitCode {
    param([object[]]$Stages = @(), [object[]]$Verdicts = @(), [int]$ExpectedVerdicts = 2)
    $couldNotVerify = $false
    $content = $false
    if (@($Stages).Count -eq 0) { $couldNotVerify = $true }
    foreach ($s in @($Stages)) {
        $code = $s.exit
        if ($null -eq $code) { $couldNotVerify = $true; continue }
        $code = [int]$code
        if ($code -eq 0) { continue }
        if ($s.kind -eq 'setup') { $couldNotVerify = $true; continue }
        if ($code -eq 1) { $content = $true } else { $couldNotVerify = $true }
    }
    $present = @($Verdicts | Where-Object { $null -ne $_ })
    if ($present.Count -lt $ExpectedVerdicts) { $couldNotVerify = $true }
    foreach ($v in $present) {
        if ($v.blocked) { $content = $true }
        elseif (-not $v.pass) { $content = $true }
    }
    if ($couldNotVerify) { return 2 }
    if ($content) { return 1 }
    return 0
}
