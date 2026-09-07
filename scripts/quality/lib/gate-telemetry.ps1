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

# S2538: one id per process, so a row can be attributed to the run that wrote it. Two stalls were
# journalled as a PASS row and a FAIL row for the same gate 23 ms apart carrying identical
# durations, and nothing in the record could say whether that was one run reporting twice or two
# runs released by one event - the question the stall raises is the one the journal could not
# answer. Generated lazily rather than at load: this file is dot-sourced by scripts that write no
# telemetry at all.
$script:GateTelemetryRunId = $null
function Get-GateTelemetryRunId {
    if (-not $script:GateTelemetryRunId) {
        $script:GateTelemetryRunId = ([guid]::NewGuid().ToString('n')).Substring(0, 12)
    }
    return $script:GateTelemetryRunId
}

# S2693: which area a row's verdict was about - 'set' for a gate that judged the caller's changed
# files, 'tree' for one that judged the whole project. Optional, and absent on every row written
# before the split, so a reader must treat a missing field as "unknown", not as 'tree'.
function Write-GateBatchTelemetryRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Runner,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][int]$ElapsedMs,
        [ValidateSet('set', 'tree', 'split')]
        [string]$Scope
    )

    $status = switch ($ExitCode) { 0 { 'PASS' } 2 { 'MISSING' } default { 'FAIL' } }
    $forward = @{
        Runner    = $Runner
        Gate      = $script:GateTelemetryBatchName
        Status    = $status
        ExitCode  = $ExitCode
        ElapsedMs = $ElapsedMs
    }
    if ($Scope) { $forward['Scope'] = $Scope }
    Write-GateTelemetryRecord @forward
}

function Write-GateTelemetryRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Runner,
        [Parameter(Mandatory = $true)][string]$Gate,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][int]$ElapsedMs,
        [ValidateSet('set', 'tree', 'split')]
        [string]$Scope,
        [int]$FindingCount,
        [string[]]$FindingPaths,
        [bool]$FindingDetailsAvailable
    )

    try {
        $path = Get-GateTelemetryPath
        $directory = Split-Path -Parent $path
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
        $record = [ordered]@{
            timestampUtc = [DateTime]::UtcNow.ToString('o')
            runner       = $Runner
            runId        = Get-GateTelemetryRunId
            gate         = $Gate
            status       = $Status
            exitCode     = $ExitCode
            elapsedMs    = $ElapsedMs
        }
        if ($Scope) { $record['scope'] = $Scope }
        if ($PSBoundParameters.ContainsKey('FindingCount')) { $record['findingCount'] = $FindingCount }
        if ($PSBoundParameters.ContainsKey('FindingPaths')) { $record['findingPaths'] = @($FindingPaths) }
        if ($PSBoundParameters.ContainsKey('FindingDetailsAvailable')) {
            $record['findingDetailsAvailable'] = $FindingDetailsAvailable
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
