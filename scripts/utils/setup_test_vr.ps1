# setup_test_vr.ps1
# Script to harvest VR 360/180 stereoscopic & monoscopic samples from public resources
# and push them to the Meta Quest 3 headset.

$ErrorActionPreference = "Stop"

# Paths
$workspaceRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$localMediaDir = Join-Path $workspaceRoot "temp\test_vr_media"
$remotePictureDir = "/sdcard/Pictures/FastMediaSorterVrTest"
$remoteMovieDir = "/sdcard/Movies/FastMediaSorterVrTest"

# Ensure target directory exists
if (-not (Test-Path $localMediaDir)) {
    New-Item -ItemType Directory -Force -Path $localMediaDir | Out-Null
    Write-Host "Created local storage directory at $localMediaDir" -ForegroundColor Green
}

# Download assets configuration
$assets = @(
    @{
        Url = "https://upload.wikimedia.org/wikipedia/commons/e/e0/Fisheye_view_of_inside_the_colosseum_in_Rome%2C_Italy.jpg"
        LocalName = "colosseum_flat_mono.jpg"
        Type = "Picture"
    },
    @{
        # High-resolution Moraine Lake source (7742x5327, Nikon D850, CC BY-SA 4.0 by DXR).
        # Replaces the previous 2048x1536 Wikimedia file that caused visible magnification
        # blockiness on the FLAT quad at VR viewing distance.
        Url = "https://upload.wikimedia.org/wikipedia/commons/7/7f/Moraine_Lake%2C_view_from_south_shore_20240823_2.jpg"
        LocalName = "moraine_lake_flat_mono.jpg"
        Type = "Picture"
    },
    @{
        Url = "https://github.com/google/spatial-media/raw/master/spatialmedia/resources/v2/equirectangular_mono.mp4"
        LocalName = "video_360_mono.mp4"
        Type = "Movie"
    },
    @{
        Url = "https://github.com/google/spatial-media/raw/master/spatialmedia/resources/v2/equirectangular_stereo.mp4"
        LocalName = "video_360_stereo_tb.mp4"
        Type = "Movie"
    },
    @{
        Url = "https://www.w3schools.com/html/mov_bbb.mp4"
        LocalName = "big_buck_bunny_flat_mono.mp4"
        Type = "Movie"
    }
)

# Step 1: Download external samples
foreach ($asset in $assets) {
    $targetPath = Join-Path $localMediaDir $asset.LocalName
    if (-not (Test-Path $targetPath)) {
        Write-Host "Downloading $($asset.LocalName) from $($asset.Url).." -ForegroundColor Cyan
        try {
            $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest -Uri $asset.Url -OutFile $targetPath -UseBasicParsing
            Write-Host "Successfully downloaded $($asset.LocalName)" -ForegroundColor Green
        } catch {
            Write-Warning "Failed to download $($asset.LocalName): $_"
        }
    } else {
        Write-Host "$($asset.LocalName) already exists locally, skipping download." -ForegroundColor Yellow
    }
}

# Step 2: Copy the built-in VR diagnostic 360 mono image into the harvest pool so adb push below
# delivers it under the playlist filename. The bundled asset is already monoscopic equirectangular
# (8192x4096) — no cropping needed. The stereo TB playlist slot has no built-in source after the
# Polyhaven CC0 swap; the corresponding playlist entry is silently skipped on device.
$builtinSrc = Join-Path $workspaceRoot "app_v2\src\vr\res\drawable-nodpi\vr_diagnostic_360_mono.jpg"
$builtinMonoDst = Join-Path $localMediaDir "diagnostic_360_mono.jpg"
if (Test-Path $builtinSrc) {
    Copy-Item -Path $builtinSrc -Destination $builtinMonoDst -Force
    Write-Host "Copied built-in mono 360 diagnostic image to harvest pool" -ForegroundColor Green
} else {
    Write-Warning "Built-in diagnostic image not found at $builtinSrc"
}

# Create a copy of the stereo 360 video as a stereo 180 video to test hemisphere rendering
$stereo360Video = Join-Path $localMediaDir "video_360_stereo_tb.mp4"
$stereo180Video = Join-Path $localMediaDir "video_180_stereo_tb.mp4"
if (Test-Path $stereo360Video) {
    Copy-Item -Path $stereo360Video -Destination $stereo180Video -Force
    Write-Host "Duplicated stereo video as 180° projection sample" -ForegroundColor Green
}

# Step 3: Check adb devices and deploy
Write-Host "Checking connected Android/Quest devices via ADB.." -ForegroundColor Cyan
$adbDevices = adb devices

$deviceConnected = $false
foreach ($line in $adbDevices) {
    if ($line -match "device$") {
        $deviceConnected = $true
        break
    }
}

if (-not $deviceConnected) {
    Write-Host "No Quest-3 or Android device detected via ADB." -ForegroundColor Yellow
    Write-Host "Samples are harvested locally at: $localMediaDir" -ForegroundColor Cyan
    Write-Host "You can run this script again once a device is connected to push them automatically." -ForegroundColor Cyan
    exit 0
}

Write-Host "Device detected! Pushing harvested samples to storage.." -ForegroundColor Green
adb shell "mkdir -p $remotePictureDir $remoteMovieDir"

# Pushing files
$deployNames = @(
    "diagnostic_360_mono.jpg",
    "diagnostic_360_stereo_tb.jpg",
    "moraine_lake_flat_mono.jpg",
    "colosseum_flat_mono.jpg",
    "video_360_mono.mp4",
    "video_360_stereo_tb.mp4",
    "video_180_stereo_tb.mp4",
    "big_buck_bunny_flat_mono.mp4"
)

foreach ($name in $deployNames) {
    $file = Get-Item (Join-Path $localMediaDir $name) -ErrorAction SilentlyContinue
    if ($null -eq $file) {
        Write-Warning "Skipping missing harvested sample: $name"
        continue
    }

    if ($file.Extension -match "jpg|jpeg|png") {
        $remotePath = "$remotePictureDir/$($file.Name)"
        Write-Host "Pushing $($file.Name) to VR test Pictures.." -ForegroundColor Cyan
        adb push $file.FullName $remotePath
    } elseif ($file.Extension -match "mp4|mkv") {
        $remotePath = "$remoteMovieDir/$($file.Name)"
        Write-Host "Pushing $($file.Name) to VR test Movies.." -ForegroundColor Cyan
        adb push $file.FullName $remotePath
    }
}

Write-Host "VR test setup complete!" -ForegroundColor Green
