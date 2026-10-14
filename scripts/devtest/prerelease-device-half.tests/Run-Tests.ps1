#requires -Version 7.0
<#
.SYNOPSIS
    S4249 contract suite for the phone device half's planning library: device roles, the exit-code
    fold and the stage-record shape.

# Subject: scripts/devtest/prerelease-device-half.ps1, scripts/devtest/lib/device-half-plan.ps1, scripts/devtest/lib/device-half-lanes.ps1

.DESCRIPTION
    Hermetic: dot-sources scripts/devtest/lib/device-half-plan.ps1 and, for the role refusals, runs
    scripts/devtest/prerelease-device-half.ps1 with ids that are refused before any device is
    touched. No adb call, no device. The fold cases pin strategic S4249 section 7: a branch that
    never ran is "could not verify", never a pass.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/devtest/lib/device-half-plan.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$lib = Join-Path $repoRoot 'scripts/devtest/lib/device-half-plan.ps1'
$half = Join-Path $repoRoot 'scripts/devtest/prerelease-device-half.ps1'
foreach ($required in @($lib, $half)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "FAIL | not found: $required" -ForegroundColor Red
        exit 1
    }
}
. $lib
. (Join-Path $repoRoot 'scripts/devtest/lib/device-half-lanes.ps1')

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

