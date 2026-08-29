function Invoke-CatalogMaintenance {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    $allRows = @(Import-Csv -Path $ExistingCsv)
    if (-not $allRows -or $allRows.Count -eq 0) { throw "No rows in $ExistingCsv" }
    if ($Limit -gt 0 -and $PruneDead) { throw '-Limit cannot be combined with -PruneDead (pruning needs a full-catalog probe).' }
    Assert-PrunableStatuses -Statuses $PruneStatuses

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
            status       = $_.liveness_status
            http         = $_.http_code
            bytes        = $_.signal_bytes
            note         = $_.liveness_note
            media_kind   = $_.media_kind
            media_found  = $_.media_kinds
            media_codecs = $_.media_codecs
            provider     = (Get-ProviderKey -Url ([string]$_.url))
            category     = $_.category
            topic        = $_.topic
            name         = $_.name
            url          = $_.url
            country      = $_.country
            homepage     = $_.homepage
        }
    }
    $reportColumns = @('status', 'http', 'bytes', 'note', 'media_kind', 'media_found', 'media_codecs',
        'provider', 'category', 'topic', 'name', 'url', 'country', 'homepage')
    $sortedReport = $reportRows | Sort-Object status, category, topic, name
    Write-CsvUtf8 -Rows $sortedReport -Path $CatalogLivenessReport -Columns $reportColumns

    # S1830: also keep a per-run copy. The single fixed path is overwritten by whatever runs next, and
    # on 2026-08-19 the publish run six minutes later erased the per-row verdicts of the prune that had
    # just deleted 1 906 rows - so the one action that destroyed user data left nothing to audit.
    $runStamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
    $runReport = Join-Path (Split-Path -Parent $CatalogLivenessReport) `
        ("{0}.{1}.csv" -f [System.IO.Path]::GetFileNameWithoutExtension($CatalogLivenessReport), $runStamp)
    Write-CsvUtf8 -Rows $sortedReport -Path $runReport -Columns $reportColumns

    Show-LivenessSummary -Rows $probed -Title '=== Catalog liveness summary ==='
    Write-Host ''
    Write-Host ("Report written: {0} (per-run copy: {1})" -f $CatalogLivenessReport, $runReport) -ForegroundColor Green

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

    # S1830 supersedes the S1117 widening. 'unknown' means "this probe could not measure the row", not
    # "the channel is dead", and it does not reproduce: two identical deep-signal runs six minutes apart
    # over the same 19 534 rows disagreed by 95 rows. Pruning on it deleted 1 512 rows on 2026-08-19,
    # 79% of that day's removals, and a deletion is not recoverable - a re-added channel gets a new id,
    # so the user's pin and collection membership do not come back. Only a verdict that is a claim ABOUT
    # the channel may prune: 'dead' (404/410, DNS failure, refused) and 'geo' (403/451, owner ruling
    # 2026-08-19).
    $pruneStatuses = if ($DeepSignal -and -not $script:PruneStatusesExplicit) { @('dead', 'geo') } else { $PruneStatuses }
    Assert-PrunableStatuses -Statuses $pruneStatuses

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

    # S1830: last gate before an irreversible write. A provider losing most of its rows is what a
    # self-inflicted rate limit looks like from here, and it is invisible in every other number the run
    # prints, so it gets its own refusal rather than a line in a log nobody reads afterwards.
    $offenders = @(Get-ProviderLossOffenders -AllUrls @($allRows | ForEach-Object { [string]$_.url }) `
            -PrunedUrls @($pruneSet) -MinShare $ProviderLossShare -MinCount $ProviderLossMin)

    if ($offenders.Count -gt 0) {
        foreach ($o in $offenders) {
            Write-Host ("  provider loss: {0} - {1} of {2} row(s) ({3:P1})" -f `
                    $o.Provider, $o.Lost, $o.Total, $o.Share) -ForegroundColor Red
        }
        if (-not $AllowProviderLoss) {
            throw ("Refusing to prune: {0} provider(s) above the loss threshold ({1:P0} of their rows and at least {2}). A whole provider going dark in one run is far more often our probe under self-inflicted load than the provider actually dying - that is exactly how 1 321 live stations were deleted on 2026-08-19. Re-probe those rows on their own before deciding, or pass -AllowProviderLoss when the loss is real." -f `
                    $offenders.Count, $ProviderLossShare, $ProviderLossMin)
        }
        Write-Warning '-AllowProviderLoss set: pruning despite the provider-wide loss above.'
    }

    $backup = Backup-IfExists -Path $ExistingCsv
    $survivors = $allRows | Where-Object { -not $pruneSet.Contains([string]$_.url) }
    Write-CsvUtf8 -Rows $survivors -Path $ExistingCsv -Columns $Schema
    Write-Host "`nPruned $pruneCount row(s); backup: $backup; catalog now $($survivors.Count) row(s)." -ForegroundColor Green
}

