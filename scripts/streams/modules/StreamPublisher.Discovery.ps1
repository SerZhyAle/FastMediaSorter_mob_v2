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
        category      = Get-CanonicalCategory -Category $category
        topic         = Get-CanonicalTopic -topic $topic
        name          = ($name -replace '\s+', ' ').Trim()
        url           = $url.Trim()
        media_kind    = $mediaKind
        protocol      = $protocol
        format        = $format
        bitrate       = $bitrate
        is_live       = $isLive.ToString().ToLowerInvariant()
        https         = $https.ToString().ToLowerInvariant()
        language      = Get-CanonicalLanguages -Languages $language
        country       = Get-CanonicalCountry -Country $country
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
        "/json/stations/bytagexact/${enc}?hidebroken=true&order=clickcount&reverse=true&limit=$PerQuery"
    }
    else {
        "/json/stations/bycountrycodeexact/${enc}?hidebroken=true&order=clickcount&reverse=true&limit=$PerQuery"
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

# iptv-org is an index, not a trust authority. A channel is eligible only when both its stable
# channel id and the actual stream host are explicitly known to belong to the broadcaster or its
# documented delivery CDN. Keep this deliberately small: adding a broadcaster is a reviewable
# source-policy change, not an automatic result of liveness.
$OfficialIptvSources = @{
    'AlJazeera.qa' = @('getaj.net')
    'AlJazeeraDocumentary.qa' = @('getaj.net')
    'AlJazeeraMubasher.qa' = @('getaj.net')
    'AlJazeeraMubasher24.qa' = @('getaj.net')
    'AlJazeeraMubasherBroadcast2.qa' = @('getaj.net')
    'BloombergTV.us' = @('bloomberg.com')
    'CGTN.cn' = @('cgtn.com')
    'CGTNArabic.cn' = @('cgtn.com')
    'CGTNDocumentary.cn' = @('cgtn.com')
    'CGTNFrench.cn' = @('cgtn.com')
    'CGTNRussian.cn' = @('cgtn.com')
    'CGTNSpanish.cn' = @('cgtn.com')
    'EuronewsEnglish.fr' = @('cdn-euronews.akamaized.net')
    'EuronewsFrench.fr' = @('cdn-euronews.akamaized.net')
    'EuronewsGerman.fr' = @('cdn-euronews.akamaized.net')
    'EuronewsItalian.fr' = @('cdn-euronews.akamaized.net')
    'EuronewsPortuguese.fr' = @('cdn-euronews.akamaized.net')
    'EuronewsSpanish.fr' = @('cdn-euronews.akamaized.net')
    'France24.fr' = @('live.france24.com')
    'NHKWorldJapan.jp' = @('nhkworld.jp')
    'RTIndia.in' = @('rttv.com')
    'RedBullTV.at' = @('rbmn-live.akamaized.net')
}

function Test-OfficialIptvSource {
    param([string]$channelId, [string]$url)
    if (-not $OfficialIptvSources.ContainsKey($channelId)) { return $false }
    $streamHost = Get-Host2 $url
    if ([string]::IsNullOrWhiteSpace($streamHost)) { return $false }
    foreach ($allowedHost in $OfficialIptvSources[$channelId]) {
        if ($streamHost -eq $allowedHost -or $streamHost.EndsWith(".$allowedHost")) { return $true }
    }
    return $false
}

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
        $c = $script:IptvChannels[$cid]
        if ($null -eq $c) { continue }
        if ($c.closed) { continue }
        if (-not (Test-OfficialIptvSource -channelId $cid -url $url)) { continue }
        if ($seenChannel.ContainsKey($cid)) { continue }

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
            -sourceKind 'PUBLIC_BROADCASTER' `
            -licenseNote 'Official broadcaster feed selected from the iptv-org index by channel and delivery-host allowlist' `
            -notes ("iptv-org approved source; channel=$cid; categories=$($cats -join '|'); quality=$($s.quality)") `
            -confidence 'high' -score 0
        if ($out.Count -ge ($PerQuery * 50)) { break }
    }
    return $out
}

# --- S1476 keyless community sources ------------------------------------------------------------
# Every source below was verified keyless on 2026-08-07: no token, no registration, no rate-limit
# challenge. That is the standing constraint, not a preference - a key-walled directory is out no
# matter how good its data. Each axis is opt-in via -Axis: none of them runs on a routine collection.

# Public webcams already sitting in the iptv-org index. `Get-IptvCandidates` cannot reach them: it
# requires a channel to appear in the maintained broadcaster allowlist, which no webcam ever will.
# This walks the same downloaded index with the OPPOSITE gate - category, not provenance - and keeps
# the header-gated drop, because a feed needing a Referer/User-Agent is unplayable for the app.
function Get-IptvWebcamCandidates {
    param([string]$axis)
    Initialize-Iptv
    $wanted = @('weather', 'outdoor', 'travel', 'relax')
    $seenChannel = @{}
    $out = @()
    foreach ($s in $script:IptvStreams) {
        $url = $s.url
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        if ($s.referrer -or $s.user_agent) { continue }
        $cid = $s.channel
        if ([string]::IsNullOrWhiteSpace($cid)) { continue }
        $c = $script:IptvChannels[$cid]
        if ($null -eq $c -or $c.closed) { continue }
        if ($seenChannel.ContainsKey($cid)) { continue }
        $cats = @($c.categories)
        if (-not ($cats | Where-Object { $wanted -contains $_ })) { continue }
        $seenChannel[$cid] = $true
        $fmt = Get-FormatFromUrl $url
        $lang = if ($c.languages -and @($c.languages).Count) { [string]@($c.languages)[0] } else { '' }
        $out += New-Candidate -axis $axis -category 'Live TV' -topic 'Webcam' `
            -name $c.name -url $url -mediaKind 'VIDEO' -protocol (Get-ProtocolFromUrl $url $fmt) `
            -format $fmt -bitrate '' -isLive $true -language $lang `
            -country ($c.country) -homepage ($c.website) -sourceKind 'COMMUNITY' `
            -licenseNote 'Publicly advertised webcam feed indexed by iptv-org' `
            -notes ("iptv-org webcam; channel=$cid; categories=$($cats -join '|')") `
            -confidence 'medium' -score 0
    }
    return $out
}

# Transport for London traffic cameras. These are NOT live streams - each is a short MP4 clip the
# camera re-publishes every few minutes - so they ship with is_live=false and their own rubric. A
# looping clip presented as an live channel would break the promise the rest of the catalog makes.
function Get-TflJamCams {
    param([string]$axis)
    $places = @()
    try { $places = Invoke-RestMethod -Uri 'https://api.tfl.gov.uk/Place/Type/JamCam' -TimeoutSec 90 -Headers @{ 'User-Agent' = $ua } }
    catch { Write-Warning "TfL JamCam index failed: $($_.Exception.Message)"; return @() }
    $out = @()
    foreach ($p in $places) {
        $videoProp = @($p.additionalProperties | Where-Object { $_.key -eq 'videoUrl' }) | Select-Object -First 1
        $url = [string]$videoProp.value
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        $name = ([string]$p.commonName -replace '^JamCams\s*', '').Trim()
        if ([string]::IsNullOrWhiteSpace($name)) { $name = [string]$p.id }
        $out += New-Candidate -axis $axis -category 'Live TV' -topic 'Traffic cams' `
            -name ("London: {0}" -f $name) -url $url -mediaKind 'VIDEO' -protocol 'HTTP' `
            -format 'mp4' -bitrate '' -isLive $false -language 'english' -country 'GB' `
            -homepage 'https://tfl.gov.uk/traffic/status/' -sourceKind 'PUBLIC_INSTITUTION' `
            -licenseNote 'Transport for London open data, Open Government Licence v3.0' `
            -notes 'TfL JamCam: short clip refreshed every few minutes, not a continuous stream' `
            -confidence 'high' -score 0
    }
    return $out
}

# WebRadioDB - small, curated, CI-validated. Its per-station metadata is the best in this report, so
# it is worth ingesting even though it overlaps rows we already carry; the dedup pass sorts that out.
function Get-WebRadioDbStations {
    param([string]$axis)
    $db = $null
    try {
        $db = Invoke-RestMethod -TimeoutSec 90 -Headers @{ 'User-Agent' = $ua } `
            -Uri 'https://jcorporation.github.io/webradiodb/db/index/webradiodb-combined.min.json'
    }
    catch { Write-Warning "WebRadioDB index failed: $($_.Exception.Message)"; return @() }
    # The combined index is a wrapper object; the stations live under `webradios`, itself keyed by
    # stream uri rather than being an array - enumerate that member's property values.
    $entries = if ($db -is [System.Array]) { $db }
    elseif ($db.PSObject.Properties['webradios']) { @($db.webradios.PSObject.Properties | ForEach-Object { $_.Value }) }
    else { @($db.PSObject.Properties | ForEach-Object { $_.Value }) }
    $out = @()
    foreach ($e in $entries) {
        $url = [string]$e.StreamUri
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        $fmt = if ($e.Codec) { ([string]$e.Codec).ToLowerInvariant() } else { Get-FormatFromUrl $url }
        $out += New-Candidate -axis $axis -category 'Radio' -topic (Get-CanonicalTopic ([string]$e.Genre)) `
            -name ([string]$e.Name) -url $url -mediaKind 'AUDIO' -protocol (Get-ProtocolFromUrl $url $fmt) `
            -format $fmt -bitrate ([string]$e.Bitrate) -isLive $true `
            -language (([string]$e.Languages -split ',' | Select-Object -First 1).Trim().ToLowerInvariant()) `
            -country ([string]$e.Country) -homepage ([string]$e.Homepage) -sourceKind 'COMMUNITY' `
            -licenseNote 'WebRadioDB (jcorporation/webradiodb) open station index' `
            -notes ("webradiodb; genre=$($e.Genre); region=$($e.Region)") -confidence 'medium' -score 0
    }
    return $out
}

