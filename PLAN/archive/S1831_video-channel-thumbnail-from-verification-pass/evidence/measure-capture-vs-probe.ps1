#requires -Version 7
<#
.SYNOPSIS
    S1831 research item 6.2 harness: time the liveness probe against the frame capture on the same
    video channels, and keep the captured frames so their usability can be judged by eye.

.DESCRIPTION
    Scratch harness, not repository code. It never touches streams.csv, never touches the production
    frame cache (temp/channel-preview-frames), and never publishes anything. It only reads the catalog
    and writes into its own output directory.

    The two passes use byte-identical arguments to the production ones, otherwise the timings would
    measure this file rather than the pipeline:
      probe   = Get-MediaStreamKinds   in scripts/streams/collect-stream-candidates.ps1
      capture = Invoke-ChannelPreviewCapture in the same file

    Sampling is provider-diverse rather than "first N": the catalog is heavily skewed towards a few
    hosts, so the first N rows would measure one CDN's behaviour and call it the population.

.EXIT CODES
    0 - measurement completed, CSV written
    1 - could not run (no ffmpeg/ffprobe, catalog missing, empty sample)
#>
[CmdletBinding()]
param(
    [string]$Csv = 'delivery/stream-catalog/streams.csv',
    [int]$SampleSize = 200,
    [int]$PerProvider = 3,
    [int]$Throttle = 12,
    [int]$ProbeTimeoutSec = 8,
    [int]$CaptureTimeoutSec = 20,
    [string]$OutDir = 'temp/S1831/measure'
)

$ErrorActionPreference = 'Stop'

$ffmpeg = (Get-Command ffmpeg -ErrorAction SilentlyContinue).Source
if (-not $ffmpeg) { $ffmpeg = "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffmpeg.exe" }
$ffprobe = (Get-Command ffprobe -ErrorAction SilentlyContinue).Source
if (-not $ffprobe) { $ffprobe = "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffprobe.exe" }
if (-not (Test-Path $ffmpeg)) { Write-Error "ffmpeg not found: $ffmpeg"; exit 1 }
if (-not (Test-Path $ffprobe)) { Write-Error "ffprobe not found: $ffprobe"; exit 1 }
if (-not (Test-Path $Csv)) { Write-Error "catalog not found: $Csv"; exit 1 }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$frameDir = Join-Path (Resolve-Path $OutDir).Path 'frames'
New-Item -ItemType Directory -Force -Path $frameDir | Out-Null

# Registrable-ish provider key: last two labels of the host. Good enough to spread the sample; the
# production Get-ProviderKey does the same job with more care about multi-part public suffixes.
function Get-ProviderKeyLite([string]$url) {
    try { $h = ([Uri]$url).Host } catch { return 'invalid' }
    $parts = $h.Split('.')
    if ($parts.Count -ge 2) { return ($parts[-2..-1] -join '.') }
    return $h
}

$video = @(Import-Csv -Path $Csv | Where-Object { [string]$_.media_kind -eq 'VIDEO' })
Write-Host ("Catalog: {0} VIDEO row(s)" -f $video.Count) -ForegroundColor Cyan

