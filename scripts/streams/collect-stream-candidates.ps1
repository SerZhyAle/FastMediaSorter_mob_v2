#requires -Version 7
<#
.SYNOPSIS
  Collect, validate, and maintain delivery/stream-catalog/streams.csv.

.DESCRIPTION
  Default mode discovers public broadcaster streams from direct official feeds and an approved
  subset of the iptv-org index, normalizes
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
    official : curated direct HLS feeds published by broadcasters and public institutions.
    livetv : approved iptv-org channels only; each channel id AND stream host must match the
             official-source policy. Header-gated (referrer/UA) streams are dropped.
    genres : radio-browser community DB by exact tag (AUDIO), top by clickcount.
    geo    : radio-browser by country + iptv-org by country (under-represented regions).
    webcam : curated public 24/7 HLS webcams (weather, wildlife, beaches, zoo animals), liveness-filtered.

  Inclusion policy: retain only a reachable stream whose broadcaster provenance is explicit:
  direct broadcaster/public-institution feed, or an iptv-org record that passes the maintained
  official channel-and-host allowlist. Liveness confirms playback but never proves provenance.

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
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithChannelPreviews -PreviewLimit 40   # S1154 atlas smoke run
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithChannelPreviews -PublishPreviewAtlas   # full atlas build + upload
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithStreamLogos                            # S1201 logo atlas from the artwork cache
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithStreamLogos -PublishStreamLogoAtlas    # logo atlas build + upload
#>
[CmdletBinding()]
param(
    # The S1476 axes (iptvcam..xiph) are opt-in only: they are not in the default set, so a routine
    # collection run keeps its current cost and shape.
    [ValidateSet('official', 'livetv', 'genres', 'geo', 'webcam',
        'iptvcam', 'tfl', 'webradiodb', 'radioparadise', 'akc', 'lautfm', 'xiph')]
    [string[]]$Axis = @('official', 'livetv', 'genres', 'geo', 'webcam'),

    # How many laut.fm station images to pull into the artwork cache during an ingest. 0 = none.
    # A budget rather than "all": the logo sheet holds 3540 tiles total, so fetching 15k images would
    # spend hours downloading artwork that has nowhere to land.
    [int]$LautFmImageBudget = 0,

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
    # For a full catalog rebuild, use Google s2 as the only favicon source. This avoids holding up
    # the atlas on unreachable homepages while retaining a stable 32 px icon for reachable domains.
    [switch]$FaviconS2Only,
    [string]$AtlasPath = 'delivery/stream-catalog/favicon-atlas.png',
    [int]$FaviconTimeoutSec = 8,
    [int]$FaviconThrottle = 16,
    # Raw artwork cache. The fetch keeps the BEST (largest) image a station's site offers - usually an
    # apple-touch-icon or og:image of 180-1200 px - instead of only the 16/32 px tab icon, so the same
    # pass can feed both the 32 px favicon atlas and a future grid-sized logo atlas (S1201) without
    # re-crawling ~2900 sites. Cached by homepage, so an interrupted run resumes.
    [string]$LogoCacheDir = 'temp/stream-logo-src',
    [switch]$RefreshLogoCache,
    # A row with no homepage still carries a stream URL. -DomainFallback derives an artwork homepage
    # from that URL's registrable domain, so a broadcaster that published only a stream link still gets
    # an icon. Hosts belonging to a CDN, a stream-hosting panel or a bare IP are excluded - their
    # favicon is the provider's, and stamping it on dozens of unrelated channels reads as a bug.
    [switch]$DomainFallback,
    # Fill the artwork cache and stop: no atlas, no CSV write, no upload. Safe to run alongside another
    # artwork pass because the cache is one file per homepage, so the two never write the same path.
    [switch]$WarmArtworkCache,
    # Build atlases from what the cache already holds and crawl nothing. The catalog now carries
    # sources whose stations each have their own homepage on one platform (laut.fm): crawling those
    # would cost hours for artwork the sheets have no room for, while the images that matter are
    # seeded from the source's own API. Use this to rebuild after such an ingest.
    [switch]$ArtworkCacheOnly,

    # Fold every `topic` cell into the closed rubric set (S1477) and rewrite streams.csv. Its own mode:
    # it rewrites a shipped column, so it must never ride along with a discovery or artwork run.
    # Combine with -Publish to upload the rewritten catalog, or run it alone to review the diff first.
    [switch]$NormalizeTopics,

    # S1154 PHASE_06 channel-preview atlas. Captures one frame per VIDEO channel with ffmpeg, packs the
    # frames into the 240x135 / 34-column sheet the app's ChannelPreviewAtlasSlicer expects, and writes
    # the url->index sidecar. Off by default: a routine catalog refresh must never trigger a multi-hour
    # capture run. The payload is published separately from stream-catalog.zip (own release assets).
    [switch]$WithChannelPreviews,
    [switch]$PublishPreviewAtlas,
    [string]$PreviewAtlasPath = 'temp/channel-preview-atlas.webp',
    [string]$PreviewCoordsPath = 'temp/channel-preview-coords.json',
    # Captured frames are kept between runs so an interrupted capture resumes instead of restarting.
    [string]$PreviewFrameDir = 'temp/channel-preview-frames',
    [switch]$RefreshPreviewFrames,
    [int]$PreviewCaptureTimeoutSec = 20,
    [int]$PreviewThrottle = 12,
    [int]$PreviewLimit = 0,

    # S1201 stream logo atlas. Packs the artwork already cached by the favicon pass (-LogoCacheDir)
    # into the same 240x135 / 34-column sheet geometry the preview atlas uses, so a station with no
    # capturable frame - every radio channel - still gets a grid-sized picture. No network access: the
    # cache is the only source, which is why this run takes minutes rather than the favicon pass's hours.
    [switch]$WithStreamLogos,
    [switch]$PublishStreamLogoAtlas,
    [string]$LogoAtlasPath = 'delivery/stream-catalog/stream-logo-atlas.webp',
    [string]$LogoCoordsPath = 'delivery/stream-catalog/stream-logo-coords.json',
    [int]$LogoLimit = 0,

    # S1445 tile packs. A sprite sheet is not randomly addressable - a WebP decoder walks the stream
    # from the top to reach a row - so the app now reads one small image per tile out of a ZIP
    # container instead of region-decoding the sheet. The pack is cut FROM the sheet, so tile indices
    # cannot drift from the published url->index sidecar.
    [switch]$WithTilePacks,
    [switch]$PublishTilePacks,
    # Element revisions of the published artwork assets. They are parameters rather than literals
    # because a rebuild MUST land under a new name: the app pins each payload by SHA-256, so a
    # re-upload under the published name breaks integrity for every install already pinned to it
    # (S1200). Bump the one you rebuilt, leave the other alone, and paste the printed pins into
    # DeliverableDescriptorCatalog.
    [string]$TilePackRev = 'v4',
    [string]$SheetRev = 'v3',
    [string]$CoordsRev = 'v3',
    # Which artwork payload a publish touches. Republishing an unchanged payload is not harmless:
    # the zip is not byte-reproducible, so it would burn a revision and force a pin change for
    # content that did not change.
    [ValidateSet('both', 'preview', 'logo')]
    [string]$ArtworkPayload = 'both',
    [string]$PreviewTilePackPath = 'temp/channel-preview-tiles.zip',
    [string]$LogoTilePackPath = 'temp/stream-logo-tiles.zip',
    [int]$TilePackQuality = 80,

    # Explicit ffmpeg binary; empty means auto-discovery (PATH, then the usual install roots).
    [string]$FfmpegPath = '',
    # Atlas size ceiling enforced before publish. This must match the app-side import cap. Over the cap
    # -> publish CSV-only (atlas skipped), never an atlas the app would discard.
    [int]$MaxAtlasBytes = 31457280,

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

# --- S0668 favicon sprite-atlas (offline tooling) -----------------------------------------------
# Geometry is a SHARED CONTRACT with the app's atlas slicer (PHASE_01 / strategic spec). The app
# reconstructs each tile rect as col = index % 16, row = index / 16, rect = (col*32, row*32, 32, 32).
# These two constants MUST match the app side and must not be changed independently.
$script:FaviconTile = 32   # PHASE_01 contract: tile = 32x32 px
$script:FaviconCols = 16   # PHASE_01 contract: 16 columns per atlas row

# Cache path for one homepage's best artwork. Keyed by the homepage, so a re-run reuses what was
# already crawled instead of hitting the site again (a full pass over the catalog is ~2900 sites).
function Get-LogoCacheFile {
    param([string]$homepage, [string]$dir)
    $sha = [System.Security.Cryptography.SHA1]::Create()
    try { $hash = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($homepage)) } finally { $sha.Dispose() }
    return (Join-Path $dir (([System.BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant() + '.img'))
}

# Public suffixes made of two labels. Without them 'live-evg10.tv360.bitel.com.pe' would reduce to
# 'com.pe' and every Peruvian channel would end up sharing one icon.
$script:MultiLabelSuffixes = @(
    'co.uk', 'org.uk', 'ac.uk', 'com.au', 'net.au', 'com.br', 'com.tr', 'co.jp', 'co.kr', 'com.ua',
    'net.ua', 'org.ua', 'co.za', 'com.mx', 'com.ar', 'co.in', 'net.in', 'com.pl', 'com.sg', 'co.nz',
    'com.hk', 'com.cn', 'co.il', 'com.co', 'com.pe', 'com.ve', 'com.ec', 'com.py', 'com.uy', 'com.bo',
    'com.pk', 'com.ng', 'com.gh', 'com.eg', 'com.sa', 'co.th', 'or.th', 'com.my', 'com.ph', 'com.vn',
    'com.tw', 'com.hr', 'com.mt', 'com.cy', 'com.do', 'com.gt', 'com.sv', 'com.ni', 'com.pa', 'com.bd',
    'com.np', 'com.lb', 'com.jo', 'com.kw', 'com.bh', 'com.om', 'com.qa'
)

# Domains whose favicon belongs to the delivery provider rather than to the station: CDNs, stream
# hosting panels, OTT aggregators, video platforms. Deriving an icon from one of these stamps the
# provider's mark on every unrelated channel it carries - visibly wrong, and worse than the country
# flag the app already falls back to. Consulted ONLY for a DERIVED homepage; a station that genuinely
# declares such a homepage in the catalog keeps it.
$script:ArtDomainBlocklist = @(
    'cloudfront.net', 'akamaized.net', 'akamaihd.net', 'edgenextcdn.net', 'amazonaws.com', 'azureedge.net',
    'fastly.net', 'cachefly.net', 'cdn77.org', 'cdnvideo.ru', '5centscdn.com', 'pluscdn.pl', '1cdn.tv',
    'zeno.fm', 'bozztv.com', 'smartbit.co', 'turbohost.eu', 'srvif.com', 'malimarcdn.com',
    'hostingcaaguazu.com', 'alsolnet.com', 'bitgravity.com', 'streamlock.net', 'streamguys1.com',
    'streamguys.com', 'wowza.com', 'wowzacloud.com', 'castr.com', 'castr.io', 'fluidstream.eu',
    'shoutcast.com', 'icecast.org', 'radiojar.com', 'radioca.st', 'mediacp.tv', 'myradiostream.com',
    'stream-hosting.net', 'listenlive.co', 'securenetsystems.net', 'streamlicensing.com',
    'streamtheworld.com', 'tritondigital.com', 'amagi.tv', 'otteravision.com', 'wns.live', 'viacast.tv',
    'flumeotv.io', 'vaunt.cloud', 'mux.com', 'jwplayer.com', 'brightcove.com', 'vimeo.com',
    'youtube.com', 'dailymotion.com', 'ovhcloud.com', 'digitalocean.com', 'contabo.net', 'hetzner.de'
)

# Registrable domain of a host ('mumt04.tangotv.in' -> 'tangotv.in'), or $null when the host is a bare
# IP address (nothing to crawl) or otherwise unusable.
function Get-RegistrableDomain {
    param([string]$hostName)
    if ([string]::IsNullOrWhiteSpace($hostName)) { return $null }
    $normalized = $hostName.Trim().ToLowerInvariant()
    if ($normalized -match '^\d{1,3}(\.\d{1,3}){3}$') { return $null }
    if ($normalized -notmatch '\.') { return $null }
    $labels = $normalized.Split('.')
    if ($labels.Count -ge 3 -and ($script:MultiLabelSuffixes -contains ($labels[-2] + '.' + $labels[-1]))) {
        return ($labels[-3..-1] -join '.')
    }
    return ($labels[-2..-1] -join '.')
}

# The homepage an artwork pass should crawl for one catalog row: the catalog's own homepage when it has
# one, and - only under -DomainFallback - a 'https://<registrable-domain>/' synthesised from the stream
# URL when it does not. The derived value is deliberately never written back to the CSV: it is a guess
# about where a logo might live, not a fact about the station, and the catalog ships as a factual source.
function Get-ArtHomepage {
    param([Parameter(Mandatory = $true)][object]$Row)
    $declared = [string]$Row.homepage
    if (-not [string]::IsNullOrWhiteSpace($declared)) { return $declared }
    if (-not $DomainFallback) { return '' }
    $url = [string]$Row.url
    if ([string]::IsNullOrWhiteSpace($url)) { return '' }
    $uri = $null
    try { $uri = [uri]$url } catch { return '' }
    if (-not $uri.IsAbsoluteUri) { return '' }
    $domain = Get-RegistrableDomain -hostName $uri.Host
    if (-not $domain) { return '' }
    if ($script:ArtDomainBlocklist -contains $domain) { return '' }
    return ("https://{0}/" -f $domain)
}

# Fetch the BEST artwork a station's site offers, as raw image bytes, or $null when nothing usable is
# found. "Best" = largest decoded pixel area, because the same bytes feed two consumers: the 32 px
# favicon atlas (downscaled) and the grid-sized logo atlas (S1201). Downscaling a 180 px apple-touch
# icon gives a far cleaner 32 px tile than a 16 px .ico, so preferring the big source helps both.
#
# Candidate order (first the ones that are usually large): apple-touch-icon and og:image declared in
# the homepage HTML, then any <link rel="icon">, then the conventional /favicon.ico, then - if
# -FaviconS2Fallback - the Google s2 endpoint at sz=128. Per-host failures are normal and swallowed.
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
            # -TimeoutSec alone does NOT bound a response that trickles bytes forever: a single such
            # host hung a whole batch for 50 minutes. OperationTimeoutSeconds caps the read, and
            # ConnectionTimeoutSeconds caps the connect, so every fetch has a hard ceiling (PS 7.4+).
            $resp = Invoke-WebRequest -Uri $u -UseBasicParsing `
                -ConnectionTimeoutSeconds $FaviconTimeoutSec -OperationTimeoutSeconds ($FaviconTimeoutSec * 2) `
                -Headers @{ 'User-Agent' = $ua } -MaximumRedirection 6 -ErrorAction Stop
            $bytes = $resp.Content
            if ($bytes -is [string]) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($bytes) }
            if ($bytes -and $bytes.Length -gt 0) { return [byte[]]$bytes }
        }
        catch { }
        return $null
    }

    # Decoded pixel area of an image blob, or 0 when it is not an image at all (an HTML error page
    # served with 200 is a common trap, and it must never win the "largest" comparison).
    $areaOf = {
        param([byte[]]$bytes)
        if (-not $bytes -or $bytes.Length -lt 64) { return 0 }
        $ms = $null
        $img = $null
        try {
            $ms = [System.IO.MemoryStream]::new($bytes)
            $img = [System.Drawing.Image]::FromStream($ms)
            return [int]$img.Width * [int]$img.Height
        }
        catch { return 0 }
        finally {
            if ($img) { $img.Dispose() }
            if ($ms) { $ms.Dispose() }
        }
    }

    if ($FaviconS2Only) {
        return (& $tryGet ("https://www.google.com/s2/favicons?domain={0}&sz=128" -f $host2))
    }

    # Parse the homepage once and collect every declared artwork URL, biggest-first by intent:
    # apple-touch-icon (typically 180 px), og:image (typically 1200x630), then plain icon links.
    $candidates = [System.Collections.Generic.List[string]]::new()
    $absolutize = {
        param([string]$href)
        if ([string]::IsNullOrWhiteSpace($href)) { return $null }
        $h = $href.Trim()
        if ($h -match '^[a-zA-Z][a-zA-Z0-9+.-]*://') { return $h }
        try { return [Uri]::new($uri, $h).AbsoluteUri } catch { return $null }
    }
    try {
        $page = Invoke-WebRequest -Uri $homepage -UseBasicParsing `
            -ConnectionTimeoutSeconds $FaviconTimeoutSec -OperationTimeoutSeconds ($FaviconTimeoutSec * 2) `
            -Headers @{ 'User-Agent' = $ua } -MaximumRedirection 6 -ErrorAction Stop
        $html = [string]$page.Content
        if (-not [string]::IsNullOrWhiteSpace($html)) {
            $appleLinks = [System.Collections.Generic.List[string]]::new()
            $iconLinks = [System.Collections.Generic.List[string]]::new()
            foreach ($m in [regex]::Matches($html, '<link\b[^>]*>', 'IgnoreCase')) {
                $tag = $m.Value
                if ($tag -notmatch 'rel\s*=\s*["'']?\s*(shortcut\s+icon|icon|apple-touch-icon[a-z-]*)\b') { continue }
                $hrefMatch = [regex]::Match($tag, 'href\s*=\s*["'']([^"'']+)["'']', 'IgnoreCase')
                if (-not $hrefMatch.Success) { continue }
                $abs = & $absolutize $hrefMatch.Groups[1].Value
                if (-not $abs) { continue }
                if ($tag -match 'apple-touch-icon') { $appleLinks.Add($abs) } else { $iconLinks.Add($abs) }
            }
            foreach ($u in $appleLinks) { $candidates.Add($u) }
            $og = [regex]::Match($html, '<meta\b[^>]*property\s*=\s*["'']og:image["''][^>]*>', 'IgnoreCase')
            if ($og.Success) {
                $ogContent = [regex]::Match($og.Value, 'content\s*=\s*["'']([^"'']+)["'']', 'IgnoreCase')
                if ($ogContent.Success) {
                    $abs = & $absolutize $ogContent.Groups[1].Value
                    if ($abs) { $candidates.Add($abs) }
                }
            }
            foreach ($u in $iconLinks) { $candidates.Add($u) }
        }
    }
    catch { }
    $candidates.Add(("{0}://{1}/favicon.ico" -f $scheme, $host2))

    # Fetch at most a handful of candidates and keep the largest that actually decodes. The cap bounds
    # the crawl: some pages declare a dozen icon links, and each miss costs a full timeout.
    $best = $null
    $bestArea = 0
    $tried = 0
    foreach ($candidate in $candidates) {
        if ($tried -ge 4) { break }
        $tried++
        $bytes = & $tryGet $candidate
        if (-not $bytes) { continue }
        $area = & $areaOf $bytes
        if ($area -gt $bestArea) {
            $best = $bytes
            $bestArea = $area
        }
        # 180x180 (apple-touch-icon) is already plenty for a grid tile - stop crawling this host.
        if ($bestArea -ge 32400) { break }
    }
    if ($best) { return $best }

    if ($FaviconS2Fallback) {
        $s2 = & $tryGet ("https://www.google.com/s2/favicons?domain={0}&sz=128" -f $host2)
        if ($s2) { return $s2 }
    }

    return $null
}

