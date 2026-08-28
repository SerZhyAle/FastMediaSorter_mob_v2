<#
.SYNOPSIS
  Pre-release sweep - detailed log audit (step 04.1).

.DESCRIPTION
  Deep, format-agnostic scan of the captured run log for app-level error surfaces the
  coarse verdict aggregator (prerelease-verdict.ps1) does not enumerate. The verdict only
  produces a single error COUNT and gates on crashes; it cannot tell the operator WHICH
  errors fired, and it silently reads 0 when the log is captured in `-v time` format
  (search-log.ps1 only parses `-v threadtime`). This audit closes that gap:

    - parses both `-v time` and `-v threadtime` logcat formats,
    - keeps only app-process lines: the pid column decides, against the process ids the
      capture itself announces in `Start proc <pid>:<package>` (S1859). Only when the capture
      carries no such announcement does the tag heuristic below decide instead, and the report
      then says so - a tag denylist is enumerative, so an unlisted tag (the one-character `A`
      of Google's tiktok tracing framework) reaches the actionable list as if it were ours,
    - collapses Java/Kotlin stack-trace frames into their throwing cluster,
    - clusters E/ (and optionally W/) lines by tag + normalized message head,
    - classifies each cluster as BENIGN (known emulator/capability fallback) or ACTIONABLE,
    - separately flags user-facing error surfaces (toast / snackbar / showError),

  so red toasts seen on screen are never lost to a green machine verdict again. Each
  ACTIONABLE cluster is a /spec-draft candidate.

  This script is reporting/triage only - it adds no app runtime code and never mutates the
  catalog. Exit code reflects whether anything needs operator attention; it does not gate
  the release by itself (the verdict aggregator owns PASS/FAIL on crashes).

  Exit codes:
    0 - no actionable app-error clusters and no error toasts
    1 - actionable clusters and/or error toasts found (operator triage / spec-draft)
    2 - infrastructure abort (LogFile missing / unreadable)

.PARAMETER LogFile
  Captured logcat for the run window (`-v time` or `-v threadtime`).

.PARAMETER Package
  Base application id used to recover the app's process ids from the capture. Prefix-matched,
  so the release id, the `.debug` id and any `:sub` process are all recognised.

.PARAMETER IncludeWarnings
  Also cluster W/ lines (off by default - E/ only).

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.ps1 -LogFile temp/run.log -Json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$LogFile,
    [string]$Package = 'com.sza.fastmediasorter',
    [switch]$IncludeWarnings,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not (Test-Path $LogFile)) {
    if ($Json) { '{"ok":false,"exitCode":2,"error":"log file not found"}' } else { Write-Host 'log file not found' }
    exit 2
}

# S1859: process attribution, and the only authoritative one. Get-AppPidsFromLog owns the
# "Start proc" parsing rule for the whole repo (S1332) - the Select-String pre-filter is what
# keeps a 137 MB sweep capture out of memory, since that function takes an array.
. (Join-Path $PSScriptRoot 'lib/adb-log-filter.ps1')

$startProcLines = @(
    Select-String -Path $LogFile -Pattern ('Start proc \d+:' + [regex]::Escape($Package)) -ErrorAction SilentlyContinue |
        ForEach-Object { $_.Line }
)
$appPids = @(Get-AppPidsFromLog -Lines $startProcLines -BasePackage $Package)
$appPidSet = @{}
foreach ($appPid in $appPids) { $appPidSet[$appPid] = $true }
$attribution = if ($appPidSet.Count -gt 0) { 'pid' } else { 'heuristic' }

# The system process legitimately logs ABOUT the app: an ANR verdict and a crash header carry
# our package in their text while running under system_server's pid. This arm exempts such a
# line from the pid drop only - the tag denylists below still judge it, exactly as they did
# before pid attribution existed.
$appTextArm = '(ANR in|Process:)\s+' + [regex]::Escape($Package)

