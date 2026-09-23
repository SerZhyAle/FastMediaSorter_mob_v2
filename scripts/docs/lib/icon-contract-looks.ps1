#requires -Version 7.0
<#
.SYNOPSIS
    The palette and the decorated look of ICON-RENDER 0.10 section 10 items B, D and E, for the icon
    contract exporter.

.DESCRIPTION
    Dot-source after scripts/docs/lib/icon-contract-svg.ps1 (it needs Resolve-Colour and Split-Argb).
      Get-ContrastRatio <a> <b>                 WCAG 2.1 contrast of two #RRGGBB colours
      Get-OnPlateColour <plate>                 '#FFFFFF' when white reaches 3:1 on the plate, else '#1F1F1F'
      Get-IconPalette <day> <night> <accentDay> <accentNight> <surfaceDay> <surfaceNight>
                                                ordered hue key -> day, night, plate, onPlate and ratios
      New-DecoratedLookSvg <glyphSvg> <plate> <onPlate>
                                                the glyph's 24 grid at 0.6 of a 40-unit circle plate
    Not a script: no exit codes. A hue whose colour resource is missing is left out of the palette,
    so the exporter's hue validation names it.
#>

$script:OnPlateDark = '#1F1F1F'
$script:PlateSide = 40
$script:GlyphShare = 0.6

function Get-RelativeLuminance([string] $hex) {
    $h = $hex.TrimStart('#')
    $c = 0..2 | ForEach-Object {
        $v = [Convert]::ToInt32($h.Substring($_ * 2, 2), 16) / 255.0
        if ($v -le 0.03928) { $v / 12.92 } else { [Math]::Pow(($v + 0.055) / 1.055, 2.4) }
    }
    return 0.2126 * $c[0] + 0.7152 * $c[1] + 0.0722 * $c[2]
}

function Get-ContrastRatio([string] $a, [string] $b) {
    $la = Get-RelativeLuminance $a; $lb = Get-RelativeLuminance $b
    $hi = [Math]::Max($la, $lb); $lo = [Math]::Min($la, $lb)
    return [Math]::Round(($hi + 0.05) / ($lo + 0.05), 2)
}

function Get-OnPlateColour([string] $plate) {
    if ((Get-ContrastRatio $plate '#FFFFFF') -ge 3.0) { return '#FFFFFF' }
    return $script:OnPlateDark
}

# The shared hue keys and the colour resource each reads (item D). accent is the product's own.
$script:HueResources = [ordered]@{
    'category.image'    = 'color_media_image'
    'category.video'    = 'color_media_video'
    'category.audio'    = 'color_media_music'
    'category.document' = 'color_media_docs'
    'category.other'    = 'color_media_other'
    'source.local'      = 'color_source_local'
    'source.smb'        = 'color_source_smb'
    'source.sftp'       = 'color_source_sftp'
    'source.ftp'        = 'color_source_ftp'
    'source.cloud'      = 'color_source_cloud'
    'state.ok'          = 'success_color'
    'state.warning'     = 'warning_color'
    'state.error'       = 'error_color'
}

function Get-IconPalette([hashtable] $dayMap, [hashtable] $nightMap, [string] $accentDay, [string] $accentNight,
                         [string] $surfaceDay, [string] $surfaceNight) {
    $pal = [ordered]@{}
    $pairs = [ordered]@{ 'accent' = @($accentDay, $accentNight) }
    foreach ($k in $script:HueResources.Keys) {
        $d = Resolve-Colour ('@color/' + $script:HueResources[$k]) $dayMap
        $n = Resolve-Colour ('@color/' + $script:HueResources[$k]) $nightMap
        if ($d -and $n) { $pairs[$k] = @((Split-Argb $d)[0], (Split-Argb $n)[0]) }
    }
    foreach ($k in $pairs.Keys) {
        $day = $pairs[$k][0]; $night = $pairs[$k][1]
        $on = Get-OnPlateColour $day
        $pal[$k] = [ordered]@{
            day             = $day
            night           = $night
            plate           = $day
            onPlate         = $on
            dayContrast     = Get-ContrastRatio $day $surfaceDay
            nightContrast   = Get-ContrastRatio $night $surfaceNight
            onPlateContrast = Get-ContrastRatio $on $day
            source          = $(if ($k -eq 'accent') { 'colorPrimary of the base themes' } else { $script:HueResources[$k] })
        }
    }
    return $pal
}

function New-DecoratedLookSvg([string] $glyphSvg, [string] $plate, [string] $onPlate) {
    $vb = [regex]::Match($glyphSvg, 'viewBox="0 0 ([0-9.]+) ([0-9.]+)"')
    $vw = [double]::Parse($vb.Groups[1].Value, [cultureinfo]::InvariantCulture)
    $inner = ($glyphSvg -replace '(?s)^<svg[^>]*>', '' -replace '(?s)</svg>\s*$', '').Trim("`n")
    $grid = $script:PlateSide * $script:GlyphShare
    $offset = ($script:PlateSide - $grid) / 2
    $scale = [Math]::Round($grid / $vw, 4)
    $r = $script:PlateSide / 2
    $t = 'translate(' + $offset.ToString([cultureinfo]::InvariantCulture) + ' ' + $offset.ToString([cultureinfo]::InvariantCulture) + ')'
    if ($scale -ne 1) { $t += ' scale(' + $scale.ToString([cultureinfo]::InvariantCulture) + ')' }
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + $script:PlateSide + '" height="' + $script:PlateSide +
        '" viewBox="0 0 ' + $script:PlateSide + ' ' + $script:PlateSide + '">' + "`n" +
        '  <circle cx="' + $r + '" cy="' + $r + '" r="' + $r + '" fill="' + $plate + '"/>' + "`n" +
        '  <g transform="' + $t + '" color="' + $onPlate + '">' + "`n" + $inner + "`n  </g>`n</svg>`n"
}
