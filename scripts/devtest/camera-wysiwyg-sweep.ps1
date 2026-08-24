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
  Measure every lens the switch button walks, instead of only the one the app is on.

.PARAMETER Lenses
  S1986: measure only the lenses named by a fragment of the printed label (e.g. 'Шир') or by their
  0-based position in the printed list (e.g. '0,2').
  Given as one comma-separated value. The device's whole lens list is printed at the start of every
  run, so a fragment can be copied from there. Use it to leave out a lens that cannot see anything -
  a front camera in a dark room produces black frames the comparator has to refuse one by one.

.PARAMETER StreamAspect
  Requested stream long/short ratio used to predict the letterbox. Default 1.7778 (16:9).

.PARAMETER Tolerance
  Allowed absolute deviation of each keep-fraction before a row counts FAIL. Default 0.05.

.PARAMETER OutDir
  Where screenshots, pulled photos and the report land. Default temp/S1920/sweep.

.PARAMETER Route
  S1986: which entry opens the viewfinder. 'ocr' (default) uses the exported OCR action, the only one
  `am start` can reach. 'camera' and 'video' go through the debug-only opener, because every camera
  activity is non-exported - so the plain photo entry and video mode, both named in the owner's
  report, were unreachable from a script until that hook existed.

.PARAMETER Rotations
  S1986: device rotation buckets (0=portrait, 1, 2, 3 as `Surface.ROTATION_*`) to drive through the
  debug-only broadcast hook, which pins the camera host's rotation instead of the accelerometer. The
  host cannot turn a real phone - sensor injection is refused on retail firmware and `user_rotation`
  never reaches a portrait-locked activity - so this is the only way a four-pose matrix runs without
  a pair of hands. Empty (default) leaves the bucket alone and measures the pose the phone is in.
  Debug builds only; a release build ignores the broadcast and every cell would then measure the same
  pose under four different labels, which is why a broadcast that changes nothing is reported.

.PARAMETER DiscoverRotation
  Measure and print the rotation of every cell without failing on it. Use once per device to learn
  the bucket-to-turn mapping; without it, a rotation that disagrees with the expectation is a FAIL.

.PARAMETER KeepArtifacts
  Keep the pulled photos. Without it they are deleted locally after comparison; the device
  copy is always removed, because this runs on the owner's working phone.

.PARAMETER NoPhysicalLensPin
  S1988: measure with `setPhysicalCameraId` skipped, so the sub-lens is left to the logical camera.
  Strategic 2.4 has two surviving causes that every existing measurement fits equally well - a crop
  computed against the wrong sensor rectangle, or a HAL that simply shows one field and saves another
  - and the only thing that separates them is the SAME scene measured twice, once with this switch
  and once without. A single run answers nothing on its own.

  Debug builds only. The broadcast is acknowledged with a distinctive result code, and a cell whose
  broadcast reached no receiver is reported and skipped rather than measured, because a cell that
  silently kept its pin would be read as evidence that removing the pin changed nothing.

  Sent per cell, not once per run: the sweep restarts the app between shots and the receiver, being
  registered by the resumed activity, forgets it. Each row records `lens_pinned` for that reason.

.EXITCODES
  0 - every measured combination agreed with its prediction
  1 - at least one combination disagreed (the defect reproduced)
  2 - could not measure (no device, camera screen not open, no photo produced, scene too flat)
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string[]]$Zooms = @('1'),
    [string[]]$Shapes = @('16:9', '4:3', 'full'),
    [switch]$SweepLenses,
    [string[]]$Lenses = @(),
    [double]$StreamAspect = 1.7778,
    [double]$Tolerance = 0.05,
    [string]$OutDir = 'temp/S1920/sweep',
    [switch]$KeepArtifacts,
    [string[]]$Rotations = @(),
    [switch]$DiscoverRotation,
    [switch]$NoPhysicalLensPin,
    [ValidateSet('ocr', 'camera', 'video')]
    [string]$Route = 'ocr'
)

$ErrorActionPreference = 'Stop'

