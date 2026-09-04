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

.PARAMETER ArtifactManifest
  S1984. `artifact.json` written by wear-prerelease-prepare.ps1. Present: the verdict opens by naming
  the file and version it judged, so a report cannot be read as being about another build. Absent:
  the phone sweep, unchanged.

.PARAMETER WalkResults
  S1984. `walk.json` written by wear-prerelease-walk.ps1. Present: each declared screen is listed and
  a `manual` screen - one nothing could be decided about - blocks the PASS without counting as a
  failure. Absent: the phone sweep, unchanged.

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
    [string]$ArtifactManifest,
    [string]$WalkResults,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

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

# S1700: the framework-emitted thumbnail-failure chain. mediaserver and the MediaMetadataRetriever
# JNI shim log these at E after a remote-thumbnail extraction fails; the JNI shim runs INSIDE the
# app process, so `-AppOnly` attributes its line to us and one slow remote video was enough to end
# a run at pass=false naming nothing the app can fix (2026-08-15 sweep: 21/21 Maestro, no toast,
# no crash). Unlike the entries above these are NOT unconditionally expected - they are suppressed
# only when the app's own handled-timeout marker is present in the same capture, so a genuine
# local-decode regression (which logs no NetworkVideoFrameDecoder timeout) still fails the gate.
$handledThumbnailTimeoutPattern = 'NetworkVideoFrameDecoder.*(Extraction TIMEOUT|getFrameAtTime returned null)'
$guardedThumbnailFallbacks = @(
    'getFrameAtTime: videoFrame is a NULL pointer',
    'failed to capture a video frame',
    'all codecs failed to extract frame',
    'failed to get video frame \(err -\d+\)'
) -join '|'

# S1969: libgui logs E/FrameEvents from inside the app process when the emulator's software renderer
# misses a frame release during a window transition, so `-AppOnly` attributes it to us and that single
# line ended the v033 sweep at pass=false with 22/22 Maestro, no toast, no crash and no ANR. Guarded,
# not unconditional: on a physical device a missed frame release can be a real defect, and EGL_emulation
# is written only by the emulator's GLES translator, so its presence anywhere in the capture proves the
# whole run was software-rendered. Mirrors the same guard in prerelease-log-audit.ps1.
$softwareRenderMarker = 'EGL_emulation'
$guardedEmulatorGpuFallbacks = 'addRelease: Did not find frame'

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
# Grep the file directly for the S1700 guard marker instead of spawning another search-log pass -
# the log is ~300k lines and each pass costs minutes; the count is never loaded into agent context.
$thumbnailHandled = @(Select-String -Path $LogFile -Pattern $handledThumbnailTimeoutPattern -List -ErrorAction SilentlyContinue).Count -gt 0
$softwareRendered = @(Select-String -Path $LogFile -Pattern $softwareRenderMarker -List -ErrorAction SilentlyContinue).Count -gt 0
$expectedPattern  = $expectedFallbacks
if ($thumbnailHandled) { $expectedPattern = "$expectedPattern|$guardedThumbnailFallbacks" }
if ($softwareRendered) { $expectedPattern = "$expectedPattern|$guardedEmulatorGpuFallbacks" }

$allErrors      = Get-Count -ExtraArgs @('-Errors', '-AppOnly', '-Unique')
$expectedErrors = Get-Count -ExtraArgs @('-Errors', '-AppOnly', '-Unique', '-Pattern', $expectedPattern)
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
$logBreakdown = [ordered]@{ pass = [bool]$logPass; actionableErrors = $netErrors; crashBlocks = [bool]$crashBlocks; priorCrash = [bool]$priorCrash; thumbnailTimeoutHandled = [bool]$thumbnailHandled; softwareRenderedCapture = [bool]$softwareRendered }

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
#
# Except a flow whose status is execError (S2396): that is the transport between Maestro and the
# device dropping - maestro.android.AdbSocket throwing out of the run - and it says nothing about
# the app, so counting it as a content defect makes the release verdict depend on which run the
# operator happened to look at. It is reported on its own line instead of being swallowed: an
# infrastructure failure means the suite did not finish judging that flow, which the reader must
# see. A flow object with no status field is a pre-S2396 JSON and keeps the old behaviour.
$maestroFailures = @()
$maestroInfra = @()
$maestroTotal = 0
$maestroPass = $true
if ($MaestroResults -and (Test-Path $MaestroResults)) {
    $suite = Get-Content -Raw -Path $MaestroResults | ConvertFrom-Json
    $flows = @($suite.flows)
    $maestroTotal = $flows.Count
    foreach ($flow in $flows) {
        if ($flow.pass) { continue }
        $status = if ($flow.PSObject.Properties.Name -contains 'status') { "$($flow.status)" } else { 'fail' }
        if ($status -eq 'execError') {
            $maestroInfra += "$($flow.flow)"
        } else {
            $maestroPass = $false
            $maestroFailures += "$($flow.flow)"
        }
    }
}
$maestroBreakdown = [ordered]@{ pass = [bool]$maestroPass; total = $maestroTotal; failures = $maestroFailures; infra = $maestroInfra }

