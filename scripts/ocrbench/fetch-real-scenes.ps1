<#
.SYNOPSIS
    S1716: bring the real bench scenes into a local cache, or register a new one into the manifest.

.DESCRIPTION
    Real scenes are pictures the owner supplies. Their media never enter the repository - only the
    manifest does, and only because it carries no credentials: a relative path and a SHA-256 per
    scene. Hand-corrected annotation is committed beside the synthetic scenes (owner ruling
    2026-08-25): it is this ticket's most expensive manual work and the one thing not regenerable.

    The transfer is a local folder addressed by FMS_OCRBENCH_SCENES. No endpoint, no authentication:
    the run lives on one machine and never goes to CI, so a service would add hosting to protect
    nothing.

    Fetch (default) copies every manifest scene into the cache and verifies its SHA-256. An unset
    FMS_OCRBENCH_SCENES with a non-empty manifest is a hard failure naming the variable, never a
    smaller corpus - strategic pillar 3 requires an absent dependency to stop the run. An empty
    manifest is not that case: nothing was requested, so nothing is missing.

    Register (-Register) writes one scene's SHA-256 into the manifest. The hash catches a silently
    re-encoded or replaced scene, after which a report's numbers would move while the annotation
    still claimed to describe the picture.

.PARAMETER Register
    Path of a scene relative to FMS_OCRBENCH_SCENES, to add to or refresh in the manifest.

.PARAMETER SceneId
    Key the annotation and every report row are addressed by. Defaults to the file's base name.

.PARAMETER Manifest
    Manifest to read or write. Defaults to the committed one under the test resources.

.PARAMETER CacheRoot
    Cache directory. Defaults to temp/ocrbench/cache.

.NOTES
    Exit codes:
      0 - every manifest scene is cached and verified, or the manifest requests none, or the
          registration succeeded.
      1 - a scene is missing or fails its SHA-256, or FMS_OCRBENCH_SCENES is unset while the
          manifest requests scenes.
      2 - the manifest cannot be read or parsed, or a registration input does not exist.
#>
param(
    [string]$Register,
    [string]$SceneId,
    [string]$Manifest,
    [string]$CacheRoot
)

$ErrorActionPreference = "Stop"
$MANIFEST_VERSION = 1

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Manifest) {
    $Manifest = Join-Path $projectRoot "app_v2\src\test\resources\ocrbench\real-scenes.json"
}
if (-not $CacheRoot) { $CacheRoot = Join-Path $projectRoot "temp\ocrbench\cache" }

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

if (-not (Test-Path -LiteralPath $Manifest)) {
    Write-Host "fetch-real-scenes: manifest not found at $Manifest" -ForegroundColor Red
    exit 2
}
try {
    $doc = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    Write-Host "fetch-real-scenes: manifest is not valid JSON - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}
if ($doc.version -ne $MANIFEST_VERSION) {
    Write-Host "fetch-real-scenes: manifest version '$($doc.version)', expected $MANIFEST_VERSION" -ForegroundColor Red
    exit 2
}

$scenes = @($doc.scenes)
$sceneRoot = $env:FMS_OCRBENCH_SCENES

if ($Register) {
    if ([string]::IsNullOrWhiteSpace($sceneRoot)) {
        Write-Host "fetch-real-scenes: FMS_OCRBENCH_SCENES is not set - cannot register." -ForegroundColor Red
        exit 1
    }
    $source = Join-Path $sceneRoot $Register
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        Write-Host "fetch-real-scenes: no file at $source" -ForegroundColor Red
        exit 2
    }
    $id = if ($SceneId) { $SceneId } else { [System.IO.Path]::GetFileNameWithoutExtension($Register) }
    $kept = @($scenes | Where-Object { $_.sceneId -ne $id })
    $kept += [pscustomobject]@{
        sceneId      = $id
        relativePath = $Register
        sha256       = Get-Sha256 -Path $source
    }
    $doc.scenes = @($kept | Sort-Object -Property sceneId)
    Set-Content -LiteralPath $Manifest -Value ($doc | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "fetch-real-scenes: registered $id" -ForegroundColor Green
    Write-Host "  annotate it at app_v2/src/test/resources/ocrbench/annotations/$id.json before it scores."
    exit 0
}

if ($scenes.Count -eq 0) {
    Write-Host "fetch-real-scenes: the manifest requests no scene - nothing to fetch." -ForegroundColor Yellow
    Write-Host "  Add one with -Register <path relative to FMS_OCRBENCH_SCENES>."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($sceneRoot)) {
    $n = $scenes.Count
    Write-Host "fetch-real-scenes: FMS_OCRBENCH_SCENES is unset, manifest requests $n scene(s)." -ForegroundColor Red
    Write-Host "  Point it at the folder holding the real scenes. The run stops rather than measuring less."
    exit 1
}

New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
$fetched = 0
$cached = 0
$failed = New-Object System.Collections.Generic.List[string]

foreach ($scene in $scenes) {
    $ext = [System.IO.Path]::GetExtension($scene.relativePath)
    $target = Join-Path $CacheRoot ("{0}{1}" -f $scene.sceneId, $ext)
    $expected = ([string]$scene.sha256).ToLowerInvariant()

    if ((Test-Path -LiteralPath $target -PathType Leaf) -and ((Get-Sha256 -Path $target) -eq $expected)) {
        $cached++
        Write-Host "  cached  $($scene.sceneId)"
        continue
    }
    $source = Join-Path $sceneRoot $scene.relativePath
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        $failed.Add("$($scene.sceneId) - not found at $source")
        Write-Host "  FAILED  $($scene.sceneId) - not found" -ForegroundColor Red
        continue
    }
    Copy-Item -LiteralPath $source -Destination $target -Force
    $actual = Get-Sha256 -Path $target
    if ($actual -ne $expected) {
        Remove-Item -LiteralPath $target -Force
        $failed.Add("$($scene.sceneId) - SHA-256 mismatch (manifest $expected, file $actual)")
        Write-Host "  FAILED  $($scene.sceneId) - SHA-256 mismatch" -ForegroundColor Red
        continue
    }
    $fetched++
    Write-Host "  fetched $($scene.sceneId)" -ForegroundColor Green
}

Write-Host ""
Write-Host "fetch-real-scenes: fetched $fetched, cached $cached, failed $($failed.Count) into $CacheRoot"
foreach ($line in $failed) { Write-Host "  $line" -ForegroundColor Red }
if ($failed.Count -gt 0) { exit 1 }
exit 0
