<#
.SYNOPSIS
    Builds the downloadable documentation PDF from the published documentation corpus (S2971).

.DESCRIPTION
    Reads the published pages of docs/docs-pages-manifest.jsonl in manifest order, takes the
    <main class="doc-content"> block of each generated page under documentation/, and joins them
    into one HTML book under temp/docs-pdf/: a cover, a table of contents grouped by corpus
    category, then one section per page starting on a new sheet.

    The book is assembled from the generated HTML, not from docs/content/, so the PDF shows exactly
    what the site shows and inherits the @media print rules of documentation/assets/docs.css (S2976).

    Inside the book:
      - every id is prefixed per page, so the anchors of 80+ pages cannot collide;
      - a link to another corpus page becomes an in-book anchor, which the PDF keeps clickable
        offline;
      - any other relative link becomes an absolute URL of the published site, because the PDF
        has no site next to it;
      - image sources become file:/// URIs so the browser embeds them.

    A headless Chromium browser (Chrome, else Edge; -BrowserPath or FMS_PDF_BROWSER override)
    prints the book with --generate-pdf-document-outline, so the PDF carries bookmarks built from
    the heading hierarchy. The PDF is written next to the site assets and replaced only after the
    browser produced a valid file.

    Runs from /skill-release before the docs commit, and by hand after a corpus change.

.PARAMETER OutputPath
    Repo-relative or absolute PDF path. Default documentation/assets/FastMediaSorter-Documentation.pdf.

.PARAMETER BrowserPath
    Explicit Chromium-family executable. Default: FMS_PDF_BROWSER, then Chrome, then Edge.

.PARAMETER WorkDir
    Scratch directory for the book HTML and the throwaway browser profile. Default temp/docs-pdf.

.PARAMETER TimeoutSeconds
    How long the browser may print before it is killed. Default 300.

.OUTPUTS
    Exit codes:
      0 - the PDF was written.
      1 - the build failed: no page could be read, the browser failed or timed out, or it
          produced no valid PDF.
      2 - could not run: the page manifest is missing or no Chromium-family browser was found.
#>

[CmdletBinding()]
param(
    [string]$OutputPath = 'documentation/assets/FastMediaSorter-Documentation.pdf',
    [string]$BrowserPath,
    [string]$WorkDir = 'temp/docs-pdf',
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$docRoot = Join-Path $repoRoot 'documentation'
$manifestPath = Join-Path $repoRoot 'docs/docs-pages-manifest.jsonl'
$siteBaseUrl = 'https://serzhyale.github.io/FastMediaSorter_mob_v2/'

function Resolve-RepoPath([string]$path) {
    if ([IO.Path]::IsPathRooted($path)) { return [IO.Path]::GetFullPath($path) }
    return [IO.Path]::GetFullPath((Join-Path $repoRoot $path))
}

function Get-PathKey([string]$fullPath) {
    return ([IO.Path]::GetFullPath($fullPath)).Replace('\', '/').ToLowerInvariant()
}

function Find-Browser {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($BrowserPath) { $candidates.Add($BrowserPath) }
    if ($env:FMS_PDF_BROWSER) { $candidates.Add($env:FMS_PDF_BROWSER) }
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if (-not $base) { continue }
        $candidates.Add((Join-Path $base 'Google/Chrome/Application/chrome.exe'))
        $candidates.Add((Join-Path $base 'Microsoft/Edge/Application/msedge.exe'))
    }
    foreach ($name in @('google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser', 'microsoft-edge')) {
        $cmd = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cmd) { $candidates.Add($cmd.Source) }
    }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c -PathType Leaf)) { return (Resolve-Path -LiteralPath $c).Path }
    }
    return $null
}

$categoryTitles = @{
    'getting-started'                 = 'Getting Started'
    'flavors-and-capabilities'        = 'Editions and Capabilities'
    'browsing-and-sorting'            = 'Browsing and Sorting'
    'sources-destinations-operations' = 'Sources, Destinations and File Operations'
    'network-and-cloud'               = 'Network Folders and Clouds'
    'video-and-player'                = 'Video and the Player'
    'images-audio-slideshow'          = 'Images, Audio and Slideshows'
    'documents-and-editor'            = 'Documents and the Text Editor'
    'streams-catalog'                 = 'Internet Streams: Channel Catalog'
    'streams-playback'                = 'Internet Streams: Playback'
    'camera-and-recording'            = 'Camera and Screen Recording'
    'ocr-drawing-sharing'             = 'Text Recognition, Drawing and Sharing'
    'launcher-desktop'                = 'Launcher: Desktop'
    'launcher-widgets'                = 'Launcher: Gadgets and Widgets'
    'launcher-taskbar'                = 'Launcher: Taskbar, Menus and Gestures'
    'programs-and-diagnostics'        = 'Programs, Statistics and Diagnostics'
    'settings'                        = 'Settings'
    'general-and-tv'                  = 'Language, Backup, Keyboard and TV'
    'wear-setup-sync'                 = 'Watch: Setup, Companion and Sync'
    'wear-media-streams'              = 'Watch: Media, Streams and Files'
    'wear-apps-health'                = 'Watch: Mini Apps and Health'
    'vr-openxr'                       = 'VR and OpenXR'
}

