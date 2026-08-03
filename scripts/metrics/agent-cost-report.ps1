<#
.SYNOPSIS
  Operator entry point for the agent-process cost measurement.

.DESCRIPTION
  Resolves the Claude Code transcript root for this project, runs the
  stack-agnostic extractor (mine-agent-transcripts.py) over an optional date
  window, and writes the JSON metrics plus a readable digest under temp/.

  Prints the headline figures the S1338 measurement plan tracks: unique
  requests, all-in cache_read, hard and combined tool-failure rates, and the
  pre-compaction token percentiles that are S1339's acceptance metric.

.PARAMETER Since
  Inclusive lower bound, YYYY-MM-DD. Omit for the whole corpus.

.PARAMETER Until
  Inclusive upper bound, YYYY-MM-DD. Omit for the whole corpus.

.PARAMETER TranscriptRoot
  Override the transcript directory. Defaults to the Claude Code projects
  directory for this repository.

.PARAMETER OutputPath
  Directory for transcript_metrics.json and transcript_digest.txt.
  Defaults to temp/metrics.

.PARAMETER Json
  Emit the headline figures as JSON instead of formatted text.

.EXAMPLE
  pwsh -NoProfile -File scripts/metrics/agent-cost-report.ps1 -Since 2026-06-30 -Until 2026-07-31

.NOTES
  Exit codes: 0 report written; 1 error (extractor failed); 2 cannot verify
  (python missing, transcript root absent or empty, output unreadable).
#>
[CmdletBinding()]
param(
    [string] $Since,
    [string] $Until,
    [string] $TranscriptRoot,
    [string] $OutputPath,
    [switch] $Json
)

$ErrorActionPreference = 'Stop'

function Fail-CannotVerify {
    param([string] $Message)
    Write-Error $Message -ErrorAction Continue
    exit 2
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$extractor = Join-Path $PSScriptRoot 'mine-agent-transcripts.py'
if (-not (Test-Path $extractor)) {
    Fail-CannotVerify "extractor not found: $extractor"
}

if (-not $TranscriptRoot) {
    # Claude Code names the transcript directory after the project path with
    # every ':', separator and underscore replaced by '-'. Repeats are NOT
    # collapsed ("p:\X" -> "p--X"), and the drive letter's case follows the
    # shell that created it - so resolve the derived name case-insensitively
    # against what is actually on disk rather than trusting the string.
    $projects = Join-Path $HOME '.claude/projects'
    if (-not (Test-Path $projects)) {
        Fail-CannotVerify "Claude Code projects directory not found: $projects"
    }
    $slug = $repoRoot -replace '[:\\/_]', '-'
    $match = Get-ChildItem -Path $projects -Directory |
        Where-Object { $_.Name -ieq $slug } |
        Select-Object -First 1
    if (-not $match) {
        Fail-CannotVerify "no transcript directory matching '$slug' under $projects"
    }
    $TranscriptRoot = $match.FullName
}
if (-not (Test-Path $TranscriptRoot)) {
    Fail-CannotVerify "transcript root not found: $TranscriptRoot"
}

if (-not $OutputPath) { $OutputPath = Join-Path $repoRoot 'temp/metrics' }
New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null

$python = (Get-Command python -ErrorAction SilentlyContinue)
if (-not $python) { $python = (Get-Command python3 -ErrorAction SilentlyContinue) }
if (-not $python) {
    Fail-CannotVerify 'python not found on PATH - cannot run the extractor.'
}

$pyArgs = @($extractor, '--root', $TranscriptRoot, '--out', $OutputPath)
if ($Since) { $pyArgs += @('--since', $Since) }
if ($Until) { $pyArgs += @('--until', $Until) }

& $python.Source @pyArgs | Out-Null
$code = $LASTEXITCODE
if ($code -eq 2) {
    Fail-CannotVerify "extractor could not read the corpus at $TranscriptRoot"
}
if ($code -ne 0) {
    Write-Error "extractor failed with exit code $code" -ErrorAction Continue
    exit 1
}

$metricsFile = Join-Path $OutputPath 'transcript_metrics.json'
if (-not (Test-Path $metricsFile)) {
    Fail-CannotVerify "extractor wrote no metrics file at $metricsFile"
}

try {
    $m = Get-Content $metricsFile -Raw | ConvertFrom-Json
} catch {
    Fail-CannotVerify "metrics file is not valid JSON: $metricsFile"
}

$summary = [ordered]@{
    window            = "$(if ($Since) { $Since } else { 'all' })..$(if ($Until) { $Until } else { 'all' })"
    sessions          = $m.sessions
    requests          = $m.requests
    cacheReadTokens   = $m.totals.cache_read
    outputTokens      = $m.totals.out
    toolCalls         = $m.failure_rates.calls
    hardFailurePct    = $m.failure_rates.hard_pct
    combinedFailurePct = $m.failure_rates.hard_plus_soft_pct
    compactions       = $m.compaction.count
    preTokensMedian   = $m.compaction.pre_tokens_percentiles.p50
    preTokensP90      = $m.compaction.pre_tokens_percentiles.p90
    reportPath        = $metricsFile
}

if ($Json) {
    $summary | ConvertTo-Json -Depth 4
} else {
    Write-Host "agent-cost-report  window $($summary.window)"
    Write-Host ("  sessions {0}  requests {1}" -f $summary.sessions, $summary.requests)
    Write-Host ("  cache_read {0:N2} G  output {1:N2} M" -f
        ($summary.cacheReadTokens / 1e9), ($summary.outputTokens / 1e6))
    Write-Host ("  tool calls {0}  hard fail {1}%  combined {2}%" -f
        $summary.toolCalls, $summary.hardFailurePct, $summary.combinedFailurePct)
    Write-Host ("  compactions {0}  preTokens p50 {1:N0}  p90 {2:N0}" -f
        $summary.compactions, $summary.preTokensMedian, $summary.preTokensP90)
    Write-Host "  report: $metricsFile"
}

exit 0
