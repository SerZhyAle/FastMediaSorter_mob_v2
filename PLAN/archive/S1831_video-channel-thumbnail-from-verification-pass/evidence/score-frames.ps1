#requires -Version 7
<#
.SYNOPSIS
    S1831: screen every captured frame for "black or flat" objectively, so the eye only has to judge
    the ones that look suspect instead of 360 thumbnails.

.DESCRIPTION
    Each frame is reduced to an 8x8 greyscale block by ffmpeg. Mean luminance says how dark it is;
    the standard deviation across the 64 cells says how much picture is in it. A broadcast frame has
    structure, a black frame has neither light nor variation, and a flat colour slate has light but
    almost no variation. The thresholds are deliberately generous - this is a screen that decides what
    a human looks at, not a verdict.

.EXIT CODES
    0 - scored, CSV written
    1 - could not run (ffmpeg missing, no frames)
#>
[CmdletBinding()]
param(
    [string]$FrameDir = 'temp/S1831/measure/frames',
    [string]$OutCsv = 'temp/S1831/measure/frame-scores.csv',
    [int]$Throttle = 12,
    [double]$DarkMean = 24,
    [double]$FlatStd = 12
)

$ErrorActionPreference = 'Stop'
$ffmpeg = (Get-Command ffmpeg -ErrorAction SilentlyContinue).Source
if (-not $ffmpeg) { $ffmpeg = "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffmpeg.exe" }
if (-not (Test-Path $ffmpeg)) { Write-Error "ffmpeg not found"; exit 1 }

$frames = @(Get-ChildItem $FrameDir -Filter *.png -ErrorAction SilentlyContinue)
if ($frames.Count -eq 0) { Write-Error "no frames in $FrameDir"; exit 1 }
Write-Host ("Scoring {0} frame(s) .." -f $frames.Count) -ForegroundColor Yellow

$scores = $frames | ForEach-Object -ThrottleLimit $Throttle -Parallel {
    $f = $_; $exe = $using:ffmpeg
    $tmp = [System.IO.Path]::GetTempFileName()
    $mean = -1.0; $std = -1.0
    try {
        # 8x8 grey block: 64 bytes of raw luminance, no container, no guessing.
        & $exe -hide_banner -loglevel error -y -i $f.FullName `
            -vf 'format=gray,scale=8:8:flags=area' -f rawvideo -pix_fmt gray $tmp 2>$null
        $bytes = [System.IO.File]::ReadAllBytes($tmp)
        if ($bytes.Length -ge 64) {
            $vals = $bytes[0..63] | ForEach-Object { [double]$_ }
            $mean = ($vals | Measure-Object -Average).Average
            $sumSq = 0.0
            foreach ($v in $vals) { $sumSq += [Math]::Pow($v - $mean, 2) }
            $std = [Math]::Sqrt($sumSq / 64)
        }
    }
    catch { }
    finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
    [pscustomobject]@{ file = $f.Name; bytes = $f.Length; mean = [Math]::Round($mean, 1); std = [Math]::Round($std, 1) }
}

$rows = foreach ($s in $scores) {
    $verdict = 'picture'
    if ($s.mean -lt 0) { $verdict = 'unreadable' }
    elseif ($s.mean -lt $DarkMean -and $s.std -lt $FlatStd) { $verdict = 'black' }
    elseif ($s.std -lt $FlatStd) { $verdict = 'flat' }
    $s | Add-Member -NotePropertyName verdict -NotePropertyValue $verdict -PassThru
}
$rows | Sort-Object std | Export-Csv -Path $OutCsv -NoTypeInformation -Encoding utf8

Write-Host ''
$g = $rows | Group-Object verdict | Sort-Object Count -Descending
foreach ($x in $g) { Write-Host ("  {0,-11} {1,4}  ({2:P0})" -f $x.Name, $x.Count, ($x.Count / $rows.Count)) }
Write-Host ("total {0}; thresholds: mean<{1} and std<{2} = black, std<{2} = flat" -f $rows.Count, $DarkMean, $FlatStd)
Write-Host ("wrote {0}" -f $OutCsv) -ForegroundColor Green
exit 0
