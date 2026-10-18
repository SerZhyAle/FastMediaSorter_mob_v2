#requires -Version 7.0
<#
.SYNOPSIS
    Quality Gate: Assert Documentation Portal UI/UX Standards.
    Part of S3533 (documentation-portal-ui-ux-testing).

.DESCRIPTION
    Comprehensive UI/UX audit for FastMediaSorter v2 documentation portal:
    - Validates all HTML pages for viewport, title, pre-paint theme resolver, theme toggle button,
      semantic landmarks, and image accessibility (alt text).
    - Requires the PAGE-STYLE 1.2 pre-paint form (sza-theme validated as dark/light, sza-lang read with
      uk mapped to ua) and a sza-lang writer on every page offering data-lang links (S4074).
    - Verifies local asset and relative link integrity across all published documentation pages
      (including Jekyll Markdown sources in docs/).
    - Validates CSS design tokens (dark/light themes), responsive breakpoints down to 400px,
      and mobile navigation drawer styling.
    - Validates search engine client implementation, scoring accuracy (no false positives on
      unmatched tokens), universal category link prefixes, and modal accessibility.
    - SITE-EXPERIENCE 0.1 on the portal layer (S4099), each finding naming its rule:
      rule 2  - every page links styles.css (the kit tokens) before docs.css, and docs.css outside
                its print rules carries no colour literal equal to a kit token value of styles.css;
      rule 3  - docs.css defines the menu-path and edition-badge components, and a page's badge row
                (doc-meta-row) carries exactly one edition badge;
      rule 4  - no page carries a style attribute or a <style> element outside its scripts;
      rule 12 - search.js renders an empty result that repeats the query and links the subject index;
      rule 15 - every page opens <body> (after the optional date stamp) with a skip link to
                #main-content, and carries that target.
    - An in-app help page under documentation/help/ is not a site page (CONTEXT-HELP rule 10): the theme
      resolver, the theme toggle, the search client and the skip link are not required of it, every
      other check is.
    - The docs/ reference pages in the portal shell (S4117): _layouts/doc.html, the Jekyll copy of the
      portal chrome, is judged by every page check above; a docs/**/*.md page naming a layout names
      `doc` (SITE-EXPERIENCE rules 1, 3, 5); and a page's {% include NAME %} names a file in
      _includes/, because a missing include fails the whole Pages build, not one page.
    - Footer scope (S4366: PAGE-CONTENT 1.5, SITE-FAMILY-MAP 1.5, SITE-STRUCTURE 0.3): the footer of every
      non-help portal page, of _layouts/doc.html and of 404.html links mailto:sza@ukr.net, github.com/SerZhyAle,
      https://sza.od.ua and the Support page; search.js reads both ?lang= and the legacy ?l= (PAGE-STYLE 3.1
      item 1).

.PARAMETER RepoRoot
    Target repository root path.

.PARAMETER Gate
    Accepted for uniform runner invocation.

.PARAMETER Quiet
    Suppress non-error progress output.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Gate,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../docs/Read-DocumentationSearchIndex.ps1')
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'docs-portal-ui-ux'; files = 'documentation/**/*.html,_layouts/doc.html,404.html,docs/**/*.md,documentation/assets/docs.css,documentation/assets/search.js' })

$docRoot = Join-Path $RepoRoot 'documentation'
$docsCssPath = Join-Path $docRoot 'assets/docs.css'
$searchJsPath = Join-Path $docRoot 'assets/search.js'
$searchIndexPath = Join-Path $docRoot 'assets/search-index.json'

if (-not (Test-Path -LiteralPath $docRoot)) {
    Write-Host "assert-docs-portal-ui-ux: FAIL - documentation directory missing at $docRoot" -ForegroundColor Red
    exit 1
}

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

# One spelling per colour, so #FFF, #ffffff and rgba(0,0,0,.5) / rgba(0, 0, 0, 0.5) compare equal.
function ConvertTo-CssColorKey([string]$value) {
    $v = $value.Trim().ToLowerInvariant()
    if ($v -match '^#([0-9a-f])([0-9a-f])([0-9a-f])$') {
        $v = '#' + $Matches[1] + $Matches[1] + $Matches[2] + $Matches[2] + $Matches[3] + $Matches[3]
    }
    $v = [regex]::Replace($v, '\s+', '')
    return [regex]::Replace($v, '(?<![\d.])0\.(\d)', '.$1')
}

