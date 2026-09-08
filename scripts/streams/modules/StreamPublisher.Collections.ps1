. "$PSScriptRoot\..\..\utils\project-paths.ps1"

# --- S2669 curated stream collections (offline tooling) ------------------------------------------
# A collection is a named, ordered group of rows of the shipped bank. The curator keeps two source
# files under delivery/stream-catalog/collections/ - rules.json (declarations plus a rule over the
# bank's own columns) and overlay.json (manual include/exclude by URL) - and this module composes
# them into delivery/stream-catalog/collections.json, the third entry of the delivered archive.
#
# SHARED CONTRACT with both apps (S2669 tactical INDEX.md "Shared Contract"):
#   - the archive entry is named exactly 'collections.json' and deliberately does NOT end in .csv,
#     because every released phone and watch build loads any .csv entry as a fallback stream bank;
#   - a member is keyed by the bank's `url` column, byte-identical, because that is the key the
#     app's catalog merge already uses and the only field whose stability the system guarantees;
#   - `schemaVersion` is 1. It is the channel for changing this shape later without touching the
#     bank, which has no version of its own.
#
# These functions THROW on a violation rather than returning a code, matching the sibling gates in
# StreamPublisher.Delivery.ps1 (Assert-CatalogNamesClean, Assert-CatalogZipEntries). The caller is
# the CLI, which lets the throw abort the run with a non-zero exit and the reason on stderr.

$script:CollectionsSchemaVersion = 1
$script:CollectionsRequiredLocales = @('en', 'ru', 'uk')

function Get-StreamCollectionsSourceDir {
    param([string]$CatalogDir = (Join-Path (Get-ProjectRoot) 'delivery/stream-catalog'))
    return (Join-Path $CatalogDir 'collections')
}

# Splits the bank's comma-separated language cell into trimmed lowercase tokens. The bank stores a
# list in one cell by convention (it is the only multi-valued column), so a rule over `language`
# has to match any one of them rather than the whole cell.
function Split-CatalogLanguages {
    param([string]$Cell)
    if ([string]::IsNullOrWhiteSpace($Cell)) { return @() }
    return @($Cell -split ',' | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ })
}

