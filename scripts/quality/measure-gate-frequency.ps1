#requires -Version 7.0
<#
.SYNOPSIS
    Reports actual quality-gate frequency from machine-readable gate telemetry.

.DESCRIPTION
    Aggregates records emitted by post-change.ps1 and assert-fast-gates.ps1. It refuses
    with exit 2 when no records exist, because an empty telemetry source is not evidence
    that every gate has zero invocations or zero failures.

.PARAMETER Filter
    Restrict the report to gate names matching this regular expression.

.PARAMETER Json
    Emit the report as JSON.

.PARAMETER Help
    Show help documentation and usage.
#>
[CmdletBinding()]
param(
    [string]$Filter = '',
    [switch]$Json,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$telemetryPath = Get-GateTelemetryPath
if (-not (Test-Path -LiteralPath $telemetryPath)) {
    $message = "Gate telemetry is unavailable: $telemetryPath. Run post-change.ps1 or assert-fast-gates.ps1 first."
    if ($Json) {
        [ordered]@{ status = 'unavailable'; source = $telemetryPath; reason = $message } | ConvertTo-Json -Depth 3
    }
    Write-Error $message -ErrorAction Continue
    exit 2
}

$records = @(
    Get-Content -LiteralPath $telemetryPath | ForEach-Object {
        try { $_ | ConvertFrom-Json -ErrorAction Stop }
        catch { $null }
    } | Where-Object { $_ }
)
if ($Filter) {
    $records = @($records | Where-Object { $_.gate -match $Filter })
}

if ($records.Count -eq 0) {
    $message = "Gate telemetry contains no records matching '$Filter'."
    if ($Json) {
        [ordered]@{ status = 'unavailable'; source = $telemetryPath; reason = $message } | ConvertTo-Json -Depth 3
    }
    Write-Error $message -ErrorAction Continue
    exit 2
}

$report = @(
    $records | Group-Object gate | ForEach-Object {
        $group = @($_.Group)
        $failures = @($group | Where-Object { $_.status -ne 'PASS' }).Count
        $totalMs = [int]($group | Measure-Object -Property elapsedMs -Sum).Sum
        [pscustomobject]@{
            Gate           = $_.Name
            Invocations    = $group.Count
            Failures       = $failures
            FailureRatePct = [Math]::Round(($failures * 100.0) / $group.Count, 2)
            TotalMs        = $totalMs
            AverageMs      = [Math]::Round($totalMs / $group.Count, 2)
        }
    } | Sort-Object -Property Invocations, Gate -Descending
)

if ($Json) {
    [ordered]@{
        status  = 'ok'
        source  = $telemetryPath
        records = $records.Count
        gates   = $report
    } | ConvertTo-Json -Depth 4
    exit 0
}

Write-Host "Gate telemetry source: $telemetryPath" -ForegroundColor Cyan
Write-Host "Records: $($records.Count); gates: $($report.Count)" -ForegroundColor Cyan
$report | Format-Table -AutoSize
exit 0
