#requires -Version 7.0
<#
.SYNOPSIS
    Write a Codex session-boundary handoff (S3141): the content channel, paired with the
    already-existing `ticket-lease.ps1 -Handoff` identity channel.

.DESCRIPTION
    Reads the ticket's current status from the catalog, reads $env:FMS_AGENT_ID, locates the
    newest temp/LEASE-HANDOFF/LEASE-HANDOFF-<Id>-*.json (written by ticket-lease.ps1 -Verb
    Claim; research/06 - this script never mints a second identity file), renders
    .claude/templates/codex-handoff.md with the caller-supplied content, and writes the result
    to temp/<Id>/handoff/<yyyyMMdd-HHmmss>.md.

    A resuming Codex session reads the newest file under that directory instead of
    reconstructing the ticket's state from scratch - the bootstrap-block step Phase 04 adds to
    AGENTS.md.

    Exit codes (S1070):
      0 - handoff file written.
      2 - invalid arguments, or -Id does not resolve in the spec catalog.
      4 - no LEASE-HANDOFF file found for -Id under temp/LEASE-HANDOFF/.

.PARAMETER Id
    Ticket id (Sxxxx).

.PARAMETER Facts
    Proven facts, comma-joined (S1184/S1340: `pwsh -File` binds only the first element of a
    string[] and rejects the rest as positional args, so this is a single string the script
    splits back into bullets - one entry must not itself contain a comma).

.PARAMETER ChangedPaths
    Changed repo-relative paths, comma-joined (same reason as -Facts).

.PARAMETER ExpectedActual
    Pre-formatted "expected: X | actual: Y" lines (CLAUDE.md section 12's evidence form),
    comma-joined (same reason as -Facts).

.PARAMETER GateVerdict
    One line: the build or static-gate verdict at the moment of handoff.

.PARAMETER AuditManualState
    One line or short paragraph: audit/manual-check state at the moment of handoff.

.PARAMETER NextAction
    One sentence: what the next session should do first.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/write-codex-handoff.ps1 -Id S3141 `
      -Facts "read-window.ps1 offloads over 8000 chars" -ChangedPaths ".sza-profile.json" `
      -ExpectedActual "expected: exit 0 | actual: exit 0" -GateVerdict "post-change PASS" `
      -AuditManualState "Phase 01 audit clean" -NextAction "start Phase 02"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Id,
    [string]$Facts = 'none stated',
    [string]$ChangedPaths = 'none',
    [string]$ExpectedActual = 'none recorded',
    [string]$GateVerdict = 'not recorded',
    [string]$AuditManualState = 'not recorded',
    [string]$NextAction = 'not stated'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$FactsList = @($Facts -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$ChangedPathsList = @($ChangedPaths -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$ExpectedActualList = @($ExpectedActual -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })

# --- Status from the catalog, as a real child process (select.ps1 is a forwarder that may
# exit non-zero on its own; calling it in-process would propagate that exit to us). ---------
$selectScript = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'
$statusJson = & pwsh -NoProfile -File $selectScript -Id $Id -Format json 2>$null
$selectExit = $LASTEXITCODE
# An unknown id exits 0 with a literal "[]" - not an error, not empty text - so both must be
# checked explicitly, or ConvertFrom-Json hands back a zero-length array whose .status throws
# under StrictMode instead of reading as "not found".
$catalogRecord = if ($selectExit -eq 0 -and -not [string]::IsNullOrWhiteSpace($statusJson)) { $statusJson | ConvertFrom-Json } else { $null }
$notFound = (-not $catalogRecord) -or ($catalogRecord -is [array] -and $catalogRecord.Count -eq 0)
if ($notFound) {
    Write-Host "write-codex-handoff: $Id did not resolve in the spec catalog (select.ps1 exit $selectExit)." -ForegroundColor Red
    exit 2
}
$status = [string]$catalogRecord.status

# --- Identity: the existing lease-handoff file, never a new one. --------------------------
$leaseHandoffDir = Join-Path $repoRoot 'temp/LEASE-HANDOFF'
$leaseCandidates = @()
if (Test-Path -LiteralPath $leaseHandoffDir) {
    $leaseCandidates = @(Get-ChildItem -Path $leaseHandoffDir -Filter "LEASE-HANDOFF-$Id-*.json" -File -ErrorAction SilentlyContinue)
}
if ($leaseCandidates.Count -eq 0) {
    Write-Host "write-codex-handoff: no LEASE-HANDOFF file for $Id under $leaseHandoffDir - claim the ticket lease first." -ForegroundColor Red
    exit 4
}
$leaseFile = ($leaseCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$leaseRelative = $leaseFile.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')

$agentId = if ($env:FMS_AGENT_ID) { $env:FMS_AGENT_ID } else { '(unset)' }

# --- Render the template --------------------------------------------------------------------
$templatePath = Join-Path $repoRoot '.claude/templates/codex-handoff.md'
$content = Get-Content -LiteralPath $templatePath -Raw
# Strip the leading authoring-guidance HTML comment before substituting: it is documentation
# for a human editing the template, not a field, and a blind -replace below would otherwise
# also rewrite its own placeholder tokens (e.g. "Substitute: <Sxxxx>" -> "Substitute: S3141"),
# turning the explanation into noise instead of leaving it as prose.
# The template opens with TWO consecutive comments (a one-line attribution, then the longer
# substitution note) - `(?:<!--.*?-->\s*)+` repeats the non-greedy match so both are stripped,
# not just the first.
$content = $content -replace '(?s)^\s*(?:<!--.*?-->\s*)+', ''

$factsBlock = ($FactsList | ForEach-Object { "- $_" }) -join "`n"
$pathsBlock = ($ChangedPathsList | ForEach-Object { "- $_" }) -join "`n"
$expectedActualBlock = ($ExpectedActualList | ForEach-Object { "- $_" }) -join "`n"

$content = $content -replace '<Sxxxx>', $Id
$content = $content -replace '<status>', $status
$content = $content -replace '<FMS_AGENT_ID value>', $agentId
$content = $content -replace '<LEASE-HANDOFF file path>', $leaseRelative
$content = $content -replace '<YYYY-MM-DD HH:mm>', (Get-Date -Format 'yyyy-MM-dd HH:mm')
$content = $content -replace '- <fact>', $factsBlock
$content = $content -replace '- <path>', $pathsBlock
$content = $content -replace '- <expected>: <actual>', $expectedActualBlock
$content = $content -replace '<verdict>', $GateVerdict
$content = $content -replace '<audit/manual state>', $AuditManualState
$content = $content -replace '<next action>', $NextAction

# --- Write -------------------------------------------------------------------------------
$handoffDir = Join-Path $repoRoot "temp/$Id/handoff"
New-Item -ItemType Directory -Path $handoffDir -Force | Out-Null
$outFile = Join-Path $handoffDir "$(Get-Date -Format 'yyyyMMdd-HHmmss').md"
Set-Content -LiteralPath $outFile -Value $content -NoNewline

$outRelative = $outFile.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
Write-Host "write-codex-handoff: wrote $outRelative"
exit 0
