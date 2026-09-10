# publish-prebuilt-native-aar.ps1
# ==============================================================================
# Publishes a locally built build-time native AAR to its permanent delivery
# release so GitHub Actions can build the app.
#
# Why this exists: app_v2/libs/ is gitignored (.gitignore "libs/"), so the AARs
# that app_v2/build.gradle.kts declares as hard dependencies of the standard,
# noLegal, legacy and vr flavors never reach GitHub. Every CI run resolved an
# absent FFmpeg AAR and died before lint/tests could run - 69 red runs in a row
# (S1539) - and the VP9 AAR failed the opposite way: Gradle answers an absent
# files("libs/..") with an empty collection, so CI stayed green and shipped an
# artifact with no software VP9 decoder in it (S2879).
#
# Which AARs exist is scripts/ci/prebuilt-native-aars.txt, shared with the CI
# fetcher and with the build's own verifyPrebuiltNativeAars task. This script
# holds no list of its own - S2879 replaced publish-ffmpeg-dts-aar.ps1, whose
# single hardcoded path is what made the second AAR need a copy of the script.
#
# Unlike the on-demand runtime payloads in the same release, these assets are
# BUILD-TIME dependencies: no shipped app version fetches them, nothing pins
# their hash in DeliverableDescriptorCatalog.kt. That is why they use stable,
# unversioned asset names and are clobbered on each rebuild - CI always wants
# the current binary.
#
# Usage (from project root, after rebuilding an AAR):
#   pwsh -NoProfile -File scripts/builders/publish-prebuilt-native-aar.ps1 -Name fms-vpx.aar
#   pwsh -NoProfile -File scripts/builders/publish-prebuilt-native-aar.ps1 -All
#
# Exit: 0 - every requested asset uploaded
#       1 - an upload failed
#       2 - could not verify: manifest or AAR absent, name not in the manifest,
#           no target selected, or gh CLI not found
# ==============================================================================

[CmdletBinding()]
param(
    [string] $Name,
    [switch] $All,
    [string] $ManifestPath = 'scripts/ci/prebuilt-native-aars.txt',
    [string] $Tag = 'delivery-so-v1'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot | Split-Path | Split-Path

function Resolve-FromRoot([string] $Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $projectRoot $Path
}

if (-not $All -and [string]::IsNullOrWhiteSpace($Name)) {
    Write-Error 'Select what to publish: -Name <file.aar> or -All.' -ErrorAction Continue
    exit 2
}

$manifest = Resolve-FromRoot $ManifestPath
if (-not (Test-Path -LiteralPath $manifest)) {
    Write-Error "Manifest not found at $manifest" -ErrorAction Continue
    exit 2
}

$entries = @(Get-Content -LiteralPath $manifest |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith('#') })

if ($entries.Count -eq 0) {
    Write-Error "Manifest $ManifestPath lists no AAR." -ErrorAction Continue
    exit 2
}

$targets = if ($All) {
    $entries
} else {
    @($entries | Where-Object { [System.IO.Path]::GetFileName($_) -eq $Name })
}

if ($targets.Count -eq 0) {
    Write-Error "'$Name' is not listed in $ManifestPath - add it there first (the manifest is the list every consumer reads)." -ErrorAction Continue
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

# Every target is checked before the first upload, so a missing second AAR does not
# leave the release half-updated behind an already-published first one.
$missing = @($targets | Where-Object { -not (Test-Path -LiteralPath (Resolve-FromRoot $_)) })
if ($missing.Count -gt 0) {
    Write-Error ("AAR not found: {0}. Build it first - see the build script named beside its dependency in app_v2/build.gradle.kts." -f ($missing -join ', ')) -ErrorAction Continue
    exit 2
}

foreach ($entry in $targets) {
    $aar = Resolve-FromRoot $entry
    $item = Get-Item -LiteralPath $aar
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $aar).Hash.ToLowerInvariant()

    Write-Host ("Publishing {0} ({1:N1} MB) to release {2} (--clobber) .." -f $item.Name, ($item.Length / 1MB), $Tag) -ForegroundColor Cyan
    & $ghExe release upload $Tag $aar --clobber
    if ($LASTEXITCODE -ne 0) {
        Write-Error "gh release upload failed for $($item.Name) (exit $LASTEXITCODE)" -ErrorAction Continue
        exit 1
    }

    Write-Host ("Published {0} ({1:N0} bytes)" -f $item.Name, $item.Length) -ForegroundColor Green
    Write-Host ("    sha256 = {0}" -f $hash) -ForegroundColor DarkGray
    Write-Host ('    url    = https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/{0}/{1}' -f $Tag, $item.Name) -ForegroundColor DarkGray
}

Write-Host 'Record the hashes above in delivery/INVENTORY.md when they change.' -ForegroundColor DarkGray
exit 0
