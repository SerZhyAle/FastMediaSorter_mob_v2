<#
.SYNOPSIS
    Captures documentation screenshots declared in docs/docs-screenshots-manifest.jsonl from a connected device.

.DESCRIPTION
    Manual tool (S2977, made to capture for real by S3541): no gate invokes it. The agent or owner drives the
    device to the state a manifest record describes, then runs this script with that record's -ShotId. The
    script grabs the frame with screencap, downscales it for the published site, quantises it to a 256-colour
    palette with ffmpeg (a raw 1080x2424 frame is 1-2 MB; the published PNG is 100-250 KB) and writes it to
    the record's expected_path under -OutRoot.

    Without -ShotId the script lists or dry-runs the matching records and captures nothing: a frame is only
    meaningful for the one state the caller has just navigated to.

    Staging: pass -OutRoot temp/Sxxxx/staging to collect frames outside the locked content tree and copy the
    staging tree into the repository in one edit afterwards.

.PARAMETER Profile
    Manifest device_profile filter for -List / -DryRun ('all' matches every profile).

.PARAMETER Locale
    Reported only; the frame shows whatever locale the device is in.

.PARAMETER Theme
    Reported only; the frame shows whatever theme the app is in.

.PARAMETER ShotId
    The manifest shot_id to capture. Required for a capture.

.PARAMETER List
    Print every manifest record and exit.

.PARAMETER DryRun
    Print what would be captured and exit without touching the device.

.PARAMETER SetupDemoMode
    Enter System UI demo mode first (clock 12:00, full battery, notifications hidden).

.PARAMETER ExitDemoMode
    Leave System UI demo mode and exit.

.PARAMETER SetupTestMedia
    Stage the test media library through scripts/utils/setup_test_media.ps1 first.

.PARAMETER DeviceSerial
    adb serial of the target device; required when more than one device is attached.

.PARAMETER OutRoot
    Root the manifest's expected_path is resolved against. Default: the repository root.

.PARAMETER Width
    Published width in pixels. Default: 540 for phone, 800 for tablet and tv, the native width for a watch.

.PARAMETER Force
    Overwrite a shot that already exists under -OutRoot.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/capture-docs-screenshots.ps1 -ShotId getting-started.welcome-screen -DeviceSerial emulator-5554 -SetupDemoMode

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/capture-docs-screenshots.ps1 -Profile phone -DryRun

.NOTES
    Exit codes:
      0 - the requested shots were listed, dry-run or captured
      1 - the manifest is missing, the shot id is unknown, or a capture step failed
      3 - the shot already exists under -OutRoot and -Force was not given (nothing written)
#>
[CmdletBinding()]
param (
    [ValidateSet('phone', 'tablet', 'tv', 'watch', 'wear-round', 'wear-square', 'all')]
    [string]$Profile = 'all',

    [ValidateSet('en', 'ru', 'uk')]
    [string]$Locale = 'en',

    [ValidateSet('dark', 'light')]
    [string]$Theme = 'light',

    [string]$ShotId,

    [switch]$List,
    [switch]$DryRun,
    [switch]$SetupDemoMode,
    [switch]$ExitDemoMode,
    [switch]$SetupTestMedia,
    [string]$DeviceSerial,
    [string]$OutRoot,
    [int]$Width = 0,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$manifestPath = Join-Path $repoRoot 'docs/docs-screenshots-manifest.jsonl'
if ([string]::IsNullOrWhiteSpace($OutRoot)) { $OutRoot = $repoRoot }
elseif (-not [System.IO.Path]::IsPathRooted($OutRoot)) { $OutRoot = Join-Path $repoRoot $OutRoot }

if (-not (Test-Path $manifestPath)) {
    Write-Host "capture-docs-screenshots: manifest missing at $manifestPath" -ForegroundColor Red
    exit 1
}

$shots = [System.Collections.Generic.List[object]]::new()
Get-Content $manifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) { $shots.Add((ConvertFrom-Json $_)) }
}

if ($List) {
    Write-Host "=== Registered Documentation Screenshot Scenarios ===" -ForegroundColor Cyan
    Write-Host "Total registered scenarios: $($shots.Count)`n"
    foreach ($s in $shots) {
        $mark = if (Test-Path (Join-Path $repoRoot $s.expected_path)) { 'captured' } else { 'pending' }
        Write-Host "[$($s.ticket)] $($s.shot_id) ($mark)" -ForegroundColor Yellow
        Write-Host "  Profile:  $($s.device_profile)"
        Write-Host "  Path:     $($s.expected_path)"
        Write-Host "  Alt Text: $($s.alt_text)`n"
    }
    exit 0
}

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android/Sdk'))) {
        if ([string]::IsNullOrWhiteSpace($root)) { continue }
        $candidate = Join-Path $root 'platform-tools/adb.exe'
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}

