#requires -Version 7
<#
.SYNOPSIS
  Collect, validate, and maintain delivery/stream-catalog/streams.csv.

.DESCRIPTION
  Default mode discovers free/publicly-listed streams from community/official sources, normalizes
  them to the 17-column catalog schema, de-duplicates against the existing catalog, runs a fast
  liveness probe, writes review artifacts to the output dir, and appends the kept rows directly to
  delivery/stream-catalog/streams.csv.

  Preview mode keeps the old safe workflow: write artifacts only, do not touch streams.csv.

  Catalog-only mode replaces the old standalone liveness checker: probe the current catalog,
  write a maintenance report, and optionally prune confirmed-dead rows.

  Probe behavior (header liveness, default):
    - Uses .NET HttpClient with ResponseHeadersRead, so endless live bodies are NOT downloaded.
    - HEAD first, then GET fallback for media servers that reject HEAD.
    - Shows console progress with done/total, elapsed time, and ETA.

  Deep-signal probe (-DeepSignal, catalog-only): pulls a few KB of REAL media body instead of trusting
  a 2xx on the playlist. For HLS it walks master -> media playlist -> first segment and reads bytes off
  the segment, so a channel that advertises a live playlist but serves no segments is reported 'dead'.
  This catches "declared but not playing" streams that the header probe marks alive. Runs many more
  concurrent runspaces (default -Throttle 48); each fetch is CancellationToken-bounded.

  Region-locked verdict (S1117): a playlist/segment/manifest/body that returns HTTP 403 or 451 is
  classified 'geo' (region-restricted from this network - may still play for a user in-region), a
  separate verdict from 'dead'/'unknown'. On an un-pinned deep-signal -PruneDead run the prune widens
  to 'dead','unknown' (non-geo failures) and 'geo' rows are kept and stamped access='geo' in the
  catalog. Header-only prune stays conservative ('dead' only).

  Discovery append gate (S0805): in the default discovery mode the same deep-signal verification runs
  as a SECOND stage after the header probe - only header-alive candidates are re-probed for real media
  bytes, and only signal-verified rows are appended to streams.csv. This stops "pseudo-alive" channels
  (playlist 2xx but no segment) from entering the shipped catalog. Opt out with -SkipDeepSignal for a
  fast prowl; -SkipLiveness (no probing at all) skips both stages.

  Sources by axis:
    livetv : iptv-org public index (VIDEO / Live TV); header-gated (referrer/UA) streams dropped.
    genres : radio-browser community DB by exact tag (AUDIO), top by clickcount.
    geo    : radio-browser by country + iptv-org by country (under-represented regions).
    webcam : curated seed list of public 24/7 HLS feeds (DW, France 24, WildEarth etc.), liveness-filtered.

  Inclusion policy: keep every reachable live channel that actually carries signal - including
  grey-area restreams and -/- channels. Only defunct ('closed') and header-gated streams
  (referrer/user_agent the app cannot supply) are dropped, since those cannot play.

.EXAMPLE
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -PreviewOnly
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -Axis genres,geo -PerQuery 30
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -Throttle 80
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -Limit 50
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -PruneDead
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -PruneDead -Publish
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -Publish   # just (re)upload current streams.csv
#>
[CmdletBinding()]
param(
    [ValidateSet('livetv', 'genres', 'geo', 'webcam')]
    [string[]]$Axis = @('livetv', 'genres', 'geo', 'webcam'),

    [int]$PerQuery = 20,
    [int]$LivenessTimeoutSec = 12,
    [int]$Throttle = 12,
    [switch]$SkipLiveness,
    # S0805: skip the deep-signal append gate in discovery mode (fast prowl). By default discovery
    # verifies real media bytes on the header-alive candidates before appending, so a "pseudo-alive"
    # channel (playlist 2xx but serving no segment/body) is excluded. -SkipLiveness implies this too.
    [switch]$SkipDeepSignal,
    [switch]$PreviewOnly,
    [switch]$CatalogOnly,
    [switch]$PruneDead,

    # Zip streams.csv and upload it as the delivery-so-v1 'stream-catalog.zip' release asset after the
    # run mutates (or, in catalog mode, after maintenance over) the catalog on disk.
    [switch]$Publish,
    [string]$PublishTag = 'delivery-so-v1',
    # S0925 guard: publishing a CSV that carries favicon_index values WITHOUT bundling the atlas ships a
    # broken artifact - the app's null-atlas import path wipes favicons for everyone (portrait then shows
    # text, not icons). Publish refuses that combination unless this switch acknowledges it (intentional
    # over-cap / favicon-less publish).
    [switch]$AllowFaviconlessPublish,

    # Favicon sprite-atlas build (S0668). When set, fetch each catalog row's favicon from its
    # homepage, pack tiles into one grid PNG, and write the per-row tile ordinal into favicon_index.
    # Default OFF so routine catalog maintenance/collection runs are unchanged.
    [switch]$WithFavicons,
    # The Google s2 favicon endpoint is a third-party fallback; ON by default but the owner can
    # disable it to keep the fetch entirely first-party (homepage favicon.ico + parsed <link>).
    [switch]$FaviconS2Fallback = $true,
    [string]$AtlasPath = 'delivery/stream-catalog/favicon-atlas.png',
    [int]$FaviconTimeoutSec = 8,
    [int]$FaviconThrottle = 16,
    # Atlas size ceiling enforced before publish. 3 MB keeps the whole stream-catalog.zip comfortably
    # inside the app's 30 s import callTimeout budget (S0583) so already-shipped apps still download
    # the larger zip in time. Over the cap -> publish CSV-only (atlas skipped), never an oversized zip.
    [int]$MaxAtlasBytes = 3145728,

    # Deep-signal catalog probe: pull a few KB of real media body (HLS -> first segment) instead of
    # trusting a 2xx on the playlist/manifest. Catches "declared but not playing" streams.
    [switch]$DeepSignal,
    [int]$Limit = 0,
    [int]$SignalBytes = 16384,
    [int]$SignalMinBytes = 2048,
    [int]$SignalTimeoutSec = 8,

    [string]$ExistingCsv = 'delivery/stream-catalog/streams.csv',
    [string]$OutDir = 'temp',
    [string]$CatalogLivenessReport = 'temp/stream-catalog-liveness.csv',
    # Statuses removed by -PruneDead. Default 'dead'; an un-pinned deep-signal run widens it to
    # 'dead','unknown' (S1117) since region-locked channels are separated into their own 'geo' verdict.
    [string[]]$PruneStatuses = @('dead'),

    # radio-browser genre tags (exact match) - skewed toward catalog's weak genres.
    [string[]]$GenreTags = @(
        'sports', 'hip-hop', 'rap', 'country', 'metal', 'folk', 'children', 'kids',
        'talk', 'comedy', 'blues', 'latin', 'gospel', 'punk', 'soul', 'funk'
    ),

    # Under-represented countries (ISO-3166 alpha-2) for both radio-browser and iptv-org.
    [string[]]$GeoCountries = @(
        'JP', 'KR', 'BR', 'MX', 'IN', 'AR', 'TR', 'ZA', 'NG', 'PL', 'SE', 'ID', 'TH', 'EG', 'SA'
    ),

    # iptv-org categories to harvest for the Live TV axis.
    [string[]]$LiveTvCategories = @(
        'news', 'documentary', 'movies', 'sports', 'kids', 'music', 'science', 'general'
    )
)

