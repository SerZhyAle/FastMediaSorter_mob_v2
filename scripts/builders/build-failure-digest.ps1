<#
.SYNOPSIS
  One-shot, caller-started build/lint failure digest (S0312).

.DESCRIPTION
  Turns the latest saved build/lint log into a compact, machine-readable digest so an
  agent can read the first actionable failure without scanning a raw log by hand.

  It delegates raw failure-block extraction to the existing
  scripts/builders/get-last-build-failure.ps1 (a.ps1 bf) per strategic ADR-1 - it does
  NOT re-implement FAILURE:-block scanning. It then runs the pure Phase 01 parser
  (build-failure-digest.contract.ps1) over the captured text, fills command / rawLogPath /
  exitCode, writes the digest JSON under temp/, and prints either a single JSON object
  (-Json) or a concise human verdict.

  Anti-stale-success guard (strategic §2.4, §11.3): when no log can be resolved or the
  log is empty, the verdict is 'blocked' (exit 20), never a fake 'success'.

  Exit codes (stable; a caller may branch on them):
    0  - digest produced, verdict 'success' (log shows BUILD SUCCESSFUL, no failure block).
    10 - digest produced, verdict 'failure' (a failure block or actionable failure found).
         Non-zero on purpose so a caller can branch on "build is broken".
    20 - digest produced, verdict 'blocked' (no log found or empty log; run not resolvable).
         Distinct from 10 so a caller can tell "broken build" from "could not read a build".
    2  - usage error (bad parameter combination, e.g. -Json with -DryRun).

  -DryRun: resolves and prints the log path it would digest plus the exit-code map it
  would apply, performs no parse, writes no artifact, and exits 0. -NoProfile-safe and
  side-effect-free apart from the resolved-plan printout.

  JSON is emitted only with -Json (single compressed object on stdout, human noise
  suppressed). Without -Json a concise human summary is printed; the first actionable
  failure (file:line + message) and the raw-log path are always shown so the digest
  never hides the real failure behind console noise (strategic §2.3).

  Artifacts: temp/build-failure-digest.json (always written on a real run).

