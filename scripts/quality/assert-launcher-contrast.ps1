<#
.SYNOPSIS
    S1895: measure the launcher's foreground colours against the surfaces they land on.

.DESCRIPTION
    The launcher taskbar and the Start panel paint their text and icons from six launcher-scoped
    theme attributes. Each theme - the base day theme, the base night theme and the six
    ThemeOverlay.FastMediaSorter.* colour themes - supplies a value for every attribute, either its
    own or the base theme's by inheritance. This gate resolves each attribute to a literal colour
    for every theme, resolves the background role underneath it the same way, and computes the
    WCAG 2.x contrast ratio.

    The threshold is 7:1, the owner's ruling of 2026-08-21 (strategic ADR-3), above the 4.5:1 that
    WCAG requires for ordinary text. The previous change to these same colours was closed on a
    visual check and shipped a pair at 4.22:1; this gate is the form of ADR-2 that survives the
    next edit.

    Nothing here is hardcoded except the pair list and the threshold: every colour is read from the
    resource files, so a change to a value or to a theme is measured rather than assumed.

.PARAMETER Quiet
    Print only the verdict line and any shortfall. Used by assert-fast-gates.ps1.

.PARAMETER Gate
    Accepted because assert-fast-gates.ps1 passes it to every gate it runs. It changes nothing
    here: a pair below the threshold exits 1 with or without it. Several gates in this repo are
    advisory unless -Gate is passed, which makes a bare run exit 0 while holding findings; a
    contrast shortfall is a defect in every context, so this one is never advisory.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-launcher-contrast.ps1 -Gate -Quiet

.NOTES
    Exit codes:
      0 - every measured pair is at or above the threshold.
      1 - at least one pair is below the threshold, or an attribute resolves to nothing.
      2 - a resource file is missing or unparseable; the gate could not measure.
