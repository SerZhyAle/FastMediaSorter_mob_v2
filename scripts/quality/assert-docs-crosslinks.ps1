# Quality Gate: Assert Documentation Cross-Links and Bookmarks
# Part of S2945 (Documentation Corpus Foundation); site-address resolution by S3540.
#
# Every href/src of every page under -Path is resolved the way GitHub Pages serves it: against the
# page's own site address, and looked up in the set of addresses the Jekyll source publishes.
# A page with a front-matter `permalink:` is served at that permalink and NOT at its physical path,
# so a check against the disk alone passed links that 404 on the live site (S3540: 227 of them).
#
# Known broken targets live in -BaselinePath, one address per line. The baseline only shrinks:
#   - a broken target missing from it fails the gate;
#   - a row whose target is no longer broken fails the gate until the row is deleted.
# Targets registered in docs/docs-pages-manifest.jsonl as unwritten pages are bookmarks, non-fatal.
#
# Exit codes:
#   0 - every link resolves, or is a bookmark, or its target is baselined.
#   1 - a new broken target, a stale baseline row, or the page manifest is missing.
#   2 - -Strict and unwritten bookmarks remain.

[CmdletBinding()]
param (
    [switch]$Strict,
    [string]$Path = "documentation",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$BaselinePath = (Join-Path $PSScriptRoot 'docs-crosslinks-baseline.txt')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path $RepoRoot).Path.TrimEnd('\', '/')
$docRoot = Join-Path $repoRoot $Path
$siteBase = 'FastMediaSorter_mob_v2'
$siteHost = 'https://serzhyale.github.io'

$pageManifestPath = Join-Path $repoRoot 'docs/docs-pages-manifest.jsonl'
if (-not (Test-Path $pageManifestPath)) {
    Write-Host "assert-docs-crosslinks: FAIL - page manifest missing at $pageManifestPath" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $docRoot)) {
    Write-Host "assert-docs-crosslinks: Directory '$Path' not found. Skipping check." -ForegroundColor Yellow
    exit 0
}

