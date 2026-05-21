# setup_test_vr.ps1
# Script to harvest VR 360/180 stereoscopic & monoscopic samples from public resources
# and push them to the Meta Quest 3 headset.

$ErrorActionPreference = "Stop"

# Paths
$workspaceRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$localMediaDir = Join-Path $workspaceRoot "temp\test_vr_media"

# Ensure target directory exists
if (-not (Test-Path $localMediaDir)) {
    New-Item -ItemType Directory -Force -Path $localMediaDir | Out-Null
    Write-Host "Created local storage directory at $localMediaDir" -ForegroundColor Green
}

# Download assets configuration
$assets = @(
    @{
        Url = "https://upload.wikimedia.org/wikipedia/commons/e/e0/Fisheye_view_of_inside_the_colosseum_in_Rome%2C_Italy.jpg"
        LocalName = "colosseum_360_mono.jpg"
        Type = "Picture"
    },
    @{
        Url = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Moraine_Lake_17092005.jpg"
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

# Step 2: Copy the built-in VR diagnostic stereo image and duplicate stereo video for 180° testing
$builtinSrc = Join-Path $workspaceRoot "app_v2\src\vr\res\drawable-nodpi\vr_diagnostic_stereo_tb.jpg"
$builtinDst = Join-Path $localMediaDir "diagnostic_360_stereo_tb.jpg"
if (Test-Path $builtinSrc) {
    Copy-Item -Path $builtinSrc -Destination $builtinDst -Force
    Write-Host "Copied built-in diagnostic stereo image to harvest pool" -ForegroundColor Green
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

# Pushing files
foreach ($file in Get-ChildItem $localMediaDir) {
    if ($file.Extension -match "jpg|jpeg|png") {
        $remotePath = "/sdcard/Pictures/$($file.Name)"
        Write-Host "Pushing $($file.Name) to Pictures.." -ForegroundColor Cyan
        adb push $file.FullName $remotePath
    } elseif ($file.Extension -match "mp4|mkv") {
        $remotePath = "/sdcard/Movies/$($file.Name)"
        Write-Host "Pushing $($file.Name) to Movies.." -ForegroundColor Cyan
        adb push $file.FullName $remotePath
    }
}

Write-Host "VR test setup complete!" -ForegroundColor Green
