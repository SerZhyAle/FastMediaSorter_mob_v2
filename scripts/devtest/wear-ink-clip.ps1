#requires -Version 7.0
<#
.SYNOPSIS
    S2757 - judge a captured frame the way a Play reviewer does: is anything DRAWN outside the glass.

.DESCRIPTION
    `adb.ps1 clip-check` reads the accessibility tree, and Compose publishes every node there as
    `touchBoundsInRoot` - the layout rectangle INFLATED to the 48 dp minimum touch target around its
    centre. So clip-check answers "is this node fully tappable", which is a real question and not
    this one. Measured 2026-09-08 on the watch calculator: both children of a value row were already
    48 dp, their published boxes therefore overhung the row by 14.9 px right and 14.6 px up, and
    clip-check called them OFF-GLASS - while a pixel read of the SAME frame showed the ink sitting
    exactly where `calculatorShape()` put it, inside the glass.

    Play does not read the tree. It photographs the frame and writes `cut off by the screen edges`.
    This script is the second, independent check that criterion needs, and the two must never be
    merged: a touch target leaving the glass is a usability defect, ink leaving the glass is the
    rejection. Either can hold without the other.

    HOW A PIXEL IS JUDGED.

      1. The glass outline comes from `adb.ps1 clip-check -Json` (`data.shape`), never from a second
         reading of its own - one source, so the two tools cannot disagree about where the glass is
         (the S1621 rule). A circle is just the case where the corner radius equals half the screen,
         so one corner-quadrant rule covers a round watch and a rounded-corner phone alike. An
         offline image declares its shape with -Round / -CornerRadius instead.
      2. Everything beyond that outline, grown by -Feather px, is the OUTSIDE region. The growth is
         not slack: the mask edge is anti-aliased on every real capture, and a rim one pixel wide
         would otherwise be reported on a frame that is correct.
      3. BACKGROUND is the modal colour of the outside region, not an assumed black. A watch frame
         is a square bitmap and the app paints its window background across all of it; assuming
         black would call a correct full-bleed background "ink" on every single frame.
      4. INK is an outside pixel whose largest channel distance from that background exceeds
         -Tolerance, gathered into 8-connected clusters, and a cluster smaller than -MinPixels is
         dropped as capture noise.

    A UNIFORM FRAME IS NOT A CLEAN FRAME. A capture taken while the splash still animates is all one
    colour, and it would pass every test above for the wrong reason. That case exits 2 - "could not
    verify" - because a run that never saw the screen and a run that saw a clean screen must not
    return the same number.

    The uniformity test samples the frame on a stride chosen to keep the sample near 40k pixels,
    while the ink test reads every outside pixel exactly. That split is deliberate: uniformity is a
    property of the whole frame and survives sampling, a single stray glyph does not.

.PARAMETER Image
    PNG to judge. Omitted: a fresh capture is taken through `adb.ps1 shot`.

.PARAMETER DeviceId
    Serial to capture from and read the shape off. Omitted: the single online device is used.

.PARAMETER Round
    Declare the frame round for an offline image. Sets the corner radius to half the shorter edge.

.PARAMETER CornerRadius
    Declare the corner radius in pixels for an offline image. Ignored when -Round is given.

.PARAMETER Tolerance
    Largest per-channel distance from the background still counted as background. Default 24.

.PARAMETER MinPixels
    Smallest ink cluster reported. Default 40.

.PARAMETER Feather
    Pixels the mask is grown by before judging, to skip the anti-aliased rim. Default 2.

.PARAMETER DensityDpi
    Screen density, so the overshoot is also reported in dp. Read from the device when omitted.

.PARAMETER OutDir
    Where a captured frame is written. Default temp/scratch.

.PARAMETER Json
    Emit a result object instead of human lines.

.PARAMETER Help
    Print usage and exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-ink-clip.ps1 -DeviceId emulator-5556 -OutDir temp/S2757

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-ink-clip.ps1 -Image temp/S2757/main.png -Round

