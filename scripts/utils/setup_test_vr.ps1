# setup_test_vr.ps1
# S0290 owner round 3 (2026-05-22): provision a full diagnostic playlist with a
# complete mono/TB/SBS coverage matrix. Where we have stable downloadable real stereo
# masters from the public web, we replace the synthetic diagnostic variants with true
# stereo content after the baseline matrix is generated. The fallback synthetic variants
# keep explicit per-eye markers so coverage still works when an external source disappears.
#
# Coverage matrix (per projection × stereo layout):
#   - Projections: 360, 180, FLAT
#   - Stereo: mono, TB (top-bottom), SBS (side-by-side)
# = 18 matrix entries + 1 extra flat-mono comparator = 19 files pushed to the headset.
#
# Pre-requisites: ffmpeg on PATH (script aborts the affected file with a warning if missing).

$ErrorActionPreference = "Stop"

# Paths
$workspaceRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$localMediaDir = Join-Path $workspaceRoot "temp\test_vr_media"
$remotePictureDir = "/sdcard/Pictures/FastMediaSorterVrTest"
$remoteMovieDir = "/sdcard/Movies/FastMediaSorterVrTest"

if (-not (Test-Path $localMediaDir)) {
    New-Item -ItemType Directory -Force -Path $localMediaDir | Out-Null
    Write-Host "Created local storage directory at $localMediaDir" -ForegroundColor Green
}

# Real-content downloads. Mono references stay as-is; selected stereo masters are downloaded
# for local QA only and then clipped / converted into the canonical playlist filenames.
$assets = @(
    @{
        # High-resolution Moraine Lake source (7742x5327, Nikon D850, CC BY-SA 4.0 by DXR).
        # Real outdoor scene - used as the FLAT mono reference.
        Url = "https://upload.wikimedia.org/wikipedia/commons/7/7f/Moraine_Lake%2C_view_from_south_shore_20240823_2.jpg"
        LocalName = "moraine_lake_flat_mono.jpg"
        Type = "Picture"
    },
    @{
        # Big Buck Bunny 1080p trailer from the official Blender peach mirror.
        # Real flat motion video - used as the FLAT mono video reference.
        Url = "https://download.blender.org/peach/trailer/trailer_1080p.mov"
        LocalName = "big_buck_bunny_flat_mono.mp4"
        Type = "Movie"
    },
    @{
        # Real 5.7K VR180 SBS sample from the public Vuze XR samples folder on Dropbox.
        # We keep the raw master under a private local name, then cut short 180 stereo
        # still/video variants from it below. Direct raw=1 link is stable under the shared
        # folder and ffprobe confirms 5760x2880 H.264/AAC as of 2026-05-22.
        Url = "https://www.dropbox.com/scl/fo/cuzh2regnr54e93bbbp7m/AHdgRn6NZv33aU8rBUd-SiY/Vuze%20XR%205.7K%203D%20VR180%20-fish%20pond.mp4?rlkey=6hyb7gi9ty818vzvm9vkwoyal&raw=1"
        LocalName = "_source_vuze_vr180_fish_pond_sbs.mp4"
        Type = "Movie"
    },
    @{
        # Public 3D SBS sample clip from RelaxVideo. Used to replace the synthetic flat
        # stereo slots with a real binocular movie / frame grab.
        Url = "http://www.relaxvideo.hu/relax/relaxvideo-demo3d-sbs.mp4"
        LocalName = "_source_magic_forest_flat_sbs.mp4"
        Type = "Movie"
    },
    @{
        # Public 360 stereo top-bottom still from the Bino examples page.
        Url = "https://bino3d.org/examples/sponza-360-tb.jpg"
        LocalName = "_source_sponza_360_tb.jpg"
        Type = "Picture"
    },
    @{
        # Public 360 stereo top-bottom video from the Bino examples page.
        Url = "https://bino3d.org/examples/rolling-marbles-360-tb.mp4"
        LocalName = "_source_rolling_marbles_360_tb.mp4"
        Type = "Movie"
    },
    @{
        # Public 360 mono motion video from the Bino examples page.
        Url = "https://bino3d.org/examples/rolling-marbles-360.mp4"
        LocalName = "_source_rolling_marbles_360_mono.mp4"
        Type = "Movie"
    },
    @{
        # Public 180 mono motion video from the Bino examples page.
        Url = "https://bino3d.org/examples/rolling-marbles-180.mp4"
        LocalName = "_source_rolling_marbles_180_mono.mp4"
        Type = "Movie"
    }
)

