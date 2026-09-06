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

# Grouping values are a producer contract: the Android filter matches these ids directly, so each source
# must converge before a candidate reaches a CSV write. Categories and countries preserve an unknown value
# for review; topics intentionally keep their existing closed-set fallback of General.
function Get-CanonicalCategory {
    param([string]$Category)
    $normalized = ($Category ?? '').Trim().ToLowerInvariant() -replace '\s+', ' '
    switch ($normalized) {
        { $_ -in @('radio', 'radio (somafm)', 'somafm') } { return 'Radio' }
        { $_ -in @('live tv', 'tv', 'television') } { return 'Live TV' }
        { $_ -in @('open movies', 'movie', 'movies', 'on demand video', 'on-demand video') } {
            return 'On-demand video'
        }
        { $_ -in @('test', 'test stream', 'test streams') } { return 'Test streams' }
        default { return $Category.Trim() }
    }
}

function Get-CanonicalLanguageToken {
    param([string]$Language)
    $normalized = ($Language ?? '').Trim().ToLowerInvariant() -replace '\s+', ' '
    switch ($normalized) {
        { $_ -in @('american english', 'british english', 'english uk', 'engilsh') } { return 'english' }
        { $_ -in @('deutsch', 'gernan', 'gerrnan') } { return 'german' }
        { $_ -in @('español argentino', 'español internacional', '#spanish') } { return 'spanish' }
        { $_ -in @('brazilian portuguese', 'português brasileiro', 'portugues do brasil', 'português (br)') } {
            return 'portuguese'
        }
        'bahasa indonesia' { return 'indonesian' }
        'ภาษาไทย' { return 'thai' }
        default { return $normalized }
    }
}

function Get-CanonicalLanguages {
    param([string]$Languages)
    $raw = ($Languages ?? '').Trim()
    if (-not $raw) { return '' }
    if ($raw.ToLowerInvariant() -eq 'english german') { return 'english,german' }
    $tokens = @($raw -split '[,;/|]' |
        ForEach-Object { Get-CanonicalLanguageToken -Language $_ } |
        Where-Object { $_ } |
        Select-Object -Unique)
    return $tokens -join ','
}

function Get-CountryNameToCode {
    if ($script:CountryNameToCode) { return $script:CountryNameToCode }
    $map = @{}
    foreach ($culture in [System.Globalization.CultureInfo]::GetCultures(
            [System.Globalization.CultureTypes]::SpecificCultures)) {
        try {
            $region = [System.Globalization.RegionInfo]::new($culture.Name)
            foreach ($name in @($region.EnglishName, $region.NativeName, $region.DisplayName)) {
                $key = ($name ?? '').Trim().ToLowerInvariant() -replace '\s+', ' '
                if ($key) { $map[$key] = $region.TwoLetterISORegionName }
            }
        } catch {
            # A culture without a region is not a catalogue value and contributes no alias.
        }
    }
    $script:CountryNameToCode = $map
    return $map
}

function Get-CanonicalCountry {
    param([string]$Country)
    $raw = ($Country ?? '').Trim()
    if (-not $raw) { return '' }
    $upper = $raw.ToUpperInvariant()
    if ($upper -match '^[A-Z]{2}$') { return $upper }
    switch ($raw.ToLowerInvariant()) {
        'uk' { return 'GB' }
        'usa' { return 'US' }
    }
    $key = $raw.ToLowerInvariant() -replace '\s+', ' '
    return (Get-CountryNameToCode)[$key] ?? $raw
}

function Map-IptvTopic([string]$cat) {
    switch ($cat) {
        'news' { 'News' }
        'documentary' { 'Documentary' }
        'movies' { 'Movies & Series' }
        'music' { 'Pop' }
        'sports' { 'Sports' }
        'kids' { 'Kids' }
        'family' { 'Kids' }
        'animation' { 'Kids' }
        'science' { 'Documentary' }
        'general' { 'General' }
        'entertainment' { 'General' }
        default { Get-CanonicalTopic $cat }
    }
}

function Normalize-PruneStatuses([string[]]$Statuses) {
    @($Statuses |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ })
}

