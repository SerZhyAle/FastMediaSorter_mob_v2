<#
.SYNOPSIS
    Fetches public-domain organic VR/3D test samples to populate
    c:\Common\test_media\3dvr\ subfolders alongside synth_vr_test_media.py.

.DESCRIPTION
    Sources (all direct HTTP, no signup, public domain or CC-BY):

      mono/            BBB 4K 60fps mono                  641 MB zip  download.blender.org
      ou/              BBB 1080p OU (above-below left)    414 MB zip  download.blender.org
      360_mono/        NASA Webb 360 equirectangular        8 MB      svs.gsfc.nasa.gov

    Blender ships these as `*.mp4.zip`; the script downloads, extracts the
    inner mp4, then deletes the zip + extract dir.

    Derives locally with ffmpeg (skipped with a warning if ffmpeg is missing):

      sbs/             BBB SBS_FULL 3840x1080   from ou/ (split halves -> hstack)
      180_mono/        NASA 180 mono            from 360_mono/ (left half crop)

    File names embed the StereoMode token expected by setup_test_media_vr.ps1
    (SBS_FULL / OU / MONO / EQUIRECT_360_MONO / EQUIRECT_180_MONO).

    Idempotent: each file is skipped if the destination already exists with
    a non-zero size. Pass -Force to redownload / re-derive.

.PARAMETER Force
    Redownload + re-derive even if destination files already exist.

.PARAMETER VrRoot
    Override the default output root (c:\Common\test_media\3dvr).

.NOTES
    File Name      : fetch_organic_vr_samples.ps1
    Prerequisite   : Internet access; ffmpeg in PATH (only for sbs/ and 180_mono/).
    Companion to   : synth_vr_test_media.py (synthetic) and setup_test_media_vr.ps1 (push to Quest).
#>

[CmdletBinding()]
param(
    [switch]$Force,
    [string]$VrRoot = "c:\Common\test_media\3dvr"
)

$ErrorActionPreference = "Stop"

# ── Sources ───────────────────────────────────────────────────────────────────
# Each entry: subfolder, target filename, source URL, expected size hint (MB).
$Downloads = @(
    @{
        Sub      = "mono"
        Name     = "BBB_MONO_2160p_60fps.mp4"
        Url      = "https://download.blender.org/demo/movies/BBB/bbb_sunflower_2160p_60fps_normal.mp4.zip"
        Mb       = 641
        ZipInner = "bbb_sunflower_2160p_60fps_normal.mp4"
    },
    @{
        Sub      = "ou"
        Name     = "BBB_OU_1080p_30fps_abl.mp4"
        Url      = "https://download.blender.org/demo/movies/BBB/bbb_sunflower_1080p_30fps_stereo_abl.mp4.zip"
        Mb       = 414
        ZipInner = "bbb_sunflower_1080p_30fps_stereo_abl.mp4"
    },
    @{
        Sub      = "360_mono"
        Name     = "NASA_Webb_EQUIRECT_360_MONO_720p.mp4"
        Url      = "https://svs.gsfc.nasa.gov/vis/a010000/a014400/a014472/360_Video_of_NASAs_Webb_Telescope_at_NASAs_Goddard_Space_Flight_Center.mp4"
        Mb       = 8
        ZipInner = $null
    }
)

# Each entry: source path (relative to VrRoot), target subfolder + name, ffmpeg args.
# Source must be one of the downloaded files above.
$Derivations = @(
    @{
        Label   = "sbs (SBS_FULL 3840x1080) from ou (above-below 1920x1080)"
        Source  = "ou\BBB_OU_1080p_30fps_abl.mp4"
        Sub     = "sbs"
        Name    = "BBB_SBS_FULL_3840x1080.mp4"
        # split -> top half = left eye, bottom half = right eye -> scale each to 1920x1080 -> hstack -> 3840x1080
        Filter  = "split[a][b];[a]crop=iw:ih/2:0:0,scale=1920:1080[lt];[b]crop=iw:ih/2:0:ih/2,scale=1920:1080[rt];[lt][rt]hstack"
        ExtraIn = @()
    },
    @{
        Label   = "180_mono (EQUIRECT_180_MONO) from 360_mono (left half)"
        Source  = "360_mono\NASA_Webb_EQUIRECT_360_MONO_720p.mp4"
        Sub     = "180_mono"
        Name    = "NASA_Webb_EQUIRECT_180_MONO_640x720.mp4"
        Filter  = "crop=iw/2:ih:0:0"
        ExtraIn = @()
    }
)

