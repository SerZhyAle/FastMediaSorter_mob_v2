#requires -Version 7.0
<#
.SYNOPSIS
    VectorDrawable -> SVG conversion shared by the icon contract exporter and the icon style gate.

.DESCRIPTION
    Dot-source this file, then call Initialize-IconColourMaps -RepoRoot <root> once. It defines
    $colourMap / $nightColours (the product's day and night colour resources) in the caller's scope,
    Convert-Drawable (one <vector> file -> @{ Svg; Notes }) and Resolve-Drawable (a vocabulary ref ->
    a drawable path under the caller's $RepoRoot). Extracted from scripts/docs/export-icon-contract.ps1
    (S3433) so the exporter and scripts/quality/assert-icon-style.ps1 measure the same picture.
    Not a script: it has no exit codes of its own; Convert-Drawable throws on an unsupported element.
#>

$ANDROID = 'http://schemas.android.com/apk/res/android'
$AAPT = 'http://schemas.android.com/aapt'
# ------------------------------------------------------------------ colour resources of this product
function Import-Colours([string] $dir, [hashtable] $into) {
    if (-not (Test-Path -LiteralPath $dir)) { return }
    foreach ($f in Get-ChildItem -LiteralPath $dir -Filter '*.xml') {
        $doc = New-Object System.Xml.XmlDocument
        try { $doc.LoadXml([System.IO.File]::ReadAllText($f.FullName).TrimStart([char]0xFEFF)) } catch { continue }
        foreach ($c in @($doc.DocumentElement.SelectNodes('color'))) { $into[$c.GetAttribute('name')] = $c.InnerText.Trim() }
    }
}

function Resolve-Colour([string] $token, [hashtable] $map) {
    $t = $token
    for ($i = 0; $i -lt 8 -and $t -match '^@color/(.+)$'; $i++) {
        $n = $Matches[1]
        if (-not $map.ContainsKey($n)) { return $null }
        $t = $map[$n]
    }
    if ($t -ieq '@android:color/white') { return '#FFFFFF' }
    if ($t -ieq '@android:color/black') { return '#000000' }
    if ($t -ieq '@android:color/transparent') { return '#00000000' }
    if ($t -match '^#[0-9A-Fa-f]{3,8}$') { return $t }
    return $null
}

# '#AARRGGBB' -> @('#RRGGBB', alpha) ; '#RGB' expanded
function Split-Argb([string] $hex) {
    $h = $hex.TrimStart('#')
    switch ($h.Length) {
        3 { return @(('#' + (($h.ToCharArray() | ForEach-Object { "$_$_" }) -join '')), $null) }
        6 { return @(('#' + $h.ToUpper()), $null) }
        8 {
            $a = [Convert]::ToInt32($h.Substring(0, 2), 16) / 255.0
            return @(('#' + $h.Substring(2).ToUpper()), [Math]::Round($a, 3))
        }
        default { return @($hex, $null) }
    }
}
function Initialize-IconColourMaps([string] $RepoRoot) {
    $script:colourMap = @{}
    Import-Colours (Join-Path $RepoRoot 'app_v2/src/main/res/values') $script:colourMap
    $script:nightColours = @{} + $script:colourMap
    Import-Colours (Join-Path $RepoRoot 'app_v2/src/main/res/values-night') $script:nightColours
}
# ------------------------------------------------------------------ VectorDrawable -> SVG
function Get-Attr($node, [string] $name) { $node.GetAttribute($name, $ANDROID) }
function Get-Num([string] $v) { if ([string]::IsNullOrWhiteSpace($v)) { return 0.0 }; return [double]::Parse($v.Trim(), [cultureinfo]::InvariantCulture) }

function Convert-Paint($node, [string] $attr, [bool] $literal, [System.Collections.Generic.List[string]] $notes) {
    $raw = Get-Attr $node $attr
    if ([string]::IsNullOrWhiteSpace($raw)) {
        foreach ($child in @($node.ChildNodes)) {
            if ($child.LocalName -eq 'attr' -and $child.NamespaceURI -eq $AAPT -and $child.GetAttribute('name') -eq "android:$attr") {
                $notes.Add('gradient flattened')
                if (-not $literal) { return @('currentColor', $null) }
                $item = $child.SelectSingleNode('.//*[local-name()="item"]')
                if ($item) { return (Split-Argb ($item.GetAttribute('color', $ANDROID))) }
                return @('currentColor', $null)
            }
        }
        return $null
    }
    $resolved = Resolve-Colour $raw.Trim() $colourMap
    if ($resolved -and $resolved -match '^#00[0-9A-Fa-f]{6}$') { return @('none', $null) }
    if (-not $literal) { return @('currentColor', $null) }
    if (-not $resolved) { return @('currentColor', $null) }
    return (Split-Argb $resolved)
}

$script:clipSeq = 0
function Convert-Children($parent, [bool] $literal, [System.Collections.Generic.List[string]] $out,
                          [System.Collections.Generic.List[string]] $notes, [string] $indent) {
    $kids = @($parent.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element })
    for ($i = 0; $i -lt $kids.Count; $i++) {
        $n = $kids[$i]
        switch ($n.LocalName) {
            'path' {
                $d = ((Get-Attr $n 'pathData') -replace '\s+', ' ').Trim()
                if (-not $d) { continue }
                $a = New-Object System.Collections.Generic.List[string]
                $a.Add('d="' + $d + '"')
                $fill = Convert-Paint $n 'fillColor' $literal $notes
                $stroke = Convert-Paint $n 'strokeColor' $literal $notes
                if ($null -eq $fill) { $a.Add($(if ($stroke) { 'fill="none"' } else { 'fill="currentColor"' })) }
                else {
                    $a.Add('fill="' + $fill[0] + '"')
                    $fa = Get-Attr $n 'fillAlpha'
                    $op = if ($fa) { [double]$fa } else { 1.0 }
                    if ($null -ne $fill[1]) { $op = $op * [double]$fill[1] }
                    if ($op -lt 1.0 -and $fill[0] -ne 'none') { $a.Add('fill-opacity="' + [Math]::Round($op, 3) + '"') }
                }
                if ((Get-Attr $n 'fillType') -ieq 'evenOdd') { $a.Add('fill-rule="evenodd"') }
                if ($stroke -and $stroke[0] -ne 'none') {
                    $a.Add('stroke="' + $stroke[0] + '"')
                    $sw = Get-Attr $n 'strokeWidth'; if ($sw) { $a.Add('stroke-width="' + $sw.Trim() + '"') }
                    $sc = Get-Attr $n 'strokeLineCap'; if ($sc) { $a.Add('stroke-linecap="' + $sc.Trim().ToLower() + '"') }
                    $sj = Get-Attr $n 'strokeLineJoin'; if ($sj) { $a.Add('stroke-linejoin="' + $sj.Trim().ToLower() + '"') }
                    $sa = Get-Attr $n 'strokeAlpha'
                    $sop = if ($sa) { [double]$sa } else { 1.0 }
                    if ($null -ne $stroke[1]) { $sop = $sop * [double]$stroke[1] }
                    if ($sop -lt 1.0) { $a.Add('stroke-opacity="' + [Math]::Round($sop, 3) + '"') }
                }
                $out.Add($indent + '<path ' + ($a -join ' ') + '/>')
            }
            'group' {
                $tx = Get-Num (Get-Attr $n 'translateX'); $ty = Get-Num (Get-Attr $n 'translateY')
                $px = Get-Num (Get-Attr $n 'pivotX'); $py = Get-Num (Get-Attr $n 'pivotY')
                $sx = Get-Attr $n 'scaleX'; $sy = Get-Attr $n 'scaleY'; $rot = Get-Attr $n 'rotation'
                $parts = New-Object System.Collections.Generic.List[string]
                if (($tx + $px) -ne 0 -or ($ty + $py) -ne 0) { $parts.Add("translate($($tx + $px) $($ty + $py))") }
                if ($rot) { $parts.Add("rotate($rot)") }
                if ($sx -or $sy) { $parts.Add("scale($(if ($sx) { $sx } else { 1 }) $(if ($sy) { $sy } else { 1 }))") }
                if ($px -ne 0 -or $py -ne 0) { $parts.Add("translate($(-$px) $(-$py))") }
                $open = if ($parts.Count) { '<g transform="' + ($parts -join ' ') + '">' } else { '<g>' }
                $out.Add($indent + $open)
                Convert-Children $n $literal $out $notes ($indent + '  ')
                $out.Add($indent + '</g>')
            }
            'clip-path' {
                $script:clipSeq++
                $id = 'c' + $script:clipSeq
                $cd = ((Get-Attr $n 'pathData') -replace '\s+', ' ').Trim()
                $out.Add($indent + '<clipPath id="' + $id + '"><path d="' + $cd + '"/></clipPath>')
                $out.Add($indent + '<g clip-path="url(#' + $id + ')">')
                # an Android clip-path clips every later sibling in its group
                $rest = New-Object System.Xml.XmlDocument
                $holder = $rest.CreateElement('group', $ANDROID)
                for ($j = $i + 1; $j -lt $kids.Count; $j++) { [void]$holder.AppendChild($rest.ImportNode($kids[$j], $true)) }
                Convert-Children $holder $literal $out $notes ($indent + '  ')
                $out.Add($indent + '</g>')
                $i = $kids.Count
            }
            default { throw "unsupported element <$($n.LocalName)>" }
        }
    }
}

function Convert-Drawable([string] $path, [bool] $literal) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.LoadXml([System.IO.File]::ReadAllText($path).TrimStart([char]0xFEFF))
    $root = $doc.DocumentElement
    if ($root.LocalName -ne 'vector') { throw "root element is <$($root.LocalName)>, not <vector>" }
    $vw = (Get-Attr $root 'viewportWidth').Trim(); $vh = (Get-Attr $root 'viewportHeight').Trim()
    $out = New-Object System.Collections.Generic.List[string]
    $notes = New-Object System.Collections.Generic.List[string]
    $script:clipSeq = 0
    Convert-Children $root $literal $out $notes '  '
    $svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + $vw + '" height="' + $vh + '" viewBox="0 0 ' + $vw + ' ' + $vh + '">' + "`n" +
        ($out -join "`n") + "`n</svg>`n"
    return @{ Svg = $svg; Notes = @($notes | Sort-Object -Unique) }
}

function Resolve-Drawable([string] $ref) {
    $set = 'main'; $module = 'app_v2'; $name = $ref
    if ($ref -match '^(.+?):(.+)$') {
        $name = $Matches[2]
        if ($Matches[1] -eq 'wear') { $module = 'wear' } else { $set = $Matches[1] }
    }
    $dir = Join-Path $RepoRoot "$module/src/$set/res/drawable"
    foreach ($ext in '.xml', '.png', '.webp') {
        $p = Join-Path $dir ($name + $ext)
        if (Test-Path -LiteralPath $p) { return $p }
    }
    return $null
}