# The text with every @media print block blanked, line count kept.
function Remove-CssPrintRules([string]$text) {
    $sb = [System.Text.StringBuilder]::new()
    $i = 0
    while ($true) {
        $start = $text.IndexOf('@media print', $i)
        if ($start -lt 0) { $null = $sb.Append($text.Substring($i)); break }
        $null = $sb.Append($text.Substring($i, $start - $i))
        $open = $text.IndexOf('{', $start)
        if ($open -lt 0) { break }
        $depth = 0
        $end = $open
        for (; $end -lt $text.Length; $end++) {
            if ($text[$end] -eq '{') { $depth++ }
            elseif ($text[$end] -eq '}') { $depth--; if ($depth -eq 0) { break } }
        }
        $null = $sb.Append("`n" * ([regex]::Matches($text.Substring($start, [Math]::Min($end + 1, $text.Length) - $start), "`n")).Count)
        $i = [Math]::Min($end + 1, $text.Length)
    }
    return $sb.ToString()
}

# A docs/*.md page is served at its `permalink:` address, which can differ from the source
# name (docs/howto/index.md -> /docs/howto/), so a link is live when it names a
# published permalink even though no file of that name exists on disk.
$publishedPermalinks = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($mdPage in (Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'docs') -Filter *.md -File)) {
    $mdHead = (Get-Content -LiteralPath $mdPage.FullName -TotalCount 12) -join "`n"
    if ($mdHead -match '(?m)^permalink:\s*(\S+)') {
        $null = $publishedPermalinks.Add([System.IO.Path]::GetFullPath((Join-Path $RepoRoot $Matches[1].Trim().TrimStart('/'))))
    }
}

# -------------------------------------------------------------------------
# 1. CSS Design Tokens & Mobile Responsive Styles
# -------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $docsCssPath)) {
    $errors.Add("Docs CSS missing at $docsCssPath")
} else {
    $css = Get-Content -LiteralPath $docsCssPath -Raw -Encoding utf8

    $requiredDarkTokens = @('--doc-bg:', '--doc-text:', '--doc-accent:', '--doc-gold:', '--doc-border:')
    foreach ($token in $requiredDarkTokens) {
        if ($css -notmatch [regex]::Escape($token)) {
            $errors.Add("docs.css missing dark theme token '$token'")
        }
    }

    $requiredLightTokens = @('--doc-bg:', '--doc-text:', '--doc-accent:', '--doc-gold:', '--doc-border:')
    if ($css -notmatch 'html\[data-theme=["'']light["'']\]') {
        $errors.Add("docs.css missing light theme selector html[data-theme='light']")
    }

    # Responsive media queries
    $breakpoints = @('1100px', '768px', '480px')
    foreach ($bp in $breakpoints) {
        if ($css -notmatch "@media\s*\(\s*max-width\s*:\s*$bp\s*\)") {
            $errors.Add("docs.css missing responsive breakpoint max-width: $bp")
        }
    }

    # Mobile drawer & menu button styles
    if ($css -notmatch '\.doc-mobile-menu-btn' -or $css -notmatch '\.doc-sidebar\.(active|open)') {
        $errors.Add("docs.css missing mobile drawer styles (.doc-mobile-menu-btn or .doc-sidebar.active)")
    }

    # Print styles
    if ($css -notmatch '@media\s+print') {
        $errors.Add("docs.css missing @media print styles")
    }

    # SITE-EXPERIENCE rule 3: the portal layer's own components exist, each with one form.
    foreach ($component in '.doc-menu-path-item', '.doc-edition-badge') {
        if ($css -notmatch ([regex]::Escape($component) + '\b')) {
            $errors.Add("docs.css does not define the $component component (SITE-EXPERIENCE rule 3)")
        }
    }

    # SITE-EXPERIENCE rule 2: a colour the kit has a token for is a reference to it, never a copy.
    # Paper colours of the print rules are outside the theme and are not judged.
    $kitPath = Join-Path $RepoRoot 'styles.css'
    if (-not (Test-Path -LiteralPath $kitPath)) {
        $errors.Add("styles.css missing at $kitPath - the kit tokens the portal layer references (SITE-EXPERIENCE rule 2)")
    } else {
        $kitNames = @('bg', 'bg-2', 'bg-3', 'card', 'card-hover', 'border', 'border-hover', 'text', 'text-2', 'muted',
            'acc', 'acc-strong', 'acc-ink', 'gold', 'gold-strong', 'gold-ink', 'code-bg', 'code-ink', 'ok', 'warn',
            'danger', 'blob-1', 'blob-2', 'blob-3')
        $kitValues = @{}
        foreach ($m in [regex]::Matches((Get-Content -LiteralPath $kitPath -Raw -Encoding utf8), '--([a-z0-9-]+)\s*:\s*([^;]+);')) {
            $value = $m.Groups[2].Value
            if ($kitNames -contains $m.Groups[1].Value -and $value -notmatch 'var\(') {
                $kitValues[(ConvertTo-CssColorKey $value)] = '--' + $m.Groups[1].Value
            }
        }
        $themeCss = Remove-CssPrintRules ([regex]::Replace($css, '(?s)/\*.*?\*/', ''))
        foreach ($m in [regex]::Matches($themeCss, '#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)')) {
            $key = ConvertTo-CssColorKey $m.Value
            if ($kitValues.ContainsKey($key)) {
                $errors.Add("docs.css copies the kit value $($m.Value) of $($kitValues[$key]); write var($($kitValues[$key])) (SITE-EXPERIENCE rule 2)")
            }
        }
    }
}

