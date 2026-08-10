# publish-ffmpeg-dts-aar.ps1
# ==============================================================================
# Publishes the locally built FFmpeg DTS AAR to its permanent delivery release so
# GitHub Actions can build the app.
#
# Why this exists: app_v2/libs/ is gitignored (.gitignore "libs/"), so the 11.5 MB
# AAR that app_v2/build.gradle.kts declares as a hard dependency for the standard,
# noLegal, legacy and vr flavors never reaches GitHub. Every CI run resolved an
# absent file and died before lint/tests could run - 69 red runs in a row (S1539).
# CI now downloads the asset this script publishes.
#
# Unlike the on-demand runtime payloads in the same release, this asset is a
# BUILD-TIME dependency: no shipped app version fetches it, nothing pins its hash
# in DeliverableDescriptorCatalog.kt. That is why it uses a stable, unversioned
# asset name and is clobbered on each rebuild - CI always wants the current one.
#
# Usage (from project root, after rebuilding the AAR):
#   pwsh -NoProfile -File scripts/builders/publish-ffmpeg-dts-aar.ps1
#
# Exit: 0 - asset uploaded
#       1 - upload failed
#       2 - could not verify: AAR absent, or gh CLI not found
# ==============================================================================

[CmdletBinding()]
param(
    [string] $AarPath = 'app_v2/libs/fms-ffmpeg-dts.aar',
    [string] $Tag = 'delivery-so-v1'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot | Split-Path | Split-Path
$aar = if ([System.IO.Path]::IsPathRooted($AarPath)) { $AarPath } else { Join-Path $projectRoot $AarPath }

if (-not (Test-Path -LiteralPath $aar)) {
    Write-Error "AAR not found at $aar - build it first: scripts/builders/build-ffmpeg-dts-wsl.ps1" -ErrorAction Continue
    exit 2
}

# gh is installed but is not on PATH in either shell here, so resolve it explicitly
# rather than failing with a misleading "not installed".
$ghExe = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $ghExe) {
    $fallback = Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe'
    if (Test-Path -LiteralPath $fallback) { $ghExe = $fallback }
}
if (-not $ghExe) {
    Write-Error 'gh CLI not found (checked PATH and "%ProgramFiles%\GitHub CLI\gh.exe").' -ErrorAction Continue
    exit 2
}

$item = Get-Item -LiteralPath $aar
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $aar).Hash.ToLowerInvariant()

Write-Host ("Publishing {0} ({1:N1} MB) to release {2} (--clobber) .." -f $item.Name, ($item.Length / 1MB), $Tag) -ForegroundColor Cyan
& $ghExe release upload $Tag $aar --clobber
if ($LASTEXITCODE -ne 0) {
    Write-Error "gh release upload failed (exit $LASTEXITCODE)" -ErrorAction Continue
    exit 1
}

Write-Host ("Published {0} ({1:N0} bytes)" -f $item.Name, $item.Length) -ForegroundColor Green
Write-Host ("    sha256 = {0}" -f $hash) -ForegroundColor DarkGray
Write-Host ('    url    = https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/{0}/{1}' -f $Tag, $item.Name) -ForegroundColor DarkGray
Write-Host 'Record the hash above in delivery/INVENTORY.md when it changes.' -ForegroundColor DarkGray
exit 0
