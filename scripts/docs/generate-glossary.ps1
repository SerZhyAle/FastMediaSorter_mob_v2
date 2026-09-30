# Generator: Documentation Glossary Page Compiler
# Part of S2968 (Documentation: Glossary of Terms)
# Compiles docs/termbase.jsonl into a standalone, searchable HTML glossary page under documentation/general/glossary.html

[CmdletBinding()]
param (
    [switch]$Check,
    [string]$Lang = "en",
    [string]$TermbasePath = "docs/termbase.jsonl",
    [string]$OutputPath
)

if (-not $OutputPath) {
    if ($Lang -eq 'ru') {
        $OutputPath = "documentation/general/glossary-ru.html"
    } elseif ($Lang -eq 'uk') {
        $OutputPath = "documentation/general/glossary-uk.html"
    } else {
        $OutputPath = "documentation/general/glossary.html"
    }
}

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$termbaseFile = Join-Path $repoRoot $TermbasePath
$outFile = Join-Path $repoRoot $OutputPath

if (-not (Test-Path $termbaseFile)) {
    Write-Error "generate-glossary: Termbase file not found at $termbaseFile"
    exit 1
}

# Load SiteBase from .sza-profile.json
$siteBase = "https://serzhyale.github.io/FastMediaSorter_mob_v2"
$profilePath = Join-Path $repoRoot '.sza-profile.json'
if (Test-Path $profilePath) {
    try {
        $prof = Get-Content $profilePath -Raw -Encoding utf8 | ConvertFrom-Json
        if ($prof.site.baseUrl) {
            $siteBase = ([string]$prof.site.baseUrl).TrimEnd('/')
        }
    } catch { }
}

# Parse termbase JSONL
$rawLines = Get-Content $termbaseFile -Encoding utf8 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
$terms = [System.Collections.Generic.List[object]]::new()
$termsById = @{}

foreach ($line in $rawLines) {
    $term = ConvertFrom-Json $line
    $terms.Add($term)
    $termsById[$term.id] = $term
}

# Load Russian definitions if available
$ruDefs = $null
if ($Lang -eq 'ru') {
    $ruDefsPath = Join-Path $repoRoot "scripts/docs/lib/termbase-definitions-ru.json"
    if (Test-Path $ruDefsPath) {
        $ruDefs = Get-Content $ruDefsPath -Raw -Encoding utf8 | ConvertFrom-Json
    }
}

# Sort and group terms depending on active language
$letters = [System.Collections.Generic.SortedDictionary[string, System.Collections.Generic.List[object]]]::new()

if ($Lang -eq 'ru' -or $Lang -eq 'uk') {
    if ($Lang -eq 'uk') {
        $cyrillicAlphabet = @('А','Б','В','Г','Ґ','Д','Е','Є','Ж','З','И','І','Ї','Й','К','Л','М','Н','О','П','Р','С','Т','У','Ф','Х','Ц','Ч','Ш','Щ','Ю','Я')
    } else {
        $cyrillicAlphabet = @('А','Б','В','Г','Д','Е','Ж','З','И','К','Л','М','Н','О','П','Р','С','Т','У','Ф','Х','Ц','Ч','Ш','Щ','Э','Ю','Я')
    }
    foreach ($c in $cyrillicAlphabet) {
        $letters[$c] = [System.Collections.Generic.List[object]]::new()
    }
    $letters['#'] = [System.Collections.Generic.List[object]]::new()

    $sortedTerms = @($terms | Sort-Object -Property @{ Expression = { 
        if ($Lang -eq 'uk' -and $_.locales -and $_.locales.uk) {
            $_.locales.uk.ToLowerInvariant()
        } elseif ($Lang -eq 'ru' -and $_.locales -and $_.locales.ru) {
            $_.locales.ru.ToLowerInvariant()
        } else {
            $_.canonical_en.ToLowerInvariant()
        }
    } })

    foreach ($t in $sortedTerms) {
        $termName = if ($Lang -eq 'uk' -and $t.locales -and $t.locales.uk) {
            [string]$t.locales.uk
        } elseif ($Lang -eq 'ru' -and $t.locales -and $t.locales.ru) {
            [string]$t.locales.ru
        } else {
            [string]$t.canonical_en
        }
        $firstChar = $termName.Trim().Substring(0, 1).ToUpperInvariant()
        if (-not $letters.ContainsKey($firstChar)) {
            $firstChar = '#'
        }
        $letters[$firstChar].Add($t)
    }
} else {
    for ($i = [int][char]'A'; $i -le [int][char]'Z'; $i++) {
        $char = [char]$i
        $letters[[string]$char] = [System.Collections.Generic.List[object]]::new()
    }

    $sortedTerms = @($terms | Sort-Object -Property @{ Expression = { $_.canonical_en.ToLowerInvariant() } })

    foreach ($t in $sortedTerms) {
        $firstChar = $t.canonical_en.Trim().Substring(0, 1).ToUpperInvariant()
        if (-not $letters.ContainsKey($firstChar)) {
            $firstChar = '#'
            if (-not $letters.ContainsKey($firstChar)) {
                $letters[$firstChar] = [System.Collections.Generic.List[object]]::new()
            }
        }
        $letters[$firstChar].Add($t)
    }
}

# Relative depth for assets (glossary is in documentation/general/, so depth to documentation/ is ../, to root is ../../)
$p = "../"
$rootRel = "../../"

function Escape-Html([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return "" }
    return [System.Web.HttpUtility]::HtmlEncode($text)
}