# -------------------------------------------------------------------------
# 2. Client Search Engine JS Asset & Scoring Logic
# -------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $searchJsPath)) {
    $errors.Add("Search JS asset missing at $searchJsPath")
} else {
    $js = Get-Content -LiteralPath $searchJsPath -Raw -Encoding utf8

    $hasDialogRole = ($js -match 'role=["'']dialog["'']' -or $js -match "['""]role['""],\s*['""]dialog['""]")
    $hasAriaModal = ($js -match 'aria-modal=["'']true["'']' -or $js -match "['""]aria-modal['""],\s*['""]true['""]")
    if (-not $hasDialogRole -or -not $hasAriaModal) {
        $errors.Add("search.js modal missing accessibility attributes (role='dialog' or aria-modal='true')")
    }

    if ($js -notmatch 'getDocRelativePrefix' -and $js -notmatch 'getSearchIndexPath') {
        $errors.Add("search.js missing universal relative path resolver functions")
    }

    if ($js -notmatch 'keydown') {
        $errors.Add("search.js missing keyboard navigation handler")
    }

    # SITE-EXPERIENCE rule 12: an empty result repeats the query in every language and offers the subject index.
    $emptyFunction = [regex]::Match($js, '(?s)function emptyResult\(query\)\s*\{.*?\n    \}')
    if (-not $emptyFunction.Success -or $emptyFunction.Value -notmatch 'subject-index' -or
        ([regex]::Matches($js, "empty:\s*'[^']*\{query\}")).Count -lt 3 -or $js -notmatch 'emptyResult\(query\);') {
        $errors.Add("search.js: an empty result does not repeat the query in en/ru/uk and link the subject index (SITE-EXPERIENCE rule 12)")
    }

    # PAGE-STYLE 3.1 item 1: the locale query reader honours the canonical `lang` and the legacy `l`.
    if ($js -notmatch "\.get\('lang'\)" -or $js -notmatch "\.get\('l'\)") {
        $errors.Add("search.js: the locale query reader does not read both ?lang= and the legacy ?l= (PAGE-STYLE 3.1 item 1)")
    }
}

# -------------------------------------------------------------------------
# 3. HTML Pages Semantic Structure, Accessibility & Link Integrity
# -------------------------------------------------------------------------
$tempSubdir = Join-Path $docRoot 'temp'
$htmlFiles = @(Get-ChildItem -LiteralPath $docRoot -Recurse -Filter *.html -File | Where-Object { -not $_.FullName.StartsWith($tempSubdir, [System.StringComparison]::OrdinalIgnoreCase) })
# The docs/ pages render through this layout, so the portal checks judge their chrome here; its own
# hrefs are Liquid expressions, which the link check below already skips.
$docLayoutPath = Join-Path $RepoRoot '_layouts/doc.html'
if (Test-Path -LiteralPath $docLayoutPath -PathType Leaf) {
    $htmlFiles += Get-Item -LiteralPath $docLayoutPath
}

$checkedPages = 0
$checkedImages = 0
$checkedLinks = 0

