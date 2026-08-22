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
# S1827: report the freshly written atlas against the shared byte ceiling, and refuse an over-cap sheet
# HERE, where it is built, instead of at publish time. Before this the only check sat in
# Invoke-PublishCatalog, so a run learned it had overshot only after fetching every homepage in the
# catalog - and the headroom was never printed at all, so approaching the ceiling was invisible.
# An over-cap atlas is rolled back to its backup (or deleted when there was none) before the throw:
# leaving the new PNG on disk next to a CSV still carrying the previous indices is the one state that
# silently mismatches, and it is exactly the pairing every consumer resolves by showing wrong icons.
function Assert-AtlasBudget {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][int]$Tiles,
        [string]$RestoreFrom = ''
    )

    $bytes = (Get-Item $Path).Length
    $percent = if ($MaxAtlasBytes -gt 0) { ($bytes / $MaxAtlasBytes) * 100 } else { 0 }
    $perTile = if ($Tiles -gt 0) { $bytes / $Tiles } else { 0 }
    $fits = if ($perTile -gt 0) { [int][Math]::Floor($MaxAtlasBytes / $perTile) } else { 0 }

    Write-Host ("Favicons: atlas budget {0:N0} of {1:N0} B ({2:N1}% of the shared ceiling), {3:N0} B/tile, room for about {4:N0} tile(s) at this density." -f `
            $bytes, $MaxAtlasBytes, $percent, $perTile, $fits) -ForegroundColor Cyan

    if ($bytes -le $MaxAtlasBytes) { return }

    if ($RestoreFrom -and (Test-Path $RestoreFrom)) {
        Copy-Item -Path $RestoreFrom -Destination $Path -Force
        Write-Warning ("Restored the previous atlas from {0}; the over-cap sheet was discarded." -f $RestoreFrom)
    }
    elseif (Test-Path $Path) {
        Remove-Item -Path $Path -Force
    }

    throw ("Favicon atlas is {0:N0} B, over the {1:N0} B ceiling shared with the app (ImportStreamCatalogUseCase.MAX_ATLAS_BYTES) and with StreamsPlayer (StreamBankReader.MaximumAtlasBytes). About {2:N0} tile(s) fit at this density and {3:N0} were packed. Every consumer discards an over-cap atlas, and none of them says so to the user: the app wipes every favicon, while StreamsPlayer keeps the previously installed sheet and applies the new indices to it, so its channels show other stations' logos and look healthy." -f `
            $bytes, $MaxAtlasBytes, $fits, $Tiles)
}

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
    $atlasBackup = Backup-IfExists -Path $AtlasPath
    $atlas.Save($AtlasPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $atlas.Dispose()

    $sizeKb = (Get-Item $AtlasPath).Length / 1KB
    Write-Host ("Favicons: packed {0} tile(s) into {1}x{2} atlas -> {3} ({4:N1} KB)." -f `
            $packable.Count, $atlasW, $atlasH, $AtlasPath, $sizeKb) -ForegroundColor Green
    Assert-AtlasBudget -Path $AtlasPath -Tiles $packable.Count -RestoreFrom $atlasBackup
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
# The sheet's height follows the tile count, the way the favicon atlas's already does. It is deliberately
# NOT a constant any more. The retired 60-row maximum came from a self-imposed "one 8192x8192
# sheet" budget, and that budget - not any consumer - is what left 877 of today's 2917 VIDEO channels
# without a thumbnail, dropped with a WARNING nobody reads (S1831). Those frames were not missing: 2928 sat
# in the frame cache, already fetched from real broadcasters, and 888 of them were packed nowhere.
# No receiving side declares a row count:
# ChannelPreviewAtlasSlicer resolves a tile as col = index % COLS, row = index / COLS and takes the atlas
# width and height as arguments.
# What IS a real ceiling is the format. VP8 stores a dimension in 14 bits, so no WebP side may exceed
# 16383 px. Measured against this repo's own ffmpeg 8.1.1 rather than read off a spec: 240x16383 encodes,
# 240x16384 refuses with "Picture size is too large. Max is 16383x16383".
$script:PreviewMaxSheetPx = 16383

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
    if ($PreviewFromCacheOnly -and $pending.Count -gt 0) {
        Write-Host ("Channel previews: -PreviewFromCacheOnly, so {0} uncaptured channel(s) stay uncaptured and no stream is opened." -f `
                $pending.Count) -ForegroundColor Yellow
        $pending = @()
    }
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