$ErrorActionPreference = 'Stop'
$ua = 'FastMediaSorter-catalog/1.0 (+stream-candidate-collector)'

# S1117: remember whether the caller pinned -PruneStatuses. When they did NOT and this is a
# deep-signal run, prune widens from 'dead' to 'dead','unknown': deep-signal now separates
# region-locked channels into their own 'geo' verdict, so the remaining 'unknown' rows are non-geo
# failures (timeout / SSL / 401 / 5xx) safe to drop. Header-only runs stay conservative ('dead').
$script:PruneStatusesExplicit = $PSBoundParameters.ContainsKey('PruneStatuses')

# Deep-signal probing pulls media bytes, so it benefits from many more concurrent runspaces than the
# header-only liveness probe. Bump the default only when the caller did not pin -Throttle explicitly.
if ($DeepSignal -and -not $PSBoundParameters.ContainsKey('Throttle')) { $Throttle = 48 }

# Trailing columns are appended at the END of the schema and existing columns are NEVER reordered:
# the app's StreamCatalogCsvParser resolves cells by header NAME, so a trailing column is
# forward/backward-compatible (old apps ignore it, new apps reading an old catalog get blank).
# col 18 favicon_index (S0668); col 19 access (S1117): '' = open, 'geo' = region-locked (403/451
# from this network - may still play for a user in-region). deep-signal probe is the only producer.
$Schema = @(
    'category', 'topic', 'name', 'url', 'media_kind', 'protocol', 'format', 'bitrate',
    'is_live', 'https', 'language', 'country', 'homepage', 'source_kind',
    'license_note', 'notes', 'confidence', 'favicon_index', 'access'
)

function Get-Host2([string]$url) {
    try { return ([uri]$url).Host.ToLowerInvariant() } catch { return '' }
}

function Get-FormatFromUrl([string]$url) {
    $u = $url.ToLowerInvariant()
    if ($u -match '\.m3u8(\?|$)') { return 'm3u8' }
    if ($u -match '\.mpd(\?|$)') { return 'mpd' }
    if ($u -match '\.aac(\?|$)') { return 'aac' }
    if ($u -match '\.mp3(\?|$)') { return 'mp3' }
    if ($u -match '\.ogg(\?|$)') { return 'ogg' }
    if ($u.StartsWith('rtsp')) { return 'rtsp' }
    return ''
}

function Get-ProtocolFromUrl([string]$url, [string]$fmt) {
    if ($url.ToLowerInvariant().StartsWith('rtsp')) { return 'RTSP' }
    switch ($fmt) {
        'm3u8' { 'HLS' }
        'mpd' { 'DASH' }
        default { 'ICECAST' }
    }
}

function To-Title([string]$s) {
    if ([string]::IsNullOrWhiteSpace($s)) { return '' }
    return (Get-Culture).TextInfo.ToTitleCase($s.ToLowerInvariant())
}

function Format-DurationShort([TimeSpan]$duration) {
    $hours = [int][Math]::Floor($duration.TotalHours)
    if ($hours -gt 0) {
        return ('{0:00}:{1:00}:{2:00}' -f $hours, $duration.Minutes, $duration.Seconds)
    }
    return ('{0:00}:{1:00}' -f $duration.Minutes, $duration.Seconds)
}

function Backup-IfExists([string]$Path) {
    if (-not (Test-Path $Path)) { return '' }
    if (-not (Test-Path 'temp')) { New-Item -ItemType Directory -Path 'temp' -Force | Out-Null }
    $ts = (Get-Date).ToString('yyyyMMdd-HHmmss')
    $backup = Join-Path 'temp' ("{0}.{1}.bak" -f (Split-Path -Leaf $Path), $ts)
    Copy-Item -Path $Path -Destination $backup -Force
    if (-not (Test-Path $backup)) { throw "Backup failed: $backup" }
    return $backup
}

function Write-CsvUtf8 {
    param([object[]]$Rows, [string]$Path, [string[]]$Columns)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $Rows | Select-Object $Columns | Export-Csv -Path $Path -NoTypeInformation -Encoding utf8
}

function Show-LivenessSummary {
    param([object[]]$Rows, [string]$Title)
    Write-Host ''
    Write-Host $Title -ForegroundColor Green
    $Rows | Group-Object liveness_status | Sort-Object Name | ForEach-Object {
        "{0,-8} {1}" -f $_.Name, $_.Count
    }
}