#>
[CmdletBinding()]
param(
    [switch]$Quiet,
    [switch]$Gate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resRoot = Join-Path $repoRoot 'app_v2/src/main/res'

$paths = @{
    DayColors   = Join-Path $resRoot 'values/colors.xml'
    NightColors = Join-Path $resRoot 'values-night/colors.xml'
    DayThemes   = Join-Path $resRoot 'values/themes.xml'
    NightThemes = Join-Path $resRoot 'values-night/themes.xml'
}

foreach ($key in $paths.Keys) {
    if (-not (Test-Path -LiteralPath $paths[$key])) {
        Write-Host "assert-launcher-contrast: CANNOT VERIFY - missing $($paths[$key])"
        exit 2
    }
}

# The threshold and the pairs are the gate's own contract, not data read from the tree.
$Threshold = 7.0
$RowAttrs = @('launcherStartRowGroup1', 'launcherStartRowGroup2', 'launcherStartRowGroup3', 'launcherStartRowGroup4')
$TaskbarAttrs = @('launcherTaskbarStartText', 'launcherTaskbarAllAppsText')
$RowBackgroundRole = 'colorSurface'
$TaskbarBackgroundRole = 'colorSurfaceVariant'

function Get-ColorMap {
    param([string]$Path)
    $map = @{}
    $xml = [xml](Get-Content -LiteralPath $Path -Raw)
    foreach ($node in $xml.SelectNodes('/resources/color')) {
        $map[$node.GetAttribute('name')] = $node.InnerText.Trim()
    }
    return $map
}

function Get-StyleItems {
    param([string]$Path, [string]$StyleName)
    $items = @{}
    $xml = [xml](Get-Content -LiteralPath $Path -Raw)
    # GetAttribute, not the dotted form: XmlElement already owns a .Name property, so $style.name
    # returns the element name "style" and every comparison silently misses.
    foreach ($style in $xml.SelectNodes('/resources/style')) {
        if ($style.GetAttribute('name') -ne $StyleName) { continue }
        foreach ($item in $style.SelectNodes('item')) {
            $items[$item.GetAttribute('name')] = $item.InnerText.Trim()
        }
    }
    return $items
}

function Resolve-Color {
    param([hashtable]$Items, [hashtable]$Palette, [string]$Attr)
    if (-not $Items.ContainsKey($Attr)) { return $null }
    $value = $Items[$Attr]
    if ($value -match '^#') { return $value }
    if ($value -match '^@color/(.+)$') {
        $name = $Matches[1]
        if ($Palette.ContainsKey($name)) { return $Palette[$name] }
        return $null
    }
    return $null
}

function Get-Luminance {
    param([string]$Hex)
    $h = $Hex.TrimStart('#')
    if ($h.Length -eq 8) { $h = $h.Substring(2) }
    $channels = 0..2 | ForEach-Object { [Convert]::ToInt32($h.Substring($_ * 2, 2), 16) / 255.0 }
    $linear = $channels | ForEach-Object {
        if ($_ -le 0.03928) { $_ / 12.92 } else { [Math]::Pow((($_ + 0.055) / 1.055), 2.4) }
    }
    return 0.2126 * $linear[0] + 0.7152 * $linear[1] + 0.0722 * $linear[2]
}

function Get-ContrastRatio {
    param([string]$Foreground, [string]$Background)
    $a = Get-Luminance $Foreground
    $b = Get-Luminance $Background
    $hi = [Math]::Max($a, $b)
    $lo = [Math]::Min($a, $b)
    return [Math]::Round((($hi + 0.05) / ($lo + 0.05)), 2)
}

$dayPalette = Get-ColorMap $paths.DayColors
$nightPalette = Get-ColorMap $paths.NightColors

# A night-qualified palette overrides the default one name by name; anything it omits still resolves.
$nightResolved = @{}
foreach ($k in $dayPalette.Keys) { $nightResolved[$k] = $dayPalette[$k] }
foreach ($k in $nightPalette.Keys) { $nightResolved[$k] = $nightPalette[$k] }

$baseDay = Get-StyleItems $paths.DayThemes 'Theme.FastMediaSorter.App'
$baseNight = Get-StyleItems $paths.NightThemes 'Theme.FastMediaSorter.App'

# Each overlay pins its own brightness, so a dark overlay resolves names from the night palette and
# a light one from the day palette. Anything an overlay does not override keeps the base value.
$themes = @(
    @{ Name = 'base-day'; Items = $baseDay; Palette = $dayPalette }
    @{ Name = 'base-night'; Items = $baseNight; Palette = $nightResolved }
)

$overlayNames = @('DarkGreen', 'DarkBlue', 'DarkRed', 'LightGreen', 'LightBlue', 'LightRed')
foreach ($overlay in $overlayNames) {
    $isDark = $overlay.StartsWith('Dark')
    $baseItems = if ($isDark) { $baseNight } else { $baseDay }
    $palette = if ($isDark) { $nightResolved } else { $dayPalette }

    $merged = @{}
    foreach ($k in $baseItems.Keys) { $merged[$k] = $baseItems[$k] }
    $own = Get-StyleItems $paths.DayThemes "ThemeOverlay.FastMediaSorter.$overlay"
    foreach ($k in $own.Keys) { $merged[$k] = $own[$k] }

    $themes += @{ Name = "overlay-$overlay"; Items = $merged; Palette = $palette }
}

$failures = @()
$measured = 0

foreach ($theme in $themes) {
    $pairs = @()
    foreach ($attr in $RowAttrs) { $pairs += @{ Fg = $attr; BgRole = $RowBackgroundRole } }
    foreach ($attr in $TaskbarAttrs) { $pairs += @{ Fg = $attr; BgRole = $TaskbarBackgroundRole } }

    foreach ($pair in $pairs) {
        $fg = Resolve-Color -Items $theme.Items -Palette $theme.Palette -Attr $pair.Fg
        $bg = Resolve-Color -Items $theme.Items -Palette $theme.Palette -Attr $pair.BgRole

        if (-not $fg) {
            $failures += "$($theme.Name): $($pair.Fg) resolves to nothing - no theme defines it and no palette entry matches"
            continue
        }
        if (-not $bg) {
            $failures += "$($theme.Name): background role $($pair.BgRole) resolves to nothing"
            continue
        }

        $ratio = Get-ContrastRatio $fg $bg
        $measured++
        if (-not $Quiet) {
            "{0,-18} {1,-28} {2} on {3}  {4,6}:1" -f $theme.Name, $pair.Fg, $fg, $bg, $ratio
        }
        if ($ratio -lt $Threshold) {
            $failures += "$($theme.Name): $($pair.Fg) $fg on $($pair.BgRole) $bg measures ${ratio}:1, below ${Threshold}:1"
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "assert-launcher-contrast: FAIL ($($failures.Count) problem(s); $measured pair(s) measured cleanly)"
    foreach ($f in $failures) { Write-Host "  $f" }
    exit 1
}

Write-Host "assert-launcher-contrast: PASS ($measured pairs at or above ${Threshold}:1 across $($themes.Count) themes)"
exit 0