# ── Banner + checks ───────────────────────────────────────────────────────────
Write-Host "--- FastMediaSorter VR Organic Sample Fetcher ---" -ForegroundColor Cyan
Write-Host "Output root: $VrRoot" -ForegroundColor DarkGray

if (-not (Test-Path $VrRoot)) {
    Write-Host "Creating $VrRoot" -ForegroundColor DarkGray
    New-Item -Path $VrRoot -ItemType Directory -Force | Out-Null
}

# Resolve curl.exe (preferred) or fall back to Invoke-WebRequest.
$Curl = $null
$curlCmd = Get-Command "curl.exe" -ErrorAction SilentlyContinue
if ($curlCmd) {
    $Curl = $curlCmd.Source
    Write-Host "Downloader: $Curl" -ForegroundColor DarkGray
} else {
    Write-Host "Downloader: Invoke-WebRequest (curl.exe not found)" -ForegroundColor DarkGray
}

# Resolve ffmpeg.
$FfmpegExe = $null
$ffmpegCmd = Get-Command "ffmpeg" -ErrorAction SilentlyContinue
if ($ffmpegCmd) {
    $FfmpegExe = $ffmpegCmd.Source
    Write-Host "ffmpeg:     $FfmpegExe" -ForegroundColor DarkGray
} else {
    Write-Warning "ffmpeg not in PATH - sbs/ and 180_mono/ derivations will be skipped."
}

# ── Helpers ───────────────────────────────────────────────────────────────────
function Save-File {
    param(
        [string]$Url,
        [string]$OutPath,
        [int]$ExpectedMb,
        [string]$ZipInner
    )

    if ((Test-Path $OutPath) -and -not $Force) {
        $sizeMb = [math]::Round((Get-Item $OutPath).Length / 1MB, 1)
        Write-Host "  SKIP (already present, $sizeMb MB): $OutPath" -ForegroundColor DarkGray
        return $true
    }

    Write-Host "  GET ($ExpectedMb MB): $Url" -ForegroundColor White
    Write-Host "       -> $OutPath" -ForegroundColor DarkGray

    $parent = Split-Path -Parent $OutPath
    if (-not (Test-Path $parent)) {
        New-Item -Path $parent -ItemType Directory -Force | Out-Null
    }

    # If source is a zip wrapper, download to a temp .zip then extract the inner mp4.
    $isZip = -not [string]::IsNullOrEmpty($ZipInner)
    $downloadPath = if ($isZip) { "$OutPath.download.zip" } else { $OutPath }

    try {
        if ($Curl) {
            & $Curl -L --fail --progress-bar -o $downloadPath $Url
            if ($LASTEXITCODE -ne 0) { throw "curl exited with code $LASTEXITCODE" }
        } else {
            $ProgressPreference = "SilentlyContinue"
            Invoke-WebRequest -Uri $Url -OutFile $downloadPath -UseBasicParsing
        }
    } catch {
        Write-Warning "  Download failed: $($_.Exception.Message)"
        if (Test-Path $downloadPath) { Remove-Item $downloadPath -ErrorAction SilentlyContinue }
        return $false
    }

    if (-not (Test-Path $downloadPath) -or (Get-Item $downloadPath).Length -eq 0) {
        Write-Warning "  Download produced empty file: $downloadPath"
        return $false
    }

    if ($isZip) {
        $extractDir = "$OutPath.extract"
        try {
            if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
            Write-Host "  UNZIP (-> $ZipInner)" -ForegroundColor DarkGray
            Expand-Archive -Path $downloadPath -DestinationPath $extractDir -Force
            $innerPath = Join-Path $extractDir $ZipInner
            if (-not (Test-Path $innerPath)) {
                # Fall back: pick the first .mp4 inside the archive.
                $candidate = Get-ChildItem -Path $extractDir -Recurse -Filter *.mp4 | Select-Object -First 1
                if ($candidate) { $innerPath = $candidate.FullName }
            }
            if (-not (Test-Path $innerPath)) {
                throw "expected '$ZipInner' inside archive, found nothing"
            }
            Move-Item -Path $innerPath -Destination $OutPath -Force
        } catch {
            Write-Warning "  Unzip failed: $($_.Exception.Message)"
            if (Test-Path $OutPath) { Remove-Item $OutPath -ErrorAction SilentlyContinue }
            return $false
        } finally {
            if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force -ErrorAction SilentlyContinue }
            if (Test-Path $downloadPath) { Remove-Item $downloadPath -Force -ErrorAction SilentlyContinue }
        }
    }

    if (-not (Test-Path $OutPath) -or (Get-Item $OutPath).Length -eq 0) {
        Write-Warning "  Resulting file missing or empty: $OutPath"
        return $false
    }
    return $true
}

