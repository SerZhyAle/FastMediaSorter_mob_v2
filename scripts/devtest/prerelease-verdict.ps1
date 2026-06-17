<#
.SYNOPSIS
  S0484 pre-release sweep - verdict aggregator (PASS/FAIL).

.DESCRIPTION
  Folds three signal sources into one machine verdict (research/05):
    - log         : crashes/ANR + app errors (minus expected fallbacks) via search-log.ps1
    - perf        : per-checkpoint pass flags from the measure records (Phase 03 output)
    - screenshot  : checkpoint screenshot results
  Reuses scripts/utils/search-log.ps1 - never reads a large logcat into context directly.

  Exit codes:
    0 - PASS
    1 - content FAIL (log / perf / screenshot)
    2 - infrastructure abort (LogFile missing / inputs unreadable)

.PARAMETER LogFile
  Captured logcat for the run window.

.PARAMETER MetricsFile
  JSON file: array of per-checkpoint measure records (each with a `pass` field).

.PARAMETER ScreensDir
  Directory of per-checkpoint screenshots (evidence).

.PARAMETER FromTs
  Optional HH:MM:SS lower bound to scope log queries to the run window.

.PARAMETER Json
  Emit a single JSON verdict object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-verdict.ps1 -LogFile temp/run.log -Json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$LogFile,
    [string]$MetricsFile,
    [string]$ScreensDir,
    [string]$FromTs,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$SearchLog = Join-Path $PSScriptRoot '..\utils\search-log.ps1'

if (-not (Test-Path $LogFile))   { if ($Json) { '{"pass":false,"error":"log file not found"}' } else { Write-Host 'log file not found' }; exit 2 }
if (-not (Test-Path $SearchLog)) { if ($Json) { '{"pass":false,"error":"search-log.ps1 not found"}' } else { Write-Host 'search-log.ps1 not found' }; exit 2 }

# Expected, non-actionable fallback patterns (research/05) - suppressed from the FAIL count.
$expectedFallbacks = @(
    'Native set .* unavailable on this install', 'OCR engines not installed', 'UnsatisfiedLinkError loading',
    'GmsAvailabilityChecker.*unavailable', 'XR device detected', 'CctAvailabilityChecker',
    'CastMediaManager.*not supported', 'LocalCastProxyServer.*unavailable',
    '\(non-critical\)', '\(ignored\)', 'NetworkReachabilityGate: no-(network|wifi)',
    'scanFolderSAFFast.*No permission', 'MediaCodec error', 'Audio renderer failed',
    'Media3OomSafeLogger', 'Upgrade reconciliation', 'ShareTarget package not installed'
) -join '|'

function Invoke-SearchLog {
    param([string[]]$ExtraArgs)
    $a = @('-NoProfile', '-File', $SearchLog, '-LogFile', $LogFile)
    if ($FromTs) { $a += @('-From', $FromTs) }
    $a += $ExtraArgs
    return (& pwsh @a 2>&1)
}

function Get-Count {
    param([string[]]$ExtraArgs)
    $out = Invoke-SearchLog -ExtraArgs (@('-Count') + $ExtraArgs)
    # search-log.ps1 -Count prints a definitive "Match count: N" line; parse only that.
    $m = [regex]::Match("$out", 'Match count:\s*(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value }
    return 0
}

# ---------- log signal (step 04.1) ----------
$allErrors      = Get-Count -ExtraArgs @('-Errors', '-AppOnly', '-Unique')
$expectedErrors = Get-Count -ExtraArgs @('-Errors', '-AppOnly', '-Unique', '-Pattern', $expectedFallbacks)
$netErrors      = [Math]::Max(0, $allErrors - $expectedErrors)

$excOut     = Invoke-SearchLog -ExtraArgs @('-Exceptions')
$crashBlocks = -not ("$excOut" -match 'No exception/crash blocks found')

$priorCrash = (Get-Count -ExtraArgs @('-AppOnly', '-Pattern', 'PREVIOUS SESSION ENDED WITH A CRASH')) -gt 0

$logPass = ($netErrors -eq 0) -and (-not $crashBlocks) -and (-not $priorCrash)
$logBreakdown = [ordered]@{ pass = [bool]$logPass; actionableErrors = $netErrors; crashBlocks = [bool]$crashBlocks; priorCrash = [bool]$priorCrash }

# ---------- perf + screenshot signals (step 04.2) ----------
# perf: every measure record in MetricsFile must have pass=true. Missing file = no perf data
# supplied this run (perf neutral/pass); a present file with any failing checkpoint = FAIL.
$perfFailures = @()
$perfPass = $true
if ($MetricsFile -and (Test-Path $MetricsFile)) {
    $records = @(Get-Content -Raw -Path $MetricsFile | ConvertFrom-Json)
    foreach ($r in $records) {
        if (-not $r.pass) { $perfPass = $false; $perfFailures += "$($r.checkpoint)=$($r.measured)/$($r.limit)" }
    }
}
$perfBreakdown = [ordered]@{ pass = [bool]$perfPass; failures = $perfFailures }

# screenshot: a present ScreensDir must contain at least one captured checkpoint screenshot.
$screenshotFailures = @()
$screenshotPass = $true
if ($ScreensDir -and (Test-Path $ScreensDir)) {
    $shots = @(Get-ChildItem -Path $ScreensDir -Filter '*.png' -File -ErrorAction SilentlyContinue)
    if ($shots.Count -eq 0) { $screenshotPass = $false; $screenshotFailures += 'no checkpoint screenshots captured' }
}
$screenshotBreakdown = [ordered]@{ pass = [bool]$screenshotPass; failures = $screenshotFailures }

$pass = $logPass -and $perfPass -and $screenshotPass

# ---------- emit verdict (step 04.3) ----------
$verdict = [ordered]@{
    pass      = [bool]$pass
    breakdown = [ordered]@{
        log        = $logBreakdown
        perf       = $perfBreakdown
        screenshot = $screenshotBreakdown
    }
}

if ($Json) { $verdict | ConvertTo-Json -Depth 6 -Compress }
else { Write-Host ("VERDICT {0} - log={1} perf={2} screenshot={3}" -f $(if ($pass) { 'PASS' } else { 'FAIL' }), $logPass, $perfPass, $screenshotPass) }

exit $(if ($pass) { 0 } else { 1 })