# Authoritative on-device playlist for the Test Immersive button.
# DiagnosticXrActivity keeps the same order in Kotlin for deterministic rotation, so this
# script validates exact sync before it downloads / regenerates anything.
$playlistMediaNames = @(
    "diagnostic_360_mono.jpg",
    "diagnostic_360_stereo_tb.jpg",
    "diagnostic_360_stereo_sbs.jpg",
    "diagnostic_180_mono.jpg",
    "diagnostic_180_stereo_tb.jpg",
    "diagnostic_180_stereo_sbs.jpg",
    "moraine_lake_flat_mono.jpg",
    "moraine_lake_flat_tb.jpg",
    "moraine_lake_flat_sbs.jpg",
    "lakeside_flat_mono.jpg",
    "video_360_mono.mp4",
    "video_360_stereo_tb.mp4",
    "video_360_stereo_sbs.mp4",
    "video_180_mono.mp4",
    "video_180_stereo_tb.mp4",
    "video_180_stereo_sbs.mp4",
    "big_buck_bunny_flat_mono.mp4",
    "big_buck_bunny_flat_tb.mp4",
    "big_buck_bunny_flat_sbs.mp4"
)

$intermediateSampleNames = @(
    "_tmp_pano180.jpg",
    "_tmp_360_stereo_tb_raw.jpg",
    "_tmp_360_stereo_tb_raw.mp4",
    "_tmp_180_stereo_sbs_raw.jpg",
    "_tmp_180_stereo_sbs_raw.mp4"
)

$legacyCleanupMediaNames = @(
    # Old local / remote artefacts that must not survive once the current 19-file playlist is active.
    ("colosseum" + "_flat_mono.jpg"),
    "video_180_lr.mp4"
)