# Round-robin across providers so no single CDN dominates the sample.
$byProvider = @{}
foreach ($r in $video) {
    $k = Get-ProviderKeyLite ([string]$r.url)
    if (-not $byProvider.ContainsKey($k)) { $byProvider[$k] = [System.Collections.Generic.List[object]]::new() }
    if ($byProvider[$k].Count -lt $PerProvider) { $byProvider[$k].Add($r) }
}
$sample = [System.Collections.Generic.List[object]]::new()
for ($slot = 0; $slot -lt $PerProvider -and $sample.Count -lt $SampleSize; $slot++) {
    foreach ($k in ($byProvider.Keys | Sort-Object)) {
        if ($sample.Count -ge $SampleSize) { break }
        if ($byProvider[$k].Count -gt $slot) { $sample.Add($byProvider[$k][$slot]) }
    }
}
if ($sample.Count -eq 0) { Write-Error 'empty sample'; exit 1 }
Write-Host ("Sample: {0} channel(s) across {1} provider(s), <= {2} per provider" -f `
        $sample.Count, $byProvider.Keys.Count, $PerProvider) -ForegroundColor Cyan

$items = @($sample | ForEach-Object {
        [pscustomobject]@{
            Url      = [string]$_.url
            Name     = [string]$_.name
            Provider = Get-ProviderKeyLite ([string]$_.url)
            File     = Join-Path $frameDir ((([System.BitConverter]::ToString(
                            [System.Security.Cryptography.SHA1]::HashData(
                                [System.Text.Encoding]::UTF8.GetBytes([string]$_.url)))) -replace '-', '').ToLowerInvariant() + '.png')
        }
    })

# --- Pass 1: the liveness probe, exactly as Get-MediaStreamKinds invokes it ------------------------
Write-Host 'Pass 1/2: liveness probe (ffprobe -show_streams) ..' -ForegroundColor Yellow
$t0 = Get-Date
$probe = $items | ForEach-Object -ThrottleLimit $Throttle -Parallel {
    $it = $_; $exe = $using:ffprobe; $sec = $using:ProbeTimeoutSec
    $argList = @('-v', 'error', '-rw_timeout', [string]($sec * 1000000),
        '-analyzeduration', '3000000', '-probesize', '1000000',
        '-print_format', 'json', '-show_streams', $it.Url)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $kinds = ''; $ok = $false
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        $p = Start-Process -FilePath $exe -ArgumentList $argList -NoNewWindow -PassThru -RedirectStandardOutput $tmp -RedirectStandardError ($tmp + '.err')
        if (-not $p.WaitForExit($sec * 1000 + 2000)) { try { $p.Kill($true) } catch { }; $p.WaitForExit(3000) | Out-Null }
        $raw = Get-Content $tmp -Raw -ErrorAction SilentlyContinue
        if ($raw) {
            $j = $raw | ConvertFrom-Json -ErrorAction SilentlyContinue
            if ($j.streams) {
                $kinds = (($j.streams | ForEach-Object { $_.codec_type } | Sort-Object -Unique) -join '+')
                $ok = ($kinds -match 'video') -or ($kinds -match 'audio')
            }
        }
    }
    catch { }
    finally { Remove-Item $tmp, ($tmp + '.err') -Force -ErrorAction SilentlyContinue }
    $sw.Stop()
    [pscustomobject]@{ Url = $it.Url; ProbeMs = [int]$sw.ElapsedMilliseconds; ProbeOk = $ok; Kinds = $kinds }
}
$probeWall = ((Get-Date) - $t0).TotalSeconds
Write-Host ("  probe pass: {0:N1}s wall, {1} of {2} confirmed media" -f `
        $probeWall, @($probe | Where-Object ProbeOk).Count, $items.Count) -ForegroundColor DarkGray

