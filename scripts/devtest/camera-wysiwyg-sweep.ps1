<#
.SYNOPSIS
  S1920: sweep the in-app camera and check that each saved photo shows what the viewfinder showed.

.DESCRIPTION
  Drives an ALREADY-OPEN camera screen on a connected device. For every zoom preset (and,
  with -SweepLenses, every lens) it screenshots the live viewfinder, presses the shutter,
  waits for the produced JPEG, pulls it, and measures the field-of-view agreement between
  the two with scripts/devtest/camera_fov_compare.py.

  The camera screen is NOT opened by this script, because every camera Activity in this app
  is android:exported="false" and `am start` refuses a non-exported component. Open it by
  hand, or through the exported MainActivity action:

    adb shell am start -a com.sza.fastmediasorter.action.CAMERA_OCR_TRANSLATE
      -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity

  The expected keep-fractions are derived from the measured PreviewView bounds and the
  requested stream aspect, NOT hardcoded: with scaleType=fitCenter the stream is letterboxed
  inside the view, so a correct capture matches the letterboxed content, not the whole view.

  Point the camera at a textured scene. A blank wall carries no structure to correlate and
  the comparator refuses it rather than reporting a confident number derived from noise.

.PARAMETER DeviceId
  Target device serial. Required when more than one device is online.

.PARAMETER Zooms
  Zoom preset labels to visit, as printed on the zoom row (e.g. 1,3,5). Default: 1.

.PARAMETER Shapes
  Frame shapes to visit, in order: 4:3, 16:9, full. Default: all three, 16:9 first because it is
  the app default and therefore the shape the owner most likely shot on. A shape the device does
  not offer is reported and skipped, never silently replaced by the one already selected.

.PARAMETER SweepLenses
  Also cycle btnCameraLensSwitch once per zoom pass.

.PARAMETER StreamAspect
  Requested stream long/short ratio used to predict the letterbox. Default 1.7778 (16:9).

.PARAMETER Tolerance
  Allowed absolute deviation of each keep-fraction before a row counts FAIL. Default 0.05.

.PARAMETER OutDir
  Where screenshots, pulled photos and the report land. Default temp/S1920/sweep.

.PARAMETER KeepArtifacts
  Keep the pulled photos. Without it they are deleted locally after comparison; the device
  copy is always removed, because this runs on the owner's working phone.

.EXITCODES
  0 - every measured combination agreed with its prediction
  1 - at least one combination disagreed (the defect reproduced)
  2 - could not measure (no device, camera screen not open, no photo produced, scene too flat)
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string[]]$Zooms = @('1'),
    [ValidateSet('4:3', '16:9', 'full')]
    [string[]]$Shapes = @('16:9', '4:3', 'full'),
    [switch]$SweepLenses,
    [double]$StreamAspect = 1.7778,
    [double]$Tolerance = 0.05,
    [string]$OutDir = 'temp/S1920/sweep',
    [switch]$KeepArtifacts
)

$ErrorActionPreference = 'Stop'
# A camera JPEG is megabytes; anything smaller is a placeholder or a truncated write, never a shot.
$MinPhotoBytes = 200000
# Declared before any function runs: Open-CameraScreen is called from the opening guard, and an
# empty action there sends `am start -a  -n ` , which fails quietly and reads as "no camera".
$ReopenAction = 'com.sza.fastmediasorter.action.CAMERA_OCR_TRANSLATE'
$ReopenComponent = 'com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity'
$OcrTempDir = '/sdcard/Android/data/com.sza.fastmediasorter.debug/files/Pictures'
$StashDir = '/sdcard/Download'
$WatcherSeconds = 30
$adb = Join-Path $PSScriptRoot 'adb.ps1'
$comparator = Join-Path $PSScriptRoot 'camera_fov_compare.py'

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $full = @('-NoProfile', '-File', $adb) + $AdbArgs
    if ($DeviceId) { $full += @('-DeviceId', $DeviceId) }
    & pwsh @full 2>&1
}