function Get-DiagnosticActivityPlaylistFromSource {
    param([Parameter(Mandatory)] [string]$WorkspaceRoot)

    $activityPath = Join-Path $WorkspaceRoot "app_v2\src\vr\java\com\sza\fastmediasorter\ui\xr\DiagnosticXrActivity.kt"
    if (-not (Test-Path $activityPath)) {
        throw "Diagnostic XR activity not found at $activityPath"
    }

    $source = Get-Content -LiteralPath $activityPath -Raw
    $match = [regex]::Match(
        $source,
        'private\s+val\s+VR_TEST_MEDIA_ORDER\s*=\s*listOf\((?<body>.*?)\n\s*\)',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    if (-not $match.Success) {
        throw "Failed to parse VR_TEST_MEDIA_ORDER from $activityPath"
    }

    $entries = [regex]::Matches($match.Groups['body'].Value, '"([^"]+)"') | ForEach-Object {
        $_.Groups[1].Value
    }
    if ($entries.Count -eq 0) {
        throw "VR_TEST_MEDIA_ORDER in $activityPath is empty"
    }

    return @($entries)
}

function Assert-DiagnosticPlaylistSync {
    param(
        [Parameter(Mandatory)] [string[]]$ScriptPlaylist,
        [Parameter(Mandatory)] [string]$WorkspaceRoot
    )

    $activityPlaylist = Get-DiagnosticActivityPlaylistFromSource -WorkspaceRoot $WorkspaceRoot
    $maxCount = [Math]::Max($ScriptPlaylist.Count, $activityPlaylist.Count)
    $mismatches = @()
    for ($index = 0; $index -lt $maxCount; $index++) {
        $scriptName = if ($index -lt $ScriptPlaylist.Count) { $ScriptPlaylist[$index] } else { '<missing>' }
        $activityName = if ($index -lt $activityPlaylist.Count) { $activityPlaylist[$index] } else { '<missing>' }
        if ($scriptName -ne $activityName) {
            $mismatches += "[$index] script=$scriptName | activity=$activityName"
        }
    }

    if ($mismatches.Count -gt 0) {
        throw "Test Immersive playlist drift detected between setup_test_vr.ps1 and DiagnosticXrActivity.kt: $($mismatches -join '; ')"
    }

    Write-Host "Verified Test Immersive playlist sync ($($ScriptPlaylist.Count) items)." -ForegroundColor DarkGray
}

Assert-DiagnosticPlaylistSync -ScriptPlaylist $playlistMediaNames -WorkspaceRoot $workspaceRoot

foreach ($name in ($playlistMediaNames + $intermediateSampleNames + $legacyCleanupMediaNames)) {
    $path = Join-Path $localMediaDir $name
    if (Test-Path $path) {
        Write-Host "Removing stale generated sample cache: $name" -ForegroundColor DarkYellow
        Remove-Item -LiteralPath $path -Force
    }
}

# Resolve ffmpeg. Prefer PATH, fall back to WinGet links / WinGet packages root so the script
# works under `pwsh -NoProfile` (which does not pick up user PATH modifications).
function Resolve-Ffmpeg {
    $candidates = @(
        (Get-Command ffmpeg -ErrorAction SilentlyContinue),
        @{ Source = (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\ffmpeg.exe') }
    )
    foreach ($c in $candidates) {
        if ($null -eq $c) { continue }
        $src = if ($c -is [hashtable]) { $c.Source } else { $c.Source }
        if (Test-Path $src) { return @{ Source = $src } }
    }
    # Last resort: scan WinGet packages
    $wingetRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    if (Test-Path $wingetRoot) {
        $hit = Get-ChildItem -Path $wingetRoot -Recurse -Filter ffmpeg.exe -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) { return @{ Source = $hit.FullName } }
    }
    return $null
}
$ffmpegCommand = Resolve-Ffmpeg
if ($null -eq $ffmpegCommand) {
    Write-Warning "ffmpeg is not on PATH and was not found under WinGet; stereo variants will be skipped. Install via 'winget install Gyan.FFmpeg' for full coverage."
} else {
    Write-Host "Using ffmpeg at: $($ffmpegCommand.Source)" -ForegroundColor DarkGray
}

# Resolve adb similarly - pwsh -NoProfile loses the Android SDK PATH.
function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
        (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk\platform-tools\adb.exe'),
        'C:\Android\Sdk\platform-tools\adb.exe'
    )
    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    return $null
}
$adbExe = Resolve-Adb
if ($null -eq $adbExe) {
    Write-Warning "adb not found; the device-push step will be skipped (samples will still be generated locally)."
} else {
    Write-Host "Using adb at: $adbExe" -ForegroundColor DarkGray
}

function Resolve-LabelFontFile {
    $candidates = @(
        'C:\Windows\Fonts\ariblk.ttf',
        'C:\Windows\Fonts\arialbd.ttf',
        'C:\Windows\Fonts\arial.ttf'
    )
    foreach ($path in $candidates) {
        if (Test-Path $path) { return $path }
    }
    return $null
}

function Format-FfmpegFilterPath {
    param([Parameter(Mandatory)] [string]$Path)
    return ($Path -replace '\\', '/') -replace ':', '\:'
}

$labelFontFile = Resolve-LabelFontFile
if ($labelFontFile) {
    Write-Host "Using L/R label font: $labelFontFile" -ForegroundColor DarkGray
} else {
    Write-Warning "No Windows fontfile found; falling back to high-contrast boxes for L/R labels."
}

# ---------------------------------------------------------------------------
# ffmpeg helpers - every helper is idempotent (skips when target already exists)
# ---------------------------------------------------------------------------

function Invoke-Ffmpeg {
    param(
        [Parameter(Mandatory)] [string]$Description,
        [Parameter(Mandatory)] [string[]]$Arguments,
        [Parameter(Mandatory)] [string]$TargetPath
    )

    if (Test-Path $TargetPath) {
        Write-Host "$(Split-Path $TargetPath -Leaf) already exists, skipping." -ForegroundColor Yellow
        return
    }
    if ($null -eq $ffmpegCommand) {
        Write-Warning "Skipping $Description because ffmpeg is missing."
        return
    }

    Write-Host "Generating $Description.." -ForegroundColor Cyan
    try {
        & $ffmpegCommand.Source -y -loglevel error @Arguments
        if ($LASTEXITCODE -ne 0) { throw "ffmpeg exited with code $LASTEXITCODE" }
        Write-Host "  ok: $(Split-Path $TargetPath -Leaf)" -ForegroundColor Green
    } catch {
        Write-Warning "  failed: $(Split-Path $TargetPath -Leaf): $_"
    }
}

# Build a readable per-eye marker. S0291 owner round 2 feedback (2026-05-22 20:51):
# h*0.48 was ~5x too large (filled FOV). New size h*0.10 puts marker on ~10% of half-frame.
# Use explicit WORD labels ("LEFT"/"RIGHT") instead of single letters - disambiguates eye
# routing (you can tell "LEFT" vs mirrored "TFEL" at a glance) and confirms by reading.
function Build-LabelFilter {
    # S0291 owner round 4 (2026-05-22 21:51): h*0.10 still too large in headset; reduce to
    # h*0.033 (3x smaller). Clear at one-eye-close inspection, no longer dominates FOV.
    param([string]$Letter, [string]$Color)
    $text = switch ($Letter) {
        'L' { 'LEFT' }
        'R' { 'RIGHT' }
        default { $Letter }
    }
    if ($labelFontFile) {
        $font = Format-FfmpegFilterPath $labelFontFile
        return "drawtext=fontfile='${font}':text='${text}':fontcolor=${Color}:fontsize=h*0.033:x=(w-text_w)/2:y=(h-text_h)/2:box=1:boxcolor=black@0.75:boxborderw=4"
    }
    return "drawbox=x=(w-w*0.10)/2:y=(h-h*0.033)/2:w=w*0.10:h=h*0.033:color=black@0.75:t=fill,drawbox=x=(w-w*0.08)/2:y=(h-h*0.026)/2:w=w*0.08:h=h*0.026:color=${Color}@0.90:t=fill"
}

function Add-EyeLabelsStill {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [ValidateSet('TB','SBS')] [string]$Layout
    )
    $left = Build-LabelFilter 'L' 'lime'
    $right = Build-LabelFilter 'R' 'red'
    if ($Layout -eq 'TB') {
        $filter = "[0:v]crop=iw:ih/2:0:0[left];[0:v]crop=iw:ih/2:0:ih/2[right];[left]${left}[ll];[right]${right}[rr];[ll][rr]vstack=inputs=2[out]"
    } else {
        $filter = "[0:v]crop=iw/2:ih:0:0[left];[0:v]crop=iw/2:ih:iw/2:0[right];[left]${left}[ll];[right]${right}[rr];[ll][rr]hstack=inputs=2[out]"
    }
    Invoke-Ffmpeg -Description "$Layout eye labels for $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', $filter,
        '-map', '[out]',
        '-frames:v', '1',
        '-q:v', '2',
        $Target
    ) -TargetPath $Target
}

