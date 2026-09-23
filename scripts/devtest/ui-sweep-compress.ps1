#!/usr/bin/env pwsh
<#
.SYNOPSIS
  S2380 phase 05 - reduces the raw UI-sweep corpus to a weight an agent can read and emits the
  manifest that is the review's only input besides the frames.

.DESCRIPTION
  The walk (`ui-sweep-walk.ps1`) leaves a raw corpus of full-resolution PNGs and one journal row per
  screen per combination. That corpus is too heavy to hand to a reviewing agent and carries frames
  with no signal at all - a FLAG_SECURE surface comes back black BY DESIGN. This script sits between
  the two and is the whole seam: the reviewer reads the compressed directory and the manifest, and
  knows nothing about the walk (strategic ADR-1, 5.3).

  Three things happen here, in order:

  1. Every frame is classified by `lib/frame-signal.ps1` - the same three reasons the screenshot
     discovery scan uses, from one file, so the two cannot disagree about the same image. A dropped
     frame is NOT deleted from the raw corpus: it is carried in the manifest with its reason, so a
     screen that produced only dropped frames reads as "photographed and empty", never as
     "never swept". Deleting it would make the screen look unswept, which is the opposite claim.

  2. Kept frames are re-encoded into `-OutDir`, keeping the source filename stem - which the walker
     builds as `<combination>__<screen>[__xNN]`, so the combination and the screen survive the move.
     That stem is the only carrier of the link once the frames leave the walk, and every observation
     in the report must point back through it (strategic 11 criterion 4). Re-encoding is bounded by
     `-MaxWidth` and `-JpegQuality`: the reviewer reads on-screen STRINGS off these frames, so the
     defaults stay well above the point where text stops being legible.

  3. A manifest joins each frame to its screen, its combination and the walk outcome the journal
     recorded for that pair, and carries the dropped frames with their reasons. Its entry count is
     kept plus dropped - no frame in the corpus is unaccounted for.

  A frame whose stem matches no journal row is kept and carried with a null outcome rather than
  discarded: an unattributed frame is evidence the walk and the catalog have diverged, and losing it
  hides that.

.PARAMETER CorpusDir
  The walk's output root - screenshots, tree dumps and `sweep-journal.json`.

.PARAMETER OutDir
  Where the compressed corpus and the manifest are written. Must stay under the ticket's temp/ root:
  the corpus is evidence for one run and is never version-controlled.

.PARAMETER MaxWidth
  Longest-edge bound in pixels for a re-encoded frame. A frame already smaller is not upscaled.

.PARAMETER JpegQuality
  JPEG quality for the re-encode, 1..100.

.PARAMETER Json
  Emit the summary as one compressed JSON object instead of a human line.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/ui-sweep-compress.ps1

.NOTES
  Exit codes:
    0 - the corpus was compressed and the manifest written (including when every frame was dropped:
        an all-empty corpus is a real answer about the run, not a failure of this script)
    1 - the corpus is present but unusable: the journal is missing or unparseable, the output root
        is outside temp/, or not one frame could be written
    2 - no corpus found: the corpus directory does not exist or holds no frame at all
