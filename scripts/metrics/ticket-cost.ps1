#requires -Version 7.0
<#
.SYNOPSIS
    Per-ticket agent token-cost journal: record a ticket's transcript window, show its context
    map, summarise a time window (S3147).

.DESCRIPTION
    Scripts an agent calls never see its reads, edits or token usage - only the runtime's own
    transcript does (PLAN/S3147_agent-context-token-cost/research/02). This entry point resolves
    the runtime and hands the work to ticket_cost.py beside it.

    -Verb Record   Appends one row to temp/agent-cost/ticket-cost.jsonl and rewrites
                   temp/<Id>/context-map.md. Silent on success: post-change.ps1 calls it at every
                   ticket closure, and printing would add to the very context it measures.
    -Verb Map      Prints temp/<Id>/context-map.md; a file line whose content changed since the
                   record ends in "(changed)".
    -Verb Summary  Ranks tickets by chars read but never edited, reads before the first edit and
                   the longest failure streak of one command; totals per runtime; average input
                   tokens of a first versus a repeat entry into a ticket.

    Runtime resolution reads CLAUDE_CODE_SESSION_ID before FMS_AGENT_RUNTIME. The order is
    measured: a Claude Code session carried FMS_AGENT_RUNTIME=Codex on 2026-09-15.

    Exit codes:
      0 - Record wrote a row, Map printed the map, Summary printed.
      1 - the extractor failed.
      2 - bad invocation, or python not found.
      3 - Map: no context map recorded for the ticket.

.EXAMPLE
    pwsh -NoProfile -File scripts/metrics/ticket-cost.ps1 -Verb Map -Id S3147
    pwsh -NoProfile -File scripts/metrics/ticket-cost.ps1 -Verb Summary -Since 2026-09-15
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Record', 'Map', 'Summary')][string]$Verb,
    [string]$Id,
    [string]$Transcript,
    [string]$Since,
    [string]$Until,
    [int]$Top = 10,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$extractor = Join-Path $PSScriptRoot 'ticket_cost.py'
$ledger = Join-Path $repoRoot 'temp/agent-cost/ticket-cost.jsonl'

if ($Verb -ne 'Summary' -and $Id -notmatch '^S\d{4}$') {
    Write-Host "ticket-cost: -Verb $Verb needs -Id Sxxxx, got '$Id'." -ForegroundColor Red
    exit 2
}

$python = @('python', 'python3', 'py') |
    ForEach-Object { Get-Command $_ -CommandType Application -ErrorAction SilentlyContinue } |
    Select-Object -First 1
if (-not $python) {
    Write-Host 'ticket-cost: python not found on PATH.' -ForegroundColor Red
    exit 2
}

switch ($Verb) {
    'Record' {
        $runtime = 'unknown'
        if ($env:CLAUDE_CODE_SESSION_ID) { $runtime = 'claude' }
        elseif ($env:FMS_AGENT_RUNTIME -match '(?i)codex') { $runtime = 'codex' }
        elseif ($env:FMS_AGENT_RUNTIME -match '(?i)gemini|antigravity') { $runtime = 'gemini' }
        elseif ($env:FMS_AGENT_RUNTIME -match '(?i)zcode') { $runtime = 'zcode' }
        $session = if ($runtime -eq 'claude') { $env:CLAUDE_CODE_SESSION_ID } else { [string]$env:FMS_AGENT_ID }
        $pyArgs = @($extractor, 'record', '--ticket', $Id, '--runtime', $runtime, '--repo-root', $repoRoot,
            '--ledger', $ledger, '--map-dir', (Join-Path $repoRoot "temp/$Id"), '--home', $HOME)
        if ($session) { $pyArgs += @('--session', $session) }
        if ($Transcript) { $pyArgs += @('--transcript', $Transcript) }
    }
    'Map' {
        $pyArgs = @($extractor, 'map', '--map', (Join-Path $repoRoot "temp/$Id/context-map.md"),
            '--repo-root', $repoRoot)
    }
    'Summary' {
        $pyArgs = @($extractor, 'summary', '--ledger', $ledger, '--top', "$Top")
        if ($Since) { $pyArgs += @('--since', $Since) }
        if ($Until) { $pyArgs += @('--until', $Until) }
        if ($Json) { $pyArgs += '--json' }
    }
}

& $python.Source @pyArgs
exit $LASTEXITCODE