# Pack the captured frames into the fixed-width sheet, encode it to WebP, and write the url->index
# sidecar. Returns the number of packed tiles. The sheet's height follows the tile count: every frame
# handed in is placed, and a set too tall for the WebP dimension limit is refused outright rather than
# trimmed to fit, because a trimmed run publishes successfully while some channels silently lose their
# thumbnail (S1831).
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
    $packable = $captured
    $rowsNeeded = [int][Math]::Ceiling($packable.Count / [double]$cols)
    $sheetW = $cols * $tileW
    $sheetH = $rowsNeeded * $tileH

    # Refuse BEFORE the encoder does. Letting the sheet reach libwebp over-size reports
    # "ffmpeg WebP encode failed (exit -22)", which names neither the tile count nor the channels that lost
    # their thumbnail - the same silent loss this replaced, wearing an encoder's mask (S1831). The refusal
    # is a throw rather than a truncation because a run that cannot cover every channel is the thing the
    # operator has to know about, not something to paper over and publish.
    if ($sheetH -gt $script:PreviewMaxSheetPx) {
        $fitRows = [int][Math]::Floor($script:PreviewMaxSheetPx / $tileH)
        $fitTiles = $fitRows * $cols
        $msg = 'Channel previews: {0} captured frame(s) need {1} row(s) = {2}px, over the {3}px WebP ' +
        'dimension limit ({4} rows = {5} tiles fit). {6} channel(s) would get no thumbnail. Refusing ' +
        'to publish a partial sheet - more tiles need a change to the tile geometry, and that geometry ' +
        'is a contract with ChannelPreviewAtlasSlicer and with StreamsPlayer (see S1828).'
        throw ($msg -f $packable.Count, $rowsNeeded, $sheetH, $script:PreviewMaxSheetPx,
            $fitRows, $fitTiles, ($packable.Count - $fitTiles))
    }
    $pngPath = [System.IO.Path]::ChangeExtension($SheetPath, '.png')
    $parent = Split-Path -Parent $SheetPath
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    # The whole sheet is one 32bpp Bitmap before it is encoded, and it now grows with the channel count
    # instead of stopping at 60 rows - 264 MiB at the old cap, 353 MiB measured at 2830 tiles, 532 MiB at
    # the format ceiling. Printed rather than merely true, so a run that dies of memory says why (S1831).
    Write-Host ("Channel previews: packing {0} tile(s) into {1}x{2} ({3} row(s), ~{4:N0} MiB in memory) .." -f `
            $packable.Count, $sheetW, $sheetH, $rowsNeeded,
        ([double]$sheetW * $sheetH * 4 / 1MB)) -ForegroundColor Yellow
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
    # Both halves of this condition are load-bearing: an over-size encode exits non-zero AND still leaves a
    # 0-byte file behind, so a Test-Path on its own reads the failure as a success (measured, S1831).
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $SheetPath)) {
        if (Test-Path $SheetPath) { Remove-Item $SheetPath -Force -ErrorAction SilentlyContinue }
        throw "ffmpeg WebP encode failed (exit $LASTEXITCODE)"
    }

    # The published sheet's only byte ceiling. Checked here rather than at publish time so a run that
    # produced an unshippable sheet fails where the sheet was made, not an hour later next to a `gh` upload.
    $encodedBytes = (Get-Item $SheetPath).Length
    if ($encodedBytes -gt $MaxPreviewAtlasBytes) {
        Remove-Item $SheetPath -Force -ErrorAction SilentlyContinue
        $overMsg = 'Channel previews: encoded sheet is {0:N0} B ({1:N1} MiB) over the {2:N0} B ({3:N1} MiB) ' +
        'limit StreamsPlayer declared for the preview sheet. {4} tile(s) at {5}x{6}. Refusing - a sheet ' +
        'past that size is discarded by the consumer, which then applies this build''s indices to the ' +
        'sheet it already had and shows every channel the wrong picture (S1828).'
        throw ($overMsg -f $encodedBytes, ($encodedBytes / 1MB), $MaxPreviewAtlasBytes,
            ($MaxPreviewAtlasBytes / 1MB), $packable.Count, $sheetW, $sheetH)
    }

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
# The sheet's height follows the tile count instead of a fixed row maximum, matching what S1831 did for
# the channel-preview sheet. The retired 60-row cap came from a self-imposed "one 8k x 8k sheet" budget
# that no consumer ever declared, and that budget - not any receiving side - is what silently dropped
# every logo past tile 3539. Measured 2026-08-20 against the live catalog: the packer lays out 4148 tiles
# covering 5468 channels, while the published sheet stops at 3540 tiles covering 3875, so 608 logos
# reaching 1593 channel urls were dropped with their artwork already in the cache, and nothing said so.
#
# No receiving side declares a row count: StreamLogoAtlasSlicer resolves a tile as col = index % COLS,
# row = index / COLS, and the external-consumer registry (docs/STREAM_CATALOG_CONSUMERS.md, S1828)
# records no pin and no ceiling for this asset at all.
#
# What IS a real ceiling is the format. VP8 stores a dimension in 14 bits, so no WebP side may exceed
# 16383 px - the same limit the preview sheet measured against this repo's own ffmpeg, not off a spec.
# At a 136 px tile that is 120 rows = 7080 tiles.
$script:LogoMaxSheetPx = 16383

# Smallest source that earns a grid tile, measured on the larger side. Below this the cached artwork
# is just a tab icon: upscaling it is the very thing this ticket exists to stop, and drawn at native
# size it would float lost in a 136x136 tile. Such stations fall through to the favicon tier, which
# insets a 32 px icon deliberately. The threshold is about picture quality only: it stopped doubling as
# a capacity guard when the row cap went (S1841). Measured 2026-08-20 over the live catalog: 4148 distinct
# tiles clear the floor, 959 rows fall below it, 0 unreadable.
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
# burned ~300 slots and pushed the sheet over its then-capacity. One tile, many urls pointing at it.
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
    $packable = $usable

    $rowsNeeded = [int][Math]::Ceiling($packable.Count / [double]$cols)
    $sheetW = $cols * $tileW
    $sheetH = $rowsNeeded * $tileH

    # Refuse BEFORE the encoder does, and refuse rather than trim. Trimming is what this replaced: it left
    # the run exit-code-clean and the sheet published-but-partial, so 608 logos reaching 1593 channel urls
    # were dropped with their artwork already cached, and nothing said so (S1841). Letting the oversize
    # reach libwebp instead reports "encode failed (exit -22)", which names neither the tile count nor the
    # stations that lost their logo - the same silent loss in an encoder's mask. A run that cannot cover
    # every station is the thing the operator has to know about, not something to publish quietly.
    if ($sheetH -gt $script:LogoMaxSheetPx) {
        $fitRows = [int][Math]::Floor($script:LogoMaxSheetPx / $tileH)
        $fitTiles = $fitRows * $cols
        $msg = 'Stream logos: {0} usable logo(s) need {1} row(s) = {2}px, over the {3}px WebP dimension ' +
        'limit ({4} rows = {5} tiles fit). {6} station(s) would get no logo. Refusing to publish a partial ' +
        'sheet - more tiles need a change to the tile geometry, and that geometry is a contract with ' +
        'StreamLogoAtlasSlicer and with the consumers registered in docs/STREAM_CATALOG_CONSUMERS.md (S1828).'
        throw ($msg -f $packable.Count, $rowsNeeded, $sheetH, $script:LogoMaxSheetPx,
            $fitRows, $fitTiles, ($packable.Count - $fitTiles))
    }
    $pngPath = [System.IO.Path]::ChangeExtension($SheetPath, '.png')
    $parent = Split-Path -Parent $SheetPath
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    # The whole sheet is one 32bpp Bitmap before it is encoded, and it grows with the station count now
    # instead of stopping at 60 rows - 250 MiB at the old cap, 296 MiB measured at today's 4148 tiles,
    # 499 MiB at the format ceiling. Printed rather than merely true, so a run that dies of memory says why.
    Write-Host ("Stream logos: packing {0} tile(s) into {1}x{2} ({3} row(s), ~{4:N0} MiB in memory) .." -f `
            $packable.Count, $sheetW, $sheetH, $rowsNeeded,
        ([double]$sheetW * $sheetH * 4 / 1MB)) -ForegroundColor Yellow
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

    # The logo sheet's own ceiling, checked before anything is published. Until this existed the sheet was
    # measured against nothing at all (S1841): Assert-AtlasBudget guards the favicon atlas at its single
    # call site, and the preview sheet got its own parameter in S1831, but the logo sheet had neither.
    $encodedBytes = (Get-Item $SheetPath).Length
    if ($encodedBytes -gt $MaxLogoAtlasBytes) {
        $overMsg = 'Stream logos: encoded sheet is {0:N0} bytes ({1:N1} MB), over the {2:N0} byte ' +
        '({3:N1} MB) ceiling for this asset, at {4} tile(s) and {5}x{6}. Refusing to publish.'
        throw ($overMsg -f $encodedBytes, ($encodedBytes / 1MB), $MaxLogoAtlasBytes,
            ($MaxLogoAtlasBytes / 1MB), $packable.Count, $sheetW, $sheetH)
    }

    Backup-IfExists -Path $CoordsFile | Out-Null
    ($map | ConvertTo-Json -Compress -Depth 2) | Set-Content -Path $CoordsFile -Encoding utf8NoBOM

    $sheetBytes = (Get-Item $SheetPath).Length
    $coordsBytes = (Get-Item $CoordsFile).Length
    Write-Host ''
    # Spare capacity is measured against the format ceiling now, not a self-imposed slot count: $maxSlots
    # went with the row cap (S1841), and leaving its name here would print an empty field.
    #
    # Built as a string first, then formatted: -f binds tighter than + in PowerShell, so formatting a
    # concatenation inline would format only its last fragment and leave the rest as literal braces.
    $capacityTiles = [int][Math]::Floor($script:LogoMaxSheetPx / $tileH) * $cols
    $doneMsg = 'Stream logo atlas: {0} tile(s) covering {1} channel(s), sheet {2}x{3}, {4} tile(s) ' +
    'spare before the {5}px format ceiling, {6} unreadable'
    Write-Host ($doneMsg -f $packable.Count, $map.Count, $sheetW, $sheetH,
        ($capacityTiles - $packable.Count), $script:LogoMaxSheetPx, $skipped) -ForegroundColor Green
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