function Add-EyeLabelsVideo {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [ValidateSet('TB','SBS')] [string]$Layout
    )
    $left = Build-LabelFilter 'L' 'lime'
    $right = Build-LabelFilter 'R' 'red'
    if ($Layout -eq 'TB') {
        $filter = "[0:v]crop=iw:ih/2:0:0[left];[0:v]crop=iw:ih/2:0:ih/2[right];[left]${left}[ll];[right]${right}[rr];[ll][rr]vstack=inputs=2[out]"
    } else {
        $filter = "[0:v]crop=iw/2:ih:0:0[left];[0:v]crop=iw/2:ih:iw/2:0[right];[left]${left}[ll];[right]${right}[rr];[ll][rr]hstack=inputs=2[out]"
    }
    Invoke-Ffmpeg -Description "$Layout eye labels for $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', $filter,
        '-map', '[out]',
        '-map', '0:a?',
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '18',
        '-pix_fmt', 'yuv420p',
        '-c:a', 'copy',
        '-movflags', '+faststart',
        $Target
    ) -TargetPath $Target
}

# 1 frame still: take a mono source, copy it twice with L/R labels in each half, stack as TB or SBS.
function New-StereoStill {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [ValidateSet('TB','SBS')] [string]$Layout
    )
    $left  = Build-LabelFilter 'L' 'lime'
    $right = Build-LabelFilter 'R' 'red'
    $stack = if ($Layout -eq 'TB') { 'vstack' } else { 'hstack' }
    $filter = "[0:v]split=2[a][b];[a]${left}[la];[b]${right}[ra];[la][ra]${stack}=inputs=2[out]"
    Invoke-Ffmpeg -Description "$Layout still from $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', $filter,
        '-map', '[out]',
        '-frames:v', '1',
        '-q:v', '2',
        $Target
    ) -TargetPath $Target
}

# Mono still: just copy / crop the source to the target shape.
function New-MonoStill {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [string]$CropFilter = $null
    )
    if ($null -ne $CropFilter -and $CropFilter -ne '') {
        $args = @('-i', $Source, '-vf', $CropFilter, '-frames:v', '1', '-q:v', '2', $Target)
    } else {
        $args = @('-i', $Source, '-frames:v', '1', '-q:v', '2', $Target)
    }
    Invoke-Ffmpeg -Description "mono still $(Split-Path $Target -Leaf)" -Arguments $args -TargetPath $Target
}

# 6-second looped video from a still image. Either mono (no overlay) or TB/SBS with L/R labels.
function New-LoopedVideo {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [ValidateSet('MONO','TB','SBS')] [string]$Layout = 'MONO'
    )

    if ($Layout -eq 'MONO') {
        $filter = '-vf', 'null'
    } else {
        $left  = Build-LabelFilter 'L' 'lime'
        $right = Build-LabelFilter 'R' 'red'
        $stack = if ($Layout -eq 'TB') { 'vstack' } else { 'hstack' }
        # For SBS the hstack doubles the width. Cap each half to 2160px wide so the combined
        # output stays at ≤4320x2160 - the Quest 3 AVC decoder ceiling for H.264 High profile.
        # For TB, vstack doubles the height, which is much less likely to exceed device limits.
        $scaleStep = if ($Layout -eq 'SBS') { '[0:v]scale=min(iw\,2160):-2[scaled];[scaled]' } else { '[0:v]' }
        $filter = '-filter_complex', "${scaleStep}split=2[a][b];[a]${left}[la];[b]${right}[ra];[la][ra]${stack}=inputs=2[out]", '-map', '[out]'
    }

    Invoke-Ffmpeg -Description "$Layout video $(Split-Path $Target -Leaf)" -Arguments (@(
        '-loop', '1',
        '-framerate', '30',
        '-t', '6',
        '-i', $Source
    ) + $filter + @(
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '20',
        '-pix_fmt', 'yuv420p',
        '-movflags', '+faststart',
        $Target
    )) -TargetPath $Target
}

# Variant of New-LoopedVideo for a flat (rectangular) source where we don't need cropping.
function New-FlatVideoFromMovie {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [ValidateSet('TB','SBS')] [string]$Layout
    )
    $left  = Build-LabelFilter 'L' 'lime'
    $right = Build-LabelFilter 'R' 'red'
    $stack = if ($Layout -eq 'TB') { 'vstack' } else { 'hstack' }
    $filter = "[0:v]split=2[a][b];[a]${left}[la];[b]${right}[ra];[la][ra]${stack}=inputs=2[out]"
    Invoke-Ffmpeg -Description "$Layout video from $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', $filter,
        '-map', '[out]',
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '20',
        '-pix_fmt', 'yuv420p',
        '-movflags', '+faststart',
        $Target
    ) -TargetPath $Target
}