function Expand-ListArg {
    <#
      S1986: `pwsh -NoProfile -File` hands every token to the binder as its own verbatim string, so
      neither list form a caller reaches for survives on its own. `-Rotations 0 1 2 3` binds the first
      value and spills the rest onto whatever positional parameters come next - measured here as
      Zooms=1, Shapes=2 - while `-Rotations 0,1,2,3` arrives as the single string "0,1,2,3", which an
      [int[]] parameter converts to 123. Both are silent: the first ran a four-pose sweep that measured
      one pose and reported PASS. Typed and validated parameters cannot catch either, because by the
      time validation sees the value the damage is in the binding, so the split happens here instead.
    #>
    param([string[]]$Values)
    $out = @()
    foreach ($value in $Values) {
        foreach ($part in ($value -split '[,;]')) {
            $trimmed = $part.Trim()
            if ($trimmed) { $out += $trimmed }
        }
    }
    return , $out
}

$Rotations = Expand-ListArg -Values $Rotations
$Zooms = Expand-ListArg -Values $Zooms
$Shapes = Expand-ListArg -Values $Shapes
$Lenses = Expand-ListArg -Values $Lenses

$badRotations = @($Rotations | Where-Object { $_ -notmatch '^[0-3]$' })
if ($badRotations.Count -gt 0) {
    Write-Host "camera-wysiwyg-sweep: -Rotations takes 0..3, got '$($badRotations -join ', ')'."
    exit 2
}
$rotationBuckets = @($Rotations | ForEach-Object { [int]$_ })
$knownShapes = @('4:3', '16:9', 'full')
$badShapes = @($Shapes | Where-Object { $knownShapes -notcontains $_ })
if ($badShapes.Count -gt 0) {
    Write-Host "camera-wysiwyg-sweep: -Shapes takes $($knownShapes -join ', '), got '$($badShapes -join ', ')'."
    exit 2
}

# A camera JPEG is megabytes; anything smaller is a placeholder or a truncated write, never a shot.
$MinPhotoBytes = 200000
# Declared before any function runs: Open-CameraScreen is called from the opening guard, and an
# empty action there sends `am start -a  -n ` , which fails quietly and reads as "no camera".
$ReopenAction = 'com.sza.fastmediasorter.action.CAMERA_OCR_TRANSLATE'
$ReopenComponent = 'com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity'
$AppPackage = 'com.sza.fastmediasorter.debug'
$RotationAction = 'com.sza.fastmediasorter.debug.CAMERA_TEST_ROTATION'
$OpenAction = 'com.sza.fastmediasorter.debug.CAMERA_TEST_OPEN'
# S1988: must equal CameraTestHooks.ACTION_LENS_PINNING.
$LensPinAction = 'com.sza.fastmediasorter.debug.CAMERA_TEST_LENS_PINNING'
# Must equal CameraTestHooks.ACK_APPLIED - the receiver's proof that it ran (see Set-RotationBucket).
$RotationAckCode = 1986
# S1986: clockwise turn the saved photo must need to line up with the viewfinder, per driven bucket.
# Measured on a Galaxy S21 with -DiscoverRotation rather than derived: the sign of a Surface rotation
# against a portrait-locked preview is exactly the kind of thing that reads as obvious and is wrong.
$RotationExpectation = @{ 0 = 0; 1 = 90; 2 = 180; 3 = 270 }
$OcrTempDir = '/sdcard/Android/data/com.sza.fastmediasorter.debug/files/Pictures'
$StashDir = '/sdcard/Download'
$WatcherSeconds = 30
# S1986: a recorded clip is megabytes too, but a far smaller floor than a photo - a two-second clip of a
# static scene compresses well below the photo floor, and reusing that floor rejected real recordings.
$MinClipBytes = 60000
$VideoSeconds = 3
$adb = Join-Path $PSScriptRoot 'adb.ps1'
$comparator = Join-Path $PSScriptRoot 'camera_fov_compare.py'
$frameExtractor = Join-Path $PSScriptRoot 'video_first_frame.py'

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
    if ($Route -eq 'ocr') {
        Invoke-Adb @('shell', '-Cmd', "am start -a $ReopenAction -n $ReopenComponent") | Out-Null
    } else {
        # S1986: the plain camera entry. Its activity is non-exported, so `am start` refuses it and the
        # debug-only opener starts it instead - after the launcher activity, because Android refuses an
        # activity launch from a background broadcast and the app would otherwise stay where it was.
        Invoke-Adb @('launch') | Out-Null
        Start-Sleep -Seconds 4
        $mode = if ($Route -eq 'video') { 'VIDEO' } else { 'PHOTO' }
        $cmd = "am broadcast -a $OpenAction --es mode $mode -p $AppPackage"
        $out = Invoke-Adb @('shell', '-Cmd', $cmd) | Out-String
        if ($out -notmatch "result=$RotationAckCode") {
            Write-Host 'camera-wysiwyg-sweep: the open broadcast reached no receiver - is this a debug build?'
            return $false
        }
    }
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        if (Test-CameraOpen) { return $true }
    }
    return $false
}

