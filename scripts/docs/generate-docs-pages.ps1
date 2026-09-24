# Generator: Documentation HTML Pages Compiler
# Part of S3410 (docs-external-content-decoupling)
# Compiles external Markdown recipe files and snippets from docs/content/ into standalone HTML pages under documentation/

[CmdletBinding()]
param (
    [switch]$Check,
    [string]$ContentDir = "docs/content/recipes",
    [string]$OutputDir = "documentation"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$contentRoot = Join-Path $repoRoot $ContentDir
$outputRoot = Join-Path $repoRoot $OutputDir

if (-not (Test-Path $contentRoot)) {
    Write-Error "generate-docs-pages: Content directory not found at $contentRoot"
    exit 1
}

$recipeFiles = Get-ChildItem -Path $contentRoot -Filter *.md | Sort-Object Name

# A double-quoted YAML scalar escapes its inner quote and backslash; stripping the outer quotes
# alone left a literal \" in the published HTML (S3503). Plain and single-quoted values pass through.
function ConvertFrom-YamlQuotedScalar([string]$value) {
    if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
        return [regex]::Replace($value.Substring(1, $value.Length - 2), '\\(["\\])', '$1')
    }
    return $value
}

function Parse-Frontmatter([string]$rawText) {
    if ($rawText -notmatch '^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$') {
        throw "Missing or malformed frontmatter delimiters (---)"
    }
    $yamlBlock = $Matches[1]
    $bodyBlock = $Matches[2].Trim()

    # Simple robust YAML parser for documentation recipe schema
    $meta = [ordered]@{}
    $currentKey = $null
    $currentList = $null
    $currentDict = $null
    $currentStep = $null
    $inSteps = $false
    $inIngredients = $false
    $inNext = $false
    $inSnippets = $false
    $inTips = $false

    $lines = $yamlBlock -split "\r?\n"
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $line = $lines[$i]
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) { continue }

        # Top level scalar key: value
        if ($line -match '^([a-z0-9_]+):\s*(.*)$' -and -not $line.StartsWith(' ') -and -not $line.StartsWith('-')) {
            $key = $Matches[1]
            $val = $Matches[2].Trim()
            $currentKey = $key
            $inSteps = ($key -eq 'steps')
            $inIngredients = ($key -eq 'ingredients')
            $inNext = ($key -eq 'next_recipes')
            $inSnippets = ($key -eq 'snippets')
            $inTips = ($key -eq 'tips')

            if ($inSteps) {
                $meta['steps'] = [System.Collections.Generic.List[object]]::new()
            } elseif ($inIngredients) {
                $meta['ingredients'] = [System.Collections.Generic.List[string]]::new()
            } elseif ($inNext) {
                $meta['next_recipes'] = [System.Collections.Generic.List[object]]::new()
            } elseif ($inSnippets) {
                $meta['snippets'] = [System.Collections.Generic.List[object]]::new()
            } elseif ($inTips) {
                $meta['tips'] = [System.Collections.Generic.List[string]]::new()
            } elseif ($val -eq '|') {
                $multiTop = [System.Text.StringBuilder]::new()
                while ($i + 1 -lt $lines.Length -and ($lines[$i + 1].StartsWith('  ') -or [string]::IsNullOrWhiteSpace($lines[$i + 1]))) {
                    $i++
                    $multiTop.AppendLine($lines[$i].TrimStart()) | Out-Null
                }
                $meta[$key] = $multiTop.ToString().Trim()
            } else {
                $meta[$key] = ConvertFrom-YamlQuotedScalar $val
            }
            continue
        }

        # List item under ingredients
        if ($inIngredients -and $line -match '^\s*-\s+(.*)$') {
            $meta['ingredients'].Add((ConvertFrom-YamlQuotedScalar $Matches[1].Trim()))
            continue
        }

        if ($inTips -and $line -match '^\s*-\s+(.*)$') {
            $meta['tips'].Add((ConvertFrom-YamlQuotedScalar $Matches[1].Trim()))
            continue
        }

        # Step item
        if ($inSteps) {
            if ($line -match '^\s*-\s+number:\s*(\d+)') {
                $currentStep = [ordered]@{ number = [int]$Matches[1]; text = "" }
                $meta['steps'].Add($currentStep)
                continue
            }
            if ($currentStep) {
                if ($line -match '^\s+([a-z0-9_]+):\s*(.*)$') {
                    $skey = $Matches[1]
                    $sval = $Matches[2].Trim()
                    if ($sval -eq '|') {
                        # Multi-line text block
                        $multiText = [System.Text.StringBuilder]::new()
                        while ($i + 1 -lt $lines.Length -and ($lines[$i + 1].StartsWith('      ') -or [string]::IsNullOrWhiteSpace($lines[$i + 1]))) {
                            $i++
                            $multiText.AppendLine($lines[$i].TrimStart()) | Out-Null
                        }
                        $currentStep[$skey] = $multiText.ToString().Trim()
                    } elseif ($skey -eq 'image' -or $skey -eq 'image_bookmark' -or $skey -eq 'callout') {
                        $subObj = [ordered]@{}
                        $currentStep[$skey] = $subObj
                        while ($i + 1 -lt $lines.Length -and $lines[$i + 1] -match '^\s{6,8}([a-z0-9_]+):\s*(.*)$') {
                            $i++
                            $subK = $Matches[1]
                            $subObj[$subK] = ConvertFrom-YamlQuotedScalar $Matches[2].Trim()
                        }
                    } else {
                        $currentStep[$skey] = ConvertFrom-YamlQuotedScalar $sval
                    }
                }
            }
            continue
        }

        # Snippets item
        if ($inSnippets) {
            if ($line -match '^\s*-\s+title:\s*(.*)$') {
                $snipObj = [ordered]@{ title = $Matches[1].Trim() }
                $meta['snippets'].Add($snipObj)
                while ($i + 1 -lt $lines.Length -and $lines[$i + 1] -match '^\s{4,6}([a-z0-9_]+):\s*(.*)$') {
                    $i++
                    $snipObj[$Matches[1]] = $Matches[2].Trim()
                }
                continue
            }
        }

        # Next recipes item
        if ($inNext) {
            if ($line -match '^\s*-\s+title:\s*(.*)$') {
                $nextObj = [ordered]@{ title = $Matches[1].Trim() }
                $meta['next_recipes'].Add($nextObj)
                while ($i + 1 -lt $lines.Length -and $lines[$i + 1] -match '^\s{4,6}([a-z0-9_]+):\s*(.*)$') {
                    $i++
                    $nextObj[$Matches[1]] = $Matches[2].Trim()
                }
                continue
            }
        }
    }

    return @{
        Meta = $meta
        Body = $bodyBlock
    }
}

