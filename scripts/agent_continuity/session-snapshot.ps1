# PositionalBinding = $false is load-bearing, not style. Called as `pwsh -File this.ps1 -FilesTouched a b c`,
# every token after the first bound to -FilesTouched only by accident of declaration order: with positional
# binding on, file #2 landed in -Decisions and file #3 in -Blockers, and the script still exited 0 - a
# corrupted snapshot that reported success. Named-only binding turns that into a hard error (S0429, 2026-08-06).
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory = $true)]
    [string] $Goal,

    [Parameter(Mandatory = $false)]
    [string] $Ticket,

    [Parameter(Mandatory = $false)]
    [string[]] $FilesTouched,

    [Parameter(Mandatory = $false)]
    [string] $Decisions,

    [Parameter(Mandatory = $false)]
    [string] $Blockers,

    [Parameter(Mandatory = $false)]
    [string] $NextStep,

    [Parameter(Mandatory = $false)]
    [string] $Agent
)

# Agent Continuity Layer (S0268) - Resume layer / writer.
# Writes a compact Markdown snapshot of the current session state.
# See: PLAN/S0268_agent_continuity_layer.md  §5.2
#      scripts/agent_continuity/README.md    §6.2 (agent id) / §6.3 (trigger)

$ErrorActionPreference = 'Stop'

# Agent identifier resolution per §6.2: -Agent wins, else env AGENT_NAME, else "agent".
$agentId = $Agent
if ([string]::IsNullOrWhiteSpace($agentId)) { $agentId = $env:AGENT_NAME }
if ([string]::IsNullOrWhiteSpace($agentId)) { $agentId = 'agent' }
$agentId = ($agentId -replace '[^A-Za-z0-9_-]', '_')

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sessionsDir = Join-Path $repoRoot 'temp\sessions'
if (-not (Test-Path $sessionsDir)) {
    New-Item -ItemType Directory -Path $sessionsDir -Force | Out-Null
}

$timestamp = Get-Date -Format 'yyyyMMddHHmmss'
$outPath = Join-Path $sessionsDir "${timestamp}_${agentId}_state.md"

function Section([string]$Header, [string]$Body) {
    if ([string]::IsNullOrWhiteSpace($Body)) { return @($Header, '-', '') }
    return @($Header, $Body, '')
}

function FilesSection([string]$Header, [string[]]$Items) {
    if ($null -eq $Items -or $Items.Count -eq 0) { return @($Header, '-', '') }
    $lines = @($Header)
    # A `pwsh -File` caller cannot pass a real array - the shell hands over flat strings - so a comma-joined
    # list is the only multi-file form available to it. Splitting here gives that caller a working path now
    # that positional binding no longer silently absorbs the extra tokens into -Decisions and -Blockers.
    foreach ($i in ($Items -split ',')) {
        $entry = $i.Trim()
        if (-not [string]::IsNullOrWhiteSpace($entry)) { $lines += "- $entry" }
    }
    $lines += ''
    return $lines
}

$content = New-Object System.Collections.Generic.List[string]
foreach ($l in (Section '## goal'     $Goal))     { $content.Add($l) }
foreach ($l in (Section '## ticket'   $Ticket))   { $content.Add($l) }
foreach ($l in (FilesSection '## files-touched' $FilesTouched)) { $content.Add($l) }
foreach ($l in (Section '## decisions' $Decisions)) { $content.Add($l) }
foreach ($l in (Section '## blockers'  $Blockers))  { $content.Add($l) }
foreach ($l in (Section '## next-step' $NextStep))  { $content.Add($l) }

try {
    $content -join "`n" | Set-Content -Path $outPath -Encoding UTF8 -NoNewline
    Write-Output $outPath
    exit 0
} catch {
    Write-Output "ERROR: snapshot write failed: $($_.Exception.Message)"
    exit 1
}