$manifestPages = @{}
$manifestPaths = @{}
foreach ($line in Get-Content $pageManifestPath) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $p = ConvertFrom-Json $line
    if ($p.page_id) { $manifestPages[$p.page_id] = $p }
    if ($p.canonical_path) { $manifestPaths[$p.canonical_path.Replace('\', '/').TrimStart('/')] = $p }
}

# 1. The published address set. Jekyll's source is the repository root; _config.yml `exclude`
#    and every `_`/`.`-prefixed path are not published.
$exclude = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($n in 'node_modules', 'vendor', 'Gemfile', 'Gemfile.lock') { [void]$exclude.Add($n) }
$configPath = Join-Path $repoRoot '_config.yml'
if (Test-Path $configPath) {
    $inExclude = $false
    foreach ($line in Get-Content $configPath) {
        if ($line -match '^exclude:\s*$') { $inExclude = $true; continue }
        if ($inExclude) {
            if ($line -match '^\s+-\s+["'']?([^"''\s#]+)') { [void]$exclude.Add($Matches[1].TrimEnd('/')) }
            elseif ($line -match '^\S') { $inExclude = $false }
        }
    }
}

function Get-Permalink([string]$fullName) {
    $reader = [System.IO.StreamReader]::new($fullName)
    try {
        if ($reader.ReadLine() -notmatch '^---\s*$') { return $null }
        for ($i = 0; $i -lt 80; $i++) {
            $l = $reader.ReadLine()
            if ($null -eq $l -or $l -match '^---\s*$') { return $null }
            if ($l -match '^permalink:\s*["'']?([^"''\s]+)') { return $Matches[1] }
        }
        return $null
    }
    finally { $reader.Dispose() }
}

$addresses = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$pageAddress = @{}
$topEntries = Get-ChildItem -LiteralPath $repoRoot -Force | Where-Object { $_.Name -notmatch '^[._]' -and -not $exclude.Contains($_.Name) }
foreach ($top in $topEntries) {
    $files = if ($top.PSIsContainer) {
        Get-ChildItem -LiteralPath $top.FullName -Recurse -File -Force |
            Where-Object { $_.FullName.Substring($repoRoot.Length) -notmatch '[\\/][._]' }
    } else { @($top) }
    foreach ($f in $files) {
        $rel = $f.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
        $isPage = $f.Extension -in '.md', '.markdown', '.html'
        $permalink = if ($isPage) { Get-Permalink $f.FullName } else { $null }
        $address = if ($permalink) { $permalink.TrimStart('/') }
        elseif ($f.Extension -in '.md', '.markdown') { $rel -replace '\.(md|markdown)$', '.html' }
        else { $rel }
        if (-not $permalink -and $address -match '(^|/)README\.html$') { [void]$addresses.Add(($address -replace 'README\.html$', 'index.html')) }
        if ($address -eq '' -or $address.EndsWith('/')) { $address += 'index.html' }
        [void]$addresses.Add($address)
        $pageAddress[$rel] = $address
    }
}

function Test-Address([string]$target) {
    return $addresses.Contains($target) -or $addresses.Contains("$target/index.html") -or $addresses.Contains("$target.html")
}

# 2. Resolve every link of every page under -Path against the page's own address.
$docFiles = Get-ChildItem -Path $docRoot -Recurse -File -Include *.html, *.md |
    Where-Object { $_.FullName.Substring($docRoot.Length) -notmatch '[\\/]temp[\\/]' }
$validLinks = 0
$bookmarks = [System.Collections.Generic.List[object]]::new()
$broken = [ordered]@{}
$resolved = @{}
$externalPattern = '^(https?:|mailto:|javascript:|tel:|data:|#|\{\{|\{%)'
$sitePrefix = [regex]::Escape("$siteHost/$siteBase")

foreach ($file in $docFiles) {
    $fileRelPath = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
    $own = $pageAddress[$fileRelPath]
    if (-not $own) { $own = $fileRelPath }
    $base = if ($own.Contains('/')) { $own.Substring(0, $own.LastIndexOf('/') + 1) } else { '' }
    $content = [System.IO.File]::ReadAllText($file.FullName)

    foreach ($m in [regex]::Matches($content, '(?:href|src)=["'']([^"'']+)["'']')) {
        $rawHref = $m.Groups[1].Value
        $key = "$base|$rawHref"
        if (-not $resolved.ContainsKey($key)) {
            $h = $rawHref
            if ($h -match "^$sitePrefix/?(.*)$") { $h = '/' + $Matches[1] }
            elseif ($h -match $externalPattern) { $resolved[$key] = ''; $validLinks++; continue }
            $h = $h -replace '[?#].*$', ''
            if (-not $h) { $resolved[$key] = ''; $validLinks++; continue }
            $joined = if ($h.StartsWith('/')) { ($h -replace "^/$siteBase(/|$)", '/').TrimStart('/') } else { $base + $h }
            $parts = [System.Collections.Generic.List[string]]::new()
            foreach ($seg in $joined.Split('/')) {
                if ($seg -eq '..') { if ($parts.Count -gt 0) { $parts.RemoveAt($parts.Count - 1) } }
                elseif ($seg -ne '.') { $parts.Add($seg) }
            }
            $target = [Uri]::UnescapeDataString([string]::Join('/', $parts))
            if ($target -eq '' -or $target.EndsWith('/')) { $target += 'index.html' }
            $resolved[$key] = if (Test-Address $target) { '' } else { $target }
        }
        $target = $resolved[$key]
        if (-not $target) { $validLinks++; continue }

        if ($manifestPaths.ContainsKey($target)) {
            $rec = $manifestPaths[$target]
            $bookmarks.Add([PSCustomObject]@{ SourceFile = $fileRelPath; Link = $rawHref; PageId = $rec.page_id; OwnerTicket = $rec.ticket; TargetTitle = $rec.title })
            continue
        }
        if (-not $broken.Contains($target)) { $broken[$target] = [System.Collections.Generic.List[string]]::new() }
        $broken[$target].Add("$fileRelPath -> $rawHref")
    }

    foreach ($bm in [regex]::Matches($content, 'class=["''][^"'']*doc-bookmark[^"'']*["''][^>]*data-page-id=["'']([^"'']+)["'']')) {
        $pageIdTarget = $bm.Groups[1].Value
        $rec = if ($manifestPages.ContainsKey($pageIdTarget)) { $manifestPages[$pageIdTarget] } else { $null }
        $bookmarks.Add([PSCustomObject]@{
                SourceFile = $fileRelPath; Link = "page_id:$pageIdTarget"; PageId = $pageIdTarget
                OwnerTicket = if ($rec) { $rec.ticket } else { 'Unknown' }
                TargetTitle = if ($rec) { $rec.title } else { 'Unregistered' }
            })
    }
}

# 3. Judge broken targets against the shrink-only baseline.
$baseline = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if (Test-Path $BaselinePath) {
    foreach ($line in Get-Content $BaselinePath) {
        $entry = ($line -replace '#.*$', '').Trim()
        if ($entry) { [void]$baseline.Add($entry.TrimStart('/')) }
    }
}
$newBroken = @($broken.Keys | Where-Object { -not $baseline.Contains($_) })
$knownBroken = @($broken.Keys | Where-Object { $baseline.Contains($_) })
$stale = @($baseline | Where-Object { -not $broken.Contains($_) })
$brokenRefs = 0
foreach ($k in $broken.Keys) { $brokenRefs += $broken[$k].Count }

Write-Host "=== Documentation Cross-Links & Bookmarks Report ===" -ForegroundColor Cyan
Write-Host "Scanned files:       $($docFiles.Count)"
Write-Host "Site addresses:      $($addresses.Count)"
Write-Host "Valid active links:  $validLinks"
Write-Host "Valid bookmarks:     $($bookmarks.Count)"
Write-Host "Broken targets:      $($broken.Count) ($brokenRefs reference(s)); baselined $($knownBroken.Count), new $($newBroken.Count)"

if ($bookmarks.Count -gt 0) {
    Write-Host "`n--- Bookmarks (Unwritten pages with target owner ticket) ---" -ForegroundColor Yellow
    foreach ($b in $bookmarks) {
        Write-Host "  Bookmark: $($b.PageId) -> [$($b.OwnerTicket)] '$($b.TargetTitle)' (in $($b.SourceFile))" -ForegroundColor Gray
    }
}
if ($knownBroken.Count -gt 0) {
    Write-Host "`n--- Known broken targets (baselined in $([IO.Path]::GetFileName($BaselinePath))) ---" -ForegroundColor Yellow
    foreach ($t in $knownBroken) { Write-Host "  $t ($($broken[$t].Count) reference(s))" -ForegroundColor Gray }
}

$failed = $false
if ($newBroken.Count -gt 0) {
    $failed = $true
    Write-Host "`nassert-docs-crosslinks: FAILED ($($newBroken.Count) broken target(s) not in the baseline):" -ForegroundColor Red
    foreach ($t in $newBroken) {
        Write-Host "  - '$t' is not a site address; referenced by:" -ForegroundColor Red
        foreach ($ref in ($broken[$t] | Select-Object -First 5)) { Write-Host "      $ref" -ForegroundColor Red }
        if ($broken[$t].Count -gt 5) { Write-Host "      .. and $($broken[$t].Count - 5) more" -ForegroundColor Red }
    }
    Write-Host "  Fix the link or the target's permalink. A page is served at its front-matter permalink, not at its file path." -ForegroundColor Red
}
if ($stale.Count -gt 0) {
    $failed = $true
    Write-Host "`nassert-docs-crosslinks: FAILED ($($stale.Count) baseline row(s) no longer broken - delete them from $BaselinePath):" -ForegroundColor Red
    foreach ($t in $stale) { Write-Host "  - $t" -ForegroundColor Red }
}
if ($failed) { exit 1 }

if ($Strict -and $bookmarks.Count -gt 0) {
    Write-Host "`nassert-docs-crosslinks: FAILED in -Strict mode ($($bookmarks.Count) unwritten bookmarks remain):" -ForegroundColor Red
    exit 2
}

Write-Host "`nassert-docs-crosslinks: PASS (0 new broken targets; $($knownBroken.Count) baselined)" -ForegroundColor Green
exit 0