.NOTES
    Exit codes:
      0 - clean: no ink outside the glass.
      2 - could not verify: bad arguments, unreadable image, no device, or a uniform frame.
      9 - ink found outside the glass.
#>

[CmdletBinding()]
param(
    [string]$Image,
    [string]$DeviceId,
    [switch]$Round,
    [int]$CornerRadius = -1,
    [int]$Tolerance = 24,
    [int]$MinPixels = 40,
    [int]$Feather = 2,
    [int]$DensityDpi = 0,
    [string]$OutDir,
    [switch]$Json,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adbScript = Join-Path $PSScriptRoot 'adb.ps1'

$result = [ordered]@{
    ok       = $false
    verdict  = 'unknown'
    reason   = ''
    image    = ''
    shape    = $null
    ink      = 0
    outside  = 0
    clusters = @()
    worstPx  = 0.0
    worstDp  = $null
}

function Write-Usage {
    Write-Host 'wear-ink-clip - is anything DRAWN outside the glass (S2757)' -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  -Image <png>          judge a captured frame offline'
    Write-Host '  -DeviceId <serial>    capture a fresh frame and read the shape off that device'
    Write-Host '  -Round                offline image is round (radius = half the shorter edge)'
    Write-Host '  -CornerRadius <px>    offline image has rounded corners of this radius'
    Write-Host '  -Tolerance <n>        channel distance still counted as background (default 24)'
    Write-Host '  -MinPixels <n>        smallest ink cluster reported (default 40)'
    Write-Host '  -Feather <px>         mask growth that skips the anti-aliased rim (default 2)'
    Write-Host '  -DensityDpi <n>       density, so the overshoot is also printed in dp'
    Write-Host '  -OutDir <path>        where a fresh capture is written'
    Write-Host '  -Json                 emit a result object instead of human lines'
    Write-Host ''
    Write-Host '  exit 0 clean | 2 could not verify | 9 ink outside the glass'
}

function Complete-Run {
    param([int]$Code, [string]$Reason, [string]$Verdict)
    $result.verdict = $Verdict
    $result.reason = $Reason
    $result.ok = ($Code -eq 0)
    if ($Json) {
        $result | ConvertTo-Json -Depth 6 -Compress
    } else {
        $colour = switch ($Code) { 0 { 'Green' } 9 { 'Red' } default { 'Yellow' } }
        Write-Host ("{0} - {1}" -f $Verdict.ToUpperInvariant(), $Reason) -ForegroundColor $colour
    }
    exit $Code
}

if ($Help) { Write-Usage; exit 0 }

# ---------- device ----------

# The outline is READ, never assumed, and it is read through clip-check so that the touch-target
# check and the ink check cannot end up judging two different circles on one device. clip-check
# exits non-zero on its own findings; that verdict is about touch targets and is deliberately
# ignored here - only its shape block is consumed.
function Get-ShapeFromDevice {
    param([string]$Id)
    $callArgs = @('clip-check', '-Json')
    if ($Id) { $callArgs += @('-DeviceId', $Id) }
    $raw = (& pwsh -NoProfile -File $adbScript @callArgs 2>$null | Out-String)
    if (-not $raw.Trim()) { return $null }
    try { $parsed = $raw | ConvertFrom-Json } catch { return $null }
    if (-not $parsed.PSObject.Properties.Name.Contains('data')) { return $null }
    if (-not $parsed.data) { return $null }
    if (-not $parsed.data.PSObject.Properties.Name.Contains('shape')) { return $null }
    return $parsed.data.shape
}

function Get-DensityFromDevice {
    param([string]$Id)
    $callArgs = @('shell', '-Cmd', 'wm density')
    if ($Id) { $callArgs += @('-DeviceId', $Id) }
    $raw = (& pwsh -NoProfile -File $adbScript @callArgs 2>$null | Out-String)
    $m = [regex]::Match($raw, 'Override density:\s*(\d+)')
    if (-not $m.Success) { $m = [regex]::Match($raw, 'Physical density:\s*(\d+)') }
    if ($m.Success) { return [int]$m.Groups[1].Value }
    return 0
}

if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'temp/scratch' }