function Test-CameraOpen {
    $t = Invoke-Adb @('uidump', '-Ids') | Out-String
    return ($t -match 'previewViewCamera' -and $t -match 'btnCapturePhoto')
}

function Open-CameraScreen {
    # Every capture route in this app hands control back to its caller once the shot is taken, so a
    # multi-combination sweep has to re-enter the camera between shots. The app is restarted first
    # because re-sending the action while the OCR host is still on top is a no-op - it only starts a
    # capture on fresh entry, and the sweep would then measure a screen that is not the viewfinder.
    Invoke-Adb @('stop') | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb @('shell', '-Cmd', "am start -a $ReopenAction -n $ReopenComponent") | Out-Null
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        if (Test-CameraOpen) { return $true }
    }
    return $false
}

function Resolve-Python {
    # `python3` on PATH under PowerShell is the Windows Store stub, which exits 9009 and prints
    # nothing useful; the working interpreter is the repo venv. Bash sees a different `python3`
    # via its own shim, so a name that works in one shell must not be assumed in the other.
    $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $venv = Join-Path $repoRoot '.venv/Scripts/python.exe'
    if (Test-Path $venv) { return $venv }
    foreach ($name in 'python', 'py') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd -and $cmd.Source -notmatch 'WindowsApps') { return $cmd.Source }
    }
    return $null
}

function Get-DeviceEpoch {
    $out = Invoke-Adb @('shell', '-Cmd', 'date +%s') | Out-String
    $line = ($out -split "`n" | Where-Object { $_ -match '^\s*\d{9,}\s*$' } | Select-Object -First 1)
    if ($line) { return [long]$line.Trim() }
    return 0
}

function Get-PhotoNewerThan {
    param([string[]]$Dirs, [long]$Epoch)
    # Identified by modification time, not by "the path changed since last look". A path comparison
    # calls a stale file new whenever the earlier look failed for any reason, and the sweep then
    # measures a photo from a previous day against today's viewfinder - silently, and with a
    # plausible-looking number. One `ls -t` across every sink at once, because DCIM is never empty
    # and asking directory by directory would never reach the sink the shot actually lands in.
    $globs = ($Dirs | ForEach-Object { "$_/*.jpg" }) -join ' '
    # Size is part of the identity, not a nicety: a stale zero-byte leftover in the capture sink
    # gets a fresh mtime the moment the watcher copies it, and would then out-rank the real shot
    # on time alone. Several newest entries are examined so one such file cannot hide the photo.
    $listing = Invoke-Adb @('shell', '-Cmd', "ls -t $globs 2>/dev/null | head -6") | Out-String
    $paths = @($listing -split "`n" | Where-Object { $_ -match '\.jpg\s*$' } | ForEach-Object { $_.Trim() })
    foreach ($path in $paths) {
        $stat = Invoke-Adb @('shell', '-Cmd', "stat -c '%Y %s' '$path' 2>/dev/null") | Out-String
        $sline = ($stat -split "`n" | Where-Object { $_ -match '^\s*\d+\s+\d+\s*$' } | Select-Object -First 1)
        if (-not $sline) { continue }
        $parts = $sline.Trim() -split '\s+'
        if ([long]$parts[0] -ge $Epoch -and [long]$parts[1] -ge $MinPhotoBytes) { return $path }
    }
    return $null
}

function Get-PreviewBounds {
    # Read off the device rather than assumed from the screen size: the two differ under system bars,
    # and once the view is sized to the stream they differ by the letterbox as well. Re-read after every
    # shape change, because the shape is exactly what moves these bounds.
    $tree = Invoke-Adb @('uidump', '-Ids') | Out-String
    if ($tree -notmatch 'previewViewCamera\s+tap\s+\d+,\d+\s+bounds\s+(\d+),(\d+)\.\.(\d+),(\d+)') { return $null }
    $b = @{ l = [int]$Matches[1]; t = [int]$Matches[2]; r = [int]$Matches[3]; b = [int]$Matches[4] }
    $b.w = $b.r - $b.l
    $b.h = $b.b - $b.t
    if ($b.w -le 0 -or $b.h -le 0) { return $null }
    return $b
}