function Format-TermCategoryBadge([string]$cat) {
    if ($Lang -eq 'ru') {
        switch ($cat) {
            'entity'             { return '<span class="doc-badge doc-badge-sm doc-badge-music">Сущность</span>' }
            'navigation_surface' { return '<span class="doc-badge doc-badge-sm doc-badge-video">Навигация</span>' }
            'feature_action'     { return '<span class="doc-badge doc-badge-sm doc-badge-image">Функция / Действие</span>' }
            'concept'            { return '<span class="doc-badge doc-badge-sm doc-badge-docs">Понятие</span>' }
            'hardware'           { return '<span class="doc-badge doc-badge-sm doc-badge-other">Устройство</span>' }
            'flavor'             { return '<span class="doc-badge doc-badge-sm doc-badge-standard">Редакция</span>' }
            default              { return "<span class=`"doc-badge doc-badge-sm`">$(Escape-Html $cat)</span>" }
        }
    } else {
        switch ($cat) {
            'entity'             { return '<span class="doc-badge doc-badge-sm doc-badge-music">Entity</span>' }
            'navigation_surface' { return '<span class="doc-badge doc-badge-sm doc-badge-video">Navigation</span>' }
            'feature_action'     { return '<span class="doc-badge doc-badge-sm doc-badge-image">Feature / Action</span>' }
            'concept'            { return '<span class="doc-badge doc-badge-sm doc-badge-docs">Concept</span>' }
            'hardware'           { return '<span class="doc-badge doc-badge-sm doc-badge-other">Hardware</span>' }
            'flavor'             { return '<span class="doc-badge doc-badge-sm doc-badge-standard">Edition / Flavor</span>' }
            default              { return "<span class=`"doc-badge doc-badge-sm`">$(Escape-Html $cat)</span>" }
        }
    }
}

function Format-PlatformsBadges($platforms) {
    if (-not $platforms -or $platforms.Count -eq 0) { return "" }
    $badges = [System.Collections.Generic.List[string]]::new()
    foreach ($plat in $platforms) {
        switch ($plat) {
            'phone' { 
                $label = if ($Lang -eq 'ru') { '📱 Телефон' } else { '📱 Phone' }
                $badges.Add("<span class=`"doc-badge doc-badge-sm`" style=`"background:rgba(63,185,80,0.15);color:var(--doc-accent,#3fb950);border:1px solid rgba(63,185,80,0.3);`">$label</span>") 
            }
            'wear'  { $badges.Add('<span class="doc-badge doc-badge-sm" style="background:rgba(206,147,216,0.15);color:#ce93d8;border:1px solid rgba(206,147,216,0.3);">⌚ Wear OS</span>') }
            'vr'    { $badges.Add('<span class="doc-badge doc-badge-sm" style="background:rgba(128,203,196,0.15);color:#80cbc4;border:1px solid rgba(128,203,196,0.3);">🥽 VR</span>') }
            default { $badges.Add("<span class=`"doc-badge doc-badge-sm`">$(Escape-Html $plat)</span>") }
        }
    }
    return [string]::Join(' ', $badges)
}

function Format-FlavorsBadges($flavors) {
    if (-not $flavors -or $flavors.Count -eq 0) { return "" }
    if ($flavors.Count -eq 1 -and $flavors[0] -eq 'all') {
        $allLabel = if ($Lang -eq 'ru') { 'Все 7 редакций' } else { 'All 7 Editions' }
        return "<span class=`"doc-badge doc-badge-sm doc-badge-standard`">$allLabel</span>"
    }
    $joined = [string]::Join(', ', $flavors)
    $prefix = if ($Lang -eq 'ru') { 'Редакции' } else { 'Editions' }
    return "<span class=`"doc-badge doc-badge-sm`" title=`"Available in: $joined`">${prefix}: $(Escape-Html $joined)</span>"
}

# Build HTML
$sb = [System.Text.StringBuilder]::new()

$pagePermalink = if ($Lang -eq 'ru') { "/documentation/general/glossary-ru.html" } elseif ($Lang -eq 'uk') { "/documentation/general/glossary-uk.html" } else { "/documentation/general/glossary.html" }
$pageHtmlLang = if ($Lang -eq 'ru') { "ru" } elseif ($Lang -eq 'uk') { "uk" } else { "en" }
$pageTitle = if ($Lang -eq 'ru') { "Словарь терминов - Документация Fast Media Sorter" } elseif ($Lang -eq 'uk') { "Словник термінів - Документація Fast Media Sorter" } else { "Glossary of Terms - Fast Media Sorter Documentation" }
$pageDesc = if ($Lang -eq 'ru') { "Официальный словарь терминов, концепций интерфейса, элементов навигации и функций для Fast Media Sorter во всех 7 редакциях Android." } elseif ($Lang -eq 'uk') { "Офіційний словник термінів, концепцій інтерфейсу, елементів навігації та функцій для Fast Media Sorter у всіх 7 редакціях Android." } else { "Authoritative glossary of terms, UI concepts, navigation surfaces, and feature definitions for Fast Media Sorter across all 7 Android editions." }
$pageLocale = if ($Lang -eq 'ru') { "ru_RU" } elseif ($Lang -eq 'uk') { "uk_UA" } else { "en_US" }

$sb.AppendLine(@"
---
permalink: $pagePermalink
layout: null
---
<!DOCTYPE html>
<html lang="$pageHtmlLang">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$pageTitle</title>
    <meta name="description" content="$pageDesc">
    <link rel="canonical" href="$siteBase$pagePermalink">
    <link rel="alternate" hreflang="en" href="$siteBase/documentation/general/glossary.html">
    <link rel="alternate" hreflang="ru" href="$siteBase/documentation/general/glossary-ru.html">
    <link rel="alternate" hreflang="uk" href="$siteBase/documentation/general/glossary-uk.html">
    <link rel="alternate" hreflang="x-default" href="$siteBase/documentation/general/glossary.html">
    <meta property="og:type" content="article">
    <meta property="og:url" content="$siteBase/documentation/general/glossary.html">
    <meta property="og:title" content="Glossary of Terms - Fast Media Sorter Documentation">
    <meta property="og:description" content="Authoritative glossary of terms, UI concepts, navigation surfaces, and feature definitions for Fast Media Sorter across all 7 Android editions.">
    <meta property="og:image" content="$siteBase/apple-touch-icon.png">
    <meta property="og:locale" content="en_US">
    <meta property="og:site_name" content="Fast Media Sorter &amp; Organizer">
    <meta name="twitter:card" content="summary">
    <meta name="twitter:title" content="Glossary of Terms - Fast Media Sorter Documentation">
    <meta name="twitter:description" content="Authoritative glossary of terms, UI concepts, navigation surfaces, and feature definitions for Fast Media Sorter across all 7 Android editions.">
    <meta name="twitter:image" content="$siteBase/apple-touch-icon.png">

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
          "item": "$siteBase/"
        },
        {
          "@type": "ListItem",
          "position": 2,
          "name": "Documentation",
          "item": "$siteBase/documentation/"
        },
        {
          "@type": "ListItem",
          "position": 3,
          "name": "General",
          "item": "$siteBase/documentation/index.html#general"
        },
        {
          "@type": "ListItem",
          "position": 4,
          "name": "Glossary of Terms",
          "item": "$siteBase/documentation/general/glossary.html"
        }
      ]
    },
    {
      "@type": "DefinedTermSet",
      "name": "Fast Media Sorter Terminology Glossary",
      "description": "Canonical definitions of media management, sorting, navigation, and playback concepts in Fast Media Sorter.",
      "inLanguage": "en",
      "url": "$siteBase/documentation/general/glossary.html"
    }
  ]
}
    </script>

    <link rel="icon" type="image/x-icon" href="${p}../favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="${p}../favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="${p}../favicon-16x16.png">

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
    <link rel="stylesheet" href="${p}../styles.css">
    <link rel="stylesheet" href="${p}assets/docs.css">

    <style>
        /* Glossary specific styling adhering to design system */
        .doc-glossary-controls {
            display: flex;
            flex-direction: column;
            gap: 1rem;
            margin: 1.5rem 0 2rem 0;
            padding: 1.25rem;
            background: var(--doc-bg-surface, #101711);
            border: 1px solid var(--doc-border, rgba(255,255,255,0.09));
            border-radius: var(--doc-radius, 12px);
        }
        .doc-glossary-search-box {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            position: relative;
        }
        .doc-glossary-input {
            width: 100%;
            padding: 0.75rem 1rem 0.75rem 2.5rem;
            background: var(--doc-bg-subtle, #1c271e);
            border: 1px solid var(--doc-border, rgba(255,255,255,0.09));
            border-radius: var(--doc-radius-sm, 6px);
            color: var(--doc-text, #f1f5ee);
            font-family: var(--doc-font-body);
            font-size: 0.95rem;
            outline: none;
            transition: var(--doc-transition);
        }
        .doc-glossary-input:focus {
            border-color: var(--doc-border-focus, #56d364);
            box-shadow: 0 0 0 3px var(--doc-accent-bg, rgba(63,185,80,0.12));
        }
        .doc-glossary-search-icon {
            position: absolute;
            left: 0.85rem;
            color: var(--doc-text-muted, #85947e);
            pointer-events: none;
        }
        .doc-filter-pills {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
            align-items: center;
        }
        .doc-filter-pill {
            padding: 0.35rem 0.75rem;
            background: var(--doc-bg-subtle, #1c271e);
            border: 1px solid var(--doc-border-subtle, rgba(255,255,255,0.05));
            border-radius: var(--doc-radius-pill, 9999px);
            color: var(--doc-text-secondary, #b8c4b2);
            font-size: 0.8rem;
            font-weight: 500;
            cursor: pointer;
            transition: var(--doc-transition);
            user-select: none;
        }
        .doc-filter-pill:hover, .doc-filter-pill.active {
            background: var(--doc-accent-bg, rgba(63,185,80,0.15));
            border-color: var(--doc-accent, #3fb950);
            color: var(--doc-text, #f1f5ee);
        }
        .doc-az-bar {
            display: flex;
            flex-wrap: wrap;
            gap: 0.25rem;
            padding: 0.75rem 1rem;
            margin-bottom: 2rem;
            background: var(--doc-bg-surface-elevated, #162018);
            border: 1px solid var(--doc-border, rgba(255,255,255,0.09));
            border-radius: var(--doc-radius, 12px);
            justify-content: center;
            position: sticky;
            top: 70px;
            z-index: 10;
            backdrop-filter: blur(8px);
        }
        .doc-az-link {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 2rem;
            height: 2rem;
            border-radius: var(--doc-radius-sm, 6px);
            color: var(--doc-accent, #3fb950);
            font-weight: 700;
            text-decoration: none;
            font-size: 0.9rem;
            transition: var(--doc-transition);
        }
        .doc-az-link:hover {
            background: var(--doc-accent-bg, rgba(63,185,80,0.15));
            color: var(--doc-accent-hover, #56d364);
        }
        .doc-az-link.disabled {
            color: var(--doc-text-muted, #85947e);
            opacity: 0.35;
            pointer-events: none;
        }
        .doc-glossary-group {
            margin-bottom: 3rem;
            scroll-margin-top: 130px;
        }
        .doc-glossary-letter-heading {
            display: flex;
            align-items: baseline;
            gap: 0.75rem;
            font-size: 2rem;
            font-family: var(--doc-font-heading);
            color: var(--doc-accent, #3fb950);
            border-bottom: 2px solid var(--doc-border, rgba(255,255,255,0.09));
            padding-bottom: 0.5rem;
            margin-bottom: 1.5rem;
        }
        .doc-glossary-letter-count {
            font-size: 0.9rem;
            color: var(--doc-text-muted, #85947e);
            font-weight: normal;
        }
        .doc-term-card {
            background: var(--doc-bg-surface, #101711);
            border: 1px solid var(--doc-border, rgba(255,255,255,0.09));
            border-left: 4px solid var(--doc-accent, #3fb950);
            border-radius: var(--doc-radius, 12px);
            padding: 1.25rem 1.5rem;
            margin-bottom: 1.25rem;
            scroll-margin-top: 140px;
            transition: var(--doc-transition);
        }
        .doc-term-card:hover {
            border-color: var(--doc-accent, #3fb950);
            box-shadow: var(--doc-shadow-sm, 0 2px 8px rgba(0,0,0,0.35));
        }
        .doc-term-header {
            display: flex;
            flex-wrap: wrap;
            justify-content: space-between;
            align-items: center;
            gap: 0.75rem;
            margin-bottom: 0.75rem;
        }
        .doc-term-title-wrapper {
            display: flex;
            align-items: center;
            gap: 0.6rem;
        }
        .doc-term-icon {
            width: 24px;
            height: 24px;
            object-fit: contain;
            border-radius: 4px;
        }
        .doc-term-title {
            margin: 0;
            font-size: 1.3rem;
            font-family: var(--doc-font-heading);
            color: var(--doc-text, #f1f5ee);
            font-weight: 700;
        }
        .doc-term-anchor {
            color: var(--doc-text-muted, #85947e);
            text-decoration: none;
            font-weight: 500;
            opacity: 0;
            transition: var(--doc-transition);
        }
        .doc-term-card:hover .doc-term-anchor {
            opacity: 0.8;
        }
        .doc-term-anchor:hover {
            opacity: 1;
            color: var(--doc-accent, #3fb950);
        }
        .doc-term-badges {
            display: flex;
            flex-wrap: wrap;
            gap: 0.4rem;
            align-items: center;
        }
        .doc-term-definition {
            font-size: 1rem;
            line-height: 1.6;
            color: var(--doc-text, #f1f5ee);
            margin: 0.5rem 0 0.75rem 0;
        }
        .doc-term-disambiguation {
            display: flex;
            gap: 0.6rem;
            align-items: flex-start;
            padding: 0.75rem 1rem;
            background: var(--doc-bg-subtle, #1c271e);
            border-left: 3px solid var(--doc-gold, #e3b341);
            border-radius: 0 var(--doc-radius-sm, 6px) var(--doc-radius-sm, 6px) 0;
            font-size: 0.9rem;
            color: var(--doc-text-secondary, #b8c4b2);
            margin-bottom: 0.75rem;
        }
        .doc-term-meta-row {
            display: flex;
            flex-wrap: wrap;
            gap: 1.25rem;
            align-items: center;
            margin-top: 0.75rem;
            padding-top: 0.75rem;
            border-top: 1px solid var(--doc-border-subtle, rgba(255,255,255,0.05));
            font-size: 0.85rem;
            color: var(--doc-text-muted, #85947e);
        }
        .doc-term-locales {
            display: flex;
            gap: 0.5rem;
            align-items: center;
        }
        .doc-term-locale-tag {
            background: var(--doc-bg-subtle, #1c271e);
            padding: 0.15rem 0.5rem;
            border-radius: var(--doc-radius-sm, 6px);
            color: var(--doc-text-secondary, #b8c4b2);
            font-family: var(--doc-font-mono);
            font-size: 0.8rem;
        }
        .doc-term-related-list {
            display: flex;
            flex-wrap: wrap;
            gap: 0.4rem;
            align-items: center;
        }
        .doc-term-related-chip {
            color: var(--doc-accent, #3fb950);
            text-decoration: none;
            background: var(--doc-accent-bg, rgba(63,185,80,0.12));
            padding: 0.15rem 0.5rem;
            border-radius: var(--doc-radius-sm, 6px);
            font-size: 0.8rem;
            transition: var(--doc-transition);
        }
        .doc-term-related-chip:hover {
            background: var(--doc-accent, #3fb950);
            color: var(--doc-accent-ink, #04130c);
        }
        .doc-term-synonyms {
            font-size: 0.85rem;
            color: var(--doc-gold, #e3b341);
            background: var(--doc-gold-bg, rgba(227,179,65,0.12));
            padding: 0.35rem 0.6rem;
            border-radius: var(--doc-radius-sm, 6px);
            margin-bottom: 0.5rem;
        }
        .doc-no-results {
            text-align: center;
            padding: 3rem 1rem;
            background: var(--doc-bg-surface, #101711);
            border: 1px dashed var(--doc-border, rgba(255,255,255,0.09));
            border-radius: var(--doc-radius, 12px);
            color: var(--doc-text-muted, #85947e);
            font-size: 1.1rem;
        }
    </style>
</head>

<body class="doc-body">

    <!-- Header Chrome -->
    <header class="doc-header">
        <div class="doc-header-inner">
            <div style="display: flex; align-items: center; gap: 1rem;">
                <a class="doc-header-brand" href="${p}../index.html">Fast Media Sorter<span style="color: var(--doc-accent, #3fb950);">.</span></a>
                <a href="${p}index.html" class="doc-badge doc-badge-sm" style="text-decoration: none; color: var(--doc-text-secondary);">Docs</a>
            </div>

            <!-- Header Quick Search Button -->
            <button class="doc-search-trigger" data-search-trigger aria-label="$(if ($Lang -eq 'ru') { 'Поиск по документации' } elseif ($Lang -eq 'uk') { 'Пошук по документації' } else { 'Search Documentation' })">
                <span>🔍</span>
                <span>$(if ($Lang -eq 'ru') { 'Поиск...' } elseif ($Lang -eq 'uk') { 'Пошук...' } else { 'Search...' })</span>
                <kbd>/</kbd>
            </button>

            <nav class="doc-header-nav" aria-label="Main Navigation">
                <a href="${p}sample-recipe.html" class="doc-header-link">$(if ($Lang -eq 'ru') { 'Аудио' } elseif ($Lang -eq 'uk') { 'Аудіо' } else { 'Audio Recipe' })</a>
                <a href="${p}sample-settings-recipe.html" class="doc-header-link">$(if ($Lang -eq 'ru') { 'Настройки' } elseif ($Lang -eq 'uk') { 'Налаштування' } else { 'Settings Recipe' })</a>
                <a href="${p}sample-program-recipe.html" class="doc-header-link">$(if ($Lang -eq 'ru') { 'Программы' } elseif ($Lang -eq 'uk') { 'Програми' } else { 'Programs Recipe' })</a>
                <a href="${p}general/glossary$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html" class="doc-header-link active">$(if ($Lang -eq 'ru') { 'Словарь' } elseif ($Lang -eq 'uk') { 'Словник' } else { 'Glossary' })</a>
                <a href="${p}design-system/index.html" class="doc-header-link">$(if ($Lang -eq 'ru') { 'Дизайн-система' } elseif ($Lang -eq 'uk') { 'Дизайн-система' } else { 'Design System' })</a>
                <a href="https://github.com/SerZhyAle/FastMediaSorter_mob_v2" target="_blank" rel="noopener" class="doc-header-link doc-link-external">GitHub</a>
                
                <!-- 13-Language Selector -->
                <div class="doc-lang-picker">
                    <button class="doc-lang-btn" id="langBtn" aria-label="Select Language" title="Select Language">$(if ($Lang -eq 'ru') { 'Язык' } elseif ($Lang -eq 'uk') { 'Мова' } else { 'Language' })</button>
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

                <a href="${p}../index.html" title="FastMediaSorter v2 Home" style="display:flex;align-items:center;"><img src="${p}../apple-touch-icon.png" alt="FastMediaSorter Icon" class="doc-app-icon"></a>
                <button class="doc-theme-btn" id="themeBtn" aria-label="Toggle light/dark theme" title="Toggle theme">◐</button>
            </nav>
        </div>
    </header>

    <div class="doc-container">

        <!-- Breadcrumbs -->
        <nav class="doc-breadcrumbs" aria-label="Breadcrumb">
            <a href="${p}../index.html">$(if ($Lang -eq 'ru') { 'Главная' } elseif ($Lang -eq 'uk') { 'Головна' } else { 'Home' })</a>
            <span class="doc-breadcrumb-separator">/</span>
            <a href="${p}index$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html">$(if ($Lang -eq 'ru') { 'Документация' } elseif ($Lang -eq 'uk') { 'Документація' } else { 'Documentation' })</a>
            <span class="doc-breadcrumb-separator">/</span>
            <a href="${p}index$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html#general">$(if ($Lang -eq 'ru') { 'Общие' } elseif ($Lang -eq 'uk') { 'Загальні' } else { 'General' })</a>
            <span class="doc-breadcrumb-separator">/</span>
            <span class="doc-breadcrumb-current">$(if ($Lang -eq 'ru') { 'Словарь терминов' } elseif ($Lang -eq 'uk') { 'Словник термінів' } else { 'Glossary of Terms' })</span>
        </nav>

        <div class="doc-grid">

            <!-- Sidebar -->
            <aside class="doc-sidebar" aria-label="Documentation Navigation">
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">$(if ($Lang -eq 'ru') { 'С чего начать' } elseif ($Lang -eq 'uk') { 'З чого почати' } else { 'Getting Started' })</div>
                    <ul class="doc-sidebar-list">
                        <li><a href="${p}index$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html" class="doc-sidebar-link">$(if ($Lang -eq 'ru') { 'Главная документации' } elseif ($Lang -eq 'uk') { 'Головна документації' } else { 'Docs Home' })</a></li>
                        <li><a href="${p}getting-started/welcome-and-setup$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html" class="doc-sidebar-link">$(if ($Lang -eq 'ru') { 'Мастер настройки' } elseif ($Lang -eq 'uk') { 'Майстер налаштування' } else { 'Setup Wizard' })</a></li>
                        <li><a href="${p}getting-started/permissions-guide$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html" class="doc-sidebar-link">$(if ($Lang -eq 'ru') { 'Разрешения приложения' } elseif ($Lang -eq 'uk') { 'Дозволи додатка' } else { 'App Permissions' })</a></li>
                        <li><a href="${p}general/glossary$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html" class="doc-sidebar-link active">$(if ($Lang -eq 'ru') { 'Словарь терминов' } elseif ($Lang -eq 'uk') { 'Словник термінів' } else { 'Glossary of Terms' })</a></li>
                        <li><a href="${p}design-system/index.html" class="doc-sidebar-link">$(if ($Lang -eq 'ru') { 'Каталог компонентов' } elseif ($Lang -eq 'uk') { 'Каталог компонентів' } else { 'Component Catalog' })</a></li>
                    </ul>
                </div>
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">$(if ($Lang -eq 'ru') { 'Категории' } elseif ($Lang -eq 'uk') { 'Категорії' } else { 'Categories' })</div>
                    <ul class="doc-sidebar-list">
                        <li><a href="#" class="doc-sidebar-link doc-category-sidebar-link" data-category="all">$(if ($Lang -eq 'ru') { "Все понятия ($($terms.Count))" } elseif ($Lang -eq 'uk') { "Всі поняття ($($terms.Count))" } else { "All Concepts ($($terms.Count))" })</a></li>
                        <li><a href="#" class="doc-sidebar-link doc-category-sidebar-link" data-category="entity">$(if ($Lang -eq 'ru') { 'Сущности и файлы' } elseif ($Lang -eq 'uk') { 'Сутності та файли' } else { 'Entities & Files' })</a></li>
                        <li><a href="#" class="doc-sidebar-link doc-category-sidebar-link" data-category="navigation_surface">$(if ($Lang -eq 'ru') { 'Элементы навигации' } elseif ($Lang -eq 'uk') { 'Елементи навігації' } else { 'Navigation Surfaces' })</a></li>
                        <li><a href="#" class="doc-sidebar-link doc-category-sidebar-link" data-category="feature_action">$(if ($Lang -eq 'ru') { 'Функции и действия' } elseif ($Lang -eq 'uk') { 'Функції та дії' } else { 'Features & Actions' })</a></li>
                        <li><a href="#" class="doc-sidebar-link doc-category-sidebar-link" data-category="concept">$(if ($Lang -eq 'ru') { 'Общие понятия' } elseif ($Lang -eq 'uk') { 'Загальні поняття' } else { 'General Concepts' })</a></li>
                    </ul>
                </div>
            </aside>

            <!-- Main Content -->
            <main class="doc-content" id="main-content">

                <div style="margin-bottom: 1.5rem; display: flex; gap: 0.5rem; align-items: center;">
                    <span class="doc-badge doc-badge-docs">$(if ($Lang -eq 'ru') { 'Документация' } elseif ($Lang -eq 'uk') { 'Документація' } else { 'Documentation' })</span>
                    <span class="doc-badge doc-badge-standard">$(if ($Lang -eq 'ru') { 'Все 7 редакций' } elseif ($Lang -eq 'uk') { 'Всі 7 редакцій' } else { 'All 7 Editions' })</span>
                    <span class="doc-badge doc-badge-sm">$(if ($Lang -eq 'ru') { "$($terms.Count) терминов" } elseif ($Lang -eq 'uk') { "$($terms.Count) термінів" } else { "$($terms.Count) Terms Compiled" })</span>
                </div>

                <h1>$(if ($Lang -eq 'ru') { 'Словарь терминов' } elseif ($Lang -eq 'uk') { 'Словник термінів' } else { 'Glossary of Terms' })</h1>

                <p class="doc-lead">
                    $(if ($Lang -eq 'ru') { 'Официальный словарь терминов, концепций интерфейса, элементов навигации и функций Fast Media Sorter во всех 7 редакциях Android. Скомпилирован автоматически из единой базы терминов (<code>docs/termbase.jsonl</code>).' } elseif ($Lang -eq 'uk') { 'Офіційний словник термінів, концепцій інтерфейсу, елементів навігації та функцій для Fast Media Sorter у всіх 7 редакціях Android. Скомпільовано автоматично з єдиної бази термінів (<code>docs/termbase.jsonl</code>).' } else { 'The authoritative dictionary of Fast Media Sorter concepts, navigation surfaces, media categories, and edition features. Compiled automatically from the single-source-of-truth termbase (<code>docs/termbase.jsonl</code>).' })
                </p>

                <!-- Filter & Search Controls -->
                <div class="doc-glossary-controls">
                    <div class="doc-glossary-search-box">
                        <span class="doc-glossary-search-icon">🔍</span>
                        <input type="search" id="glossaryFilter" class="doc-glossary-input" placeholder="$(if ($Lang -eq 'ru') { 'Поиск терминов, определений, переводов (en/uk) или синонимов...' } elseif ($Lang -eq 'uk') { 'Пошук термінів, визначень, перекладів (ru/en) або синонімів...' } else { 'Search terms, definitions, translations (ru/uk), or synonyms...' })" aria-label="Filter terms">
                    </div>
                    <div class="doc-filter-pills" id="categoryPills">
                        <span style="font-size:0.8rem;color:var(--doc-text-muted);margin-right:0.25rem;">$(if ($Lang -eq 'ru') { 'Категория:' } elseif ($Lang -eq 'uk') { 'Категорія:' } else { 'Category:' })</span>
                        <button class="doc-filter-pill active" data-cat="all">$(if ($Lang -eq 'ru') { "Все ($($terms.Count))" } elseif ($Lang -eq 'uk') { "Всі ($($terms.Count))" } else { "All ($($terms.Count))" })</button>
                        <button class="doc-filter-pill" data-cat="entity">$(if ($Lang -eq 'ru') { 'Сущности' } elseif ($Lang -eq 'uk') { 'Сутності' } else { 'Entities' })</button>
                        <button class="doc-filter-pill" data-cat="navigation_surface">$(if ($Lang -eq 'ru') { 'Навигация' } elseif ($Lang -eq 'uk') { 'Навігація' } else { 'Navigation' })</button>
                        <button class="doc-filter-pill" data-cat="feature_action">$(if ($Lang -eq 'ru') { 'Действия' } elseif ($Lang -eq 'uk') { 'Дії' } else { 'Actions' })</button>
                        <button class="doc-filter-pill" data-cat="concept">$(if ($Lang -eq 'ru') { 'Понятия' } elseif ($Lang -eq 'uk') { 'Поняття' } else { 'Concepts' })</button>
                        <button class="doc-filter-pill" data-cat="hardware">$(if ($Lang -eq 'ru') { 'Устройства' } elseif ($Lang -eq 'uk') { 'Пристрої' } else { 'Hardware' })</button>
                    </div>
                    <div class="doc-filter-pills" id="platformPills">
                        <span style="font-size:0.8rem;color:var(--doc-text-muted);margin-right:0.25rem;">$(if ($Lang -eq 'ru') { 'Платформа:' } elseif ($Lang -eq 'uk') { 'Платформа:' } else { 'Platform:' })</span>
                        <button class="doc-filter-pill active" data-plat="all">$(if ($Lang -eq 'ru') { 'Все' } elseif ($Lang -eq 'uk') { 'Всі' } else { 'All' })</button>
                        <button class="doc-filter-pill" data-plat="phone">📱 $(if ($Lang -eq 'ru') { 'Телефон' } elseif ($Lang -eq 'uk') { 'Телефон' } else { 'Phone' })</button>
                        <button class="doc-filter-pill" data-plat="wear">⌚ Wear OS</button>
                        <button class="doc-filter-pill" data-plat="vr">🥽 VR</button>
                    </div>
                </div>

                <!-- Alphabet Jump Bar -->
                <nav class="doc-az-bar" aria-label="Alphabetical Index">
"@) | Out-Null

foreach ($entry in $letters.GetEnumerator()) {
    $letter = $entry.Key
    $count = $entry.Value.Count
    if ($count -gt 0) {
        $sb.AppendLine("                    <a href=`"#letter-$letter`" class=`"doc-az-link`" title=`"$letter ($count terms)`">$letter</a>") | Out-Null
    } else {
        $sb.AppendLine("                    <span class=`"doc-az-link disabled`">$letter</span>") | Out-Null
    }
}

$sb.AppendLine(@"
                </nav>

                <!-- Terms Container -->
                <div id="glossaryTermsContainer">
"@) | Out-Null

foreach ($entry in $letters.GetEnumerator()) {
    $letter = $entry.Key
    $list = $entry.Value
    if ($list.Count -eq 0) { continue }

    $letterTermCountText = if ($Lang -eq 'ru') { "$($list.Count) терминов" } elseif ($Lang -eq 'uk') { "$($list.Count) термінів" } else { "$($list.Count) terms" }
    $sb.AppendLine(@"
                    <!-- Letter Group $letter -->
                    <section id="letter-$letter" class="doc-glossary-group" data-letter="$letter">
                        <h2 class="doc-glossary-letter-heading">$letter <span class="doc-glossary-letter-count">($letterTermCountText)</span></h2>
                        <div class="doc-glossary-cards">
"@) | Out-Null

    foreach ($t in $list) {
        $termId = Escape-Html $t.id
        $canonical = if ($Lang -eq 'ru' -and $t.locales -and $t.locales.ru) {
            Escape-Html $t.locales.ru
        } elseif ($Lang -eq 'uk' -and $t.locales -and $t.locales.uk) {
            Escape-Html $t.locales.uk
        } else {
            Escape-Html $t.canonical_en
        }
        $category = Escape-Html $t.category
        
        $definitionText = if ($Lang -eq 'ru' -and $ruDefs -and $ruDefs.($t.id) -and $ruDefs.($t.id).def) {
            $ruDefs.($t.id).def
        } else {
            $t.definition_en
        }
        $definition = Escape-Html $definitionText

        $disambiguationText = if ($Lang -eq 'ru' -and $ruDefs -and $ruDefs.($t.id) -and $ruDefs.($t.id).dis) {
            $ruDefs.($t.id).dis
        } elseif ($t.disambiguation_en) {
            $t.disambiguation_en
        } else {
            $null
        }
        $disambiguation = if ($disambiguationText) { Escape-Html $disambiguationText } else { $null }

        $categoryBadge = Format-TermCategoryBadge $t.category
        $platformBadges = Format-PlatformsBadges $t.platforms
        $flavorBadges = Format-FlavorsBadges $t.flavors
        
        $platformsAttr = if ($t.platforms) { Escape-Html ([string]::Join(' ', $t.platforms)) } else { "phone" }
        $flavorsAttr = if ($t.flavors) { Escape-Html ([string]::Join(' ', $t.flavors)) } else { "all" }
        
        # Search keywords attribute
        $searchTerms = [System.Collections.Generic.List[string]]::new()
        $searchTerms.Add($t.canonical_en)
        $searchTerms.Add($t.id)
        if ($definitionText) { $searchTerms.Add($definitionText) }
        if ($disambiguationText) { $searchTerms.Add($disambiguationText) }
        if ($t.locales) {
            if ($t.locales.ru) { $searchTerms.Add([string]$t.locales.ru) }
            if ($t.locales.uk) { $searchTerms.Add([string]$t.locales.uk) }
        }
        if ($t.forbidden_synonyms_en) {
            foreach ($syn in $t.forbidden_synonyms_en) { $searchTerms.Add([string]$syn) }
        }
        $keywordsAttr = Escape-Html ([string]::Join(' ', $searchTerms).ToLowerInvariant())

        # Reference visual icon
        $iconHtml = ""
        if ($t.reference_visual) {
            $visPath = $rootRel + $t.reference_visual
            $iconHtml = "<img src=`"$visPath`" alt=`"$canonical icon`" class=`"doc-term-icon`" loading=`"lazy`">"
        }

        $sb.AppendLine(@"
                            <article id="term-$termId" class="doc-term-card" data-term-id="$termId" data-category="$category" data-platforms="$platformsAttr" data-flavors="$flavorsAttr" data-keywords="$keywordsAttr">
                                <div class="doc-term-header">
                                    <div class="doc-term-title-wrapper">
                                        $iconHtml
                                        <h3 class="doc-term-title">$canonical</h3>
                                        <a href="#term-$termId" class="doc-term-anchor" title="Direct link to $canonical">#</a>
                                    </div>
                                    <div class="doc-term-badges">
                                        $categoryBadge
                                        $platformBadges
                                        $flavorBadges
                                    </div>
                                </div>

                                <p class="doc-term-definition">$definition</p>
"@) | Out-Null

        if ($disambiguation) {
            $distinctionLabel = if ($Lang -eq 'ru') { 'Различие:' } elseif ($Lang -eq 'uk') { 'Відмінність:' } else { 'Distinction:' }
            $sb.AppendLine(@"
                                <div class="doc-term-disambiguation">
                                    <span style="font-size:1.1rem;line-height:1;">ℹ️</span>
                                    <div><strong>$distinctionLabel</strong> $disambiguation</div>
                                </div>
"@) | Out-Null
        }

        if ($t.forbidden_synonyms_en -and $t.forbidden_synonyms_en.Count -gt 0) {
            $syns = [string]::Join(', ', $t.forbidden_synonyms_en)
            $altLabel = if ($Lang -eq 'ru') { 'Альтернативные / устаревшие названия:' } elseif ($Lang -eq 'uk') { 'Альтернативні / застарілі назви:' } else { 'Alternative / Legacy terms:' }
            $sb.AppendLine(@"
                                <div class="doc-term-synonyms">
                                    <strong>$altLabel</strong> $(Escape-Html $syns)
                                </div>
"@) | Out-Null
        }

        # Meta row: Locales + Related terms
        $hasLocales = ($t.locales -and ($t.locales.ru -or $t.locales.uk -or $t.canonical_en))
        $hasRelated = ($t.related_terms -and $t.related_terms.Count -gt 0)

        if ($hasLocales -or $hasRelated) {
            $sb.AppendLine("                                <div class=`"doc-term-meta-row`">") | Out-Null
            
            if ($hasLocales) {
                $translationsLabel = if ($Lang -eq 'ru') { 'Переводы:' } elseif ($Lang -eq 'uk') { 'Переклади:' } else { 'Translations:' }
                $sb.AppendLine("                                    <div class=`"doc-term-locales`">") | Out-Null
                $sb.AppendLine("                                        <span>$translationsLabel</span>") | Out-Null
                if ($Lang -ne 'en') {
                    $sb.AppendLine("                                        <span class=`"doc-term-locale-tag`">EN: $(Escape-Html $t.canonical_en)</span>") | Out-Null
                }
                if ($Lang -ne 'ru' -and $t.locales -and $t.locales.ru) {
                    $sb.AppendLine("                                        <span class=`"doc-term-locale-tag`">RU: $(Escape-Html $t.locales.ru)</span>") | Out-Null
                }
                if ($Lang -ne 'uk' -and $t.locales -and $t.locales.uk) {
                    $sb.AppendLine("                                        <span class=`"doc-term-locale-tag`">UK: $(Escape-Html $t.locales.uk)</span>") | Out-Null
                }
                $sb.AppendLine("                                    </div>") | Out-Null
            }

            if ($hasRelated) {
                $seeAlsoLabel = if ($Lang -eq 'ru') { 'См. также:' } elseif ($Lang -eq 'uk') { 'Див. також:' } else { 'See also:' }
                $sb.AppendLine("                                    <div class=`"doc-term-related-list`">") | Out-Null
                $sb.AppendLine("                                        <span>$seeAlsoLabel</span>") | Out-Null
                foreach ($relId in $t.related_terms) {
                    $relTitle = if ($termsById.ContainsKey($relId)) {
                        if ($Lang -eq 'ru' -and $termsById[$relId].locales -and $termsById[$relId].locales.ru) {
                            $termsById[$relId].locales.ru
                        } elseif ($Lang -eq 'uk' -and $termsById[$relId].locales -and $termsById[$relId].locales.uk) {
                            $termsById[$relId].locales.uk
                        } else {
                            $termsById[$relId].canonical_en
                        }
                    } else {
                        $relId
                    }
                    $sb.AppendLine("                                        <a href=`"#term-$(Escape-Html $relId)`" class=`"doc-term-related-chip`">$(Escape-Html $relTitle)</a>") | Out-Null
                }
                $sb.AppendLine("                                    </div>") | Out-Null
            }

            $sb.AppendLine("                                </div>") | Out-Null
        }

        $sb.AppendLine("                            </article>") | Out-Null
    }

    $sb.AppendLine("                        </div>`n                    </section>") | Out-Null
}

$noResultsText = if ($Lang -eq 'ru') { '🔍 Термины не найдены. Попробуйте изменить фильтр или поисковый запрос.' } elseif ($Lang -eq 'uk') { '🔍 Терміни не знайдені. Спробуйте змінити фільтр або пошуковий запит.' } else { '🔍 No matching terms found. Try adjusting your filter or search query.' }
$tocTitleText = if ($Lang -eq 'ru') { 'Алфавитный указатель' } elseif ($Lang -eq 'uk') { 'Алфавітний покажчик' } else { 'Alphabet Index' }

$sb.AppendLine(@"
                    <div id="noResultsMessage" class="doc-no-results" style="display: none;">
                        $noResultsText
                    </div>
                </div>

            </main>

            <!-- Table of Contents / Alphabet Quick Nav -->
            <aside class="doc-toc-wrapper" aria-label="Glossary Table of Contents">
                <nav class="doc-toc">
                    <div class="doc-toc-title">$tocTitleText</div>
                    <ul class="doc-toc-list">
"@) | Out-Null

foreach ($entry in $letters.GetEnumerator()) {
    $letter = $entry.Key
    $count = $entry.Value.Count
    if ($count -gt 0) {
        $letterLabel = if ($Lang -eq 'ru') { "Буква $letter" } elseif ($Lang -eq 'uk') { "Буква $letter" } else { "Letter $letter" }
        $sb.AppendLine("                        <li><a href=`"#letter-$letter`" class=`"doc-toc-link`">$letterLabel <span style=`"color:var(--doc-text-muted);font-size:0.8rem;`">($count)</span></a></li>") | Out-Null
    }
}

$sb.AppendLine(@"
                    </ul>
                </nav>
            </aside>

        </div><!-- /.doc-grid -->

    </div><!-- /.doc-container -->

    <footer class="doc-footer">
        <div class="doc-footer-inner">
            <div>$(if ($Lang -eq 'ru') { 'Fast Media Sorter &copy; 2026 SerZhyAle. Бесплатный органайзер с открытым исходным кодом.' } elseif ($Lang -eq 'uk') { 'Fast Media Sorter &copy; 2026 SerZhyAle. Безкоштовний органайзер із відкритим вихідним кодом.' } else { 'Fast Media Sorter &copy; 2026 SerZhyAle. Free and open-source Android organizer.' })</div>
                <a href="${p}../docs/$(if ($Lang -eq 'ru') { 'PRIVACY_POLICY.ru.html' } elseif ($Lang -eq 'uk') { 'PRIVACY_POLICY.uk.html' } else { 'PRIVACY_POLICY.html' })">$(if ($Lang -eq 'ru') { 'Политика конфиденциальности' } elseif ($Lang -eq 'uk') { 'Політика конфіденційності' } else { 'Privacy Policy' })</a>
                <a href="${p}../docs/$(if ($Lang -eq 'ru') { 'TERMS_OF_SERVICE_RU.html' } elseif ($Lang -eq 'uk') { 'TERMS_OF_SERVICE_UK.html' } else { 'TERMS_OF_SERVICE.html' })">$(if ($Lang -eq 'ru') { 'Условия использования' } elseif ($Lang -eq 'uk') { 'Умови використання' } else { 'Terms of Service' })</a>
                <a href="${p}general/glossary$(if ($Lang -eq 'ru') { '-ru' } elseif ($Lang -eq 'uk') { '-uk' }).html">$(if ($Lang -eq 'ru') { 'Словарь' } elseif ($Lang -eq 'uk') { 'Словник' } else { 'Glossary' })</a>
                <a href="${p}design-system/index.html">Component System</a>
                <a href="https://github.com/SerZhyAle/FastMediaSorter_mob_v2" target="_blank" rel="noopener">GitHub</a>
            </div>
        </div>
    </footer>

    <!-- Interactive Client Filtering Script -->
    <script>
        (function () {
            var filterInput = document.getElementById('glossaryFilter');
            var catPills = document.querySelectorAll('#categoryPills .doc-filter-pill');
            var platPills = document.querySelectorAll('#platformPills .doc-filter-pill');
            var cards = document.querySelectorAll('.doc-term-card');
            var groups = document.querySelectorAll('.doc-glossary-group');
            var noResults = document.getElementById('noResultsMessage');
            var azLinks = document.querySelectorAll('.doc-az-link');
            var tocLinks = document.querySelectorAll('.doc-toc-link');

            var currentCategory = 'all';
            var currentPlatform = 'all';
            var currentQuery = '';

            function updateFilter() {
                var visibleCount = 0;
                var groupCounts = {};

                cards.forEach(function (card) {
                    var cardCat = card.getAttribute('data-category') || '';
                    var cardPlat = card.getAttribute('data-platforms') || '';
                    var cardKw = card.getAttribute('data-keywords') || '';
                    var groupLetter = card.closest('.doc-glossary-group').getAttribute('data-letter');

                    var matchesCat = (currentCategory === 'all' || cardCat === currentCategory);
                    var matchesPlat = (currentPlatform === 'all' || cardPlat.indexOf(currentPlatform) !== -1);
                    var matchesQuery = (!currentQuery || cardKw.indexOf(currentQuery) !== -1);

                    if (matchesCat && matchesPlat && matchesQuery) {
                        card.style.display = '';
                        visibleCount++;
                        groupCounts[groupLetter] = (groupCounts[groupLetter] || 0) + 1;
                    } else {
                        card.style.display = 'none';
                    }
                });

                groups.forEach(function (group) {
                    var letter = group.getAttribute('data-letter');
                    var count = groupCounts[letter] || 0;
                    group.style.display = count > 0 ? '' : 'none';
                });

                azLinks.forEach(function (link) {
                    var letter = link.textContent.trim();
                    var count = groupCounts[letter] || 0;
                    if (count > 0) {
                        link.classList.remove('disabled');
                    } else {
                        link.classList.add('disabled');
                    }
                });

                if (noResults) {
                    noResults.style.display = visibleCount === 0 ? 'block' : 'none';
                }
            }

            if (filterInput) {
                filterInput.addEventListener('input', function (e) {
                    currentQuery = e.target.value.trim().toLowerCase();
                    updateFilter();
                });
            }

            catPills.forEach(function (pill) {
                pill.addEventListener('click', function () {
                    catPills.forEach(function (p) { p.classList.remove('active'); });
                    pill.classList.add('active');
                    currentCategory = pill.getAttribute('data-cat');
                    updateFilter();
                });
            });

            platPills.forEach(function (pill) {
                pill.addEventListener('click', function () {
                    platPills.forEach(function (p) { p.classList.remove('active'); });
                    pill.classList.add('active');
                    currentPlatform = pill.getAttribute('data-plat');
                    updateFilter();
                });
            });

            // Theme switcher
            var btn = document.getElementById('themeBtn');
            if (btn) {
                btn.addEventListener('click', function () {
                    var current = document.documentElement.getAttribute('data-theme') || 'dark';
                    var next = current === 'dark' ? 'light' : 'dark';
                    document.documentElement.setAttribute('data-theme', next);
                    try { localStorage.setItem('sza-theme', next); } catch (e) { }
                });
            }

            // Language Selector
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
        }());
    </script>

    <!-- WAVE-PARTICLES backdrop -->
    <canvas id="docCanvas" data-wave-particles data-palette="GREEN" data-wave-theme="html" data-wave-wash="--doc-bg" data-intensity="0.35"></canvas>
    <script src="${p}assets/wave-particles.js"></script>

    <!-- In-browser Search Modal Engine -->
    <script src="${p}assets/search.js"></script>
</body>
</html>
"@) | Out-Null

$html = $sb.ToString()

# S2972 check: Liquid markers
if ($html -match '\{\{|\{%') {
    Write-Error "generate-glossary: Output contains Liquid template markers ({{ or {%)."
    exit 1
}

$outDir = Split-Path $outFile -Parent
if (-not $Check -and -not (Test-Path $outDir)) {
    $null = New-Item -ItemType Directory -Force $outDir
}

if ($Check) {
    if (-not (Test-Path $outFile)) {
        Write-Error "generate-glossary: Target file $OutputPath does not exist."
        exit 1
    }
    $existing = Get-Content $outFile -Raw -Encoding utf8
    if ($existing -ne $html) {
        Write-Error "generate-glossary: Target file $OutputPath is outdated compared to termbase source."
        exit 1
    }
    Write-Host "generate-glossary: PASS - $OutputPath is up to date ($($terms.Count) terms)." -ForegroundColor Green
    exit 0
}

[System.IO.File]::WriteAllText($outFile, $html, [System.Text.UTF8Encoding]::new($false))
Write-Host "generate-glossary: Successfully compiled $($terms.Count) terms -> $OutputPath" -ForegroundColor Green
exit 0