function Get-CategoryTitle([string]$slug) {
    if ($categoryTitles.ContainsKey($slug)) { return $categoryTitles[$slug] }
    return (Get-Culture).TextInfo.ToTitleCase(($slug -replace '-', ' '))
}

function ConvertTo-HtmlText([string]$text) {
    return [System.Net.WebUtility]::HtmlEncode($text)
}

if (-not (Test-Path -LiteralPath $manifestPath)) {
    Write-Host "build-docs-pdf: CANNOT VERIFY - page manifest missing at $manifestPath" -ForegroundColor Red
    exit 2
}

$browser = Find-Browser
if (-not $browser) {
    Write-Host 'build-docs-pdf: CANNOT VERIFY - no Chromium-family browser found (Chrome or Edge); pass -BrowserPath or set FMS_PDF_BROWSER' -ForegroundColor Red
    exit 2
}

# 1. Published pages in manifest order; the anchor map must be complete before any link is rewritten.
$pages = [System.Collections.Generic.List[object]]::new()
$anchorByPath = @{}
foreach ($line in Get-Content -LiteralPath $manifestPath -Encoding utf8) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $record = $line | ConvertFrom-Json
    if (-not $record.is_published) { continue }
    $fullPath = Resolve-RepoPath $record.canonical_path
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        Write-Host "build-docs-pdf: skipped $($record.page_id) - $($record.canonical_path) does not exist" -ForegroundColor Yellow
        continue
    }
    $anchor = 'p-' + ($record.page_id -replace '[^A-Za-z0-9_-]', '-')
    $anchorByPath[(Get-PathKey $fullPath)] = $anchor
    $pages.Add([pscustomobject]@{
            Id       = $record.page_id
            Title    = $record.title
            Category = $record.category
            Path     = $fullPath
            Anchor   = $anchor
        })
}

if ($pages.Count -eq 0) {
    Write-Host 'build-docs-pdf: FAIL - the manifest lists no published page that exists on disk' -ForegroundColor Red
    exit 1
}

$mainRegex = [regex]::new('(?s)<main\b[^>]*\bclass="[^"]*\bdoc-content\b[^"]*"[^>]*>(.*?)</main>')
$scriptRegex = [regex]::new('(?is)<script\b.*?</script>')
# The lookbehind keeps data-shot-id / data-src style attributes out of the rewrite.
$idRegex = [regex]::new('(?<![-\w])(id|for)="([^"]+)"')
$ariaRefRegex = [regex]::new('(?<![-\w])(aria-labelledby|aria-describedby|aria-controls)="([^"]+)"')
$hrefRegex = [regex]::new('(?<![-\w])href="([^"]*)"')
$srcRegex = [regex]::new('(?<![-\w])src="([^"]*)"')
$headingRegex = [regex]::new('(?i)<(/?)h([1-6])\b')
$schemeRegex = [regex]::new('^(?:[a-z][a-z0-9+.-]*:|//)', 'IgnoreCase')