# screenshot: evidence only. A present ScreensDir reports the number of captured screenshots
# but does not contribute to PASS/FAIL.
$screenshotCount = 0
if ($ScreensDir -and (Test-Path $ScreensDir)) {
    $shots = @(Get-ChildItem -Path $ScreensDir -Filter '*.png' -File -ErrorAction SilentlyContinue)
    $screenshotCount = $shots.Count
}
$screenshotBreakdown = [ordered]@{ count = $screenshotCount; evidenceOnly = $true }

# artifact: identity of the thing this run judged (S1984). Absent parameter = the phone sweep, whose
# inputs are unchanged; present = the caller recorded which file and version it installed, so the
# verdict cannot be read as being about a different day's build.
$artifactBreakdown = $null
$artifactLine = ''
if ($ArtifactManifest) {
    if (-not (Test-Path $ArtifactManifest)) {
        if ($Json) { '{"pass":false,"error":"artifact manifest not found"}' } else { Write-Host 'artifact manifest not found' }
        exit 2
    }
    try { $manifest = Get-Content -Path $ArtifactManifest -Raw | ConvertFrom-Json }
    catch {
        if ($Json) { '{"pass":false,"error":"artifact manifest unreadable"}' } else { Write-Host 'artifact manifest unreadable' }
        exit 2
    }
    $artifactBreakdown = [ordered]@{
        apk = if ($manifest.apk) { "$($manifest.apk.name) $($manifest.apk.versionName) ($($manifest.apk.versionCode))" } else { $null }
        aab = if ($manifest.aab) { "$($manifest.aab.name) $($manifest.aab.versionName) ($($manifest.aab.versionCode))" } else { $null }
    }
    $artifactLine = "ARTIFACT $($artifactBreakdown.apk) | $($artifactBreakdown.aab)"
}

# walk: per-screen outcomes of a declared walk (S1984). A `manual` screen is not a failure and not a
# pass - nothing was decided about it - so it blocks the PASS instead of being counted either way.
$walkBreakdown = $null
$manualOpen = 0
if ($WalkResults) {
    if (-not (Test-Path $WalkResults)) {
        if ($Json) { '{"pass":false,"error":"walk results not found"}' } else { Write-Host 'walk results not found' }
        exit 2
    }
    try { $walk = Get-Content -Path $WalkResults -Raw | ConvertFrom-Json }
    catch {
        if ($Json) { '{"pass":false,"error":"walk results unreadable"}' } else { Write-Host 'walk results unreadable' }
        exit 2
    }
    $walkScreens = @($walk.screens)
    $manualOpen = @($walkScreens | Where-Object { $_.outcome -eq 'manual' }).Count
    $walkBreakdown = [ordered]@{
        observed = @($walkScreens | Where-Object { $_.outcome -eq 'observed' }).Count
        failed   = @($walkScreens | Where-Object { $_.outcome -eq 'failed' }).Count
        manual   = $manualOpen
        coverage = $walk.coverage
        screens  = @($walkScreens | ForEach-Object { [ordered]@{ id = $_.id; outcome = $_.outcome; detail = $_.detail } })
    }
}

$walkPass = (-not $walkBreakdown) -or ($walkBreakdown.failed -eq 0)
$pass = $logPass -and $perfPass -and $maestroPass -and $walkPass

# ---------- emit verdict (step 04.3) ----------
$verdict = [ordered]@{
    pass      = [bool]($pass -and $manualOpen -eq 0)
    blocked   = [bool]($pass -and $manualOpen -gt 0)
    breakdown = [ordered]@{
        log        = $logBreakdown
        perf       = $perfBreakdown
        maestro    = $maestroBreakdown
        screenshot = $screenshotBreakdown
        artifact   = $artifactBreakdown
        walk       = $walkBreakdown
    }
}

if ($Json) { $verdict | ConvertTo-Json -Depth 6 -Compress }
else {
    if ($artifactLine) { Write-Host $artifactLine -ForegroundColor Cyan }
    if ($walkBreakdown) {
        foreach ($s in $walkBreakdown.screens) { Write-Host ("  screen {0,-24} {1}{2}" -f $s.id, $s.outcome, $(if ($s.detail) { " - $($s.detail)" } else { '' })) }
        # S2547: what the run OPENED, printed beside what it decided. A PASS used to be silent about
        # its own scope, so a reader could not tell a walk of the whole app from a walk of half of it.
        if ($walkBreakdown.coverage) {
            Write-Host ("  coverage {0} screen(s) walked, {1} excluded with a recorded reason" -f `
                $walkBreakdown.coverage.walked, $walkBreakdown.coverage.excluded) -ForegroundColor Cyan
        }
    }
    $word = if (-not $pass) { 'FAIL' } elseif ($manualOpen -gt 0) { 'BLOCKED - manual observation open' } else { 'PASS' }
    Write-Host ("VERDICT {0} - log={1} perf={2} maestro={3} walk={4} screenshots={5}" -f $word, $logPass, $perfPass, $maestroPass, $walkPass, $screenshotCount)
    if ($maestroInfra.Count -gt 0) {
        Write-Host ("  maestro infra (not counted as a defect, flow not judged): {0}" -f ($maestroInfra -join ', '))
    }
}

# A run with nothing broken but something unobserved is not a pass: strategic S1984 criterion 6
# refuses a PASS while a step a machine could not run is still open.
exit $(if ($pass -and $manualOpen -eq 0) { 0 } else { 1 })