function Invoke-Ffmpeg {
    param([string]$Source, [string]$Target, [string]$Filter, [string]$Label)

    if (-not $FfmpegExe) {
        Write-Warning "  SKIP ($Label): ffmpeg not available."
        return $false
    }
    if (-not (Test-Path $Source)) {
        Write-Warning "  SKIP ($Label): source missing: $Source"
        return $false
    }
    if ((Test-Path $Target) -and -not $Force) {
        $sizeMb = [math]::Round((Get-Item $Target).Length / 1MB, 1)
        Write-Host "  SKIP (already derived, $sizeMb MB): $Target" -ForegroundColor DarkGray
        return $true
    }

    $parent = Split-Path -Parent $Target
    if (-not (Test-Path $parent)) {
        New-Item -Path $parent -ItemType Directory -Force | Out-Null
    }

    Write-Host "  DERIVE ($Label):" -ForegroundColor White
    Write-Host "       <- $Source" -ForegroundColor DarkGray
    Write-Host "       -> $Target" -ForegroundColor DarkGray

    # -y overwrite (we control idempotency above), -hide_banner, fast x264 preset, copy audio.
    & $FfmpegExe -y -hide_banner -loglevel warning -stats `
        -i $Source `
        -vf $Filter `
        -c:v libx264 -preset veryfast -crf 20 `
        -c:a copy `
        $Target

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "  ffmpeg exited with code $LASTEXITCODE"
        if (Test-Path $Target) { Remove-Item $Target -ErrorAction SilentlyContinue }
        return $false
    }
    return $true
}

# ── 1. Downloads ──────────────────────────────────────────────────────────────
Write-Host "`n[1/2] Downloading organic samples..." -ForegroundColor Yellow

$dlOk = 0
$dlFail = 0
foreach ($entry in $Downloads) {
    $sub = $entry.Sub
    $name = $entry.Name
    $url = $entry.Url
    $mb = $entry.Mb
    $zipInner = $entry.ZipInner
    $target = Join-Path (Join-Path $VrRoot $sub) $name

    if (Save-File -Url $url -OutPath $target -ExpectedMb $mb -ZipInner $zipInner) {
        $dlOk++
    } else {
        $dlFail++
    }
}

# ── 2. Derivations ────────────────────────────────────────────────────────────
Write-Host "`n[2/2] Deriving via ffmpeg..." -ForegroundColor Yellow

$drvOk = 0
$drvFail = 0
foreach ($entry in $Derivations) {
    $source = Join-Path $VrRoot $entry.Source
    $target = Join-Path (Join-Path $VrRoot $entry.Sub) $entry.Name

    if (Invoke-Ffmpeg -Source $source -Target $target -Filter $entry.Filter -Label $entry.Label) {
        $drvOk++
    } else {
        $drvFail++
    }
}

# ── Summary ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " Organic VR samples ready" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " Root:        $VrRoot" -ForegroundColor White
Write-Host " Downloaded:  $dlOk ok, $dlFail failed" -ForegroundColor White
Write-Host " Derived:     $drvOk ok, $drvFail failed" -ForegroundColor White
Write-Host ""
Write-Host " Next: run synth_vr_test_media.py to fill the remaining variants" -ForegroundColor DarkGray
Write-Host "       (360_sbs, 360_ou, 180_sbs, vr180_fisheye, cylinder)," -ForegroundColor DarkGray
Write-Host "       then push everything via setup_test_media_vr.ps1." -ForegroundColor DarkGray
Write-Host ""
