<#
.SYNOPSIS
    Cold-start timing for the wear app on a watch (S3368).
.DESCRIPTION
    Forces the app to stop, launches it N times, and reads the cold-start phase markers from the
    device logcat: fork -> application entrypoint -> host surface -> first frame -> first
    composition, plus SLF4J-banner / Davey / skipped-frame flags. Prints a per-run table and the
    per-phase mean and spread; keeps the raw logcat capture of every run under -OutDir.
    Device work goes through scripts/devtest/adb.ps1 (stop / launch / shell verbs).
    -SystemOnly measures a release build, which logs none of the app markers: each run reports only
    the system's own Displayed duration.
Manual tool: S3368 measurement helper - invoked by hand against a watch to take before/after
    cold-start baselines; it is not wired into any pipeline or closure gate.

# Exit codes:
#   0 - every run carried the full marker set
#   1 - at least one run missed a marker (details printed)
#   2 - the device named by -DeviceId was not online
#   3 - usage error (-DeviceId missing)
#>

param(
    [string]$DeviceId = '',
    [int]$Runs = 3,
    [string]$Package = 'com.sza.fastmediasorter.debug',
    [string]$OutDir = 'temp/S3368',
    [switch]$SystemOnly
)

$ErrorActionPreference = 'Stop'

if (-not $DeviceId) {
    Write-Error 'usage: pwsh -NoProfile -File scripts/utils/wear-cold-start-measure.ps1 -DeviceId <serial> [-Runs 3] [-Package <id>] [-OutDir temp/S3368]' -ErrorAction Continue
    exit 3
}
if ($Runs -lt 1) { $Runs = 1 }

$adbScript = Join-Path $PSScriptRoot '..\devtest\adb.ps1'

function Invoke-AdbVerb {
    param([string]$Verb, [string[]]$Extra = @())
    $out = & pwsh -NoProfile -File $adbScript $Verb -DeviceId $DeviceId @Extra 2>&1
    return ($out | Out-String)
}

# The watch must be in the online list before any device work.
$deviceList = Invoke-AdbVerb 'devices'
if ($deviceList -notmatch [regex]::Escape($DeviceId)) {
    Write-Error "device '$DeviceId' is not online - run: scripts/devtest/adb.ps1 devices" -ErrorAction Continue
    exit 2
}

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }

# Marker regexes over logcat -v threadtime. 'displayed' carries the system-computed launch time.
$markers = [ordered]@{
    fork           = 'Late-enabling -Xcheck:jni'
    appStarted     = 'FastMediaSorter Wear OS app started'
    surfaceCreated = 'MainActivity created'
    homeComposed   = 'HomeScreen composing'
}
# The seconds part is absent below one second ("+687ms"), which a release build usually is.
$displayedRegex = 'Displayed ' + [regex]::Escape($Package) + '.*\+(?:(\d+)s)?(\d+)ms'
$timestampRegex = '^(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d\d\d)'
$slf4jRegex = 'SLF4J:'
$daveyRegex = 'Davey!'
$skippedRegex = 'Skipped (\d+) frames'

function Get-LineTimeMs {
    param([string]$Line)
    if ($Line -match $timestampRegex) {
        $t = [datetime]::ParseExact($Matches[2], 'HH:mm:ss.fff', $null)
        return ([int]($t.TimeOfDay.TotalMilliseconds))
    }
    return $null
}

function Find-LastMarkerMs {
    param([string[]]$Lines, [string]$Pattern, [double]$NotAfterMs, [double]$NotBeforeMs)
    $best = $null
    foreach ($line in $Lines) {
        if ($line -notmatch $Pattern) { continue }
        $t = Get-LineTimeMs $line
        if ($null -eq $t) { continue }
        if ($t -gt $NotAfterMs -or $t -lt $NotBeforeMs) { continue }
        $best = $t
    }
    return $best
}

function ConvertTo-PhaseRow {
    param([hashtable]$Found)
    # All phases are wall-clock deltas between log markers; the system's own "+XsYms" duration
    # travels beside them as systemDisplayed (it measures from activity start, not process fork).
    $row = [ordered]@{}
    $row.forkToApp = [math]::Round($Found.appStarted - $Found.fork)
    $row.appToSurface = [math]::Round($Found.surfaceCreated - $Found.appStarted)
    $row.surfaceToFrame = [math]::Round($Found.displayedWall - $Found.surfaceCreated)
    $row.frameToHome = [math]::Round($Found.homeComposed - $Found.displayedWall)
    $row.forkToFrameTotal = [math]::Round($Found.displayedWall - $Found.fork)
    $row.forkToHomeTotal = [math]::Round($Found.homeComposed - $Found.fork)
    $row.systemDisplayed = [math]::Round($Found.displayedDuration)
    return $row
}

$allRows = @()
$missingMarker = $false
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