function New-StillFromVideo {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [string]$Timestamp = '00:00:04.000'
    )
    Invoke-Ffmpeg -Description "still from $(Split-Path $Source -Leaf)" -Arguments @(
        '-ss', $Timestamp,
        '-i', $Source,
        '-frames:v', '1',
        '-q:v', '2',
        $Target
    ) -TargetPath $Target
}

function New-VideoClip {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [string]$Timestamp = '00:00:04.000',
        [string]$Duration = '8'
    )
    Invoke-Ffmpeg -Description "clip from $(Split-Path $Source -Leaf)" -Arguments @(
        '-ss', $Timestamp,
        '-t', $Duration,
        '-i', $Source,
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '18',
        '-pix_fmt', 'yuv420p',
        '-c:a', 'aac',
        '-b:a', '160k',
        '-movflags', '+faststart',
        $Target
    ) -TargetPath $Target
}

function New-VideoClipWithTone {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [string]$Timestamp = '00:00:04.000',
        [string]$Duration = '8'
    )
    Invoke-Ffmpeg -Description "clip with diagnostic tone from $(Split-Path $Source -Leaf)" -Arguments @(
        '-ss', $Timestamp,
        '-t', $Duration,
        '-i', $Source,
        '-f', 'lavfi',
        '-t', $Duration,
        '-i', 'sine=frequency=440:sample_rate=48000',
        '-map', '0:v:0',
        '-map', '1:a:0',
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '18',
        '-pix_fmt', 'yuv420p',
        '-c:a', 'aac',
        '-b:a', '128k',
        '-af', 'volume=0.12',
        '-shortest',
        '-movflags', '+faststart',
        $Target
    ) -TargetPath $Target
}

function Convert-SbsStillToTbStill {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target
    )
    Invoke-Ffmpeg -Description "TB still from SBS $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', '[0:v]crop=iw/2:ih:0:0[left];[0:v]crop=iw/2:ih:iw/2:0[right];[left][right]vstack=inputs=2[out]',
        '-map', '[out]',
        '-frames:v', '1',
        '-q:v', '2',
        $Target
    ) -TargetPath $Target
}

function Convert-SbsVideoToTbVideo {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target
    )
    Invoke-Ffmpeg -Description "TB video from SBS $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', '[0:v]crop=iw/2:ih:0:0[left];[0:v]crop=iw/2:ih:iw/2:0[right];[left][right]vstack=inputs=2[out]',
        '-map', '[out]',
        '-map', '0:a?',
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '18',
        '-pix_fmt', 'yuv420p',
        '-c:a', 'copy',
        '-movflags', '+faststart',
        $Target
    ) -TargetPath $Target
}

function Convert-TbStillToSbsStill {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target
    )
    Invoke-Ffmpeg -Description "SBS still from TB $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', '[0:v]crop=iw:ih/2:0:0[top];[0:v]crop=iw:ih/2:0:ih/2[bottom];[top][bottom]hstack=inputs=2[out]',
        '-map', '[out]',
        '-frames:v', '1',
        '-q:v', '2',
        $Target
    ) -TargetPath $Target
}

function Convert-TbVideoToSbsVideo {
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target
    )
    Invoke-Ffmpeg -Description "SBS video from TB $(Split-Path $Source -Leaf)" -Arguments @(
        '-i', $Source,
        '-filter_complex', '[0:v]crop=iw:ih/2:0:0[top];[0:v]crop=iw:ih/2:0:ih/2[bottom];[top][bottom]hstack=inputs=2[out]',
        '-map', '[out]',
        '-map', '0:a?',
        '-c:v', 'libx264',
        '-preset', 'veryfast',
        '-crf', '18',
        '-pix_fmt', 'yuv420p',
        '-c:a', 'copy',
        '-movflags', '+faststart',
        $Target
    ) -TargetPath $Target
}

# ---------------------------------------------------------------------------
# Step 1: download the real-content references
# ---------------------------------------------------------------------------
foreach ($asset in $assets) {
    $targetPath = Join-Path $localMediaDir $asset.LocalName
    if (-not (Test-Path $targetPath)) {
        Write-Host "Downloading $($asset.LocalName) from $($asset.Url).." -ForegroundColor Cyan
        try {
            $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest -Uri $asset.Url -OutFile $targetPath -UseBasicParsing
            Write-Host "  ok: $($asset.LocalName)" -ForegroundColor Green
        } catch {
            Write-Warning "  failed: $($asset.LocalName): $_"
        }
    } else {
        Write-Host "$($asset.LocalName) already exists, skipping download." -ForegroundColor Yellow
    }
}

# ---------------------------------------------------------------------------
# Step 2: harvest the bundled diagnostic 360 mono image
# ---------------------------------------------------------------------------
$builtinSrc = Join-Path $workspaceRoot "app_v2\src\vr\res\drawable-nodpi\vr_diagnostic_360_mono.jpg"
$builtinMonoDst = Join-Path $localMediaDir "diagnostic_360_mono.jpg"
if (Test-Path $builtinSrc) {
    Copy-Item -Path $builtinSrc -Destination $builtinMonoDst -Force
    Write-Host "Copied built-in mono 360 diagnostic image (8K equirect)" -ForegroundColor Green
} else {
    Write-Warning "Built-in diagnostic image not found at $builtinSrc"
}

