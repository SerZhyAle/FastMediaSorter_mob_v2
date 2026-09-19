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
    pwsh -NoProfile -File scripts/utils/batch-set-android-string.ps1 -JsonPath temp/updates.json

.OUTPUTS
    Exit codes:
      0 - every entry was written, or was skipped as invalid.
      1 - unusable input (missing or unparseable JSON), or at least one entry failed to write. The
          batch always runs to the end first, so the summary names how many of each there were.
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

# S3305: every optional field is read through PSObject.Properties, never as $update.Name. Under
# Set-StrictMode -Version Latest a missing property on a PSCustomObject THROWS rather than yielding
# $null, so the documented minimal entry - Key, Locale, Value and nothing else - made this script
# fail on its own first line of work. Measured on a 199-entry batch: 199 failed, 0 written, each one
# reported as "The property 'Module' cannot be found on this object".
function Get-UpdateField {
    param([Parameter(Mandatory = $true)]$Update, [Parameter(Mandatory = $true)][string]$Name)

    if ($Update.PSObject.Properties[$Name]) { return $Update.PSObject.Properties[$Name].Value }
    return $null
}

foreach ($update in $updates) {
    $key = Get-UpdateField -Update $update -Name 'Key'
    $locale = Get-UpdateField -Update $update -Name 'Locale'
    if (-not $key -or -not $locale) {
        Write-Host "[skip] Invalid entry: missing Key or Locale" -ForegroundColor Yellow
        $stats.Skipped++
        continue
    }

    $module = Get-UpdateField -Update $update -Name 'Module'
    if (-not $module) { $module = "app_v2" }
    $value = Get-UpdateField -Update $update -Name 'Value'
    if ($null -eq $value) { $value = "" }

    $params = @{
        Module = $module
        Locale = $locale
        Key    = $key
        Value  = $value
    }

    # The worker defaults to strings.xml, so a key living in a thematic split file is reported as
    # missing unless the entry names its file.
    $file = Get-UpdateField -Update $update -Name 'File'
    if ($file) { $params["File"] = $file }

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
        & pwsh -NoProfile -File $workerScript @params
        if ($LASTEXITCODE -eq 0) {
            $stats.Success++
        } else {
            Write-Host "[error] Failed to update $key ($locale)" -ForegroundColor Red
            $stats.Failed++
        }
    }
    catch {
        Write-Host "[error] Exception updating $key ($locale): $_" -ForegroundColor Red
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
