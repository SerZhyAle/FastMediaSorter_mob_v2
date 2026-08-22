# S1841 validation harness: exercise Build-StreamLogoAtlas against the real artwork cache,
# writing to temp/ instead of delivery/, so nothing published is touched.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../../..").Path
Set-Location $root

# Orchestrator params the Artwork module reads out of its caller's scope.
$LogoCacheDir      = 'temp/stream-logo-src'
$LogoAtlasPath     = 'temp/S1841/validate/stream-logo-atlas.webp'
$LogoCoordsPath    = 'temp/S1841/validate/stream-logo-coords.json'
$MaxLogoAtlasBytes = 50331648
$FfmpegPath        = ''

. (Join-Path $root 'scripts/streams/modules/StreamPublisher.Common.ps1')
. (Join-Path $root 'scripts/streams/modules/StreamPublisher.Artwork.ps1')

$rows = Import-Csv 'delivery/stream-catalog/streams.csv'
Write-Host ("Rows in catalog: {0}" -f $rows.Count) -ForegroundColor Cyan

$covered = Build-StreamLogoAtlas -Rows $rows
Write-Host ("RESULT covered-urls={0}" -f $covered) -ForegroundColor Cyan

$coords = Get-Content $LogoCoordsPath -Raw | ConvertFrom-Json
$idx = ($coords.PSObject.Properties | ForEach-Object { [int]$_.Value })
Write-Host ("RESULT max-tile-index={0}" -f ($idx | Measure-Object -Maximum).Maximum) -ForegroundColor Cyan
Write-Host ("RESULT distinct-tiles={0}" -f (($idx | Sort-Object -Unique).Count)) -ForegroundColor Cyan
Write-Host ("RESULT webp-bytes={0}" -f (Get-Item $LogoAtlasPath).Length) -ForegroundColor Cyan
