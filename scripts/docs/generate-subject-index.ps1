<#
.SYNOPSIS
    Generator for the documentation A-Z subject index (S2969).

.DESCRIPTION
    Manual tool: generator for documentation subject index and topic catalog.
    Compiles docs/termbase.jsonl, docs/docs-pages-manifest.jsonl, and docs/content/recipes/*.md
    into a comprehensive, searchable A-Z Subject Index under documentation/subject-index.html.

.PARAMETER Check
    When specified, verifies that documentation/subject-index.html matches source inputs and exits 0 on match, 1 on mismatch.

.PARAMETER TermbasePath
    Path to termbase.jsonl.

.PARAMETER ManifestPath
    Path to docs-pages-manifest.jsonl.

.PARAMETER RecipesDir
    Directory holding recipe Markdown files.

.PARAMETER OutputPath
    Output path for subject-index.html.

.NOTES
    Exit codes:
      0 - subject index generated successfully or -Check verified
      1 - input file missing, output outdated on -Check, or Liquid template error
#>

[CmdletBinding()]
param (
    [switch]$Check,
    [string]$Lang = "en",
    [string]$TermbasePath = "docs/termbase.jsonl",
    [string]$ManifestPath = "docs/docs-pages-manifest.jsonl",
    [string]$RecipesDir = "docs/content/recipes",
    [string]$OutputPath
)

if (-not $OutputPath) {
    if ($Lang -eq 'ru') {
        $OutputPath = "documentation/subject-index-ru.html"
    } else {
        $OutputPath = "documentation/subject-index.html"
    }
}

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$termbaseFile = Join-Path $repoRoot $TermbasePath
$manifestFile = Join-Path $repoRoot $ManifestPath
$recipesFolder = Join-Path $repoRoot $RecipesDir
$outFile = Join-Path $repoRoot $OutputPath

if (-not (Test-Path $termbaseFile)) {
    Write-Error "generate-subject-index: Termbase file missing at $termbaseFile"
    exit 1
}

if (-not (Test-Path $manifestFile)) {
    Write-Error "generate-subject-index: Manifest file missing at $manifestFile"
    exit 1
}

# 1. Load manifest pages
$manifestPages = @{}
Get-Content $manifestFile -Encoding utf8 | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $p = ConvertFrom-Json $_
        if ($p.page_id) {
            $manifestPages[$p.page_id] = $p
        }
    }
}

# 2. Load termbase entries
$terms = [System.Collections.Generic.List[object]]::new()
Get-Content $termbaseFile -Encoding utf8 | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $t = ConvertFrom-Json $_
        $terms.Add($t)
    }
}

# 3. Aggregate Index Entries
# An index entry maps: Title, Letter, Category, Platforms, Description, References (List of { Title, Url })
$indexEntries = [System.Collections.Generic.List[object]]::new()
$seenTopics = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

function Escape-Html([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return "" }
    return [System.Web.HttpUtility]::HtmlEncode($text)
}

function Format-CategoryBadge([string]$cat) {
    switch ($cat) {
        'entity'             { return '<span class="doc-badge doc-badge-sm doc-badge-music">Entity</span>' }
        'navigation_surface' { return '<span class="doc-badge doc-badge-sm doc-badge-video">Navigation</span>' }
        'feature_action'     { return '<span class="doc-badge doc-badge-sm doc-badge-image">Feature / Action</span>' }
        'concept'            { return '<span class="doc-badge doc-badge-sm doc-badge-docs">Concept</span>' }
        'hardware'           { return '<span class="doc-badge doc-badge-sm doc-badge-other">Hardware</span>' }
        'flavor'             { return '<span class="doc-badge doc-badge-sm doc-badge-standard">Edition / Flavor</span>' }
        'protocol'           { return '<span class="doc-badge doc-badge-sm doc-badge-docs">Protocol</span>' }
        'format'             { return '<span class="doc-badge doc-badge-sm doc-badge-video">Format</span>' }
        'setting'            { return '<span class="doc-badge doc-badge-sm doc-badge-standard">Setting</span>' }
        default              { return "<span class=`"doc-badge doc-badge-sm`">$(Escape-Html $cat)</span>" }
    }
}

function Format-PlatformsBadges($platforms) {
    if (-not $platforms -or $platforms.Count -eq 0) { return "" }
    $badges = [System.Collections.Generic.List[string]]::new()
    foreach ($plat in $platforms) {
        switch ($plat) {
            'phone' { $badges.Add('<span class="doc-badge doc-badge-sm" style="background:rgba(63,185,80,0.15);color:var(--doc-accent,#3fb950);border:1px solid rgba(63,185,80,0.3);">📱 Phone</span>') }
            'wear'  { $badges.Add('<span class="doc-badge doc-badge-sm" style="background:rgba(206,147,216,0.15);color:#ce93d8;border:1px solid rgba(206,147,216,0.3);">⌚ Wear OS</span>') }
            'vr'    { $badges.Add('<span class="doc-badge doc-badge-sm" style="background:rgba(128,203,196,0.15);color:#80cbc4;border:1px solid rgba(128,203,196,0.3);">🥽 VR</span>') }
            'tv'    { $badges.Add('<span class="doc-badge doc-badge-sm" style="background:rgba(88,166,255,0.15);color:#58a6ff;border:1px solid rgba(88,166,255,0.3);">📺 TV / D-Pad</span>') }
            default { $badges.Add("<span class=`"doc-badge doc-badge-sm`">$(Escape-Html $plat)</span>") }
        }
    }
    return [string]::Join(' ', $badges)
}

$sortedManifestPages = @($manifestPages.Values | Sort-Object -Property page_id)

# Helper to find matching recipe pages for a term
function Find-TermPages($term) {
    $matched = [System.Collections.Generic.List[object]]::new()
    $termId = $term.id
    $termName = $term.canonical_en
    
    # Try finding by explicit category or keyword
    foreach ($p in $sortedManifestPages) {
        $found = $false
        if ($p.canonical_path) {
            $base = [System.IO.Path]::GetFileNameWithoutExtension($p.canonical_path)
            if ($base -like "*$termId*" -or $p.title -like "*$termName*") {
                $found = $true
            }
        }
        if ($found) {
            $relUrl = $p.canonical_path.TrimStart('/')
            if ($relUrl.StartsWith('documentation/')) {
                $relUrl = $relUrl.Substring('documentation/'.Length)
            }
            $matched.Add([pscustomobject]@{
                Title = $p.title
                Url = $relUrl
            })
        }
    }
    return @($matched | Sort-Object -Property Title)
}

# Add Termbase Entries
foreach ($t in ($terms | Sort-Object -Property { $_.canonical_en.ToLowerInvariant() })) {
    $termName = $t.canonical_en
    if ([string]::IsNullOrWhiteSpace($termName)) { continue }

    $pages = Find-TermPages $t
    $topicRecord = [ordered]@{
        Title = $termName
        Category = $t.category
        Platforms = $t.platforms
        Description = $t.definition_en
        References = $pages
        Kind = "term"
    }
    $indexEntries.Add($topicRecord)
    [void]$seenTopics.Add($termName)
}

# Add Recipe Pages directly as indexed topics
foreach ($p in $sortedManifestPages) {
    if ([string]::IsNullOrWhiteSpace($p.title) -or $seenTopics.Contains($p.title)) { continue }
    
    $relUrl = if ($p.canonical_path) {
        $r = $p.canonical_path.TrimStart('/')
        if ($r.StartsWith('documentation/')) { $r.Substring('documentation/'.Length) } else { $r }
    } else { "index.html" }
    $cat = switch -wildcard ($p.category) {
        "*Wear*"     { "hardware" }
        "*VR*"       { "hardware" }
        "*Streams*"  { "feature_action" }
        "*Settings*" { "setting" }
        "*Editions*" { "flavor" }
        "*Network*"  { "protocol" }
        default      { "concept" }
    }
    
    $plats = [System.Collections.Generic.List[string]]::new()
    if ($p.category -like "*Wear*") { $plats.Add("wear") }
    elseif ($p.category -like "*VR*") { $plats.Add("vr") }
    else { $plats.Add("phone") }
    
    $pageRefs = [System.Collections.Generic.List[object]]::new()
    $pageRefs.Add([pscustomobject]@{
        Title = $p.title
        Url = $relUrl
    })

    $topicRecord = [ordered]@{
        Title = $p.title
        Category = $cat
        Platforms = $plats
        Description = "Documentation recipe and user guide for $($p.title)."
        References = $pageRefs
        Kind = "page"
    }
    $indexEntries.Add($topicRecord)
    [void]$seenTopics.Add($p.title)
}

# Sort topics alphabetically
$sortedEntries = @($indexEntries | Sort-Object -Property @{ Expression = { $_.Title.ToLowerInvariant() } })

# Group by first letter
$grouped = [ordered]@{}
for ($i = [int][char]'A'; $i -le [int][char]'Z'; $i++) {
    $char = [string][char]$i
    $grouped[$char] = [System.Collections.Generic.List[object]]::new()
}
$grouped['#'] = [System.Collections.Generic.List[object]]::new()

foreach ($entry in $sortedEntries) {
    $first = $entry.Title.Trim().Substring(0, 1).ToUpperInvariant()
    if ($grouped.Contains($first)) {
        $grouped[$first].Add($entry)
    } else {
        $grouped['#'].Add($entry)
    }
}

# Build HTML
$sb = [System.Text.StringBuilder]::new()

$null = $sb.AppendLine(@"
---
permalink: /documentation/subject-index.html
layout: null
---
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Documentation Subject Index (A-Z) - Fast Media Sorter</title>
    <meta name="description" content="Alphabetical subject index, topic directory, and keyword catalog for Fast Media Sorter documentation across all 105 guides and recipes.">
    <link rel="canonical" href="https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/subject-index.html">
    <link rel="alternate" hreflang="en" href="https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/subject-index.html">
    <link rel="alternate" hreflang="x-default" href="https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/subject-index.html">
    <meta property="og:type" content="article">
    <meta property="og:url" content="https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/subject-index.html">
    <meta property="og:title" content="Documentation Subject Index (A-Z) - Fast Media Sorter">
    <meta property="og:description" content="Alphabetical subject index, topic directory, and keyword catalog for Fast Media Sorter documentation across all 105 guides and recipes.">
    <meta property="og:image" content="https://serzhyale.github.io/FastMediaSorter_mob_v2/apple-touch-icon.png">
    <meta property="og:locale" content="en_US">
    <meta property="og:site_name" content="Fast Media Sorter &amp; Organizer">
    <meta name="twitter:card" content="summary">
    <meta name="twitter:title" content="Documentation Subject Index (A-Z) - Fast Media Sorter">
    <meta name="twitter:description" content="Alphabetical subject index, topic directory, and keyword catalog for Fast Media Sorter documentation across all 105 guides and recipes.">
    <meta name="twitter:image" content="https://serzhyale.github.io/FastMediaSorter_mob_v2/apple-touch-icon.png">
    <script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "BreadcrumbList",
      "itemListElement": [
        {
          "@type": "ListItem",
          "position": 1,
          "name": "Home",
          "item": "https://serzhyale.github.io/FastMediaSorter_mob_v2/"
        },
        {
          "@type": "ListItem",
          "position": 2,
          "name": "Documentation",
          "item": "https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/"
        },
        {
          "@type": "ListItem",
          "position": 3,
          "name": "Subject Index",
          "item": "https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/subject-index.html"
        }
      ]
    },
    {
      "@type": "CollectionPage",
      "name": "Fast Media Sorter Subject Index",
      "description": "Alphabetical subject index, topic directory, and keyword catalog for Fast Media Sorter documentation across all 105 guides and recipes.",
      "inLanguage": "en",
      "url": "https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/subject-index.html"
    }
  ]
}
    </script>
    
    <link rel="icon" type="image/x-icon" href="../favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="../favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="../favicon-16x16.png">

    <!-- Pre-paint theme resolver -->
    <script>
        (function () {
            try {
                var t = localStorage.getItem('sza-theme') ||
                    (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
                document.documentElement.setAttribute('data-theme', t);
            } catch (e) { }
        }());
    </script>

    <!-- Typography -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Plus+Jakarta+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet">

    <!-- Styles -->
    <link rel="stylesheet" href="../styles.css">
    <link rel="stylesheet" href="assets/docs.css">
    <style>
        .doc-alpha-nav {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            margin-bottom: 2rem;
            position: sticky;
            top: 70px;
            background: var(--doc-bg, #0d1117);
            padding: 10px 0;
            z-index: 10;
            border-bottom: 1px solid var(--doc-border, #30363d);
        }
        .doc-alpha-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 32px;
            height: 32px;
            border-radius: 6px;
            background: var(--doc-card-bg, #161b22);
            color: var(--doc-text, #c9d1d9);
            text-decoration: none;
            font-weight: 600;
            font-size: 0.9rem;
            border: 1px solid var(--doc-border, #30363d);
            transition: all 0.15s ease;
        }
        .doc-alpha-btn:hover, .doc-alpha-btn.active {
            background: var(--doc-accent, #3fb950);
            color: #fff;
            border-color: var(--doc-accent, #3fb950);
        }
        .doc-alpha-btn.disabled {
            opacity: 0.3;
            pointer-events: none;
        }
        .doc-index-filter {
            width: 100%;
            max-width: 480px;
            padding: 10px 14px;
            font-size: 1rem;
            border-radius: 6px;
            border: 1px solid var(--doc-border, #30363d);
            background: var(--doc-card-bg, #161b22);
            color: var(--doc-text, #c9d1d9);
            margin-bottom: 1.5rem;
        }
        .doc-index-filter:focus {
            outline: none;
            border-color: var(--doc-accent, #3fb950);
            box-shadow: 0 0 0 2px rgba(63, 185, 80, 0.2);
        }
        .doc-letter-section {
            margin-bottom: 3rem;
            scroll-margin-top: 130px;
        }
        .doc-letter-title {
            font-size: 1.8rem;
            font-weight: 800;
            border-bottom: 2px solid var(--doc-border, #30363d);
            padding-bottom: 0.5rem;
            margin-bottom: 1.25rem;
            color: var(--doc-accent, #3fb950);
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .doc-index-item {
            background: var(--doc-card-bg, #161b22);
            border: 1px solid var(--doc-border, #30363d);
            border-radius: 8px;
            padding: 1rem 1.25rem;
            margin-bottom: 0.75rem;
            transition: border-color 0.15s ease;
        }
        .doc-index-item:hover {
            border-color: rgba(63, 185, 80, 0.5);
        }
        .doc-index-item-head {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            flex-wrap: wrap;
            margin-bottom: 0.35rem;
        }
        .doc-index-item-title {
            font-size: 1.15rem;
            font-weight: 700;
            margin: 0;
            color: var(--doc-text-bright, #f0f6fc);
        }
        .doc-index-item-desc {
            font-size: 0.92rem;
            color: var(--doc-text-muted, #8b949e);
            margin: 0.25rem 0 0.5rem 0;
            line-height: 1.5;
        }
        .doc-index-item-links {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
            margin-top: 0.5rem;
        }
        .doc-index-pill {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 3px 8px;
            background: rgba(63, 185, 80, 0.1);
            color: var(--doc-accent, #3fb950);
            border: 1px solid rgba(63, 185, 80, 0.25);
            border-radius: 4px;
            font-size: 0.82rem;
            text-decoration: none;
            font-weight: 500;
            transition: all 0.15s ease;
        }
        .doc-index-pill:hover {
            background: var(--doc-accent, #3fb950);
            color: #fff;
        }
    </style>
</head>

<body class="doc-body">

    <!-- Header Chrome -->
    <header class="doc-header">
        <div class="doc-header-inner">
            <div style="display: flex; align-items: center; gap: 1rem;">
                <a class="doc-header-brand" href="../index.html">Fast Media Sorter<span style="color: var(--doc-accent, #3fb950);">.</span></a>
                <span class="doc-badge doc-badge-standard">Subject Index</span>
            </div>

            <!-- Header Quick Search Button -->
            <button class="doc-search-trigger" data-search-trigger aria-label="Search Documentation">
                <span>🔍</span>
                <span>Search documentation...</span>
                <kbd>/</kbd>
            </button>

            <nav class="doc-header-nav" aria-label="Main Navigation">
                <a href="index.html" class="doc-header-link">Docs Home</a>
                <a href="overview.html" class="doc-header-link">Overview</a>
                <a href="subject-index.html" class="doc-header-link active">Subject Index</a>
                <a href="general/glossary.html" class="doc-header-link">Glossary</a>
                <a href="design-system/index.html" class="doc-header-link">Design System</a>
                
                <!-- 13-Language Selector -->
                <div class="doc-lang-picker">
                    <button class="doc-lang-btn" id="langBtn" aria-label="Select Language" title="Select Language">Language</button>
                    <div class="doc-lang-menu" id="langMenu" role="menu">
                        <a href="?lang=en" class="doc-lang-item active" data-lang="en"><span>English</span><span class="lang-code">en</span></a>
                        <a href="?lang=es" class="doc-lang-item" data-lang="es"><span>Español</span><span class="lang-code">es</span></a>
                        <a href="?lang=de" class="doc-lang-item" data-lang="de"><span>Deutsch</span><span class="lang-code">de</span></a>
                        <a href="?lang=fr" class="doc-lang-item" data-lang="fr"><span>Français</span><span class="lang-code">fr</span></a>
                        <a href="?lang=it" class="doc-lang-item" data-lang="it"><span>Italiano</span><span class="lang-code">it</span></a>
                        <a href="?lang=pt" class="doc-lang-item" data-lang="pt"><span>Português</span><span class="lang-code">pt</span></a>
                        <a href="?lang=ru" class="doc-lang-item" data-lang="ru"><span>Русский</span><span class="lang-code">ru</span></a>
                        <a href="?lang=uk" class="doc-lang-item" data-lang="uk"><span>Українська</span><span class="lang-code">uk</span></a>
                        <a href="?lang=zh-Hans" class="doc-lang-item" data-lang="zh-Hans"><span>简体中文</span><span class="lang-code">zh</span></a>
                        <a href="?lang=hi" class="doc-lang-item" data-lang="hi"><span>हिन्दी</span><span class="lang-code">hi</span></a>
                        <a href="?lang=bn" class="doc-lang-item" data-lang="bn"><span>বাংলা</span><span class="lang-code">bn</span></a>
                        <a href="?lang=ar" class="doc-lang-item" data-lang="ar"><span>العربية</span><span class="lang-code">ar</span></a>
                        <a href="?lang=ur" class="doc-lang-item" data-lang="ur"><span>اردو</span><span class="lang-code">ur</span></a>
                    </div>
                </div>

                <a href="../index.html" title="FastMediaSorter v2 Home" style="display:flex;align-items:center;"><img src="../apple-touch-icon.png" alt="FastMediaSorter Icon" class="doc-app-icon"></a>
                <button class="doc-theme-btn" id="themeBtn" aria-label="Toggle light/dark theme" title="Toggle theme">◐</button>
            </nav>
        </div>
    </header>

    <div class="doc-container">
        <!-- Breadcrumbs -->
        <nav class="doc-breadcrumbs" aria-label="Breadcrumb">
            <a href="../index.html">Home</a>
            <span class="doc-breadcrumb-separator">/</span>
            <a href="index.html">Documentation</a>
            <span class="doc-breadcrumb-separator">/</span>
            <span class="doc-breadcrumb-current">Subject Index</span>
        </nav>

        <div class="doc-grid">
            <!-- Sidebar -->
            <aside class="doc-sidebar" aria-label="Documentation Navigation">
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">Navigation Hub</div>
                    <ul class="doc-sidebar-list">
                        <li><a href="index.html" class="doc-sidebar-link">Documentation Home</a></li>
                        <li><a href="overview.html" class="doc-sidebar-link">Product Overview</a></li>
                        <li><a href="subject-index.html" class="doc-sidebar-link active">A-Z Subject Index</a></li>
                        <li><a href="general/glossary.html" class="doc-sidebar-link">Glossary of Terms</a></li>
                        <li><a href="design-system/index.html" class="doc-sidebar-link">Design System</a></li>
                    </ul>
                </div>
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">Index Stats</div>
                    <p style="font-size: 0.85rem; color: var(--doc-text-muted, #8b949e); margin: 0 0 0.5rem 0;">
                        Aggregates <strong>$($indexEntries.Count)</strong> indexed topics, terms, formats, and guides across all 22 documentation areas.
                    </p>
                </div>
            </aside>

            <!-- Main Index Content -->
            <main class="doc-content" id="main-content">
                <header class="doc-page-header" style="margin-bottom: 1.5rem;">
                    <h1 style="font-size: 2.3rem; margin-bottom: 0.5rem;">A-Z Subject &amp; Topic Index</h1>
                    <p class="doc-lead" style="font-size: 1.15rem; max-width: 780px;">
                        Quickly locate documentation pages, features, settings, network protocols, media formats, and terminology arranged alphabetically.
                    </p>
                    <input type="search" id="indexFilter" class="doc-index-filter" placeholder="Filter topics or keywords in index (e.g. SMB, FLAC, Wear, PIN)..." aria-label="Filter subject index">
                </header>

                <!-- Alphabetical Jump Navigation Bar -->
                <nav class="doc-alpha-nav" aria-label="Alphabetical Jump Navigation">
"@)

# Render Letter Buttons
foreach ($letter in $grouped.Keys) {
    $count = $grouped[$letter].Count
    if ($count -gt 0) {
        $null = $sb.AppendLine("                    <a href=`"#letter-$letter`" class=`"doc-alpha-btn`">$letter</a>")
    } else {
        $null = $sb.AppendLine("                    <span class=`"doc-alpha-btn disabled`">$letter</span>")
    }
}

$null = $sb.AppendLine(@"
                </nav>

                <!-- Alphabetical Index Sections -->
                <div id="indexSectionsContainer">
"@)

foreach ($letter in $grouped.Keys) {
    $entriesInLetter = $grouped[$letter]
    if ($entriesInLetter.Count -eq 0) { continue }

    $null = $sb.AppendLine(@"
                    <section class="doc-letter-section" id="letter-$letter">
                        <div class="doc-letter-title">
                            <span>$letter</span>
                            <span style="font-size: 0.9rem; font-weight: normal; color: var(--doc-text-muted, #8b949e);">$($entriesInLetter.Count) items</span>
                        </div>
                        <div class="doc-letter-items">
"@)

    foreach ($entry in $entriesInLetter) {
        $escapedTitle = Escape-Html $entry.Title
        $badge = Format-CategoryBadge $entry.Category
        $platBadges = Format-PlatformsBadges $entry.Platforms
        $desc = Escape-Html $entry.Description

        $null = $sb.AppendLine(@"
                            <article class="doc-index-item" data-topic="$($entry.Title.ToLowerInvariant())">
                                <div class="doc-index-item-head">
                                    <h3 class="doc-index-item-title">$escapedTitle</h3>
                                    $badge
                                    $platBadges
                                </div>
                                <p class="doc-index-item-desc">$desc</p>
                                <div class="doc-index-item-links">
"@)

        if ($entry.References -and $entry.References.Count -gt 0) {
            foreach ($ref in $entry.References) {
                $refTitle = Escape-Html $ref.Title
                $refUrl = $ref.Url
                $null = $sb.AppendLine("                                    <a href=`"$refUrl`" class=`"doc-index-pill`">📖 $refTitle</a>")
            }
        } elseif ($entry.Kind -eq "term") {
            $null = $sb.AppendLine("                                    <a href=`"general/glossary.html#term-$($entry.Title.ToLowerInvariant() -replace '[^a-z0-9]+', '-')`" class=`"doc-index-pill`">📖 Glossary Definition</a>")
        }

        $null = $sb.AppendLine(@"
                                </div>
                            </article>
"@)
    }

    $null = $sb.AppendLine(@"
                        </div>
                    </section>
"@)
}

$null = $sb.AppendLine(@"
                </div>
            </main>
        </div>
    </div>

    <footer class="doc-footer">
        <div class="doc-footer-inner">
            <div>Fast Media Sorter &copy; 2026 SerZhyAle. Free and open-source Android organizer.</div>
            <div class="doc-footer-links">
                <a href="../privacy.html">Privacy Policy</a>
                <a href="overview.html">Overview</a>
                <a href="subject-index.html">Subject Index</a>
                <a href="general/glossary.html">Glossary</a>
                <a href="design-system/index.html">Design System</a>
                <a href="https://github.com/SerZhyAle/FastMediaSorter_mob_v2" target="_blank" rel="noopener">GitHub</a>
            </div>
        </div>
    </footer>

    <!-- Interactive Client Scripts -->
    <script src="assets/search.js"></script>
    <script>
        (function () {
            // Theme toggle
            var btn = document.getElementById('themeBtn');
            if (btn) {
                btn.addEventListener('click', function () {
                    var current = document.documentElement.getAttribute('data-theme') || 'dark';
                    var next = current === 'dark' ? 'light' : 'dark';
                    document.documentElement.setAttribute('data-theme', next);
                    try { localStorage.setItem('sza-theme', next); } catch (e) { }
                });
            }

            // Language picker
            var langBtn = document.getElementById('langBtn');
            var langMenu = document.getElementById('langMenu');
            if (langBtn && langMenu) {
                langBtn.addEventListener('click', function (e) {
                    e.stopPropagation();
                    langMenu.classList.toggle('show');
                });
                document.addEventListener('click', function () {
                    langMenu.classList.remove('show');
                });
            }

            // Real-time Index Filter
            var filterInput = document.getElementById('indexFilter');
            if (filterInput) {
                filterInput.addEventListener('input', function () {
                    var q = filterInput.value.trim().toLowerCase();
                    var items = document.querySelectorAll('.doc-index-item');
                    var sections = document.querySelectorAll('.doc-letter-section');
                    
                    items.forEach(function (item) {
                        var topic = item.getAttribute('data-topic') || '';
                        var text = item.textContent.toLowerCase();
                        if (!q || topic.indexOf(q) !== -1 || text.indexOf(q) !== -1) {
                            item.style.display = '';
                        } else {
                            item.style.display = 'none';
                        }
                    });

                    sections.forEach(function (sec) {
                        var visible = sec.querySelectorAll('.doc-index-item:not([style*="display: none"])');
                        if (visible.length === 0) {
                            sec.style.display = 'none';
                        } else {
                            sec.style.display = '';
                        }
                    });
                });
            }
        }());
    </script>
</body>
</html>
"@)

$htmlContent = $sb.ToString()

# Liquid markers check
if ($htmlContent -match '\{\{|\{%') {
    Write-Error "generate-subject-index: Output contains Liquid template markers ({{ or {%)."
    exit 1
}

$outDir = Split-Path $outFile -Parent
if (-not $Check -and -not (Test-Path $outDir)) {
    $null = New-Item -ItemType Directory -Force $outDir
}

if ($Check) {
    if (-not (Test-Path $outFile)) {
        Write-Error "generate-subject-index: Output file $outFile does not exist."
        exit 1
    }
    $existing = [System.IO.File]::ReadAllText($outFile, [System.Text.UTF8Encoding]::new($false))
    $normExisting = $existing -replace "\r\n", "`n"
    $normNew = $htmlContent -replace "\r\n", "`n"
    if ($normExisting.Trim() -ne $normNew.Trim()) {
        Write-Error "generate-subject-index: Output file $outFile is out of date with termbase and manifest."
        exit 1
    }
    Write-Host "generate-subject-index: Output $outFile is up to date ($($indexEntries.Count) items)." -ForegroundColor Green
    exit 0
}

[System.IO.File]::WriteAllText($outFile, $htmlContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "generate-subject-index: Generated A-Z subject index ($($indexEntries.Count) items) -> $outFile" -ForegroundColor Green
exit 0
