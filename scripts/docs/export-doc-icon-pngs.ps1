<#
.SYNOPSIS
    S0889 - export the docs/site icon assets (SVG + PNG) for every drawable named in
    docs/icons/doc-icon-map.json.

.DESCRIPTION
    The doc-icon map (owner decision 2026-07-03: nearest app drawable for every emoji
    slot) names the drawables the docs and landing pages need - a superset of the 58
    public inventory icons (adds ic_profile_*, ic_play, ic_folder, ..). For each DISTINCT
    drawable this emits, under docs/icons/doc/:
      - <drawable>.svg : colour-neutral (fill="currentColor"), for the landing pages that
        inline the SVG so it themes with light/dark (owner pick).
      - <drawable>.png : a baked-colour raster (default #24292e) at 2x, for the markdown
        surfaces that embed via <img> (owner: "turn it into png to show on site").
    Conversion reuses the shared VectorDrawable->SVG lib (same path as the S0815 exporter);
    rasterization is one cairosvg batch (scripts/docs/lib/rasterize_svgs.py, .venv).

    Generated tree - never hand-edited. Deterministic: a re-run is byte-identical when the
    map and sources are unchanged. Stale assets (a drawable dropped from the map) are pruned.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $Color = '#24292e',
    [int]    $PngWidth = 96
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/vectordrawable-svg.ps1')

$mapPath     = Join-Path $RepoRoot 'docs/icons/doc-icon-map.json'
$drawableDir = Join-Path $RepoRoot 'app_v2/src/main/res/drawable'
$outDir      = Join-Path $RepoRoot 'docs/icons/doc'
$python      = Join-Path $RepoRoot '.venv/Scripts/python.exe'
$rasterizer  = Join-Path $PSScriptRoot 'lib/rasterize_svgs.py'

if (-not (Test-Path -LiteralPath $mapPath)) { Write-Host "ERROR: map not found: $mapPath"; exit 1 }
if (-not (Test-Path -LiteralPath $python))  { Write-Host "ERROR: venv python not found: $python"; exit 1 }

$map = Get-Content -LiteralPath $mapPath -Raw | ConvertFrom-Json

# Collect every distinct drawable named anywhere in the map.
$names = New-Object System.Collections.Generic.HashSet[string]
foreach ($c in $map.landing) { [void]$names.Add($c.drawable) }
foreach ($c in $map.howto)   { [void]$names.Add($c.drawable) }
foreach ($c in $map.docsMap) { [void]$names.Add($c.drawable) }
foreach ($p in $map.settingsSections.PSObject.Properties) { [void]$names.Add($p.Value) }
$distinct = @($names) | Sort-Object

$null = New-Item -ItemType Directory -Force -Path $outDir
$utf8 = [System.Text.UTF8Encoding]::new($false)

$expected = New-Object System.Collections.Generic.HashSet[string]
$missing  = New-Object System.Collections.Generic.List[string]
$skipped  = New-Object System.Collections.Generic.List[string]
$jobs     = New-Object System.Collections.Generic.List[object]
$emitted  = 0

foreach ($name in $distinct) {
    $src = Join-Path $drawableDir ($name + '.xml')
    if (-not (Test-Path -LiteralPath $src)) {
        $missing.Add($name)
        continue
    }
    $result = Convert-VectorToSvg $src
    if ($result.Skip) {
        Write-Warning ('skip ' + $name + '.xml - ' + $result.Skip)
        $skipped.Add($name + ' - ' + $result.Skip)
        continue
    }
    $svgPath = Join-Path $outDir ($name + '.svg')
    $pngPath = Join-Path $outDir ($name + '.png')
    [System.IO.File]::WriteAllText($svgPath, $result.Svg, $utf8)
    [void]$expected.Add($name + '.svg')
    [void]$expected.Add($name + '.png')
    $jobs.Add([ordered]@{ svg = $svgPath; png = $pngPath; width = $PngWidth; color = $Color })
    $emitted++
}

if ($missing.Count -gt 0) {
    Write-Host ''
    Write-Host ('ERROR: ' + $missing.Count + ' mapped drawable(s) missing under ' + $drawableDir + ':')
    foreach ($m in $missing) { Write-Host ('    - ' + $m) }
    exit 1
}

# One cairosvg batch for every PNG.
$jobsFile = Join-Path $RepoRoot 'temp/_doc_icon_raster_jobs.json'
$null = New-Item -ItemType Directory -Force -Path (Split-Path $jobsFile)
[System.IO.File]::WriteAllText($jobsFile, ($jobs | ConvertTo-Json -Depth 4), $utf8)
& $python $rasterizer $jobsFile
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: rasterizer exited $LASTEXITCODE"; exit 1 }
Remove-Item -LiteralPath $jobsFile -Force -ErrorAction SilentlyContinue

# Prune assets no longer backed by the map.
$pruned = 0
Get-ChildItem -LiteralPath $outDir -File | Where-Object { -not $expected.Contains($_.Name) } | ForEach-Object {
    Remove-Item -LiteralPath $_.FullName -Force
    $pruned++
}

Write-Host ''
Write-Host 'Doc icon asset export complete.'
Write-Host ('  distinct drawables: ' + $distinct.Count)
Write-Host ('  svg+png emitted:    ' + $emitted)
Write-Host ('  stale pruned:       ' + $pruned)
Write-Host ('  skipped:            ' + $skipped.Count)
foreach ($s in $skipped) { Write-Host ('    - ' + $s) }
Write-Host ('  output: ' + $outDir)
exit 0
