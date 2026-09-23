<#
.SYNOPSIS
  S2380 - classifies one captured frame as carrying signal or not.

.DESCRIPTION
  Pure function over one image file: no device, no adb, no writes. It lives here because two
  consumers must answer "is this frame worth reading" identically - the screenshot-audit discovery
  scan and the UI-sweep corpus compression - and a second copy of the three reasons is exactly the
  divergence that would let a frame be dropped by one path and kept by the other.

  The three reasons are the whole taxonomy: `zero-byte`, `unreadable`, `near-black`. A black or
  zero-byte capture is FLAG_SECURE working as designed, not a rendering defect - it has been misread
  as a real bug at least four times in this repo, so it must never reach a findings list. The verdict
  is taken from the file's own bytes: the in-app secure-window detector has a documented false
  negative and cannot be trusted about the same frame.

  A frame is called near-black only when it is BOTH near-uniform and dark: a legitimately
  dark-themed screen carries real content and must not be dropped on brightness alone - and the
  sweep photographs six dark themes on purpose.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

Add-Type -AssemblyName System.Drawing

# A fixed sample grid rather than every pixel: the question is only whether the frame carries any
# content at all, and a full read of 300+ full-resolution captures costs far more than it settles.
$script:FrameSignalSampleGrid = 16

$script:FrameSignalReasons = @('zero-byte', 'unreadable', 'near-black')

function Get-FrameSkipReason {
    <#
    .SYNOPSIS
        Returns $null when the frame is worth analysing, or the skip reason when it is not.
    #>
    param(
        [Parameter(Mandatory)][string]$Path,
        [long]$SizeBytes = -1,
        [double]$NearBlackVarianceMax = 12,
        [double]$NearBlackLuminanceMax = 18
    )

    if ($SizeBytes -lt 0) {
        $item = Get-Item -LiteralPath $Path -ErrorAction SilentlyContinue
        if (-not $item) { return 'unreadable' }
        $SizeBytes = $item.Length
    }
    if ($SizeBytes -eq 0) { return 'zero-byte' }

    $bitmap = $null
    try {
        $bitmap = [System.Drawing.Bitmap]::FromFile($Path)
        if ($bitmap.Width -eq 0 -or $bitmap.Height -eq 0) { return 'unreadable' }

        $grid = $script:FrameSignalSampleGrid
        $luminances = [System.Collections.Generic.List[double]]::new()
        for ($ix = 0; $ix -lt $grid; $ix++) {
            for ($iy = 0; $iy -lt $grid; $iy++) {
                $x = [int](($ix + 0.5) * $bitmap.Width / $grid)
                $y = [int](($iy + 0.5) * $bitmap.Height / $grid)
                $pixel = $bitmap.GetPixel([Math]::Min($x, $bitmap.Width - 1), [Math]::Min($y, $bitmap.Height - 1))
                $luminances.Add(0.299 * $pixel.R + 0.587 * $pixel.G + 0.114 * $pixel.B)
            }
        }

        $mean = ($luminances | Measure-Object -Average).Average
        $variance = ($luminances | ForEach-Object { [Math]::Pow($_ - $mean, 2) } | Measure-Object -Average).Average

        if ($variance -le $NearBlackVarianceMax -and $mean -le $NearBlackLuminanceMax) { return 'near-black' }
        return $null
    } catch {
        # A frame the imaging stack cannot open is a broken capture, not a usability finding.
        return 'unreadable'
    } finally {
        if ($bitmap) { $bitmap.Dispose() }
    }
}
