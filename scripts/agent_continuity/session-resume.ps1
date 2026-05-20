[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string] $Agent,

    [Parameter(Mandatory = $false)]
    [switch] $Latest = $true
)

# Agent Continuity Layer (S0268) - Resume layer / reader.
# Prints the most recent snapshot under temp/sessions/ for the requested agent.
# Absence of any snapshot is not an error.
# See: PLAN/S0268_agent_continuity_layer.md  §5.2

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sessionsDir = Join-Path $repoRoot 'temp\sessions'

if (-not (Test-Path $sessionsDir)) {
    Write-Output 'NO-SNAPSHOT'
    exit 0
}

try {
    $candidates = Get-ChildItem -Path $sessionsDir -Filter '*_state.md' -File -ErrorAction Stop
} catch {
    Write-Output "ERROR: failed to enumerate snapshots: $($_.Exception.Message)"
    exit 1
}

if ($Agent) {
    $agentId = ($Agent -replace '[^A-Za-z0-9_-]', '_')
    $candidates = $candidates | Where-Object { $_.Name -match "_${agentId}_state\.md$" }
}

if ($null -eq $candidates -or $candidates.Count -eq 0) {
    Write-Output 'NO-SNAPSHOT'
    exit 0
}

# Snapshot filenames start with yyyyMMddHHmmss - lex sort is chronological.
$picked = $candidates | Sort-Object -Property Name -Descending | Select-Object -First 1

Write-Output $picked.FullName
Write-Output ''
try {
    $body = Get-Content -Path $picked.FullName -Raw -ErrorAction Stop
    Write-Output $body
    exit 0
} catch {
    Write-Output "ERROR: failed to read snapshot $($picked.FullName): $($_.Exception.Message)"
    exit 1
}
