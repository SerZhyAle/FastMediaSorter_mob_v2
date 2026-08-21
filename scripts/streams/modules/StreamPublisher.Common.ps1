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
# --- Topic rubrics (S1477) ----------------------------------------------------------------------
# The catalog's `topic` cell is what the app shows as a "rubric" facet, and every source hands us its
# own free text: radio-browser tags, laut.fm genres, iptv-org categories, a station's own strapline.
# Left alone that produced 436 distinct rubrics over 3916 rows, 333 of them used once or twice - a
# picker nobody can use. Every topic is folded into the closed set below, at ingest and on demand.
#
# Two ordered stages, because neither alone is honest:
#  - the exact table pins values whose plain reading is wrong ("Adult Contemporary" is a pop format,
#    not adult content; "Chr" is Contemporary Hit Radio; "Blues Rock" belongs with rock, not blues);
#  - the pattern list then catches the long tail, and its ORDER is the specificity ranking. A rule
#    higher up wins, so 'metal' must be tested before 'rock' and 'adult contemporary' before 'adult'.
$script:TopicRubricExact = @{
    'adult contemporary' = 'Pop'; 'soft adult contemporary' = 'Pop'; 'chr' = 'Pop'; 'am pop' = 'Pop'
    'top 40' = 'Pop'; 'top hits' = 'Pop'; 'hits' = 'Pop'; 'charts' = 'Pop'; 'city pop' = 'Pop'
    'blues rock' = 'Rock'; 'gothic' = 'Rock'; 'darkwave' = 'Rock'; 'dark wave' = 'Rock'
    'new wave' = 'Rock'; 'aor' = 'Rock'; 'indy' = 'Rock'; 'anarchy' = 'Rock'
    # Pinned, not pattern-matched: a bare 'alternative' is a rock format, but 'alternative country'
    # must still reach the country rule, so the word cannot become a Rock pattern.
    'alternative' = 'Rock'; 'classic alternative' = 'Rock'; 'experimental' = 'Rock'
    # A seasonal pop format, not devotional programming - 'christ' would otherwise send it to Religious.
    'christmas music' = 'Pop'; 'holiday music (nov-dec)' = 'Pop'
    'anni 80' = 'Oldies'
    'hardcore' = 'Metal'; 'deathcore' = 'Metal'
    'beats' = 'Electronic'; 'mixes' = 'Electronic'; 'party' = 'Electronic'; 'balearic' = 'Electronic'
    'abstract' = 'Electronic'; 'garage' = 'Electronic'; 'ebm' = 'Electronic'
    'acoustic' = 'Chillout'; 'chilled trap' = 'Chillout'
    'vocal' = 'Jazz & Blues'; 'all-vinyl' = 'Jazz & Blues'
    'classics' = 'Classical'; 'blasmusik' = 'Classical'; 'orquestrada' = 'Classical'
    'evergreens' = 'Oldies'; 'discography' = 'Oldies'; 'archive' = 'Oldies'
    'calypso' = 'World'; 'roma' = 'World'; 'galicia' = 'World'; 'breton' = 'World'
    'amchikonkani' = 'World'; 'akan' = 'World'; 'amharic' = 'World'
    'الموسيقى العربية' = 'World'; 'arab music' = 'World'; 'arabic music' = 'World'
    'military' = 'Talk'; 'conspiracies' = 'Talk'; 'artists' = 'Talk'; 'p4' = 'Talk'
    'legislative' = 'Talk'; 'conservative' = 'Talk'; 'public' = 'Talk'
    'fantasy' = 'Movies & Series'; 'video games' = 'Movies & Series'; 'disney' = 'Kids'
    'life guide' = 'Lifestyle'; 'romance' = 'Lifestyle'; 'auto' = 'Lifestyle'; 'bikers' = 'Lifestyle'
    'commercial' = 'Business'; 'explicit' = 'Adult'
    'eclectic' = 'General'; 'variety' = 'General'; 'music' = 'General'; 'misc' = 'General'
    'others' = 'General'; 'undefined' = 'General'; 'bizzare' = 'General'; 'aris' = 'General'
    'ai' = 'General'; 'acir' = 'General'; 'adazoa' = 'General'; 'apache 207' = 'Hip-hop'
    # Its own rubric rather than 'Webcam': a TfL camera republishes a short clip, so it is not the
    # continuous feed the webcam rubric promises, and the owner asked for it to stay distinguishable.
    'traffic cams' = 'Traffic cams'; 'traffic' = 'Traffic cams'
}

