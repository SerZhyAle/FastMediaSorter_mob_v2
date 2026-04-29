<#
.SYNOPSIS
    Generates VR-flavor launcher icons by compositing a "VR" badge onto the standard icons.
.DESCRIPTION
    Takes the main (standard) launcher icons and overlays a purple circle badge
    with white "VR" text in the top-right corner. Generates:
      - ic_launcher_adaptive_fore.png  (foreground with VR badge)
      - ic_launcher_adaptive_back.png  (copied from main, unchanged)
      - ic_launcher.png               (legacy icon with VR badge)
    for all 5 densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).
.NOTES
    Re-run this script whenever the base icon changes.
#>

param(
    [string]$AppRoot = (Join-Path $PSScriptRoot "..\..\app_v2")
)

Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Drawing

$mainRes = Join-Path $AppRoot "src\main\res"
$vrRes = Join-Path $AppRoot "src\vr\res"

# Adaptive foreground canvas sizes and legacy icon sizes per density
$densities = [ordered]@{
    "mdpi"    = @{ fore = 108; icon = 48 }
    "hdpi"    = @{ fore = 162; icon = 72 }
    "xhdpi"   = @{ fore = 216; icon = 96 }
    "xxhdpi"  = @{ fore = 324; icon = 144 }
    "xxxhdpi" = @{ fore = 432; icon = 192 }
}

# Badge color — Material Deep Purple 600 (#5E35B1)
$badgeColor = [System.Drawing.Color]::FromArgb(255, 94, 53, 177)

# --- xxxhdpi base reference values (others scaled proportionally) ---
# Foreground badge (on 432×432 canvas)
$baseForeSize = 432
$baseBadgeDiam = 150   # badge circle diameter
$baseBadgeCX = 320   # badge center X
$baseBadgeCY = 105   # badge center Y
$baseFontSize = 64    # "VR" font size in pixels

# Legacy icon badge (on 192×192 canvas)
$baseIconSize = 192
$baseIBadgeDiam = 70
$baseIFontSize = 30

foreach ($dpi in $densities.Keys) {
    $foreSize = $densities[$dpi].fore
    $iconSize = $densities[$dpi].icon
    $foreScale = $foreSize / $baseForeSize
    $iconScale = $iconSize / $baseIconSize

    $outDir = Join-Path $vrRes "mipmap-$dpi"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    # ── 1. Adaptive foreground: arrows + VR badge ──────────────────────
    $srcForePath = Join-Path $mainRes "mipmap-$dpi\ic_launcher_adaptive_fore.png"
    $baseFore = [System.Drawing.Image]::FromFile($srcForePath)

    $bmp = New-Object System.Drawing.Bitmap($foreSize, $foreSize)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.DrawImage($baseFore, 0, 0, $foreSize, $foreSize)

    # Badge circle
    $bd = [int]($baseBadgeDiam * $foreScale)
    $bx = [int]($baseBadgeCX * $foreScale)
    $by = [int]($baseBadgeCY * $foreScale)
    $brush = New-Object System.Drawing.SolidBrush($badgeColor)
    $rect = New-Object System.Drawing.Rectangle(($bx - $bd / 2), ($by - $bd / 2), $bd, $bd)
    $g.FillEllipse($brush, $rect)

    # "VR" text
    $fs = [Math]::Max(8, [int]($baseFontSize * $foreScale))
    $font = New-Object System.Drawing.Font("Arial", $fs, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $tb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $sf.LineAlignment = 'Center'
    $tr = New-Object System.Drawing.RectangleF(($bx - $bd / 2), ($by - $bd / 2), $bd, $bd)
    $g.DrawString("VR", $font, $tb, $tr, $sf)

    $dstFore = Join-Path $outDir "ic_launcher_adaptive_fore.png"
    $bmp.Save($dstFore, [System.Drawing.Imaging.ImageFormat]::Png)

    $sf.Dispose(); $tb.Dispose(); $font.Dispose(); $brush.Dispose()
    $g.Dispose(); $bmp.Dispose(); $baseFore.Dispose()

    # ── 2. Adaptive background: copy unchanged ─────────────────────────
    $srcBack = Join-Path $mainRes "mipmap-$dpi\ic_launcher_adaptive_back.png"
    $dstBack = Join-Path $outDir "ic_launcher_adaptive_back.png"
    Copy-Item $srcBack $dstBack -Force

    # ── 3. Legacy icon: square icon + VR badge ─────────────────────────
    $srcIconPath = Join-Path $mainRes "mipmap-$dpi\ic_launcher.png"
    $baseIcon = [System.Drawing.Image]::FromFile($srcIconPath)

    $bmpI = New-Object System.Drawing.Bitmap($iconSize, $iconSize)
    $gi = [System.Drawing.Graphics]::FromImage($bmpI)
    $gi.SmoothingMode = 'AntiAlias'
    $gi.TextRenderingHint = 'AntiAliasGridFit'
    $gi.InterpolationMode = 'HighQualityBicubic'
    $gi.DrawImage($baseIcon, 0, 0, $iconSize, $iconSize)

    # Badge on legacy icon (top-right corner)
    $ibd = [int]($baseIBadgeDiam * $iconScale)
    $ibx = [int]($iconSize - $ibd / 2 - 6 * $iconScale)
    $iby = [int]($ibd / 2 + 6 * $iconScale)
    $iBrush = New-Object System.Drawing.SolidBrush($badgeColor)
    $iRect = New-Object System.Drawing.Rectangle(($ibx - $ibd / 2), ($iby - $ibd / 2), $ibd, $ibd)
    $gi.FillEllipse($iBrush, $iRect)

    $ifs = [Math]::Max(6, [int]($baseIFontSize * $iconScale))
    $iFont = New-Object System.Drawing.Font("Arial", $ifs, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $iTb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $iSf = New-Object System.Drawing.StringFormat
    $iSf.Alignment = 'Center'
    $iSf.LineAlignment = 'Center'
    $iTr = New-Object System.Drawing.RectangleF(($ibx - $ibd / 2), ($iby - $ibd / 2), $ibd, $ibd)
    $gi.DrawString("VR", $iFont, $iTb, $iTr, $iSf)

    $dstIcon = Join-Path $outDir "ic_launcher.png"
    $bmpI.Save($dstIcon, [System.Drawing.Imaging.ImageFormat]::Png)

    $iSf.Dispose(); $iTb.Dispose(); $iFont.Dispose(); $iBrush.Dispose()
    $gi.Dispose(); $bmpI.Dispose(); $baseIcon.Dispose()

    Write-Host "[OK] $dpi  fore=$($foreSize)px  icon=$($iconSize)px"
}

Write-Host "`nVR icons generated at: $vrRes"
