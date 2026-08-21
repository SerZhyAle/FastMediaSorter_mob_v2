function Invoke-LivenessProbe {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [string]$Activity = 'Liveness probe'
    )

    if (-not $Rows -or $Rows.Count -eq 0) { return @() }

    Write-Host ("{0} {1} URLs (throttle {2}, timeout {3}s) .." -f $Activity, $Rows.Count, $Throttle, $LivenessTimeoutSec) -ForegroundColor Yellow
    $probeJob = $Rows | ForEach-Object -ThrottleLimit $Throttle -Parallel {
        $row = $_
        $timeout = $using:LivenessTimeoutSec
        $ua2 = $using:ua
        $status = 'unknown'
        $httpCode = ''
        $note = ''
        $url = [string]$row.url

        if ($url -like 'rtsp://*') {
            try {
                $u = [Uri]$url
                $port = if ($u.Port -gt 0) { $u.Port } else { 554 }
                $tcp = [System.Net.Sockets.TcpClient]::new()
                $iar = $tcp.BeginConnect($u.Host, $port, $null, $null)
                if ($iar.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds($timeout))) {
                    $tcp.EndConnect($iar)
                    $status = 'alive'
                    $note = 'rtsp tcp-connect'
                }
                else {
                    $status = 'unknown'
                    $note = 'rtsp tcp-timeout'
                }
                $tcp.Close()
            }
            catch {
                $message = $_.Exception.Message
                if ($message -match 'refused|actively refused') {
                    $status = 'dead'
                    $note = 'rtsp conn-refused'
                }
                else {
                    $status = 'unknown'
                    $note = ('rtsp ' + ($message -replace '\s+', ' ').Trim())
                }
            }
        }
        else {
            $handler = [System.Net.Http.HttpClientHandler]::new()
            $handler.AllowAutoRedirect = $true
            $handler.MaxAutomaticRedirections = 6
            try { $handler.AutomaticDecompression = [System.Net.DecompressionMethods]::All } catch {}
            $client = [System.Net.Http.HttpClient]::new($handler)
            $client.Timeout = [TimeSpan]::FromSeconds($timeout)
            $client.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $ua2) | Out-Null
            $client.DefaultRequestHeaders.TryAddWithoutValidation('Icy-MetaData', '1') | Out-Null
            $responseHeadersRead = [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead

            $probe = {
                param([string]$methodName)
                $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$methodName, $url)
                $response = $client.SendAsync($request, $responseHeadersRead).GetAwaiter().GetResult()
                try {
                    return [int]$response.StatusCode
                }
                finally {
                    $response.Dispose()
                    $request.Dispose()
                }
            }

            try {
                try { $statusCode = & $probe 'Head' } catch { $statusCode = & $probe 'Get' }
                if (-not ($statusCode -ge 200 -and $statusCode -lt 400)) {
                    try { $statusCode = & $probe 'Get' } catch { }
                }

                if ($statusCode -ge 200 -and $statusCode -lt 400) {
                    $status = 'alive'
                    $httpCode = [string]$statusCode
                }
                elseif ($statusCode -in 404, 410) {
                    $status = 'dead'
                    $httpCode = [string]$statusCode
                    $note = "http $statusCode"
                }
                else {
                    $status = 'unknown'
                    $httpCode = [string]$statusCode
                    $note = "http $statusCode"
                }
            }
            catch {
                $message = $_.Exception.Message
                $inner = if ($_.Exception.InnerException) { $_.Exception.InnerException.Message } else { '' }
                $full = ("$message $inner" -replace '\s+', ' ').Trim()
                if ($full -match 'ICY|status line|invalid response|unrecognized|ended prematurely') {
                    $status = 'alive'
                    $note = 'icy/non-http'
                }
                elseif ($full -match 'No such host|not known|name resolution|NameResolution|known host') {
                    $status = 'dead'
                    $note = 'dns-fail'
                }
                elseif ($full -match 'refused|actively refused') {
                    $status = 'dead'
                    $note = 'conn-refused'
                }
                elseif ($full -match 'canceled|cancelled|timed out|timeout|task was canceled') {
                    $status = 'unknown'
                    $note = 'timeout'
                }
                else {
                    $status = 'unknown'
                    $note = $full
                }
            }
            finally {
                $client.Dispose()
                $handler.Dispose()
            }
        }

        Add-Member -InputObject $row -NotePropertyName 'liveness_status' -NotePropertyValue $status -Force
        Add-Member -InputObject $row -NotePropertyName 'http_code' -NotePropertyValue $httpCode -Force
        Add-Member -InputObject $row -NotePropertyName 'liveness_note' -NotePropertyValue $note -Force
        $row
    } -AsJob

    $total = $Rows.Count
    $progressWatch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($probeJob.State -in @('NotStarted', 'Running')) {
        $done = @($probeJob.ChildJobs | Where-Object { $_.State -in @('Completed', 'Failed', 'Stopped') }).Count
        $percent = if ($total -gt 0) { [int][Math]::Floor(($done / $total) * 100) } else { 100 }
        $elapsedText = Format-DurationShort $progressWatch.Elapsed
        $statusText = "{0}/{1} done | elapsed {2} | ETA estimating.." -f $done, $total, $elapsedText
        if ($done -gt 0 -and $done -lt $total) {
            $avgSeconds = $progressWatch.Elapsed.TotalSeconds / $done
            $eta = [TimeSpan]::FromSeconds($avgSeconds * ($total - $done))
            $statusText = "{0}/{1} done | elapsed {2} | ETA {3}" -f $done, $total, $elapsedText, (Format-DurationShort $eta)
        }
        elseif ($done -ge $total) {
            $statusText = "{0}/{1} done | elapsed {2} | finishing results.." -f $done, $total, $elapsedText
        }

        Write-Progress -Activity $Activity -Status $statusText -PercentComplete $percent
        Start-Sleep -Milliseconds 500
    }
    Write-Progress -Activity $Activity -Completed

    return @(Receive-Job -Job $probeJob -Wait -AutoRemoveJob)
}