# Radio Paradise publishes its own stream table for third-party players. Tiny but zero-maintenance,
# and the same class of listener-funded station as the SomaFM rows already in the catalog.
function Get-RadioParadiseStations {
    param([string]$axis)
    $list = $null
    try { $list = Invoke-RestMethod -Uri 'https://api.radioparadise.com/api/list_streams' -TimeoutSec 30 -Headers @{ 'User-Agent' = $ua } }
    catch { Write-Warning "Radio Paradise list failed: $($_.Exception.Message)"; return @() }
    $out = @()
    foreach ($channel in @($list.channels)) {
        # Highest-quality non-FLAC variant: FLAC is far heavier than a mobile listener needs, and the
        # app has no bandwidth negotiation to fall back from it.
        $stream = @($channel.streams | Where-Object { $_.url -and [string]$_.label -notmatch 'flac' }) | Select-Object -Last 1
        if (-not $stream) { continue }
        # The API returns the url without a scheme.
        $url = [string]$stream.url
        if ($url -notmatch '^https?://') { $url = "https://$url" }
        $out += New-Candidate -axis $axis -category 'Radio' -topic 'Eclectic' `
            -name ("Radio Paradise - {0}" -f $channel.title) -url $url -mediaKind 'AUDIO' `
            -protocol (Get-ProtocolFromUrl $url 'aac') -format 'aac' -bitrate '320' -isLive $true `
            -language 'english' -country 'US' -homepage 'https://radioparadise.com/' `
            -sourceKind 'COMMUNITY' -licenseNote 'Radio Paradise listener-funded station, public stream list' `
            -notes ("radioparadise; channel={0}" -f $channel.chan) -confidence 'high' -score 0
    }
    return $out
}

# AKC's player endpoint has no index, so the broadcast ids are enumerated. Every id becomes a
# candidate and the liveness gate decides: probing here would duplicate the pass that follows.
function Get-AkcBroadcasts {
    param([string]$axis, [int]$maxId = 240)
    $out = @()
    foreach ($id in 1..$maxId) {
        $url = "https://install.akctvcontrol.com/speed/broadcast/$id/desktop-playlist.m3u8"
        $out += New-Candidate -axis $axis -category 'Live TV' -topic 'Webcam' `
            -name ("AKC live cam {0}" -f $id) -url $url -mediaKind 'VIDEO' -protocol 'HLS' `
            -format 'm3u8' -bitrate '' -isLive $true -language 'english' -country 'US' `
            -homepage 'https://www.akc.tv/' -sourceKind 'COMMUNITY' `
            -licenseNote 'American Kennel Club public web player endpoint' `
            -notes ("akc enumeration id=$id") -confidence 'low' -score 0
    }
    return $out
}

