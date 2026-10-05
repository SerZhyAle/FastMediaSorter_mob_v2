# Generator for Documentation Search Index (JSON)
# Part of S2970 (Documentation Site HTML & In-Browser Search)
# Builds documentation/assets/search-index.json from manifests and HTML recipe pages.

[CmdletBinding()]
param (
    [string]$OutputPath = "documentation/assets/search-index.json"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$docsDir = Join-Path $repoRoot 'docs'
$docRoot = Join-Path $repoRoot 'documentation'
$pageManifestPath = Join-Path $docsDir 'docs-pages-manifest.jsonl'

if (-not (Test-Path $pageManifestPath)) {
    Write-Error "generate-docs-search-index: Page manifest missing at $pageManifestPath"
    exit 1
}

# 1. Load page manifest
$manifestPages = @{}
Get-Content $pageManifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $p = ConvertFrom-Json $_
        if ($p.page_id) {
            $manifestPages[$p.page_id] = $p
        }
    }
}

# 2. Find and index physical HTML files
$indexedPages = [System.Collections.Generic.List[object]]::new()
$processedPageIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

if (Test-Path $docRoot) {
    $htmlFiles = Get-ChildItem -Path $docRoot -Recurse -File -Filter *.html | Where-Object { $_.FullName -notmatch '[\\/](temp|design-system)[\\/]' -and $_.Name -notmatch '^sample-' }
    
    foreach ($file in $htmlFiles) {
        $content = Get-Content $file.FullName -Raw
        $relPath = (Resolve-Path $file.FullName -Relative).Replace('\', '/').TrimStart('./')

        # Extract title
        $titleMatch = [regex]::Match($content, '<title>([^<]+)</title>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        $rawTitle = if ($titleMatch.Success) { $titleMatch.Groups[1].Value.Trim() } else { $file.BaseName }
        $cleanTitle = $rawTitle -replace '\s*-\s*Fast\s*Media\s*Sorter.*$', ''

        # Extract description
        $descMatch = [regex]::Match($content, '<meta\s+name=["'']description["'']\s+content=["'']([^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        $desc = if ($descMatch.Success) { $descMatch.Groups[1].Value.Trim() } else { "" }

        # Extract H2 / H3 headings
        $headings = [System.Collections.Generic.List[string]]::new()
        $hMatches = [regex]::Matches($content, '<h[23][^>]*>([^<]+)</h[23]>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        foreach ($h in $hMatches) {
            $hText = $h.Groups[1].Value.Trim()
            if (-not [string]::IsNullOrWhiteSpace($hText)) {
                $headings.Add($hText)
            }
        }

        # Only main content is searchable; repeated navigation must not dominate recipe matches.
        $mainMatch = [regex]::Match($content, '<main\b[^>]*>([\s\S]*?)</main>', 'IgnoreCase')
        $mainBody = if ($mainMatch.Success) { $mainMatch.Groups[1].Value } else { '' }
        $cleanBody = $mainBody -replace '<script[\s\S]*?</script>', ' ' `
                              -replace '<style[\s\S]*?</style>', ' ' `
                              -replace '<[^>]+>', ' '
        $cleanBody = [System.Net.WebUtility]::HtmlDecode($cleanBody) -replace '\s+', ' '
        $cleanBody = $cleanBody.Trim()

        # Determine page_id from path or manifest
        $matchedManifest = $null
        foreach ($p in $manifestPages.Values) {
            if ($p.canonical_path -and $relPath.EndsWith($p.canonical_path.TrimStart('/'))) {
                $matchedManifest = $p
                break
            }
        }

        $pageId = if ($matchedManifest) { $matchedManifest.page_id } else { $file.BaseName }
        $category = if ($matchedManifest) { $matchedManifest.category } else { "documentation" }
        $ticket = if ($matchedManifest) { $matchedManifest.ticket } else { "S2970" }

        $pageLang = if ($relPath -match '-ru\.html$') { "ru" } elseif ($relPath -match '-uk\.html$') { "uk" } else { "en" }
        $pageRecord = [ordered]@{
            page_id = $pageId
            lang = $pageLang
            title = $cleanTitle
            url = $relPath
            category = $category
            ticket = $ticket
            description = $desc
            headings = $headings.ToArray()
            body = $cleanBody
            published = $true
            keywords = ($cleanTitle + " " + $desc + " " + [string]::Join(" ", $headings)).ToLowerInvariant()
        }
        $indexedPages.Add($pageRecord)
        $null = $processedPageIds.Add($pageId)
    }
}

# 3. Add planned/unwritten pages from manifest so search finds them as planned topics
foreach ($p in $manifestPages.Values) {
    if (-not $processedPageIds.Contains($p.page_id)) {
        $plannedRecord = [ordered]@{
            page_id = $p.page_id
            title = $p.title
            url = $p.canonical_path
            category = $p.category
            ticket = $p.ticket
            description = "Planned documentation guide for $($p.title)."
            headings = @()
            published = $false
            keywords = ($p.title + " " + $p.category + " " + $p.ticket).ToLowerInvariant()
        }
        $indexedPages.Add($plannedRecord)
    }
}

# 4. Output search index JSON
$targetOut = Join-Path $repoRoot $OutputPath
$targetDir = Split-Path $targetOut -Parent
if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir -Force | Out-Null }

# Shards keep the full recipe text searchable without one large first-use JSON download.
# Each shard has a bounded UTF-8 size and can be committed through the same file-based workflow.
$chunkManifest = [System.Collections.Generic.List[object]]::new()
foreach ($locale in 'en', 'ru', 'uk') {
    $records = @($indexedPages | Where-Object { $_.lang -eq $locale } | Sort-Object url)
    $batch = [System.Collections.Generic.List[string]]::new()
    $bytes = 0
    $number = 1
    foreach ($record in $records) {
        $recordJson = ConvertTo-Json $record -Depth 6 -Compress
        $recordBytes = [Text.Encoding]::UTF8.GetByteCount($recordJson)
        if ($batch.Count -gt 0 -and $bytes + $recordBytes -gt 500000) {
            $name = 'search-pages-{0}-{1:D2}.json' -f $locale, $number
            [IO.File]::WriteAllText((Join-Path $targetDir $name), '{"pages":[' + ($batch -join ',') + ']}', [Text.UTF8Encoding]::new($false))
            $chunkManifest.Add([ordered]@{ file = $name; lang = $locale })
            $batch.Clear(); $bytes = 0; $number++
        }
        $batch.Add($recordJson); $bytes += $recordBytes
    }
    if ($batch.Count -gt 0) {
        $name = 'search-pages-{0}-{1:D2}.json' -f $locale, $number
        [IO.File]::WriteAllText((Join-Path $targetDir $name), '{"pages":[' + ($batch -join ',') + ']}', [Text.UTF8Encoding]::new($false))
        $chunkManifest.Add([ordered]@{ file = $name; lang = $locale })
    }
}
# Remove only stale files owned by this generator, not arbitrary assets.
$names = @($chunkManifest | ForEach-Object { $_.file })
Get-ChildItem -LiteralPath $targetDir -Filter 'search-pages-*.json' | Where-Object { $_.Name -notin $names } | Remove-Item
$indexPayload = [ordered]@{
    version = "2.0"
    total_pages = @($indexedPages | Where-Object { $_.published }).Count
    chunks = $chunkManifest
}
$json = ConvertTo-Json $indexPayload -Depth 6
[System.IO.File]::WriteAllText($targetOut, $json, [Text.UTF8Encoding]::new($false))
Write-Host "generate-docs-search-index: Written search index ($($indexedPages.Count) pages) to $targetOut" -ForegroundColor Green
exit 0