function Test-CollectionRuleMatch {
    param(
        [Parameter(Mandatory = $true)]$Row,
        $Rule
    )
    if ($null -eq $Rule) { return $false }
    $constrained = $false
    foreach ($facet in @('country', 'category', 'topic')) {
        # An absent property yields $null, and @($null) has Count 1 in PowerShell - so an unfiltered
        # wrap would read a facet the rule never mentions as a constraint on the empty string and
        # reject every row. Filter first.
        $wanted = @($Rule.$facet | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
        if ($wanted.Count -eq 0) { continue }
        $constrained = $true
        $actual = ([string]$Row.$facet).Trim().ToLowerInvariant()
        $hit = $false
        foreach ($value in $wanted) {
            if ($actual -eq ([string]$value).Trim().ToLowerInvariant()) { $hit = $true; break }
        }
        if (-not $hit) { return $false }
    }
    $wantedLanguages = @($Rule.language | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
    if ($wantedLanguages.Count -gt 0) {
        $constrained = $true
        $actual = Split-CatalogLanguages -Cell ([string]$Row.language)
        $hit = $false
        foreach ($value in $wantedLanguages) {
            if ($actual -contains ([string]$value).Trim().ToLowerInvariant()) { $hit = $true; break }
        }
        if (-not $hit) { return $false }
    }
    # An empty rule selects nothing rather than everything: such a collection is built entirely from
    # the overlay, and "no constraint" must never mean "the whole bank of 19k rows".
    return $constrained
}

# Composes collections.json from the bank plus the two curator sources.
#
# Member order (strategic 3.1 wish 1 - a collection must read, not look random):
#   overlay includes first, in the order the curator listed them, then the rule matches in the
#   bank's own row order; excludes are removed last so an explicit removal always wins; `order` is
#   then renumbered from 1 and contiguous.
function Build-StreamCollections {
    param(
        [string]$CsvPath = (Join-Path (Get-ProjectRoot) 'delivery/stream-catalog/streams.csv'),
        [string]$SourceDir = (Get-StreamCollectionsSourceDir),
        [string]$OutPath = (Join-Path (Get-ProjectRoot) 'delivery/stream-catalog/collections.json')
    )
    if (-not (Test-Path $CsvPath)) { throw "Catalog CSV not found for collections build: $CsvPath" }
    $rulesPath = Join-Path $SourceDir 'rules.json'
    $overlayPath = Join-Path $SourceDir 'overlay.json'
    if (-not (Test-Path $rulesPath)) { throw "Collection rules not found: $rulesPath" }
    if (-not (Test-Path $overlayPath)) { throw "Collection overlay not found: $overlayPath" }

    $rows = @(Import-Csv $CsvPath)
    $bankUrls = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($row in $rows) { [void]$bankUrls.Add([string]$row.url) }

    $declarations = @(Get-Content $rulesPath -Raw | ConvertFrom-Json)
    $overlayRaw = Get-Content $overlayPath -Raw | ConvertFrom-Json
    $overlay = @{}
    if ($null -ne $overlayRaw) {
        foreach ($property in $overlayRaw.PSObject.Properties) { $overlay[$property.Name] = $property.Value }
    }

    $declaredIds = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($declaration in $declarations) { [void]$declaredIds.Add([string]$declaration.id) }
    foreach ($key in $overlay.Keys) {
        if (-not $declaredIds.Contains([string]$key)) {
            throw ("Refusing to build collections: overlay.json names collection '{0}', which rules.json does not declare. An overlay for an undeclared collection is silently lost, so it is refused here rather than dropped." -f $key)
        }
    }

    $built = @()
    foreach ($declaration in ($declarations | Sort-Object @{ Expression = { [int]$_.order } }, @{ Expression = { [string]$_.id } })) {
        $id = [string]$declaration.id
        $entry = if ($overlay.ContainsKey($id)) { $overlay[$id] } else { $null }
        $includes = @()
        $excludes = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        if ($null -ne $entry) {
            $includes = @($entry.include | Where-Object { $_ })
            foreach ($url in @($entry.exclude | Where-Object { $_ })) { [void]$excludes.Add([string]$url) }
        }

        $ordered = [System.Collections.Generic.List[string]]::new()
        $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($url in $includes) {
            $text = [string]$url
            if ($excludes.Contains($text)) { continue }
            if ($seen.Add($text)) { $ordered.Add($text) }
        }
        foreach ($row in $rows) {
            if (-not (Test-CollectionRuleMatch -Row $row -Rule $declaration.rule)) { continue }
            $text = [string]$row.url
            if ($excludes.Contains($text)) { continue }
            if ($seen.Add($text)) { $ordered.Add($text) }
        }

        $members = @()
        for ($i = 0; $i -lt $ordered.Count; $i++) {
            $members += [ordered]@{ url = $ordered[$i]; order = ($i + 1) }
        }
        $names = [ordered]@{}
        if ($null -ne $declaration.names) {
            foreach ($property in $declaration.names.PSObject.Properties) { $names[$property.Name] = [string]$property.Value }
        }
        $built += [ordered]@{
            id      = $id
            order   = [int]$declaration.order
            names   = $names
            members = $members
        }
    }

    $payload = [ordered]@{
        schemaVersion = $script:CollectionsSchemaVersion
        collections   = $built
    }
    $json = ($payload | ConvertTo-Json -Depth 8) -replace "`r`n", "`n"
    if (-not $json.EndsWith("`n")) { $json += "`n" }
    [System.IO.File]::WriteAllText($OutPath, $json, [System.Text.UTF8Encoding]::new($false))
    Write-Host ("Collections built: {0} collection(s) -> {1}" -f $built.Count, $OutPath) -ForegroundColor Cyan
    return $payload
}

# Refuses a collection set that would reach a user broken. Each refusal names the collection and the
# specific violation, because the curator edits two files and a bare "invalid" would not say which.
function Assert-StreamCollections {
    param(
        [string]$CollectionsPath = (Join-Path (Get-ProjectRoot) 'delivery/stream-catalog/collections.json'),
        [string]$CsvPath = (Join-Path (Get-ProjectRoot) 'delivery/stream-catalog/streams.csv')
    )
    if (-not (Test-Path $CollectionsPath)) { throw "Collections artifact not found: $CollectionsPath" }
    if (-not (Test-Path $CsvPath)) { throw "Catalog CSV not found for collections check: $CsvPath" }

    $payload = Get-Content $CollectionsPath -Raw | ConvertFrom-Json
    if ([int]$payload.schemaVersion -ne $script:CollectionsSchemaVersion) {
        throw ("Refusing to publish collections: schemaVersion is {0}, this publisher writes {1}. A consumer keys its parse off this number, so shipping an unexpected one silently disables the whole feature on every client." -f `
            $payload.schemaVersion, $script:CollectionsSchemaVersion)
    }

    $bankUrls = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($row in @(Import-Csv $CsvPath)) { [void]$bankUrls.Add([string]$row.url) }

    $offences = @()
    $seenIds = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($collection in @($payload.collections)) {
        $id = [string]$collection.id
        if ([string]::IsNullOrWhiteSpace($id)) { $offences += 'a collection carries a blank id'; continue }
        if (-not $seenIds.Add($id)) { $offences += ("collection id '{0}' is declared more than once" -f $id) }

        $members = @($collection.members)
        if ($members.Count -eq 0) {
            $offences += ("collection '{0}' has no members - its rule matched nothing and its overlay adds nothing" -f $id)
        }
        foreach ($locale in $script:CollectionsRequiredLocales) {
            $name = if ($null -ne $collection.names) { [string]$collection.names.$locale } else { '' }
            if ([string]::IsNullOrWhiteSpace($name)) {
                $offences += ("collection '{0}' has no '{1}' name - the app would show its raw identifier" -f $id, $locale)
            }
        }
        $missing = @($members | Where-Object { -not $bankUrls.Contains([string]$_.url) })
        if ($missing.Count -gt 0) {
            $offences += ("collection '{0}' names {1} member url(s) absent from the bank (first: '{2}')" -f $id, $missing.Count, ([string]$missing[0].url))
        }
    }

    if ($offences.Count -gt 0) {
        throw ("Refusing to publish collections: {0}. Fix delivery/stream-catalog/collections/rules.json or overlay.json and rebuild - never hand-edit collections.json." -f `
            ($offences -join '; '))
    }
    Write-Host ("Collections check passed: {0} collection(s), every member present in the bank." -f @($payload.collections).Count) -ForegroundColor Green
    return $true
}