# laut.fm - the whole community station database in one request. Its artwork is a first-class API
# field (`images.station_*`), which matters: every station's page_url is on the same laut.fm domain,
# so the homepage favicon crawl would stamp one identical icon on all of them. The station image is
# seeded straight into the artwork cache instead, keyed by the same homepage the atlas builders use.
function Get-LautFmStations {
    param([string]$axis, [int]$imageBudget = 0)
    $stations = @()
    try { $stations = Invoke-RestMethod -Uri 'https://api.laut.fm/stations' -TimeoutSec 300 -Headers @{ 'User-Agent' = $ua } }
    catch { Write-Warning "laut.fm index failed: $($_.Exception.Message)"; return @() }
    Write-Host ("    laut.fm: {0} station(s) in the index" -f $stations.Count) -ForegroundColor DarkGray
    $out = @()
    $imaged = 0
    foreach ($s in $stations) {
        if ($s.active -ne $true) { continue }
        $url = [string]$s.stream_url
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        $homepage = [string]$s.page_url
        $genre = if ($s.genres -and @($s.genres).Count) { [string]@($s.genres)[0] } else { '' }
        $out += New-Candidate -axis $axis -category 'Radio' -topic (Get-CanonicalTopic $genre) `
            -name ([string]$s.display_name) -url $url -mediaKind 'AUDIO' -protocol 'ICY' -format 'mp3' `
            -bitrate '' -isLive $true -language 'german' -country 'DE' -homepage $homepage `
            -sourceKind 'COMMUNITY' -licenseNote 'laut.fm community station, public API index' `
            -notes ("laut.fm; genres=$(@($s.genres) -join '|'); location=$($s.location)") `
            -confidence 'medium' -score 0
        if ($imageBudget -gt 0 -and $imaged -lt $imageBudget) {
            if (Save-ArtworkFromUrl -Homepage $homepage -ImageUrl ([string]$s.images.station_640x640)) { $imaged++ }
        }
    }
    if ($imageBudget -gt 0) {
        Write-Host ("    laut.fm: seeded {0} station image(s) into the artwork cache" -f $imaged) -ForegroundColor DarkGray
    }
    return $out
}

# Xiph's public Icecast directory: self-hosted hobbyist stations that opted in to being listed. Only
# ~5% are HTTPS; the owner accepted plain http here (2026-08-07) because the alternative is dropping
# 95% of the source. The `https` column records the truth either way.
function Get-XiphYpStations {
    param([string]$axis)
    $xml = $null
    try {
        $raw = Invoke-WebRequest -Uri 'https://dir.xiph.org/yp.xml' -TimeoutSec 180 -Headers @{ 'User-Agent' = $ua } -UseBasicParsing
        $xml = [xml]$raw.Content
    }
    catch { Write-Warning "Xiph YP fetch failed: $($_.Exception.Message)"; return @() }
    $out = @()
    foreach ($entry in @($xml.directory.entry)) {
        $url = [string]$entry.listen_url
        if ([string]::IsNullOrWhiteSpace($url)) { continue }
        $serverType = [string]$entry.server_type
        $fmt = switch -Regex ($serverType) {
            'aacp?' { 'aac'; break }
            'ogg' { 'ogg'; break }
            default { 'mp3' }
        }
        $out += New-Candidate -axis $axis -category 'Radio' -topic (Get-CanonicalTopic ([string]$entry.genre)) `
            -name ([string]$entry.server_name) -url $url -mediaKind 'AUDIO' `
            -protocol (Get-ProtocolFromUrl $url $fmt) -format $fmt -bitrate ([string]$entry.bitrate) `
            -isLive $true -language '' -country '' -homepage '' -sourceKind 'COMMUNITY' `
            -licenseNote 'Xiph public Icecast YP directory, self-listed stream' `
            -notes ("xiph yp; genre=$($entry.genre); server_type=$serverType") -confidence 'low' -score 0
    }
    return $out
}

