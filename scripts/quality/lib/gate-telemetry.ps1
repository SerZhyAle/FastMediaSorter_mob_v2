#requires -Version 7.0

function Get-GateTelemetryPath {
    $repositoryRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return Join-Path $repositoryRoot 'temp/metrics/gate-executions.jsonl'
}

function Write-GateTelemetryRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Runner,
        [Parameter(Mandatory = $true)][string]$Gate,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][int]$ElapsedMs
    )

    try {
        $path = Get-GateTelemetryPath
        $directory = Split-Path -Parent $path
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
        $record = [ordered]@{
            timestampUtc = [DateTime]::UtcNow.ToString('o')
            runner       = $Runner
            gate         = $Gate
            status       = $Status
            exitCode     = $ExitCode
            elapsedMs    = $ElapsedMs
        }
        [System.IO.File]::AppendAllText(
            $path,
            (($record | ConvertTo-Json -Compress) + [Environment]::NewLine)
        )
    }
    catch {
        # Telemetry must not change the gate's certification verdict.
    }
}