# Fetch the best artwork for each given homepage into the on-disk cache and return a hashtable of
# homepage -> image bytes for the ones that yielded an image. Both atlas builders and the standalone
# cache-warm mode share this, so a crawl performed by any of them serves all three.
function Invoke-ArtworkCacheFetch {
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Homepages)

    Write-Host ("Favicons: fetching for {0} unique homepage(s) (throttle {1}, timeout {2}s) .." -f `
            $Homepages.Count, $FaviconThrottle, $FaviconTimeoutSec) -ForegroundColor Yellow

    # Parallel fetch -> host -> bytes map. GDI+ packing stays single-threaded in the callers.
    # Two things this loop must get right, both learned the hard way on a 2917-site pass:
    #  - crawled artwork is CACHED on disk, so a re-run (or a second atlas built from the same bytes)
    #    costs seconds instead of another hour;
    #  - progress is reported per batch, because -Parallel emits nothing until the whole set returns
    #    and a silent hour is indistinguishable from a hang.
    $faviconFn = ${function:Get-FaviconBytes}.ToString()
    $cacheFn = ${function:Get-LogoCacheFile}.ToString()
    if (-not (Test-Path $LogoCacheDir)) { New-Item -ItemType Directory -Path $LogoCacheDir -Force | Out-Null }
    $cacheDir = (Resolve-Path $LogoCacheDir).Path
    $fetched = @{}
    $fromCache = 0

    # An artwork we already hold is read here, on the main thread, and never handed to the crawl at all.
    # Two reasons, and the second one is a correctness bug rather than a saving:
    #  - a host with an image must never be walked again; crawling is the expensive half of this script;
    #  - the batch below has a 120 s wall-clock ceiling and abandons ALL of its results when one host
    #    wedges. Cached hosts riding in that batch used to vanish from the map with it, so a station
    #    whose icon was on disk lost its tile because an unrelated neighbour hung.
    # -RefreshLogoCache is the deliberate opt-out: it re-crawls everything, cached or not.
    $pending = [System.Collections.Generic.List[string]]::new()
    foreach ($hp in $Homepages) {
        $cacheFile = Get-LogoCacheFile -homepage $hp -dir $cacheDir
        if (-not $RefreshLogoCache -and (Test-Path $cacheFile)) {
            try {
                $bytes = [System.IO.File]::ReadAllBytes($cacheFile)
                if ($bytes -and $bytes.Length -gt 0) {
                    $fetched[$hp] = $bytes
                    $fromCache++
                    continue
                }
            }
            catch { }
        }
        $pending.Add($hp)
    }
    if ($ArtworkCacheOnly -and $pending.Count -gt 0) {
        Write-Host ("Favicons: {0} cached; {1} uncached homepage(s) skipped (-ArtworkCacheOnly)." -f `
                $fromCache, $pending.Count) -ForegroundColor DarkYellow
        return $fetched
    }
    Write-Host ("Favicons: {0} homepage(s) already cached (kept, not re-crawled), {1} to crawl." -f `
            $fromCache, $pending.Count) -ForegroundColor DarkGray

    if ($pending.Count -gt 0) {
        $started = Get-Date
        # A batch is a barrier: the slowest host in it holds up the rest, so keep batches small enough
        # that one pathological site costs a short stall, not a silent half-hour.
        $batchSize = 48
        $done = 0
        for ($offset = 0; $offset -lt $pending.Count; $offset += $batchSize) {
            $batch = @($pending[$offset..([Math]::Min($offset + $batchSize, $pending.Count) - 1)])
            # Hard wall-clock ceiling per batch. Even with per-request timeouts a site can wedge a
            # runspace (a stalled TLS handshake, a GDI+ decode that never returns) and hold the whole
            # batch: two separate 50-minute stalls came from exactly that. Stragglers are abandoned
            # here; their hosts simply stay uncached and are retried on a later run.
            # -TimeoutSeconds surfaces as a TERMINATING "pipeline has been stopped" error, and this
            # script runs under $ErrorActionPreference = 'Stop', so an abandoned batch would kill the
            # whole crawl. Catch it: the batch's results are lost, its hosts stay uncached, the run
            # continues with the next batch.
            $results = @()
            try {
                $results = $batch | ForEach-Object -ThrottleLimit $FaviconThrottle -TimeoutSeconds 120 -Parallel {
                $hp = $_
                $ua = $using:ua
                $FaviconTimeoutSec = $using:FaviconTimeoutSec
                $FaviconS2Fallback = $using:FaviconS2Fallback
                $FaviconS2Only = $using:FaviconS2Only
                $cacheDir = $using:cacheDir
                $refresh = $using:RefreshLogoCache
                ${function:Get-FaviconBytes} = $using:faviconFn
                ${function:Get-LogoCacheFile} = $using:cacheFn
                # The fetcher compares decoded image sizes, so GDI+ must be loaded in THIS runspace
                # too - -Parallel does not inherit the caller's Add-Type.
                Add-Type -AssemblyName System.Drawing
                $cacheFile = Get-LogoCacheFile -homepage $hp -dir $cacheDir
                # A host that yielded nothing is remembered too. Without this marker every re-run
                # re-crawls the same dead sites, and they are exactly the ones that hang.
                $missFile = $cacheFile + '.miss'
                # Only uncached hosts reach this runspace (the caller reads existing artwork itself),
                # so the sole job here is to crawl, cache the result, and mark a barren host.
                $bytes = $null
                if ($refresh -or -not (Test-Path $missFile)) {
                    try { $bytes = Get-FaviconBytes -homepage $hp } catch { $bytes = $null }
                    if ($bytes) {
                        try {
                            [System.IO.File]::WriteAllBytes($cacheFile, $bytes)
                            # A previous barren verdict is void once the host finally yields an image.
                            if (Test-Path $missFile) { Remove-Item $missFile -Force -ErrorAction SilentlyContinue }
                        }
                        catch { }
                    }
                    else {
                        try { [System.IO.File]::WriteAllText($missFile, '') } catch { }
                    }
                }
                    [pscustomobject]@{ Homepage = $hp; Bytes = $bytes }
                }
            }
            catch {
                Write-Host ("  batch abandoned after 120s (hosts {0}-{1}) - continuing" -f `
                        ($offset + 1), ($offset + $batch.Count)) -ForegroundColor DarkYellow
            }
            foreach ($r in $results) {
                if ($r.Bytes) { $fetched[$r.Homepage] = [byte[]]$r.Bytes }
            }
            $done += $batch.Count
            $elapsed = (Get-Date) - $started
            $rate = if ($elapsed.TotalSeconds -gt 0) { $done / $elapsed.TotalSeconds } else { 0 }
            $etaSec = if ($rate -gt 0) { ($pending.Count - $done) / $rate } else { 0 }
            Write-Host ("  crawled {0}/{1} new host(s), {2} image(s) in hand, elapsed {3}, eta {4}" -f `
                    $done, $pending.Count, $fetched.Count, (Format-DurationShort $elapsed), `
                (Format-DurationShort ([TimeSpan]::FromSeconds([Math]::Round($etaSec))))) -ForegroundColor DarkGray
        }
    }
    Write-Host ("Favicons: {0}/{1} homepage(s) hold an image ({2} kept from cache, {3} newly crawled)." -f `
            $fetched.Count, $Homepages.Count, $fromCache, ($fetched.Count - $fromCache)) -ForegroundColor DarkGray
    return $fetched
}

# Pack the decoded tiles into one grid PNG atlas (16 cols x ceil(n/16) rows, each cell 32x32), save it
# to $AtlasPath, and return a hashtable mapping each packed row's url -> zero-based tile ordinal. Rows
# whose favicon could not be fetched/decoded are absent from the map (their favicon_index stays blank).
# System.Drawing (GDI+) handles decode (.ico/.png/.gif/.jpg via Image.FromStream), scaling, and PNG save.
function Build-FaviconAtlas {
    param([Parameter(Mandatory = $true)][object[]]$Rows, [Parameter(Mandatory = $true)][string]$AtlasPath)

    Add-Type -AssemblyName System.Drawing

    $tile = $script:FaviconTile   # 32 - PHASE_01 contract
    $cols = $script:FaviconCols   # 16 - PHASE_01 contract

    # Distinct non-blank homepages to fetch (dedup so a repeated homepage is fetched only once).
    $homepages = @($Rows | ForEach-Object { Get-ArtHomepage -Row $_ } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
    $fetched = Invoke-ArtworkCacheFetch -Homepages $homepages

    # Decode in row order; rows that decode successfully get the next sequential ordinal.
    $packable = [System.Collections.Generic.List[object]]::new()
    foreach ($row in $Rows) {
        $hp = Get-ArtHomepage -Row $row
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

# --- S1154 channel-preview atlas (offline packer, PHASE_06) -------------------------------------

# Geometry contract paired with the app-side ChannelPreviewAtlasSlicer (TILE_W / TILE_H / COLS). The
# app resolves a tile as col = index % COLS, row = index / COLS, so the packer must lay tiles out
# identically or every preview drifts onto a neighbouring channel.
$script:PreviewTileW = 240
$script:PreviewTileH = 135
$script:PreviewCols = 34
# ADR-2 budget: one 8192x8192 sheet, so floor(8192 / 135) = 60 rows x 34 cols = 2040 tiles maximum.
$script:PreviewMaxRows = 60

# Resolve the ffmpeg binary used for frame capture and the PNG -> WebP encode. PATH first, then the
# usual install roots; -FfmpegPath overrides everything. Cached for the run.
function Get-FfmpegExe {
    if ($script:FfmpegExe) { return $script:FfmpegExe }
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($FfmpegPath) { $candidates.Add($FfmpegPath) }
    $onPath = (Get-Command ffmpeg -ErrorAction SilentlyContinue).Source
    if ($onPath) { $candidates.Add($onPath) }
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }) {
        $candidates.Add((Join-Path $root 'ffmpeg\bin\ffmpeg.exe'))
        # Fallback for a dev box without a standalone ffmpeg: Virtual Desktop Streamer ships a full
        # n7.x build (https/hls/libwebp all enabled), which is exactly what the capture needs.
        $candidates.Add((Join-Path $root 'Virtual Desktop Streamer\ffmpeg.exe'))
    }
    $candidates.Add('C:\ffmpeg\bin\ffmpeg.exe')
    if ($env:ProgramData) { $candidates.Add((Join-Path $env:ProgramData 'chocolatey\bin\ffmpeg.exe')) }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { $script:FfmpegExe = (Resolve-Path $c).Path; return $script:FfmpegExe }
    }
    throw 'ffmpeg not found on PATH or in the standard install roots - pass -FfmpegPath <ffmpeg.exe> (needed to capture channel frames and encode the WebP sheet).'
}

# gh is often installed but absent from PATH on the dev machine (e.g. C:\Program Files\GitHub CLI),
# so resolve it the same way adb is auto-discovered before any release-asset upload.
function Get-GhExe {
    $ghExe = (Get-Command gh -ErrorAction SilentlyContinue).Source
    if (-not $ghExe) {
        foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }) {
            $cand = Join-Path $root 'GitHub CLI\gh.exe'
            if (Test-Path $cand) { $ghExe = $cand; break }
        }
    }
    if (-not $ghExe) { throw 'gh CLI not found on PATH or standard install locations - cannot upload the release asset (install GitHub CLI or upload the artifact manually).' }
    return $ghExe
}

# Stable per-URL frame path, so an interrupted capture resumes: a URL that already has a frame on disk
# is skipped on the next run unless -RefreshPreviewFrames is set.
function Get-PreviewFrameFile {
    param([string]$Url, [string]$Dir)
    $sha = [System.Security.Cryptography.SHA1]::Create()
    try { $hash = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Url)) } finally { $sha.Dispose() }
    return (Join-Path $Dir (([System.BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant() + '.png'))
}

# Capture one 240x135 frame per row with ffmpeg (throttled, hard per-channel timeout) and return the
# rows that produced a usable frame, in input order. Failures are normal - a dead/geo-locked channel
# simply gets no preview tile.
function Invoke-ChannelPreviewCapture {
    param([Parameter(Mandatory = $true)][object[]]$Rows)

    $ffmpeg = Get-FfmpegExe
    if (-not (Test-Path $PreviewFrameDir)) { New-Item -ItemType Directory -Path $PreviewFrameDir -Force | Out-Null }
    # Absolute: a -Parallel runspace does NOT inherit the caller's working directory, so a relative
    # output path would make ffmpeg write somewhere else entirely (and every capture look failed).
    $frameDir = (Resolve-Path $PreviewFrameDir).Path

    $targets = [System.Collections.Generic.List[object]]::new()
    $cached = 0
    foreach ($row in $Rows) {
        $file = Get-PreviewFrameFile -Url ([string]$row.url) -Dir $frameDir
        if ($RefreshPreviewFrames -and (Test-Path $file)) { Remove-Item $file -Force }
        if (Test-Path $file) { $cached++ }
        $targets.Add([pscustomobject]@{ Url = [string]$row.url; File = $file; Pending = -not (Test-Path $file) })
    }
    $pending = @($targets | Where-Object { $_.Pending })
    Write-Host ("Channel previews: ffmpeg {0}" -f $ffmpeg) -ForegroundColor DarkGray
    Write-Host ("Channel previews: {0} channel(s), {1} already captured, {2} to capture (throttle {3}, timeout {4}s) .." -f `
            $targets.Count, $cached, $pending.Count, $PreviewThrottle, $PreviewCaptureTimeoutSec) -ForegroundColor Yellow

    $tileW = $script:PreviewTileW
    $tileH = $script:PreviewTileH
    $started = Get-Date
    $done = 0
    # Batched so a multi-hour run reports progress; -Parallel itself emits nothing until it returns.
    $batchSize = 96
    for ($offset = 0; $offset -lt $pending.Count; $offset += $batchSize) {
        $batch = @($pending[$offset..([Math]::Min($offset + $batchSize, $pending.Count) - 1)])
        $batch | ForEach-Object -ThrottleLimit $PreviewThrottle -Parallel {
            $item = $_
            $exe = $using:ffmpeg
            $timeoutSec = $using:PreviewCaptureTimeoutSec
            # Start-Process joins -ArgumentList WITHOUT quoting, so a UA containing spaces would split
            # into extra arguments and every capture would fail. Keep this token space-free.
            $agent = 'FastMediaSorter-catalog/1.0'
            $vf = "scale={0}:{1}:force_original_aspect_ratio=increase,crop={0}:{1}" -f $using:tileW, $using:tileH
            $errFile = $item.File + '.err'
            $argList = @(
                '-hide_banner', '-loglevel', 'error', '-y',
                '-user_agent', $agent,
                '-rw_timeout', ([string]($timeoutSec * 1000000)),
                '-analyzeduration', '5000000', '-probesize', '5000000',
                '-i', $item.Url,
                # -update 1 is required for a single-file image2 output: without it ffmpeg treats the
                # path as a sequence pattern and refuses to write.
                '-frames:v', '1', '-update', '1', '-vf', $vf, '-f', 'image2', $item.File
            )
            try {
                $proc = Start-Process -FilePath $exe -ArgumentList $argList -NoNewWindow -PassThru -RedirectStandardError $errFile
                if (-not $proc.WaitForExit($timeoutSec * 1000)) {
                    # A live stream never ends on its own: kill the whole tree when the frame did not land.
                    try { $proc.Kill($true) } catch { }
                    $proc.WaitForExit(3000) | Out-Null
                }
            }
            catch { }
            if ((Test-Path $item.File) -and (Get-Item $item.File).Length -eq 0) {
                Remove-Item $item.File -Force -ErrorAction SilentlyContinue
            }
            # Keep ffmpeg's stderr only for a channel that produced no frame - that is the only case
            # worth diagnosing later; a successful capture leaves no litter behind.
            if ((Test-Path $errFile) -and (Test-Path $item.File)) {
                Remove-Item $errFile -Force -ErrorAction SilentlyContinue
            }
        }
        $done += $batch.Count
        $elapsed = (Get-Date) - $started
        $rate = if ($elapsed.TotalSeconds -gt 0) { $done / $elapsed.TotalSeconds } else { 0 }
        $etaSec = if ($rate -gt 0) { ($pending.Count - $done) / $rate } else { 0 }
        Write-Host ("  captured {0}/{1} pending, elapsed {2}, eta {3}" -f `
                $done, $pending.Count, (Format-DurationShort $elapsed), `
            (Format-DurationShort ([TimeSpan]::FromSeconds([Math]::Round($etaSec))))) -ForegroundColor DarkGray
    }

    $withFrame = @($targets | Where-Object { (Test-Path $_.File) -and (Get-Item $_.File).Length -gt 0 })
    Write-Host ("Channel previews: {0}/{1} channel(s) produced a frame." -f $withFrame.Count, $targets.Count) -ForegroundColor DarkGray
    return $withFrame
}

