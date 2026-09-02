# publish-libvlc-so.ps1
# ==============================================================================
# Extracts libVLC's arm64-v8a native libraries from the resolved libvlc-all AAR
# and publishes them to the permanent delivery release, so the noLegal build can
# fetch them at runtime instead of carrying 44 MB in the APK (S1971).
#
# Why a script and not a one-off manual copy: DeliverableDescriptorCatalog.kt
# compiles a SHA-256 per payload file, and that pin is the only thing standing
# between the mirror and a substituted binary. A hash cannot be compared by eye,
# so the comparison has to be the thing that gates the upload.
#
# The assets are versioned in the file NAME (arm64-v8a-libvlc-v1.so) and are
# never deleted - an already-released app keeps fetching the revision it pins.
# A rebuilt payload uploads a NEW revision, it does not clobber this one. That is
# the opposite of publish-ffmpeg-dts-aar.ps1, whose asset is build-time only.
#
# Usage (from project root):
#   pwsh -NoProfile -File scripts/builders/publish-libvlc-so.ps1 -WhatIf
#   pwsh -NoProfile -File scripts/builders/publish-libvlc-so.ps1
#
# Exit: 0 - assets uploaded (or measured, with -WhatIf)
#       1 - upload failed, or a measured hash/size did not match the pin
#       2 - could not verify: AAR not resolved in the Gradle cache, an expected
#           jni entry is absent, or gh CLI not found
# ==============================================================================

[CmdletBinding()]
param(
    [string] $Version = '3.7.5',
    [string] $Abi = 'arm64-v8a',
    [string] $Tag = 'delivery-so-v1',
    [string] $Rev = 'v1',
    [switch] $WhatIf
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot | Split-Path | Split-Path

# The pins compiled into DeliverableDescriptorCatalog.kt. Keep both sides equal:
# a payload whose hash differs from the pin fails verification on every device,
# and the device is a far more expensive place to discover it than this script.
$expected = [ordered]@{
    'libvlc.so'    = @{ Size = 46087168L; Sha256 = '5ab99a7cb793a0df95d60551f04f254c6d8314dc26d4b0ef5b4feac5a2f3615f' }
    'libvlcjni.so' = @{ Size = 94440L; Sha256 = 'b2137913ed5ec1dd2a2cc07565534c854ec4d32445b1d28f2544d0de252116f2' }
}

$cacheRoot = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.videolan.android\libvlc-all\$Version"
if (-not (Test-Path -LiteralPath $cacheRoot)) {
    Write-Error "libvlc-all $Version is not resolved in the Gradle cache ($cacheRoot). Run a noLegal build first." -ErrorAction Continue
    exit 2
}

$aar = Get-ChildItem -LiteralPath $cacheRoot -Recurse -Filter "libvlc-all-$Version.aar" | Select-Object -First 1
if (-not $aar) {
    Write-Error "No libvlc-all-$Version.aar under $cacheRoot." -ErrorAction Continue
    exit 2
}

$ghExe = $null
if (-not $WhatIf) {
    # gh is installed but is absent from PATH in both shells here, so resolve it
    # explicitly rather than failing with a misleading "not installed".
    $ghExe = (Get-Command gh -ErrorAction SilentlyContinue).Source
    if (-not $ghExe) {
        $fallback = Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe'
        if (Test-Path -LiteralPath $fallback) { $ghExe = $fallback }
    }
    if (-not $ghExe) {
        Write-Error 'gh CLI not found (checked PATH and "%ProgramFiles%\GitHub CLI\gh.exe").' -ErrorAction Continue
        exit 2
    }
}

$stagingDir = Join-Path $projectRoot 'temp\S1971\payload'
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($aar.FullName)
$extracted = [ordered]@{}
try {
    foreach ($soName in $expected.Keys) {
        $entryPath = "jni/$Abi/$soName"
        $entry = $zip.Entries | Where-Object { $_.FullName -eq $entryPath } | Select-Object -First 1
        if (-not $entry) {
            Write-Error "AAR has no entry $entryPath." -ErrorAction Continue
            exit 2
        }
        $dest = Join-Path $stagingDir $soName
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
        $extracted[$soName] = $dest
    }
}
finally {
    $zip.Dispose()
}

$mismatch = $false
foreach ($soName in $expected.Keys) {
    $path = $extracted[$soName]
    $size = (Get-Item -LiteralPath $path).Length
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    $pin = $expected[$soName]
    Write-Host ("{0,-14} {1,12:N0} bytes  {2}" -f $soName, $size, $hash) -ForegroundColor Cyan
    if ($size -ne $pin.Size) {
        Write-Host ("    size mismatch: pinned {0:N0}" -f $pin.Size) -ForegroundColor Red
        $mismatch = $true
    }
    if ($hash -ne $pin.Sha256) {
        Write-Host ("    sha256 mismatch: pinned {0}" -f $pin.Sha256) -ForegroundColor Red
        $mismatch = $true
    }
}

if ($mismatch) {
    Write-Error 'Extracted payload does not match the pins in DeliverableDescriptorCatalog.kt. Update the pins and the app together, or publish a new revision.' -ErrorAction Continue
    exit 1
}

if ($WhatIf) {
    Write-Host 'Pins match. -WhatIf: nothing uploaded.' -ForegroundColor Green
    exit 0
}

foreach ($soName in $expected.Keys) {
    $bare = [System.IO.Path]::GetFileNameWithoutExtension($soName)
    $assetName = "$Abi-$bare-$Rev.so"
    $assetPath = Join-Path $stagingDir $assetName
    Copy-Item -LiteralPath $extracted[$soName] -Destination $assetPath -Force

    Write-Host ("Uploading {0} .." -f $assetName) -ForegroundColor Cyan
    # No --clobber: a published revision is permanent, because installs already
    # in the field fetch exactly this name. A changed payload gets -v2.
    & $ghExe release upload $Tag $assetPath
    if ($LASTEXITCODE -ne 0) {
        Write-Error "gh release upload failed for $assetName (exit $LASTEXITCODE)" -ErrorAction Continue
        exit 1
    }
    Write-Host ('    https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/{0}/{1}' -f $Tag, $assetName) -ForegroundColor DarkGray
}

Write-Host 'Published. Record the set in delivery/INVENTORY.md.' -ForegroundColor Green
exit 0
