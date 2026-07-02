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

$ANDROID_NS = 'http://schemas.android.com/apk/res/android'

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

# Map an android fill/stroke colour token to its SVG paint value.
# Returns 'none' for transparent, 'currentColor' otherwise, $null when absent.
function ConvertTo-SvgPaint([string] $androidColor) {
    if ([string]::IsNullOrWhiteSpace($androidColor)) { return $null }
    $c = $androidColor.Trim()
    if ($c -ieq '@android:color/transparent' -or $c -ieq '@color/transparent') { return 'none' }
    if ($c -match '^#00[0-9A-Fa-f]{6}$') { return 'none' }   # #00RRGGBB = fully transparent
    if ($c -match '^#00000000$') { return 'none' }
    return 'currentColor'
}

# Convert one VectorDrawable file to SVG text.
# Returns @{ Svg = <string>; Skip = $null } on success, or @{ Svg = $null; Skip = <reason> }.
function Convert-VectorToSvg([string] $srcPath) {
    $raw = Get-Content -LiteralPath $srcPath -Raw
    if ($raw -match '<gradient|<clip-path|<group\b|aapt:attr') {
        return @{ Svg = $null; Skip = 'uses <gradient>/<clip-path>/<group>/aapt:attr (not translated)' }
    }

    $doc = New-Object System.Xml.XmlDocument
    $doc.LoadXml($raw)
    $root = $doc.DocumentElement
    if ($root.LocalName -ne 'vector') {
        return @{ Svg = $null; Skip = "root element is <$($root.LocalName)>, not <vector>" }
    }

    $vw = $root.GetAttribute('viewportWidth', $ANDROID_NS)
    $vh = $root.GetAttribute('viewportHeight', $ANDROID_NS)
    if ([string]::IsNullOrWhiteSpace($vw) -or [string]::IsNullOrWhiteSpace($vh)) {
        return @{ Svg = $null; Skip = 'missing android:viewportWidth/viewportHeight' }
    }
    $vw = $vw.Trim(); $vh = $vh.Trim()

    $pathTags = New-Object System.Collections.Generic.List[string]
    foreach ($node in $root.ChildNodes) {
        if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }   # skip comments/whitespace
        if ($node.LocalName -ne 'path') {
            return @{ Svg = $null; Skip = "contains unsupported element <$($node.LocalName)>" }
        }

        $pd = $node.GetAttribute('pathData', $ANDROID_NS)
        if ([string]::IsNullOrWhiteSpace($pd)) { continue }
        # pathData grammar == SVG d grammar; collapse source indentation/newlines so
        # multi-line pathData yields a clean, deterministic single-line d.
        $d = ($pd -replace '\s+', ' ').Trim()

        $fillPaint   = ConvertTo-SvgPaint ($node.GetAttribute('fillColor', $ANDROID_NS))
        $strokePaint = ConvertTo-SvgPaint ($node.GetAttribute('strokeColor', $ANDROID_NS))
        $fillType    = $node.GetAttribute('fillType', $ANDROID_NS)
        $fillAlpha   = $node.GetAttribute('fillAlpha', $ANDROID_NS)
        $strokeWidth = $node.GetAttribute('strokeWidth', $ANDROID_NS)

        $attrs = New-Object System.Collections.Generic.List[string]
        $attrs.Add('d="' + $d + '"')

        if ($null -eq $fillPaint) {
            # No android:fillColor: a stroke-only path has no fill; otherwise assume a
            # themed fill so the path stays visible.
            if ($strokePaint -eq 'currentColor') { $attrs.Add('fill="none"') }
            else { $attrs.Add('fill="currentColor"') }
        } else {
            $attrs.Add('fill="' + $fillPaint + '"')
        }

        if ($fillType -ieq 'evenOdd') { $attrs.Add('fill-rule="evenodd"') }
        if (-not [string]::IsNullOrWhiteSpace($fillAlpha)) { $attrs.Add('fill-opacity="' + $fillAlpha.Trim() + '"') }

        if ($strokePaint -eq 'currentColor') {
            $attrs.Add('stroke="currentColor"')
            if (-not [string]::IsNullOrWhiteSpace($strokeWidth)) { $attrs.Add('stroke-width="' + $strokeWidth.Trim() + '"') }
        }

        $pathTags.Add('  <path ' + [string]::Join(' ', $attrs) + '/>')
    }

    if ($pathTags.Count -eq 0) {
        return @{ Svg = $null; Skip = 'no <path android:pathData> found' }
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('<svg xmlns="http://www.w3.org/2000/svg" width="' + $vw + '" height="' + $vh + '" viewBox="0 0 ' + $vw + ' ' + $vh + '">')
    foreach ($t in $pathTags) { $lines.Add($t) }
    $lines.Add('</svg>')
    return @{ Svg = ([string]::Join("`n", $lines) + "`n"); Skip = $null }
}

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