# Map iptv-org category id -> our topic vocabulary.
function Map-IptvTopic([string]$cat) {
    switch ($cat) {
        'news' { 'News' }
        'documentary' { 'Documentary' }
        'movies' { 'Movie' }
        'music' { 'Pop' }
        'sports' { 'Sports' }
        'kids' { 'Kids' }
        'family' { 'Kids' }
        'animation' { 'Kids' }
        'science' { 'Science & Space' }
        'general' { 'General' }
        'entertainment' { 'General' }
        default { To-Title $cat }
    }
}

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
function Invoke-SignalProbe {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [string]$Activity = 'Signal probe'
    )

    if (-not $Rows -or $Rows.Count -eq 0) { return @() }

    Write-Host ("{0} {1} URLs (throttle {2}, timeout {3}s, pull up to {4} KB) .." -f `
            $Activity, $Rows.Count, $Throttle, $SignalTimeoutSec, [int]($SignalBytes / 1024)) -ForegroundColor Yellow

    $probeJob = $Rows | ForEach-Object -ThrottleLimit $Throttle -Parallel {
        $row      = $_
        $timeout  = $using:SignalTimeoutSec
        $maxBytes = $using:SignalBytes
        $minBytes = $using:SignalMinBytes
        $ua2      = $using:ua
        $url      = ([string]$row.url).Trim()
        $fmt      = ([string]$row.format).ToLowerInvariant()
        $proto    = ([string]$row.protocol).ToUpperInvariant()

        $status   = 'unknown'
        $httpCode = ''
        $note     = ''
        $gotBytes = 0

        if ($url -like 'rtsp://*') {
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

        Add-Member -InputObject $row -NotePropertyName 'liveness_status' -NotePropertyValue $status -Force
        Add-Member -InputObject $row -NotePropertyName 'http_code' -NotePropertyValue $httpCode -Force
        Add-Member -InputObject $row -NotePropertyName 'liveness_note' -NotePropertyValue $note -Force
        Add-Member -InputObject $row -NotePropertyName 'signal_bytes' -NotePropertyValue $gotBytes -Force
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

$rbServers = @(
    'https://de1.api.radio-browser.info',
    'https://de2.api.radio-browser.info',
    'https://nl1.api.radio-browser.info',
    'https://at1.api.radio-browser.info'
)

function Invoke-RadioBrowser([string]$path) {
    foreach ($srv in $rbServers) {
        try {
            return Invoke-RestMethod -Uri "$srv$path" -TimeoutSec 25 -Headers @{ 'User-Agent' = $ua }
        }
        catch {
            Write-Verbose "radio-browser $srv failed: $($_.Exception.Message)"
        }
    }
    throw "All radio-browser mirrors failed for $path"
}

function New-Candidate {
    param(
        [string]$axis, [string]$category, [string]$topic, [string]$name, [string]$url,
        [string]$mediaKind, [string]$protocol, [string]$format, [string]$bitrate,
        [bool]$isLive, [string]$language, [string]$country, [string]$homepage,
        [string]$sourceKind, [string]$licenseNote, [string]$notes, [string]$confidence,
        [int]$score
    )
    $https = $url.ToLowerInvariant().StartsWith('https')
    [pscustomobject]@{
        category      = $category
        topic         = $topic
        name          = ($name -replace '\s+', ' ').Trim()
        url           = $url.Trim()
        media_kind    = $mediaKind
        protocol      = $protocol
        format        = $format
        bitrate       = $bitrate
        is_live       = $isLive.ToString().ToLowerInvariant()
        https         = $https.ToString().ToLowerInvariant()
        language      = $language
        country       = $country
        homepage      = $homepage
        source_kind   = $sourceKind
        license_note  = $licenseNote
        notes         = $notes
        confidence    = $confidence
        favicon_index = ''
        access        = ''
        axis          = $axis
        score         = $score
        liveness_status = ''
        http_code       = ''
        liveness_note   = ''
        dup             = ''
    }
}

function Get-RadioBrowserStations {
    param([string]$axis, [string]$kind, [string]$key, [string]$topicHint)
    $enc = [uri]::EscapeDataString($key)
    $path = if ($kind -eq 'tag') {
        "/json/stations/bytagexact/$enc?hidebroken=true&order=clickcount&reverse=true&limit=$PerQuery"
    }
    else {
        "/json/stations/bycountrycodeexact/$enc?hidebroken=true&order=clickcount&reverse=true&limit=$PerQuery"
    }
    $stations = @()
    try { $stations = Invoke-RadioBrowser $path } catch { Write-Warning $_; return @() }

    $out = @()
    foreach ($s in $stations) {
        $url = if ($s.url_resolved) { $s.url_resolved } else { $s.url }
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        if ($s.lastcheckok -ne 1) { continue }
        $fmt = if ($s.codec) { $s.codec.ToLowerInvariant() } else { Get-FormatFromUrl $url }
        $proto = if ($s.hls -eq 1) { 'HLS' } else { Get-ProtocolFromUrl $url $fmt }
        $topic = if ($s.tags) { To-Title ($s.tags -split ',' | Select-Object -First 1) } else { To-Title $topicHint }
        if ([string]::IsNullOrWhiteSpace($topic)) { $topic = To-Title $topicHint }
        $conf = if ($s.clickcount -ge 50 -and $s.votes -ge 5) { 'high' } else { 'medium' }
        $out += New-Candidate -axis $axis -category 'Radio' -topic $topic -name $s.name -url $url `
            -mediaKind 'AUDIO' -protocol $proto -format $fmt -bitrate ([string]$s.bitrate) `
            -isLive $true -language ($s.language) -country ($s.countrycode) -homepage ($s.homepage) `
            -sourceKind 'COMMUNITY' `
            -licenseNote 'radio-browser community DB, publicly listed free stream' `
            -notes ("tags: $($s.tags); clicks=$($s.clickcount); votes=$($s.votes)") `
            -confidence $conf -score ([int]$s.clickcount)
    }
    return $out
}

$script:IptvChannels = $null
$script:IptvStreams = $null

function Initialize-Iptv {
    if ($null -ne $script:IptvChannels) { return }
    Write-Host '  downloading iptv-org channels.json + streams.json ..' -ForegroundColor DarkGray
    $ch = Invoke-RestMethod -Uri 'https://iptv-org.github.io/api/channels.json' -TimeoutSec 60
    $st = Invoke-RestMethod -Uri 'https://iptv-org.github.io/api/streams.json' -TimeoutSec 120
    $map = @{}
    foreach ($c in $ch) { $map[$c.id] = $c }
    $script:IptvChannels = $map
    $script:IptvStreams = $st
}

function Get-IptvCandidates {
    param([string]$axis, [string[]]$categories, [string[]]$countries)
    Initialize-Iptv
    $seenChannel = @{}
    $out = @()
    foreach ($s in $script:IptvStreams) {
        $url = $s.url
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        if ($s.referrer) { continue }
        if ($s.user_agent) { continue }
        $cid = $s.channel
        if ([string]::IsNullOrWhiteSpace($cid)) { continue }
        if ($seenChannel.ContainsKey($cid)) { continue }
        $c = $script:IptvChannels[$cid]
        if ($null -eq $c) { continue }
        # Inclusion policy: keep every reachable live channel, including -/- and grey-area
        # restreams. Only 'closed' (defunct) and header-gated streams (referrer/user_agent, filtered
        # above) are dropped, since those cannot actually play in the app.
        if ($c.closed) { continue }

        $cats = @($c.categories)
        if ($categories -and -not ($cats | Where-Object { $categories -contains $_ })) { continue }
        if ($countries -and ($countries -notcontains $c.country)) { continue }

        $seenChannel[$cid] = $true
        $fmt = Get-FormatFromUrl $url
        $proto = Get-ProtocolFromUrl $url $fmt
        $primaryCat = if ($cats.Count) { $cats[0] } else { 'general' }
        $lang = if ($c.languages -and $c.languages.Count) { $c.languages[0] } else { '' }
        $name = if ($s.title) { "$($c.name) ($($s.title))" } else { $c.name }
        $out += New-Candidate -axis $axis -category 'Live TV' -topic (Map-IptvTopic $primaryCat) `
            -name $name -url $url -mediaKind 'VIDEO' -protocol $proto -format $fmt -bitrate '' `
            -isLive $true -language $lang -country ($c.country) -homepage ($c.website) `
            -sourceKind 'COMMUNITY' `
            -licenseNote 'iptv-org public index, publicly listed free stream (verify broadcaster-official before promote)' `
            -notes ("iptv-org; categories=$($cats -join '|'); quality=$($s.quality)") `
            -confidence 'medium' -score 0
        if ($out.Count -ge ($PerQuery * 50)) { break }
    }
    return $out
}

function Get-WebcamSeeds {
    param([string]$axis)
    # Curated public 24/7 HLS feeds. Every URL below is deep-signal verified (real segment bytes pulled
    # via -CatalogOnly -DeepSignal) before baking in - CDN/akamai paths rotate, so re-verify with
    # `-Axis webcam -PreviewOnly` when refreshing. S0843: the prior NASA ntv1/ntv2 + DW dwstream105 seeds
    # went stale (playlist 200 but segment 404 - "declared but not playing") and were replaced with these
    # signal-confirmed feeds; NASA retired its public akamai HLS in the NASA+ migration (dead/403).
    $seeds = @(
        @{ name = 'DW English'; url = 'https://amg01644-amg01644c1-amgplt0343.playout.now3.amagi.tv/ts-eu-w1-n2/playlist/amg01644-amg01644c1-amgplt0343/playlist.m3u8'; topic = 'Documentary'; country = 'DE'; language = 'english'; homepage = 'https://www.dw.com/'; source = 'PUBLIC'; note = 'Deutsche Welle public broadcaster free 24/7 feed' }
        @{ name = 'CGTN Documentary'; url = 'https://amg00405-rakutentv-cgtndocumentary-rakuten-0ql8j.amagi.tv/master.m3u8'; topic = 'Documentary'; country = 'CN'; language = 'english'; homepage = 'https://www.cgtn.com/'; source = 'PUBLIC'; note = 'CGTN documentary free 24/7 feed' }
        @{ name = 'Al Arabiya Programs'; url = 'https://live.alarabiya.net/alarabiapublish/aaprograms.smil/playlist.m3u8'; topic = 'Documentary'; country = 'AE'; language = 'arabic'; homepage = 'https://www.alarabiya.net/'; source = 'PUBLIC'; note = 'Al Arabiya programs free 24/7 feed' }
        @{ name = 'Asharq Documentary'; url = 'https://svs.itworkscdn.net/asharqdocumentarylive/asharqdocumentary.smil/playlist_dvr.m3u8'; topic = 'Documentary'; country = 'SA'; language = 'arabic'; homepage = 'https://www.asharq.com/'; source = 'PUBLIC'; note = 'Asharq documentary free 24/7 feed' }
        @{ name = 'France 24 English'; url = 'https://live.france24.com/hls/live/2037218-b/F24_EN_HI_HLS/master_5000.m3u8'; topic = 'News'; country = 'FR'; language = 'english'; homepage = 'https://www.france24.com/'; source = 'PUBLIC'; note = 'France 24 English public broadcaster free 24/7 feed' }
        @{ name = 'Al Jazeera English'; url = 'https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8'; topic = 'News'; country = 'QA'; language = 'english'; homepage = 'https://www.aljazeera.com/'; source = 'PUBLIC'; note = 'Al Jazeera English free 24/7 feed' }
        @{ name = 'CGTN'; url = 'https://amg00405-rakutentv-cgtn-rakuten-i9tar.amagi.tv/master.m3u8'; topic = 'News'; country = 'CN'; language = 'english'; homepage = 'https://www.cgtn.com/'; source = 'PUBLIC'; note = 'CGTN English news free 24/7 feed' }
        @{ name = 'InWonder'; url = 'https://amg00861-terninternation-inwonder-samsungau-1k63k.amagi.tv/playlist/amg00861-terninternation-inwonder-samsungau/playlist.m3u8'; topic = 'Science & Space'; country = 'NL'; language = 'english'; homepage = 'https://www.wonder.tv/'; source = 'COMMUNITY'; note = 'Wonder science/nature FAST channel free 24/7 feed' }
        @{ name = 'WildEarth'; url = 'https://dqga3jatxofgx.cloudfront.net/WildEarth.m3u8'; topic = 'Outdoor'; country = 'ZA'; language = 'english'; homepage = 'https://wildearth.tv/'; source = 'COMMUNITY'; note = 'WildEarth live wildlife safari webcam 24/7 feed' }
        @{ name = 'Red Bull TV'; url = 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8'; topic = 'Outdoor'; country = 'AT'; language = 'english'; homepage = 'https://www.redbull.com/int-en/tv'; source = 'COMMUNITY'; note = 'Red Bull TV outdoor/adventure free 24/7 feed' }
        @{ name = 'AKC TV Puppies 24/7'; url = 'https://install.akctvcontrol.com/speed/broadcast/140/desktop-playlist.m3u8'; topic = 'Outdoor'; country = 'US'; language = 'english'; homepage = 'https://www.akc.tv/'; source = 'COMMUNITY'; note = 'AKC TV live puppy webcam 24/7 feed' }
        @{ name = '30A Darcizzle Offshore'; url = 'https://30a-tv.com/darcizzle.m3u8'; topic = 'Outdoor'; country = 'US'; language = 'english'; homepage = 'https://30a.tv/'; source = 'COMMUNITY'; note = '30A live offshore/beach webcam 24/7 feed' }
    )
    $out = @()
    foreach ($w in $seeds) {
        $out += New-Candidate -axis $axis -category 'Live TV' -topic $w.topic -name $w.name -url $w.url `
            -mediaKind 'VIDEO' -protocol 'HLS' -format 'm3u8' -bitrate '' -isLive $true `
            -language $w.language -country $w.country -homepage $w.homepage `
            -sourceKind $w.source -licenseNote $w.note -notes 'seed 24/7 public feed' -confidence 'medium' -score 0
    }
    return $out
}