# Pack the captured frames into the fixed-grid sheet, encode it to WebP, and write the url->index
# sidecar. Returns the number of packed tiles. Tiles beyond the 2040-slot sheet capacity are dropped
# (reported, never silently truncated).
function Build-ChannelPreviewAtlas {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [string]$SheetPath = $PreviewAtlasPath,
        [string]$CoordsFile = $PreviewCoordsPath
    )

    Add-Type -AssemblyName System.Drawing

    $captured = Invoke-ChannelPreviewCapture -Rows $Rows
    if ($captured.Count -eq 0) { throw 'No channel frames captured - atlas not written.' }

    $cols = $script:PreviewCols
    $tileW = $script:PreviewTileW
    $tileH = $script:PreviewTileH
    $maxSlots = $cols * $script:PreviewMaxRows
    $packable = $captured
    if ($packable.Count -gt $maxSlots) {
        Write-Warning ("Channel previews: {0} frames exceed the {1}-slot sheet capacity; dropping the last {2}." -f `
                $packable.Count, $maxSlots, ($packable.Count - $maxSlots))
        $packable = @($packable[0..($maxSlots - 1)])
    }

    $rowsNeeded = [int][Math]::Ceiling($packable.Count / [double]$cols)
    $sheetW = $cols * $tileW
    $sheetH = $rowsNeeded * $tileH
    $pngPath = [System.IO.Path]::ChangeExtension($SheetPath, '.png')
    $parent = Split-Path -Parent $SheetPath
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    Write-Host ("Channel previews: packing {0} tile(s) into {1}x{2} .." -f $packable.Count, $sheetW, $sheetH) -ForegroundColor Yellow
    $map = [ordered]@{}
    $sheet = [System.Drawing.Bitmap]::new($sheetW, $sheetH)
    $g = [System.Drawing.Graphics]::FromImage($sheet)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.Clear([System.Drawing.Color]::Black)
        for ($i = 0; $i -lt $packable.Count; $i++) {
            $col = $i % $cols
            $rr = [int][Math]::Floor($i / $cols)
            $dest = [System.Drawing.Rectangle]::new($col * $tileW, $rr * $tileH, $tileW, $tileH)
            $tile = $null
            try {
                $tile = [System.Drawing.Image]::FromFile($packable[$i].File)
                $g.DrawImage($tile, $dest)
                $map[$packable[$i].Url] = $i
            }
            catch {
                # A truncated frame file must not abort the whole sheet: leave the slot black and skip
                # the url, so the app falls back to its own capture for that channel.
                Write-Warning ("Channel previews: unreadable frame skipped ({0})" -f $packable[$i].Url)
            }
            finally { if ($tile) { $tile.Dispose() } }
        }
    }
    finally {
        $g.Dispose()
        $sheet.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $sheet.Dispose()
    }

    $ffmpeg = Get-FfmpegExe
    Write-Host 'Channel previews: encoding WebP sheet ..' -ForegroundColor Yellow
    & $ffmpeg -hide_banner -loglevel error -y -i $pngPath -c:v libwebp -preset picture -quality 80 -compression_level 6 $SheetPath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $SheetPath)) { throw "ffmpeg WebP encode failed (exit $LASTEXITCODE)" }

    Backup-IfExists -Path $CoordsFile | Out-Null
    # -Compress keeps the sidecar small; the app parses it as a flat url -> index JSON object.
    ($map | ConvertTo-Json -Compress -Depth 2) | Set-Content -Path $CoordsFile -Encoding utf8NoBOM

    $sheetBytes = (Get-Item $SheetPath).Length
    $coordsBytes = (Get-Item $CoordsFile).Length
    Write-Host ''
    Write-Host ("Channel-preview atlas: {0} tile(s), sheet {1}x{2}" -f $map.Count, $sheetW, $sheetH) -ForegroundColor Green
    foreach ($f in @($SheetPath, $CoordsFile)) {
        $h = (Get-FileHash -Algorithm SHA256 -Path $f).Hash.ToLowerInvariant()
        Write-Host ("  {0}" -f (Resolve-Path $f).Path) -ForegroundColor Green
        Write-Host ("    sha256 = {0}" -f $h) -ForegroundColor DarkGray
        Write-Host ("    bytes  = {0:N0}" -f (Get-Item $f).Length) -ForegroundColor DarkGray
    }
    Write-Host ("  (sheet {0:N1} MB, sidecar {1:N1} KB) - paste both pins into DeliverableDescriptorCatalog.channelPreviewAtlas()" -f `
        ($sheetBytes / 1MB), ($coordsBytes / 1KB)) -ForegroundColor DarkGray
    return $map.Count
}