.PARAMETER LogPath
  Explicit build/lint log to digest. When omitted, the newest temp/*build*.log is used
  (same selection rule as get-last-build-failure.ps1).

.PARAMETER Command
  The build/lint command whose log is being digested (e.g. assembleStandardDebug). Recorded
  verbatim in the digest 'command' field. Defaults to 'unknown' when not supplied.

.PARAMETER Json
  Emit a single compressed JSON object instead of human-readable lines.

.PARAMETER DryRun
  Print the resolved plan (log path + exit-code map) and exit 0 without parsing or writing.

.EXAMPLE
  pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1
  Digest the newest temp/*build*.log and print a human verdict.

.EXAMPLE
  pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1 -Command assembleStandardDebug -Json
  Machine-readable digest for the newest build log, tagged with the command.

.EXAMPLE
  pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1 -DryRun
  Show which log would be digested and the exit-code map, without doing any work.
#>
[CmdletBinding()]
param(
    [string]$LogPath,
    [string]$Command,
    [switch]$Json,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# Verdict -> exit-code map. Single source of truth, shared by the real run and the
# -DryRun plan printout so the documented contract and the runtime never drift.
$verdictExit = [ordered]@{
    success = 0
    failure = 10
    blocked = 20
}
$usageErrorExit = 2

# Dot-source the Phase 01 contract + parser from this script's own folder.
. (Join-Path $PSScriptRoot 'build-failure-digest.contract.ps1')

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$bfScript    = Join-Path $PSScriptRoot 'get-last-build-failure.ps1'
$artifactPath = Join-Path $projectRoot 'temp\build-failure-digest.json'

function Resolve-PlannedLogPath {
    <#
      Resolve the log path the digest WOULD read, without triggering the child script's
      own exit 2/3. This mirrors get-last-build-failure.ps1's selection rule (explicit
      -LogPath, else newest temp/*build*.log). It is plan resolution only - it does not
      scan for FAILURE: blocks (that stays delegated to the child per ADR-1). Returns
      $null when nothing resolves.
    #>
    param([string]$ExplicitLogPath)

    if ($ExplicitLogPath) {
        $resolved = Resolve-Path -Path $ExplicitLogPath -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Path }
        return $null
    }

    $tempDir = Join-Path $projectRoot 'temp'
    if (-not (Test-Path -Path $tempDir)) { return $null }

    $latest = Get-ChildItem -Path $tempDir -Filter '*build*.log' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latest) { return $null }
    return $latest.FullName
}

# ---------- usage-error guard ----------
# Machine output of a no-op plan is meaningless, so reject -Json + -DryRun.
if ($Json -and $DryRun) {
    Write-Host "Usage error: -Json and -DryRun cannot be combined (a dry-run plan has no JSON digest)." -ForegroundColor Red
    exit $usageErrorExit
}

# ---------- dry-run plan ----------
if ($DryRun) {
    $plannedLog = Resolve-PlannedLogPath -ExplicitLogPath $LogPath
    Write-Host "DRY RUN - build-failure-digest" -ForegroundColor Cyan
    if ($plannedLog) {
        Write-Host "  would digest log : $plannedLog"
    } else {
        Write-Host "  would digest log : <none resolvable> (verdict would be 'blocked')"
    }
    Write-Host "  artifact         : $artifactPath"
    Write-Host "  exit-code map    : success=0  failure=10  blocked=20  usage-error=2"
    Write-Host "  no parse performed, no artifact written."
    exit 0
}

# ---------- acquire raw log text via the existing bf path (ADR-1) ----------
# Invoke get-last-build-failure.ps1 and capture its stdout + exit code. We do not
# duplicate its FAILURE:-block scan; we feed its output lines to the pure parser.
$bfArgs = @{}
if ($LogPath) { $bfArgs['LogPath'] = $LogPath }

$bfOutput = & $bfScript @bfArgs 2>&1
$bfExit = $LASTEXITCODE
$logLines = @($bfOutput | ForEach-Object { [string]$_ })

# Resolve the absolute raw-log path for the digest. The child does not return it, so
# resolve it the same way for the record (null when nothing resolved / exit 2 / 3).
$resolvedLogPath = Resolve-PlannedLogPath -ExplicitLogPath $LogPath

# get-last-build-failure.ps1: exit 2 = no log, exit 3 = empty log. Either way the run
# is not resolvable -> blocked, not a stale success. Leave logLines empty so the parser
# yields the default 'blocked' verdict.
if ($bfExit -eq 2 -or $bfExit -eq 3) {
    $logLines = @()
    $resolvedLogPath = $null
}

# ---------- parse ----------
$digest = ConvertFrom-BuildFailureLog -LogLines $logLines

# ---------- fill envelope fields ----------
if ([string]::IsNullOrWhiteSpace($Command)) {
    $digest.command = 'unknown'
} else {
    $digest.command = $Command
}
$digest.rawLogPath = $resolvedLogPath
$digest.exitCode   = $verdictExit[$digest.verdict]

# ---------- write artifact under temp/ ----------
$tempDir = Join-Path $projectRoot 'temp'
if (-not (Test-Path -Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
}
$digest | ConvertTo-Json -Depth 5 | Set-Content -Path $artifactPath -Encoding UTF8

# ---------- emit ----------
if ($Json) {
    # Single compressed JSON object on stdout; no human noise.
    $digest | ConvertTo-Json -Depth 5 -Compress
} else {
    $faf = $digest.firstActionableFailure
    Write-Host "verdict: $($digest.verdict)"
    Write-Host "command: $($digest.command)  exitCode: $($digest.exitCode)"
    if ($faf -and ($faf.file -or $faf.message -or $faf.module)) {
        $location = if ($faf.file) {
            "$($faf.file)$(if ($null -ne $faf.line) { ":$($faf.line)" })"
        } else {
            $faf.module
        }
        Write-Host "first actionable failure: $location"
        if ($faf.message) { Write-Host "  message: $($faf.message)" }
    } else {
        Write-Host "first actionable failure: <none>"
    }
    if ($digest.rawLogPath) {
        Write-Host "raw log: $($digest.rawLogPath)"
    } else {
        Write-Host "raw log: <none resolvable>"
    }
}

exit $digest.exitCode