# Validate that indexed rows never ship without their matching atlas.
function Assert-FaviconIndexPairing {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [Parameter(Mandatory = $true)][bool]$BundledAtlas,
        [switch]$AllowFaviconlessPublish
    )
    if ($BundledAtlas -or $AllowFaviconlessPublish) { return }
    $indexed = @($Rows | Where-Object { $_.favicon_index -match '^\d+$' }).Count
    if ($indexed -gt 0) {
        throw ("Refusing to publish: {0} row(s) carry favicon_index but no atlas is bundled." -f $indexed)
    }
}

function Assert-CatalogZipEntries {
    param(
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][bool]$BundledAtlas
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $ZipPath).Path)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $first = if ($entryNames.Count -gt 0) { $entryNames[0] } else { '' }
        if ($first -cne 'streams.csv') {
            throw "Compat invariant violated: zip entry 0 is '$first', expected exactly 'streams.csv' first."
        }
        if ($BundledAtlas -and -not ($entryNames -ccontains 'favicon-atlas.png')) {
            throw ("Compat invariant violated: an atlas was bundled but no entry is named exactly 'favicon-atlas.png' (entries: {0})." -f ($entryNames -join ', '))
        }
        return $entryNames
    }
    finally { $archive.Dispose() }
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
    $catalogRows = @(Import-Csv $CsvPath)
    $rowCount = $catalogRows.Count
    # S1835: every consumer discards a row with an empty name or url without a word, so shipping one
    # makes our row count and theirs disagree with neither side noticing. Refuse rather than strip:
    # stripping here would be a silent prune of the published bank, which is the exact event that cost
    # users their pins (S1830, S1832). Zero such rows exist today, so this costs nothing until a
    # collector regresses - and then it names the count instead of hiding it.
    $blankRows = @($catalogRows | Where-Object {
            [string]::IsNullOrWhiteSpace([string]$_.name) -or [string]::IsNullOrWhiteSpace([string]$_.url)
        })
    if ($blankRows.Count -gt 0) {
        throw ("Refusing to publish: {0} of {1} row(s) carry an empty name or url. Consumers drop such rows silently, so publishing them makes our row count and theirs diverge unnoticed. Fix the collector that produced them rather than stripping them here." -f `
                $blankRows.Count, $rowCount)
    }
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
    Assert-FaviconIndexPairing -Rows $catalogRows -BundledAtlas $bundledAtlas -AllowFaviconlessPublish:$AllowFaviconlessPublish

    $entryNames = @(Assert-CatalogZipEntries -ZipPath $zip -BundledAtlas $bundledAtlas)
    Write-Host ("  zip entries: {0}" -f ($entryNames -join ', ')) -ForegroundColor DarkGray

    $zipBytes = (Get-Item $zip).Length
    $zipKb = $zipBytes / 1KB
    # S1835: the 128 MB archive ceiling had no producer-side check. The only thing that stopped an
    # oversized upload was gh failing, caught by a generic message that never named the cause. Read as
    # MiB, matching how the 30 MiB atlas cap is already spelled in this script.
    $maxZipBytes = 134217728
    if ($zipBytes -gt $maxZipBytes) {
        throw ("Refusing to publish: stream-catalog.zip is {0:N1} MB, over the {1:N0} MB ceiling the catalog consumers declare. An archive past it fails their update outright. Shrink the payload (fewer bundled assets, or a smaller atlas) rather than raising this number - the ceiling belongs to the consumers, not to us." -f `
            ($zipBytes / 1MB), ($maxZipBytes / 1MB))
    }
    $bundleNote = if ($bundledAtlas) { 'csv + atlas' } else { 'csv-only' }
    Write-Host ("  zip {0:N1} KB ({1}); uploading to release {2} (--clobber) .." -f $zipKb, $bundleNote, $Tag) -ForegroundColor Cyan
    & $ghExe release upload $Tag $zip --clobber
    if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    Write-Host ("Published stream-catalog.zip -> {0} ({1} rows, {2:N1} KB, {3})." -f $Tag, $rowCount, $zipKb, $bundleNote) -ForegroundColor Green
}

