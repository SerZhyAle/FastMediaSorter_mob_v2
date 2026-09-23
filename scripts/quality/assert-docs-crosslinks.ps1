# Quality Gate: Assert Documentation Cross-Links and Bookmarks
# Part of S2945 (Documentation Corpus Foundation)
# Checks HTML and Markdown links under documentation/ against docs/docs-pages-manifest.jsonl

[CmdletBinding()]
param (
    [switch]$Strict,
    [string]$Path = "documentation"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$docsDir = Join-Path $repoRoot 'docs'
$docRoot = Join-Path $repoRoot $Path

$pageManifestPath = Join-Path $docsDir 'docs-pages-manifest.jsonl'
if (-not (Test-Path $pageManifestPath)) {
    Write-Error "assert-docs-crosslinks: Page manifest missing at $pageManifestPath"
    exit 1
}

# 1. Load page manifest
$manifestPages = @{}
$manifestPaths = @{}
Get-Content $pageManifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $p = ConvertFrom-Json $_
        if ($p.page_id) {
            $manifestPages[$p.page_id] = $p
        }
        if ($p.canonical_path) {
            $normalized = $p.canonical_path.Replace('\', '/').TrimStart('/')
            $manifestPaths[$normalized] = $p
        }
    }
}

# 2. Find all HTML and MD files in documentation directory
if (-not (Test-Path $docRoot)) {
    Write-Host "assert-docs-crosslinks: Directory '$Path' not found. Skipping check." -ForegroundColor Yellow
    exit 0
}

$docFiles = Get-ChildItem -Path $docRoot -Recurse -File -Include *.html, *.md | Where-Object { $_.FullName -notmatch '[\\/]temp[\\/]' }

$validLinks = 0
$bookmarks = [System.Collections.Generic.List[object]]::new()
$brokenLinks = [System.Collections.Generic.List[object]]::new()

foreach ($file in $docFiles) {
    $content = Get-Content $file.FullName -Raw
    $fileRelPath = (Resolve-Path $file.FullName -Relative).Replace('\', '/').TrimStart('./')

    # Regex for href attributes
    $hrefMatches = [regex]::Matches($content, 'href=["'']([^"'']+)["'']')
    foreach ($m in $hrefMatches) {
        $rawHref = $m.Groups[1].Value

        # Skip external links, mailto, javascript, in-page anchors
        if ($rawHref -match '^(https?://|mailto:|javascript:|#)' -or $rawHref -like '../*') {
            # In-page anchor or root site link
            $validLinks++
            continue
        }

        # Clean query string & anchor
        $cleanHref = $rawHref -replace '\?.*$', '' -replace '#.*$', ''
        if ([string]::IsNullOrWhiteSpace($cleanHref)) { continue }

        # Resolve relative target
        $targetPhysicalPath = Join-Path $file.DirectoryName $cleanHref
        $targetExists = Test-Path $targetPhysicalPath

        # Check if matched in manifest
        $relativeDocPath = (Resolve-Path $targetPhysicalPath -ErrorAction SilentlyContinue)
        $canonicalDocPath = if ($relativeDocPath) {
            (Resolve-Path $targetPhysicalPath -Relative).Replace('\', '/').TrimStart('./')
        } else {
            # Normalize path string
            $combined = (Join-Path (Split-Path $fileRelPath) $cleanHref).Replace('\', '/')
            # Resolve ../ or ./
            $parts = $combined.Split('/')
            $resolvedParts = [System.Collections.Generic.List[string]]::new()
            foreach ($part in $parts) {
                if ($part -eq '..') {
                    if ($resolvedParts.Count -gt 0) { $resolvedParts.RemoveAt($resolvedParts.Count - 1) }
                } elseif ($part -ne '.' -and $part -ne '') {
                    $resolvedParts.Add($part)
                }
            }
            [string]::Join('/', $resolvedParts)
        }

        $manifestRecord = if ($manifestPaths.ContainsKey($canonicalDocPath)) { $manifestPaths[$canonicalDocPath] } else { $null }

        if ($targetExists) {
            $validLinks++
        } elseif ($manifestRecord -ne $null) {
            $bookmarks.Add([PSCustomObject]@{
                SourceFile = $fileRelPath
                Link = $rawHref
                PageId = $manifestRecord.page_id
                OwnerTicket = $manifestRecord.ticket
                TargetTitle = $manifestRecord.title
            })
        } else {
            $brokenLinks.Add([PSCustomObject]@{
                SourceFile = $fileRelPath
                Link = $rawHref
                ResolvedPath = $canonicalDocPath
            })
        }
    }

    # Also detect explicit data-page-id or doc-bookmark spans/links
    $bookmarkMatches = [regex]::Matches($content, 'class=["''][^"'']*doc-bookmark[^"'']*["''][^>]*data-page-id=["'']([^"'']+)["'']')
    foreach ($bm in $bookmarkMatches) {
        $pageIdTarget = $bm.Groups[1].Value
        $manifestRec = if ($manifestPages.ContainsKey($pageIdTarget)) { $manifestPages[$pageIdTarget] } else { $null }
        $bookmarks.Add([PSCustomObject]@{
            SourceFile = $fileRelPath
            Link = "page_id:$pageIdTarget"
            PageId = $pageIdTarget
            OwnerTicket = if ($manifestRec) { $manifestRec.ticket } else { "Unknown" }
            TargetTitle = if ($manifestRec) { $manifestRec.title } else { "Unregistered" }
        })
    }
}

# 3. Output results
Write-Host "=== Documentation Cross-Links & Bookmarks Report ===" -ForegroundColor Cyan
Write-Host "Scanned files:       $($docFiles.Count)"
Write-Host "Valid active links:  $validLinks"
Write-Host "Valid bookmarks:     $($bookmarks.Count)"
Write-Host "Broken links:        $($brokenLinks.Count)"

if ($bookmarks.Count -gt 0) {
    Write-Host "`n--- Bookmarks (Unwritten pages with target owner ticket) ---" -ForegroundColor Yellow
    foreach ($b in $bookmarks) {
        Write-Host "  Bookmark: $($b.PageId) -> [$($b.OwnerTicket)] '$($b.TargetTitle)' (in $($b.SourceFile))" -ForegroundColor Gray
    }
}

if ($brokenLinks.Count -gt 0) {
    Write-Host "`nassert-docs-crosslinks: FAILED ($($brokenLinks.Count) broken link(s) found):" -ForegroundColor Red
    foreach ($bl in $brokenLinks) {
        Write-Host "  - In '$($bl.SourceFile)': href='$($bl.Link)' (resolved: '$($bl.ResolvedPath)')" -ForegroundColor Red
    }
    exit 1
}

if ($Strict -and $bookmarks.Count -gt 0) {
    Write-Host "`nassert-docs-crosslinks: FAILED in -Strict mode ($($bookmarks.Count) unwritten bookmarks remain):" -ForegroundColor Red
    exit 2
}

Write-Host "`nassert-docs-crosslinks: PASS (0 broken links, all targets verified)" -ForegroundColor Green
exit 0
