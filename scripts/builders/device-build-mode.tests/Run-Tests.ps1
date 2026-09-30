# Run-Tests.ps1 (S3510) - regression suite for scripts/builders/device-build-mode.ps1.
#
# The device builder rebuilds from scratch only when the Hilt graph or the build itself changed, and
# otherwise trusts an incremental build. Both halves of that trade are asserted: every change that
# can stale the Hilt component must choose Full, and an ordinary body edit must choose Incremental -
# a decider that always says Full would pass the first half and save nothing.
#
# Hermetic: a sandbox tree under $env:TEMP stands in for the project, and the launch poller reads
# scripted logcat instead of a device.
#
# Usage:  pwsh -NoProfile -File scripts/builders/device-build-mode.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/device-build-mode.ps1')

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

$sandbox = Join-Path $env:TEMP "device-build-mode.tests.$PID"

function Write-SandboxFile([string]$rel, [string]$text) {
    $path = Join-Path $sandbox $rel
    $parent = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    Set-Content -LiteralPath $path -Value $text -Encoding utf8
    # Two writes inside one timer tick would keep the old write time and look unchanged; a moved
    # timestamp is what a real edit produces, so the suite forces one.
    (Get-Item -LiteralPath $path).LastWriteTimeUtc = [DateTime]::UtcNow.AddSeconds((Get-Random -Minimum 1 -Maximum 100000))
}

function Get-Baseline {
    $snap = Get-DeviceBuildSourceSnapshot -ProjectRoot $sandbox -Previous $null
    $path = Get-DeviceBuildStatePath -ProjectRoot $sandbox
    Save-DeviceBuildState -Path $path -Snapshot $snap
    return (Read-DeviceBuildState -Path $path)
}

function Get-ModeAfter([scriptblock]$change) {
    $baseline = Get-Baseline
    & $change
    $current = Get-DeviceBuildSourceSnapshot -ProjectRoot $sandbox -Previous $baseline
    return (Get-DeviceBuildMode -Previous $baseline -Current $current)
}

$viewModel = @'
package com.sza.fastmediasorter.ui.demo

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DemoViewModel @Inject constructor(
    private val repo: DemoRepository,
) : ViewModel() {
    fun load() = repo.load()
}
'@

$plain = @'
package com.sza.fastmediasorter.util

object Plain {
    fun twice(x: Int) = x * 2
}
'@

