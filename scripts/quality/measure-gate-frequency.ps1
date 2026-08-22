#requires -Version 7.0
<#
.SYNOPSIS
    Reports actual quality-gate frequency from machine-readable gate telemetry.

.DESCRIPTION
    Aggregates records emitted by post-change.ps1 and assert-fast-gates.ps1. It refuses
    with exit 2 when no records exist, because an empty telemetry source is not evidence
    that every gate has zero invocations or zero failures.

    Columns: Executed counts the records where the gate actually ran; Skipped counts the ones
    where it did not apply; Failures counts FAIL and MISSING only. FailureRatePct and AverageMs
    are per EXECUTED run, so a gate that skips on most closures is not flattered by its skips.
    Runners names which runner produced the records - a gate showing only one runner is enforced
    on that runner's cadence alone.

    S1937: until 2026-08-22 only assert-fast-gates.ps1 wrote records, so the journal saw
    roughly 11 runs a day and missed the ~61 daily closures that execute the same gates.
    A report over that journal answered a different question than the one it was asked.

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
        # S1937: a SKIP is not a failure. post-change.ps1 now journals its skips too - a gate
        # that did not apply to a change used to be indistinguishable here from one that found
        # a defect, which would have turned the report's own answer upside down: the gates that
        # skip most often are exactly the ones this report exists to identify as never firing.
        $skips = @($group | Where-Object { $_.status -eq 'SKIP' }).Count
        $failures = @($group | Where-Object { $_.status -eq 'FAIL' -or $_.status -eq 'MISSING' }).Count
        $executed = $group.Count - $skips
        $totalMs = [int]($group | Measure-Object -Property elapsedMs -Sum).Sum
        [pscustomobject]@{
            Gate           = $_.Name
            Executed       = $executed
            Skipped        = $skips
            Failures       = $failures
            FailureRatePct = if ($executed -gt 0) { [Math]::Round(($failures * 100.0) / $executed, 2) } else { 0 }
            TotalMs        = $totalMs
            AverageMs      = if ($executed -gt 0) { [Math]::Round($totalMs / $executed, 2) } else { 0 }
            Runners        = (@($group | Select-Object -ExpandProperty runner -Unique) | Sort-Object) -join '+'
        }
    } | Sort-Object -Property Executed, Skipped, Gate -Descending
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