# Known-benign clusters: expected emulator / device-capability fallbacks that legitimately
# log at E/W on an emulator and must not be treated as release defects. Extend deliberately -
# every entry suppresses a real red line, so keep each one specific and commented.
$benignPatterns = @(
    'WifiRequiredException', 'Wi-Fi required',                 # SMB/network gated off without Wi-Fi on emulator
    'Cast SDK not available', 'ModuleUnavailableException',    # Play Services Cast module absent on emulator
    'DynamiteModule', 'cast.framework.dynamite',               # same Cast/Dynamite family
    'EGL_emulation', 'ro.sf.lcd_density',                      # emulator GPU / surfaceflinger noise
    'OMXNodeInstance',                                          # emulator media codec node teardown noise
    'ACodec.*DynamicANWBuffer', 'setPortMode on output to DynamicANWBuffer',  # emulator SW HEVC native-buffer fallback; video still renders
    'StagefrightMetadataRetriever', 'Failed to instantiate a MediaExtractor',  # system metadata probe on non-media/invalid files; app falls back
    'MediaScannerJNI',                                          # system MediaStore scanner errors on invalid/stub files, not the app
    'dead thread',                                             # benign Handler-after-teardown race
    'Bluetooth binder is null', 'BatteryExternalStats', 'KernelCpuSpeedReader',  # system services, not app
    'OCR engines not installed', 'UnsatisfiedLinkError loading', # expected optional-native fallback
    'NetworkReachabilityGate: no-(network|wifi)',              # offline gate, expected
    'SpellCheckerSession',                                      # IME service noise
    'BufferQueueProducer.*(cancelBuffer|requestBuffer).*no connected producer',  # S0484: app-scoped but
    # a well-known harmless SurfaceTexture teardown race (player/view surface torn down mid-frame)
    # S1391: message signatures that appear under many different process-name tags, so a tag list
    # cannot catch them.
    'Not starting debugger since process cannot load the jdwp agent',  # every debuggable process logs this
    'Failed to open rendernode',                                       # emulator has no host rendernode
    'cr_AndroidProtocolHandler.*Unable to open asset URL'              # WebView probes first; the EPUB interceptor then serves the asset
) -join '|'

# S1700: the framework-emitted thumbnail-failure chain (mediaserver FrameDecoder ->
# StagefrightMetadataRetriever -> MetadataRetrieverClient -> MediaMetadataRetrieverJNI). Kept OUT of
# $benignPatterns deliberately: it is benign only when the app already handled the extraction on its
# own budget, which the paired NetworkVideoFrameDecoder marker proves. Without that marker the same
# chain means local decoding broke and must stay actionable. Mirrors the guard in prerelease-verdict.ps1.
$guardedThumbnailChain = 'getFrameAtTime: videoFrame is a NULL pointer|failed to capture a video frame|all codecs failed to extract frame|failed to get video frame \(err -\d+\)'
$thumbnailChainHandled = @(
    Select-String -Path $LogFile -Pattern 'NetworkVideoFrameDecoder.*(Extraction TIMEOUT|getFrameAtTime returned null)' -List -ErrorAction SilentlyContinue
).Count -gt 0

# S1969: the emulator's software renderer misses a frame release during a window transition and
# libgui logs E/FrameEvents from INSIDE the app process, so pid attribution keeps it and that one
# line failed an otherwise clean sweep (v033 2026-08-22: 22/22 Maestro, 0 toasts, 0 crashes, 0 ANRs).
# Guarded like the thumbnail chain above instead of joining $benignPatterns, because on a physical
# device a missed frame release can be a real defect. EGL_emulation is written only by the emulator's
# GLES translator, so its presence anywhere in the capture proves the whole run was software-rendered;
# on a device it appears in no line at all. A time window around the FrameEvents line would cost a
# timestamp parse per line of a 105 MB capture and add nothing, since the marker is absent everywhere
# rather than merely far away.
$softwareRenderedCapture = @(
    Select-String -Path $LogFile -Pattern 'EGL_emulation' -List -ErrorAction SilentlyContinue
).Count -gt 0
$guardedEmulatorGpuNoiseTag = '^FrameEvents$'
$guardedEmulatorGpuNoise    = 'addRelease: Did not find frame'