# S2645: the name column is the only facet copied from the upstream directory untouched, and the four
# functions below are the repair rules the -NormalizeNames mode applies to it. They are pure - no IO, no
# network - so the mode that rewrites a shipped bank can be reasoned about from its tests alone.
#
# The rules NEVER drop a row. The bank's inclusion policy is every live channel, and the last mass
# removal cost 1 321 live stations along with the pins filed against them (S1830, S1832); a nameless row
# therefore has a name derived for it rather than being deleted.

# Two decoding passes cover the double-encoded names measured in the bank (`102 FM L&amp;#039;Originale`
# needs two), and the ceiling exists so a station whose real name contains the literal text of an entity
# cannot be rewritten indefinitely.
$script:CatalogNameDecodePasses = 3

# Anchored at the start only, and it requires a digit: `- 0 N - Blues on Radio` is a serialised encoder
# slot, while `- NEUERSCHEINUNGEN - Radio Charts` is a real station name that begins with a dash.
$script:CatalogNameMachinePrefix = '^\s*-\s*\d+\s*\p{L}?\s*-\s+'

# Trailing separators only. Leading punctuation is deliberately absent: `.977 Country`,
# `#joint radio Blues Rock` and `_Funky Corner Radio (USA)` are the stations' own names, and trimming
# them would be this repair inventing a defect of its own.
$script:CatalogNameTrailingSeparators = ' -_|,;:'

# Names the encoder wrote because the broadcaster never set one, lower-cased with runs of whitespace
# already collapsed. Every entry is measured in the published bank, not guessed: the counts behind the
# top of this list are in the S2645 strategic spec section 5. Grows as new encoder defaults surface.
$script:CatalogNamePlaceholders = @(
    'online radio',
    'онлайн радио',
    'unspecified name',
    'default stream',
    'orban opticodec-pc encoder',
    'this is my server name',
    'my station name',
    'my radio',
    'mb studio',
    'mbstudio',
    'mbstudiocloud',
    'mb recaster',
    'radioboss stream',
    'radiocaster stream',
    'instreamer',
    'stream',
    'streaming',
    'no name',
    'noname',
    'unknown',
    'untitled',
    'new station',
    'server 1',
    'testserver 1',
    'test',
    'test stream',
    'radio',
    '(null)',
    'null'
)

# Repair one catalog name. Returns the input unchanged when no rule applies.
function Repair-CatalogName {
    param([string]$Name)
    $value = [string]$Name
    if ([string]::IsNullOrWhiteSpace($value)) { return '' }
    for ($pass = 0; $pass -lt $script:CatalogNameDecodePasses; $pass++) {
        $decoded = [System.Net.WebUtility]::HtmlDecode($value)
        if ($decoded -ceq $value) { break }
        $value = $decoded
    }
    $value = $value -replace $script:CatalogNameMachinePrefix, ''
    $value = ($value -replace '\s+', ' ').Trim()
    return $value.TrimEnd($script:CatalogNameTrailingSeparators.ToCharArray()).Trim()
}

# The token an uninformative name is rebuilt from: the host, plus the port when the row carries a
# non-default one.
#
# The port is in here because of a measurement, not for completeness. Shared streaming hosts give every
# tenant the same hostname and a port of its own, so a host-only token leaves the wall standing: over the
# 1 622 uninformative rows of the 2026-09-06 bank, a bare host still left 1 099 rows sharing a name
# (67 of them reading `Online Radio (hoth.alonhosting.com)`), while host-and-port left 83. Appending the
# mount path as well reaches 0, and is deliberately not done - it puts `/stream` in front of the user in
# every one of those names to settle 83 rows out of 19 149.
function Get-CatalogNameFromUrl {
    param([string]$Url)
    $trimmed = ([string]$Url).Trim()
    if ($trimmed -notmatch '^(?<scheme>[A-Za-z][A-Za-z0-9+.\-]*)://(?<authority>[^/?#]*)') {
        return ''
    }
    $scheme = $Matches['scheme'].ToLowerInvariant()
    $authority = $Matches['authority']
    $at = $authority.LastIndexOf('@')
    if ($at -ge 0) { $authority = $authority.Substring($at + 1) }
    if (-not $authority) { return '' }

    $hostPart = $authority
    $port = ''
    $colon = $authority.LastIndexOf(':')
    if ($colon -ge 0 -and $authority.Substring($colon + 1) -match '^\d+$') {
        $hostPart = $authority.Substring(0, $colon)
        $port = $authority.Substring($colon + 1)
    }
    if (-not $hostPart) { return '' }

    $defaultPort = switch ($scheme) {
        'http' { '80' }
        'https' { '443' }
        'rtsp' { '554' }
        default { '' }
    }
    $suffix = if ($port -and $port -ne $defaultPort) { ':' + $port } else { '' }
    return $hostPart.ToLowerInvariant() + $suffix
}