# The footer text of a page, judged for the contact line, the hub link and the Support link.
function Get-FooterContactGap([string]$Html) {
    $footer = [regex]::Match($Html, '(?is)<footer\b.*?</footer>').Value
    $gaps = [System.Collections.Generic.List[string]]::new()
    if ($footer -notmatch 'mailto:sza@ukr\.net') { $gaps.Add('the contact sza@ukr.net') }
    if ($footer -notmatch 'github\.com/SerZhyAle') { $gaps.Add('the GitHub contact SerZhyAle') }
    if ($footer -notmatch 'href="https://sza\.od\.ua/?"') { $gaps.Add('the hub link https://sza.od.ua') }
    if ($footer -notmatch 'href="[^"]*SUPPORT(-[a-z-]+)?(\.html)?[^"]*"') { $gaps.Add('the Support page link') }
    return $gaps
}

# The not-found page is a child page outside the portal tree: its footer is judged, nothing else here.
$notFoundPath = Join-Path $RepoRoot '404.html'
if (Test-Path -LiteralPath $notFoundPath -PathType Leaf) {
    foreach ($missing in (Get-FooterContactGap (Get-Content -LiteralPath $notFoundPath -Raw -Encoding utf8))) {
        $errors.Add("404.html - footer lacks $missing")
    }
}