# Deep-signal probe: instead of trusting a 2xx on the playlist/manifest, pull a few KB of REAL media
# body. For HLS it walks master -> media playlist -> first segment and reads bytes off that segment, so
# a stream that advertises a live playlist but serves no segments is correctly classified 'dead'. Each
# fetch is bounded by a CancellationToken so endless live bodies are never fully downloaded.
# S1830: only a verdict that is a claim ABOUT the channel may delete it. 'unknown' records a failed
# measurement on our side, and it does not even reproduce - two identical deep-signal runs six minutes
# apart over the same 19 534 rows disagreed by 95 rows. Checked at entry as well as at the prune, so a
# run that cannot legally prune says so before spending an hour probing rather than after.
function Assert-PrunableStatuses {
    param([Parameter(Mandatory = $true)][string[]]$Statuses)
    if ($Statuses -notcontains 'unknown') { return }
    throw ("Refusing to prune on 'unknown' (S1830). That verdict means this probe could not measure the row - timeout, TLS, 5xx - not that the channel is dead, and it does not reproduce between runs. " +
        'Pruning on it deleted 1 321 live stations of a single provider on 2026-08-19, 79% of that run removals, and a deletion is not recoverable: a re-added channel mints a new id, so every affected user loses the pin and the collection membership. ' +
        'Prune on dead,geo and read the per-run report to see what stayed unmeasured.')
}

# S1830: the load-spreading and loss-alarm key for one row, built on the existing Get-RegistrableDomain
# (which already carries the multi-label suffix list, so 'cp-1.owh.radio.br' does not collapse into
# 'com.br'-shaped nonsense). The wrapper exists because those two uses need a key for EVERY row, while
# Get-RegistrableDomain returns $null for a bare IP host and for a host with no dot - correct for "what
# domain would I crawl", wrong for "which endpoint am I about to hammer". Falls back to the host, then
# to the raw string, so grouping is never silently dropped.
function Get-ProviderKey {
    param([Parameter(Mandatory = $true)][string]$Url)
    $hostName = ''
    try { $hostName = ([Uri]$Url).Host } catch { $hostName = '' }
    if ([string]::IsNullOrWhiteSpace($hostName)) { return '<unparsable>' }
    $domain = Get-RegistrableDomain -hostName $hostName
    if ([string]::IsNullOrWhiteSpace($domain)) { return $hostName.ToLowerInvariant() }
    return $domain
}

# S1830: which providers are losing so much of themselves that the run looks like a probe failure
# rather than a catalog cleanup. A named function rather than inline code at the call site so the
# threshold can be checked against the real 2026-08-19 removal set instead of against a retyped copy
# of the same comparison - a check that re-implements what it verifies proves only that it agrees
# with itself.
function Get-ProviderLossOffenders {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$AllUrls,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$PrunedUrls,
        [double]$MinShare = 0.35,
        [int]$MinCount = 50
    )

    $totals = @{}
    foreach ($u in $AllUrls) { $k = Get-ProviderKey -Url $u; $totals[$k] = 1 + [int]$totals[$k] }
    $losses = @{}
    foreach ($u in $PrunedUrls) { $k = Get-ProviderKey -Url $u; $losses[$k] = 1 + [int]$losses[$k] }

    $out = [System.Collections.Generic.List[object]]::new()
    foreach ($k in $losses.Keys) {
        $lost = [int]$losses[$k]
        $total = [int]$totals[$k]
        if ($total -le 0) { continue }
        $share = $lost / $total
        if ($lost -ge $MinCount -and $share -ge $MinShare) {
            $out.Add([pscustomobject]@{ Provider = $k; Lost = $lost; Total = $total; Share = $share })
        }
    }
    return @($out | Sort-Object -Property Lost -Descending)
}