# --- Pass 2: the frame capture, exactly as Invoke-ChannelPreviewCapture invokes it -----------------
Write-Host 'Pass 2/2: frame capture (ffmpeg -frames:v 1) ..' -ForegroundColor Yellow
$t1 = Get-Date
$cap = $items | ForEach-Object -ThrottleLimit $Throttle -Parallel {
    $it = $_; $exe = $using:ffmpeg; $sec = $using:CaptureTimeoutSec
    $agent = 'FastMediaSorter-catalog/1.0'
    $vf = 'scale=240:135:force_original_aspect_ratio=increase,crop=240:135'
    $errFile = $it.File + '.err'
    $argList = @('-hide_banner', '-loglevel', 'error', '-y', '-user_agent', $agent,
        '-rw_timeout', [string]($sec * 1000000),
        '-analyzeduration', '5000000', '-probesize', '5000000',
        '-i', $it.Url, '-frames:v', '1', '-update', '1', '-vf', $vf, '-f', 'image2', $it.File)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $p = Start-Process -FilePath $exe -ArgumentList $argList -NoNewWindow -PassThru -RedirectStandardError $errFile
        if (-not $p.WaitForExit($sec * 1000)) { try { $p.Kill($true) } catch { }; $p.WaitForExit(3000) | Out-Null }
    }
    catch { }
    $sw.Stop()
    $bytes = 0
    if (Test-Path $it.File) {
        $bytes = (Get-Item $it.File).Length
        if ($bytes -eq 0) { Remove-Item $it.File -Force -ErrorAction SilentlyContinue }
    }
    if ((Test-Path $errFile) -and $bytes -gt 0) { Remove-Item $errFile -Force -ErrorAction SilentlyContinue }
    [pscustomobject]@{ Url = $it.Url; CaptureMs = [int]$sw.ElapsedMilliseconds; FrameBytes = $bytes; File = $it.File }
}
$capWall = ((Get-Date) - $t1).TotalSeconds
Write-Host ("  capture pass: {0:N1}s wall, {1} of {2} produced a frame" -f `
        $capWall, @($cap | Where-Object { $_.FrameBytes -gt 0 }).Count, $items.Count) -ForegroundColor DarkGray

# --- Join and report ------------------------------------------------------------------------------
$probeByUrl = @{}; $probe | ForEach-Object { $probeByUrl[$_.Url] = $_ }
$capByUrl = @{}; $cap | ForEach-Object { $capByUrl[$_.Url] = $_ }
$rows = foreach ($it in $items) {
    $p = $probeByUrl[$it.Url]; $c = $capByUrl[$it.Url]
    [pscustomobject]@{
        provider    = $it.Provider
        name        = $it.Name
        url         = $it.Url
        probe_ms    = $p.ProbeMs
        probe_ok    = $p.ProbeOk
        kinds       = $p.Kinds
        capture_ms  = $c.CaptureMs
        frame_bytes = $c.FrameBytes
        frame_ok    = ($c.FrameBytes -gt 0)
        frame_file  = if ($c.FrameBytes -gt 0) { Split-Path -Leaf $c.File } else { '' }
    }
}
$csvOut = Join-Path $OutDir 'measurement.csv'
$rows | Export-Csv -Path $csvOut -NoTypeInformation -Encoding utf8
Write-Host ''
Write-Host ("Wrote {0} ({1} row(s))" -f $csvOut, $rows.Count) -ForegroundColor Green

$probeOk = @($rows | Where-Object probe_ok).Count
$frameOk = @($rows | Where-Object frame_ok).Count
$both = @($rows | Where-Object { $_.probe_ok -and $_.frame_ok }).Count
$probeOnly = @($rows | Where-Object { $_.probe_ok -and -not $_.frame_ok }).Count
$frameOnly = @($rows | Where-Object { -not $_.probe_ok -and $_.frame_ok }).Count
$medP = ($rows.probe_ms | Sort-Object)[[int]($rows.Count / 2)]
$medC = ($rows.capture_ms | Sort-Object)[[int]($rows.Count / 2)]
$sumP = ($rows.probe_ms | Measure-Object -Sum).Sum
$sumC = ($rows.capture_ms | Measure-Object -Sum).Sum

Write-Host ''
Write-Host '=== S1831 research 6.2 ===' -ForegroundColor Cyan
Write-Host ("sample                 : {0} channels, {1} providers" -f $rows.Count, (@($rows.provider | Sort-Object -Unique)).Count)
Write-Host ("probe confirmed media  : {0} ({1:P0})" -f $probeOk, ($probeOk / $rows.Count))
Write-Host ("capture produced frame : {0} ({1:P0})" -f $frameOk, ($frameOk / $rows.Count))
Write-Host ("  both agree           : {0}" -f $both)
Write-Host ("  probe ok, no frame   : {0}   <- merged pass would LOSE these as alive" -f $probeOnly)
Write-Host ("  frame ok, probe said no: {0} <- merged pass would GAIN these" -f $frameOnly)
Write-Host ("median probe / capture : {0} ms / {1} ms" -f $medP, $medC)
Write-Host ("cpu-time probe+capture : {0:N1}s (today) vs capture-only {1:N1}s (merged)" -f (($sumP + $sumC) / 1000), ($sumC / 1000))
Write-Host ("wall probe+capture     : {0:N1}s (today) vs capture-only {1:N1}s (merged)" -f ($probeWall + $capWall), $capWall)
Write-Host ("frames kept in         : {0}" -f $frameDir)
exit 0