# The subset of the placeholders that is REPLACED by the token rather than keeping its words in front of
# it. The test is what the existing name is about: a value that names the encoder software, the server, or
# simply asserts there is no name tells the user nothing a host already tells them better, so carrying it
# through only lengthens the result - `Orban Opticodec-PC Encoder (stream.valenzuelasistemas.net.ar:8000)`
# is 66 characters that say less than its last 37.
#
# What stays OUT of this list, and is therefore kept and suffixed: the values that describe the MEDIUM -
# `Online Radio`, `Radio`, `stream`. In a bank holding radio, live TV and webcams side by side, "this one
# is a radio" is a real signal, and it is the broadcaster's own word for the channel.
$script:CatalogNameNullTokens = @(
    '(null)',
    'null',
    'no name',
    'noname',
    'unknown',
    'untitled',
    'unspecified name',
    'new station',
    'my station name',
    'my radio',
    'this is my server name',
    'server 1',
    'testserver 1',
    'test',
    'test stream',
    'default stream',
    'orban opticodec-pc encoder',
    'mb studio',
    'mbstudio',
    'mbstudiocloud',
    'mb recaster',
    'radioboss stream',
    'radiocaster stream',
    'instreamer'
)

# True when the name tells the user nothing: no letter and no digit at all, or a known encoder default.
function Test-CatalogNameUninformative {
    param([string]$Name)
    $value = ([string]$Name).Trim()
    if (-not $value) { return $true }
    if ($value -notmatch '[\p{L}\p{N}]') { return $true }
    $folded = ($value.ToLowerInvariant() -replace '\s+', ' ')
    return $script:CatalogNamePlaceholders -contains $folded
}

# True when nothing in the name is worth carrying into the repaired one.
function Test-CatalogNameDiscardable {
    param([string]$Name)
    $value = ([string]$Name).Trim()
    if (-not $value) { return $true }
    if ($value -notmatch '[\p{L}\p{N}]') { return $true }
    $folded = ($value.ToLowerInvariant() -replace '\s+', ' ')
    return $script:CatalogNameNullTokens -contains $folded
}

# Resolve the final name for one catalog row: repair it, then rebuild it from the row's host when the
# result still says nothing. Returns the name and the rule that produced it ('' when nothing fired), so
# the caller's move report can be read rule by rule instead of row by row.
function Resolve-CatalogName {
    param([string]$Name, [string]$Url)
    $repaired = Repair-CatalogName -Name $Name
    if (-not (Test-CatalogNameUninformative -Name $repaired)) {
        $rule = if ($repaired -cne ([string]$Name)) { 'repair' } else { '' }
        return [pscustomobject]@{ Name = $repaired; Rule = $rule }
    }
    $token = Get-CatalogNameFromUrl -Url $Url
    # No host to build from: leave the row exactly as it arrived. The publish gate then refuses the bank
    # and names the row, which is the honest outcome - inventing a name here would hide the real defect.
    if (-not $token) {
        $rule = if ($repaired -cne ([string]$Name)) { 'repair' } else { '' }
        return [pscustomobject]@{ Name = $repaired; Rule = $rule }
    }
    if (Test-CatalogNameDiscardable -Name $repaired) {
        return [pscustomobject]@{ Name = $token; Rule = 'derive-replace' }
    }
    return [pscustomobject]@{ Name = ('{0} ({1})' -f $repaired, $token); Rule = 'derive-suffix' }
}