function Convert-PageBody([string]$body, [object]$page) {
    $prefix = $page.Anchor
    $pageDir = Split-Path -Parent $page.Path

    $body = $scriptRegex.Replace($body, '')
    $body = $idRegex.Replace($body, { param($m) "$($m.Groups[1].Value)=`"$prefix--$($m.Groups[2].Value)`"" })
    $body = $ariaRefRegex.Replace($body, {
            param($m)
            $refs = ($m.Groups[2].Value -split '\s+' | Where-Object { $_ } | ForEach-Object { "$prefix--$_" }) -join ' '
            "$($m.Groups[1].Value)=`"$refs`""
        })

    $body = $hrefRegex.Replace($body, {
            param($m)
            $target = [System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value)
            if ($target.StartsWith('#')) {
                $fragment = $target.Substring(1)
                if ($fragment) { return "href=`"#$prefix--$fragment`"" }
                return "href=`"#$prefix`""
            }
            if ($schemeRegex.IsMatch($target) -or [string]::IsNullOrEmpty($target)) { return $m.Value }

            $fragment = ''
            $hashAt = $target.IndexOf('#')
            if ($hashAt -ge 0) { $fragment = $target.Substring($hashAt + 1); $target = $target.Substring(0, $hashAt) }
            $query = ''
            $queryAt = $target.IndexOf('?')
            if ($queryAt -ge 0) { $query = $target.Substring($queryAt); $target = $target.Substring(0, $queryAt) }
            $resolved = if ($target) { [IO.Path]::GetFullPath((Join-Path $pageDir $target)) } else { $page.Path }

            $key = Get-PathKey $resolved
            if ($anchorByPath.ContainsKey($key)) {
                $targetAnchor = $anchorByPath[$key]
                if ($fragment) { return "href=`"#$targetAnchor--$fragment`"" }
                return "href=`"#$targetAnchor`""
            }
            $rootKey = (Get-PathKey $repoRoot).TrimEnd('/') + '/'
            if ($key.StartsWith($rootKey)) {
                $relative = [IO.Path]::GetRelativePath($repoRoot, $resolved).Replace('\', '/')
                $url = $siteBaseUrl + $relative + $query
                if ($fragment) { $url += "#$fragment" }
                return "href=`"$([System.Net.WebUtility]::HtmlEncode($url))`""
            }
            return $m.Value
        })

    $body = $srcRegex.Replace($body, {
            param($m)
            $source = [System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value)
            if ($schemeRegex.IsMatch($source) -or [string]::IsNullOrEmpty($source)) { return $m.Value }
            $resolved = [IO.Path]::GetFullPath((Join-Path $pageDir $source))
            return "src=`"$(([Uri]$resolved).AbsoluteUri)`""
        })

    # Category headings take level 1 in the book, so each page moves one level down; the PDF
    # outline is built from this hierarchy.
    $body = $headingRegex.Replace($body, {
            param($m)
            $level = [Math]::Min(6, [int]$m.Groups[2].Value + 1)
            "<$($m.Groups[1].Value)h$level"
        })
    return $body
}

# 2. Assemble the book.
$sections = [System.Text.StringBuilder]::new()
$toc = [System.Text.StringBuilder]::new()
$categoryOrder = [System.Collections.Generic.List[string]]::new()
foreach ($p in $pages) { if (-not $categoryOrder.Contains($p.Category)) { $categoryOrder.Add($p.Category) } }

$built = 0
foreach ($category in $categoryOrder) {
    $categoryAnchor = 'c-' + ($category -replace '[^A-Za-z0-9_-]', '-')
    $categoryTitle = ConvertTo-HtmlText (Get-CategoryTitle $category)
    $categoryPages = @($pages | Where-Object { $_.Category -eq $category })

    [void]$toc.AppendLine("<li class=`"pdf-toc-category`"><a href=`"#$categoryAnchor`">$categoryTitle</a><ol>")
    [void]$sections.AppendLine("<section class=`"pdf-category`" id=`"$categoryAnchor`"><h1 class=`"pdf-category-title`">$categoryTitle</h1>")

    foreach ($page in $categoryPages) {
        $raw = Get-Content -LiteralPath $page.Path -Raw -Encoding utf8
        $match = $mainRegex.Match($raw)
        if (-not $match.Success) {
            Write-Host "build-docs-pdf: skipped $($page.Id) - no <main class=`"doc-content`"> block" -ForegroundColor Yellow
            continue
        }
        $title = ConvertTo-HtmlText $page.Title
        [void]$toc.AppendLine("<li><a href=`"#$($page.Anchor)`">$title</a></li>")
        [void]$sections.AppendLine("<article class=`"pdf-page doc-content`" id=`"$($page.Anchor)`">")
        [void]$sections.AppendLine((Convert-PageBody $match.Groups[1].Value $page))
        [void]$sections.AppendLine('</article>')
        $built++
    }
    [void]$toc.AppendLine('</ol></li>')
    [void]$sections.AppendLine('</section>')
}

if ($built -eq 0) {
    Write-Host 'build-docs-pdf: FAIL - no page carried a doc-content block' -ForegroundColor Red
    exit 1
}

$siteCss = ([Uri](Join-Path $repoRoot 'styles.css')).AbsoluteUri
$docsCss = ([Uri](Join-Path $docRoot 'assets/docs.css')).AbsoluteUri
$builtOn = (Get-Date).ToString('yyyy-MM-dd')

$book = @"
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Fast Media Sorter Documentation</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Plus+Jakarta+Sans:wght@300;400;500;600;700&display=swap">
<link rel="stylesheet" href="$siteCss">
<link rel="stylesheet" href="$docsCss">
<style>
@page { size: A4; margin: 16mm 14mm; }
html, body { background: #fff !important; color: #000 !important; }
body { margin: 0; }
.pdf-cover { break-after: page; padding-top: 35vh; text-align: center; }
.pdf-cover-title { font-size: 2.4rem; font-weight: 800; margin: 0 0 0.75rem; }
.pdf-cover-sub { font-size: 1.1rem; margin: 0.25rem 0; }
.pdf-toc { break-after: page; }
.pdf-toc ol { list-style: none; padding-left: 0; }
.pdf-toc ol ol { padding-left: 1.25rem; margin: 0.25rem 0 0.75rem; }
.pdf-toc a { color: #000; text-decoration: none; }
.pdf-toc-category > a { font-weight: 700; }
.pdf-category-title { break-before: page; }
.pdf-page { break-before: page; max-width: 100% !important; padding: 0 !important; }
.pdf-category-title + .pdf-page { break-before: avoid; }
.pdf-page a { color: #0b5d3b; }
.doc-img-bookmark { border: 1px dashed #999; padding: 0.5rem 0.75rem; margin: 0.75rem 0; break-inside: avoid; }
.doc-img-bookmark-meta { display: none; }
img { max-width: 100%; height: auto; break-inside: avoid; }
</style>
</head>
<body class="doc-body">
<div class="pdf-cover">
<p class="pdf-cover-title">Fast Media Sorter Documentation</p>
<p class="pdf-cover-sub">Step-by-step guides for every feature</p>
<p class="pdf-cover-sub">$($siteBaseUrl)documentation/</p>
<p class="pdf-cover-sub">Built $builtOn</p>
</div>
<nav class="pdf-toc" aria-label="Contents">
<h1 id="contents">Contents</h1>
<ol>
$($toc.ToString())
</ol>
</nav>
$($sections.ToString())
</body>
</html>
"@

$workFull = Resolve-RepoPath $WorkDir
New-Item -ItemType Directory -Force -Path $workFull | Out-Null
$bookPath = Join-Path $workFull 'book.html'
[IO.File]::WriteAllText($bookPath, $book, [Text.UTF8Encoding]::new($false))

# 3. Print. The throwaway profile keeps the headless run away from the owner's open browser.
$outputFull = Resolve-RepoPath $OutputPath
$stagingPdf = Join-Path $workFull 'book.pdf'
if (Test-Path -LiteralPath $stagingPdf) { Remove-Item -LiteralPath $stagingPdf -Force }
$profileDir = Join-Path $workFull 'profile'

$browserArgs = @(
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--disable-extensions', "--user-data-dir=`"$profileDir`"",
    '--no-pdf-header-footer', '--generate-pdf-document-outline',
    "--print-to-pdf=`"$stagingPdf`"", "`"$(([Uri]$bookPath).AbsoluteUri)`""
)
Write-Host "build-docs-pdf: $built pages -> printing with $browser"
$process = Start-Process -FilePath $browser -ArgumentList $browserArgs -PassThru -WindowStyle Hidden
if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    Write-Host "build-docs-pdf: FAIL - the browser did not finish within $TimeoutSeconds s" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path -LiteralPath $stagingPdf) -or (Get-Item -LiteralPath $stagingPdf).Length -lt 1024) {
    Write-Host "build-docs-pdf: FAIL - the browser produced no PDF (exit $($process.ExitCode)); book kept at $bookPath" -ForegroundColor Red
    exit 1
}
$head = [byte[]]::new(5)
$stream = [IO.File]::OpenRead($stagingPdf)
try { [void]$stream.Read($head, 0, 5) } finally { $stream.Dispose() }
if ([Text.Encoding]::ASCII.GetString($head) -ne '%PDF-') {
    Write-Host "build-docs-pdf: FAIL - $stagingPdf is not a PDF" -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputFull) | Out-Null
Move-Item -LiteralPath $stagingPdf -Destination $outputFull -Force
$sizeMb = [Math]::Round((Get-Item -LiteralPath $outputFull).Length / 1MB, 2)
Write-Host "build-docs-pdf: PASS - $built pages, $sizeMb MB -> $([IO.Path]::GetRelativePath($repoRoot, $outputFull))" -ForegroundColor Green
exit 0