# --- S0668 favicon sprite-atlas (offline tooling) -----------------------------------------------
# Geometry is a SHARED CONTRACT with the app's atlas slicer (PHASE_01 / strategic spec). The app
# reconstructs each tile rect as col = index % 16, row = index / 16, rect = (col*32, row*32, 32, 32).
# These two constants MUST match the app side and must not be changed independently.
$script:FaviconTile = 32   # PHASE_01 contract: tile = 32x32 px
$script:FaviconCols = 16   # PHASE_01 contract: 16 columns per atlas row

# Fetch raw favicon image bytes for one homepage, or $null when none can be found.
# Fallback chain: (1) <scheme>://<host>/favicon.ico; (2) <link rel="icon"|"shortcut icon"> href from
# the homepage HTML; (3) if -FaviconS2Fallback, the Google s2 favicons endpoint at sz=32 (matches the
# tile size). Per-host failures are swallowed - a missing favicon is normal. Empty homepage -> $null.
function Get-FaviconBytes {
    param([string]$homepage)
    if ([string]::IsNullOrWhiteSpace($homepage)) { return $null }

    $uri = $null
    try { $uri = [uri]$homepage } catch { return $null }
    if (-not $uri.IsAbsoluteUri -or [string]::IsNullOrWhiteSpace($uri.Host)) { return $null }
    $host2 = $uri.Host.ToLowerInvariant()
    $scheme = if ($uri.Scheme -in 'http', 'https') { $uri.Scheme } else { 'https' }

    # Try to GET a URL and return its non-empty raw body bytes, or $null on any failure.
    $tryGet = {
        param([string]$u)
        try {
            $resp = Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec $FaviconTimeoutSec `
                -Headers @{ 'User-Agent' = $ua } -MaximumRedirection 6 -ErrorAction Stop
            $bytes = $resp.Content
            if ($bytes -is [string]) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($bytes) }
            if ($bytes -and $bytes.Length -gt 0) { return [byte[]]$bytes }
        }
        catch { }
        return $null
    }

    # (1) Conventional /favicon.ico at the site root.
    $ico = & $tryGet ("{0}://{1}/favicon.ico" -f $scheme, $host2)
    if ($ico) { return $ico }

    # (2) Parse the homepage HTML for a declared icon link.
    try {
        $page = Invoke-WebRequest -Uri $homepage -UseBasicParsing -TimeoutSec $FaviconTimeoutSec `
            -Headers @{ 'User-Agent' = $ua } -MaximumRedirection 6 -ErrorAction Stop
        $html = [string]$page.Content
        if (-not [string]::IsNullOrWhiteSpace($html)) {
            # <link rel="icon" ..> or <link rel="shortcut icon" ..> in any attribute order.
            $linkMatches = [regex]::Matches($html, '<link\b[^>]*>', 'IgnoreCase')
            foreach ($m in $linkMatches) {
                $tag = $m.Value
                if ($tag -notmatch 'rel\s*=\s*["'']?\s*(shortcut\s+icon|icon|apple-touch-icon)\b') { continue }
                $hrefMatch = [regex]::Match($tag, 'href\s*=\s*["'']([^"'']+)["'']', 'IgnoreCase')
                if (-not $hrefMatch.Success) { continue }
                $href = $hrefMatch.Groups[1].Value.Trim()
                if ([string]::IsNullOrWhiteSpace($href)) { continue }
                $iconUrl = $href
                if ($href -notmatch '^[a-zA-Z][a-zA-Z0-9+.-]*://') {
                    try { $iconUrl = [Uri]::new($uri, $href).AbsoluteUri } catch { $iconUrl = $href }
                }
                $linkBytes = & $tryGet $iconUrl
                if ($linkBytes) { return $linkBytes }
            }
        }
    }
    catch { }

    # (3) Third-party Google s2 fallback (gated). sz=32 to match the atlas tile size.
    if ($FaviconS2Fallback) {
        $s2 = & $tryGet ("https://www.google.com/s2/favicons?domain={0}&sz=32" -f $host2)
        if ($s2) { return $s2 }
    }

    return $null
}

# Fetch favicons for every distinct non-empty homepage (throttled; deduped so a repeated homepage is
# fetched once), pack the decoded tiles into one grid PNG atlas (16 cols x ceil(n/16) rows, each cell
# 32x32), save it to $AtlasPath,
# and return a hashtable mapping each packed row's url -> zero-based tile ordinal. Rows whose favicon
# could not be fetched/decoded are absent from the map (their favicon_index stays blank). System.Drawing
# (GDI+) handles decode (.ico/.png/.gif/.jpg via Image.FromStream), scaling, and PNG save.
function Build-FaviconAtlas {
    param([Parameter(Mandatory = $true)][object[]]$Rows, [Parameter(Mandatory = $true)][string]$AtlasPath)

    Add-Type -AssemblyName System.Drawing

    $tile = $script:FaviconTile   # 32 - PHASE_01 contract
    $cols = $script:FaviconCols   # 16 - PHASE_01 contract

    # Distinct non-blank homepages to fetch (dedup so a repeated homepage is fetched only once).
    $homepages = @($Rows | ForEach-Object { [string]$_.homepage } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)

    Write-Host ("Favicons: fetching for {0} unique homepage(s) (throttle {1}, timeout {2}s) .." -f `
            $homepages.Count, $FaviconThrottle, $FaviconTimeoutSec) -ForegroundColor Yellow

    # Parallel fetch -> host -> bytes map. GDI+ packing stays single-threaded below.
    $faviconFn = ${function:Get-FaviconBytes}.ToString()
    $fetched = @{}
    if ($homepages.Count -gt 0) {
        $results = $homepages | ForEach-Object -ThrottleLimit $FaviconThrottle -Parallel {
            $hp = $_
            $ua = $using:ua
            $FaviconTimeoutSec = $using:FaviconTimeoutSec
            $FaviconS2Fallback = $using:FaviconS2Fallback
            ${function:Get-FaviconBytes} = $using:faviconFn
            $bytes = $null
            try { $bytes = Get-FaviconBytes -homepage $hp } catch { $bytes = $null }
            [pscustomobject]@{ Homepage = $hp; Bytes = $bytes }
        }
        foreach ($r in $results) { if ($r.Bytes) { $fetched[$r.Homepage] = [byte[]]$r.Bytes } }
    }
    Write-Host ("Favicons: {0}/{1} homepage(s) returned image bytes." -f $fetched.Count, $homepages.Count) -ForegroundColor DarkGray

    # Decode in row order; rows that decode successfully get the next sequential ordinal.
    $packable = [System.Collections.Generic.List[object]]::new()
    foreach ($row in $Rows) {
        $hp = [string]$row.homepage
        if ([string]::IsNullOrWhiteSpace($hp)) { continue }
        if (-not $fetched.ContainsKey($hp)) { continue }
        $bytes = $fetched[$hp]
        $bmp = $null
        try {
            $ms = [System.IO.MemoryStream]::new($bytes)
            $img = [System.Drawing.Image]::FromStream($ms)
            # Copy into an owned 32-bit bitmap so we can dispose the source stream immediately.
            $bmp = [System.Drawing.Bitmap]::new($img, $img.Width, $img.Height)
            $img.Dispose()
            $ms.Dispose()
        }
        catch {
            if ($bmp) { $bmp.Dispose() }
            $bmp = $null
        }
        if ($bmp) { $packable.Add([pscustomobject]@{ Url = [string]$row.url; Bitmap = $bmp }) }
    }

    $map = @{}
    if ($packable.Count -eq 0) {
        Write-Warning 'Favicons: no images decoded; atlas not written (all favicon_index will be blank).'
        return $map
    }

    $rowsNeeded = [int][Math]::Ceiling($packable.Count / [double]$cols)
    $atlasW = $cols * $tile
    $atlasH = $rowsNeeded * $tile

    $atlas = [System.Drawing.Bitmap]::new($atlasW, $atlasH)
    $g = [System.Drawing.Graphics]::FromImage($atlas)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.Clear([System.Drawing.Color]::Transparent)
        for ($i = 0; $i -lt $packable.Count; $i++) {
            $col = $i % $cols
            $rr = [int][Math]::Floor($i / $cols)
            $destRect = [System.Drawing.Rectangle]::new($col * $tile, $rr * $tile, $tile, $tile)
            $g.DrawImage($packable[$i].Bitmap, $destRect)
            $map[$packable[$i].Url] = $i   # zero-based tile ordinal
        }
    }
    finally {
        $g.Dispose()
        foreach ($p in $packable) { $p.Bitmap.Dispose() }
    }

    $parent = Split-Path -Parent $AtlasPath
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    Backup-IfExists -Path $AtlasPath | Out-Null
    $atlas.Save($AtlasPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $atlas.Dispose()

    $sizeKb = (Get-Item $AtlasPath).Length / 1KB
    Write-Host ("Favicons: packed {0} tile(s) into {1}x{2} atlas -> {3} ({4:N1} KB)." -f `
            $packable.Count, $atlasW, $atlasH, $AtlasPath, $sizeKb) -ForegroundColor Green
    return $map
}

# Build the atlas for $Rows and write each row's favicon_index property in place (blank when the row
# has no packed favicon), so a subsequent Write-CsvUtf8 -Columns $Schema persists indices that match
# the atlas just written. Mutates the row objects; the atlas PNG lands at $AtlasPath.
function Set-FaviconIndices {
    param([Parameter(Mandatory = $true)][object[]]$Rows, [string]$AtlasFile = $AtlasPath)
    $map = Build-FaviconAtlas -Rows $Rows -AtlasPath $AtlasFile
    foreach ($row in $Rows) {
        $url = [string]$row.url
        $idx = if ($map.ContainsKey($url)) { [string]$map[$url] } else { '' }
        if ($row.PSObject.Properties['favicon_index']) { $row.favicon_index = $idx }
        else { Add-Member -InputObject $row -NotePropertyName 'favicon_index' -NotePropertyValue $idx -Force }
    }
    return $map.Count
}

function Invoke-CatalogMaintenance {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    $allRows = @(Import-Csv -Path $ExistingCsv)
    if (-not $allRows -or $allRows.Count -eq 0) { throw "No rows in $ExistingCsv" }
    if ($Limit -gt 0 -and $PruneDead) { throw '-Limit cannot be combined with -PruneDead (pruning needs a full-catalog probe).' }

    # S0668: build the favicon atlas over the FULL catalog and stamp favicon_index on every row before
    # any CSV write. Indices are set on $allRows, so the prune path's $survivors (same objects) keep
    # them. When not pruning, persist the stamped catalog immediately since maintenance returns early.
    if ($WithFavicons) {
        Set-FaviconIndices -Rows $allRows -AtlasFile $AtlasPath | Out-Null
        if (-not $PruneDead) {
            $backup = Backup-IfExists -Path $ExistingCsv
            Write-CsvUtf8 -Rows $allRows -Path $ExistingCsv -Columns $Schema
            Write-Host ("Favicons: wrote favicon_index into {0} ({1} rows); backup -> {2}" -f $ExistingCsv, $allRows.Count, $backup) -ForegroundColor Green
        }
    }

    $rows = $allRows
    if ($Limit -gt 0 -and $allRows.Count -gt $Limit) {
        Write-Host ("Probing only first {0} of {1} rows (-Limit)" -f $Limit, $allRows.Count) -ForegroundColor DarkYellow
        $rows = @($allRows[0..($Limit - 1)])
    }

    $mode = if ($DeepSignal) { 'deep signal' } else { 'header liveness' }
    Write-Host ("Catalog maintenance mode ({0}) for {1} rows" -f $mode, $rows.Count) -ForegroundColor Cyan
    $probed = if ($SkipLiveness) { $rows }
    elseif ($DeepSignal) { Invoke-SignalProbe -Rows $rows -Activity 'Catalog signal' }
    else { Invoke-LivenessProbe -Rows $rows -Activity 'Catalog liveness' }

    $reportRows = $probed | ForEach-Object {
        [pscustomobject]@{
            status     = $_.liveness_status
            http       = $_.http_code
            bytes      = $_.signal_bytes
            note       = $_.liveness_note
            media_kind = $_.media_kind
            category   = $_.category
            topic      = $_.topic
            name       = $_.name
            url        = $_.url
            country    = $_.country
            homepage   = $_.homepage
        }
    }
    Write-CsvUtf8 -Rows ($reportRows | Sort-Object status, category, topic, name) -Path $CatalogLivenessReport `
        -Columns @('status', 'http', 'bytes', 'note', 'media_kind', 'category', 'topic', 'name', 'url', 'country', 'homepage')
    Show-LivenessSummary -Rows $probed -Title '=== Catalog liveness summary ==='
    Write-Host ''
    Write-Host ("Report written: {0}" -f $CatalogLivenessReport) -ForegroundColor Green

    # S1117: stamp the access flag from the deep-signal verdict onto the original catalog rows so a
    # prune write persists it. 'geo' (region-locked 403/451) -> access='geo'; every other verdict
    # clears access. Only on a full deep-signal probe (no -Limit) so a partial run never mislabels.
    # -Parallel returns deserialized copies, so map verdicts back to $allRows by url.
    if ($DeepSignal -and -not ($Limit -gt 0)) {
        $statusByUrl = @{}
        foreach ($p in $probed) { $statusByUrl[[string]$p.url] = [string]$p.liveness_status }
        foreach ($r in $allRows) {
            $acc = if ($statusByUrl[[string]$r.url] -eq 'geo') { 'geo' } else { '' }
            if ($r.PSObject.Properties['access']) { $r.access = $acc }
            else { Add-Member -InputObject $r -NotePropertyName 'access' -NotePropertyValue $acc -Force }
        }
    }

    # S1117: widen prune to 'dead','unknown' on an un-pinned deep-signal run - geo is now its own
    # verdict, so surviving 'unknown' rows are non-geo failures safe to drop. Header-only or a
    # caller-pinned -PruneStatuses stays exactly as given.
    $pruneStatuses = if ($DeepSignal -and -not $script:PruneStatusesExplicit) { @('dead', 'unknown') } else { $PruneStatuses }

    $pruneUrls = @($probed | Where-Object { $pruneStatuses -contains $_.liveness_status } | ForEach-Object { [string]$_.url })
    $pruneSet = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($u in $pruneUrls) { [void]$pruneSet.Add($u) }
    $pruneCount = $pruneSet.Count

    if ($pruneCount -eq 0) {
        Write-Host "`nNothing to prune (no rows classified: $($pruneStatuses -join ', '))." -ForegroundColor Green
        return
    }

    if (-not $PruneDead) {
        Write-Host "`nWould prune $pruneCount row(s) [status in: $($pruneStatuses -join ', ')] - re-run with -PruneDead to apply:" -ForegroundColor Yellow
        $reportRows | Where-Object { $pruneSet.Contains([string]$_.url) } | Sort-Object category, topic, name |
            ForEach-Object { " - [{0}] {1}  ({2})  {3}" -f $_.category, $_.name, $_.note, $_.url }
        return
    }

    $backup = Backup-IfExists -Path $ExistingCsv
    $survivors = $allRows | Where-Object { -not $pruneSet.Contains([string]$_.url) }
    Write-CsvUtf8 -Rows $survivors -Path $ExistingCsv -Columns $Schema
    Write-Host "`nPruned $pruneCount row(s); backup: $backup; catalog now $($survivors.Count) row(s)." -ForegroundColor Green
}

