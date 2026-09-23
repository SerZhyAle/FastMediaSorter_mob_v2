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

            if ($inSteps) {
                $meta['steps'] = [System.Collections.Generic.List[object]]::new()
            } elseif ($inIngredients) {
                $meta['ingredients'] = [System.Collections.Generic.List[string]]::new()
            } elseif ($inNext) {
                $meta['next_recipes'] = [System.Collections.Generic.List[object]]::new()
            } elseif ($inSnippets) {
                $meta['snippets'] = [System.Collections.Generic.List[object]]::new()
            } else {
                # Clean quotes
                if ($val.StartsWith('"') -and $val.EndsWith('"') -and $val.Length -ge 2) {
                    $val = $val.Substring(1, $val.Length - 2)
                }
                $meta[$key] = $val
            }
            continue
        }

        # List item under ingredients
        if ($inIngredients -and $line -match '^\s*-\s+(.*)$') {
            $item = $Matches[1].Trim()
            if ($item.StartsWith('"') -and $item.EndsWith('"') -and $item.Length -ge 2) {
                $item = $item.Substring(1, $item.Length - 2)
            }
            $meta['ingredients'].Add($item)
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
                            $subV = $Matches[2].Trim()
                            if ($subV.StartsWith('"') -and $subV.EndsWith('"') -and $subV.Length -ge 2) {
                                $subV = $subV.Substring(1, $subV.Length - 2)
                            }
                            $subObj[$subK] = $subV
                        }
                    } else {
                        if ($sval.StartsWith('"') -and $sval.EndsWith('"') -and $sval.Length -ge 2) {
                            $sval = $sval.Substring(1, $sval.Length - 2)
                        }
                        $currentStep[$skey] = $sval
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
    return $text
}

function Render-RecipeHtml([hashtable]$doc) {
    $m = $doc.Meta
    $body = $doc.Body

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
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - Fast Media Sorter</title>
    <meta name="description" content="$desc">
    
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
</head>

<body class="doc-body">

    <!-- Header Chrome -->
    <header class="doc-header">
        <div class="doc-header-inner">
            <div style="display: flex; align-items: center; gap: 1rem;">
                <a class="doc-header-brand" href="../index.html">Fast Media Sorter<span style="color: var(--doc-accent, #3fb950);">.</span></a>
                <a href="index.html" class="doc-badge doc-badge-sm" style="text-decoration: none; color: var(--doc-text-secondary);">Docs</a>
            </div>

            <!-- Header Quick Search Button -->
            <button class="doc-search-trigger" data-search-trigger aria-label="Search Documentation">
                <span>🔍</span>
                <span>Search...</span>
                <kbd>/</kbd>
            </button>

            <nav class="doc-header-nav" aria-label="Main Navigation">
                <a href="sample-recipe.html" class="doc-header-link $(if ($m['canonical_url'] -eq 'documentation/sample-recipe.html') { 'active' })">Audio Recipe</a>
                <a href="sample-settings-recipe.html" class="doc-header-link $(if ($m['canonical_url'] -eq 'documentation/sample-settings-recipe.html') { 'active' })">Settings Recipe</a>
                <a href="sample-program-recipe.html" class="doc-header-link $(if ($m['canonical_url'] -eq 'documentation/sample-program-recipe.html') { 'active' })">Programs Recipe</a>
                <a href="design-system/index.html" class="doc-header-link">Design System</a>
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
                        <li><a href="index.html" class="doc-sidebar-link">Docs Home</a></li>
                        <li><a href="sample-recipe.html" class="doc-sidebar-link $(if ($m['canonical_url'] -eq 'documentation/sample-recipe.html') { 'active' })">Audio Recipe</a></li>
                        <li><a href="sample-settings-recipe.html" class="doc-sidebar-link $(if ($m['canonical_url'] -eq 'documentation/sample-settings-recipe.html') { 'active' })">Settings Recipe</a></li>
                        <li><a href="sample-program-recipe.html" class="doc-sidebar-link $(if ($m['canonical_url'] -eq 'documentation/sample-program-recipe.html') { 'active' })">Programs Recipe</a></li>
                        <li><a href="design-system/index.html" class="doc-sidebar-link">Component Catalog</a></li>
                    </ul>
                </div>
                <div class="doc-sidebar-section">
                    <div class="doc-sidebar-title">Media Categories</div>
                    <ul class="doc-sidebar-list">
                        <li><a href="sample-recipe.html#music" class="doc-sidebar-link"><span class="doc-badge doc-badge-music doc-badge-sm">Music</span> Audio Player</a></li>
                        <li><a href="index.html#video" class="doc-sidebar-link"><span class="doc-badge doc-badge-video doc-badge-sm">Video</span> Video Player</a></li>
                        <li><a href="index.html#image" class="doc-sidebar-link"><span class="doc-badge doc-badge-image doc-badge-sm">Photos</span> Photo Sorter</a></li>
                    </ul>
                </div>
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
            $stext = Format-MarkdownInline $st['text']

            # paragraph handling
            $stextHtml = if ($stext.Contains("`n")) {
                "<p>" + ($stext -replace "\r?\n\r?\n", "</p>`n<p>") + "</p>"
            } else {
                "<p>$stext</p>"
            }

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
                            <img src="$($img['src'])" alt="$($img['alt'])" class="doc-screenshot" loading="lazy" />
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
                            <p>$($co['text'])</p>
                        </div>
"@) | Out-Null
            }

            $sb.AppendLine("                    </div>`n                </div>") | Out-Null
        }
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
    if ($m['steps']) {
        foreach ($st in $m['steps']) {
            $snum = $st['number']
            $sid = if ($st['id']) { $st['id'] } else { "step-$snum" }
            $stitle = $st['title']
            $sb.AppendLine("                        <li><a href=`"#$sid`" class=`"doc-toc-link`">$snum. $stitle</a></li>") | Out-Null
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
            <div>Fast Media Sorter &copy; 2026 SerZhyAle. Free and open-source Android organizer.</div>
            <div class="doc-footer-links">
                <a href="../privacy.html">Privacy Policy</a>
                <a href="design-system/index.html">Component System</a>
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
    <script src="assets/wave-particles.js"></script>

    <!-- In-browser Search Modal Engine -->
    <script src="assets/search.js"></script>
</body>
</html>
"@) | Out-Null

    return $sb.ToString()
}

Write-Host "=== Documentation HTML Pages Generator ===" -ForegroundColor Cyan
Write-Host "Found $($recipeFiles.Count) external Markdown recipes in $ContentDir"

$compiledCount = 0
$mismatches = 0

foreach ($rf in $recipeFiles) {
    $raw = Get-Content $rf.FullName -Raw -Encoding utf8
    $parsed = Parse-Frontmatter $raw
    $html = Render-RecipeHtml $parsed

    $outFileName = [System.IO.Path]::ChangeExtension($rf.Name, '.html')
    $outFilePath = Join-Path $outputRoot $outFileName

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
        [System.IO.File]::WriteAllText($outFilePath, $html, [System.Text.Encoding]::UTF8)
        Write-Host "  [COMPILED] $($rf.Name) -> $outFileName" -ForegroundColor Green
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
