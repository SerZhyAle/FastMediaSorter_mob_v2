#requires -Version 7.0

function Get-GateTelemetryPath {
    $repositoryRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return Join-Path $repositoryRoot 'temp/metrics/gate-executions.jsonl'
}

# S2453: the name reserved for a whole runner's wall clock, distinct from any child gate. A doc
# row like `a.ps1 fg | 32 s` is a claim about the batch, and since S2451 ran the children
# concurrently the sum of their elapsedMs is no longer that number - so without this record the
# headline figure the agent reads is the one thing the journal cannot answer.
$script:GateTelemetryBatchName = '(batch)'

function Write-GateBatchTelemetryRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Runner,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][int]$ElapsedMs
    )

    $status = switch ($ExitCode) { 0 { 'PASS' } 2 { 'MISSING' } default { 'FAIL' } }
    Write-GateTelemetryRecord -Runner $Runner -Gate $script:GateTelemetryBatchName `
        -Status $status -ExitCode $ExitCode -ElapsedMs $ElapsedMs
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
