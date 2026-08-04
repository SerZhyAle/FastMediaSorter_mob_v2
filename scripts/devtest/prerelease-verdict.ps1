<#
.SYNOPSIS
  S0484 pre-release sweep - verdict aggregator (PASS/FAIL).

.DESCRIPTION
  Folds four signal sources into one machine verdict (research/05 + S0551):
    - log         : crashes/ANR + app errors (minus expected fallbacks) via search-log.ps1
    - perf        : per-checkpoint pass flags from the measure records (Phase 03 output)
    - maestro     : capability regression suite pass flags from maestro/run-tests.ps1 -Json
    - screenshot  : checkpoint screenshots as evidence only
  Reuses scripts/utils/search-log.ps1 - never reads a large logcat into context directly.

  Exit codes:
    0 - PASS
    1 - content FAIL (log / perf / maestro)
    2 - infrastructure abort (LogFile missing / inputs unreadable)

.PARAMETER LogFile
  Captured logcat for the run window.

.PARAMETER MetricsFile
  JSON file: array of per-checkpoint measure records (each with a `pass` field).

.PARAMETER ScreensDir
  Directory of per-checkpoint screenshots (evidence only; never gates PASS/FAIL).

.PARAMETER MaestroResults
  JSON file emitted by maestro/run-tests.ps1 -Json: { flows:[{flow,pass,log}] }.

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
    [string]$MaestroResults,
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
    'Media3OomSafeLogger', 'Upgrade reconciliation', 'ShareTarget package not installed',
    # S1391: emulator-only noise that reached the FAIL count on the 2026-08-04 sweep, where the run
    # was otherwise clean - 17/17 Maestro green, no toast, no crash, no ANR - yet the gate returned
    # pass=false on these alone. Each entry is emulator or framework behaviour the app cannot change.
    'EGL_emulation', 'eglQueryContext',                        # emulator GPU stack, no host rendernode
    'Failed to open rendernode',                               # same emulator GPU stack, once per process
    # 2026-08-04 sweep, same S1391 class: android.window.SurfaceSyncGroup (AOSP framework, absent from
    # app_v2 sources) logs a 1000 ms timeout waiting for the window transaction ack while the software
    # renderer is saturated by back-to-back Maestro flows. Nothing the app owns can ack faster.
    'SurfaceSyncGroup\(.*\) as ready',
    # search-log matches the message body, not the tag, so these carry no tag prefix.
    'Unable to open asset URL: file:///android_asset',          # WebView probes the asset first; EpubResourceContentHelper serves it ms later
    'Not starting debugger since process cannot load the jdwp agent',  # every debuggable process on the image logs this
    'isn''t requested by package'                              # Maestro grants a permission the app does not declare
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

# Strict fatal markers only, matched on raw lines. A full *:V capture carries benign
# `Exception:` / `Caused by:` lines from unrelated system processes (e.g. FeatureFlagsImplExport
# AconfigStorageReadException, SparseMappingTable "RuntimeException: Stack trace"); search-log
# -Exceptions flags those as crash blocks. Grep the file directly for true crashes / ANRs /
# native tombstones (raw match works regardless of log format; the count is not loaded into the
# agent context).
#
# Package-scoped: a full *:V capture spans the WHOLE device, so an unrelated process crashing
# or ANR-ing (Play Services indexing, systemui, etc. - all routine on a long AVD session) would
# otherwise flip this verdict to FAIL even though our app never faulted (seen 2026-07-12: "ANR in
# com.google.android.gms .. executing service .icing.service.IndexWorkerService, waited 200001ms",
# fully unrelated to FastMediaSorter). "ANR in <pkg>" names the package on the same line, so that
# is matched directly; FATAL EXCEPTION / tombstone dumps put the process name a few lines below
# (AndroidRuntime's "Process: <pkg>, PID: .." line / debuggerd's process header), so those pull a
# short trailing context window before checking for our package.
$AppPackagePattern = 'com\.sza\.fastmediasorter(\.debug)?'
$anrMatches = @(Select-String -Path $LogFile -Pattern "ANR in $AppPackagePattern" -ErrorAction SilentlyContinue)
$crashDumpMatches = @(Select-String -Path $LogFile -Pattern 'FATAL EXCEPTION|beginning of crash dump|beginning of crash' -Context 0, 4 -ErrorAction SilentlyContinue) |
    Where-Object {
        $window = @($_.Line) + @($_.Context.PostContext)
        ($window -join "`n") -match $AppPackagePattern
    }