# Upload the atlas as its OWN release assets (never inside stream-catalog.zip): the payload has an
# independent lifecycle and the app downloads the two files by their versioned asset names.
function Invoke-PublishChannelPreviewAtlas {
    param([string]$SheetPath = $PreviewAtlasPath, [string]$CoordsFile = $PreviewCoordsPath, [string]$Tag = $PublishTag)
    foreach ($f in @($SheetPath, $CoordsFile)) {
        if (-not (Test-Path $f)) { throw "Channel-preview atlas artifact missing for publish: $f (run with -WithChannelPreviews first)" }
    }
    $ghExe = Get-GhExe
    # The remote asset name carries the element revision (-v1), matching DeliverableDescriptorCatalog's
    # withRev(); the on-device file name stays unversioned.
    $stageDir = 'temp/channel-preview-publish'
    if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
    New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
    $sheetAsset = Join-Path $stageDir ("channel-preview-atlas-{0}.webp" -f $SheetRev)
    $coordsAsset = Join-Path $stageDir ("channel-preview-coords-{0}.json" -f $CoordsRev)
    Copy-Item $SheetPath $sheetAsset -Force
    Copy-Item $CoordsFile $coordsAsset -Force

    Write-Host ("Publishing channel-preview atlas to release {0} (--clobber) .." -f $Tag) -ForegroundColor Cyan
    & $ghExe release upload $Tag $sheetAsset $coordsAsset --clobber
    if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    # Report the names actually uploaded. A hardcoded revision here once printed 'v1' for a v2 upload,
    # which is the kind of log that sends a later diagnosis down the wrong path.
    Write-Host ("Published {0} ({1:N1} MB) + {2} ({3:N1} KB) -> {4}." -f `
        (Split-Path -Leaf $sheetAsset), ((Get-Item $sheetAsset).Length / 1MB), `
        (Split-Path -Leaf $coordsAsset), ((Get-Item $coordsAsset).Length / 1KB), $Tag) -ForegroundColor Green
}

