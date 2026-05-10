<#
.SYNOPSIS
    Updates multiple Android <string> resources from a JSON file.

.DESCRIPTION
    A batch wrapper around set-android-string.ps1. 
    Accepts a JSON file containing an array of string update requests.

.PARAMETER JsonPath
    Path to the JSON file with updates.

.PARAMETER DryRun
    Prints planned changes without writing files.

.EXAMPLE
    pwsh -File scripts/utils/batch-set-android-string.ps1 -JsonPath temp/updates.json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$JsonPath,

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue' # Continue on individual errors to process the rest of the batch

if (-not (Test-Path $JsonPath)) {
    Write-Error "JSON file not found: $JsonPath"
    exit 1
}

try {
    $rawJson = Get-Content -Raw -Path $JsonPath
    $updates = $rawJson | ConvertFrom-Json
}
catch {
    Write-Error "Failed to parse JSON: $_"
    exit 1
}

if ($updates.GetType().Name -ne 'Object[]' -and $updates.GetType().Name -ne 'RuntimeType') {
    # If JSON is a single object instead of array
    if ($updates.GetType().Name -eq 'PSCustomObject') {
        $updates = @($updates)
    } else {
        Write-Error "JSON must contain an array of update objects."
        exit 1
    }
}

$scriptDir = $PSScriptRoot
$workerScript = Join-Path $scriptDir "set-android-string.ps1"

$stats = @{
    Total   = $updates.Count
    Success = 0
    Failed  = 0
    Skipped = 0
}

Write-Host "Starting batch update of $($stats.Total) strings..." -ForegroundColor Cyan

foreach ($update in $updates) {
    if (-not $update.Key -or -not $update.Locale) {
        Write-Host "[skip] Invalid entry: missing Key or Locale" -ForegroundColor Yellow
        $stats.Skipped++
        continue
    }

    $module = if ($update.Module) { $update.Module } else { "app_v2" }
    $value = if ($null -ne $update.Value) { $update.Value } else { "" }
    
    $params = @{
        Module = $module
        Locale = $update.Locale
        Key    = $update.Key
        Value  = $value
    }

    if ($update.PSObject.Properties['ExpectedOldValue']) {
        $params["ExpectedOldValue"] = $update.ExpectedOldValue
    }
    
    if ($update.PSObject.Properties['CreateIfMissing'] -and $update.CreateIfMissing) {
        $params["CreateIfMissing"] = $true
    }

    if ($DryRun) {
        $params["DryRun"] = $true
    }

    try {
        & pwsh -File $workerScript @params
        if ($LASTEXITCODE -eq 0) {
            $stats.Success++
        } else {
            Write-Host "[error] Failed to update $($update.Key) ($($update.Locale))" -ForegroundColor Red
            $stats.Failed++
        }
    }
    catch {
        Write-Host "[error] Exception updating $($update.Key): $_" -ForegroundColor Red
        $stats.Failed++
    }
}

Write-Host ""
Write-Host "Batch Summary:" -ForegroundColor Cyan
Write-Host "  Total:   $($stats.Total)"
Write-Host "  Success: $($stats.Success)" -ForegroundColor Green
$failedColor = if ($stats.Failed -gt 0) { "Red" } else { "Gray" }
$skippedColor = if ($stats.Skipped -gt 0) { "Yellow" } else { "Gray" }

Write-Host "  Failed:  $($stats.Failed)" -ForegroundColor $failedColor
Write-Host "  Skipped: $($stats.Skipped)" -ForegroundColor $skippedColor

if ($stats.Failed -gt 0) {
    exit 1
}
exit 0
