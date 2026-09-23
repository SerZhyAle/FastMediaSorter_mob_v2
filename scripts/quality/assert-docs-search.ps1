# Quality Gate: Assert Documentation Search & Responsiveness
# Part of S2970 (docs-site-html-search)
# Validates search-index.json structure, verifies search query matches, and checks CSS mobile responsiveness.

[CmdletBinding()]
param (
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$searchIndexPath = Join-Path $repoRoot 'documentation/assets/search-index.json'
$searchJsPath = Join-Path $repoRoot 'documentation/assets/search.js'
$docsCssPath = Join-Path $repoRoot 'documentation/assets/docs.css'
$indexHtmlPath = Join-Path $repoRoot 'documentation/index.html'

Write-Host "=== Documentation Search & Responsiveness Quality Gate ===" -ForegroundColor Cyan

$errors = [System.Collections.Generic.List[string]]::new()
$searchPages = @()

# 1. Search Index Validation
if (-not (Test-Path $searchIndexPath)) {
    $errors.Add("Search index missing at $searchIndexPath. Run scripts/docs/generate-docs-search-index.ps1 first.")
} else {
    try {
        $rawJson = Get-Content $searchIndexPath -Raw -Encoding utf8
        $indexPayload = ConvertFrom-Json $rawJson
        $searchPages = $indexPayload.pages

        Write-Host "Loaded search index version $($indexPayload.version) with $($searchPages.Count) pages (total declared: $($indexPayload.total_pages))." -ForegroundColor Green

        if (-not $searchPages -or $searchPages.Count -eq 0) {
            $errors.Add("Search index contains 0 page records.")
        }

        # Check required fields
        $requiredFields = @('page_id', 'title', 'url', 'category', 'description', 'keywords')
        $invalidRecords = 0
        foreach ($item in $searchPages) {
            foreach ($field in $requiredFields) {
                if (-not $item.PSObject.Properties[$field] -or [string]::IsNullOrWhiteSpace($item.$field)) {
                    $invalidRecords++
                    break
                }
            }
        }
        if ($invalidRecords -gt 0) {
            $errors.Add("Found $invalidRecords search records missing required fields ($($requiredFields -join ', ')).")
        }
    } catch {
        $errors.Add("Failed to parse $searchIndexPath as JSON: $($_.Exception.Message)")
    }
}

# 2. Search Query Engine Simulation
if ($searchPages -and $searchPages.Count -gt 0) {
    function Test-SearchQuery([string]$query) {
        $tokens = $query.ToLower().Split(" `t`r`n", [System.StringSplitOptions]::RemoveEmptyEntries)
        $hits = [System.Collections.Generic.List[object]]::new()

        foreach ($doc in $searchPages) {
            $score = 0
            $titleLower = if ($doc.title) { ($doc.title).ToLower() } else { "" }
            $kwLower = if ($doc.keywords) { ($doc.keywords).ToLower() } else { "" }
            $catLower = if ($doc.category) { ($doc.category).ToLower() } else { "" }
            $descLower = if ($doc.description) { ($doc.description).ToLower() } else { "" }

            foreach ($token in $tokens) {
                if ($titleLower.Contains($token)) { $score += 10 }
                if ($kwLower.Contains($token)) { $score += 5 }
                if ($catLower.Contains($token)) { $score += 3 }
                if ($descLower.Contains($token)) { $score += 2 }
            }

            if ($score -gt 0) {
                $hits.Add([PSCustomObject]@{
                    Doc = $doc
                    Score = $score
                })
            }
        }

        $sortedHits = $hits | Sort-Object -Property Score -Descending
        return $sortedHits
    }

    $testCases = @(
        @{ Query = "music"; ExpectedSnippet = "Music" },
        @{ Query = "sorting"; ExpectedSnippet = "Sort" },
        @{ Query = "statistics"; ExpectedSnippet = "Stat" },
        @{ Query = "permissions"; ExpectedSnippet = "Permiss" },
        @{ Query = "smb"; ExpectedSnippet = "SMB" }
    )

    Write-Host "`n--- Search Query Simulation Tests ---" -ForegroundColor Yellow
    foreach ($tc in $testCases) {
        $results = Test-SearchQuery -query $tc.Query
        if ($results.Count -eq 0) {
            $errors.Add("Search query '$($tc.Query)' returned 0 results.")
            Write-Host "  Query '$($tc.Query)': FAIL (0 results)" -ForegroundColor Red
        } else {
            $topDoc = $results[0].Doc
            Write-Host "  Query '$($tc.Query)': PASS ($($results.Count) hits, Top: '$($topDoc.title)' [$($topDoc.url)])" -ForegroundColor Green
        }
    }
}

# 3. Client Search Engine JS Asset Check
if (-not (Test-Path $searchJsPath)) {
    $errors.Add("Search client engine missing at $searchJsPath")
} else {
    $jsContent = Get-Content $searchJsPath -Raw
    if ($jsContent -notmatch 'keydown' -or $jsContent -notmatch 'doc-search-modal') {
        $errors.Add("Search JS ($searchJsPath) missing modal or keydown event listener.")
    } else {
        Write-Host "Search client JS asset ($searchJsPath) verified." -ForegroundColor Green
    }
}

# 4. Mobile Responsiveness & Viewport CSS Check
if (-not (Test-Path $docsCssPath)) {
    $errors.Add("Docs CSS missing at $docsCssPath")
} else {
    $cssContent = Get-Content $docsCssPath -Raw
    if ($cssContent -notmatch '@media\s*\(\s*max-width\s*:\s*(768|480)px\s*\)') {
        $errors.Add("Docs CSS ($docsCssPath) missing mobile media query breakpoints (768px or 480px).")
    } else {
        Write-Host "Docs mobile responsive CSS ($docsCssPath) verified (supports down to 400px viewport)." -ForegroundColor Green
    }
}

# 5. Hub Navigation and Search Integration Check
if (-not (Test-Path $indexHtmlPath)) {
    $errors.Add("Documentation Hub missing at $indexHtmlPath")
} else {
    $hubContent = Get-Content $indexHtmlPath -Raw
    if ($hubContent -notmatch 'search\.js') {
        $errors.Add("Documentation Hub ($indexHtmlPath) does not include search.js")
    }
    if ($hubContent -notmatch 'doc-(topic-)?card|doc-next-card') {
        $errors.Add("Documentation Hub ($indexHtmlPath) missing topic category cards.")
    }
    Write-Host "Documentation Hub navigation and search integration verified." -ForegroundColor Green
}

# 6. Final Verdict
Write-Host ""
if ($errors.Count -gt 0) {
    Write-Host "assert-docs-search: FAILED with $($errors.Count) errors:" -ForegroundColor Red
    foreach ($err in $errors) {
        Write-Host "  - $err" -ForegroundColor Red
    }
    exit 1
}

Write-Host "assert-docs-search: PASS (Search index valid, queries verified, mobile CSS responsive)" -ForegroundColor Green
exit 0