#>
[CmdletBinding()]
param(
    [string]$CorpusDir = 'temp/S2380/sweep',
    [string]$OutDir = 'temp/S2380/sweep-compressed',
    [int]$MaxWidth = 1080,
    [ValidateRange(1, 100)][int]$JpegQuality = 72,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/frame-signal.ps1')

function Resolve-UnderRepo {
    param([string]$PathValue)
    if ([System.IO.Path]::IsPathRooted($PathValue)) { return $PathValue }
    return (Join-Path $repoRoot $PathValue)
}

function Stop-Compress {
    param([int]$Code, [string]$Reason)
    if ($Json) { [pscustomobject]@{ ok = $false; exitCode = $Code; reason = $Reason } | ConvertTo-Json -Compress }
    else { Write-Host "ui-sweep-compress: $Reason" -ForegroundColor Red }
    exit $Code
}

$corpusPath = Resolve-UnderRepo $CorpusDir
$outPath = Resolve-UnderRepo $OutDir

# The corpus is one run's evidence and is never version-controlled; writing it anywhere but temp/
# would put hundreds of frames in front of the next commit (CLAUDE.md Rule 1).
$tempRoot = Join-Path $repoRoot 'temp'
if (-not $outPath.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
    Stop-Compress 1 "the output root must live under temp/, got: $OutDir"
}

if (-not (Test-Path -LiteralPath $corpusPath -PathType Container)) {
    Stop-Compress 2 "no corpus at $CorpusDir"
}

$frames = @(Get-ChildItem -LiteralPath $corpusPath -Filter '*.png' -File -ErrorAction SilentlyContinue |
    Sort-Object Name)
if ($frames.Count -eq 0) {
    Stop-Compress 2 "the corpus at $CorpusDir holds no frame"
}

# --- the journal, which is what makes a frame attributable ---------------------------------------

$journalPath = Join-Path $corpusPath 'sweep-journal.json'
if (-not (Test-Path -LiteralPath $journalPath)) {
    Stop-Compress 1 "the corpus has frames but no sweep-journal.json - nothing could be attributed"
}
try {
    $journal = Get-Content -LiteralPath $journalPath -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    Stop-Compress 1 "sweep-journal.json could not be parsed: $($_.Exception.Message)"
}

# Stem -> row. The walker names a frame `<combination>__<screen>`, and an expanded node appends
# `__xNN`; both resolve to the same journal row, which is what carries the outcome.
$rowByStem = @{}
foreach ($row in @($journal.rows)) {
    if (-not $row.combination -or -not $row.screen) { continue }
    $rowByStem["$($row.combination)__$($row.screen)"] = $row
}

function Resolve-Row {
    param([string]$Stem)
    $base = $Stem -replace '__x\d+$', ''
    $isExpanded = ($base -ne $Stem)
    $match = if ($rowByStem.ContainsKey($base)) { $rowByStem[$base] } else { $null }
    return @{ row = $match; key = $base; expanded = $isExpanded }
}

# --- re-encode -----------------------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object { $_.MimeType -eq 'image/jpeg' } | Select-Object -First 1
if (-not $jpegCodec) { Stop-Compress 1 'the imaging stack offers no JPEG encoder' }
$encoderParams = [System.Drawing.Imaging.EncoderParameters]::new(1)
$encoderParams.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new(
    [System.Drawing.Imaging.Encoder]::Quality, [long]$JpegQuality)

function Write-CompressedFrame {
    # Returns the written size in bytes, or $null when the frame could not be re-encoded.
    param([string]$Source, [string]$Destination)
    $src = $null; $dst = $null; $graphics = $null
    try {
        $src = [System.Drawing.Bitmap]::FromFile($Source)
        $longest = [double]([Math]::Max($src.Width, $src.Height))
        $scale = [Math]::Min(1.0, $MaxWidth / $longest)
        $width = [Math]::Max(1, [int][Math]::Round($src.Width * $scale))
        $height = [Math]::Max(1, [int][Math]::Round($src.Height * $scale))
        $dst = [System.Drawing.Bitmap]::new($width, $height)
        $graphics = [System.Drawing.Graphics]::FromImage($dst)
        # Bicubic rather than the default: the reviewer reads strings off these frames, and a
        # nearest-neighbour downscale is where small type stops being legible first.
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.DrawImage($src, 0, 0, $width, $height)
        $dst.Save($Destination, $jpegCodec, $encoderParams)
        return (Get-Item -LiteralPath $Destination).Length
    } catch {
        return $null
    } finally {
        if ($graphics) { $graphics.Dispose() }
        if ($dst) { $dst.Dispose() }
        if ($src) { $src.Dispose() }
    }
}

if (-not (Test-Path -LiteralPath $outPath)) { New-Item -ItemType Directory -Path $outPath -Force | Out-Null }

$kept = [System.Collections.Generic.List[object]]::new()
$dropped = [System.Collections.Generic.List[object]]::new()
$rawBytes = 0L
$outBytes = 0L

foreach ($frame in $frames) {
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($frame.Name)
    $attribution = Resolve-Row -Stem $stem
    $row = $attribution.row
    $rawBytes += $frame.Length

    $entry = [ordered]@{
        frame       = $null
        rawFrame    = $frame.Name
        stem        = $stem
        combination = if ($row) { $row.combination } else { $null }
        screen      = if ($row) { $row.screen } else { $null }
        screenName  = if ($row) { $row.name } else { $null }
        outcome     = if ($row) { $row.outcome } else { $null }
        detail      = if ($row) { $row.detail } else { 'no journal row matches this frame' }
        expanded    = $attribution.expanded
        dropReason  = $null
        bytes       = 0
    }

    $reason = Get-FrameSkipReason -Path $frame.FullName -SizeBytes $frame.Length
    if ($reason) {
        # Marked, never deleted: the raw frame stays where the walk put it, so "photographed and
        # empty" stays distinguishable from "never swept".
        $entry.dropReason = $reason
        $dropped.Add([pscustomobject]$entry)
        continue
    }

    $destName = "$stem.jpg"
    $written = Write-CompressedFrame -Source $frame.FullName -Destination (Join-Path $outPath $destName)
    if ($null -eq $written) {
        $entry.dropReason = 'unreadable'
        $dropped.Add([pscustomobject]$entry)
        continue
    }

    $entry.frame = $destName
    $entry.bytes = $written
    $outBytes += $written
    $kept.Add([pscustomobject]$entry)
}

$encoderParams.Dispose()

$manifestPath = Join-Path $outPath 'sweep-manifest.json'
[ordered]@{
    ticket       = 'S2380'
    generatedAt  = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    corpusDir    = $CorpusDir
    outDir       = $OutDir
    journal      = 'sweep-journal.json'
    subset       = $journal.subset
    maxWidth     = $MaxWidth
    jpegQuality  = $JpegQuality
    entryCount   = ($kept.Count + $dropped.Count)
    keptCount    = $kept.Count
    droppedCount = $dropped.Count
    rawBytes     = $rawBytes
    outBytes     = $outBytes
    kept         = @($kept)
    dropped      = @($dropped)
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$byReason = ($dropped | Group-Object dropReason | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', '
if (-not $byReason) { $byReason = 'none' }

if ($Json) {
    [pscustomobject]@{
        ok = $true; exitCode = 0; manifest = $manifestPath; outDir = $outPath
        kept = $kept.Count; dropped = $dropped.Count; rawBytes = $rawBytes; outBytes = $outBytes
    } | ConvertTo-Json -Compress
} else {
    # Printed as a ratio rather than a saving: a corpus of flat synthetic frames can come out LARGER
    # as JPEG, and "-81%" read as a saving would be the opposite of what happened.
    $ratio = if ($rawBytes -gt 0) { [int](100.0 * $outBytes / $rawBytes) } else { 0 }
    Write-Host ("ui-sweep-compress: kept $($kept.Count), dropped $($dropped.Count) ($byReason); " +
        "$([int]($rawBytes / 1KB)) KB -> $([int]($outBytes / 1KB)) KB ($ratio% of raw); manifest $manifestPath") -ForegroundColor Cyan
}
exit 0