# ---------------------------------------------------------------------------
# Step 3: derive the 9 image variants (3 projections × 3 stereo layouts)
# ---------------------------------------------------------------------------
$pano = $builtinMonoDst             # 8192x4096 equirectangular mono
$flatSource = Join-Path $localMediaDir "moraine_lake_flat_mono.jpg"

# 360 mono is the bundled file as-is, already named correctly.
# 360 TB / SBS: stack the pano with L/R labels.
New-StereoStill -Source $pano -Target (Join-Path $localMediaDir "diagnostic_360_stereo_tb.jpg")  -Layout 'TB'
New-StereoStill -Source $pano -Target (Join-Path $localMediaDir "diagnostic_360_stereo_sbs.jpg") -Layout 'SBS'

# 180 mono: crop the right half of the pano (lon 0..180 degrees).
New-MonoStill -Source $pano -Target (Join-Path $localMediaDir "diagnostic_180_mono.jpg") -CropFilter "crop=iw/2:ih:iw/2:0"
# 180 TB / SBS: same crop, then stack with labels.
$pano180 = Join-Path $localMediaDir "_tmp_pano180.jpg"
New-MonoStill -Source $pano -Target $pano180 -CropFilter "crop=iw/2:ih:iw/2:0"
New-StereoStill -Source $pano180 -Target (Join-Path $localMediaDir "diagnostic_180_stereo_tb.jpg")  -Layout 'TB'
New-StereoStill -Source $pano180 -Target (Join-Path $localMediaDir "diagnostic_180_stereo_sbs.jpg") -Layout 'SBS'

# FLAT mono: real-content moraine lake panorama as is (already named correctly).
# FLAT TB / SBS: stack the flat source with labels.
if (Test-Path $flatSource) {
    New-StereoStill -Source $flatSource -Target (Join-Path $localMediaDir "moraine_lake_flat_tb.jpg")  -Layout 'TB'
    New-StereoStill -Source $flatSource -Target (Join-Path $localMediaDir "moraine_lake_flat_sbs.jpg") -Layout 'SBS'
}

# Keep a small honest flat-mono crop so the playlist has a second flat frame to compare.
New-MonoStill -Source $pano -Target (Join-Path $localMediaDir "lakeside_flat_mono.jpg") -CropFilter "crop=4096:2304:(iw-4096)/2:(ih-2304)/2"

# ---------------------------------------------------------------------------
# Step 4: derive the 9 video variants (same matrix as images)
# ---------------------------------------------------------------------------
# 360 mono / TB / SBS: looped pano
New-LoopedVideo -Source $pano -Target (Join-Path $localMediaDir "video_360_mono.mp4")       -Layout 'MONO'
New-LoopedVideo -Source $pano -Target (Join-Path $localMediaDir "video_360_stereo_tb.mp4")  -Layout 'TB'
New-LoopedVideo -Source $pano -Target (Join-Path $localMediaDir "video_360_stereo_sbs.mp4") -Layout 'SBS'

# 180 mono / TB / SBS: looped pano180 (cropped half)
if (Test-Path $pano180) {
    New-LoopedVideo -Source $pano180 -Target (Join-Path $localMediaDir "video_180_mono.mp4")       -Layout 'MONO'
    New-LoopedVideo -Source $pano180 -Target (Join-Path $localMediaDir "video_180_stereo_tb.mp4")  -Layout 'TB'
    New-LoopedVideo -Source $pano180 -Target (Join-Path $localMediaDir "video_180_stereo_sbs.mp4") -Layout 'SBS'
}

# FLAT mono: the BBB trailer as is (already on disk under the right name).
# FLAT TB / SBS: derive from the BBB trailer with L/R labels.
$bbb = Join-Path $localMediaDir "big_buck_bunny_flat_mono.mp4"
if (Test-Path $bbb) {
    New-FlatVideoFromMovie -Source $bbb -Target (Join-Path $localMediaDir "big_buck_bunny_flat_tb.mp4")  -Layout 'TB'
    New-FlatVideoFromMovie -Source $bbb -Target (Join-Path $localMediaDir "big_buck_bunny_flat_sbs.mp4") -Layout 'SBS'
}

# ---------------------------------------------------------------------------
# Step 4.5: replace selected synthetic stereo slots with real Internet masters
# ---------------------------------------------------------------------------