$crashCount  = $anrMatches.Count + $crashDumpMatches.Count
$crashBlocks = ($crashCount -gt 0)

$priorCrash = (Get-Count -ExtraArgs @('-AppOnly', '-Pattern', 'PREVIOUS SESSION ENDED WITH A CRASH')) -gt 0

$logPass = ($netErrors -eq 0) -and (-not $crashBlocks) -and (-not $priorCrash)
$logBreakdown = [ordered]@{ pass = [bool]$logPass; actionableErrors = $netErrors; crashBlocks = [bool]$crashBlocks; priorCrash = [bool]$priorCrash }

# ---------- perf + maestro + screenshot signals (step 04.2) ----------
# perf: every measure record in MetricsFile must have pass=true. Missing file = no perf data
# supplied this run (perf neutral/pass); a present file with any failing checkpoint = FAIL.
$perfFailures = @()
$perfAdvisory = @()
$perfPass = $true
if ($MetricsFile -and (Test-Path $MetricsFile)) {
    $records = @(Get-Content -Raw -Path $MetricsFile | ConvertFrom-Json)
    foreach ($r in $records) {
        # Advisory checkpoints (e.g. list-scroll on an emulator, where gfxinfo janky% is
        # structurally inflated by software rendering) are reported but never gate the verdict.
        if ($r.advisory) { $perfAdvisory += "$($r.checkpoint)=$($r.measured)/$($r.limit)"; continue }
        if (-not $r.pass) { $perfPass = $false; $perfFailures += "$($r.checkpoint)=$($r.measured)/$($r.limit)" }
    }
}
$perfBreakdown = [ordered]@{ pass = [bool]$perfPass; failures = $perfFailures; advisory = $perfAdvisory }

# maestro: every suite flow in MaestroResults must have pass=true. Missing file = no suite data
# supplied this run (neutral/pass); a present file with any failing flow = FAIL.
$maestroFailures = @()
$maestroTotal = 0
$maestroPass = $true
if ($MaestroResults -and (Test-Path $MaestroResults)) {
    $suite = Get-Content -Raw -Path $MaestroResults | ConvertFrom-Json
    $flows = @($suite.flows)
    $maestroTotal = $flows.Count
    foreach ($flow in $flows) {
        if (-not $flow.pass) {
            $maestroPass = $false
            $maestroFailures += "$($flow.flow)"
        }
    }
}
$maestroBreakdown = [ordered]@{ pass = [bool]$maestroPass; total = $maestroTotal; failures = $maestroFailures }

# screenshot: evidence only. A present ScreensDir reports the number of captured screenshots
# but does not contribute to PASS/FAIL.
$screenshotCount = 0
if ($ScreensDir -and (Test-Path $ScreensDir)) {
    $shots = @(Get-ChildItem -Path $ScreensDir -Filter '*.png' -File -ErrorAction SilentlyContinue)
    $screenshotCount = $shots.Count
}
$screenshotBreakdown = [ordered]@{ count = $screenshotCount; evidenceOnly = $true }

$pass = $logPass -and $perfPass -and $maestroPass

# ---------- emit verdict (step 04.3) ----------
$verdict = [ordered]@{
    pass      = [bool]$pass
    breakdown = [ordered]@{
        log        = $logBreakdown
        perf       = $perfBreakdown
        maestro    = $maestroBreakdown
        screenshot = $screenshotBreakdown
    }
}

if ($Json) { $verdict | ConvertTo-Json -Depth 6 -Compress }
else { Write-Host ("VERDICT {0} - log={1} perf={2} maestro={3} screenshots={4}" -f $(if ($pass) { 'PASS' } else { 'FAIL' }), $logPass, $perfPass, $maestroPass, $screenshotCount) }

exit $(if ($pass) { 0 } else { 1 })
