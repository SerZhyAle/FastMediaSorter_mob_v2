#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/devtest/wear-ink-clip.ps1 (S2757).

.DESCRIPTION
    The frames are DRAWN here rather than captured, so the suite needs no watch, no emulator and no
    adb. That is the point: a shape gate whose own correctness can only be checked on hardware is a
    gate nobody re-checks, which is the failure S2757 section 2.2 recorded against the touch-target
    check it replaces half of.

    Every case pins one decision the script makes, and the three exit codes are each pinned by name:
    0 clean, 2 could not verify, 9 ink outside the glass. A case that only asserted "not zero" would
    pass on the wrong reason, and "could not verify" versus "found ink" is exactly the distinction
    the caller acts on differently.

.PARAMETER KeepArtifacts
    Leave the generated frames on disk for inspection instead of deleting them.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>

[CmdletBinding()]
param([switch]$KeepArtifacts)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$target = Join-Path $repoRoot 'scripts/devtest/wear-ink-clip.ps1'
$workDir = Join-Path $repoRoot 'temp/scratch/wear-ink-clip-tests'

if (-not (Test-Path $target)) {
    Write-Error "target script not found: $target" -ErrorAction Continue
    exit 1
}
if (Test-Path $workDir) { Remove-Item $workDir -Recurse -Force }
New-Item -ItemType Directory -Path $workDir -Force | Out-Null

$script:passed = 0
$script:failed = 0

function New-Frame {
    param(
        [string]$Name,
        [int]$Size = 384,
        [switch]$Rounded,
        [int]$CornerRadius = 0,
        [System.Drawing.Color]$Background = [System.Drawing.Color]::Black,
        [scriptblock]$Paint
    )
    $bmp = [System.Drawing.Bitmap]::new($Size, $Size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear($Background)
    if ($Paint) { & $Paint $g $bmp }
    $g.Dispose()
    $path = Join-Path $workDir "$Name.png"
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return $path
}

function Invoke-Target {
    param([string[]]$Arguments)
    $out = & pwsh -NoProfile -File $target @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ code = $LASTEXITCODE; text = $out }
}

function Assert-Case {
    param([string]$Name, [int]$Expected, [string[]]$Arguments)
    $r = Invoke-Target $Arguments
    if ($r.code -eq $Expected) {
        $script:passed++
        Write-Host ("PASS  {0} (exit {1})" -f $Name, $r.code) -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host ("FAIL  {0}: expected exit {1}, got {2}" -f $Name, $Expected, $r.code) -ForegroundColor Red
        Write-Host ($r.text.Trim()) -ForegroundColor DarkGray
    }
}

# The app's own background painted across the whole square bitmap, which is what a real watch
# capture looks like: correct, and indistinguishable from ink to any check that assumes black.
$fillDisc = {
    param($g, $bmp)
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(20, 24, 30))
    $g.FillRectangle($brush, 0, 0, $bmp.Width, $bmp.Height)
    $inner = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(230, 230, 235))
    $g.FillEllipse($inner, 90, 150, 200, 60)
    $brush.Dispose(); $inner.Dispose()
}

$cleanRound = New-Frame -Name 'clean-round' -Paint $fillDisc
Assert-Case -Name 'round frame, content well inside the circle -> clean' -Expected 0 `
    -Arguments @('-Image', $cleanRound, '-Round')

# A bar running the full width of a round frame, placed HIGH rather than at the vertical centre:
# at the centre a circle already spans the full width and nothing is cut, so a bar there would be a
# case that proves nothing. At y=60 the circle is 279 px wide and both ends of the bar sit on black
# glass - exactly the geometry Play photographs and calls `cut off by the screen edges`.
$inkRound = New-Frame -Name 'ink-round' -Paint {
    param($g, $bmp)
    $bg = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(20, 24, 30))
    $g.FillRectangle($bg, 0, 0, $bmp.Width, $bmp.Height)
    $ink = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(240, 240, 245))
    $g.FillRectangle($ink, 0, 60, $bmp.Width, 44)
    $bg.Dispose(); $ink.Dispose()
}
Assert-Case -Name 'round frame, full-width bar leaves the circle -> ink' -Expected 9 `
    -Arguments @('-Image', $inkRound, '-Round')

$blank = New-Frame -Name 'blank'
Assert-Case -Name 'uniform frame (splash still animating) -> could not verify' -Expected 2 `
    -Arguments @('-Image', $blank, '-Round')

# One pixel of rim noise is what every anti-aliased capture carries; a gate that fires on it is a
# gate switched off within a day.
$rim = New-Frame -Name 'rim-noise' -Paint {
    param($g, $bmp)
    $bg = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(20, 24, 30))
    $g.FillRectangle($bg, 0, 0, $bmp.Width, $bmp.Height)
    $inner = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(230, 230, 235))
    $g.FillEllipse($inner, 100, 160, 180, 50)
    $bg.Dispose(); $inner.Dispose()
    for ($i = 0; $i -lt 12; $i++) {
        $bmp.SetPixel(4 + $i * 3, 4, [System.Drawing.Color]::FromArgb(255, 255, 255))
    }
}
Assert-Case -Name 'scattered single pixels stay under -MinPixels -> clean' -Expected 0 `
    -Arguments @('-Image', $rim, '-Round')

# A rounded RECTANGLE is judged by its four corner arcs, so content that would leave a circle of the
# same size is legitimately inside this shape. Same rule, different radius - no watch-only branch.
$rectFrame = New-Frame -Name 'rounded-rect' -Paint $fillDisc
Assert-Case -Name 'rounded rectangle, corner arcs only -> clean' -Expected 0 `
    -Arguments @('-Image', $rectFrame, '-CornerRadius', '40')

Assert-Case -Name 'no shape declared -> could not verify' -Expected 2 `
    -Arguments @('-Image', $cleanRound)

Assert-Case -Name 'missing image -> could not verify' -Expected 2 `
    -Arguments @('-Image', (Join-Path $workDir 'absent.png'), '-Round')

Assert-Case -Name '-Help exits clean' -Expected 0 -Arguments @('-Help')

if (-not $KeepArtifacts) { Remove-Item $workDir -Recurse -Force -ErrorAction SilentlyContinue }

Write-Host ''
Write-Host ("wear-ink-clip contract: {0} passed, {1} failed" -f $script:passed, $script:failed) `
    -ForegroundColor $(if ($script:failed -gt 0) { 'Red' } else { 'Cyan' })
if ($script:failed -gt 0) { exit 1 }
exit 0