$real360TbStillSource = Join-Path $localMediaDir "_source_sponza_360_tb.jpg"
$real360TbVideoSource = Join-Path $localMediaDir "_source_rolling_marbles_360_tb.mp4"
if ((Test-Path $real360TbStillSource) -and (Test-Path $real360TbVideoSource)) {
    Write-Host "Replacing 360° stereo slots with real Bino top-bottom content.." -ForegroundColor Cyan
    foreach ($name in @(
        'diagnostic_360_stereo_tb.jpg',
        'diagnostic_360_stereo_sbs.jpg',
        'video_360_stereo_tb.mp4',
        'video_360_stereo_sbs.mp4'
    )) {
        Remove-Item (Join-Path $localMediaDir $name) -Force -ErrorAction SilentlyContinue
    }

    $still360Tb = Join-Path $localMediaDir "diagnostic_360_stereo_tb.jpg"
    $still360Sbs = Join-Path $localMediaDir "diagnostic_360_stereo_sbs.jpg"
    $video360Tb = Join-Path $localMediaDir "video_360_stereo_tb.mp4"
    $video360Sbs = Join-Path $localMediaDir "video_360_stereo_sbs.mp4"
    $rawStill360Tb = Join-Path $localMediaDir "_tmp_360_stereo_tb_raw.jpg"
    $rawVideo360Tb = Join-Path $localMediaDir "_tmp_360_stereo_tb_raw.mp4"

    Copy-Item -Path $real360TbStillSource -Destination $rawStill360Tb -Force
    Add-EyeLabelsStill -Source $rawStill360Tb -Target $still360Tb -Layout 'TB'
    Convert-TbStillToSbsStill -Source $still360Tb -Target $still360Sbs

    New-VideoClipWithTone -Source $real360TbVideoSource -Target $rawVideo360Tb -Timestamp '00:00:20.000' -Duration '8'
    if (Test-Path $rawVideo360Tb) {
        Add-EyeLabelsVideo -Source $rawVideo360Tb -Target $video360Tb -Layout 'TB'
    }
    if (Test-Path $video360Tb) {
        Convert-TbVideoToSbsVideo -Source $video360Tb -Target $video360Sbs
    }
}

$real360MonoVideoSource = Join-Path $localMediaDir "_source_rolling_marbles_360_mono.mp4"
if (Test-Path $real360MonoVideoSource) {
    Remove-Item (Join-Path $localMediaDir "video_360_mono.mp4") -Force -ErrorAction SilentlyContinue
    New-VideoClipWithTone -Source $real360MonoVideoSource -Target (Join-Path $localMediaDir "video_360_mono.mp4") -Timestamp '00:00:20.000' -Duration '8'
}

$real180MonoVideoSource = Join-Path $localMediaDir "_source_rolling_marbles_180_mono.mp4"
if (Test-Path $real180MonoVideoSource) {
    Remove-Item (Join-Path $localMediaDir "video_180_mono.mp4") -Force -ErrorAction SilentlyContinue
    New-VideoClipWithTone -Source $real180MonoVideoSource -Target (Join-Path $localMediaDir "video_180_mono.mp4") -Timestamp '00:00:20.000' -Duration '8'
}

$realVr180SbsSource = Join-Path $localMediaDir "_source_vuze_vr180_fish_pond_sbs.mp4"
if (Test-Path $realVr180SbsSource) {
    Write-Host "Replacing 180° stereo slots with real Vuze XR SBS content.." -ForegroundColor Cyan
    foreach ($name in @(
        'diagnostic_180_stereo_sbs.jpg',
        'diagnostic_180_stereo_tb.jpg',
        'video_180_stereo_sbs.mp4',
        'video_180_stereo_tb.mp4'
    )) {
        Remove-Item (Join-Path $localMediaDir $name) -Force -ErrorAction SilentlyContinue
    }

    $still180Sbs = Join-Path $localMediaDir "diagnostic_180_stereo_sbs.jpg"
    $still180Tb = Join-Path $localMediaDir "diagnostic_180_stereo_tb.jpg"
    $video180Sbs = Join-Path $localMediaDir "video_180_stereo_sbs.mp4"
    $video180Tb = Join-Path $localMediaDir "video_180_stereo_tb.mp4"
    $rawStill180Sbs = Join-Path $localMediaDir "_tmp_180_stereo_sbs_raw.jpg"
    $rawVideo180Sbs = Join-Path $localMediaDir "_tmp_180_stereo_sbs_raw.mp4"

    New-StillFromVideo -Source $realVr180SbsSource -Target $rawStill180Sbs -Timestamp '00:00:04.000'
    if (Test-Path $rawStill180Sbs) {
        Add-EyeLabelsStill -Source $rawStill180Sbs -Target $still180Sbs -Layout 'SBS'
    }
    if (Test-Path $still180Sbs) {
        Convert-SbsStillToTbStill -Source $still180Sbs -Target $still180Tb
    }

    New-VideoClip -Source $realVr180SbsSource -Target $rawVideo180Sbs -Timestamp '00:00:04.000' -Duration '8'
    if (Test-Path $rawVideo180Sbs) {
        Add-EyeLabelsVideo -Source $rawVideo180Sbs -Target $video180Sbs -Layout 'SBS'
    }
    if (Test-Path $video180Sbs) {
        Convert-SbsVideoToTbVideo -Source $video180Sbs -Target $video180Tb
    }
}