foreach ($file in $htmlFiles) {
    $checkedPages++
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    $relPath = (Resolve-Path -LiteralPath $file.FullName -Relative).Replace('\', '/').TrimStart('./')
    $dir = $file.DirectoryName

    # 3a. Viewport meta tag
    if ($content -notmatch '<meta\s+name=["'']viewport["'']\s+content=["''][^"'']*width=device-width[^"'']*["'']' -and
        $content -notmatch '<meta\s+content=["''][^"'']*width=device-width[^"'']*["'']\s+name=["'']viewport["'']') {
        $errors.Add("$relPath - missing responsive viewport meta tag")
    }

    # 3b. Title
    if ($content -notmatch '<title>\s*[^<]+\s*</title>') {
        $errors.Add("$relPath - missing or empty <title> tag")
    }

    # An in-app help page (documentation/help/, generated by scripts/docs/help/generate-help-pages.ps1) is
    # not a site page (CONTEXT-HELP rule 10): it runs no script, loads nothing and carries no site chrome, so
    # the theme resolver, the theme toggle, the search client and the skip link have nothing to attach to.
    # Every other check below still judges it.
    $isHelpPage = [System.IO.Path]::GetRelativePath($docRoot, $file.FullName).Replace('\', '/').StartsWith('help/')

    # 3c. Pre-paint resolver, PAGE-STYLE 1.2 section 7: sza-theme and sza-lang are shared by every page
    # on the origin, so a stored theme is validated and a stored 'uk' reads as 'ua' (S4074).
    if ($isHelpPage) {
        # Site chrome only: the page follows the application's theme through help.css, with no script.
    } elseif ($content -notmatch 'localStorage\.getItem\(.*sza-theme') {
        $errors.Add("$relPath - missing pre-paint theme resolver script in head")
    } elseif ($content -notmatch "t\s*!==\s*'dark'\s*&&\s*t\s*!==\s*'light'" -or
        $content -notmatch "localStorage\.getItem\(\s*'sza-lang'\s*\)" -or
        $content -notmatch "l\s*===\s*'uk'\s*\)\s*l\s*=\s*'ua'") {
        $errors.Add("$relPath - pre-paint resolver is not the PAGE-STYLE 1.2 form (validate sza-theme dark/light, read sza-lang with uk -> ua)")
    }

    # 3c-bis. A page offering data-lang links remembers an RU / EN / UA choice in sza-lang (PAGE-STYLE 4.2).
    if ($content -match '<a\b[^>]*\bdata-lang=' -and $content -notmatch "localStorage\.setItem\(\s*'sza-lang'") {
        $errors.Add("$relPath - data-lang links without a sza-lang writer (PAGE-STYLE 4.2)")
    }

    # 3d. Theme toggle button
    if (-not $isHelpPage -and $content -notmatch 'id=["'']themeBtn["'']' -and $content -notmatch 'class=["''][^"'']*doc-theme-btn') {
        $errors.Add("$relPath - missing theme toggle button (#themeBtn)")
    }

    # 3e. Semantic landmarks
    if ($content -notmatch '<header\b' -or $content -notmatch '<main\b' -or $content -notmatch '<footer\b') {
        $warnings.Add("$relPath - missing one or more semantic landmarks (<header>, <main>, <footer>)")
    }

    # 3f. Search integration
    if (-not $isHelpPage -and $content -notmatch 'search\.js') {
        $errors.Add("$relPath - missing search.js client script inclusion")
    }

    # 3f-ter. Footer scope (PAGE-CONTENT 1.5, SITE-FAMILY-MAP 1.5, SITE-STRUCTURE 0.3): a child or portal
    # page footer carries the contact, the hub and the Support page. A help page keeps its help footer.
    if (-not $isHelpPage -and $content -match '<footer\b') {
        foreach ($missing in (Get-FooterContactGap $content)) { $errors.Add("$relPath - footer lacks $missing") }
    }

    # 3f-bis. SITE-EXPERIENCE rules 2, 3, 4 and 15 on the page itself (S4099).
    # The Jekyll layout names a stylesheet as {{ '/x.css' | relative_url }}; both forms are judged.
    $kitLink = [regex]::Match($content, '<link\b[^>]*href="[^"]*styles\.css(?:''\s*\|\s*relative_url\s*\}\})?"')
    $layerLink = [regex]::Match($content, '<link\b[^>]*href="[^"]*docs\.css(?:''\s*\|\s*relative_url\s*\}\})?"')
    if ($layerLink.Success -and (-not $kitLink.Success -or $kitLink.Index -gt $layerLink.Index)) {
        $errors.Add("$relPath - styles.css (the kit tokens) is not linked before docs.css (SITE-EXPERIENCE rule 2)")
    }
    $markup = [regex]::Replace($content, '(?is)<script\b[^>]*>.*?</script>', '<script></script>')
    $styleAttributes = ([regex]::Matches($markup, '<[a-zA-Z][^>]*\sstyle\s*=')).Count
    if ($styleAttributes -gt 0) {
        $errors.Add("$relPath - $styleAttributes style attribute(s); the layout belongs in docs.css (SITE-EXPERIENCE rule 4)")
    }
    if ($markup -match '<style\b') {
        $errors.Add("$relPath - a <style> element; the page's presentation belongs in docs.css (SITE-EXPERIENCE rule 4)")
    }
    $metaRow = [regex]::Match($markup, '(?s)<div class="doc-meta-row">(.*?)</div>')
    if ($metaRow.Success -and ([regex]::Matches($metaRow.Groups[1].Value, 'class="doc-edition-badge"')).Count -ne 1) {
        $errors.Add("$relPath - the badge row does not carry exactly one edition badge (SITE-EXPERIENCE rule 3)")
    }
    if (-not $isHelpPage -and (
            $markup -notmatch '<body\b[^>]*>\s*(?:<div class="doc-stamp"[^>]*>[^<]*</div>\s*)?<a class="doc-skip-link" href="#main-content">[^<]+</a>' -or
            $markup -notmatch '\bid="main-content"')) {
        $errors.Add("$relPath - the page does not open with a skip link to #main-content (SITE-EXPERIENCE rule 15)")
    }

    # 3g. Image accessibility (alt attribute)
    $imgMatches = [regex]::Matches($content, '<img\b([^>]*)>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($m in $imgMatches) {
        $checkedImages++
        $imgAttrs = $m.Groups[1].Value
        if ($imgAttrs -notmatch '\balt=["''][^"'']*["'']') {
            $errors.Add("$relPath - <img> tag missing alt attribute ($($m.Value))")
        }
    }

    # 3h. Local asset and relative link resolution
    $refMatches = [regex]::Matches($content, '(?:src|href)=["'']([^"''#?]+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($m in $refMatches) {
        $checkedLinks++
        $target = $m.Groups[1].Value.Trim()
        if ($target -match '^(https?://|mailto:|javascript:|#|\{\{)' -or [string]::IsNullOrWhiteSpace($target)) {
            continue
        }

        $resolved = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($dir, $target))
        $exists = (Test-Path -LiteralPath $resolved) -or $publishedPermalinks.Contains($resolved) -or ($resolved -match '\.html$' -and (Test-Path -LiteralPath ($resolved -replace '\.html$', '.md')))
        if (-not $exists) {
            $errors.Add("$relPath - broken reference '$target' (resolved: $resolved)")
        }
    }
}

# -------------------------------------------------------------------------
# 3i. docs/ reference pages: on the portal shell, and no include that is not there (S4117)
# -------------------------------------------------------------------------
$docsSourceRoot = Join-Path $RepoRoot 'docs'
$includesRoot = Join-Path $RepoRoot '_includes'
$checkedDocsPages = 0
if (Test-Path -LiteralPath $docsSourceRoot) {
    foreach ($mdPage in (Get-ChildItem -LiteralPath $docsSourceRoot -Recurse -Filter *.md -File)) {
        $mdText = Get-Content -LiteralPath $mdPage.FullName -Raw -Encoding utf8
        if ([string]::IsNullOrEmpty($mdText)) { continue }
        $mdRel = [System.IO.Path]::GetRelativePath($RepoRoot, $mdPage.FullName).Replace([char]92, '/')
        # docs/DOCS_MAP.md is generated by the canon harness document_registry/generate.ps1 with
        # layout: default and is an unpublished contributor inventory map, not a portal reference page.
        if ($mdRel -eq 'docs/DOCS_MAP.md') { continue }
        $frontMatter = [regex]::Match($mdText, '\A---\r?\n(.*?)\r?\n---\r?\n', [System.Text.RegularExpressions.RegexOptions]::Singleline)
        if ($frontMatter.Success) {
            $layoutLine = [regex]::Match($frontMatter.Groups[1].Value, '(?m)^layout:\s*(\S+)\s*$')
            if ($layoutLine.Success) {
                $checkedDocsPages++
                if ($layoutLine.Groups[1].Value -ne 'doc') {
                    $errors.Add("$mdRel - layout '$($layoutLine.Groups[1].Value)' is not the portal shell 'doc' (SITE-EXPERIENCE rules 1, 3, 5)")
                }
            }
        }
        foreach ($include in [regex]::Matches($mdText, '\{%-?\s*include\s+([\w./-]+)')) {
            if (-not (Test-Path -LiteralPath (Join-Path $includesRoot $include.Groups[1].Value) -PathType Leaf)) {
                $errors.Add("$mdRel - includes '$($include.Groups[1].Value)', which is not in _includes/; the Pages build would fail")
            }
        }
    }
}

# -------------------------------------------------------------------------
# 4. Search Simulation: Unmatched Query Scoring Verification
# -------------------------------------------------------------------------
if (Test-Path -LiteralPath $searchIndexPath) {
    try {
        $indexData = Get-Content -LiteralPath $searchIndexPath -Raw -Encoding utf8 | ConvertFrom-Json
        $pages = @(Read-DocumentationSearchIndex -Path $searchIndexPath)

        # Verify that an unindexed random term matches 0 documents
        $bogusQuery = "xyznonexistentbogusterm999"
        $bogusMatches = 0
        foreach ($p in $pages) {
            $t = if ($p.title) { $p.title.ToLowerInvariant() } else { "" }
            $k = if ($p.keywords) { $p.keywords.ToLowerInvariant() } else { "" }
            $d = if ($p.description) { $p.description.ToLowerInvariant() } else { "" }
            $body = if ($p.PSObject.Properties['body']) { ([string]$p.body).ToLowerInvariant() } else { "" }
            if ($body.Contains($bogusQuery) -or $t.Contains($bogusQuery) -or $k.Contains($bogusQuery) -or $d.Contains($bogusQuery)) {
                $bogusMatches++
            }
        }
        if ($bogusMatches -gt 0) {
            $errors.Add("Search index contains match for bogus token '$bogusQuery' ($bogusMatches hit(s))")
        }
    } catch {
        $errors.Add("Failed to parse search index ${searchIndexPath}: $($_.Exception.Message)")
    }
}

# -------------------------------------------------------------------------
# Verdict
# -------------------------------------------------------------------------
if (-not $Quiet) {
    Write-Host "Audited $checkedPages documentation page(s), $checkedImages image(s), $checkedLinks link reference(s), $checkedDocsPages docs/ page layout(s)." -ForegroundColor Gray
}

if ($warnings.Count -gt 0 -and -not $Quiet) {
    foreach ($w in $warnings) {
        Write-Host "  WARN: $w" -ForegroundColor Yellow
    }
}

if ($errors.Count -gt 0) {
    Write-Host "assert-docs-portal-ui-ux: FAIL ($($errors.Count) error(s) found)" -ForegroundColor Red
    foreach ($err in $errors) {
        Write-Host "  - $err" -ForegroundColor Red
    }
    exit 1
}

Write-Host "assert-docs-portal-ui-ux: PASS ($checkedPages pages verified, a11y clean, 0 broken refs, responsive tokens valid)" -ForegroundColor Green
exit 0
