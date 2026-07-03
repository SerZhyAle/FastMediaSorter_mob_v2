<#
.SYNOPSIS
    S0815 Phase 2 - export app VectorDrawable icons to lightweight web SVGs.

.DESCRIPTION
    Reads docs/icons/icon-inventory.json (Phase 1) and, for every DISTINCT
    drawable with assetFormat=vector and public=true, reads the Android
    VectorDrawable at app_v2/src/main/res/drawable/<drawable>.xml and converts it
    to a colour-neutral SVG under docs/icons/svg/<drawable>.svg:
      - android:viewportWidth/Height  -> viewBox + width/height
      - <path android:pathData="D">   -> <path d="D">  (grammar is identical)
      - every android:fillColor (placeholder @android:color/white, literal #hex,
        or ?attr/..) -> fill="currentColor" so icons inherit the surrounding text
        colour and follow the site light/dark theme (research/03 D2).
      - transparent fill (@android:color/transparent, #00RRGGBB) -> fill="none"
        (stroke-only paths; currentColor would fill the open subpath).
      - android:fillAlpha -> fill-opacity; android:fillType="evenOdd" -> fill-rule.
      - android:strokeColor/Width (when present) -> stroke="currentColor" +
        stroke-width. All other android:*/app:* attrs are dropped.

    Drawables using <gradient>/<clip-path>/<group>/aapt:attr are NOT partially
    translated: a WARNING is logged and the drawable skipped (better to under-emit
    and flag than emit a broken SVG). Skips are reported at the end.

    Non-vector inventory rows:
      - assetFormat=raster  -> the source PNG is copied verbatim (brand logos).
      - assetFormat=framework -> no asset (android.R.drawable.* has no local file).
      - public=false        -> excluded (kept out of the public asset tree).

    Output is deterministic (fixed attribute order, LF newlines, UTF-8 no BOM,
    single trailing newline) so the Phase 4 drift gate can re-run and byte-diff.
    docs/icons/svg/ is a generated tree - never hand-edited.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

# S0889: the VectorDrawable -> SVG conversion moved to a shared lib so the doc-icon PNG
# exporter reuses the exact same path (no drift between the two generated trees).
. (Join-Path $PSScriptRoot 'lib/vectordrawable-svg.ps1')

$inventoryPath = Join-Path $RepoRoot 'docs/icons/icon-inventory.json'
$drawableDir   = Join-Path $RepoRoot 'app_v2/src/main/res/drawable'
$svgDir        = Join-Path $RepoRoot 'docs/icons/svg'

if (-not (Test-Path -LiteralPath $inventoryPath)) {
    Write-Host "ERROR: inventory not found: $inventoryPath"
    exit 1
}

$inventory = Get-Content -LiteralPath $inventoryPath -Raw | ConvertFrom-Json

# Drawables are reused across many inventory rows - dedup to distinct names so we
# emit one SVG per drawable, not one per entry.
$vectorNames = @(@($inventory | Where-Object { $_.assetFormat -eq 'vector' -and $_.public }) |
    ForEach-Object { $_.drawable } | Sort-Object -Unique)
$rasterNames = @(@($inventory | Where-Object { $_.assetFormat -eq 'raster' -and $_.public }) |
    ForEach-Object { $_.drawable } | Sort-Object -Unique)

$null = New-Item -ItemType Directory -Force -Path $svgDir

$utf8 = [System.Text.UTF8Encoding]::new($false)
$expected = New-Object System.Collections.Generic.HashSet[string]
$skipped  = New-Object System.Collections.Generic.List[string]
$missing  = New-Object System.Collections.Generic.List[string]
$emitted  = 0
$copied   = 0

foreach ($name in $vectorNames) {
    $src = Join-Path $drawableDir ($name + '.xml')
    if (-not (Test-Path -LiteralPath $src)) {
        $missing.Add($name + ' (vector source: ' + $src + ')')
        continue
    }
    $result = Convert-VectorToSvg $src
    if ($result.Skip) {
        Write-Warning ('skip ' + $name + '.xml - ' + $result.Skip)
        $skipped.Add($name + ' - ' + $result.Skip)
        continue
    }
    [System.IO.File]::WriteAllText((Join-Path $svgDir ($name + '.svg')), $result.Svg, $utf8)
    [void]$expected.Add($name + '.svg')
    $emitted++
}

foreach ($name in $rasterNames) {
    $src = Join-Path $drawableDir ($name + '.png')
    if (-not (Test-Path -LiteralPath $src)) {
        $missing.Add($name + ' (raster source: ' + $src + ')')
        continue
    }
    Copy-Item -LiteralPath $src -Destination (Join-Path $svgDir ($name + '.png')) -Force
    [void]$expected.Add($name + '.png')
    $copied++
}

# Prune assets no longer backed by the inventory (e.g. an icon dropped or made
# non-public) so the generated tree is exactly the expected set and a re-run is
# byte-identical.
$pruned = 0
Get-ChildItem -LiteralPath $svgDir -File | Where-Object { -not $expected.Contains($_.Name) } | ForEach-Object {
    Remove-Item -LiteralPath $_.FullName -Force
    $pruned++
}

Write-Host ''
Write-Host 'Icon SVG export complete.'
Write-Host ('  vector drawables (distinct, public): ' + $vectorNames.Count)
Write-Host ('  SVGs emitted:                        ' + $emitted)
Write-Host ('  raster PNGs copied:                  ' + $copied)
Write-Host ('  stale assets pruned:                 ' + $pruned)
Write-Host ('  skipped:                             ' + $skipped.Count)
foreach ($s in $skipped) { Write-Host ('    - ' + $s) }
Write-Host ('  output: ' + $svgDir)

if ($missing.Count -gt 0) {
    Write-Host ''
    Write-Host ('ERROR: ' + $missing.Count + ' source drawable(s) missing:')
    foreach ($m in $missing) { Write-Host ('    - ' + $m) }
    exit 1
}

exit 0