# Entry point for -WithChannelPreviews: VIDEO rows of the shipped catalog, in catalog order.
function Invoke-BuildChannelPreviewAtlasRun {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    $videoRows = @(Import-Csv -Path $ExistingCsv | Where-Object { [string]$_.media_kind -eq 'VIDEO' })
    if ($videoRows.Count -eq 0) { throw "No VIDEO rows in $ExistingCsv - nothing to preview." }
    if ($PreviewLimit -gt 0 -and $videoRows.Count -gt $PreviewLimit) {
        Write-Host ("Channel previews: limited to the first {0} of {1} VIDEO rows (-PreviewLimit)" -f $PreviewLimit, $videoRows.Count) -ForegroundColor DarkYellow
        $videoRows = @($videoRows[0..($PreviewLimit - 1)])
    }
    Build-ChannelPreviewAtlas -Rows $videoRows | Out-Null
}

# --- S1201 stream logo atlas (offline packer) ---------------------------------------------------

# Square, unlike the channel-preview sheet's 16:9 frames. A logo is fitted whole inside its tile and
# is almost always square, so on a 240x135 tile it still reached only 135 px tall while 44% of the
# width stayed transparent padding - measured at 8160x8100 and 19.4 MB. A square tile gives the logo
# the identical height for half the pixels.
#
# 136 rather than 135 because the encode below is lossy, and lossy WebP is always 4:2:0: with an odd
# tile size every second tile boundary would fall mid-chroma-block and bleed colour into its neighbour's
# edge - a visible seam on the full-bleed logos. Even dimensions keep every tile chroma-independent.
#
# The app-side half of this contract is StreamLogoAtlasSlicer's companion object - move one side alone
# and every rect drifts.
$script:LogoTileW = 136
$script:LogoTileH = 136
$script:LogoCols = 59
$script:LogoMaxRows = 60

# Smallest source that earns a grid tile, measured on the larger side. Below this the cached artwork
# is just a tab icon: upscaling it is the very thing this ticket exists to stop, and drawn at native
# size it would float lost in a 240x135 tile. Such stations fall through to the favicon tier, which
# insets a 32 px icon deliberately. The threshold also keeps the sheet inside its 2040 slots - the
# cache holds ~2525 originals, of which ~1838 clear 96 px.
$script:LogoMinSourcePx = 96

# Decoded size of an image file, or $null when it is not a readable image.
function Get-ImageSize {
    param([string]$path)
    $ms = $null
    $img = $null
    try {
        $ms = [System.IO.MemoryStream]::new([System.IO.File]::ReadAllBytes($path))
        $img = [System.Drawing.Image]::FromStream($ms)
        return [pscustomobject]@{ Width = [int]$img.Width; Height = [int]$img.Height }
    }
    catch { return $null }
    finally {
        if ($img) { $img.Dispose() }
        if ($ms) { $ms.Dispose() }
    }
}