# S1830: round-robin the rows across providers so the parallel window never belongs to one host.
# ForEach-Object -ThrottleLimit caps only the TOTAL number of in-flight requests, and the catalog is
# sorted by category and name, which on the 2026-08-19 bank put 1 964 rows of one provider in a single
# contiguous block - so for that whole stretch all 48-64 concurrent requests hit one host, the host
# started refusing, and the refusals became 'unknown' verdicts that pruned 1 321 live stations.
# Interleaving drops one provider's share of the window to its share of the bank (10,4% for laut.fm).
# Only the probe INPUT order changes; the report and the CSV keep their own ordering.
function Get-ProviderInterleavedRows {
    param([Parameter(Mandatory = $true)][object[]]$Rows)

    $byProvider = [ordered]@{}
    foreach ($row in $Rows) {
        $key = Get-ProviderKey -Url ([string]$row.url)
        if (-not $byProvider.Contains($key)) { $byProvider[$key] = [System.Collections.Generic.List[object]]::new() }
        $byProvider[$key].Add($row)
    }

    # Spread each provider evenly over the whole sequence by giving its i-th row the fractional
    # position (i + 0.5) / n, then sorting on that. Plain round-robin is not enough and was measured
    # failing: once the small providers run out, the biggest one's remainder comes out contiguously,
    # which on this bank still left a 1 143-row block of a single provider at the tail. With fractional
    # positions a provider holding 10,4% of the bank appears about every tenth row from start to end,
    # so the parallel window can never fill up with one host.
    $keyed = [System.Collections.Generic.List[object]]::new()
    foreach ($entry in $byProvider.GetEnumerator()) {
        $rowsOfProvider = $entry.Value
        $n = [double]$rowsOfProvider.Count
        for ($i = 0; $i -lt $rowsOfProvider.Count; $i++) {
            $keyed.Add([pscustomobject]@{
                    Position = ($i + 0.5) / $n
                    Provider = [string]$entry.Key
                    Row      = $rowsOfProvider[$i]
                })
        }
    }

    return @($keyed | Sort-Object Position, Provider | ForEach-Object { $_.Row })
}

# S1830: ffprobe ships beside ffmpeg, so look next to the resolved ffmpeg first and fall back to PATH.
# Returns '' instead of throwing: without ffprobe the probe degrades to the old byte criterion with a
# warning rather than refusing to run at all, because a maintenance run that cannot start repairs
# nothing.
function Get-FfprobeExe {
    if ($script:FfprobeExe) { return $script:FfprobeExe }
    $candidates = [System.Collections.Generic.List[string]]::new()
    try {
        $ffmpeg = Get-FfmpegExe
        if ($ffmpeg) { $candidates.Add((Join-Path (Split-Path -Parent $ffmpeg) 'ffprobe.exe')) }
    }
    catch { $null = $_ }
    $onPath = (Get-Command ffprobe -ErrorAction SilentlyContinue).Source
    if ($onPath) { $candidates.Add($onPath) }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { $script:FfprobeExe = (Resolve-Path $c).Path; return $script:FfprobeExe }
    }
    return ''
}

# S1830: the owner's liveness criterion, 2026-08-20 - "важно не чтобы оно 200 отдавала, а видео или
# музыку". Returns the codec_type set ffprobe actually found, so a verdict can rest on decoded media
# instead of on a byte count that an HTML "stream offline" page satisfies just as well.
# Kept a named function rather than inline runspace code so it stays testable outside the probe; the
# parallel block re-creates it from this definition, because -Parallel runspaces inherit no functions.
function Get-MediaStreamKinds {
    param(
        [Parameter(Mandatory = $true)][string]$FfprobeExe,
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSec = 10
    )

    $result = [pscustomobject]@{ Kinds = ''; Codecs = ''; Ok = $false }

    $probeArgs = @(
        '-v', 'error',
        '-rw_timeout', [string]($TimeoutSec * 1000000),
        '-analyzeduration', '3000000',
        '-probesize', '1000000',
        '-print_format', 'json',
        '-show_streams',
        $Url
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new($FfprobeExe)
    foreach ($a in $probeArgs) { $psi.ArgumentList.Add($a) }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $proc = $null
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
        $stdout = $proc.StandardOutput.ReadToEndAsync()
        if (-not $proc.WaitForExit($TimeoutSec * 1000 + 2000)) {
            try { $proc.Kill($true) } catch { $null = $_ }
            return $result
        }
        if ($proc.ExitCode -ne 0) { return $result }
        $text = $stdout.GetAwaiter().GetResult()
        if ([string]::IsNullOrWhiteSpace($text)) { return $result }
        $streams = @(($text | ConvertFrom-Json).streams)
        if ($streams.Count -eq 0) { return $result }
        $kinds = @($streams | ForEach-Object { $_.codec_type } | Where-Object { $_ } | Sort-Object -Unique)
        $result.Kinds = ($kinds -join '+')
        $result.Codecs = (@($streams | ForEach-Object { $_.codec_name } | Where-Object { $_ } | Sort-Object -Unique) -join ',')
        $result.Ok = ($kinds -contains 'audio') -or ($kinds -contains 'video')
    }
    catch {
        $null = $_
    }
    finally {
        if ($proc) { try { $proc.Dispose() } catch { $null = $_ } }
    }
    return $result
}