function Set-RotationBucket {
    <#
      S1986: pins the camera host's rotation bucket through the debug-only receiver. Returns $true only
      when the receiver actually answered.

      The acknowledgement is a result CODE, not the word "completed": `am broadcast` prints
      "Broadcast completed: result=0" whether a receiver ran or not - measured against a build with no
      such receiver at all - so treating that line as delivery would let every cell measure the same
      pose under four different labels. The receiver sets a distinctive code, and nothing else can.
    #>
    param([int]$Rotation)
    $cmd = "am broadcast -a $RotationAction --ei rotation $Rotation -p $AppPackage"
    $out = Invoke-Adb @('shell', '-Cmd', $cmd) | Out-String
    Start-Sleep -Seconds 2
    return ($out -match "result=$RotationAckCode")
}

function Set-LensPinning {
    <#
      S1988: turns the physical sub-lens pin off (or back on) through the debug-only receiver, and
      returns $true only when that receiver actually answered.

      Same acknowledgement discipline as Set-RotationBucket, for the same reason: `am broadcast` prints
      "Broadcast completed: result=0" whether anything listened or not, so a run against a build with no
      such receiver would produce a full matrix of ordinary pinned cells labelled as the experiment -
      which is the one reading that would answer strategic 2.4 wrongly rather than leaving it open.

      The receiver also rebinds the session, because the pin is consulted while the use cases are being
      built; without that the next photo would come from the session the switch meant to replace.
    #>
    param([bool]$Disabled)
    $flag = if ($Disabled) { 'true' } else { 'false' }
    $cmd = "am broadcast -a $LensPinAction --ez disabled $flag -p $AppPackage"
    $out = Invoke-Adb @('shell', '-Cmd', $cmd) | Out-String
    Start-Sleep -Seconds 2
    return ($out -match "result=$RotationAckCode")
}

