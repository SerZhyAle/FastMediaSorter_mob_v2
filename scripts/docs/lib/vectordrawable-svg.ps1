<#
.SYNOPSIS
    Shared Android VectorDrawable -> web SVG converter (S0815 core, extracted S0889).

.DESCRIPTION
    Dot-source this file to get ConvertTo-SvgPaint + Convert-VectorToSvg. Extracted
    verbatim from export-icon-svgs.ps1 so both the public-icon SVG exporter (S0815)
    and the doc-icon PNG exporter (S0889) share ONE conversion path - no drift
    between the two generated trees. The output is byte-identical to the pre-extract
    inline version (the S0815 drift gate re-verifies this).
#>

$script:ANDROID_NS = 'http://schemas.android.com/apk/res/android'

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

    $vw = $root.GetAttribute('viewportWidth', $script:ANDROID_NS)
    $vh = $root.GetAttribute('viewportHeight', $script:ANDROID_NS)
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

        $pd = $node.GetAttribute('pathData', $script:ANDROID_NS)
        if ([string]::IsNullOrWhiteSpace($pd)) { continue }
        # pathData grammar == SVG d grammar; collapse source indentation/newlines so
        # multi-line pathData yields a clean, deterministic single-line d.
        $d = ($pd -replace '\s+', ' ').Trim()

        $fillPaint   = ConvertTo-SvgPaint ($node.GetAttribute('fillColor', $script:ANDROID_NS))
        $strokePaint = ConvertTo-SvgPaint ($node.GetAttribute('strokeColor', $script:ANDROID_NS))
        $fillType    = $node.GetAttribute('fillType', $script:ANDROID_NS)
        $fillAlpha   = $node.GetAttribute('fillAlpha', $script:ANDROID_NS)
        $strokeWidth = $node.GetAttribute('strokeWidth', $script:ANDROID_NS)

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