# S1831: the capture-shaped twin of Get-MediaStreamKinds. It returns the SAME three fields, because those
# land in the row as media_kinds / media_codecs and in the human-readable note, so anything that produced a
# different shape would silently change what those columns mean.
#
# What differs is the evidence. Get-MediaStreamKinds asks ffprobe to characterise the streams and believes
# the answer; this asks ffmpeg to decode one frame and write it, so `Ok` means "this address handed us a
# picture just now", not "this address declares a video track". Measured over 400 channels, one per
# provider, that is the stronger test on both counts: 360 produced a frame against 340 the probe confirmed,
# and the two calls cost the same (median 2680 ms against 2682 ms).
#
# The frame lands on the preview cache path, so the sheet build later packs what this pass already fetched
# instead of opening every address a second time.
function Get-CapturedFrameKinds {
    param(
        [Parameter(Mandatory = $true)][string]$FfmpegExe,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$FramePath,
        [int]$TimeoutSec = 20,
        [int]$TileW = 240,
        [int]$TileH = 135
    )

    $result = [pscustomobject]@{ Kinds = ''; Codecs = ''; Ok = $false }

    # Identical to Invoke-ChannelPreviewCapture's arguments except for -loglevel: `info` is what makes
    # ffmpeg print the "Stream #0:0: Video: h264" lines this function reads the codec set out of. At
    # `error` the capture still works and the columns come back empty, which reads as a dead channel.
    $capArgs = @(
        '-hide_banner', '-loglevel', 'info', '-y',
        '-user_agent', 'FastMediaSorter-catalog/1.0',
        '-rw_timeout', [string]($TimeoutSec * 1000000),
        '-analyzeduration', '5000000', '-probesize', '5000000',
        '-i', $Url,
        '-frames:v', '1', '-update', '1',
        '-vf', ("scale={0}:{1}:force_original_aspect_ratio=increase,crop={0}:{1}" -f $TileW, $TileH),
        '-f', 'image2', $FramePath
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new($FfmpegExe)
    foreach ($a in $capArgs) { $psi.ArgumentList.Add($a) }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $proc = $null
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
        # Read stderr asynchronously: ffmpeg's banner alone can fill the pipe buffer, and a full pipe
        # deadlocks the child while we sit in WaitForExit.
        $stderr = $proc.StandardError.ReadToEndAsync()
        if (-not $proc.WaitForExit($TimeoutSec * 1000)) {
            # A live stream never ends on its own: kill the whole tree when the frame did not land.
            try { $proc.Kill($true) } catch { $null = $_ }
            $proc.WaitForExit(3000) | Out-Null
        }
        $text = ''
        try { $text = $stderr.GetAwaiter().GetResult() } catch { $null = $_ }

        # Read only the INPUT section. ffmpeg describes its own output with the same "Stream #0:0: Video:"
        # shape, so matching the whole text reports `png` - our tile encoder - as a codec the broadcaster
        # sent, and that string would be written into the row's media_codecs column. Measured on a live
        # channel: 'aac,h264,png' before this cut, 'aac,h264' after.
        $inputEnd = $text.IndexOf("`nOutput #")
        if ($inputEnd -lt 0) { $inputEnd = $text.IndexOf('Output #') }
        $inputText = if ($inputEnd -gt 0) { $text.Substring(0, $inputEnd) } else { $text }

        $kinds = [System.Collections.Generic.List[string]]::new()
        $codecs = [System.Collections.Generic.List[string]]::new()
        foreach ($m in [regex]::Matches($inputText, 'Stream #\d+:\d+[^:]*: (Video|Audio): ([A-Za-z0-9_.-]+)')) {
            $k = $m.Groups[1].Value.ToLowerInvariant()
            $c = $m.Groups[2].Value.ToLowerInvariant()
            if (-not $kinds.Contains($k)) { $kinds.Add($k) }
            if (-not $codecs.Contains($c)) { $codecs.Add($c) }
        }
        $result.Kinds = (($kinds | Sort-Object) -join '+')
        $result.Codecs = (($codecs | Sort-Object) -join ',')

        # A failed encode still leaves the file behind at zero bytes, so presence alone is not proof.
        if (Test-Path $FramePath) {
            if ((Get-Item $FramePath).Length -gt 0) { $result.Ok = $true }
            else { Remove-Item $FramePath -Force -ErrorAction SilentlyContinue }
        }
    }
    catch {
        $null = $_
    }
    finally {
        if ($proc) { try { $proc.Dispose() } catch { $null = $_ } }
    }
    return $result
}

