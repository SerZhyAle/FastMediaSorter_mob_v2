# Quality Gate: Assert Documentation Screenshots and Image Bookmarks
# Part of S2977 (Documentation Screenshot Kit)
# Scans documentation/ for <img> tags and doc-img-bookmark placeholders.

[CmdletBinding()]
param (
    [switch]$Strict,
    [string]$Path = "documentation"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$docRoot = Join-Path $repoRoot $Path

if (-not (Test-Path $docRoot)) {
    Write-Host "assert-docs-screenshots: Directory '$Path' not found. Skipping check." -ForegroundColor Yellow
    exit 0
}

$docFiles = Get-ChildItem -Path $docRoot -Recurse -File -Include *.html, *.md | Where-Object { $_.FullName -notmatch '[\\/]temp[\\/]' }

$validImages = 0
$missingImages = [System.Collections.Generic.List[object]]::new()
$emptyAltImages = [System.Collections.Generic.List[object]]::new()
$imageBookmarks = [System.Collections.Generic.List[object]]::new()

foreach ($file in $docFiles) {
    $content = Get-Content $file.FullName -Raw
    $fileRelPath = (Resolve-Path $file.FullName -Relative).Replace('\', '/').TrimStart('./')

    # 1. Check <img> elements
    $imgMatches = [regex]::Matches($content, '<img\s+([^>]+)>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($m in $imgMatches) {
        $imgAttrs = $m.Groups[1].Value

        # Extract src
        $srcMatch = [regex]::Match($imgAttrs, 'src=["'']([^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $srcMatch.Success) { continue }
        $src = $srcMatch.Groups[1].Value

        # Skip external or data URIs
        if ($src -match '^(https?://|data:)') {
            $validImages++
            continue
        }

        # Resolve physical target
        $cleanSrc = $src -replace '\?.*$', '' -replace '#.*$', ''
        $targetPhysicalPath = Join-Path $file.DirectoryName $cleanSrc

        if (Test-Path $targetPhysicalPath) {
            $validImages++
        } else {
            $missingImages.Add([PSCustomObject]@{
                SourceFile = $fileRelPath
                Src = $src
                ExpectedPath = (Resolve-Path $targetPhysicalPath -ErrorAction SilentlyContinue)
            })
        }

        # Extract alt
        $altMatch = [regex]::Match($imgAttrs, 'alt=["'']([^"'']*)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $altMatch.Success -or [string]::IsNullOrWhiteSpace($altMatch.Groups[1].Value)) {
            $emptyAltImages.Add([PSCustomObject]@{
                SourceFile = $fileRelPath
                Src = $src
            })
        }
    }

    # 2. Check <div class="doc-img-bookmark" ...> elements
    $bmMatches = [regex]::Matches($content, '<div\s+[^>]*class=["''][^"'']*(?<=[\s"''])doc-img-bookmark(?=[\s"''])[^"'']*["''][^>]*>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)
    foreach ($m in $bmMatches) {
        $tag = $m.Value
        $shotIdMatch = [regex]::Match($tag, 'data-shot-id=["'']([^"'']+)["'']')
        $profileMatch = [regex]::Match($tag, 'data-device-profile=["'']([^"'']+)["'']')

        $shotIdVal = if ($shotIdMatch.Success) { $shotIdMatch.Groups[1].Value } else { "unspecified" }
        $profileVal = if ($profileMatch.Success) { $profileMatch.Groups[1].Value } else { "phone" }

        $imageBookmarks.Add([PSCustomObject]@{
            SourceFile = $fileRelPath
            ShotId = $shotIdVal
            Profile = $profileVal
        })
    }
}

# 3. Output results
Write-Host "=== Documentation Screenshots & Image Bookmarks Report ===" -ForegroundColor Cyan
Write-Host "Scanned files:          $($docFiles.Count)"
Write-Host "Valid image files:      $validImages"
Write-Host "Missing image files:    $($missingImages.Count)"
Write-Host "Empty/missing alt-text: $($emptyAltImages.Count)"
Write-Host "Pending image bookmarks: $($imageBookmarks.Count)"

if ($imageBookmarks.Count -gt 0) {
    Write-Host "`n--- Pending Image Bookmarks (To be captured in Phase B) ---" -ForegroundColor Yellow
    foreach ($bm in $imageBookmarks) {
        Write-Host "  Image Bookmark: $($bm.ShotId) (Profile: $($bm.Profile), in $($bm.SourceFile))" -ForegroundColor Gray
    }
}

$hasErrors = $false

if ($missingImages.Count -gt 0) {
    Write-Host "`nassert-docs-screenshots: Missing image files detected:" -ForegroundColor Red
    foreach ($mi in $missingImages) {
        Write-Host "  - In '$($mi.SourceFile)': src='$($mi.Src)' not found" -ForegroundColor Red
    }
    $hasErrors = $true
}

if ($emptyAltImages.Count -gt 0) {
    Write-Host "`nassert-docs-screenshots: Images missing descriptive alt-text:" -ForegroundColor Red
    foreach ($ea in $emptyAltImages) {
        Write-Host "  - In '$($ea.SourceFile)': src='$($ea.Src)'" -ForegroundColor Red
    }
    $hasErrors = $true
}

if ($Strict -and $imageBookmarks.Count -gt 0) {
    Write-Host "`nassert-docs-screenshots: FAILED in -Strict mode ($($imageBookmarks.Count) uncaptured image bookmarks remain):" -ForegroundColor Red
    exit 2
}

if ($hasErrors) {
    Write-Host "`nassert-docs-screenshots: FAILED" -ForegroundColor Red
    exit 1
}

Write-Host "`nassert-docs-screenshots: PASS (All referenced images exist, alt-texts valid)" -ForegroundColor Green
exit 0