# Zip the catalog CSV (+ favicon atlas when present) and (re-)upload it as the GitHub Release asset
# users fetch on "Import list". Uploads whatever streams.csv is on disk at call time (already
# pruned/appended by the run).
#
# Compat invariant (S0668 strategic 3.2 / 11 #6): the zip MUST always contain a streams.csv entry,
# packed FIRST (entry 0), so an already-shipped app reaches the CSV via ZipInputStream without first
# streaming the whole atlas. Compress-Archive does NOT order entries by -Path, so the CSV is written
# with -Force (creating the zip with the CSV as the sole/first entry) and the atlas is appended in a
# separate -Update pass. The atlas is bundled only when it exists AND is within $MaxAtlasBytes - over
# the cap it is skipped (CSV-only publish) so the larger zip never breaks an old app's 30 s import
# callTimeout (S0583), rather than shipping an oversized zip.
function Invoke-PublishCatalog {
    param([string]$CsvPath = $ExistingCsv, [string]$Tag = $PublishTag, [string]$AtlasFile = $AtlasPath)
    if (-not (Test-Path $CsvPath)) { throw "Catalog CSV not found for publish: $CsvPath" }
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) { throw 'gh CLI not found on PATH - cannot upload the release asset (install GitHub CLI or upload temp/stream-catalog.zip manually).' }
    if (-not (Test-Path 'temp')) { New-Item -ItemType Directory -Path 'temp' -Force | Out-Null }
    $zip = 'temp/stream-catalog.zip'
    $rowCount = (Import-Csv $CsvPath).Count
    Write-Host ''
    Write-Host ("Publishing catalog ({0} rows): zipping {1} -> {2} .." -f $rowCount, $CsvPath, $zip) -ForegroundColor Cyan

    # CSV first (entry 0) - always present.
    Compress-Archive -Path $CsvPath -DestinationPath $zip -Force
    Write-Host ("  + {0} ({1:N1} KB) [entry 0]" -f (Split-Path -Leaf $CsvPath), ((Get-Item $CsvPath).Length / 1KB)) -ForegroundColor DarkGray

    # Atlas appended after the CSV, only if it exists and fits the size budget.
    $bundledAtlas = $false
    if (Test-Path $AtlasFile) {
        $atlasBytes = (Get-Item $AtlasFile).Length
        if ($atlasBytes -le $MaxAtlasBytes) {
            Compress-Archive -Path $AtlasFile -DestinationPath $zip -Update
            $bundledAtlas = $true
            Write-Host ("  + {0} ({1:N1} KB) [appended]" -f (Split-Path -Leaf $AtlasFile), ($atlasBytes / 1KB)) -ForegroundColor DarkGray
        }
        else {
            Write-Warning ("Favicon atlas {0} is {1:N1} KB > cap {2:N1} KB (30s import callTimeout budget, S0583); publishing CSV-only." -f `
                    (Split-Path -Leaf $AtlasFile), ($atlasBytes / 1KB), ($MaxAtlasBytes / 1KB))
        }
    }

    # S0925: never ship a favicon-indexed CSV without the matching atlas. The app's null-atlas import
    # path (FaviconAtlasStore.write(null, coords)) discards every favicon, so all channels degrade to
    # text (portrait icon-only chips show no icon at all). Fail loudly unless explicitly acknowledged.
    if (-not $bundledAtlas) {
        $csvFaviconIndexed = @(Import-Csv $CsvPath | Where-Object { $_.favicon_index -match '^\d+$' }).Count
        if ($csvFaviconIndexed -gt 0 -and -not $AllowFaviconlessPublish) {
            throw ("Refusing to publish: {0} row(s) carry favicon_index but no atlas is bundled (missing '{1}' or over the {2:N1} KB cap). The app would wipe all favicons. Restore/rebuild the atlas (-WithFavicons), or pass -AllowFaviconlessPublish to ship CSV-only intentionally." -f `
                    $csvFaviconIndexed, $AtlasFile, ($MaxAtlasBytes / 1KB))
        }
    }

    # Assert the compat invariant: a streams.csv-bearing entry exists and is entry 0.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $zip).Path)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $first = if ($entryNames.Count -gt 0) { $entryNames[0] } else { '' }
        if (-not ($first -like '*streams.csv')) {
            throw "Compat invariant violated: zip entry 0 is '$first', expected a streams.csv entry first."
        }
        if (-not ($entryNames | Where-Object { $_ -like '*streams.csv' })) {
            throw 'Compat invariant violated: zip has no streams.csv entry.'
        }
        Write-Host ("  zip entries: {0}" -f ($entryNames -join ', ')) -ForegroundColor DarkGray
    }
    finally { $archive.Dispose() }

    $zipKb = (Get-Item $zip).Length / 1KB
    $bundleNote = if ($bundledAtlas) { 'csv + atlas' } else { 'csv-only' }
    Write-Host ("  zip {0:N1} KB ({1}); uploading to release {2} (--clobber) .." -f $zipKb, $bundleNote, $Tag) -ForegroundColor Cyan
    & gh release upload $Tag $zip --clobber
    if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    Write-Host ("Published stream-catalog.zip -> {0} ({1} rows, {2:N1} KB, {3})." -f $Tag, $rowCount, $zipKb, $bundleNote) -ForegroundColor Green
}