# The distinct tiles worth packing, plus every url that maps onto each. A homepage the crawler tried
# and found empty leaves a '<hash>.img.miss' marker and no '.img', so an existence test skips it;
# survivors are measured against [LogoMinSourcePx].
#
# Tiles are keyed by cache FILE, not by url: several stations routinely share one site (a network's
# genre streams all point at the same homepage), and giving each its own copy of the identical logo
# burned ~300 of the 2040 slots and pushed the sheet over capacity. One tile, many urls pointing at it.
function Select-LogoRows {
    param([Parameter(Mandatory = $true)][object[]]$Rows)
    Add-Type -AssemblyName System.Drawing
    $tiles = [System.Collections.Generic.List[object]]::new()
    $byFile = @{}
    $seenUrl = [System.Collections.Generic.HashSet[string]]::new()
    $tooSmall = 0
    $unreadable = 0
    $shared = 0
    foreach ($row in $Rows) {
        $homepage = Get-ArtHomepage -Row $row
        $url = [string]$row.url
        if ([string]::IsNullOrWhiteSpace($homepage) -or [string]::IsNullOrWhiteSpace($url)) { continue }
        if (-not $seenUrl.Add($url)) { continue }
        $cache = Get-LogoCacheFile -homepage $homepage -dir $LogoCacheDir
        if (-not (Test-Path $cache)) { continue }
        if ($byFile.ContainsKey($cache)) {
            $byFile[$cache].Urls.Add($url)
            $shared++
            continue
        }
        $size = Get-ImageSize -path $cache
        if (-not $size) { $unreadable++; continue }
        if ([Math]::Max($size.Width, $size.Height) -lt $script:LogoMinSourcePx) { $tooSmall++; continue }
        $tile = [pscustomobject]@{ File = $cache; Urls = [System.Collections.Generic.List[string]]::new() }
        $tile.Urls.Add($url)
        $byFile[$cache] = $tile
        $tiles.Add($tile)
    }
    Write-Host ("Stream logos: {0} distinct tile(s) for {1} extra shared url(s); {2} below {3} px, {4} unreadable" -f `
            $tiles.Count, $shared, $tooSmall, $script:LogoMinSourcePx, $unreadable) -ForegroundColor DarkGray
    return $tiles
}

# Destination rect for one logo inside its tile: scaled to fit whole and centred. Fitting rather than
# filling is what keeps a station's mark intact instead of slicing its edges off.
#
# Small sources ARE scaled up to the tile. Capping at 1:1 was tried and looked wrong: a 100 px logo
# baked 36 px of padding into its tile and then rendered visibly smaller than its neighbours, so the
# grid read as ragged. It also saved nothing - the consumer upscales the tile to cell size either way,
# so the cap only moved where the interpolation happened. The [LogoMinSourcePx] floor already bounds
# how far anything is stretched.
function Get-LogoDestRect {
    param([int]$imgW, [int]$imgH, [int]$tileX, [int]$tileY, [int]$tileW, [int]$tileH)
    if ($imgW -le 0 -or $imgH -le 0) { return $null }
    $scale = [Math]::Min($tileW / [double]$imgW, $tileH / [double]$imgH)
    $w = [int][Math]::Max(1, [Math]::Round($imgW * $scale))
    $h = [int][Math]::Max(1, [Math]::Round($imgH * $scale))
    $x = $tileX + [int][Math]::Floor(($tileW - $w) / 2.0)
    $y = $tileY + [int][Math]::Floor(($tileH - $h) / 2.0)
    return [System.Drawing.Rectangle]::new($x, $y, $w, $h)
}

function Build-StreamLogoAtlas {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [string]$SheetPath = $LogoAtlasPath,
        [string]$CoordsFile = $LogoCoordsPath
    )

    Add-Type -AssemblyName System.Drawing

    $usable = Select-LogoRows -Rows $Rows
    if ($usable.Count -eq 0) { throw "No cached artwork under $LogoCacheDir - run the favicon pass first." }

    $cols = $script:LogoCols
    $tileW = $script:LogoTileW
    $tileH = $script:LogoTileH
    $maxSlots = $cols * $script:LogoMaxRows
    $packable = $usable
    if ($packable.Count -gt $maxSlots) {
        Write-Warning ("Stream logos: {0} logos exceed the {1}-slot sheet capacity; dropping the last {2}." -f `
                $packable.Count, $maxSlots, ($packable.Count - $maxSlots))
        $packable = @($packable[0..($maxSlots - 1)])
    }

    $rowsNeeded = [int][Math]::Ceiling($packable.Count / [double]$cols)
    $sheetW = $cols * $tileW
    $sheetH = $rowsNeeded * $tileH
    $pngPath = [System.IO.Path]::ChangeExtension($SheetPath, '.png')
    $parent = Split-Path -Parent $SheetPath
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    Write-Host ("Stream logos: packing {0} tile(s) into {1}x{2} .." -f $packable.Count, $sheetW, $sheetH) -ForegroundColor Yellow
    $map = [ordered]@{}
    $skipped = 0
    # 32bpp ARGB cleared to Transparent, NOT the preview packer's opaque black: the padding around a
    # non-16:9 logo must take the app's own cell colour, so one sheet serves both light and dark themes.
    $sheet = [System.Drawing.Bitmap]::new($sheetW, $sheetH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($sheet)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
        $g.Clear([System.Drawing.Color]::Transparent)
        for ($i = 0; $i -lt $packable.Count; $i++) {
            $col = $i % $cols
            $rr = [int][Math]::Floor($i / $cols)
            $logo = $null
            try {
                $logo = [System.Drawing.Image]::FromFile($packable[$i].File)
                $dest = Get-LogoDestRect -imgW $logo.Width -imgH $logo.Height `
                    -tileX ($col * $tileW) -tileY ($rr * $tileH) -tileW $tileW -tileH $tileH
                if ($dest) {
                    $g.DrawImage($logo, $dest)
                    foreach ($u in $packable[$i].Urls) { $map[$u] = $i }
                }
            }
            catch {
                # A truncated or non-image cache entry must not abort the sheet: leave the slot empty
                # and drop the url, so that station falls back to its favicon in the grid.
                $skipped++
            }
            finally { if ($logo) { $logo.Dispose() } }
        }
    }
    finally {
        $g.Dispose()
        $sheet.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $sheet.Dispose()
    }

    $ffmpeg = Get-FfmpegExe
    # Quality 90, above the preview sheet's 80: logos are flat colour and type, which lossy blurs
    # sooner than photographic frames. Lossless was measured too - visually indistinguishable at tile
    # scale for 15.9 MB against 6.5 MB, so the bytes buy nothing a user can see. The 4:2:0 seam lossy
    # would otherwise cause between tiles is handled by the even tile size, not by the quality knob.
    Write-Host 'Stream logos: encoding WebP sheet (alpha preserved) ..' -ForegroundColor Yellow
    & $ffmpeg -hide_banner -loglevel error -y -i $pngPath -c:v libwebp -preset picture -quality 90 -compression_level 6 $SheetPath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $SheetPath)) { throw "ffmpeg WebP encode failed (exit $LASTEXITCODE)" }

    Backup-IfExists -Path $CoordsFile | Out-Null
    ($map | ConvertTo-Json -Compress -Depth 2) | Set-Content -Path $CoordsFile -Encoding utf8NoBOM

    $sheetBytes = (Get-Item $SheetPath).Length
    $coordsBytes = (Get-Item $CoordsFile).Length
    Write-Host ''
    Write-Host ("Stream logo atlas: {0} tile(s) covering {1} channel(s), sheet {2}x{3}, {4} slot(s) spare, {5} unreadable" -f `
            $packable.Count, $map.Count, $sheetW, $sheetH, ($maxSlots - $packable.Count), $skipped) -ForegroundColor Green
    foreach ($f in @($SheetPath, $CoordsFile)) {
        $h = (Get-FileHash -Algorithm SHA256 -Path $f).Hash.ToLowerInvariant()
        Write-Host ("  {0}" -f (Resolve-Path $f).Path) -ForegroundColor Green
        Write-Host ("    sha256 = {0}" -f $h) -ForegroundColor DarkGray
        Write-Host ("    bytes  = {0:N0}" -f (Get-Item $f).Length) -ForegroundColor DarkGray
    }
    Write-Host ("  (sheet {0:N1} MB, sidecar {1:N1} KB) - paste both pins into DeliverableDescriptorCatalog.streamLogoAtlas()" -f `
        ($sheetBytes / 1MB), ($coordsBytes / 1KB)) -ForegroundColor DarkGray
    return $map.Count
}

# Upload the logo atlas as its own release assets, mirroring the channel-preview atlas: the payload
# has an independent lifecycle and the app fetches the two files by their versioned asset names.
function Invoke-PublishStreamLogoAtlas {
    param([string]$SheetPath = $LogoAtlasPath, [string]$CoordsFile = $LogoCoordsPath, [string]$Tag = $PublishTag)
    foreach ($f in @($SheetPath, $CoordsFile)) {
        if (-not (Test-Path $f)) { throw "Stream logo atlas artifact missing for publish: $f (run with -WithStreamLogos first)" }
    }
    $ghExe = Get-GhExe
    $stageDir = 'temp/stream-logo-publish'
    if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
    New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
    # The remote asset name carries the element revision (-v1), matching DeliverableDescriptorCatalog's
    # withRev(); the on-device file name stays unversioned.
    $sheetAsset = Join-Path $stageDir ("stream-logo-atlas-{0}.webp" -f $SheetRev)
    $coordsAsset = Join-Path $stageDir ("stream-logo-coords-{0}.json" -f $CoordsRev)
    Copy-Item $SheetPath $sheetAsset -Force
    Copy-Item $CoordsFile $coordsAsset -Force

    Write-Host ("Publishing stream logo atlas to release {0} (--clobber) .." -f $Tag) -ForegroundColor Cyan
    & $ghExe release upload $Tag $sheetAsset $coordsAsset --clobber
    if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    Write-Host ("Published {0} ({1:N1} MB) + {2} ({3:N1} KB) -> {4}." -f `
        (Split-Path -Leaf $sheetAsset), ((Get-Item $sheetAsset).Length / 1MB), `
        (Split-Path -Leaf $coordsAsset), ((Get-Item $coordsAsset).Length / 1KB), $Tag) -ForegroundColor Green
}

# Entry point for -WithStreamLogos: every catalog row with a homepage, in catalog order. Deliberately
# not AUDIO-only - a video channel whose frame capture failed benefits from the same fallback.
function Invoke-BuildStreamLogoAtlasRun {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    # AUDIO first: if the sheet ever overflows, the dropped rows should be video channels, which have
    # the preview atlas to fall back on. Radio has nothing else, so it gets the slots first.
    $rows = @(Import-Csv -Path $ExistingCsv |
            Where-Object { -not [string]::IsNullOrWhiteSpace((Get-ArtHomepage -Row $_)) } |
            Sort-Object -Stable @{ Expression = { if ([string]$_.media_kind -eq 'AUDIO') { 0 } else { 1 } } })
    if ($rows.Count -eq 0) { throw "No rows with a homepage in $ExistingCsv - nothing to pack." }
    if ($LogoLimit -gt 0 -and $rows.Count -gt $LogoLimit) {
        Write-Host ("Stream logos: limited to the first {0} of {1} rows (-LogoLimit)" -f $LogoLimit, $rows.Count) -ForegroundColor DarkYellow
        $rows = @($rows[0..($LogoLimit - 1)])
    }
    Build-StreamLogoAtlas -Rows $rows | Out-Null
}

# --- S1445 tile packs (offline repack of a sprite sheet into a random-access container) ----------

# Cut a finished sprite sheet into one image per slot and pack them into a ZIP whose entry name is
# the slot index as a plain decimal string with no extension - the contract StreamTilePackReader
# reads. Entries are stored uncompressed: the tiles are already compressed images, and stored entries
# keep ZipFile's random access cheap on device.
#
# The cut is driven by the sheet itself rather than by the source frames, so the tile indices stay
# exactly the ones the published url->index sidecar already points at.
function Build-TilePackFromSheet {
    param(
        [Parameter(Mandatory = $true)][string]$SheetPath,
        [Parameter(Mandatory = $true)][string]$CoordsFile,
        [Parameter(Mandatory = $true)][int]$TileW,
        [Parameter(Mandatory = $true)][int]$TileH,
        [Parameter(Mandatory = $true)][int]$Cols,
        [Parameter(Mandatory = $true)][string]$OutZip,
        [int]$Quality = $TilePackQuality,
        # `untile` refuses a grid whose tile size is not a whole number of chroma blocks, and the
        # preview tile is 135 px tall - odd. Converting to a non-subsampled format first sidesteps
        # that; `rgba` is required for the logo sheet, whose transparent margins are the whole point.
        [string]$PixelFormat = 'rgb24'
    )

    foreach ($f in @($SheetPath, $CoordsFile)) {
        if (-not (Test-Path $f)) { throw "Tile pack input missing: $f" }
    }

    $ffmpeg = Get-FfmpegExe
    $sheetFull = (Resolve-Path $SheetPath).Path

    # ffmpeg prints the stream line only on stderr; the sheet dimensions decide the untile grid, and
    # guessing them from the sidecar would silently mis-cut a sheet whose last row is partly empty.
    $probe = & $ffmpeg -hide_banner -i $sheetFull -f null - 2>&1 | Out-String
    $match = [regex]::Match($probe, 'Stream #0:0.*?,\s(\d{3,5})x(\d{3,5})')
    if (-not $match.Success) { throw "Could not read the sheet dimensions of $SheetPath from ffmpeg output." }
    $sheetW = [int]$match.Groups[1].Value
    $sheetH = [int]$match.Groups[2].Value
    if ($sheetW % $TileW -ne 0 -or $sheetH % $TileH -ne 0) {
        throw ("Sheet {0}x{1} is not a whole number of {2}x{3} tiles - geometry contract broken." -f $sheetW, $sheetH, $TileW, $TileH)
    }
    $sheetCols = [int]($sheetW / $TileW)
    if ($sheetCols -ne $Cols) {
        throw ("Sheet has {0} columns, the app contract says {1} - refusing to cut." -f $sheetCols, $Cols)
    }
    $rows = [int]($sheetH / $TileH)

    $wanted = [System.Collections.Generic.HashSet[int]]::new()
    $coords = Get-Content $CoordsFile -Raw | ConvertFrom-Json
    foreach ($prop in $coords.PSObject.Properties) { $null = $wanted.Add([int]$prop.Value) }

    $stage = Join-Path 'temp' ('tile-pack-' + [System.IO.Path]::GetFileNameWithoutExtension($OutZip))
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    $cutDir = Join-Path $stage 'cut'
    $packDir = Join-Path $stage 'pack'
    New-Item -ItemType Directory -Path $cutDir -Force | Out-Null
    New-Item -ItemType Directory -Path $packDir -Force | Out-Null

    Write-Host ("Tile pack: cutting {0}x{1} into {2}x{3} tiles ({4} slot(s), {5} wanted) .." -f `
            $sheetW, $sheetH, $Cols, $rows, ($Cols * $rows), $wanted.Count) -ForegroundColor Yellow
    # One ffmpeg pass: untile emits the whole grid in row-major order, which is the order the packer
    # laid the tiles down in (col = index % cols), so frame N is slot N.
    & $ffmpeg -hide_banner -loglevel error -y -i $sheetFull -vf ("format={0},untile={1}x{2}" -f $PixelFormat, $Cols, $rows) `
        -c:v libwebp -quality $Quality -compression_level 6 -start_number 0 (Join-Path $cutDir '%06d.webp')
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg untile failed (exit $LASTEXITCODE)" }

    $kept = 0
    foreach ($index in ($wanted | Sort-Object)) {
        $cut = Join-Path $cutDir ('{0:D6}.webp' -f $index)
        if (-not (Test-Path $cut)) {
            Write-Warning ("Tile pack: slot {0} has no cut tile - skipped." -f $index)
            continue
        }
        Move-Item $cut (Join-Path $packDir ([string]$index)) -Force
        $kept++
    }
    if ($kept -eq 0) { throw 'Tile pack: no tiles survived the cut - refusing to write an empty pack.' }

    Backup-IfExists -Path $OutZip | Out-Null
    if (Test-Path $OutZip) { Remove-Item $OutZip -Force }
    Compress-Archive -Path (Join-Path $packDir '*') -DestinationPath $OutZip -CompressionLevel NoCompression
    Remove-Item $cutDir -Recurse -Force

    $packBytes = (Get-Item $OutZip).Length
    $sheetBytes = (Get-Item $sheetFull).Length
    $hash = (Get-FileHash -Algorithm SHA256 -Path $OutZip).Hash.ToLowerInvariant()
    Write-Host ''
    Write-Host ("Tile pack: {0} entr(ies) -> {1}" -f $kept, (Resolve-Path $OutZip).Path) -ForegroundColor Green
    Write-Host ("    sha256 = {0}" -f $hash) -ForegroundColor DarkGray
    Write-Host ("    bytes  = {0:N0} (sheet was {1:N0}; {2:P0} of it)" -f $packBytes, $sheetBytes, ($packBytes / $sheetBytes)) -ForegroundColor DarkGray
    return $kept
}

function Invoke-BuildTilePacksRun {
    Build-TilePackFromSheet -SheetPath $PreviewAtlasPath -CoordsFile $PreviewCoordsPath `
        -TileW $script:PreviewTileW -TileH $script:PreviewTileH -Cols $script:PreviewCols `
        -OutZip $PreviewTilePackPath | Out-Null
    Build-TilePackFromSheet -SheetPath $LogoAtlasPath -CoordsFile $LogoCoordsPath `
        -TileW $script:LogoTileW -TileH $script:LogoTileH -Cols $script:LogoCols `
        -OutZip $LogoTilePackPath -PixelFormat 'rgba' | Out-Null
}

# S1483: the manifest is how an INSTALLED app learns that newer artwork exists. Pins compiled into a
# build cannot do that - a build comparing against itself always concludes it is current, which is why
# a rebuilt payload reached nobody until a new version shipped. This file is published by the same run
# that uploads the payload, so the two can never disagree.
#
# `stamp` is the pack's own SHA-256: it changes exactly when the artwork changes, which is the only
# event the app cares about. The per-file hashes are published for third parties and for diagnosis,
# NOT as an app-side gate - the app validates a pack structurally (S1483 phase 04).
function Write-ArtworkManifest {
    param([string]$Path, [hashtable]$Sets)
    $manifest = [ordered]@{
        schemaVersion = 1
        generatedAt   = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        sets          = [ordered]@{}
    }
    foreach ($setName in @($Sets.Keys | Sort-Object)) {
        $files = [System.Collections.Generic.List[object]]::new()
        foreach ($file in $Sets[$setName]) {
            if (-not (Test-Path $file)) { continue }
            $files.Add([ordered]@{
                    name   = (Split-Path -Leaf $file)
                    size   = (Get-Item $file).Length
                    sha256 = (Get-FileHash -Algorithm SHA256 -Path $file).Hash.ToLowerInvariant()
                })
        }
        if ($files.Count -eq 0) { continue }
        $manifest.sets[$setName] = [ordered]@{
            # The pack is entry 0 of each set by construction below, and it is what the stamp tracks.
            stamp = $files[0].sha256
            files = $files
        }
    }
    ($manifest | ConvertTo-Json -Depth 6) | Set-Content -Path $Path -Encoding utf8NoBOM
    Write-Host ("Artwork manifest: {0} set(s) -> {1}" -f $manifest.sets.Count, $Path) -ForegroundColor Green
    return $Path
}

# Upload both packs as their own release assets, alongside the sheets, which stay published unchanged
# for third-party consumers of the catalog.
function Invoke-PublishTilePacks {
    param([string]$PreviewPack = $PreviewTilePackPath, [string]$LogoPack = $LogoTilePackPath, [string]$Tag = $PublishTag)
    foreach ($f in @($PreviewPack, $LogoPack)) {
        if (-not (Test-Path $f)) { throw "Tile pack missing for publish: $f (run with -WithTilePacks first)" }
    }
    $ghExe = Get-GhExe
    $stageDir = 'temp/tile-pack-publish'
    if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
    New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
    # The remote asset name carries the element revision, matching DeliverableDescriptorCatalog's
    # withRev(); the on-device file name stays unversioned.
    # S1483: the app fetches STABLE names - a revision in the name is what stranded a rebuilt payload
    # at a URL no installed copy knew about. The revisioned sheets stay published for third parties,
    # and the older revisioned packs are never deleted: builds shipped before this change still pin
    # them by hash and would otherwise lose their artwork.
    $uploads = [System.Collections.Generic.List[string]]::new()
    $manifestSets = @{}
    if ($ArtworkPayload -in 'both', 'preview') {
        $previewAsset = Join-Path $stageDir 'channel-preview-tiles.zip'
        $previewCoords = Join-Path $stageDir 'channel-preview-coords.json'
        Copy-Item $PreviewPack $previewAsset -Force
        Copy-Item $PreviewCoordsPath $previewCoords -Force
        $uploads.Add($previewAsset); $uploads.Add($previewCoords)
        $manifestSets['channelPreview'] = @($previewAsset, $previewCoords)
    }
    if ($ArtworkPayload -in 'both', 'logo') {
        $logoAsset = Join-Path $stageDir 'stream-logo-tiles.zip'
        $logoCoords = Join-Path $stageDir 'stream-logo-coords.json'
        Copy-Item $LogoPack $logoAsset -Force
        Copy-Item $LogoCoordsPath $logoCoords -Force
        $uploads.Add($logoAsset); $uploads.Add($logoCoords)
        $manifestSets['streamLogo'] = @($logoAsset, $logoCoords)
    }
    # A partial publish must not drop the other set from the manifest: the app reads it as the whole
    # truth, and a missing set would read as "this payload no longer exists".
    if ($ArtworkPayload -ne 'both') {
        $keptSet = if ($ArtworkPayload -eq 'logo') { 'channelPreview' } else { 'streamLogo' }
        $keptPack = if ($ArtworkPayload -eq 'logo') { $PreviewPack } else { $LogoPack }
        $keptCoords = if ($ArtworkPayload -eq 'logo') { $PreviewCoordsPath } else { $LogoCoordsPath }
        if ((Test-Path $keptPack) -and (Test-Path $keptCoords)) {
            $manifestSets[$keptSet] = @($keptPack, $keptCoords)
        }
    }
    $manifestPath = Join-Path $stageDir 'artwork-manifest.json'
    Write-ArtworkManifest -Path $manifestPath -Sets $manifestSets | Out-Null
    $uploads.Add($manifestPath)

    Write-Host ("Publishing tile pack(s) [{0}] to release {1} (--clobber) .." -f $ArtworkPayload, $Tag) -ForegroundColor Cyan
    & $ghExe release upload $Tag @($uploads) --clobber
    if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    foreach ($f in $uploads) {
        $h = (Get-FileHash -Algorithm SHA256 -Path $f).Hash.ToLowerInvariant()
        Write-Host ("Published {0} ({1:N0} bytes)" -f (Split-Path -Leaf $f), (Get-Item $f).Length) -ForegroundColor Green
        Write-Host ("    sha256 = {0}" -f $h) -ForegroundColor DarkGray
    }
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
# the cap it is skipped (CSV-only publish) because the app deliberately drops an over-cap atlas while
# still importing its CSV.
function Invoke-PublishCatalog {
    param([string]$CsvPath = $ExistingCsv, [string]$Tag = $PublishTag, [string]$AtlasFile = $AtlasPath)
    if (-not (Test-Path $CsvPath)) { throw "Catalog CSV not found for publish: $CsvPath" }
    $ghExe = Get-GhExe
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
            Write-Warning ("Favicon atlas {0} is {1:N1} KB > app import cap {2:N1} KB; publishing CSV-only." -f `
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
    & $ghExe release upload $Tag $zip --clobber
    if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    Write-Host ("Published stream-catalog.zip -> {0} ({1} rows, {2:N1} KB, {3})." -f $Tag, $rowCount, $zipKb, $bundleNote) -ForegroundColor Green
}

# S1477: rubric normalisation is its own mode - it rewrites a shipped CSV column, so it must not ride
# along with discovery, maintenance or an artwork pass. The before/after histogram is printed so the
# fold can be reviewed before -Publish sends it to every user.
if ($NormalizeTopics) {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    $topicRows = @(Import-Csv -Path $ExistingCsv)
    $before = @($topicRows | ForEach-Object { [string]$_.topic } | Select-Object -Unique).Count
    $moves = @{}
    $changed = 0
    foreach ($row in $topicRows) {
        $old = [string]$row.topic
        $new = Get-CanonicalTopic -topic $old
        if ($old -ne $new) {
            $changed++
            $key = "{0} -> {1}" -f $(if ([string]::IsNullOrWhiteSpace($old)) { '(blank)' } else { $old }), $new
            $moves[$key] = 1 + $(if ($moves.ContainsKey($key)) { $moves[$key] } else { 0 })
        }
        $row.topic = $new
    }
    $after = @($topicRows | Group-Object topic | Sort-Object Count -Descending)
    Write-Host ("Rubrics: {0} distinct -> {1}; {2} of {3} row(s) re-labelled." -f `
            $before, $after.Count, $changed, $topicRows.Count) -ForegroundColor Cyan
    foreach ($bucket in $after) {
        Write-Host ("  {0,5}  {1}" -f $bucket.Count, $bucket.Name) -ForegroundColor DarkGray
    }
    $mapReport = Join-Path $OutDir 'topic-rubric-moves.csv'
    Write-CsvUtf8 -Rows @($moves.GetEnumerator() | Sort-Object -Property Value -Descending |
            ForEach-Object { [pscustomobject]@{ move = $_.Key; rows = $_.Value } }) `
        -Path $mapReport -Columns @('move', 'rows')
    Write-Host ("Rubrics: per-value moves written to {0}" -f $mapReport) -ForegroundColor DarkGray
    $topicBackup = Backup-IfExists -Path $ExistingCsv
    Write-CsvUtf8 -Rows $topicRows -Path $ExistingCsv -Columns $Schema
    Write-Host ("Rubrics: rewrote {0}; backup -> {1}" -f $ExistingCsv, $topicBackup) -ForegroundColor Green
    if ($Publish) { Invoke-PublishCatalog }
    return
}

# Cache-warm is its own mode and writes nothing but cache files, so it is the one artwork pass that may
# run while another one is in flight: a full favicon rebuild can be crawling the declared homepages
# while this fills in the domain-derived ones. It deliberately never touches streams.csv or an atlas -
# whichever pass runs next picks the new cache entries up.
if ($WarmArtworkCache) {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    $warmRows = @(Import-Csv -Path $ExistingCsv)
    $declared = @($warmRows | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.homepage) }).Count
    $warmHomepages = @($warmRows | ForEach-Object { Get-ArtHomepage -Row $_ } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
    Write-Host ("Artwork cache warm: {0} row(s), {1} with a declared homepage, {2} distinct homepage(s) to cover{3}." -f `
            $warmRows.Count, $declared, $warmHomepages.Count, $(if ($DomainFallback) { ' (domain fallback ON)' } else { '' })) -ForegroundColor Cyan
    $warmed = Invoke-ArtworkCacheFetch -Homepages $warmHomepages
    Write-Host ("Artwork cache warm: {0}/{1} homepage(s) hold an image; cache dir {2}." -f `
            $warmed.Count, $warmHomepages.Count, $LogoCacheDir) -ForegroundColor Green
    return
}

# S1154 PHASE_06: the atlas build is its own mode - it never runs as a side effect of discovery or
# catalog maintenance (a capture pass costs hours and hits every live channel).
if ($WithChannelPreviews) {
    Invoke-BuildChannelPreviewAtlasRun
    if ($PublishPreviewAtlas) { Invoke-PublishChannelPreviewAtlas }
    return
}
if ($PublishPreviewAtlas) {
    Invoke-PublishChannelPreviewAtlas
    return
}

# S1445: repacking a finished sheet into tile packs is its own mode too - it must never ride along
# with a catalog refresh, because it publishes a payload the app pins by hash.
if ($WithTilePacks) {
    Invoke-BuildTilePacksRun
    if ($PublishTilePacks) { Invoke-PublishTilePacks }
    return
}
if ($PublishTilePacks) {
    Invoke-PublishTilePacks
    return
}

# S1201: the logo atlas is likewise its own mode. It reads only the artwork cache, so unlike the
# preview capture it costs minutes and no network - but it still must not run as a side effect.
if ($WithStreamLogos) {
    Invoke-BuildStreamLogoAtlasRun
    if ($PublishStreamLogoAtlas) { Invoke-PublishStreamLogoAtlas }
    return
}
if ($PublishStreamLogoAtlas) {
    Invoke-PublishStreamLogoAtlas
    return
}

if ($CatalogOnly) {
    Invoke-CatalogMaintenance
    if ($Publish) { Invoke-PublishCatalog }
    return
}

Write-Host "Collecting stream candidates [axis: $($Axis -join ', ')]" -ForegroundColor Cyan
$all = [System.Collections.Generic.List[object]]::new()

if ($Axis -contains 'official') {
    Write-Host '* direct official broadcaster feeds ..' -ForegroundColor Yellow
    $r = Get-OfficialTvSeeds -axis 'official'
    Write-Host ("    official seeds: {0}" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

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

if ($Axis -contains 'iptvcam') {
    Write-Host '* iptv-org weather / outdoor / travel / relax cams ..' -ForegroundColor Yellow
    $r = Get-IptvWebcamCandidates -axis 'iptvcam'
    Write-Host ("    iptv-org cams: {0}" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'tfl') {
    Write-Host '* Transport for London traffic cameras ..' -ForegroundColor Yellow
    $r = Get-TflJamCams -axis 'tfl'
    Write-Host ("    TfL JamCams: {0}" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'webradiodb') {
    Write-Host '* WebRadioDB curated station index ..' -ForegroundColor Yellow
    $r = Get-WebRadioDbStations -axis 'webradiodb'
    Write-Host ("    webradiodb: {0}" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'radioparadise') {
    Write-Host '* Radio Paradise channels ..' -ForegroundColor Yellow
    $r = Get-RadioParadiseStations -axis 'radioparadise'
    Write-Host ("    radioparadise: {0}" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'akc') {
    Write-Host '* AKC broadcast enumeration ..' -ForegroundColor Yellow
    $r = Get-AkcBroadcasts -axis 'akc'
    Write-Host ("    akc candidates: {0} (liveness decides)" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'lautfm') {
    Write-Host '* laut.fm community stations ..' -ForegroundColor Yellow
    $r = Get-LautFmStations -axis 'lautfm' -imageBudget $LautFmImageBudget
    Write-Host ("    laut.fm: {0} active station(s)" -f $r.Count)
    $r | ForEach-Object { $all.Add($_) }
}

if ($Axis -contains 'xiph') {
    Write-Host '* Xiph public Icecast YP directory ..' -ForegroundColor Yellow
    $r = Get-XiphYpStations -axis 'xiph'
    Write-Host ("    xiph yp: {0}" -f $r.Count)
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