function Get-RoleError {
    param([string[]]$Shards, [string]$SelfTest)
    try { $null = Resolve-DeviceRoles -DeviceIds $Shards -SelfTestDeviceId $SelfTest; return '' }
    catch { return $_.Exception.Message }
}

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ('s4249-half-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null

try {
    # --- roles ----------------------------------------------------------------------------------
    $roles = Resolve-DeviceRoles -DeviceIds @('emulator-5554', 'emulator-5556') -SelfTestDeviceId 'emulator-5558'
    Assert-Equal -Label 'roles: distinct emulator ids are accepted, shard i on device i' `
        -Expected '1=emulator-5554/2=emulator-5556/self=emulator-5558' `
        -Actual ("{0}/self={1}" -f (($roles.Shards | ForEach-Object { "$($_.Index)=$($_.DeviceId)" }) -join '/'), $roles.SelfTestDeviceId)
    Assert-Equal -Label 'roles: a comma-separated shard list is split' `
        -Expected 2 -Actual @((Resolve-DeviceRoles -DeviceIds @('emulator-5554,emulator-5556') -SelfTestDeviceId 'emulator-5558').Shards).Count
    Assert-Equal -Label 'roles: a physical serial is rejected and named' `
        -Expected $true -Actual ((Get-RoleError @('RFCR110NBQJ') 'emulator-5558') -match 'RFCR110NBQJ.*not a sweep emulator')
    Assert-Equal -Label 'roles: a duplicate shard id is rejected' `
        -Expected $true -Actual ((Get-RoleError @('emulator-5554', 'emulator-5554') 'emulator-5558') -match 'listed twice')
    Assert-Equal -Label 'roles: a self-test device equal to a shard device is rejected' `
        -Expected $true -Actual ((Get-RoleError @('emulator-5554') 'emulator-5554') -match 'also a shard device')
    Assert-Equal -Label 'roles: a physical self-test device is rejected' `
        -Expected $true -Actual ((Get-RoleError @('emulator-5554') 'R5CY9070WNB') -match 'R5CY9070WNB')

    $null = & pwsh -NoProfile -File $half -DeviceIds 'RFCR110NBQJ' -SelfTestDeviceId 'emulator-5558' -OutDir $scratch -Json 2>&1
    Assert-Equal -Label 'orchestrator: a refused role exits 2 before touching a device' -Expected 2 -Actual $LASTEXITCODE
    Assert-Equal -Label 'orchestrator: a refused role still writes the timings file' `
        -Expected 1 -Actual @(Get-ChildItem -LiteralPath $scratch -Filter 'stages_*.json').Count

    # --- exit-code fold ---------------------------------------------------------------------------
    $fold = @(
        @{ L = 'all clean'; E = 0; A = @{ RunnerCodes = @(0, 0); MergeCode = 0; MigrationCode = 0; SelfTestCode = 0 } },
        @{ L = 'runner 3 + merge 3 is content'; E = 1; A = @{ RunnerCodes = @(3, 0); MergeCode = 3; MigrationCode = 0; SelfTestCode = 0 } },
        @{ L = 'runner 4 is could-not-verify'; E = 2; A = @{ RunnerCodes = @(4, 0); MergeCode = 4; MigrationCode = 0; SelfTestCode = 0 } },
        @{ L = 'runner 5 is could-not-verify'; E = 2; A = @{ RunnerCodes = @(5, 0); MergeCode = 0; MigrationCode = 0; SelfTestCode = 0 } },
        @{ L = 'merge 2 is could-not-verify'; E = 2; A = @{ RunnerCodes = @(0, 0); MergeCode = 2; MigrationCode = 0; SelfTestCode = 0 } },
        @{ L = 'migration 1 is content'; E = 1; A = @{ RunnerCodes = @(0); MergeCode = 0; MigrationCode = 1; SelfTestCode = 0 } },
        @{ L = 'self-test 1 is content'; E = 1; A = @{ RunnerCodes = @(0); MergeCode = 0; MigrationCode = 0; SelfTestCode = 1 } },
        @{ L = 'self-test 2 is could-not-verify'; E = 2; A = @{ RunnerCodes = @(0); MergeCode = 0; MigrationCode = 0; SelfTestCode = 2 } },
        @{ L = 'could-not-verify outranks content'; E = 2; A = @{ RunnerCodes = @(3); MergeCode = 3; MigrationCode = 0; SelfTestCode = 2 } },
        @{ L = 'a self-test that never ran is could-not-verify'; E = 2; A = @{ RunnerCodes = @(0); MergeCode = 0; MigrationCode = 0; SelfTestCode = $null } },
        @{ L = 'no runner at all is could-not-verify'; E = 2; A = @{ RunnerCodes = @(); MergeCode = 0; MigrationCode = 0; SelfTestCode = 0 } },
        @{ L = 'a failed setup stage is could-not-verify'; E = 2; A = @{ SetupCodes = @(0, 10); RunnerCodes = @(0); MergeCode = 0; MigrationCode = 0; SelfTestCode = 0 } }
    )
    foreach ($case in $fold) {
        $a = $case.A
        Assert-Equal -Label "fold: $($case.L)" -Expected $case.E -Actual (Get-HalfExitCode @a)
    }

    # --- lanes: real child processes, no device ---------------------------------------------------
    $laneStages = [System.Collections.Generic.List[object]]::new()
    $laneDir = Join-Path $scratch 'lanes'
    New-Item -ItemType Directory -Path $laneDir -Force | Out-Null
    $laneCodes = Invoke-Lanes -ChildDir $laneDir -WorkingDirectory $scratch -Stages $laneStages -Quiet -PollMilliseconds 200 -Lanes @(
        , @([pscustomobject]@{ Name = 'a1'; Device = 'emulator-5554'; Arguments = @('-NoProfile', '-Command', 'exit 0') },
            [pscustomobject]@{ Name = 'a2'; Device = 'emulator-5554'; Arguments = @('-NoProfile', '-Command', 'Write-Output "two words"; exit 3') }),
        , @([pscustomobject]@{ Name = 'b1'; Device = 'emulator-5556'; Env = @{ S4249_LANE_PROBE = 'x' }
                Arguments = @('-NoProfile', '-Command', 'if ($env:S4249_LANE_PROBE -eq "x") { exit 5 } else { exit 6 }') })
    )
    Assert-Equal -Label 'lanes: every step exit code is returned by name' -Expected '0/3/5' `
        -Actual ("{0}/{1}/{2}" -f $laneCodes['a1'], $laneCodes['a2'], $laneCodes['b1'])
    Assert-Equal -Label 'lanes: one stage record per step' -Expected 3 -Actual $laneStages.Count
    Assert-Equal -Label 'lanes: a step env var does not leak into the parent' -Expected '' -Actual "$env:S4249_LANE_PROBE"
    Assert-Equal -Label 'lanes: a quoted argument with spaces reaches the child whole' -Expected 'two words' `
        -Actual ((Get-Content -LiteralPath (Join-Path $laneDir 'a2_emulator-5554.out') -Raw).Trim())

    # Requires: a step waits for a step of another lane, and a failed requirement skips it.
    $depStages = [System.Collections.Generic.List[object]]::new()
    $script:actionRan = 0
    $depCodes = Invoke-Lanes -ChildDir $laneDir -WorkingDirectory $scratch -Stages $depStages -Quiet -PollMilliseconds 200 -Lanes @(
        , @([pscustomobject]@{ Name = 'build'; Device = 'emulator-5554'; Arguments = @('-NoProfile', '-Command', 'Start-Sleep -Milliseconds 600; exit 0') },
            [pscustomobject]@{ Name = 'breaks'; Device = 'emulator-5554'; Arguments = @('-NoProfile', '-Command', 'exit 1') }),
        , @([pscustomobject]@{ Name = 'after-build'; Device = 'emulator-5556'; Requires = @('build')
                Action = { $script:actionRan++; return 0 } },
            [pscustomobject]@{ Name = 'after-breaks'; Device = 'emulator-5556'; Requires = @('breaks')
                Arguments = @('-NoProfile', '-Command', 'exit 0') },
            [pscustomobject]@{ Name = 'after-skipped'; Device = 'emulator-5556'; Requires = @('after-breaks')
                Arguments = @('-NoProfile', '-Command', 'exit 0') }),
        , @([pscustomobject]@{ Name = 'orphan'; Device = 'emulator-5558'; Requires = @('no-such-step')
                Arguments = @('-NoProfile', '-Command', 'exit 0') })
    )
    Assert-Equal -Label 'lanes: an in-process action waits for its requirement and runs once' -Expected '0/1' `
        -Actual ("{0}/{1}" -f $depCodes['after-build'], $script:actionRan)
    Assert-Equal -Label 'lanes: a failed requirement skips the step with no exit code' -Expected 'True/True' `
        -Actual ("{0}/{1}" -f $depCodes.ContainsKey('after-breaks'), ($null -eq $depCodes['after-breaks']))
    Assert-Equal -Label 'lanes: a skip propagates to the steps that require it' -Expected 'True' `
        -Actual ($null -eq $depCodes['after-skipped'])
    Assert-Equal -Label 'lanes: a requirement no lane produces is skipped, not waited on forever' -Expected 'True/True' `
        -Actual ("{0}/{1}" -f $depCodes.ContainsKey('orphan'), ($null -eq $depCodes['orphan']))
    Assert-Equal -Label 'lanes: every step, skipped or run, has a stage record' -Expected 6 -Actual $depStages.Count

    # --- stage record ---------------------------------------------------------------------------
    $start = [datetime]'2026-10-14T10:00:00'
    $rec = New-StageRecord -Name 'shard1' -Device 'emulator-5554' -Start $start -End $start.AddSeconds(95.25) -Exit 0
    Assert-Equal -Label 'stage record: keys name, device, start, end, seconds, exit' `
        -Expected 'name,device,start,end,seconds,exit' -Actual (@($rec.Keys) -join ',')
    Assert-Equal -Label 'stage record: seconds rounded to one decimal' -Expected '95.2' -Actual ([string]$rec.seconds)
    $timings = Write-StageTimings -Stages @($rec) -Path (Join-Path $scratch 'stages_case.json')
    $back = @(Get-Content -LiteralPath $timings -Raw | ConvertFrom-Json)
    Assert-Equal -Label 'stage timings: one record written as a JSON array' -Expected 'shard1/emulator-5554/0' `
        -Actual ("{0}/{1}/{2}" -f $back[0].name, $back[0].device, $back[0].exit)
}
finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "prerelease-device-half.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
