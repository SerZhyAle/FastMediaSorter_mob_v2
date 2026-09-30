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
    $htmlFiles = Get-ChildItem -Path $docRoot -Recurse -File -Filter *.html | Where-Object { $_.FullName -notmatch '[\\/]temp[\\/]' }
    
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

        # Extract text snippets for keyword indexing
        $cleanBody = $content -replace '<script[\s\S]*?</script>', ' ' `
                              -replace '<style[\s\S]*?</style>', ' ' `
                              -replace '<[^>]+>', ' ' `
                              -replace '&[a-z]+;', ' ' `
                              -replace '\s+', ' '
        
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

$indexPayload = [ordered]@{
    version = "1.0"
    generated_at = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
    total_pages = $indexedPages.Count
    pages = $indexedPages
}

$json = ConvertTo-Json $indexPayload -Depth 6
[System.IO.File]::WriteAllText($targetOut, $json, [System.Text.Encoding]::UTF8)
Write-Host "generate-docs-search-index: Written search index ($($indexedPages.Count) pages) to $targetOut" -ForegroundColor Green
exit 0