function Invoke-DemoMode([string]$adbExe, [string[]]$serialArgs, [bool]$enter) {
    $demo = 'com.android.systemui.demo'
    if (-not $enter) {
        & $adbExe @serialArgs shell am broadcast -a $demo -e command exit | Out-Null
        return
    }
    & $adbExe @serialArgs shell settings put global sysui_demo_allowed 1 | Out-Null
    & $adbExe @serialArgs shell am broadcast -a $demo -e command enter | Out-Null
    & $adbExe @serialArgs shell am broadcast -a $demo -e command clock -e hhmm 1200 | Out-Null
    & $adbExe @serialArgs shell am broadcast -a $demo -e command battery -e level 100 -e plugged false | Out-Null
    & $adbExe @serialArgs shell am broadcast -a $demo -e command network -e wifi show -e level 4 | Out-Null
    & $adbExe @serialArgs shell am broadcast -a $demo -e command notifications -e visible false | Out-Null
}

$serialArgs = if ($DeviceSerial) { @('-s', $DeviceSerial) } else { @() }

if ($ExitDemoMode) {
    $adbExe = Resolve-Adb
    if (-not $adbExe) { Write-Host "capture-docs-screenshots: adb not found" -ForegroundColor Red; exit 1 }
    Invoke-DemoMode $adbExe $serialArgs $false
    Write-Host "capture-docs-screenshots: demo mode left"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($ShotId) -or $DryRun) {
    $targetShots = $shots | Where-Object {
        ($Profile -eq 'all' -or $_.device_profile -eq $Profile) -and
        ([string]::IsNullOrWhiteSpace($ShotId) -or $_.shot_id -eq $ShotId)
    }
    $pending = @($targetShots | Where-Object { -not (Test-Path (Join-Path $OutRoot $_.expected_path)) })
    Write-Host "=== Documentation Screenshot Capture Harness ===" -ForegroundColor Cyan
    Write-Host "Profile: $Profile | matching: $(@($targetShots).Count) | pending under ${OutRoot}: $($pending.Count)"
    foreach ($s in $pending) {
        Write-Host "  [DRY-RUN] $($s.shot_id) [$($s.ticket)] -> $($s.expected_path)" -ForegroundColor Gray
    }
    Write-Host "capture-docs-screenshots: nothing captured (pass -ShotId after navigating the device to that state)"
    exit 0
}

$record = $shots | Where-Object { $_.shot_id -eq $ShotId } | Select-Object -First 1
if (-not $record) {
    Write-Host "capture-docs-screenshots: unknown shot id '$ShotId'" -ForegroundColor Red
    exit 1
}

$target = Join-Path $OutRoot $record.expected_path
if ((Test-Path $target) -and -not $Force) {
    Write-Host "capture-docs-screenshots: $ShotId already exists at $target (pass -Force to overwrite)" -ForegroundColor Yellow
    exit 3
}

$adbExe = Resolve-Adb
$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $adbExe -or -not $ffmpeg) {
    Write-Host "capture-docs-screenshots: adb or ffmpeg not found" -ForegroundColor Red
    exit 1
}

if ($SetupTestMedia) {
    # Without -DeviceId the seeder writes to every attached device, the owner's phone included.
    $seedArgs = if ($DeviceSerial) { @('-DeviceId', $DeviceSerial) } else { @() }
    pwsh -NoProfile -File (Join-Path $repoRoot 'scripts/utils/setup_test_media.ps1') @seedArgs
    if ($LASTEXITCODE -ne 0) { Write-Host "capture-docs-screenshots: test media staging failed" -ForegroundColor Red; exit 1 }
}
if ($SetupDemoMode) { Invoke-DemoMode $adbExe $serialArgs $true }

if ($Width -le 0) {
    $Width = switch -Regex ($record.device_profile) {
        '^phone$' { 540 }
        '^(tablet|tv)$' { 800 }
        default { 0 }
    }
}

$rawDir = Join-Path $repoRoot 'temp/scratch/docs-screenshots-raw'
New-Item -ItemType Directory -Force $rawDir | Out-Null
$raw = Join-Path $rawDir "$ShotId.png"
$remote = '/sdcard/docs-screenshot-capture.png'

& $adbExe @serialArgs shell screencap -p $remote
if ($LASTEXITCODE -ne 0) { Write-Host "capture-docs-screenshots: screencap failed" -ForegroundColor Red; exit 1 }
& $adbExe @serialArgs pull $remote $raw | Out-Null
$pullExit = $LASTEXITCODE
& $adbExe @serialArgs shell rm -f $remote | Out-Null
if ($pullExit -ne 0 -or -not (Test-Path $raw)) { Write-Host "capture-docs-screenshots: pull failed" -ForegroundColor Red; exit 1 }

New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
$scale = if ($Width -gt 0) { "scale=${Width}:-2:flags=lanczos," } else { '' }
$filter = "${scale}split[a][b];[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a"
& $ffmpeg.Source -loglevel error -y -i $raw -vf $filter $target
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $target)) {
    Write-Host "capture-docs-screenshots: ffmpeg encode failed for $ShotId" -ForegroundColor Red
    exit 1
}

$kb = [int]((Get-Item $target).Length / 1KB)
Write-Host "capture-docs-screenshots: CAPTURED $ShotId -> $($record.expected_path) ($kb KB, width $(if ($Width -gt 0) { $Width } else { 'native' }))" -ForegroundColor Green
exit 0