# Foreign / other-process tags dropped entirely (same treatment as $systemTagHint): recurrent
# emulator/system/GMS/Maestro-harness noise that is never our app process, so a match can never
# hide an app defect. Non-anchored substring match on the parsed tag token (S0976). Keep each
# entry a distinctive tag name; word-boundary the short/generic ones so they do not over-match.
$foreignTagPatterns = @(
    # GMS / Play services (other process)
    'Finsky', 'GoogleApiManager', 'RoleControllerServiceImpl', 'MDDMetricsProcessor',
    'DocsApplication', 'DefaultHttpIssuer', '\bDck\b',
    # Maestro test harness (dev.mobile.maestro)
    'HCPackageInfoUtils', 'mobile\.maestro',
    # Emulator graphics / codec stack
    '\bMESA\b', 'GFXSTREAM', 'Codec2-AIDL', 'c2-service-goldfish', 'ConsumerBase',
    '\bmediaserver\b', 'LegacyGraphicsTracker', 'SurfaceSyncGroup',
    # System / window-manager / platform services (other process)
    'TransitionChain', 'IPCThreadState', 'SystemServiceRegistry', 'FeatureFlagsImplExport',
    'NsdService', 'AtomicFile', 'ShortcutService', 'JobScheduler', '\bJobStatus\b',
    'libprocessgroup', 'Nl80211Native', 'ImeLatencyLogger', 'RemoteFillService',
    'ClipboardService', 'NwpModelManager',
    # S0484 2026-07-12 sweep: PID-cross-checked against the app's "Start proc" lines, confirmed
    # none belong to our process - other-app / system_server / codec-HAL noise (S0976).
    'SmsApplication', 'TaskPersister', '\badbd\b', 'BpTransactionCompletedListener',
    'WifiMulticastLockManager', 'MediaControlProfile', 'WorkSourceUtil',
    'C2IgbaBuffer', 'Codec2-Component-Aidl', '\beptr\b', '\bsystem_server\b',
    # S1391 2026-08-04 sweep: the audit reported 49 actionable clusters on a run with zero toasts,
    # zero crashes and 17/17 Maestro green. Every cluster below was PID-checked against the app's
    # own process and belongs to the emulator image, the system server, or another installed app.
    'audio@7\.1-impl\.ranchu', 'pcmWrite',                     # emulator audio HAL
    'CellBroadcastUtils', 'ConnectivityService', 'WifiStaIfaceAidlImpl',  # system connectivity services
    'AppOpService', 'lowmemorykiller', 'JavaBinder', 'hwservicemanager', 'BLASTSyncEngine',
    'AbstractOpenableExtension', 'PropertyBackgroundShape', 'ExpressiveConceptModelManager',
    'HandwritingSuperpacksUtil',                               # Google keyboard, other process
    'DelightKLPDownloader', '\bMDD\b', 'DownloadManager',      # GMS model-download stack
    'Codec2-', 'C2Goldfish', '\bnative\b',                     # emulator codec stack
    'webview_service', 'droid\.apps\.docs', 'id\.gms\.unstable', '\.android\.chrome',
    'd\.process\.acore', 'd\.process\.media', 'ndroid\.keychain', 'id\.partnersetup',
    'ackageinstaller', 'gs\.intelligence', 'ocessService\d'    # process-name tags of other apps
) -join '|'

