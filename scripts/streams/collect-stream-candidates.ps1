#requires -Version 7
<#
.SYNOPSIS
  Discover new stream-catalog candidates for delivery/stream-catalog/streams.csv.

.DESCRIPTION
  Pulls free/publicly-listed streams from community/official sources, normalizes them to the
  17-column catalog schema, de-duplicates against the existing streams.csv, runs a liveness probe,
  and writes two artifacts to the output dir:
    - stream-candidates.csv         : exactly the 17 catalog columns, ready to review + append.
    - stream-candidates-report.csv  : same rows plus diagnostic columns (axis, liveness, http, dup, score).

  This NEVER edits streams.csv. The merge stays a manual review step (matches the existing
  temp/*-candidates / *-report workflow).

  Sources by axis:
    livetv : iptv-org public index (VIDEO / Live TV), official-leaning, NSFW + referrer/UA-gated dropped.
    genres : radio-browser community DB by exact tag (AUDIO), top by clickcount.
    geo    : radio-browser by country + iptv-org by country (under-represented regions).
    webcam : curated seed list of public 24/7 HLS feeds (NASA etc.), liveness-filtered.

  Legal boundary: standard catalog = free, publicly published streams only. Pirate "mega IPTV"
  lists and YouTube-live rips do NOT belong here (those are noLegal / yt-dlp territory).

.EXAMPLE
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -Axis genres,geo -PerQuery 30
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -SkipLiveness
#>
[CmdletBinding()]
param(
    [ValidateSet('livetv', 'genres', 'geo', 'webcam')]
    [string[]]$Axis = @('livetv', 'genres', 'geo', 'webcam'),

    [int]$PerQuery = 20,
    [int]$LivenessTimeoutSec = 12,
    [int]$Throttle = 12,
    [switch]$SkipLiveness,

    [string]$ExistingCsv = 'delivery/stream-catalog/streams.csv',
    [string]$OutDir = 'temp',

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

# ---- schema -----------------------------------------------------------------
$Schema = @(
    'category', 'topic', 'name', 'url', 'media_kind', 'protocol', 'format', 'bitrate',
    'is_live', 'https', 'language', 'country', 'homepage', 'source_kind',
    'license_note', 'notes', 'confidence'
)

# ---- helpers ----------------------------------------------------------------
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

# Build one normalized candidate PSCustomObject (17 schema cols + diagnostics).
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
        category     = $category
        topic        = $topic
        name         = ($name -replace '\s+', ' ').Trim()
        url          = $url.Trim()
        media_kind   = $mediaKind
        protocol     = $protocol
        format       = $format
        bitrate      = $bitrate
        is_live      = $isLive.ToString().ToLowerInvariant()
        https        = $https.ToString().ToLowerInvariant()
        language     = $language
        country      = $country
        homepage     = $homepage
        source_kind  = $sourceKind
        license_note = $licenseNote
        notes        = $notes
        confidence   = $confidence
        # diagnostics (not part of catalog schema):
        axis            = $axis
        score           = $score
        liveness_status = ''
        http_code       = ''
        dup             = ''
    }
}

# ---- pull: radio-browser genres / geo ---------------------------------------
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

# ---- pull: iptv-org ---------------------------------------------------------
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
        # Drop streams that need a custom referrer/user-agent - they won't "just play" in ExoPlayer.
        if ($s.referrer) { continue }
        if ($s.user_agent) { continue }
        $cid = $s.channel
        if ([string]::IsNullOrWhiteSpace($cid)) { continue }
        if ($seenChannel.ContainsKey($cid)) { continue }   # one feed per channel
        $c = $script:IptvChannels[$cid]
        if ($null -eq $c) { continue }
        if ($c.is_nsfw -eq $true) { continue }
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
        if ($out.Count -ge ($PerQuery * 50)) { break }   # global safety cap
    }
    return $out
}

# ---- pull: webcam / 24-7 seed ----------------------------------------------
function Get-WebcamSeeds {
    param([string]$axis)
    $seeds = @(
        @{ name = 'NASA TV Public (ISS/Live)'; url = 'https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8'; topic = 'Science & Space'; country = 'US'; homepage = 'https://www.nasa.gov/nasatv'; note = 'NASA public-affairs live TV channel, US government free public feed' }
        @{ name = 'NASA TV Media'; url = 'https://ntv2.akamaized.net/hls/live/2013923/NASA-NTV2-HLS/master.m3u8'; topic = 'Science & Space'; country = 'US'; homepage = 'https://www.nasa.gov/nasatv'; note = 'NASA media channel, US government free public feed' }
        @{ name = 'DW Documentary 24/7'; url = 'https://dwamdstream105.akamaized.net/hls/live/2015531/dwstream105/master.m3u8'; topic = 'Documentary'; country = 'DE'; homepage = 'https://www.dw.com/'; note = 'Deutsche Welle public broadcaster free 24/7 feed' }
    )
    $out = @()
    foreach ($w in $seeds) {
        $out += New-Candidate -axis $axis -category 'Live TV' -topic $w.topic -name $w.name -url $w.url `
            -mediaKind 'VIDEO' -protocol 'HLS' -format 'm3u8' -bitrate '' -isLive $true `
            -language 'english' -country $w.country -homepage $w.homepage `
            -sourceKind 'GOV' -licenseNote $w.note -notes 'seed 24/7 public feed' -confidence 'medium' -score 0
    }
    return $out
}

# ---- main: collect ----------------------------------------------------------
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

# ---- dedup: within batch + against existing streams.csv ---------------------
$existingKeys = [System.Collections.Generic.HashSet[string]]::new()
$existingUrls = [System.Collections.Generic.HashSet[string]]::new()
if (Test-Path $ExistingCsv) {
    $existing = Import-Csv -Path $ExistingCsv
    foreach ($e in $existing) {
        [void]$existingUrls.Add($e.url.Trim().ToLowerInvariant())
        $h = Get-Host2 $e.url
        [void]$existingKeys.Add("$h|$($e.name.Trim().ToLowerInvariant())")
    }
    Write-Host ("Existing catalog rows: {0}" -f $existing.Count)
}
else {
    Write-Warning "Existing CSV not found: $ExistingCsv (dedup vs catalog skipped)"
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

# ---- liveness probe ---------------------------------------------------------
if (-not $SkipLiveness -and $deduped.Count -gt 0) {
    Write-Host ("Liveness probing {0} URLs (throttle {1}, timeout {2}s) .." -f $deduped.Count, $Throttle, $LivenessTimeoutSec) -ForegroundColor Yellow
    $probed = $deduped | ForEach-Object -ThrottleLimit $Throttle -Parallel {
        $c = $_
        $timeout = $using:LivenessTimeoutSec
        $ua2 = $using:ua
        $status = 'unknown'; $code = ''
        try {
            $isHls = $c.format -eq 'm3u8'
            $resp = Invoke-WebRequest -Uri $c.url -TimeoutSec $timeout -MaximumRedirection 5 `
                -Headers @{ 'User-Agent' = $ua2 } -SkipHttpErrorCheck
            $code = [string]$resp.StatusCode
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
                if ($isHls) {
                    $body = ''
                    try { $body = [string]$resp.Content } catch { $body = '' }
                    if ($body -match '#EXTM3U') {
                        if ($body -match '#EXT-X-ENDLIST') { $status = 'alive-vod' }
                        elseif ($body -match '#EXT-X-STREAM-INF' -or $body -match '#EXTINF') { $status = 'alive' }
                        else { $status = 'alive-thin' }
                    }
                    else { $status = 'not-hls' }
                }
                else {
                    $status = 'alive'
                }
            }
            else { $status = 'dead' }
        }
        catch {
            $status = 'dead'; $code = $_.Exception.Message
        }
        $c.liveness_status = $status
        $c.http_code = $code
        $c
    }
    $deduped = [System.Collections.Generic.List[object]]::new()
    $probed | ForEach-Object { $deduped.Add($_) }
    $aliveCount = ($deduped | Where-Object { $_.liveness_status -like 'alive*' }).Count
    Write-Host ("Liveness: {0}/{1} alive" -f $aliveCount, $deduped.Count) -ForegroundColor Cyan
}

# ---- write artifacts --------------------------------------------------------
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
$candPath = Join-Path $OutDir 'stream-candidates.csv'
$reportPath = Join-Path $OutDir 'stream-candidates-report.csv'

# Report = everything (sorted: alive first, then by axis/score).
$sorted = $deduped | Sort-Object `
    @{ Expression = { if ($_.liveness_status -like 'alive*') { 0 } else { 1 } } }, `
    axis, @{ Expression = 'score'; Descending = $true }, category, topic, name
$sorted | Select-Object ($Schema + @('axis', 'score', 'liveness_status', 'http_code', 'dup')) |
    Export-Csv -Path $reportPath -NoTypeInformation -Encoding utf8

# Clean candidates = schema-only, alive (or unknown when liveness skipped), ready to append.
$keep = if ($SkipLiveness) { $sorted } else { $sorted | Where-Object { $_.liveness_status -like 'alive*' } }
$keep | Select-Object $Schema | Export-Csv -Path $candPath -NoTypeInformation -Encoding utf8

Write-Host ''
Write-Host ("Wrote {0} clean candidates -> {1}" -f $keep.Count, $candPath) -ForegroundColor Green
Write-Host ("Wrote {0} report rows    -> {1}" -f $sorted.Count, $reportPath) -ForegroundColor Green
Write-Host ''
Write-Host 'Next: review the report CSV, then append vetted rows from stream-candidates.csv to' -ForegroundColor DarkGray
Write-Host '      delivery/stream-catalog/streams.csv (header columns match 1:1).' -ForegroundColor DarkGray