if ($CatalogOnly) {
    Invoke-CatalogMaintenance
    if ($Publish) { Invoke-PublishCatalog }
    return
}

Write-Host "Collecting stream candidates [axis: $($Axis -join ', ')]" -ForegroundColor Cyan
$all = [System.Collections.Generic.List[object]]::new()

if ($Axis -contains 'genres') {
    Write-Host '* radio-browser by genre tag ..' -ForegroundColor Yellow
    foreach ($t in $GenreTags) {
        $r = Get-RadioBrowserStations -axis 'genres' -kind 'tag' -key $t -topicHint $t
        Write-Host ("    {0,-12} {1,3} stations" -f $t, $r.Count)
        $r | ForEach-Object { $all.Add($_) }
    }
}

if ($Axis -contains 'geo') {
    Write-Host '* radio-browser by country ..' -ForegroundColor Yellow
    foreach ($cc in $GeoCountries) {
        $r = Get-RadioBrowserStations -axis 'geo' -kind 'country' -key $cc -topicHint 'General'
        Write-Host ("    {0,-6} {1,3} stations" -f $cc, $r.Count)
        $r | ForEach-Object { $all.Add($_) }
    }
    Write-Host '* iptv-org by country ..' -ForegroundColor Yellow
    $r = Get-IptvCandidates -axis 'geo' -categories @() -countries $GeoCountries
    Write-Host ("    iptv-org geo: {0} channels" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'livetv') {
    Write-Host '* iptv-org by category (Live TV) ..' -ForegroundColor Yellow
    $r = Get-IptvCandidates -axis 'livetv' -categories $LiveTvCategories -countries @()
    Write-Host ("    iptv-org livetv: {0} channels" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'webcam') {
    Write-Host '* webcam / 24-7 seeds ..' -ForegroundColor Yellow
    $r = Get-WebcamSeeds -axis 'webcam'
    Write-Host ("    webcam seeds: {0}" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

Write-Host ("Raw candidates: {0}" -f $all.Count) -ForegroundColor Cyan

$existing = @()
$existingKeys = [System.Collections.Generic.HashSet[string]]::new()
$existingUrls = [System.Collections.Generic.HashSet[string]]::new()
if (Test-Path $ExistingCsv) {
    $existing = @(Import-Csv -Path $ExistingCsv)
    foreach ($e in $existing) {
        [void]$existingUrls.Add($e.url.Trim().ToLowerInvariant())
        $h = Get-Host2 $e.url
        [void]$existingKeys.Add("$h|$($e.name.Trim().ToLowerInvariant())")
    }
    Write-Host ("Existing catalog rows: {0}" -f $existing.Count)
}
else {
    Write-Warning "Existing CSV not found: $ExistingCsv (catalog will be created on apply)"
}

$batchUrls = [System.Collections.Generic.HashSet[string]]::new()
$deduped = [System.Collections.Generic.List[object]]::new()
foreach ($c in $all) {
    $u = $c.url.ToLowerInvariant()
    $h = Get-Host2 $c.url
    $nameKey = "$h|$($c.name.ToLowerInvariant())"
    if ($existingUrls.Contains($u) -or $existingKeys.Contains($nameKey)) { $c.dup = 'in-catalog'; continue }
    if (-not $batchUrls.Add($u)) { $c.dup = 'in-batch'; continue }
    $deduped.Add($c)
}
Write-Host ("After dedup: {0} new candidates" -f $deduped.Count) -ForegroundColor Cyan

if (-not $SkipLiveness -and $deduped.Count -gt 0) {
    $probed = Invoke-LivenessProbe -Rows @($deduped) -Activity 'Candidate liveness'
    $deduped = [System.Collections.Generic.List[object]]::new()
    $probed | ForEach-Object { $deduped.Add($_) }
    $aliveCount = ($deduped | Where-Object { $_.liveness_status -eq 'alive' }).Count
    Write-Host ("Liveness: {0}/{1} alive" -f $aliveCount, $deduped.Count) -ForegroundColor Cyan

    # S0805: second-stage deep-signal gate. The header probe above only reads the playlist status, so a
    # "pseudo-alive" channel (playlist 2xx, no segment/body) still reads 'alive' and would be appended.
    # Re-probe just the header-alive survivors for REAL media bytes and let that verdict decide the
    # append; header-dead/unknown rows are left untouched. -Parallel runspaces return deserialized
    # copies, so merge the verified rows back into $deduped by URL (unique per batch after dedup).
    if (-not $SkipDeepSignal -and $aliveCount -gt 0) {
        $headerAlive = @($deduped | Where-Object { $_.liveness_status -eq 'alive' })
        $verified = Invoke-SignalProbe -Rows $headerAlive -Activity 'Candidate signal'
        $verifiedByUrl = @{}
        foreach ($v in $verified) { $verifiedByUrl[[string]$v.url] = $v }
        $merged = [System.Collections.Generic.List[object]]::new()
        foreach ($row in $deduped) {
            $u = [string]$row.url
            if ($verifiedByUrl.ContainsKey($u)) { $merged.Add($verifiedByUrl[$u]) } else { $merged.Add($row) }
        }
        $deduped = $merged
        $signalAlive = @($deduped | Where-Object { $_.liveness_status -eq 'alive' }).Count
        $dropped = $headerAlive.Count - $signalAlive
        Write-Host ("Deep signal: {0}/{1} header-alive carry real signal ({2} pseudo-alive dropped)" -f `
                $signalAlive, $headerAlive.Count, $dropped) -ForegroundColor Cyan
    }
}

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
$candPath = Join-Path $OutDir 'stream-candidates.csv'
$reportPath = Join-Path $OutDir 'stream-candidates-report.csv'

$sorted = $deduped | Sort-Object `
    @{ Expression = { if ($_.liveness_status -eq 'alive') { 0 } else { 1 } } }, `
    axis, @{ Expression = 'score'; Descending = $true }, category, topic, name
# signal_bytes (S0805) is blank for header-only rows (never deep-probed) and carries the verified byte
# count for the deep-signal stage, so the report shows WHY a pseudo-alive row was dropped.
$reportColumns = $Schema + @('axis', 'score', 'liveness_status', 'http_code', 'liveness_note', 'signal_bytes', 'dup')
Write-CsvUtf8 -Rows $sorted -Path $reportPath -Columns $reportColumns

$keep = if ($SkipLiveness) { $sorted } else { $sorted | Where-Object { $_.liveness_status -eq 'alive' } }
Write-CsvUtf8 -Rows $keep -Path $candPath -Columns $Schema

Show-LivenessSummary -Rows $sorted -Title '=== Candidate liveness summary ==='
Write-Host ''
Write-Host ("Wrote {0} clean candidates -> {1}" -f @($keep).Count, $candPath) -ForegroundColor Green
Write-Host ("Wrote {0} report rows    -> {1}" -f @($sorted).Count, $reportPath) -ForegroundColor Green

if ($PreviewOnly) {
    Write-Host ''
    Write-Host 'Preview only mode: streams.csv was not changed.' -ForegroundColor Yellow
    return
}

$rowsToAppend = @($keep | Select-Object $Schema)
if ($rowsToAppend.Count -eq 0) {
    Write-Host ''
    Write-Host 'No alive new rows to append to streams.csv.' -ForegroundColor Yellow
    return
}

$backup = Backup-IfExists -Path $ExistingCsv
$mergedRows = @($existing) + $rowsToAppend
# S0668: stamp favicon_index on the full merged catalog (and write the atlas) before persisting, so
# the saved streams.csv matches the atlas just built. Default OFF -> behaviour unchanged.
if ($WithFavicons) { Set-FaviconIndices -Rows $mergedRows -AtlasFile $AtlasPath | Out-Null }
Write-CsvUtf8 -Rows $mergedRows -Path $ExistingCsv -Columns $Schema

Write-Host ''
if ($backup) {
    Write-Host ("Updated catalog: +{0} row(s), now {1}; backup -> {2}" -f $rowsToAppend.Count, $mergedRows.Count, $backup) -ForegroundColor Green
}
else {
    Write-Host ("Created catalog: {0} row(s) -> {1}" -f $mergedRows.Count, $ExistingCsv) -ForegroundColor Green
}

if ($Publish) { Invoke-PublishCatalog }
