#!/usr/bin/env powershell
param(
    [Parameter(Mandatory = $true)]
    [string]$CloudResourceName,

    [int]$WarmDelaySeconds = 10,

    [switch]$SkipWarmRun
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$flowPath = Join-Path $projectRoot "maestro\scan_benchmark.yml"
$tempDir = Join-Path $projectRoot "temp"

if (-not (Test-Path $flowPath)) {
    throw "Flow not found: $flowPath"
}

if (-not (Test-Path $tempDir)) {
    New-Item -Path $tempDir -ItemType Directory -Force | Out-Null
}

function Get-CommandOrThrow {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $cmd) {
        throw "Required command not found: $Name"
    }
    return $cmd.Source
}

function Parse-LogcatTimestamp {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Line
    )

    if ($Line -notmatch '^(?<md>\d{2}-\d{2})\s+(?<hms>\d{2}:\d{2}:\d{2}\.\d{3})') {
        return $null
    }

    $year = (Get-Date).Year
    $value = "$year-$($matches['md']) $($matches['hms'])"
    return [DateTime]::ParseExact($value, "yyyy-MM-dd HH:mm:ss.fff", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Measure-ScanRun {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RunLabel,
        [Parameter(Mandatory = $true)]
        [string]$MaestroPath,
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,
        [Parameter(Mandatory = $true)]
        [string]$FlowPath,
        [Parameter(Mandatory = $true)]
        [string]$ResourceName
    )

    Write-Host "[$RunLabel] Clearing logcat buffer..." -ForegroundColor Yellow
    & $AdbPath logcat -c | Out-Null

    Write-Host "[$RunLabel] Running Maestro flow..." -ForegroundColor Cyan
    & $MaestroPath test -e "CLOUD_RESOURCE_NAME=$ResourceName" $FlowPath
    $maestroExit = $LASTEXITCODE

    if ($maestroExit -ne 0) {
        return [ordered]@{
            run_label = $RunLabel
            success = $false
            error = "Maestro flow failed with exit code $maestroExit"
            scan_duration_s = $null
            file_count = $null
            api_pages_fetched = 0
            network_errors = 0
            t_start = $null
            t_end = $null
        }
    }

    Start-Sleep -Seconds 2

    $logLines = & $AdbPath logcat -d -v time

    $startLine = $logLines | Where-Object {
        $_ -match "BrowseLoadingManager: START loading - resource='"
    } | Select-Object -First 1

    $completeLine = $null
    $fileCount = $null

    foreach ($line in $logLines) {
        if ($line -match 'BrowseLoadingManager: COMPLETE -\s+(?<count>\d+)\s+files loaded and displayed') {
            $completeLine = $line
            $fileCount = [int]$matches['count']
            break
        }
    }

    $startTs = $null
    $endTs = $null
    $duration = $null

    if ($startLine) {
        $startTs = Parse-LogcatTimestamp -Line $startLine
    }
    if ($completeLine) {
        $endTs = Parse-LogcatTimestamp -Line $completeLine
    }
    if ($startTs -and $endTs) {
        $duration = [Math]::Round(($endTs - $startTs).TotalSeconds, 3)
    }

    $pageTokenCount = ($logLines | Where-Object {
        $_ -match 'pageToken|nextPageToken'
    }).Count

    $networkErrorCount = ($logLines | Where-Object {
        $_ -match 'CloudMediaScanner: listFiles failed|BrowseLoadingManager: ERROR|CloudOperationStrategy: listFiles failed'
    }).Count

    return [ordered]@{
        run_label = $RunLabel
        success = [bool]($startTs -and $endTs)
        error = if ($startTs -and $endTs) { $null } else { "Could not parse START/COMPLETE markers from logcat" }
        scan_duration_s = $duration
        file_count = $fileCount
        api_pages_fetched = $pageTokenCount
        network_errors = $networkErrorCount
        t_start = if ($startTs) { $startTs.ToString("o") } else { $null }
        t_end = if ($endTs) { $endTs.ToString("o") } else { $null }
    }
}

$maestroPath = (Get-Command "maestro" -ErrorAction SilentlyContinue)?.Source
if (-not $maestroPath) {
    $maestroPath = (Get-Command "maestro-cli" -ErrorAction SilentlyContinue)?.Source
}
if (-not $maestroPath) {
    throw "Required command not found: maestro (or maestro-cli)"
}

$adbPath = Get-CommandOrThrow -Name "adb"

$timestamp = Get-Date
$datePart = $timestamp.ToString("yyyy-MM-dd_HHmmss")
$outputFile = Join-Path $tempDir "scan_benchmark_$datePart.json"

Write-Host "Running cloud scan benchmark for resource '$CloudResourceName'" -ForegroundColor Green

$coldRun = Measure-ScanRun -RunLabel "cold" -MaestroPath $maestroPath -AdbPath $adbPath -FlowPath $flowPath -ResourceName $CloudResourceName

$runs = @($coldRun)

if (-not $SkipWarmRun) {
    Write-Host "Waiting $WarmDelaySeconds seconds before warm run..." -ForegroundColor Yellow
    Start-Sleep -Seconds $WarmDelaySeconds
    $warmRun = Measure-ScanRun -RunLabel "warm" -MaestroPath $maestroPath -AdbPath $adbPath -FlowPath $flowPath -ResourceName $CloudResourceName
    $runs += $warmRun
}

$summary = [ordered]@{
    generated_at = $timestamp.ToString("o")
    resource_name = $CloudResourceName
    flow = "maestro/scan_benchmark.yml"
    device = ((& $adbPath devices) -join "`n")
    results = $runs
}

$summary | ConvertTo-Json -Depth 6 | Set-Content -Path $outputFile -Encoding UTF8

Write-Host "Benchmark JSON saved: $outputFile" -ForegroundColor Green

$outputFile