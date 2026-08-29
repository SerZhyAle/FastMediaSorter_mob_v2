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
  pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithChannelPreviews -PreviewFromCacheOnly  # S1831 repack from cached frames, no network
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
    # S1827: that acknowledgement now costs more than its own name suggests. A second consumer,
    # StreamsPlayer, does NOT wipe anything on a favicon-less publish - it keeps the atlas it already has
    # and applies the new indices to it, so its users see other stations' logos on a UI that looks
    # correct. "Ship CSV-only intentionally" means "ship silently wrong icons to a third party".
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

    # Normalize category, topic, language and country on an existing catalog. Unlike discovery this is
    # a reviewable metadata-only rewrite: it creates a move report and never runs network collection.
    [switch]$NormalizeFacets,

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
    # Pack the sheet from frames the cache already holds and open no stream at all - the preview twin of
    # -ArtworkCacheOnly. Every capture is a request against somebody else's live server, so rebuilding the
    # sheet to check the packer itself must not cost them anything (S1831).
    [switch]$PreviewFromCacheOnly,
    # S1831 escape hatch. By default a VIDEO row's liveness is decided by taking its frame, which is both
    # the stronger test and half the cost of today's probe-then-capture pair. This reverts to the
    # ffprobe-only test for a run that must be compared against a pre-S1831 baseline, or if a provider ever
    # turns out to punish the capture. It does NOT change any verdict semantics - the ffprobe rung is in
    # the chain either way - only which rung gets asked first.
    [switch]$SkipCaptureFirst,
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
    # S1827: the shared atlas ceiling, 30 MiB, and the ONLY place this repository spells the number.
    # Three independent consumers carry their own copy of it, and each discards an over-cap atlas in its
    # own way: this publisher skips bundling it (CSV-only), the app
    # (ImportStreamCatalogUseCase.MAX_ATLAS_BYTES) drops it and wipes every favicon, and StreamsPlayer
    # (StreamBankReader.MaximumAtlasBytes, another repository) keeps the previously installed sheet and
    # applies the new CSV's indices to it, so its channels show other stations' logos while looking
    # healthy. Enforced at build time by Assert-AtlasBudget and again before publish.
    [int]$MaxAtlasBytes = 31457280,

    # S1831: the PREVIEW sheet's ceiling, and a DIFFERENT contract from $MaxAtlasBytes above. That one is the
    # favicon atlas's 30 MiB, shared with the app and with StreamBankReader. This one is the 48 MiB
    # StreamsPlayer declared for the preview sheet (S1828). Conflating them applies the wrong number to both:
    # 30 MiB would refuse a legal preview sheet, 48 MiB would let an over-cap favicon atlas ship and wipe
    # every user's icons. Until this parameter existed the preview sheet was checked against nothing at all -
    # Assert-AtlasBudget has one call site and it guards the favicon atlas - so an oversized sheet reached
    # publication with no complaint from anywhere.
    [int]$MaxPreviewAtlasBytes = 50331648,

    # S1841: the LOGO sheet's ceiling, and a THIRD contract distinct from both above. Three published
    # assets, three consumers, three numbers; conflating any two applies the wrong one to both, which is
    # the mistake S1831 recorded for the preview sheet. Until this parameter existed the logo sheet was
    # measured against nothing at all - Assert-AtlasBudget has one call site and it guards the favicon
    # atlas - so a sheet of any size reached publication unopposed. No external consumer declares a pin
    # or a ceiling for this asset (docs/STREAM_CATALOG_CONSUMERS.md, S1828), so the number is ours to
    # pick: 48 MiB sits well above the 16.0 MB measured for today's 4148 tiles and above the ~28.6 MB
    # the format ceiling's 7080 tiles would cost, making it a regression alarm, not a brake on growth.
    [int]$MaxLogoAtlasBytes = 50331648,

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
    # S1830: a prune that takes a large share of ONE provider is the shape of a probe failure, not of a
    # provider dying - on 2026-08-19 laut.fm lost 1 321 of its 2 038 rows and nothing printed said so,
    # because each station has its own subdomain and a per-host histogram showed only 17-row entries.
    # Refuse such a run and name the provider; -AllowProviderLoss is the deliberate override.
    [double]$ProviderLossShare = 0.35,
    [int]$ProviderLossMin = 50,
    [switch]$AllowProviderLoss,

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

# A `pwsh -File` invocation cannot build an array: `-PruneStatuses dead,unknown,geo` arrives as ONE
# string, which matches no verdict, so the prune finds nothing and reports "Nothing to prune (no rows
# classified: dead,unknown,geo)" - a wrong verdict that reads exactly like a correct one. Measured
# 2026-08-19 on a full 19534-row deep-signal sweep that should have pruned 2001 rows. Splitting here
# costs nothing for a caller that already passed a real array.
# The call itself sits just below the module dot-sources, since that is where its function is defined.

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

. (Join-Path $PSScriptRoot 'modules/StreamPublisher.Common.ps1')
. (Join-Path $PSScriptRoot 'modules/StreamPublisher.Probes.ps1')
. (Join-Path $PSScriptRoot 'modules/StreamPublisher.Discovery.ps1')
. (Join-Path $PSScriptRoot 'modules/StreamPublisher.Artwork.ps1')
. (Join-Path $PSScriptRoot 'modules/StreamPublisher.Delivery.ps1')

# Must follow the dot-sources: Normalize-PruneStatuses is defined in StreamPublisher.Common.ps1, and
# calling it above them aborted every run of this script under ErrorActionPreference='Stop'.
$PruneStatuses = Normalize-PruneStatuses -Statuses $PruneStatuses

if (Invoke-PublisherModeDispatch) { return }
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