for ($i = 1; $i -le $Runs; $i++) {
    [void](Invoke-AdbVerb 'stop' @('-Package', $Package))
    Start-Sleep -Seconds 2
    # S3368: empty the buffer so the poll below can only match THIS run's Displayed line - without
    # this, a poll that fires before the new launch finishes re-reads the previous run's markers.
    [void](Invoke-AdbVerb 'logcat-clear')
    [void](Invoke-AdbVerb 'launch' @('-Package', $Package))

    # Poll until the system reports the first frame, bounded at ~40 s.
    $displayedLine = $null
    for ($poll = 0; $poll -lt 20; $poll++) {
        Start-Sleep -Milliseconds 2000
        $tail = (Invoke-AdbVerb 'shell' @('-Cmd', 'logcat -d -v threadtime -t 900')) -split "`r?`n"
        $hit = $tail | Where-Object { $_ -match $displayedRegex } | Select-Object -Last 1
        if ($hit) { $displayedLine = $hit; break }
    }
    if (-not $displayedLine) {
        Write-Error "run ${i}: no 'Displayed' line appeared within the wait window" -ErrorAction Continue
        $missingMarker = $true
        continue
    }

    # Full capture for the archive, tail for the marker extraction.
    $capturePath = Join-Path $OutDir "wear-cold-start-$stamp-run$i.log"
    (Invoke-AdbVerb 'shell' @('-Cmd', 'logcat -d -v threadtime')) | Set-Content -Path $capturePath -Encoding UTF8
    $lines = (Get-Content $capturePath)

    $displayedMs = $null
    if ($displayedLine -match $displayedRegex) {
        $seconds = if ($Matches[1]) { [double]($Matches[1]) } else { 0 }
        $displayedMs = $seconds * 1000 + [double]($Matches[2])
    }

    if ($SystemOnly) {
        $allRows += [ordered]@{ run = $i; systemDisplayed = [math]::Round($displayedMs) }
        Write-Host ("run {0}: system +{1} ms" -f $i, [math]::Round($displayedMs))
        continue
    }

    # The system Displayed timestamp anchors the window; every marker must sit between the fork
    # that produced this launch and the frame itself, so stale markers of older launches drop out.
    $displayedLineMs = Get-LineTimeMs $displayedLine
    $forkMs = Find-LastMarkerMs $lines $markers.fork $displayedLineMs (-10000)
    if ($null -eq $forkMs) { $forkMs = $displayedLineMs - 6000 }

    $found = @{ displayedDuration = $displayedMs; displayedWall = $displayedLineMs; fork = $forkMs }
    $runMissing = $false
    foreach ($name in @('appStarted', 'surfaceCreated', 'homeComposed')) {
        # HomeScreen composes ~0.6-0.7 s AFTER the first frame, so the upper bound must reach past it.
        $t = Find-LastMarkerMs $lines $markers[$name] ($displayedLineMs + 5000) $forkMs
        if ($null -eq $t) { $runMissing = $true; $missingMarker = $true }
        $found[$name] = $t
    }
    if ($runMissing) {
        Write-Error "run ${i}: marker(s) missing between fork and first frame - see $capturePath" -ErrorAction Continue
        continue
    }

    $window = $lines | Where-Object {
        $t = Get-LineTimeMs $_
        ($null -ne $t) -and ($t -ge $forkMs) -and ($t -le ($displayedLineMs + 2500))
    }
    $slf4j = [bool]($window | Where-Object { $_ -match $slf4jRegex })
    $davey = @($window | Where-Object { $_ -match $daveyRegex }).Count
    $skipped = 0
    foreach ($w in $window) { if ($w -match $skippedRegex) { $skipped = [math]::Max($skipped, [int]($Matches[1])) } }

    $row = ConvertTo-PhaseRow $found
    $row | Add-Member -NotePropertyName run -NotePropertyValue $i
    $row | Add-Member -NotePropertyName slf4jBanner -NotePropertyValue $slf4j
    $row | Add-Member -NotePropertyName daveyCount -NotePropertyValue $davey
    $row | Add-Member -NotePropertyName maxSkipped -NotePropertyValue $skipped
    $allRows += $row

    Write-Host ("run {0}: fork->app {1} ms | app->surface {2} ms | surface->frame {3} ms | frame->home {4} ms | fork->frame {5} ms | fork->home {6} ms | system +{7} ms | SLF4J {8} | Davey {9} | skipped {10}" -f `
        $i, $row.forkToApp, $row.appToSurface, $row.surfaceToFrame, $row.frameToHome, $row.forkToFrameTotal, $row.forkToHomeTotal, $row.systemDisplayed, $slf4j, $davey, $skipped)
}

if ($allRows.Count -eq 0) {
    Write-Error 'no run produced a complete marker set - nothing to summarise' -ErrorAction Continue
    exit 1
}

Write-Host ''
Write-Host 'per-phase mean / spread over complete runs (ms):'
$phases = if ($SystemOnly) { @('systemDisplayed') } else { @('forkToApp', 'appToSurface', 'surfaceToFrame', 'frameToHome', 'forkToFrameTotal', 'forkToHomeTotal', 'systemDisplayed') }
foreach ($phase in $phases) {
    $values = @($allRows | ForEach-Object { $_[$phase] })
    $mean = [math]::Round(($values | Measure-Object -Average).Average)
    $spread = ($values | Measure-Object -Minimum -Maximum)
    Write-Host ("  {0,-18} mean {1,6}  spread {2,6} .. {3,6}" -f $phase, $mean, $spread.Minimum, $spread.Maximum)
}

if ($missingMarker) {
    Write-Error 'at least one run missed markers - exit 1 per the header contract' -ErrorAction Continue
    exit 1
}
exit 0