# Benign (tag, message-signature) pairs (S0976). Unlike $foreignTagPatterns these tags either name
# our own package (com.sza.fastmediasorter.debug) or are generic enough that a blanket drop could
# swallow a future real app error - so a cluster is benign ONLY when its tag AND message both match.
# A cluster matched here is kept and reported as benign (not dropped, not actionable).
$benignTagSignaturePairs = @(
    @{ tag = 'WindowOrganizerController'; sig = 'non-organized|not .*organized' },  # task-reorg on our task
    @{ tag = 'AppOps';                    sig = 'attributionTag' },                 # missing attributionTag, harmless
    # S1391: system_server bookkeeping for a uid it has not registered yet - fires while another app
    # installs or starts, never on our own op changes. Tag-only drop would be too broad, hence a pair.
    @{ tag = 'AppOps';                    sig = 'Trying to set mode for unknown uid' },
    @{ tag = 'PermissionService';         sig = 'WRITE_EXTERNAL_STORAGE' },         # not requested on scoped storage - expected
    @{ tag = 'PackageManager';            sig = 'alignment|mobile\.maestro' },      # Maestro alignment probe, not our error
    @{ tag = '.*';                        sig = 'Failed to query component interface for required system resources' }
)

function Test-BenignPair([string]$tag, [string]$msg) {
    foreach ($pair in $benignTagSignaturePairs) {
        if ($tag -match $pair.tag -and $msg -match $pair.sig) { return $true }
    }
    return $false
}

# User-facing error-surface markers - lines that map to a visible red toast / snackbar.
$toastPatterns = 'Toast|Snackbar|showError|showErrorMessage|notifyError|UiError|error_toast|ErrorEvent'

# App-line heuristic. threadtime carries the package column explicitly; `-v time` does not,
# so fall back to "not a known system/native tag". This keeps the audit working regardless of
# how the sweep captured the log.
$systemTagHint = '^(SurfaceFlinger|Bluetooth\w*|Battery\w*|Kernel\w*|libc|memtrack|gralloc|EGL_emulation|OMXNodeInstance|InputDispatcher|android\.os\.Debug|SELinux|cutils|audio_hw\w*|Parcel|app_process|chatty|linker\w*|Typeface|StrictMode|HwBinder|ProfileSaver|zygote\w*|SpellCheckerSession|ActivityManager|WindowManager|SurfaceControl)'

# One parsed record per non-stack-trace E/(W) line.
$lineRegexThreadtime = '^\s*\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(?<pid>\d+)\s+\d+\s+(?<lvl>[EW])\s+(?<tag>\S+?)\s*:\s*(?<msg>.*)$'
$lineRegexTime       = '^\s*\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(?<lvl>[EW])/(?<tag>[^(]+?)\(\s*(?<pid>\d+)\)\s*:\s*(?<msg>.*)$'

$wantLevels = if ($IncludeWarnings) { @('E', 'W') } else { @('E') }

$clusters    = @{}   # key -> [pscustomobject] cluster
$toastHits   = @()

function Test-StackFrame([string]$msg) {
    return ($msg -match '^\s*at\s' -or $msg -match '^\s*Caused by:' -or $msg -match '^\s*\.\.\.\s+\d+\s+more' -or $msg -match '^\s*Suppressed:')
}