function Get-UiNodesJson {
    param([switch]$WithIds)
    $verbArgs = @('uidump', '-Json')
    if ($WithIds) { $verbArgs += '-Ids' }
    $raw = Invoke-Adb $verbArgs | Out-String
    try { return (($raw | ConvertFrom-Json).data.nodes) } catch { return $null }
}

function Set-FrameShape {
    <#
      Drives the camera settings dialog to the requested frame shape. Returns $true when the shape is
      now selected, $false when the device does not offer it - the caller reports that and moves on,
      because falling through would measure the previously selected shape under the new shape's label.
    #>
    param([string]$Shape)

    Invoke-Adb @('tap-id', '-ResourceId', 'btnCameraSettings') | Out-Null
    # Polled, not slept: the dialog animates in, and a fixed 2 s wait reported "shape not offered" for a
    # dialog that was simply still arriving.
    $nodes = $null
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Seconds 1
        $nodes = Get-UiNodesJson -WithIds
        if ($nodes | Where-Object { $_.resIdShort -eq 'rowCameraAspect' }) { break }
        $nodes = $null
    }
    if (-not $nodes) { return $false }

    # The row is a dropdown whose list opens from the value field, not from the row box - tapping the
    # row's own centre does nothing. The field is identified by sharing the row's left edge, then tapped
    # by coordinate: the dialog does not scroll, so the tree read a moment ago still describes it.
    $row = @($nodes | Where-Object { $_.resIdShort -eq 'rowCameraAspect' })[0]
    $field = @($nodes | Where-Object { $_.resIdShort -eq 'sdr_value' -and $_.x1 -eq $row.x1 })
    if ($field.Count -lt 1) { return $false }
    Invoke-Adb @('tap', '-X', "$($field[0].tapX)", '-Y', "$($field[0].tapY)") | Out-Null
    Start-Sleep -Seconds 2

    $options = Get-UiNodesJson
    if (-not $options) { return $false }
    $label = $Shape
    if ($Shape -eq 'full') {
        # "4:3" and "16:9" are literals the app never translates; the full-screen entry is a translated
        # string, so it is identified by elimination rather than by name. Asking for it by an English
        # label would work on one locale and return "not offered" on every other one (S1879).
        $ratios = @($options | Where-Object { $_.label -eq '4:3' -or $_.label -eq '16:9' })
        if ($ratios.Count -eq 0) { return $false }
        $column = $ratios[0].x1
        $other = @($options | Where-Object {
                $_.x1 -eq $column -and $_.label -and $_.label -ne '4:3' -and $_.label -ne '16:9'
            })
        if ($other.Count -ne 1) { return $false }
        $label = $other[0].label
    } elseif (-not ($options | Where-Object { $_.label -eq $Shape })) {
        return $false
    }

    $tap = Invoke-Adb @('tap-label', '-Label', $label, '-Exact') | Out-String
    if ($tap -notmatch 'TAP-LABEL') { return $false }
    Start-Sleep -Seconds 1
    Invoke-Adb @('tap-id', '-ResourceId', 'btnCameraSettingsApply') | Out-Null
    Start-Sleep -Seconds 3
    return $true
}

if (-not (Test-Path $comparator)) {
    Write-Host "camera-wysiwyg-sweep: comparator missing at $comparator"
    exit 2
}
$python = Resolve-Python
if (-not $python) {
    Write-Host 'camera-wysiwyg-sweep: no usable Python found (need the repo .venv, or python/py with PIL + numpy).'
    exit 2
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not (Test-CameraOpen)) {
    if (-not (Open-CameraScreen)) {
        Write-Host 'camera-wysiwyg-sweep: could not reach the camera screen - open it by hand (see .DESCRIPTION).'
        exit 2
    }
}
$tree = Invoke-Adb @('uidump', '-Ids') | Out-String
if ($tree -notmatch 'btnCapturePhoto') {
    Write-Host 'camera-wysiwyg-sweep: no shutter button on screen - is the camera in video mode?'
    exit 2
}