function Get-OfficialTvSeeds {
    param([string]$axis)
    # Direct feeds are retained here even when iptv-org also lists them. This provides an
    # independently attributable baseline if an index record disappears or its ordering changes.
    $seeds = @(
        @{ name = 'Red Bull TV'; topic = 'Sports'; url = 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8'; country = 'AT'; language = 'english'; homepage = 'https://www.redbull.com/int-en/channels/best-of-red-bull-stream'; source = 'PUBLIC_BROADCASTER'; note = 'Red Bull Media House public 24/7 HLS feed' }
        @{ name = 'Bloomberg TV US'; topic = 'Business'; url = 'https://www.bloomberg.com/media-manifest/streams/us.m3u8'; country = 'US'; language = 'english'; homepage = 'https://www.bloomberg.com/live/us/btv'; source = 'PUBLIC_BROADCASTER'; note = 'Bloomberg public live HLS feed' }
        @{ name = 'Bloomberg TV Europe'; topic = 'Business'; url = 'https://www.bloomberg.com/media-manifest/streams/eu.m3u8'; country = 'US'; language = 'english'; homepage = 'https://www.bloomberg.com/live/europe'; source = 'PUBLIC_BROADCASTER'; note = 'Bloomberg public live HLS feed' }
        @{ name = 'Bloomberg TV Asia'; topic = 'Business'; url = 'https://www.bloomberg.com/media-manifest/streams/asia.m3u8'; country = 'US'; language = 'english'; homepage = 'https://www.bloomberg.com/live/asia'; source = 'PUBLIC_BROADCASTER'; note = 'Bloomberg public live HLS feed' }
        @{ name = 'Euronews English'; topic = 'News'; url = 'https://cdn-euronews.akamaized.net/live/eds/euronews-en/25080/euronews-en.m3u8'; country = 'FR'; language = 'english'; homepage = 'https://www.euronews.com/live'; source = 'PUBLIC_BROADCASTER'; note = 'Euronews public live HLS feed' }
        @{ name = 'DW (Deutsche Welle) English'; topic = 'News'; url = 'https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/master.m3u8'; country = 'DE'; language = 'english'; homepage = 'https://www.dw.com/en/live-tv/channel-english'; source = 'PUBLIC_BROADCASTER'; note = 'Deutsche Welle public live HLS feed' }
        @{ name = 'France 24 English'; topic = 'News'; url = 'https://live.france24.com/hls/live/2037218-b/F24_EN_HI_HLS/master_5000.m3u8'; country = 'FR'; language = 'english'; homepage = 'https://www.france24.com/en/live'; source = 'PUBLIC_BROADCASTER'; note = 'France 24 public live HLS feed' }
        @{ name = 'Al Jazeera English'; topic = 'News'; url = 'https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8'; country = 'QA'; language = 'english'; homepage = 'https://www.aljazeera.com/live/'; source = 'PUBLIC_BROADCASTER'; note = 'Al Jazeera public live HLS feed' }
        @{ name = 'CGTN English'; topic = 'News'; url = 'https://english-livebkali.cgtn.com/live/encgtn.m3u8'; country = 'CN'; language = 'english'; homepage = 'https://www.cgtn.com/tv'; source = 'PUBLIC_BROADCASTER'; note = 'CGTN public live HLS feed' }
        @{ name = 'RT India'; topic = 'News'; url = 'https://rt-india.rttv.com/dvr/rtindia/playlist.m3u8'; country = 'IN'; language = 'english'; homepage = 'https://www.rt.com/'; source = 'PUBLIC_BROADCASTER'; note = 'RT public live HLS feed' }
    )
    $out = @()
    foreach ($seed in $seeds) {
        $out += New-Candidate -axis $axis -category 'Live TV' -topic $seed.topic -name $seed.name -url $seed.url `
            -mediaKind 'VIDEO' -protocol 'HLS' -format 'm3u8' -bitrate '' -isLive $true `
            -language $seed.language -country $seed.country -homepage $seed.homepage `
            -sourceKind $seed.source -licenseNote $seed.note -notes 'direct official source' `
            -confidence 'high' -score 0
    }
    return $out
}

function Get-WebcamSeeds {
    param([string]$axis)
    # Public 24/7 camera feeds. The topic is intentionally Webcam so the catalog filter keeps this
    # viewing experience separate from ordinary outdoor, news, and documentary TV channels.
    $seeds = @(
        @{ name = '3Cat Weather Cameras'; url = 'https://directes-tv-int.3catdirectes.cat/live-content/beauties-hls/master.m3u8'; country = 'ES'; language = 'catalan'; homepage = 'https://www.3cat.cat/3cat/directes/beauties/'; source = 'COMMUNITY'; note = '3Cat public live weather camera feed' }
        @{ name = 'Uhu Owl Cam'; url = 'https://uhu.streaming.pixtura.de/live/Uhu2.stream/playlist.m3u8'; country = 'DE'; language = 'german'; homepage = 'https://uhu.webcam.pixtura.de/'; source = 'COMMUNITY'; note = 'Uhu-Webcam public live owl camera' }
        @{ name = 'WildEarth Safari'; url = 'https://dqga3jatxofgx.cloudfront.net/WildEarth.m3u8'; country = 'ZA'; language = 'english'; homepage = 'https://wildearth.tv/'; source = 'COMMUNITY'; note = 'WildEarth public live wildlife safari camera' }
        @{ name = 'AKC Puppies'; url = 'https://install.akctvcontrol.com/speed/broadcast/140/desktop-playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://www.akc.tv/'; source = 'COMMUNITY'; note = 'AKC public live puppy camera' }
        @{ name = '30A Darcizzle Offshore'; url = 'https://30a-tv.com/darcizzle.m3u8'; country = 'US'; language = 'english'; homepage = 'https://30a.tv/'; source = 'COMMUNITY'; note = '30A public offshore and beach camera' }
        @{ name = 'San Diego Zoo Panda Cam'; url = 'https://zssd-panda2024.hls.camzonecdn.com/CamzoneStreams/zssd-panda2024/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live panda camera' }
        @{ name = 'San Diego Zoo Platypus Cam'; url = 'https://zssd-platypus.hls.camzonecdn.com/CamzoneStreams/zssd-platypus/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live platypus camera' }
        @{ name = 'San Diego Zoo Baboon Cam'; url = 'https://zssd-baboon.hls.camzonecdn.com/CamzoneStreams/zssd-baboon/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live baboon camera' }
        @{ name = 'San Diego Zoo Hippo Cam'; url = 'https://zssd-hippo.hls.camzonecdn.com/CamzoneStreams/zssd-hippo/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live hippo camera' }
        @{ name = 'San Diego Zoo Penguin Cam'; url = 'https://zssd-penguin.hls.camzonecdn.com/CamzoneStreams/zssd-penguin/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live penguin camera' }
        @{ name = 'San Diego Zoo Koala Cam'; url = 'https://zssd-koala.hls.camzonecdn.com/CamzoneStreams/zssd-koala/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live koala camera' }
        @{ name = 'San Diego Zoo Tiger Cam'; url = 'https://zssd-tiger.hls.camzonecdn.com/CamzoneStreams/zssd-tiger/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live tiger camera' }
        @{ name = 'San Diego Zoo Polar Bear Cam'; url = 'https://polarplunge.hls.camzonecdn.com/CamzoneStreams/polarplunge/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live polar bear camera' }
        @{ name = 'San Diego Zoo Ape Cam'; url = 'https://ape.hls.camzonecdn.com/CamzoneStreams/ape/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live ape camera' }
        @{ name = 'San Diego Zoo Elephant Cam'; url = 'https://elephants.hls.camzonecdn.com/CamzoneStreams/elephants/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live elephant camera' }
        @{ name = 'San Diego Zoo Giraffe Cam'; url = 'https://zssd-kijami.hls.camzonecdn.com/CamzoneStreams/zssd-kijami/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live giraffe camera' }
        @{ name = 'San Diego Zoo Condor Cam'; url = 'https://zssd-condorhd.hls.camzonecdn.com/CamzoneStreams/zssd-condorhd/Playlist.m3u8'; country = 'US'; language = 'english'; homepage = 'https://zoo.sandiegozoo.org/live-cams'; source = 'COMMUNITY'; note = 'San Diego Zoo public live condor camera' }
        @{ name = 'AccuWeather Network'; url = 'https://gpuserver3.tier1streams.com/AccuWeather/index.m3u8'; country = 'US'; language = 'english'; homepage = 'https://www.accuweather.com/'; source = 'COMMUNITY'; note = 'AccuWeather live weather network' }
    )
    $out = @()
    foreach ($w in $seeds) {
        $out += New-Candidate -axis $axis -category 'Live TV' -topic 'Webcam' -name $w.name -url $w.url `
            -mediaKind 'VIDEO' -protocol 'HLS' -format 'm3u8' -bitrate '' -isLive $true `
            -language $w.language -country $w.country -homepage $w.homepage `
            -sourceKind $w.source -licenseNote $w.note -notes 'seed 24/7 public feed' -confidence 'medium' -score 0
    }
    return $out
}