function Get-AspectArg {
    <#
      The stream shape the UI was SET to, as long/short - the non-circular expectation. The full-screen
      selection fills the view instead of letterboxing it, so it declares 0 and the whole region counts.
    #>
    param([string]$Shape)
    switch ($Shape) {
        '16:9' { return 1.7778 }
        '4:3' { return 1.3333 }
        default { return 0 }
    }
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
    param(
        [string[]]$Dirs,
        [long]$Epoch,
        # S1986: a recorded clip is found by the same rule as a photo. It is a separate extension and a
        # separate size floor rather than a second function, so a clip can never be matched by the photo
        # search or the other way round - both land in the same sinks.
        [string]$Extension = 'jpg',
        [long]$MinBytes = $MinPhotoBytes
    )
    # Identified by modification time, not by "the path changed since last look". A path comparison
    # calls a stale file new whenever the earlier look failed for any reason, and the sweep then
    # measures a photo from a previous day against today's viewfinder - silently, and with a
    # plausible-looking number. One `ls -t` across every sink at once, because DCIM is never empty
    # and asking directory by directory would never reach the sink the shot actually lands in.
    $globs = ($Dirs | ForEach-Object { "$_/*.$Extension" }) -join ' '
    # Size is part of the identity, not a nicety: a stale zero-byte leftover in the capture sink
    # gets a fresh mtime the moment the watcher copies it, and would then out-rank the real shot
    # on time alone. Several newest entries are examined so one such file cannot hide the photo.
    $listing = Invoke-Adb @('shell', '-Cmd', "ls -t $globs 2>/dev/null | head -6") | Out-String
    $paths = @($listing -split "`n" | Where-Object { $_ -match "\.$Extension\s*`$" } | ForEach-Object { $_.Trim() })
    foreach ($path in $paths) {
        $stat = Invoke-Adb @('shell', '-Cmd', "stat -c '%Y %s' '$path' 2>/dev/null") | Out-String
        $sline = ($stat -split "`n" | Where-Object { $_ -match '^\s*\d+\s+\d+\s*$' } | Select-Object -First 1)
        if (-not $sline) { continue }
        $parts = $sline.Trim() -split '\s+'
        if ([long]$parts[0] -ge $Epoch -and [long]$parts[1] -ge $MinBytes) { return $path }
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

function Close-SettingsDialog {
    # A failed shape change must not leave the dialog on screen: the next cell would screenshot the
    # dialog instead of the viewfinder and measure it against a photo.
    Invoke-Adb @('key', '-Key', 'BACK') | Out-Null
    Start-Sleep -Seconds 1
}

function Get-LensLabel {
    # The camera screen names its current lens next to the switch button. Read it rather than counted:
    # the app REMEMBERS the lens across a restart, so "tap the switch N times from a fresh start" lands
    # on a different lens each pass - measured here as a full shape pass labelled lens0 that was in fact
    # shot on the front camera, in a dark room, producing twelve black frames under lens0/lens1 labels.
    $nodes = Get-UiNodesJson -WithIds
    $node = @($nodes | Where-Object { $_.resIdShort -eq 'cameraLensLabel' })
    if ($node.Count -lt 1) { return $null }
    return [string]$node[0].label
}

function Switch-Lens {
    <#
      One press of the switch button, waited out until the label actually changes.

      Waited, not slept: rebinding the session to another physical camera takes longer than a fixed
      pause - leaving the front camera measured three presses before the label moved, because every
      press sent during the rebind was swallowed. A sweep that assumes one press equals one lens then
      spins forever on the lens it is already on. Returns the new label, or $null if nothing moved.
    #>
    param([string]$From)
    Invoke-Adb @('tap-id', '-ResourceId', 'btnCameraLensSwitch') | Out-Null
    for ($i = 0; $i -lt 8; $i++) {
        Start-Sleep -Seconds 2
        $now = Get-LensLabel
        if ($now -and $now -ne $From) { return $now }
    }
    return $null
}

function Get-LensCycle {
    <#
      The lens labels in the order the switch button walks them, starting wherever it is now and
      stopping when a label comes round again. Sorted before use so a lens index means the same lens
      on every run of the same device, whatever lens the app happened to remember.
    #>
    $seen = @()
    $current = Get-LensLabel
    for ($i = 0; $i -lt 8; $i++) {
        if (-not $current -or $seen -contains $current) { break }
        $seen += $current
        $next = Switch-Lens -From $current
        if (-not $next) {
            # One retry before believing the cycle has ended. A press lost to a rebind looks exactly
            # like the last lens, and a cycle cut short there hides a lens from the whole run.
            $next = Switch-Lens -From $current
        }
        $current = $next
    }
    return , @($seen | Sort-Object)
}

function Select-Lens {
    param([string]$Label)
    $current = Get-LensLabel
    for ($i = 0; $i -lt 10; $i++) {
        if ($current -eq $Label) { return $true }
        $next = Switch-Lens -From $current
        # A press lost to a rebind is not the end of the road - re-read and press again. Giving up on
        # the first lost press skipped a whole lens for a run, reporting "could not select" about a
        # lens the enumeration had just walked through.
        if (-not $next) { $next = Get-LensLabel }
        if (-not $next) { return $false }
        $current = $next
    }
    return $false
}

function Set-FrameShape {
    <#
      Drives the camera settings dialog to the requested frame shape. Returns $true when the shape is
      now selected, $false when the device does not offer it - the caller reports that and moves on,
      because falling through would measure the previously selected shape under the new shape's label.
    #>
    param([string]$Shape)

    # Opened only when it is not already up. A previous cell that failed part-way leaves the dialog on
    # screen, and tapping the settings button then lands on whatever sits at those coordinates inside
    # the dialog - which is how a run reported "16:9 not offered" against a dialog that was displaying
    # 16:9 at that moment.
    if (-not (Get-UiNodesJson -WithIds | Where-Object { $_.resIdShort -eq 'rowCameraAspect' })) {
        Invoke-Adb @('tap-id', '-ResourceId', 'btnCameraSettings') | Out-Null
    }
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
    # row's own centre does nothing. The field is the `sdr_value` closest to the row's own centre line:
    # every row in this dialog carries one, they differ only in vertical position, and the value sits to
    # the RIGHT of the title rather than under it - so matching on a shared left edge, as this did
    # before, matched nothing and reported the shape as unavailable.
    $row = @($nodes | Where-Object { $_.resIdShort -eq 'rowCameraAspect' })[0]
    $field = @($nodes | Where-Object { $_.resIdShort -eq 'sdr_value' } |
            Sort-Object { [Math]::Abs($_.tapY - $row.tapY) })
    if ($field.Count -lt 1) { Close-SettingsDialog; return $false }
    Invoke-Adb @('tap', '-X', "$($field[0].tapX)", '-Y', "$($field[0].tapY)") | Out-Null
    Start-Sleep -Seconds 2

    $options = Get-UiNodesJson
    if (-not $options) { Close-SettingsDialog; return $false }
    $label = $Shape
    if ($Shape -eq 'full') {
        # "4:3" and "16:9" are literals the app never translates; the full-screen entry is a translated
        # string, so it is identified by elimination rather than by name. Asking for it by an English
        # label would work on one locale and return "not offered" on every other one (S1879).
        $ratios = @($options | Where-Object { $_.label -eq '4:3' -or $_.label -eq '16:9' })
        if ($ratios.Count -eq 0) { Close-SettingsDialog; return $false }
        $column = $ratios[0].x1
        $other = @($options | Where-Object {
                $_.x1 -eq $column -and $_.label -and $_.label -ne '4:3' -and $_.label -ne '16:9'
            })
        if ($other.Count -ne 1) { Close-SettingsDialog; return $false }
        $label = $other[0].label
    } elseif (-not ($options | Where-Object { $_.label -eq $Shape })) {
        Close-SettingsDialog; return $false
    }

    $tap = Invoke-Adb @('tap-label', '-Label', $label, '-Exact') | Out-String
    if ($tap -notmatch 'TAP-LABEL') { Close-SettingsDialog; return $false }
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
    '/sdcard/DCIM',
    '/sdcard/Movies',
    # Where the debug opener points the camera host, and therefore where a clip taken through it lands.
    '/sdcard/Android/data/com.sza.fastmediasorter.debug/files/camera_test',
    $OcrTempDir,
    $StashDir
)
$captureExtension = if ($Route -eq 'video') { 'mp4' } else { 'jpg' }
$captureMinBytes = if ($Route -eq 'video') { $MinClipBytes } else { $MinPhotoBytes }

$rows = @()
# The lenses are addressed by the label the screen prints for each, never by a number of taps on the
# switch: the app remembers the last lens across the restart this sweep does between cells, so a tap
# count means a different lens on every pass.
$lensCycle = Get-LensCycle
if ($lensCycle.Count -lt 1) {
    Write-Host 'camera-wysiwyg-sweep: could not read the lens label - is this the camera screen?'
    exit 2
}
# Wrapped in @() around the WHOLE branch, not inside it: a branch that yields exactly one lens gets
# unwrapped to a bare string, and $lensList[0] then returns its first CHARACTER - measured as a run
# that skipped all twelve cells looking for a lens named with one letter.
$lensList = @(if ($Lenses.Count -gt 0) {
    # A value is either a fragment of the printed label or its 0-based position in the printed list.
    # The positions exist because the labels are localised: on a Cyrillic device they cannot be typed
    # into a command line that passes through a shell without arriving as something else.
    @($lensCycle | Where-Object {
            $label = $_
            $index = [array]::IndexOf($lensCycle, $label)
            @($Lenses | Where-Object { $label -like "*$_*" -or $_ -eq "$index" }).Count -gt 0
        })
} elseif ($SweepLenses) {
    $lensCycle
} else {
    $lensCycle[0]
})
if ($lensList.Count -lt 1) {
    Write-Host "camera-wysiwyg-sweep: -Lenses matched none of: $($lensCycle -join ', ')"
    exit 2
}
Write-Host "camera-wysiwyg-sweep: lenses on this device: $($lensCycle -join ', '); measuring: $($lensList -join ', ')"
# -1 is the "do not touch the bucket" cell: measure the pose the phone is physically in.
$rotationList = if ($rotationBuckets.Count -gt 0) { $rotationBuckets } else { @(-1) }

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

    for ($lens = 0; $lens -lt $lensList.Count; $lens++) {
        $lensLabel = $lensList[$lens]
        foreach ($zoom in $Zooms) {
        foreach ($rot in $rotationList) {
            $label = if ($rot -lt 0) { "shape$shapeTag-lens$lens-zoom$zoom" } else { "shape$shapeTag-lens$lens-zoom$zoom-rot$rot" }
            if (-not (Test-CameraOpen)) {
                if (-not (Open-CameraScreen)) {
                    Write-Host "  $label - SKIP: could not re-open the camera screen"
                    continue
                }
            }
            # Selected per cell, by label. Whatever lens the restart left selected, this ends on the
            # one the row claims - which a tap count cannot promise.
            if (-not (Select-Lens -Label $lensLabel)) {
                Write-Host "  $label - SKIP: could not select lens '$lensLabel'"
                continue
            }
            # Zoom presets carry no resource-id of their own, so they are reached by their printed
            # label - the one place tap-label is the only option (adb.ps1 prefers tap-id elsewhere).
            $tap = Invoke-Adb @('tap-label', '-Label', $zoom, '-Exact') | Out-String
            if ($tap -notmatch 'TAP-LABEL') {
                Write-Host "  $label - SKIP: zoom preset '$zoom' not on screen"
                continue
            }
            Start-Sleep -Seconds 2

            # S1986: the bucket is pinned per cell, not once per run - Open-CameraScreen restarts the app
            # between shots and the receiver, being registered by the resumed activity, forgets it.
            if ($rot -ge 0 -and -not (Set-RotationBucket -Rotation $rot)) {
                Write-Host "  $label - SKIP: the rotation broadcast reached no receiver (release build?)"
                continue
            }

            # S1988: per cell for the same reason as the bucket above - the restart drops the flag with
            # the process. Skipped rather than measured when nobody answered: an unacknowledged cell is
            # an ordinary pinned shot, and reading it as the experiment answers 2.4 with the wrong run.
            if ($NoPhysicalLensPin -and -not (Set-LensPinning -Disabled $true)) {
                Write-Host "  $label - SKIP: the lens-pinning broadcast reached no receiver (release build?)"
                continue
            }

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
            if ($Route -eq 'video') {
                # The same button starts and stops the recording, and the file is only written on stop.
                # The pause is not politeness: an encoder writes its first keyframe some way in, and a
                # clip cut before it holds no decodable frame - which reads downstream as "no video was
                # produced" rather than as "the recording was too short to measure".
                Start-Sleep -Seconds $VideoSeconds
                Invoke-Adb @('tap-id', '-ResourceId', 'btnCapturePhoto') | Out-Null
                Start-Sleep -Seconds 2
            }

            # Polled tightly on purpose: when the shot goes to the camera-OCR temp sink it is deleted
            # again the moment the flow finishes decoding it, so a one-second poll can miss the file
            # entirely and report "no photo" for a capture that did happen.
            $remote = $null
            for ($wait = 0; $wait -lt 60; $wait++) {
                Start-Sleep -Milliseconds 400
                $candidate = Get-PhotoNewerThan -Dirs $remoteDirs -Epoch $shutterEpoch `
                    -Extension $captureExtension -MinBytes $captureMinBytes
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
                Write-Host "  $label - SKIP: no new $captureExtension appeared within 25s"
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

            # S1986: a clip is measured through one decoded frame, because a video fails differently
            # from a photo - it is turned by a matrix in the container rather than by an EXIF tag, so a
            # player can show it upright while the frames themselves are not. The frame a player would
            # show is what gets compared, which is the same thing the viewfinder screenshot is.
            $measured = $localPhoto
            if ($Route -eq 'video') {
                $framePng = [System.IO.Path]::ChangeExtension($localPhoto, '.png')
                $frameOut = & $python $frameExtractor '--video' $localPhoto '--out' $framePng '--json' 2>&1 | Out-String
                if ($LASTEXITCODE -ne 0 -or -not (Test-Path $framePng)) {
                    Write-Host "  $label - SKIP: could not read a frame from the clip - $($frameOut.Trim())"
                    continue
                }
                $measured = $framePng
            }

            $region = "$vl,$vt,$vr,$vb"
            # S1986: --content-from-aspect, not --content-from-photo. The band now comes from the shape
            # the UI was set to; deriving it from the photo let a frame cropped top and bottom define
            # its own expectation and agree with it, which is how a whole sweep reported PASS while the
            # owner was looking at a cropped picture.
            $comparatorArgs = @(
                $comparator, '--screenshot', $shotPath, '--photo', $measured, '--region', $region,
                '--content-from-aspect', (Get-AspectArg -Shape $shape),
                '--expect-fx', $expectFx, '--expect-fy', $expectFy, '--tolerance', $Tolerance,
                '--label', $label, '--json'
            )
            if ($rot -ge 0 -and -not $DiscoverRotation) {
                $comparatorArgs += @('--expect-rotation', $RotationExpectation[$rot])
            }
            $json = & $python @comparatorArgs 2>&1 | Out-String
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
            # S1988: which variable this cell was measured under, written into the row itself so a saved
            # report cannot later be read as the other half of the comparison. The photo name goes with
            # it because its pixel size is the only observable that answers strategic 5's first question
            # - the app derives its high-resolution mode from the selected photo size, so no broadcast
            # can read that flag back out.
            $row | Add-Member -NotePropertyName 'lens_pinned' -NotePropertyValue (-not $NoPhysicalLensPin)
            $row | Add-Member -NotePropertyName 'photo_file' -NotePropertyValue (Split-Path -Leaf $localPhoto)
            $rows += $row
            Write-Host ("  {0} - {1} rot={2} keep=({3},{4}) expected=({5},{6}) corr={7}" -f `
                    $label, $row.verdict, $row.rotation_deg, $row.keep_fx, $row.keep_fy, $expectFx, $expectFy, $row.correlation)

            if (-not $KeepArtifacts) { Remove-Item $localPhoto -Force -ErrorAction SilentlyContinue }
        }
        }
    }
}


# S1988: best effort - the next run force-stops the app anyway, so the flag cannot outlive this
# process. Restoring it here is for the phone the owner keeps using after the sweep ends.
if ($NoPhysicalLensPin -and (Test-CameraOpen)) { Set-LensPinning -Disabled $false | Out-Null }

if ($rows.Count -eq 0) {
    Write-Host 'camera-wysiwyg-sweep: nothing was measured - no verdict.'
    exit 2
}

$reportPath = Join-Path $OutDir 'sweep-report.json'
$rows | ConvertTo-Json -Depth 4 | Set-Content -Path $reportPath -Encoding UTF8
$failed = @($rows | Where-Object { $_.verdict -eq 'FAIL' })
Write-Host ''
$pinState = if ($NoPhysicalLensPin) { 'physical lens pin OFF (S1988 experiment)' } else { 'physical lens pin on (shipped behaviour)' }
Write-Host ("camera-wysiwyg-sweep: {0} measured, {1} disagreed, {2}. Report: {3}" -f $rows.Count, $failed.Count, $pinState, $reportPath)
if ($failed.Count -gt 0) { exit 1 }
exit 0