# Ordered specificity ranking; first match wins. Patterns are matched against the lowercased topic.
$script:TopicRubricRules = @(
    @{ P = 'test pattern|^m3u8$|^https?:|^#$|^\d+kbps$'; R = 'Test' }
    @{ P = 'quran|قران|القرآن|islam|اسلامي|christ|gospel|bible|biblia|catholic|evangel|adventist|baptist|^ccm$|religio|cristian|alistair begg'; R = 'Religious' }
    @{ P = 'webcam|outdoor|traffic|^zoo|beach|weather|nature cam'; R = 'Webcam' }
    @{ P = 'metal|deathcore'; R = 'Metal' }
    @{ P = 'rap|hip.?hop|^trap$|^drill$|g-funk|deutschrap'; R = 'Hip-hop' }
    @{ P = 'punk|rock|grunge|deutschrock'; R = 'Rock' }
    @{ P = 'jazz|blues|bebop|bossa'; R = 'Jazz & Blues' }
    @{ P = 'classic(al)? music|opera|choral|orchestr|symphon|medieval|ancient music|cl[aá]ssic[oa]'; R = 'Classical' }
    @{ P = 'techno|house|trance|electro|^edm$|drum ?(and|&) ?bass|breakbeat|broken beat|chiptune|^dance|dance classics|club|^#?dj$|mashup'; R = 'Electronic' }
    @{ P = 'ambient|chill|lo-?fi|lounge|relax'; R = 'Chillout' }
    @{ P = 'soul|funk|r ?& ?b|rnb|boogie|amapiano|groovy'; R = 'R&B & Soul' }
    @{ P = 'reggae|dancehall|^ska$'; R = 'Reggae' }
    @{ P = 'country|folk|bluegrass|americana|celtic|ethnic|etnic|türkü|aboriginal|nordic'; R = 'Country & Folk' }
    @{ P = 'latin|salsa|bachata|cumbia|reggaeton|sertanej|pagode|^banda$|brasil|bras[ií]lia|mexic|tango|fiesta|clásicos|clasicos|baladas|argentin|bogota|buenos aires|^chile$|ciudad de m'; R = 'Latin' }
    @{ P = 'afro|afric|arab|bolly|india|indones|dangdut|koplo|campursari|yogyakarta|greek|chines|korea|^kpop$|^enka$|filipino|bangla|balkan|bosnia|biesiada|chanson|musique|müzik|world|international|^opm$|^turkey$|anadolu'; R = 'World' }
    @{ P = 'oldies|nostalgi|golden|^classic hits|^classic$|disco'; R = 'Oldies' }
    @{ P = '^#?(19|20)?\d{2}''?(s|er)\b|^\d{4}''?s'; R = 'Oldies' }
    @{ P = 'j-?pop|^pop|pop music|pop$|charts|hits|ballad'; R = 'Pop' }
    @{ P = 'news|noticia|actualidad|^infos?$|informa'; R = 'News' }
    @{ P = 'talk|podcast|audiobook|^books?$|drama|politic|debate|speech|public radio|culture|cultural'; R = 'Talk' }
    @{ P = 'sport|futbol|futebol|soccer|hockey|baseball|deporte|desporto|^spor$|era spor'; R = 'Sports' }
    @{ P = 'kids|child|crian|fairytale|cartoon'; R = 'Kids' }
    @{ P = 'movie|series|cinema|^film|anime'; R = 'Movies & Series' }
    @{ P = 'document|science|space|history'; R = 'Documentary' }
    @{ P = 'educat|learn|school|universit'; R = 'Education' }
    @{ P = 'comedy|humor'; R = 'Comedy' }
    @{ P = 'business|finance|econom'; R = 'Business' }
    @{ P = 'shop'; R = 'Shopping' }
    @{ P = 'lifestyle|cooking|^food|health|fashion'; R = 'Lifestyle' }
    @{ P = 'adult'; R = 'Adult' }
    @{ P = '^\d{2,4}([.,]\d+)?\s*(fm|am|mhz|khz)?$|^(fm|am)\b|\bfm\b|\bam\d|local|region|community radio|public radio|iheart|bauer radio|sveriges|duna|full service'; R = 'Local radio' }
)

# Write one already-known artwork URL straight into the crawl cache, under the key the atlas builders
# derive from a homepage. This is for a source that publishes a per-station image (laut.fm): crawling
# its pages would cost hours and return the platform's own icon, identical for every station on it.
# Never overwrites: an image already in the cache was either crawled or seeded, and both beat a refetch.
function Save-ArtworkFromUrl {
    param([string]$Homepage, [string]$ImageUrl)
    if ([string]::IsNullOrWhiteSpace($Homepage) -or [string]::IsNullOrWhiteSpace($ImageUrl)) { return $false }
    if (-not (Test-Path $LogoCacheDir)) { New-Item -ItemType Directory -Path $LogoCacheDir -Force | Out-Null }
    $cacheFile = Get-LogoCacheFile -homepage $Homepage -dir (Resolve-Path $LogoCacheDir).Path
    if (Test-Path $cacheFile) { return $false }
    try {
        $resp = Invoke-WebRequest -Uri $ImageUrl -UseBasicParsing -Headers @{ 'User-Agent' = $ua } `
            -ConnectionTimeoutSeconds $FaviconTimeoutSec -OperationTimeoutSeconds ($FaviconTimeoutSec * 2) `
            -MaximumRedirection 4 -ErrorAction Stop
        $bytes = $resp.Content
        if ($bytes -is [string]) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($bytes) }
        if (-not $bytes -or $bytes.Length -lt 256) { return $false }
        [System.IO.File]::WriteAllBytes($cacheFile, [byte[]]$bytes)
        return $true
    }
    catch { return $false }
}

# Fold one source-supplied topic into the closed rubric set. Everything unrecognised becomes 'General'
# rather than surviving as its own rubric - a rubric used by one station is noise in the picker.
function Get-CanonicalTopic {
    param([string]$topic)
    if ([string]::IsNullOrWhiteSpace($topic)) { return 'General' }
    # Typographic apostrophes are folded to the straight one FIRST, so every pattern below can assume a
    # single spelling. They must be written as escapes: PowerShell treats U+2018/U+2019 as string
    # delimiters, so a literal one inside a quoted pattern silently ends the string.
    $curly = "[`u{2018}`u{2019}`u{0060}`u{00B4}]"
    $normalized = ($topic -replace $curly, "'").Trim().ToLowerInvariant() -replace '\s+', ' '
    if ($script:TopicRubricExact.ContainsKey($normalized)) { return $script:TopicRubricExact[$normalized] }
    foreach ($rule in $script:TopicRubricRules) {
        if ($normalized -match $rule.P) { return $rule.R }
    }
    return 'General'
}

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

function Normalize-PruneStatuses([string[]]$Statuses) {
    @($Statuses |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ })
}