function Invoke-PublisherModeDispatch {
if ($NormalizeFacets) {
    if (-not (Test-Path $ExistingCsv)) { throw "Catalog CSV not found: $ExistingCsv" }
    $facetRows = @(Import-Csv -Path $ExistingCsv)
    $result = Normalize-CatalogFacetRows -Rows $facetRows
    $mapReport = Join-Path $OutDir 'facet-normalization-moves.csv'
    Write-CsvUtf8 -Rows $result.Moves -Path $mapReport -Columns @('facet', 'from', 'to', 'rows')
    foreach ($facet in @('category', 'topic', 'language', 'country')) {
        $moved = @($result.Moves | Where-Object { $_.facet -eq $facet } | Measure-Object -Property rows -Sum).Sum
        Write-Host ("Facets: {0} move(s) in {1}." -f ($moved ?? 0), $facet) -ForegroundColor DarkGray
    }
    $stamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
    $backup = Join-Path $OutDir ("streams.csv.{0}.bak" -f $stamp)
    Copy-Item -LiteralPath $ExistingCsv -Destination $backup -Force
    if (-not (Test-Path -LiteralPath $backup)) { throw "Facet-normalization backup failed: $backup" }
    Write-CsvUtf8 -Rows $result.Rows -Path $ExistingCsv -Columns $Schema
    Write-Host ("Facets: rewrote {0}; moves -> {1}; backup -> {2}" -f $ExistingCsv, $mapReport, $backup) `
        -ForegroundColor Green
    if ($Publish) { Invoke-PublishCatalog }
    return $true
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
    return $true
}

function Normalize-CatalogFacetRows {
    param([object[]]$Rows)
    $moves = @{}
    $normalizers = @(
        @{ facet = 'category'; apply = { param($value) Get-CanonicalCategory -Category $value } },
        @{ facet = 'topic'; apply = { param($value) Get-CanonicalTopic -topic $value } },
        @{ facet = 'language'; apply = { param($value) Get-CanonicalLanguages -Languages $value } },
        @{ facet = 'country'; apply = { param($value) Get-CanonicalCountry -Country $value } }
    )
    foreach ($row in $Rows) {
        foreach ($normalizer in $normalizers) {
            $facet = [string]$normalizer.facet
            $old = [string]$row.$facet
            $new = [string](& $normalizer.apply $old)
            if ($old -eq $new) { continue }
            $key = "{0}`u{001F}{1}`u{001F}{2}" -f $facet, $old, $new
            $moves[$key] = 1 + $(if ($moves.ContainsKey($key)) { $moves[$key] } else { 0 })
            $row.$facet = $new
        }
    }
    $moveRows = @($moves.GetEnumerator() | ForEach-Object {
            $parts = $_.Key -split "`u{001F}", 3
            [pscustomobject]@{ facet = $parts[0]; from = $parts[1]; to = $parts[2]; rows = $_.Value }
        } | Sort-Object facet, rows -Descending)
    [pscustomobject]@{ Rows = $Rows; Moves = $moveRows }
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
    return $true
}

# S1828: external consumers pin revisioned asset names in their own code and do not roll forward on
# their own, so raising a revision default strands whoever still fetches the displaced name. Refuse
# before the first upload rather than after it - rolling an asset back on GitHub costs more than a
# refusal. One guard here covers both revisioned publishers, which dispatch from four places below.
if ($PublishPreviewAtlas -or $PublishStreamLogoAtlas) {
    $revisionGate = Join-Path $PSScriptRoot '..\quality\assert-stream-asset-revisions.ps1'
    if (-not (Test-Path $revisionGate)) {
        throw "Pinned-revision gate missing at $revisionGate - refusing to publish revisioned assets unchecked."
    }
    & $revisionGate -Quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Pinned asset revisions check refused this publication (exit $LASTEXITCODE); see the message above."
    }
}

# S1154 PHASE_06: the atlas build is its own mode - it never runs as a side effect of discovery or
# catalog maintenance (a capture pass costs hours and hits every live channel).
if ($WithChannelPreviews) {
    Invoke-BuildChannelPreviewAtlasRun
    if ($PublishPreviewAtlas) { Invoke-PublishChannelPreviewAtlas }
    return $true
}
if ($PublishPreviewAtlas) {
    Invoke-PublishChannelPreviewAtlas
    return $true
}

# S1445: repacking a finished sheet into tile packs is its own mode too - it must never ride along
# with a catalog refresh, because it publishes a payload the app pins by hash.
if ($WithTilePacks) {
    Invoke-BuildTilePacksRun
    if ($PublishTilePacks) { Invoke-PublishTilePacks }
    return $true
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
return $false
}
