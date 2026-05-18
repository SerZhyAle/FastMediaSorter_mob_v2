# Targeted recovery for KAPT stalls on standardDebug (and friends).
#
# Origin: PLAN/spec_gradle_kapt_stall_standarddebug.md (archived to temp/done/).
# Symptom: `:app_v2:kaptGenerateStubsStandardDebugKotlin` or `:app_v2:kaptStandardDebugKotlin`
# hangs for several minutes with no output during targeted validation commands such as
# `:app_v2:compileStandardDebugKotlin` or `:app_v2:testStandardDebugUnitTest`.
# This is a silent stall (no Gradle failure), so build-debug.PS1's failure-driven
# auto-retry path (cache-pack-error / kapt incrementalData lock) does not engage.
#
# What this script does:
#   1. Stops every running Gradle daemon for the project.
#   2. Removes only the volatile kapt / kotlin / executionHistory directories that
#      are responsible for incremental-mode wedges. It does NOT nuke `.gradle/` or
#      `app_v2/build/` - that is what `scripts/builders/clean-gradle-caches.ps1`
#      is for, and it costs a full rebuild.
#   3. Optionally re-runs a target Gradle task once with `--no-daemon` so the
#      retry runs in a fresh JVM with no leftover daemon state.
#
# Why `--no-daemon` on retry:
#   `gradle.properties` pins `kapt.use.worker.api=false`, which forces kapt to share
#   the Gradle daemon JVM. When that daemon's heap is fragmented or a Kotlin daemon
#   child has wedged on an annotation processor, the cheapest reset is a fresh JVM.
#
# Usage:
#   pwsh -File scripts/utils/recover-kapt-stall.ps1
#   pwsh -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"
#   pwsh -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:assembleStandardDebug" -SkipRetry

[CmdletBinding()]
param(
    [string]$Task,
    [switch]$SkipRetry
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"

if (-not (Test-Path -Path $gradleWrapper)) {
    Write-Host "Error: gradlew.bat not found at $gradleWrapper" -ForegroundColor Red
    exit 1
}

function Stop-GradleDaemons {
    Write-Host "Stopping Gradle daemons.." -ForegroundColor DarkGray
    & $gradleWrapper --stop | Out-Null
}

function Remove-PathIfExists {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $absolutePath = Join-Path $projectRoot $RelativePath
    if (Test-Path -Path $absolutePath) {
        Write-Host "Removing $RelativePath" -ForegroundColor DarkGray
        Remove-Item -Path $absolutePath -Recurse -Force -ErrorAction SilentlyContinue
        return $true
    }

    return $false
}

function Get-LatestGradleVersionDir {
    $gradleDir = Join-Path $projectRoot ".gradle"
    if (-not (Test-Path -Path $gradleDir)) { return $null }

    return Get-ChildItem -Path $gradleDir -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d+\.\d+(\.\d+)?$' } |
        Sort-Object Name -Descending |
        Select-Object -First 1
}

Write-Host "KAPT stall recovery starting.." -ForegroundColor Cyan

Stop-GradleDaemons

# Volatile dirs whose stale state is the usual cause of the silent stall.
# Order matters only insofar as we want feedback per item; each removal is independent.
$relativePathsToClean = @(
    "app_v2\build\tmp\kapt3",
    "app_v2\build\generated\source\kapt",
    "app_v2\build\generated\source\kaptKotlin",
    "app_v2\build\kotlin",
    "app_v2\build\tmp\kotlin-classes"
)

$removedAny = $false
foreach ($relPath in $relativePathsToClean) {
    if (Remove-PathIfExists -RelativePath $relPath) { $removedAny = $true }
}

$gradleVersionDir = Get-LatestGradleVersionDir
if ($null -ne $gradleVersionDir) {
    $executionHistoryRel = ".gradle\$($gradleVersionDir.Name)\executionHistory"
    if (Remove-PathIfExists -RelativePath $executionHistoryRel) { $removedAny = $true }
}

if (-not $removedAny) {
    Write-Host "No volatile kapt/kotlin/executionHistory directories were present." -ForegroundColor DarkGray
}

if ($SkipRetry -or [string]::IsNullOrWhiteSpace($Task)) {
    Write-Host "KAPT stall recovery complete. Re-run your build/test command manually." -ForegroundColor Green
    exit 0
}

Write-Host "Retrying target task once with --no-daemon: $Task" -ForegroundColor Cyan

# Chaquopy gate: noLegal flavor requires the plugin, every other flavor must turn it off
# so the Chaquopy beforeVariants block does not hide non-noLegal variants (see
# project_build_gotchas memory entry - S0174 / S0175 follow-up).
$chaquopyFlag = if ($Task -match '(?i)noLegal') { "-Pchaquopy.enabled=true" } else { "-Pchaquopy.enabled=false" }

& $gradleWrapper $Task $chaquopyFlag --no-daemon --no-build-cache

$retryExit = $LASTEXITCODE
if ($retryExit -ne 0) {
    Write-Host "Retry failed with exit code $retryExit." -ForegroundColor Red
    Write-Host "If the retry stalled again rather than failing, abort it and run:" -ForegroundColor Yellow
    Write-Host "    pwsh -File scripts/builders/clean-gradle-caches.ps1" -ForegroundColor Yellow
    Write-Host "Then retry the original command." -ForegroundColor Yellow
    exit $retryExit
}

Write-Host "Retry succeeded after KAPT stall recovery." -ForegroundColor Green
exit 0