$realFlatSbsSource = Join-Path $localMediaDir "_source_magic_forest_flat_sbs.mp4"
if (Test-Path $realFlatSbsSource) {
    Write-Host "Replacing FLAT stereo slots with real Magic Forest SBS content.." -ForegroundColor Cyan
    foreach ($name in @(
        'big_buck_bunny_flat_sbs.mp4',
        'big_buck_bunny_flat_tb.mp4'
    )) {
        Remove-Item (Join-Path $localMediaDir $name) -Force -ErrorAction SilentlyContinue
    }

    $flatVideoSbs = Join-Path $localMediaDir "big_buck_bunny_flat_sbs.mp4"
    $flatVideoTb = Join-Path $localMediaDir "big_buck_bunny_flat_tb.mp4"

    New-VideoClip -Source $realFlatSbsSource -Target $flatVideoSbs -Timestamp '00:00:12.000' -Duration '8'
    if (Test-Path $flatVideoSbs) {
        Convert-SbsVideoToTbVideo -Source $flatVideoSbs -Target $flatVideoTb
    }
}

# Clean up the intermediate pano180 helper to keep temp/ tidy.
if (Test-Path $pano180) { Remove-Item $pano180 -Force }
foreach ($tmp in @("_tmp_360_stereo_tb_raw.jpg", "_tmp_360_stereo_tb_raw.mp4", "_tmp_180_stereo_sbs_raw.jpg", "_tmp_180_stereo_sbs_raw.mp4")) {
    $tmpPath = Join-Path $localMediaDir $tmp
    if (Test-Path $tmpPath) { Remove-Item $tmpPath -Force }
}

# ---------------------------------------------------------------------------
# Step 5: ADB push the full coverage set
# ---------------------------------------------------------------------------
Write-Host "Checking connected Android/Quest devices via ADB.." -ForegroundColor Cyan
$adbDevices = if ($adbExe) { & $adbExe devices } else { @() }

$deviceConnected = $false
foreach ($line in $adbDevices) {
    if ($line -match "device$") { $deviceConnected = $true; break }
}

if (-not $deviceConnected) {
    Write-Host "No device detected via ADB." -ForegroundColor Yellow
    Write-Host "Local cache: $localMediaDir" -ForegroundColor Cyan
    exit 0
}

Write-Host "Device detected - pushing harvested samples.." -ForegroundColor Green
& $adbExe shell "mkdir -p $remotePictureDir $remoteMovieDir"

# Full playlist (in the canonical display order used by Test Immersive rotation)
$deployNames = $playlistMediaNames

$remotePictureCleanupNames = @(
    $deployNames | Where-Object { $_ -match "\.(jpg|jpeg|png)$" }
)
$remotePictureCleanupNames += (("colosseum" + "_flat_mono.jpg"))

$remoteMovieCleanupNames = @(
    $deployNames | Where-Object { $_ -match "\.(mp4|mkv)$" }
)
$remoteMovieCleanupNames += "video_180_lr.mp4"

Write-Host "Removing stale remote VR test samples.." -ForegroundColor Cyan
foreach ($name in $remotePictureCleanupNames) {
    & $adbExe shell "rm -f $remotePictureDir/$name"
}
foreach ($name in $remoteMovieCleanupNames) {
    & $adbExe shell "rm -f $remoteMovieDir/$name"
}

function Get-RemoteFileSize {
    param([string]$AdbExe, [string]$RemotePath)
    $output = & $AdbExe shell "stat -c %s '$RemotePath' 2>/dev/null" 2>&1
    if ($LASTEXITCODE -ne 0) { return $null }
    $trimmed = ($output | Out-String).Trim()
    if ($trimmed -match '^\d+$') { return [long]$trimmed }
    return $null
}

foreach ($name in $deployNames) {
    $file = Get-Item (Join-Path $localMediaDir $name) -ErrorAction SilentlyContinue
    if ($null -eq $file) {
        Write-Warning "Skipping missing sample: $name"
        continue
    }
    if ($file.Extension -match "jpg|jpeg|png") {
        $remotePath = "$remotePictureDir/$($file.Name)"
    } elseif ($file.Extension -match "mp4|mkv") {
        $remotePath = "$remoteMovieDir/$($file.Name)"
    } else {
        Write-Warning "Skipping unknown extension: $name"
        continue
    }
    # S0291 owner round 2: skip push only when remote already matches local size byte-for-byte.
    # Earlier runs blindly skipped on existence; that left stale large files on the device
    # after the script regenerated smaller variants locally.
    $localSize = $file.Length
    $remoteSize = Get-RemoteFileSize -AdbExe $adbExe -RemotePath $remotePath
    if ($null -ne $remoteSize -and $remoteSize -eq $localSize) {
        Write-Host "Skipping $($file.Name) (remote already matches $localSize bytes).." -ForegroundColor DarkGray
        continue
    }
    if ($null -ne $remoteSize) {
        Write-Host "Re-pushing $($file.Name) (remote $remoteSize, local $localSize bytes).." -ForegroundColor Yellow
    } else {
        Write-Host "Pushing $($file.Name).." -ForegroundColor Cyan
    }
    & $adbExe push $file.FullName $remotePath
}

Write-Host "VR test setup complete!" -ForegroundColor Green
Write-Host "On-device tip: stereo slots now carry large L/R labels; Bino videos include a soft diagnostic tone where source audio is absent." -ForegroundColor Cyan