try {
    Write-SandboxFile 'build.gradle.kts' 'plugins {}'
    Write-SandboxFile 'settings.gradle.kts' 'include(":app_v2")'
    Write-SandboxFile 'app_v2/build.gradle.kts' 'android {}'
    Write-SandboxFile 'gradle/libs.versions.toml' '[versions]'
    Write-SandboxFile 'app_v2/src/main/java/demo/DemoViewModel.kt' $viewModel
    Write-SandboxFile 'app_v2/src/main/java/demo/Plain.kt' $plain
    Write-SandboxFile 'app_v2/src/test/java/demo/DemoTestModule.kt' "@Module`nobject DemoTestModule"

    # --- the mode decision ------------------------------------------------------------------------
    $snap = Get-DeviceBuildSourceSnapshot -ProjectRoot $sandbox -Previous $null
    $first = Get-DeviceBuildMode -Previous $null -Current $snap
    Assert-That 'no snapshot chooses Full' ($first.Mode -eq 'Full') "mode=$($first.Mode)"

    Assert-That 'test source sets stay out of the snapshot' `
    (-not $snap['sources'].Contains('app_v2/src/test/java/demo/DemoTestModule.kt')) 'test source was snapshotted'

    $same = Get-ModeAfter { }
    Assert-That 'an untouched tree chooses Incremental' ($same.Mode -eq 'Incremental') "reasons=$($same.Reasons -join '; ')"

    $body = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/main/java/demo/DemoViewModel.kt' ($viewModel -replace 'repo\.load\(\)', 'repo.load().also { }')
    }
    Assert-That 'a body edit in a Hilt file chooses Incremental' ($body.Mode -eq 'Incremental') "reasons=$($body.Reasons -join '; ')"

    $plainEdit = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/main/java/demo/Plain.kt' ($plain -replace 'x \* 2', 'x + x')
    }
    Assert-That 'an edit in a file with no marker chooses Incremental' ($plainEdit.Mode -eq 'Incremental') "reasons=$($plainEdit.Reasons -join '; ')"

    $marker = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/main/java/demo/Plain.kt' ($plain -replace 'object Plain', "@Module`n@InstallIn(SingletonComponent::class)`nobject Plain")
    }
    Assert-That 'a marker added to an existing file chooses Full' `
    ($marker.Mode -eq 'Full' -and ($marker.Reasons -join ' ') -match 'Plain\.kt') "reasons=$($marker.Reasons -join '; ')"
    Write-SandboxFile 'app_v2/src/main/java/demo/Plain.kt' $plain

    $newFile = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/standard/java/demo/DemoActivity.kt' "package demo`n@AndroidEntryPoint`nclass DemoActivity : BaseActivity()"
    }
    Assert-That 'a new entry point in a flavor source set chooses Full' ($newFile.Mode -eq 'Full') "reasons=$($newFile.Reasons -join '; ')"

    $removed = Get-ModeAfter {
        Remove-Item -LiteralPath (Join-Path $sandbox 'app_v2/src/standard/java/demo/DemoActivity.kt')
    }
    Assert-That 'a removed Hilt file chooses Full' `
    ($removed.Mode -eq 'Full' -and ($removed.Reasons -join ' ') -match 'DemoActivity\.kt') "reasons=$($removed.Reasons -join '; ')"

    $newPlain = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/main/java/demo/Other.kt' 'object Other'
    }
    Assert-That 'a new file with no marker chooses Incremental' ($newPlain.Mode -eq 'Incremental') "reasons=$($newPlain.Reasons -join '; ')"

    $renamed = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/main/java/demo/DemoViewModel.kt' ($viewModel -replace 'class DemoViewModel', 'class RenamedViewModel')
    }
    Assert-That 'a renamed class in a Hilt file chooses Full' ($renamed.Mode -eq 'Full') "reasons=$($renamed.Reasons -join '; ')"
    Write-SandboxFile 'app_v2/src/main/java/demo/DemoViewModel.kt' $viewModel

    $moved = Get-ModeAfter {
        Write-SandboxFile 'app_v2/src/main/java/demo/DemoViewModel.kt' ($viewModel -replace 'ui\.demo', 'ui.moved')
    }
    Assert-That 'a moved package in a Hilt file chooses Full' ($moved.Mode -eq 'Full') "reasons=$($moved.Reasons -join '; ')"
    Write-SandboxFile 'app_v2/src/main/java/demo/DemoViewModel.kt' $viewModel

    $gradle = Get-ModeAfter {
        Write-SandboxFile 'gradle/libs.versions.toml' "[versions]`nhilt = `"2.57`""
    }
    Assert-That 'a dependency catalog edit chooses Full' `
    ($gradle.Mode -eq 'Full' -and ($gradle.Reasons -join ' ') -match 'libs\.versions\.toml') "reasons=$($gradle.Reasons -join '; ')"

    $moduleScript = Get-ModeAfter {
        Write-SandboxFile 'app_v2/build.gradle.kts' 'android { namespace = "x" }'
    }
    Assert-That 'a module build script edit chooses Full' ($moduleScript.Mode -eq 'Full') "reasons=$($moduleScript.Reasons -join '; ')"

    $baseline = Get-Baseline
    $baseline['schema'] = 0
    $current = Get-DeviceBuildSourceSnapshot -ProjectRoot $sandbox -Previous $baseline
    $schema = Get-DeviceBuildMode -Previous $baseline -Current $current
    Assert-That 'a snapshot of another schema chooses Full' ($schema.Mode -eq 'Full') "reasons=$($schema.Reasons -join '; ')"

    $baseline = Get-Baseline
    $current = Get-DeviceBuildSourceSnapshot -ProjectRoot $sandbox -Previous $baseline
    $forced = Get-DeviceBuildMode -Previous $baseline -Current $current -ForceFull
    Assert-That '-Full forces Full on an untouched tree' `
    ($forced.Mode -eq 'Full' -and ($forced.Reasons -join ' ') -match '-Full') "reasons=$($forced.Reasons -join '; ')"

    $statePath = Get-DeviceBuildStatePath -ProjectRoot $sandbox
    Set-Content -LiteralPath $statePath -Value '{ torn' -Encoding utf8
    $torn = Read-DeviceBuildState -Path $statePath 3>$null
    Assert-That 'an unreadable snapshot reads as none' ($null -eq $torn) 'a torn file was trusted'

    Assert-That 'the snapshot lives under app_v2/build so clean removes it' `
    ($statePath -match 'app_v2[\\/]build[\\/]fms-device-build-state\.json$') "path=$statePath"

    # --- the signature ------------------------------------------------------------------------------
    Assert-That 'a file with no marker has an empty signature' `
    ((Get-DeviceBuildHiltSignature -Lines @('package a', 'class B')) -eq '') 'non-empty'

    Assert-That 'a Hilt file has a signature' `
    ((Get-DeviceBuildHiltSignature -Lines @('package a', '@AndroidEntryPoint', 'class B')).Length -eq 32) 'empty'

    Assert-That '@InjectMocks is not a Hilt marker' `
    ((Get-DeviceBuildHiltSignature -Lines @('package a', '@InjectMocks lateinit var x: X')) -eq '') 'matched'

    # --- the launch crash ---------------------------------------------------------------------------
    # Verbatim from the S3094 crash report, 2026-09-13.
    $s3094 = @(
        'E AndroidRuntime: FATAL EXCEPTION: main',
        'E AndroidRuntime: Caused by: java.lang.ClassCastException: com.sza.fastmediasorter.DaggerFastMediaSorterApp_HiltComponents_SingletonC$ActivityCImpl cannot be cast to com.sza.fastmediasorter.ui.main.MainActivity_GeneratedInjector'
    )
    $unrelated = @(
        'E AndroidRuntime: FATAL EXCEPTION: main',
        'E AndroidRuntime: java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Integer'
    )
    Assert-That 'the S3094 crash is recognised' (Test-StaleHiltLaunchCrash -Lines $s3094) 'missed'
    Assert-That 'an unrelated ClassCastException is not' (-not (Test-StaleHiltLaunchCrash -Lines $unrelated)) 'matched'

    $pkg = 'com.sza.fastmediasorter.debug'
    $displayedLine = 'I ActivityTaskManager: Displayed com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity for user 0: +512ms'

    $crashOutcome = Wait-DeviceLaunchOutcome -ReadLog { $s3094 } -Package $pkg -TimeoutSeconds 0 -PollSeconds 0
    Assert-That 'the poller reports the crash' ($crashOutcome -eq 'StaleHiltCrash') "outcome=$crashOutcome"

    $okOutcome = Wait-DeviceLaunchOutcome -ReadLog { @('I zygote: start', $displayedLine) } -Package $pkg -TimeoutSeconds 0 -PollSeconds 0
    Assert-That 'the poller reports a displayed activity' ($okOutcome -eq 'Displayed') "outcome=$okOutcome"

    $script:reads = 0
    $late = Wait-DeviceLaunchOutcome -ReadLog {
        $script:reads++
        if ($script:reads -ge 3) { $s3094 } else { @('I zygote: start') }
    } -Package $pkg -TimeoutSeconds 5 -PollSeconds 0.01
    Assert-That 'the poller keeps reading until the crash arrives' ($late -eq 'StaleHiltCrash' -and $script:reads -eq 3) "outcome=$late reads=$script:reads"

    $otherApp = Wait-DeviceLaunchOutcome -ReadLog { @('I ActivityTaskManager: Displayed com.other.app/.Main for user 0: +100ms') } `
        -Package $pkg -TimeoutSeconds 0 -PollSeconds 0
    Assert-That 'another app being displayed is no signal' ($otherApp -eq 'NoSignal') "outcome=$otherApp"
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "passed: $script:pass   failed: $script:fail"
if ($script:fail -gt 0) { exit 1 }
exit 0
