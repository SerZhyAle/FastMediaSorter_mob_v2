[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Request,

    [Parameter(Mandatory = $false)]
    [string] $Route = '',

    [Parameter(Mandatory = $false)]
    [string] $Module = '',

    [Parameter(Mandatory = $false)]
    [string] $Flavor = '',

    [Parameter(Mandatory = $false)]
    [string] $Ticket = '',

    [Parameter(Mandatory = $false)]
    [string[]] $FilesTouched,

    [Parameter(Mandatory = $false)]
    [string] $ValidationKind = '',

    [Parameter(Mandatory = $false)]
    [int] $ValidationExit = 0,

    [Parameter(Mandatory = $false)]
    [string] $InterruptionMarker = '',

    [Parameter(Mandatory = $false)]
    [ValidateSet('done', 'partial', 'aborted', 'escalated', '')]
    [string] $Outcome = ''
)

# Agent Continuity Layer (S0268) - Request logger.
# Appends one JSONL line per significant session to dev/agent-continuity/requests.jsonl.
# See: PLAN/S0268_agent_continuity_layer.md  §5.3
#      scripts/agent_continuity/README.md    §6.6 (format)

$ErrorActionPreference = 'Stop'

if ($Ticket -and $Ticket -notmatch '^S\d{4}$') {
    Write-Output "ERROR: -Ticket must match S####, got: $Ticket"
    exit 1
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$logDir = Join-Path $repoRoot 'dev\agent-continuity'
$logPath = Join-Path $logDir 'requests.jsonl'

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$timestamp = Get-Date -Format 'yyyy-MM-ddTHH:mm:ss'

# Build ordered hash so JSON keys land in the documented order.
$record = [ordered]@{
    ts                  = $timestamp
    request             = $Request
    route               = $Route
    module              = $Module
    flavor              = $Flavor
    ticket              = $Ticket
    files_touched       = if ($null -eq $FilesTouched) { @() } else { @($FilesTouched) }
    validation_kind     = $ValidationKind
    validation_exit     = $ValidationExit
    interruption_marker = $InterruptionMarker
    outcome             = $Outcome
}

try {
    $jsonLine = $record | ConvertTo-Json -Compress -Depth 4
    Add-Content -Path $logPath -Value $jsonLine -Encoding UTF8
    Write-Output $timestamp
    exit 0
} catch {
    Write-Output "ERROR: failed to append to ${logPath}: $($_.Exception.Message)"
    exit 1
}