$deviceShape = $null
if (-not $Image) {
    if (-not (Test-Path $adbScript)) {
        Complete-Run 2 "adb helper not found at $adbScript" 'could-not-verify'
    }
    if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
    $shotArgs = @('shot', '-OutDir', $OutDir, '-Json')
    if ($DeviceId) { $shotArgs += @('-DeviceId', $DeviceId) }
    $shotRaw = (& pwsh -NoProfile -File $adbScript @shotArgs 2>$null | Out-String)
    $shotFile = ''
    try {
        $shotParsed = $shotRaw | ConvertFrom-Json
        if ($shotParsed.data -and $shotParsed.data.PSObject.Properties.Name.Contains('file')) {
            $shotFile = [string]$shotParsed.data.file
        }
    } catch { $shotFile = '' }
    if (-not $shotFile -or -not (Test-Path $shotFile)) {
        Complete-Run 2 'could not capture a frame from the device' 'could-not-verify'
    }
    $Image = $shotFile
    $deviceShape = Get-ShapeFromDevice $DeviceId
    if ($DensityDpi -le 0) { $DensityDpi = Get-DensityFromDevice $DeviceId }
}

if (-not (Test-Path $Image)) {
    Complete-Run 2 "image not found: $Image" 'could-not-verify'
}
$result.image = (Resolve-Path $Image).Path

# ---------- pixels ----------

Add-Type -AssemblyName System.Drawing
try {
    $bmp = [System.Drawing.Bitmap]::new($result.image)
} catch {
    Complete-Run 2 "could not read the image: $($_.Exception.Message)" 'could-not-verify'
}

$w = $bmp.Width
$h = $bmp.Height