foreach ($raw in [System.IO.File]::ReadLines((Resolve-Path $LogFile))) {
    $m = [regex]::Match($raw, $lineRegexThreadtime)
    if (-not $m.Success) { $m = [regex]::Match($raw, $lineRegexTime) }
    if (-not $m.Success) { continue }

    $lvl = $m.Groups['lvl'].Value
    if ($wantLevels -notcontains $lvl) { continue }

    $tag = $m.Groups['tag'].Value.Trim()
    $msg = $m.Groups['msg'].Value.Trim()

    # S1859: attribution first, and it decides on its own. A line from another process is not
    # classified at all, whatever it says - the benign allowlists below silence a cluster by
    # identity, which is a different question from whose process emitted it.
    if ($attribution -eq 'pid') {
        $linePid = [int]$m.Groups['pid'].Value
        if (-not $appPidSet.ContainsKey($linePid) -and ("$tag $msg") -notmatch $appTextArm) { continue }
    }

    if (Test-StackFrame $msg) { continue }            # stack frames fold into their cluster head
    if ($tag -match $systemTagHint) { continue }      # system/native noise, not the app
    if ($tag -match $foreignTagPatterns) { continue } # foreign/other-process noise (GMS/Maestro/emulator), S0976

    # Toast surface detection on tag or message.
    if (("$tag $msg") -match $toastPatterns) {
        $toastHits += [pscustomobject]@{ tag = $tag; msg = $msg }
    }

    $isBenign = (("$tag $msg") -match $benignPatterns) -or (Test-BenignPair $tag $msg) -or
                ($thumbnailChainHandled -and $msg -match $guardedThumbnailChain) -or
                ($softwareRenderedCapture -and $tag -match $guardedEmulatorGpuNoiseTag -and
                 $msg -match $guardedEmulatorGpuNoise)

    # Normalize the message head: drop volatile path/number tails so identical errors cluster.
    $head = ($msg -replace '\d+', '#') -replace '\s+', ' '
    if ($head.Length -gt 80) { $head = $head.Substring(0, 80) }
    $key = "$lvl|$tag|$head"

    if ($clusters.ContainsKey($key)) {
        $clusters[$key].count++
    } else {
        $clusters[$key] = [pscustomobject]@{
            level   = $lvl
            tag     = $tag
            sample  = $msg
            count   = 1
            benign  = [bool]$isBenign
        }
    }
}

$all        = $clusters.Values | Sort-Object -Property @{ Expression = 'count'; Descending = $true }
$actionable = @($all | Where-Object { -not $_.benign })
$benign     = @($all | Where-Object { $_.benign })
$toastUnique = @($toastHits | Sort-Object tag, msg -Unique)

$exit = if ($actionable.Count -gt 0 -or $toastUnique.Count -gt 0) { 1 } else { 0 }

if ($Json) {
    [ordered]@{
        ok             = $true
        exitCode       = $exit
        attribution    = $attribution
        appPidCount    = $appPidSet.Count
        softwareRendered= [bool]$softwareRenderedCapture
        actionableCount= $actionable.Count
        benignCount    = $benign.Count
        toastCount     = $toastUnique.Count
        actionable     = @($actionable | Select-Object level, tag, count, sample)
        toasts         = @($toastUnique | Select-Object tag, msg)
    } | ConvertTo-Json -Depth 5 -Compress
} else {
    Write-Host "Detailed log audit: $LogFile"
    if ($attribution -eq 'pid') {
        Write-Host ("  attribution: pid - {0} app process id(s) recovered from the capture" -f $appPidSet.Count)
    } else {
        Write-Host "  attribution: HEURISTIC - no 'Start proc $Package' announcement in this capture, so lines were kept by tag, not by process. Clusters below may belong to other processes." -ForegroundColor Yellow
    }
    Write-Host ("  actionable clusters: {0}  |  benign: {1}  |  error toasts: {2}" -f $actionable.Count, $benign.Count, $toastUnique.Count)
    if ($actionable.Count) {
        Write-Host "`n  ACTIONABLE app-error clusters (spec-draft candidates):"
        foreach ($c in $actionable) { Write-Host ("    [{0}] {1} x{2}  {3}" -f $c.level, $c.tag, $c.count, $c.sample) }
    }
    if ($toastUnique.Count) {
        Write-Host "`n  USER-FACING error surfaces (red toasts/snackbars):"
        foreach ($t in $toastUnique) { Write-Host ("    {0}: {1}" -f $t.tag, $t.msg) }
    }
    if (-not $actionable.Count -and -not $toastUnique.Count) { Write-Host "  clean - no actionable app errors or error toasts" }
}

exit $exit