# The letterboxed preview band is found in each screenshot by the comparator (--detect-content)
# rather than predicted from a declared aspect: the user can change the frame shape per lens, and a
# predicted letterbox that disagrees with the live one shifts every result by the difference.
# Against the band actually shown, a WYSIWYG capture keeps all of it - so the expectation is 1.0/1.0.
$expectFx = 1.0
$expectFy = 1.0


$remoteDirs = @(
    '/sdcard/DCIM/Camera',
    $OcrTempDir,
    $StashDir
)

$rows = @()
$lensPasses = if ($SweepLenses) { 2 } else { 1 }

foreach ($shape in $Shapes) {
    if (-not (Test-CameraOpen)) {
        if (-not (Open-CameraScreen)) {
            Write-Host "  shape $shape - SKIP: could not re-open the camera screen"
            continue
        }
    }
    if (-not (Set-FrameShape -Shape $shape)) {
        Write-Host "  shape $shape - SKIP: not offered on this device"
        continue
    }
    $bounds = Get-PreviewBounds
    if (-not $bounds) {
        Write-Host "  shape $shape - SKIP: could not read previewViewCamera bounds after the shape change"
        continue
    }
    $vl = $bounds.l; $vt = $bounds.t; $vr = $bounds.r; $vb = $bounds.b
    $viewW = $bounds.w; $viewH = $bounds.h
    # The shape reaches a local FILE NAME through the row label, and a colon is not legal in a Windows
    # path - "16:9" made every pull of that pass fail with a path Windows silently refused to create.
    $shapeTag = $shape.Replace(':', '-')
    Write-Host "camera-wysiwyg-sweep: shape $shape - preview ${viewW}x${viewH} at $vl,$vt; a correct capture keeps the whole live band"

    for ($lens = 0; $lens -lt $lensPasses; $lens++) {
        if ($lens -gt 0) {
            Invoke-Adb @('tap-id', '-ResourceId', 'btnCameraLensSwitch') | Out-Null
            Start-Sleep -Seconds 3
        }
        foreach ($zoom in $Zooms) {
            $label = "shape$shapeTag-lens$lens-zoom$zoom"
            if (-not (Test-CameraOpen)) {
                if (-not (Open-CameraScreen)) {
                    Write-Host "  $label - SKIP: could not re-open the camera screen"
                    continue
                }
                # A lens choice does not survive the restart, so re-apply it before measuring.
                for ($l = 0; $l -lt $lens; $l++) {
                    Invoke-Adb @('tap-id', '-ResourceId', 'btnCameraLensSwitch') | Out-Null
                    Start-Sleep -Seconds 3
                }
            }
            # Zoom presets carry no resource-id of their own, so they are reached by their printed
            # label - the one place tap-label is the only option (adb.ps1 prefers tap-id elsewhere).
            $tap = Invoke-Adb @('tap-label', '-Label', $zoom, '-Exact') | Out-String
            if ($tap -notmatch 'TAP-LABEL') {
                Write-Host "  $label - SKIP: zoom preset '$zoom' not on screen"
                continue
            }
            Start-Sleep -Seconds 2

            # The camera-OCR route writes its shot to an app-private sink and deletes it the instant the
            # flow finishes decoding it - measured too fast for host-side polling to win. An on-device
            # copier started before the shutter snatches it; the plain camera route, whose file is
            # permanent, is unaffected by it.
            Invoke-Adb @('shell', '-Cmd', "rm -f $StashDir/CAP_*.jpg") | Out-Null
            $watch = "nohup timeout $WatcherSeconds sh -c 'while true; do cp $OcrTempDir/CAP_*.jpg $StashDir/ 2>/dev/null; done' >/dev/null 2>&1 &"
            Invoke-Adb @('shell', '-Cmd', $watch) | Out-Null

            $shutterEpoch = Get-DeviceEpoch
            $shotOut = Invoke-Adb @('shot', '-OutDir', $OutDir) | Out-String
            if ($shotOut -notmatch 'SHOT\s+(.+\.png)') {
                Write-Host "  $label - SKIP: screenshot failed"
                continue
            }
            $shotPath = $Matches[1].Trim()

            Invoke-Adb @('tap-id', '-ResourceId', 'btnCapturePhoto') | Out-Null

            # Polled tightly on purpose: when the shot goes to the camera-OCR temp sink it is deleted
            # again the moment the flow finishes decoding it, so a one-second poll can miss the file
            # entirely and report "no photo" for a capture that did happen.
            $remote = $null
            for ($wait = 0; $wait -lt 60; $wait++) {
                Start-Sleep -Milliseconds 400
                $candidate = Get-PhotoNewerThan -Dirs $remoteDirs -Epoch $shutterEpoch
                if ($candidate) {
                    $remote = $candidate
                    # Copy it out of the app's reach at once, so the pull cannot lose a race with cleanup.
                    $stash = '/sdcard/Download/s1920_' + (Split-Path -Leaf $candidate)
                    Invoke-Adb @('shell', '-Cmd', "cp '$candidate' '$stash'") | Out-Null
                    $remote = $stash
                    break
                }
            }
            if (-not $remote) {
                Write-Host "  $label - SKIP: no new photo appeared within 20s"
                continue
            }

            $localPhoto = Join-Path $OutDir ($label + '_' + (Split-Path -Leaf $remote))
            Invoke-Adb @('pull', '-Remote', $remote, '-Local', $localPhoto) | Out-Null
            # The device copy goes regardless of the verdict: this runs on the owner's working phone.
            Invoke-Adb @('shell', '-Cmd', "rm -f '$remote'") | Out-Null
            if (-not (Test-Path $localPhoto)) {
                Write-Host "  $label - SKIP: pull failed for $remote"
                continue
            }

            $region = "$vl,$vt,$vr,$vb"
            $json = & $python $comparator --screenshot $shotPath --photo $localPhoto --region $region --content-from-photo --expect-fx $expectFx --expect-fy $expectFy --tolerance $Tolerance --label $label --json 2>&1 | Out-String
            $code = $LASTEXITCODE
            if ($code -eq 2) {
                Write-Host "  $label - SKIP: $($json.Trim())"
                continue
            }
            $row = $null
            try { $row = $json | ConvertFrom-Json } catch { $row = $null }
            if (-not $row) {
                Write-Host "  $label - SKIP: unreadable comparator output"
                continue
            }
            $rows += $row
            Write-Host ("  {0} - {1} keep=({2},{3}) expected=({4},{5}) corr={6}" -f $label, $row.verdict, $row.keep_fx, $row.keep_fy, $expectFx, $expectFy, $row.correlation)

            if (-not $KeepArtifacts) { Remove-Item $localPhoto -Force -ErrorAction SilentlyContinue }
        }
    }
}


if ($rows.Count -eq 0) {
    Write-Host 'camera-wysiwyg-sweep: nothing was measured - no verdict.'
    exit 2
}

$reportPath = Join-Path $OutDir 'sweep-report.json'
$rows | ConvertTo-Json -Depth 4 | Set-Content -Path $reportPath -Encoding UTF8
$failed = @($rows | Where-Object { $_.verdict -eq 'FAIL' })
Write-Host ''
Write-Host ("camera-wysiwyg-sweep: {0} measured, {1} disagreed. Report: {2}" -f $rows.Count, $failed.Count, $reportPath)
if ($failed.Count -gt 0) { exit 1 }
exit 0