$radius = -1
if ($deviceShape) {
    $radius = [int]$deviceShape.radius
    # The device reports its shape in its own pixel space; a capture scaled away from it would make
    # every radius comparison meaningless, so that case is refused rather than rescaled silently.
    if ([int]$deviceShape.width -ne $w -or [int]$deviceShape.height -ne $h) {
        $bmp.Dispose()
        Complete-Run 2 ("capture {0}x{1} does not match the reported display {2}x{3}" -f `
                $w, $h, [int]$deviceShape.width, [int]$deviceShape.height) 'could-not-verify'
    }
} elseif ($Round) {
    $radius = [int]([Math]::Min($w, $h) / 2)
} elseif ($CornerRadius -ge 0) {
    $radius = $CornerRadius
}

if ($radius -lt 0) {
    $bmp.Dispose()
    Complete-Run 2 'no display shape: pass -DeviceId, -Round or -CornerRadius' 'could-not-verify'
}

$result.shape = [ordered]@{
    width  = $w
    height = $h
    radius = $radius
    round  = ($radius * 2 -eq $w -and $radius * 2 -eq $h)
}

$rect = [System.Drawing.Rectangle]::new(0, 0, $w, $h)
$locked = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $locked.Stride
$bytes = [byte[]]::new($stride * $h)
[System.Runtime.InteropServices.Marshal]::Copy($locked.Scan0, $bytes, 0, $bytes.Length)
$bmp.UnlockBits($locked)
$bmp.Dispose()

function Get-QuantKey {
    param([byte]$R, [byte]$G, [byte]$B)
    return (([int]$R -shr 3) -shl 10) -bor (([int]$G -shr 3) -shl 5) -bor ([int]$B -shr 3)
}

# Uniformity is a property of the whole frame, so a stride sample answers it; the stride is chosen
# from the frame size rather than fixed, so a phone capture costs the same as a watch one.
$step = [Math]::Max(1, [int][Math]::Ceiling([Math]::Sqrt(($w * $h) / 40000.0)))
$frameHist = @{}
$sampled = 0
for ($y = 0; $y -lt $h; $y += $step) {
    $rowBase = $y * $stride
    for ($x = 0; $x -lt $w; $x += $step) {
        $o = $rowBase + $x * 4
        $key = Get-QuantKey $bytes[$o + 2] $bytes[$o + 1] $bytes[$o]
        if ($frameHist.ContainsKey($key)) { $frameHist[$key]++ } else { $frameHist[$key] = 1 }
        $sampled++
    }
}
$dominant = ($frameHist.Values | Measure-Object -Maximum).Maximum
if ($dominant -ge ($sampled * 0.995)) {
    Complete-Run 2 'frame is a single colour - nothing was rendered yet' 'could-not-verify'
}

if ($radius -le 0) {
    Complete-Run 0 'plain rectangle - no corner can cut content' 'clean'
}

# The outside region is walked directly instead of testing every pixel of the frame: for a row in a
# corner band, the inside span is an arc segment, so both outside runs on that row are closed-form.
$grown = $radius + $Feather
$outsideIdx = [System.Collections.Generic.List[int]]::new()
$bgHist = @{}
for ($y = 0; $y -lt $h; $y++) {
    $cy = if ($y -lt $radius) { $radius } elseif ($y -gt $h - 1 - $radius) { $h - 1 - $radius } else { -1 }
    if ($cy -lt 0) { continue }
    $dy = $y - $cy
    $span = ($grown * $grown) - ($dy * $dy)
    $half = if ($span -gt 0) { [Math]::Sqrt($span) } else { -1.0 }
    $leftEnd = if ($half -lt 0) { $radius - 1 } else { [int][Math]::Ceiling($radius - $half) - 1 }
    $rightStart = if ($half -lt 0) { $w - $radius } else { $w - 1 - $radius + [int][Math]::Floor($half) + 1 }
    $rowBase = $y * $stride
    # The comma binds tighter than the minus, so every arithmetic element of an array literal is
    # parenthesised here: @(3, $w - 1) parses as ($3, $w) - 1 and dies on op_Subtraction.
    $leftStop = [Math]::Min($leftEnd, ($radius - 1))
    $rightFrom = [Math]::Max($rightStart, ($w - $radius))
    $ranges = @(, @(0, $leftStop))
    $ranges += , @($rightFrom, ($w - 1))
    foreach ($range in $ranges) {
        for ($x = $range[0]; $x -le $range[1]; $x++) {
            if ($x -lt 0 -or $x -ge $w) { continue }
            $o = $rowBase + $x * 4
            $key = Get-QuantKey $bytes[$o + 2] $bytes[$o + 1] $bytes[$o]
            if ($bgHist.ContainsKey($key)) { $bgHist[$key]++ } else { $bgHist[$key] = 1 }
            $outsideIdx.Add($y * $w + $x)
        }
    }
}

$result.outside = $outsideIdx.Count
if ($outsideIdx.Count -eq 0) {
    Complete-Run 0 'no region outside the glass to judge' 'clean'
}

$bgKey = ($bgHist.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 1).Key
$bgR = (($bgKey -shr 10) -band 31) -shl 3
$bgG = (($bgKey -shr 5) -band 31) -shl 3
$bgB = ($bgKey -band 31) -shl 3

$inkSet = [System.Collections.Generic.HashSet[int]]::new()
foreach ($idx in $outsideIdx) {
    $x = $idx % $w
    $y = [int][Math]::Floor($idx / $w)
    $o = $y * $stride + $x * 4
    $dr = [Math]::Abs([int]$bytes[$o + 2] - $bgR)
    $dg = [Math]::Abs([int]$bytes[$o + 1] - $bgG)
    $db = [Math]::Abs([int]$bytes[$o] - $bgB)
    if ([Math]::Max($dr, [Math]::Max($dg, $db)) -gt $Tolerance) { [void]$inkSet.Add($idx) }
}

if ($inkSet.Count -eq 0) {
    Complete-Run 0 ("{0} pixel(s) outside the glass, all background" -f $result.outside) 'clean'
}

function Get-Overshoot {
    param([int]$X, [int]$Y, [int]$W, [int]$H, [int]$R)
    $cx = if ($X -lt $R) { $R } elseif ($X -gt $W - 1 - $R) { $W - 1 - $R } else { -1 }
    $cy = if ($Y -lt $R) { $R } elseif ($Y -gt $H - 1 - $R) { $H - 1 - $R } else { -1 }
    if ($cx -lt 0 -or $cy -lt 0) { return -1.0 }
    $dx = $X - $cx
    $dy = $Y - $cy
    return ([Math]::Sqrt($dx * $dx + $dy * $dy) - $R)
}

# 8-connected clusters, so a diagonal glyph stroke is one finding rather than a dotted line of
# findings the operator has to reassemble by eye.
$seen = [System.Collections.Generic.HashSet[int]]::new()
$clusters = [System.Collections.Generic.List[object]]::new()
$worst = 0.0
foreach ($start in $inkSet) {
    if ($seen.Contains($start)) { continue }
    $stack = [System.Collections.Generic.Stack[int]]::new()
    $stack.Push($start)
    [void]$seen.Add($start)
    $count = 0
    $x1 = $w; $y1 = $h; $x2 = -1; $y2 = -1
    $localWorst = 0.0
    while ($stack.Count -gt 0) {
        $cur = $stack.Pop()
        $cx = $cur % $w
        $cy = [int][Math]::Floor($cur / $w)
        $count++
        if ($cx -lt $x1) { $x1 = $cx }
        if ($cy -lt $y1) { $y1 = $cy }
        if ($cx -gt $x2) { $x2 = $cx }
        if ($cy -gt $y2) { $y2 = $cy }
        $ov = Get-Overshoot $cx $cy $w $h $radius
        if ($ov -gt $localWorst) { $localWorst = $ov }
        for ($dy = -1; $dy -le 1; $dy++) {
            for ($dx = -1; $dx -le 1; $dx++) {
                $nx = $cx + $dx
                $ny = $cy + $dy
                if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $w -or $ny -ge $h) { continue }
                $n = $ny * $w + $nx
                if ($seen.Contains($n)) { continue }
                if (-not $inkSet.Contains($n)) { continue }
                [void]$seen.Add($n)
                $stack.Push($n)
            }
        }
    }
    if ($count -ge $MinPixels) {
        $clusters.Add([ordered]@{
                pixels      = $count
                x1          = $x1
                y1          = $y1
                x2          = $x2
                y2          = $y2
                overshootPx = [Math]::Round($localWorst, 1)
            })
        if ($localWorst -gt $worst) { $worst = $localWorst }
    }
}

if ($clusters.Count -eq 0) {
    Complete-Run 0 ("{0} stray pixel(s) outside the glass, every cluster below -MinPixels {1}" -f $inkSet.Count, $MinPixels) 'clean'
}

$result.ink = ($clusters | ForEach-Object { $_.pixels } | Measure-Object -Sum).Sum
$result.clusters = @($clusters | Sort-Object -Property pixels -Descending | Select-Object -First 10)
$result.worstPx = [Math]::Round($worst, 1)
if ($DensityDpi -gt 0) { $result.worstDp = [Math]::Round($worst / $DensityDpi * 160, 1) }

if (-not $Json) {
    Write-Host ("SHAPE {0}x{1} radius {2}{3}" -f $w, $h, $radius, $(if ($result.shape.round) { ' (round)' } else { '' })) -ForegroundColor Gray
    Write-Host ("BACKGROUND rgb({0},{1},{2}) from {3} pixel(s) outside the glass" -f $bgR, $bgG, $bgB, $result.outside) -ForegroundColor Gray
    foreach ($c in $result.clusters) {
        Write-Host ("  INK {0,6} px  box [{1},{2}]-[{3},{4}]  overshoot {5} px" -f `
                $c.pixels, $c.x1, $c.y1, $c.x2, $c.y2, $c.overshootPx) -ForegroundColor Red
    }
}
$dpNote = if ($null -ne $result.worstDp) { ' ({0} dp)' -f $result.worstDp } else { '' }
Complete-Run 9 ("{0} ink cluster(s) outside the glass, worst overshoot {1} px{2}" -f $clusters.Count, $result.worstPx, $dpNote) 'ink-outside-glass'