function Invoke-SignalProbe {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [string]$Activity = 'Signal probe'
    )

    if (-not $Rows -or $Rows.Count -eq 0) { return @() }

    $ffprobeExe = Get-FfprobeExe
    if (-not $ffprobeExe) {
        Write-Warning ('ffprobe not found - liveness falls back to the byte criterion, under which a ' +
            'host returning an HTML "stream offline" page reads as alive (S1830).')
    }
    # -Parallel runspaces inherit no functions, so the definition travels as text and is re-created inside.
    $mediaProbeDef = ${function:Get-MediaStreamKinds}.ToString()

    # S1831: the capture that doubles as the liveness test for VIDEO rows. Resolved here rather than inside
    # the runspaces so a missing ffmpeg is one warning instead of one per channel - and so its absence is a
    # downgrade to the old probe-only path, never a hard failure: this function has always worked without
    # ffmpeg and must keep doing so.
    $frameCaptureDef = ${function:Get-CapturedFrameKinds}.ToString()
    $previewPathDef = ${function:Get-PreviewFrameFile}.ToString()
    $captureExe = ''
    $captureDir = ''
    if (-not $SkipCaptureFirst) {
        try {
            $captureExe = Get-FfmpegExe
            if (-not (Test-Path $PreviewFrameDir)) { New-Item -ItemType Directory -Path $PreviewFrameDir -Force | Out-Null }
            # Absolute: a -Parallel runspace does not inherit the caller's working directory, so a relative
            # path would make ffmpeg write somewhere else entirely and every capture would look failed.
            $captureDir = (Resolve-Path $PreviewFrameDir).Path
        }
        catch {
            Write-Warning ('ffmpeg not found - VIDEO rows fall back to the ffprobe-only liveness test and no ' +
                'preview frames are collected on this run. ' + $_.Exception.Message)
            $captureExe = ''
        }
    }

    $ordered = Get-ProviderInterleavedRows -Rows $Rows
    $providerCount = @($Rows | ForEach-Object { Get-ProviderKey -Url ([string]$_.url) } | Sort-Object -Unique).Count

    $framesBefore = 0
    if ($captureDir) { $framesBefore = @(Get-ChildItem $captureDir -Filter '*.png' -ErrorAction SilentlyContinue).Count }

    Write-Host ("{0} {1} URLs across {2} provider(s), interleaved (throttle {3}, timeout {4}s, pull up to {5} KB, media check {6}) .." -f `
            $Activity, $Rows.Count, $providerCount, $Throttle, $SignalTimeoutSec, [int]($SignalBytes / 1024),
            $(if ($captureExe) { 'capture-first, then ffprobe' } elseif ($ffprobeExe) { 'ffprobe' } else { 'BYTES ONLY' })) -ForegroundColor Yellow

    $probeJob = $ordered | ForEach-Object -ThrottleLimit $Throttle -Parallel {
        ${function:Get-MediaStreamKinds} = $using:mediaProbeDef
        ${function:Get-CapturedFrameKinds} = $using:frameCaptureDef
        ${function:Get-PreviewFrameFile} = $using:previewPathDef
        $row      = $_
        $timeout  = $using:SignalTimeoutSec
        $maxBytes = $using:SignalBytes
        $minBytes = $using:SignalMinBytes
        $ua2      = $using:ua
        $ffprobe  = $using:ffprobeExe
        $capExe   = $using:captureExe
        $capDir   = $using:captureDir
        $capSec   = $using:PreviewCaptureTimeoutSec
        $url      = ([string]$row.url).Trim()
        $fmt      = ([string]$row.format).ToLowerInvariant()
        $proto    = ([string]$row.protocol).ToUpperInvariant()

        $status   = 'unknown'
        $httpCode = ''
        $note     = ''
        $gotBytes = 0

        # S1830: ask the decoder first. The playable majority then costs one ffprobe instead of a body
        # pull, and only what it cannot confirm falls through to the branches below - whose job narrows
        # to telling geo from dead from "we could not measure".
        #
        # S1831 puts one more rung ABOVE that for a VIDEO row: try to take the frame first, and let the
        # frame be the proof. It is the stronger claim - "this address handed us a picture" rather than
        # "this address declares a video track" - and it is what the owner asked for: test that the
        # channel serves video, not that it answers 200. It also leaves the thumbnail behind, so the
        # sheet build no longer opens every address a second time.
        #
        # Order matters and is the whole safety argument. The capture is added ABOVE the ffprobe rung,
        # never INSTEAD of it. ffmpeg cannot tell 403 from 404 from a timeout, so a design that let a
        # failed capture stand as a verdict would collapse geo/dead/unknown into one failure and hand
        # -PruneDead a mandate to delete region-locked channels - the S1830 incident, re-run. Measured
        # over 400 channels, 2 of them declare media that ffprobe confirms while the capture still fails;
        # those keep today's verdict because the rung below is still there, and it costs nothing on the
        # 90% where the capture succeeds.
        $mediaKinds = ''
        $mediaCodecs = ''
        $mediaConfirmed = $false
        $framePath = ''
        $isVideoRow = (([string]$row.media_kind).Trim().ToUpperInvariant() -eq 'VIDEO')

        if ($capExe -and $capDir -and $isVideoRow) {
            # Always capture, never trust the cached frame as evidence. A frame on disk proves the address
            # served video ON THE DAY IT WAS TAKEN, and liveness is a statement about now: short-circuiting
            # on the cache would report a channel that died last week as alive, forever, without a single
            # request - and that verdict feeds -PruneDead. Caught in testing, where a 24-row sample came
            # back "21 alive, 3 unknown" and not one `geo`, because every url already had a frame from a
            # capture run eight days earlier. The cache is the handoff to the sheet build; it is not proof.
            $framePath = Get-PreviewFrameFile -Url $url -Dir $capDir
            # Capture beside the cached frame, not onto it. ffmpeg opens the output with -y, so a capture
            # that fails halfway would truncate a good frame taken on an earlier run - and a channel that
            # is merely geo-blocked from THIS network still plays for a user in-region and should keep the
            # thumbnail it already has.
            $stagePath = $framePath + '.new'
            $cap = Get-CapturedFrameKinds -FfmpegExe $capExe -Url $url -FramePath $stagePath -TimeoutSec $capSec
            if ($cap.Ok) {
                try { Move-Item -LiteralPath $stagePath -Destination $framePath -Force } catch { $null = $_ }
                $mediaKinds = $cap.Kinds
                $mediaCodecs = $cap.Codecs
                $mediaConfirmed = $true
            }
            else {
                Remove-Item -LiteralPath $stagePath -Force -ErrorAction SilentlyContinue
            }
        }

        if (-not $mediaConfirmed -and $ffprobe) {
            $media = Get-MediaStreamKinds -FfprobeExe $ffprobe -Url $url -TimeoutSec $timeout
            $mediaKinds = $media.Kinds
            $mediaCodecs = $media.Codecs
            $mediaConfirmed = $media.Ok
        }

        if ($mediaConfirmed) {
            $status = 'alive'
            $note = "media $mediaKinds [$mediaCodecs]"
        }
        elseif ($url -like 'rtsp://*') {
            # RTSP: OPTIONS handshake over a raw socket; a valid RTSP reply line proves a live server.
            try {
                $u = [Uri]$url
                $port = if ($u.Port -gt 0) { $u.Port } else { 554 }
                $tcp = [System.Net.Sockets.TcpClient]::new()
                $iar = $tcp.BeginConnect($u.Host, $port, $null, $null)
                if ($iar.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds($timeout))) {
                    $tcp.EndConnect($iar)
                    $tcp.ReceiveTimeout = [int]($timeout * 1000)
                    $tcp.SendTimeout = [int]($timeout * 1000)
                    $ns = $tcp.GetStream()
                    $req = "OPTIONS $url RTSP/1.0`r`nCSeq: 1`r`nUser-Agent: $ua2`r`n`r`n"
                    $reqBytes = [System.Text.Encoding]::ASCII.GetBytes($req)
                    $ns.Write($reqBytes, 0, $reqBytes.Length)
                    $buf = [byte[]]::new(1024)
                    $n = $ns.Read($buf, 0, $buf.Length)
                    $resp = if ($n -gt 0) { [System.Text.Encoding]::ASCII.GetString($buf, 0, $n) } else { '' }
                    $ns.Dispose()
                    if ($resp -match 'RTSP/1\.0\s+200') { $status = 'alive'; $gotBytes = $n; $note = 'rtsp options 200' }
                    elseif ($resp -match 'RTSP/1\.0\s+(\d+)') { $httpCode = $Matches[1]; $status = 'unknown'; $note = "rtsp $($Matches[1])" }
                    else { $status = 'unknown'; $note = 'rtsp connect, no rtsp reply' }
                }
                else { $status = 'unknown'; $note = 'rtsp tcp-timeout' }
                $tcp.Close()
            }
            catch {
                $m = $_.Exception.Message
                if ($m -match 'refused|actively refused') { $status = 'dead'; $note = 'rtsp conn-refused' }
                else { $status = 'unknown'; $note = ('rtsp ' + ($m -replace '\s+', ' ').Trim()) }
            }
        }
        else {
            $handler = [System.Net.Http.HttpClientHandler]::new()
            $handler.AllowAutoRedirect = $true
            $handler.MaxAutomaticRedirections = 6
            try { $handler.AutomaticDecompression = [System.Net.DecompressionMethods]::All } catch {}
            $client = [System.Net.Http.HttpClient]::new($handler)
            $client.Timeout = [TimeSpan]::FromSeconds([Math]::Max(30, $timeout + 5))
            $client.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $ua2) | Out-Null
            $client.DefaultRequestHeaders.TryAddWithoutValidation('Icy-MetaData', '1') | Out-Null
            $headersOnly = [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead

            # Pull up to $cap body bytes within the timeout. Returns @{ Code; Bytes; Text }.
            $fetch = {
                param([string]$u, [int]$cap, [bool]$asText)
                $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($timeout))
                $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $u)
                $count = 0
                $code = 0
                $sb = if ($asText) { [System.Text.StringBuilder]::new() } else { $null }
                try {
                    $resp = $client.SendAsync($req, $headersOnly, $cts.Token).GetAwaiter().GetResult()
                    $code = [int]$resp.StatusCode
                    if ($code -ge 200 -and $code -lt 300) {
                        $bodyStream = $resp.Content.ReadAsStream()
                        $rbuf = [byte[]]::new(16384)
                        while ($count -lt $cap) {
                            $want = [Math]::Min($rbuf.Length, $cap - $count)
                            $read = $bodyStream.ReadAsync($rbuf, 0, $want, $cts.Token).GetAwaiter().GetResult()
                            if ($read -le 0) { break }
                            $count += $read
                            if ($asText) { [void]$sb.Append([System.Text.Encoding]::UTF8.GetString($rbuf, 0, $read)) }
                        }
                        $bodyStream.Dispose()
                    }
                    $resp.Dispose()
                }
                finally {
                    $req.Dispose(); $cts.Dispose()
                }
                [pscustomobject]@{ Code = $code; Bytes = $count; Text = $(if ($asText) { $sb.ToString() } else { '' }) }
            }

            # Resolve a possibly-relative playlist/segment reference against its base URL.
            $resolve = {
                param([string]$base, [string]$rel)
                if ($rel -match '^[a-zA-Z][a-zA-Z0-9+.-]*://') { return $rel }
                try { return [Uri]::new([Uri]$base, $rel).AbsoluteUri } catch { return $rel }
            }

            $isHls = ($fmt -eq 'm3u8') -or ($proto -eq 'HLS') -or ($url -match '\.m3u8(\?|$)')
            $isDash = ($fmt -eq 'mpd') -or ($proto -eq 'DASH') -or ($url -match '\.mpd(\?|$)')

            try {
                if ($isHls) {
                    $pl = & $fetch $url 262144 $true
                    $httpCode = [string]$pl.Code
                    if ($pl.Code -in 403, 451) { $status = 'geo'; $note = "playlist http $($pl.Code) region-locked" }
                    elseif ($pl.Code -in 404, 410) { $status = 'dead'; $note = "playlist http $($pl.Code)" }
                    elseif ($pl.Code -lt 200 -or $pl.Code -ge 400) { $status = 'unknown'; $note = "playlist http $($pl.Code)" }
                    elseif ([string]::IsNullOrWhiteSpace($pl.Text)) { $status = 'dead'; $note = 'empty playlist' }
                    else {
                        $plUrl = $url
                        $lines = $pl.Text -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
                        $masterIdx = -1
                        for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -like '#EXT-X-STREAM-INF*') { $masterIdx = $i; break } }
                        if ($masterIdx -ge 0) {
                            $variant = $null
                            for ($i = $masterIdx + 1; $i -lt $lines.Count; $i++) { if ($lines[$i] -notlike '#*') { $variant = $lines[$i]; break } }
                            if ($variant) {
                                $variantUrl = & $resolve $plUrl $variant
                                $media = & $fetch $variantUrl 262144 $true
                                if ($media.Code -ge 200 -and $media.Code -lt 300 -and -not [string]::IsNullOrWhiteSpace($media.Text)) {
                                    $plUrl = $variantUrl
                                    $lines = $media.Text -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
                                }
                            }
                        }
                        $seg = $null
                        foreach ($ln in $lines) { if ($ln -like '#EXT-X-MAP*' -and $ln -match 'URI="([^"]+)"') { $seg = $Matches[1]; break } }
                        if (-not $seg) { foreach ($ln in $lines) { if ($ln -notlike '#*') { $seg = $ln; break } } }
                        if (-not $seg) { $status = 'dead'; $note = 'playlist has no segments' }
                        else {
                            $segUrl = & $resolve $plUrl $seg
                            $sg = & $fetch $segUrl $maxBytes $false
                            $gotBytes = $sg.Bytes
                            if ($sg.Bytes -ge $minBytes) { $status = 'alive'; $note = "hls segment $($sg.Bytes)B" }
                            elseif ($sg.Code -in 403, 451) { $status = 'geo'; $note = "segment http $($sg.Code) region-locked" }
                            elseif ($sg.Code -in 404, 410) { $status = 'dead'; $note = "segment http $($sg.Code)" }
                            elseif ($sg.Bytes -gt 0) { $status = 'alive'; $note = "hls segment small $($sg.Bytes)B" }
                            else { $status = 'dead'; $note = "segment no data (http $($sg.Code))" }
                        }
                    }
                }
                elseif ($isDash) {
                    $mf = & $fetch $url 262144 $true
                    $httpCode = [string]$mf.Code
                    $gotBytes = $mf.Bytes
                    if ($mf.Code -in 403, 451) { $status = 'geo'; $note = "manifest http $($mf.Code) region-locked" }
                    elseif ($mf.Code -in 404, 410) { $status = 'dead'; $note = "manifest http $($mf.Code)" }
                    elseif ($mf.Code -ge 200 -and $mf.Code -lt 300 -and $mf.Text -match '<MPD') { $status = 'alive'; $note = 'dash manifest ok' }
                    elseif ($mf.Code -ge 200 -and $mf.Code -lt 300) { $status = 'unknown'; $note = '200 but not an mpd' }
                    else { $status = 'unknown'; $note = "manifest http $($mf.Code)" }
                }
                else {
                    # ICECAST / progressive / direct media: pull real body bytes off the stream.
                    $bd = & $fetch $url $maxBytes $false
                    $httpCode = [string]$bd.Code
                    $gotBytes = $bd.Bytes
                    if ($bd.Bytes -ge $minBytes) { $status = 'alive'; $note = "body $($bd.Bytes)B" }
                    elseif ($bd.Code -in 403, 451) { $status = 'geo'; $note = "http $($bd.Code) region-locked" }
                    elseif ($bd.Code -in 404, 410) { $status = 'dead'; $note = "http $($bd.Code)" }
                    elseif ($bd.Bytes -gt 0) { $status = 'alive'; $note = "body small $($bd.Bytes)B" }
                    elseif ($bd.Code -ge 200 -and $bd.Code -lt 400) { $status = 'unknown'; $note = "http $($bd.Code) no body" }
                    else { $status = 'unknown'; $note = "http $($bd.Code)" }
                }
            }
            catch {
                $message = $_.Exception.Message
                $inner = if ($_.Exception.InnerException) { $_.Exception.InnerException.Message } else { '' }
                $full = ("$message $inner" -replace '\s+', ' ').Trim()
                if ($full -match 'ICY|status line|invalid response|unrecognized|ended prematurely') { $status = 'alive'; $note = 'icy/non-http' }
                elseif ($full -match 'No such host|not known|name resolution|NameResolution|known host') { $status = 'dead'; $note = 'dns-fail' }
                elseif ($full -match 'refused|actively refused') { $status = 'dead'; $note = 'conn-refused' }
                elseif ($full -match 'canceled|cancelled|timed out|timeout|task was canceled') { $status = 'unknown'; $note = 'timeout' }
                else { $status = 'unknown'; $note = $full }
            }
            finally {
                $client.Dispose(); $handler.Dispose()
            }
        }

        # S1830: bytes are not media. When ffprobe ran and found no audio or video stream, an 'alive'
        # reached on byte count alone is exactly the false positive the owner ruled out - an HTML
        # "stream offline" page satisfies it. Downgrade to 'unknown' ("we could not confirm"), never to
        # 'dead': that would be a claim about the channel this probe did not earn.
        if ($ffprobe -and -not $mediaConfirmed -and $status -eq 'alive') {
            $status = 'unknown'
            $note = "no decodable media ($note)"
        }

        Add-Member -InputObject $row -NotePropertyName 'liveness_status' -NotePropertyValue $status -Force
        Add-Member -InputObject $row -NotePropertyName 'http_code' -NotePropertyValue $httpCode -Force
        Add-Member -InputObject $row -NotePropertyName 'liveness_note' -NotePropertyValue $note -Force
        Add-Member -InputObject $row -NotePropertyName 'signal_bytes' -NotePropertyValue $gotBytes -Force
        Add-Member -InputObject $row -NotePropertyName 'media_kinds' -NotePropertyValue $mediaKinds -Force
        Add-Member -InputObject $row -NotePropertyName 'media_codecs' -NotePropertyValue $mediaCodecs -Force
        $row
    } -AsJob

    $total = $Rows.Count
    $progressWatch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($probeJob.State -in @('NotStarted', 'Running')) {
        $done = @($probeJob.ChildJobs | Where-Object { $_.State -in @('Completed', 'Failed', 'Stopped') }).Count
        $percent = if ($total -gt 0) { [int][Math]::Floor(($done / $total) * 100) } else { 100 }
        $elapsedText = Format-DurationShort $progressWatch.Elapsed
        $statusText = "{0}/{1} done | elapsed {2} | ETA estimating.." -f $done, $total, $elapsedText
        if ($done -gt 0 -and $done -lt $total) {
            $avgSeconds = $progressWatch.Elapsed.TotalSeconds / $done
            $eta = [TimeSpan]::FromSeconds($avgSeconds * ($total - $done))
            $statusText = "{0}/{1} done | elapsed {2} | ETA {3}" -f $done, $total, $elapsedText, (Format-DurationShort $eta)
        }
        elseif ($done -ge $total) {
            $statusText = "{0}/{1} done | elapsed {2} | finishing results.." -f $done, $total, $elapsedText
        }
        Write-Progress -Activity $Activity -Status $statusText -PercentComplete $percent
        Start-Sleep -Milliseconds 500
    }
    Write-Progress -Activity $Activity -Completed

    $probed = @(Receive-Job -Job $probeJob -Wait -AutoRemoveJob)

    # S1831: say how many thumbnails this pass collected. Without the line the handoff is invisible - the
    # sheet build would look as if it captured them itself, which is the very duplication this removed.
    if ($captureDir) {
        $framesAfter = @(Get-ChildItem $captureDir -Filter '*.png' -ErrorAction SilentlyContinue).Count
        # The delta counts channels that had no frame before. Every VIDEO row that answered also had its
        # existing frame refreshed in place, which the count cannot show - hence "at least".
        Write-Host ("{0}: preview frame cache {1} -> {2}, at least {3} newly covered channel(s). The sheet build packs these rather than re-fetching them." -f `
                $Activity, $framesBefore, $framesAfter, ($framesAfter - $framesBefore)) -ForegroundColor DarkGray
    }

    return $probed
}