function Format-MarkdownInline([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return "" }
    # bold **text**
    $text = [regex]::Replace($text, '\*\*([^*]+)\*\*', '<strong>$1</strong>')
    # code `code`
    $text = [regex]::Replace($text, '`([^`]+)`', '<code>$1</code>')
    # links [text](page:<page_id>) | [text](term:<term_id>) | [text](https://..)
    $text = [regex]::Replace($text, '\[([^\]]+)\]\(([^)\s]+)\)', {
            param($lm)
            Resolve-MarkdownLink $lm.Groups[1].Value $lm.Groups[2].Value
        })
    return $text
}

# Links name a page by its permanent page_id, never by file path, so a page can move without
# breaking its readers; a page that is not written yet renders as a bookmark (S2945 linking rules).
function Resolve-MarkdownLink([string]$label, [string]$target) {
    if ($target.StartsWith('page:')) {
        $pageId = $target.Substring(5)
        if (-not $script:ManifestPages.ContainsKey($pageId)) {
            throw "Link to unknown page_id '$pageId' in $($script:CurrentOutRel)"
        }
        if ($script:PageTargets.ContainsKey($pageId)) {
            $href = Get-RelativeHref $script:CurrentOutRel $script:PageTargets[$pageId]
            return "<a href=`"$href`" class=`"doc-link`">$label</a>"
        }
        $owner = $script:ManifestPages[$pageId].ticket
        return "<span class=`"doc-bookmark`" data-page-id=`"$pageId`" title=`"Covered in $owner`">$label</span>"
    }
    if ($target.StartsWith('term:')) {
        $termId = $target.Substring(5)
        if (-not $script:TermIds.Contains($termId)) {
            throw "Link to unknown termbase id '$termId' in $($script:CurrentOutRel)"
        }
        return "<span class=`"doc-link-term`" data-term=`"$termId`">$label</span>"
    }
    if ($target -match '^https?://') {
        return "<a href=`"$target`" target=`"_blank`" rel=`"noopener`" class=`"doc-link-external`">$label</a>"
    }
    return "<a href=`"$target`" class=`"doc-link`">$label</a>"
}

function Get-RelativeHref([string]$fromRel, [string]$toRel) {
    $fromDir = Split-Path $fromRel -Parent
    if ([string]::IsNullOrEmpty($fromDir)) { return $toRel }
    return [System.IO.Path]::GetRelativePath($fromDir, $toRel).Replace('\', '/')
}

function Get-CorpusNavHtml([string]$outRel, [string]$p) {
    if ($script:CorpusPages.Count -eq 0) { return "" }
    $nav = [System.Text.StringBuilder]::new()
    $groups = $script:CorpusPages | Sort-Object Category, Order, Title | Group-Object Category
    foreach ($group in $groups) {
        $nav.Append("`n                <div class=`"doc-sidebar-section`">`n                    <div class=`"doc-sidebar-title`">$($group.Name)</div>`n                    <ul class=`"doc-sidebar-list`">") | Out-Null
        foreach ($page in $group.Group) {
            $active = if ($page.OutRel -eq $outRel) { ' active' } else { '' }
            $nav.Append("`n                        <li><a href=`"$p$($page.OutRel)`" class=`"doc-sidebar-link$active`">$($page.Title)</a></li>") | Out-Null
        }
        $nav.Append("`n                    </ul>`n                </div>") | Out-Null
    }
    return $nav.ToString()
}

function Format-MarkdownBlock([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return "" }
    $blocks = [regex]::Split($text.Trim(), '\r?\n\s*\r?\n')
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($block in $blocks) {
        $blockLines = @($block -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        $bullets = @($blockLines | Where-Object { $_ -match '^\s*[-*]\s+' })
        $numbered = @($blockLines | Where-Object { $_ -match '^\s*\d+\.\s+' })
        if ($bullets.Count -eq $blockLines.Count) {
            $items = $blockLines | ForEach-Object { "<li>" + (Format-MarkdownInline ($_ -replace '^\s*[-*]\s+', '')) + "</li>" }
            $out.Add("<ul>" + ($items -join '') + "</ul>")
        } elseif ($numbered.Count -eq $blockLines.Count) {
            $items = $blockLines | ForEach-Object { "<li>" + (Format-MarkdownInline ($_ -replace '^\s*\d+\.\s+', '')) + "</li>" }
            $out.Add("<ol>" + ($items -join '') + "</ol>")
        } else {
            $out.Add("<p>" + (Format-MarkdownInline ($blockLines -join ' ')) + "</p>")
        }
    }
    return ($out -join "`n                        ")
}

function ConvertTo-PlainText([string]$markdown) {
    if ([string]::IsNullOrWhiteSpace($markdown)) { return '' }
    $plain = [regex]::Replace($markdown, '\[([^\]]+)\]\([^)]*\)', '$1')
    $plain = $plain -replace '\*\*|__|`', ''
    return ($plain -replace '\s+', ' ').Trim()
}

function ConvertTo-HtmlAttribute([string]$text) {
    return [System.Net.WebUtility]::HtmlEncode(([string]$text))
}

# S2972: the canon sitemap generator reads a page's address only from a `permalink:` line in its
# first 12 lines, and a static HTML file has nowhere else to declare one. `layout: null` keeps
# Jekyll from wrapping the page in a theme layout once it sees front matter.
function Get-PageFrontMatter([string]$outRel) {
    if (-not $outRel.Contains('/')) { return '' }
    return "---`npermalink: /documentation/$outRel`nlayout: null`n---`n"
}

function Get-SeoHeadHtml([hashtable]$m, [string]$outRel) {
    $pageUrl = "$script:SiteBase/documentation/$outRel"
    $title = [string]$m['title']
    $desc = [string]$m['description']
    $ogImage = $script:DefaultOgImage
    $twitterCard = 'summary'
    foreach ($st in @($m['steps'])) {
        if ($st -and $st['image'] -and $st['image']['src']) {
            $ogImage = "$script:SiteBase/documentation/$($st['image']['src'])"
            $twitterCard = 'summary_large_image'
            break
        }
    }

    $crumbs = [System.Collections.Generic.List[object]]::new()
    $crumbs.Add([ordered]@{ '@type' = 'ListItem'; position = 1; name = 'Home'; item = "$script:SiteBase/" })
    $crumbs.Add([ordered]@{ '@type' = 'ListItem'; position = 2; name = 'Documentation'; item = "$script:SiteBase/documentation/" })
    $crumbs.Add([ordered]@{ '@type' = 'ListItem'; position = 3; name = $title; item = $pageUrl })
    $graph = [System.Collections.Generic.List[object]]::new()
    $graph.Add([ordered]@{ '@type' = 'BreadcrumbList'; itemListElement = $crumbs.ToArray() })

    $steps = @($m['steps'] | Where-Object { $_ })
    if ($steps.Count -gt 0) {
        $howToSteps = foreach ($st in $steps) {
            $sid = if ($st['id']) { $st['id'] } else { "step-$($st['number'])" }
            [ordered]@{
                '@type'  = 'HowToStep'
                position = [int]$st['number']
                name     = [string]$st['title']
                text     = (ConvertTo-PlainText $st['text'])
                url      = "$pageUrl#$sid"
            }
        }
        $graph.Add([ordered]@{
                '@type'     = 'HowTo'
                name        = $title
                description = $desc
                inLanguage  = 'en'
                image       = $ogImage
                step        = @($howToSteps)
            })
    } else {
        $graph.Add([ordered]@{
                '@type'     = 'TechArticle'
                headline    = $title
                description = $desc
                inLanguage  = 'en'
                url         = $pageUrl
            })
    }
    $ld = [ordered]@{ '@context' = 'https://schema.org'; '@graph' = $graph.ToArray() }
    $ldJson = ConvertTo-Json $ld -Depth 10 -EscapeHandling EscapeHtml

    $t = ConvertTo-HtmlAttribute "$title - Fast Media Sorter"
    $d = ConvertTo-HtmlAttribute $desc
    return @"
    <link rel="canonical" href="$pageUrl">
    <link rel="alternate" hreflang="en" href="$pageUrl">
    <link rel="alternate" hreflang="x-default" href="$pageUrl">
    <meta property="og:type" content="article">
    <meta property="og:url" content="$pageUrl">
    <meta property="og:title" content="$t">
    <meta property="og:description" content="$d">
    <meta property="og:image" content="$ogImage">
    <meta property="og:locale" content="en_US">
    <meta property="og:site_name" content="Fast Media Sorter &amp; Organizer">
    <meta name="twitter:card" content="$twitterCard">
    <meta name="twitter:title" content="$t">
    <meta name="twitter:description" content="$d">
    <meta name="twitter:image" content="$ogImage">
    <script type="application/ld+json">
$ldJson
    </script>
"@
}

function Render-RecipeHtml([hashtable]$doc, [string]$outRel) {
    $m = $doc.Meta
    $body = Format-MarkdownInline $doc.Body
    $script:CurrentOutRel = $outRel
    $p = '../' * ($outRel.Split('/').Count - 1)
    $corpusNav = Get-CorpusNavHtml $outRel $p
    $seoHead = Get-SeoHeadHtml $m $outRel
    $frontMatter = Get-PageFrontMatter $outRel

    $title = $m['title']
    $desc = $m['description']
    $category = if ($m['category']) { $m['category'] } else { "Documentation" }
    $catSlug = if ($m['category_slug']) { $m['category_slug'] } else { "general" }
    $flavor = if ($m['flavor']) { $m['flavor'] } else { "All Editions" }
    $recNum = if ($m['recipe_number']) { "Recipe #" + $m['recipe_number'] } else { "Recipe" }

    $badgeClass = switch ($catSlug) {
        "audio" { "doc-badge-music" }
        "settings" { "doc-badge-standard" }
        "programs" { "doc-badge-standard" }
        default { "doc-badge-standard" }
    }

    $sb = [System.Text.StringBuilder]::new()

    # Pre-HTML Chrome
    $sb.AppendLine(@"
$frontMatter<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - Fast Media Sorter</title>
    <meta name="description" content="$desc">
$seoHead

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
            <button class="doc-search-trigger" data-search-trigger aria-label="Search Documentation">
                <span>🔍</span>
                <span>Search...</span>
                <kbd>/</kbd>
            </button>

            <nav class="doc-header-nav" aria-label="Main Navigation">
                <a href="${p}sample-recipe.html" class="doc-header-link $(if ($m['canonical_url'] -eq 'documentation/sample-recipe.html') { 'active' })">Audio Recipe</a>
                <a href="${p}sample-settings-recipe.html" class="doc-header-link $(if ($m['canonical_url'] -eq 'documentation/sample-settings-recipe.html') { 'active' })">Settings Recipe</a>
                <a href="${p}sample-program-recipe.html" class="doc-header-link $(if ($m['canonical_url'] -eq 'documentation/sample-program-recipe.html') { 'active' })">Programs Recipe</a>
                <a href="${p}design-system/index.html" class="doc-header-link">Design System</a>
                <a href="https://github.com/SerZhyAle/FastMediaSorter_mob_v2" target="_blank" rel="noopener" class="doc-header-link doc-link-external">GitHub</a>
                
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

                <a href="${p}../index.html" title="FastMediaSorter v2 Home" style="display:flex;align-items:center;"><img src="${p}../apple-touch-icon.png" alt="FastMediaSorter Icon" class="doc-app-icon"></a>
                <button class="doc-theme-btn" id="themeBtn" aria-label="Toggle light/dark theme" title="Toggle theme">◐</button>
            </nav>
        </div>
    </header>

    <div class="doc-container">

        <!-- Breadcrumbs -->
        <nav class="doc-breadcrumbs" aria-label="Breadcrumb">
            <a href="${p}../index.html">Home</a>
            <span class="doc-breadcrumb-separator">/</span>
            <a href="${p}index.html">Documentation</a>
            <span class="doc-breadcrumb-separator">/</span>
            <span>$category</span>
            <span class="doc-breadcrumb-separator">/</span>
            <span class="doc-breadcrumb-current">$title</span>
        </nav>

        <div class="doc-grid">

            <!-- Sidebar -->
            <aside class="doc-sidebar" aria-label="Documentation Navigation">
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">Getting Started</div>
                    <ul class="doc-sidebar-list">
                        <li><a href="${p}index.html" class="doc-sidebar-link">Docs Home</a></li>
                        <li><a href="${p}sample-recipe.html" class="doc-sidebar-link $(if ($m['canonical_url'] -eq 'documentation/sample-recipe.html') { 'active' })">Audio Recipe</a></li>
                        <li><a href="${p}sample-settings-recipe.html" class="doc-sidebar-link $(if ($m['canonical_url'] -eq 'documentation/sample-settings-recipe.html') { 'active' })">Settings Recipe</a></li>
                        <li><a href="${p}sample-program-recipe.html" class="doc-sidebar-link $(if ($m['canonical_url'] -eq 'documentation/sample-program-recipe.html') { 'active' })">Programs Recipe</a></li>
                        <li><a href="${p}design-system/index.html" class="doc-sidebar-link">Component Catalog</a></li>
                    </ul>
                </div>
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">Media Categories</div>
                    <ul class="doc-sidebar-list">
                        <li><a href="${p}sample-recipe.html#music" class="doc-sidebar-link"><span class="doc-badge doc-badge-music doc-badge-sm">Music</span> Audio Player</a></li>
                        <li><a href="${p}index.html#video" class="doc-sidebar-link"><span class="doc-badge doc-badge-video doc-badge-sm">Video</span> Video Player</a></li>
                        <li><a href="${p}index.html#image" class="doc-sidebar-link"><span class="doc-badge doc-badge-image doc-badge-sm">Photos</span> Photo Sorter</a></li>
                    </ul>
                </div>$corpusNav
            </aside>

            <!-- Main Recipe Content -->
            <main class="doc-content" id="main-content">

                <div style="margin-bottom: 1.5rem; display: flex; gap: 0.5rem; align-items: center;">
                    <span class="doc-badge $badgeClass">$category</span>
                    <span class="doc-badge doc-badge-standard">$flavor</span>
                    <span class="doc-badge doc-badge-sm">$recNum</span>
                </div>

                <h1>$title</h1>

                <p class="doc-lead">
                    $body
                </p>
"@) | Out-Null

    if ($m['why']) {
        $whyHtml = Format-MarkdownBlock $m['why']
        $sb.AppendLine(@"

                <h2 id="why">Why You'll Love This</h2>
                        $whyHtml
"@) | Out-Null
    }

    # Prerequisites Card
    if ($m['ingredients'] -and $m['ingredients'].Count -gt 0) {
        $sb.AppendLine(@"

                <!-- Prerequisite Card -->
                <div class="doc-card-prereq">
                    <div class="doc-card-prereq-title">
                        <span class="doc-card-prereq-icon">✓</span>
                        <span>Before You Begin: Ingredients &amp; Prerequisites</span>
                    </div>
                    <ul class="doc-prereq-list">
"@) | Out-Null
        foreach ($ing in $m['ingredients']) {
            $formatted = Format-MarkdownInline $ing
            $sb.AppendLine("                        <li><span class=`"doc-prereq-bullet`">✓</span> $formatted</li>") | Out-Null
        }
        $sb.AppendLine("                    </ul>`n                </div>") | Out-Null
    }

    # Step by step
    if ($m['steps']) {
        foreach ($st in $m['steps']) {
            $snum = $st['number']
            $sid = if ($st['id']) { $st['id'] } else { "step-$snum" }
            $stitle = $st['title']
            $stextHtml = Format-MarkdownBlock $st['text']

            $sb.AppendLine(@"

                <!-- Step $snum -->
                <div class="doc-step" id="$sid">
                    <div class="doc-step-header">
                        <span class="doc-step-badge">$snum</span>
                        <h2 class="doc-step-title">$stitle</h2>
                    </div>
                    <div class="doc-step-body">
                        $stextHtml
"@) | Out-Null

            if ($st['image']) {
                $img = $st['image']
                $sb.AppendLine(@"
                        <figure class="doc-figure">
                            <img src="${p}$($img['src'])" alt="$($img['alt'])" class="doc-screenshot" loading="lazy" />
                            <figcaption>$($img['caption'])</figcaption>
                        </figure>
"@) | Out-Null
            }

            if ($st['image_bookmark']) {
                $bm = $st['image_bookmark']
                $sb.AppendLine(@"
                        <div class="doc-img-bookmark"
                             data-shot-id="$($bm['shot_id'])"
                             data-device-profile="$($bm['device_profile'])"
                             data-screen-state="$($bm['screen_state'])"
                             data-alt="$($bm['alt'])"
                             data-caption="$($bm['caption'])">
                            <div class="doc-img-bookmark-inner">
                                <span class="doc-img-bookmark-badge">Image Bookmark [Planned]</span>
                                <strong class="doc-img-bookmark-title">$($bm['title'])</strong>
                                <p class="doc-img-bookmark-desc">$($bm['desc'])</p>
                                <div class="doc-img-bookmark-meta">
                                    <span>Profile: <code>$($bm['device_profile'])</code></span>
                                    <span>ID: <code>$($bm['shot_id'])</code></span>
                                </div>
                            </div>
                        </div>
"@) | Out-Null
            }

            if ($st['callout']) {
                $co = $st['callout']
                $coClass = if ($co['type'] -eq 'warning') { 'doc-callout-warning' } else { 'doc-callout-tip' }
                $coIcon = if ($co['type'] -eq 'warning') { '⚠️' } else { '💡' }
                $sb.AppendLine(@"
                        <div class="doc-callout $coClass">
                            <div class="doc-callout-title">
                                <span class="doc-callout-icon">$coIcon</span> $($co['title'])
                            </div>
                            <p>$(Format-MarkdownInline $co['text'])</p>
                        </div>
"@) | Out-Null
            }

            $sb.AppendLine("                    </div>`n                </div>") | Out-Null
        }
    }

    if ($m['outcome']) {
        $outcomeHtml = Format-MarkdownBlock $m['outcome']
        $sb.AppendLine(@"

                <h2 id="outcome">What You Get</h2>
                <div class="doc-callout doc-callout-note">
                        $outcomeHtml
                </div>
"@) | Out-Null
    }

    if ($m['tips'] -and $m['tips'].Count -gt 0) {
        $tipItems = ($m['tips'] | ForEach-Object { "<li>" + (Format-MarkdownInline $_) + "</li>" }) -join "`n                        "
        $sb.AppendLine(@"

                <h2 id="tips">Tips and Troubleshooting</h2>
                <div class="doc-callout doc-callout-tip">
                    <ul>
                        $tipItems
                    </ul>
                </div>
"@) | Out-Null
    }

    # External Snippets
    if ($m['snippets'] -and $m['snippets'].Count -gt 0) {
        $sb.AppendLine(@"

                <!-- External Configuration & Listing Snippets -->
                <div class="doc-snippet-section" style="margin-top: 2.5rem;">
                    <h3>External Configuration &amp; Code Snippets</h3>
"@) | Out-Null
        foreach ($snp in $m['snippets']) {
            $snipPath = Join-Path $repoRoot $snp['path']
            $snipContent = if (Test-Path $snipPath) { [System.Web.HttpUtility]::HtmlEncode((Get-Content $snipPath -Raw)) } else { "<!-- Snippet missing at $($snp['path']) -->" }
            $sb.AppendLine(@"
                    <div class="doc-snippet-card" style="margin-bottom: 1.5rem;">
                        <div class="doc-snippet-header" style="font-weight: 600; margin-bottom: 0.5rem;">📄 $($snp['title']) (<code>$($snp['path'])</code>):</div>
                        <pre style="background: var(--doc-bg-card, #161b22); padding: 1rem; border-radius: 8px; border: 1px solid var(--doc-border, #30363d); overflow-x: auto;"><code class="language-$($snp['language'])">$snipContent</code></pre>
                    </div>
"@) | Out-Null
        }
        $sb.AppendLine("                </div>") | Out-Null
    }

    # Next Recipes
    if ($m['next_recipes'] -and $m['next_recipes'].Count -gt 0) {
        $sb.AppendLine(@"

                <!-- What to Try Next Section -->
                <div class="doc-next-section">
                    <div class="doc-next-title">What to Try Next: Related Recipes</div>
                    <div class="doc-next-grid">
"@) | Out-Null
        foreach ($nr in $m['next_recipes']) {
            $nurl = if ($nr['url']) { $nr['url'] } else { "#" }
            $ntarget = if ($nr['target']) { "data-target=`"$($nr['target'])`"" } else { "" }
            $nclass = if ($nr['target']) { "doc-next-card doc-link-bookmark" } else { "doc-next-card" }
            $nbadge = if ($nr['badge']) { $nr['badge'] } else { "Docs" }
            $nbadgeType = if ($nr['badge_type']) { "doc-badge-" + $nr['badge_type'] } else { "doc-badge-docs" }
            if ($nurl.StartsWith('page:')) {
                $nextPageId = $nurl.Substring(5)
                if (-not $script:ManifestPages.ContainsKey($nextPageId)) {
                    throw "Next recipe links to unknown page_id '$nextPageId' in $outRel"
                }
                if ($script:PageTargets.ContainsKey($nextPageId)) {
                    $nurl = Get-RelativeHref $outRel $script:PageTargets[$nextPageId]
                } else {
                    $nr['bookmark_id'] = $nextPageId
                }
            }

            if ($nr['bookmark_id']) {
                $sb.AppendLine(@"
                        <div class="doc-next-card">
                            <span class="doc-badge doc-badge-sm $nbadgeType">$nbadge</span>
                            <h4><span class="doc-bookmark" data-page-id="$($nr['bookmark_id'])">$($nr['title']) [Planned]</span></h4>
                            <p>$($nr['description'])</p>
                        </div>
"@) | Out-Null
            } else {
                $sb.AppendLine(@"
                        <a href="$nurl" class="$nclass" $ntarget>
                            <span class="doc-badge doc-badge-sm $nbadgeType">$nbadge</span>
                            <h4>$($nr['title'])</h4>
                            <p>$($nr['description'])</p>
                        </a>
"@) | Out-Null
            }
        }
        $sb.AppendLine("                    </div>`n                </div>") | Out-Null
    }

    # Table of Contents
    $sb.AppendLine(@"

            </main>

            <!-- Table of Contents -->
            <aside class="doc-toc-wrapper" aria-label="Page Table of Contents">
                <nav class="doc-toc">
                    <div class="doc-toc-title">On this page</div>
                    <ul class="doc-toc-list">
"@) | Out-Null
    if ($m['why']) {
        $sb.AppendLine("                        <li><a href=`"#why`" class=`"doc-toc-link`">Why You'll Love This</a></li>") | Out-Null
    }
    if ($m['steps']) {
        foreach ($st in $m['steps']) {
            $snum = $st['number']
            $sid = if ($st['id']) { $st['id'] } else { "step-$snum" }
            $stitle = $st['title']
            $sb.AppendLine("                        <li><a href=`"#$sid`" class=`"doc-toc-link`">$snum. $stitle</a></li>") | Out-Null
        }
    }
    if ($m['outcome']) {
        $sb.AppendLine("                        <li><a href=`"#outcome`" class=`"doc-toc-link`">What You Get</a></li>") | Out-Null
    }
    if ($m['tips'] -and $m['tips'].Count -gt 0) {
        $sb.AppendLine("                        <li><a href=`"#tips`" class=`"doc-toc-link`">Tips and Troubleshooting</a></li>") | Out-Null
    }
    $sb.AppendLine(@"
                    </ul>
                </nav>
            </aside>

        </div><!-- /.doc-grid -->

    </div><!-- /.doc-container -->

    <footer class="doc-footer">
        <div class="doc-footer-inner">
            <div>Fast Media Sorter &copy; 2026 SerZhyAle. Free and open-source Android organizer.</div>
            <div class="doc-footer-links">
                <a href="${p}../privacy.html">Privacy Policy</a>
                <a href="${p}design-system/index.html">Component System</a>
                <a href="https://github.com/SerZhyAle/FastMediaSorter_mob_v2" target="_blank" rel="noopener">GitHub</a>
            </div>
        </div>
    </footer>

    <!-- Interactive scripts: Theme switcher & 13-Language selector -->
    <script>
        (function () {
            var btn = document.getElementById('themeBtn');
            if (btn) {
                btn.addEventListener('click', function () {
                    var current = document.documentElement.getAttribute('data-theme') || 'dark';
                    var next = current === 'dark' ? 'light' : 'dark';
                    document.documentElement.setAttribute('data-theme', next);
                    try { localStorage.setItem('sza-theme', next); } catch (e) { }
                });
            }

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

            var urlParams = new URLSearchParams(window.location.search);
            var activeLang = urlParams.get('lang') || localStorage.getItem('sza-docs-lang') || 'en';
            
            var items = document.querySelectorAll('.doc-lang-item');
            items.forEach(function (item) {
                var lang = item.getAttribute('data-lang');
                if (lang === activeLang) {
                    item.classList.add('active');
                    if (langBtn) {
                        var name = item.querySelector('span').textContent;
                        langBtn.textContent = 'Language: ' + name;
                    }
                } else {
                    item.classList.remove('active');
                }
                item.addEventListener('click', function (e) {
                    try { localStorage.setItem('sza-docs-lang', lang); } catch (err) { }
                });
            });
        }());
    </script>

    <!-- WAVE-PARTICLES backdrop: the contract's reference implementation, served byte-identical -->
    <canvas id="docCanvas" data-wave-particles data-palette="GREEN" data-wave-theme="html" data-wave-wash="--doc-bg" data-intensity="0.35"></canvas>
    <script src="${p}assets/wave-particles.js"></script>

    <!-- In-browser Search Modal Engine -->
    <script src="${p}assets/search.js"></script>
</body>
</html>
"@) | Out-Null

    return $sb.ToString()
}

Write-Host "=== Documentation HTML Pages Generator ===" -ForegroundColor Cyan
Write-Host "Found $($recipeFiles.Count) external Markdown recipes in $ContentDir"

$compiledCount = 0
$mismatches = 0

$script:SiteBase = ([string]((Get-Content (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json).site.baseUrl)).TrimEnd('/')
# store_assets/ is excluded from the Jekyll build, so the landing pages' screenshot answers 404;
# the touch icon is the one brand image the site is guaranteed to serve.
$script:DefaultOgImage = "$script:SiteBase/apple-touch-icon.png"

$script:ManifestPages = @{}
$pageManifestPath = Join-Path $repoRoot 'docs/docs-pages-manifest.jsonl'
if (Test-Path $pageManifestPath) {
    Get-Content $pageManifestPath -Encoding utf8 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object {
        $pageRecord = ConvertFrom-Json $_
        $script:ManifestPages[$pageRecord.page_id] = $pageRecord
    }
}
$script:TermIds = [System.Collections.Generic.HashSet[string]]::new()
$termbasePath = Join-Path $repoRoot 'docs/termbase.jsonl'
if (Test-Path $termbasePath) {
    Get-Content $termbasePath -Encoding utf8 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object {
        $null = $script:TermIds.Add((ConvertFrom-Json $_).id)
    }
}

# First pass: every recipe's output path must be known before any page renders, because a page
# links its siblings by page_id and draws the corpus sidebar from all of them.
$parsedRecipes = [System.Collections.Generic.List[object]]::new()
$script:PageTargets = @{}
$script:CorpusPages = [System.Collections.Generic.List[object]]::new()
foreach ($rf in $recipeFiles) {
    $raw = Get-Content $rf.FullName -Raw -Encoding utf8
    $parsed = Parse-Frontmatter $raw
    $canonical = [string]$parsed.Meta['canonical_url']
    $outRel = if ($canonical -match '^documentation/(.+/.+\.html)$') {
        $Matches[1]
    } else {
        [System.IO.Path]::ChangeExtension($rf.Name, '.html')
    }
    $parsedRecipes.Add([PSCustomObject]@{ Parsed = $parsed; OutRel = $outRel })
    $pageId = [string]$parsed.Meta['page_id']
    if ($pageId) { $script:PageTargets[$pageId] = $outRel }
    if ($outRel.Contains('/')) {
        $script:CorpusPages.Add([PSCustomObject]@{
                OutRel   = $outRel
                Title    = $parsed.Meta['nav_title'] ?? $parsed.Meta['title']
                Category = $parsed.Meta['category']
                Order    = $parsed.Meta['recipe_number']
            })
    }
}

foreach ($pr in $parsedRecipes) {
    $html = Render-RecipeHtml $pr.Parsed $pr.OutRel
    # S2972: a page with front matter goes through Liquid on the Pages build, where a stray
    # template marker either aborts the whole site build or silently eats page text.
    if ($pr.OutRel.Contains('/') -and $html -match '\{\{|\{%') {
        Write-Error "generate-docs-pages: $($pr.OutRel) contains a Liquid marker ({{ or {%) - Jekyll would evaluate it; reword the recipe source."
        exit 1
    }

    $outFileName = $pr.OutRel
    $outFilePath = Join-Path $outputRoot $outFileName
    $outFileDir = Split-Path $outFilePath -Parent
    if (-not $Check -and -not (Test-Path $outFileDir)) {
        $null = New-Item -ItemType Directory -Force $outFileDir
    }

    if ($Check) {
        if (-not (Test-Path $outFilePath)) {
            Write-Host "  [MISSING] $outFileName does not exist on disk" -ForegroundColor Red
            $mismatches++
        } else {
            $existing = Get-Content $outFilePath -Raw -Encoding utf8
            if ($existing -ne $html) {
                Write-Host "  [OUTDATED] $outFileName differs from compiled Markdown source" -ForegroundColor Yellow
                $mismatches++
            } else {
                Write-Host "  [CURRENT]  $outFileName matches Markdown source" -ForegroundColor Green
            }
        }
    } else {
        # No BOM: Jekyll detects front matter only when the file starts with `---`.
        [System.IO.File]::WriteAllText($outFilePath, $html, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  [COMPILED] -> $outFileName" -ForegroundColor Green
        $compiledCount++
    }
}

if ($Check) {
    if ($mismatches -gt 0) {
        Write-Error "generate-docs-pages: $mismatches file(s) are outdated or missing. Re-run without -Check."
        exit 1
    }
    Write-Host "`nAll documentation HTML pages are up to date with external Markdown sources." -ForegroundColor Green
} else {
    Write-Host "`nSuccessfully compiled $compiledCount documentation pages from external Markdown." -ForegroundColor Green
}
exit 0
