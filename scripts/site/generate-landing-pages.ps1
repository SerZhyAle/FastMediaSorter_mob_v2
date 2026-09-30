<#
.SYNOPSIS
    Generates the landing page in every site language from index.html plus _data/landing/<slug>.json.

.DESCRIPTION
    index.html is the English source and stays hand-edited - the contract gates (PAGE-STYLE,
    PAGE-CONTENT, SITE-FAMILY-MAP) and the family-footer renderer read it as finished HTML.
    index-ru.html and index-uk.html are hand-maintained translations with their own structure.
    Every other language in _data/languages.yml that owns a _data/landing/<slug>.json is written
    as index-<slug>.html: the English page with each translatable segment swapped for its
    translation. A segment absent from the data file stays English, so a partial translation
    still publishes.

    Segments, keyed by their English text with whitespace collapsed:
      text   a run of text and inline tags (a, b, strong, em, span, code, br, ..) between two
             block-level tags; the key keeps the inline tags, so a translation must keep them too;
             also alt / title / aria-label / placeholder values, the meta description family,
             and the "description" string of the JSON-LD block.
      js     a single-quoted literal inside an inline script.

    Two marked regions are rendered into all pages, hand-maintained ones included:
      lang-alternates  the hreflang links in <head>, one per existing landing page;
      lang-list        the footer list of every landing language by its endonym - the header
                       switcher stays RU / EN / UA as contract PAGE-STYLE 4.2 fixes it.

    Adding a language: its locale in locales_config.xml, generate-site-languages.ps1, then a
    _data/landing/<slug>.json and one run of this script. No template or workflow is edited.

.PARAMETER Root
    Repository root. Defaults to two levels above this script.

.PARAMETER Extract
    Write _data/landing/en.json - the current source catalog a translator works from - and stop.

.PARAMETER Check
    Write nothing; exit 1 when any page differs from what this script would write, or when a
    data file holds a key the English page no longer has.

.PARAMETER Quiet
    Print only the verdict line.

.EXAMPLE
    pwsh -NoProfile -File scripts/site/generate-landing-pages.ps1 -Check

.OUTPUTS
    Exit codes:
      0 - pages written, or (-Check) every page current.
      1 - (-Check) a page is stale or a data file carries a stale key.
      2 - cannot run: the root, index.html, _data/languages.yml or a data file is missing or invalid.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$Extract,
    [switch]$Check,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit 0
}

function Stop-CannotRun([string]$reason) {
    Write-Host "generate-landing-pages: CANNOT RUN - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotRun "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

$siteUrl = 'https://serzhyale.github.io/FastMediaSorter_mob_v2/'
# Hand-maintained pages and the footer-list label each carries in its own language.
$handPages = [ordered]@{ en = 'Languages'; ru = 'Языки'; uk = 'Мови' }
$utf8 = [System.Text.UTF8Encoding]::new($false)
$sourcePath = Join-Path $Root 'index.html'
$languagesPath = Join-Path $Root '_data/languages.yml'
$dataDir = Join-Path $Root '_data/landing'

if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { Stop-CannotRun 'index.html not found' }
if (-not (Test-Path -LiteralPath $languagesPath -PathType Leaf)) { Stop-CannotRun '_data/languages.yml not found' }

# ---- languages -------------------------------------------------------------------------------

$languages = [System.Collections.Generic.List[object]]::new()
$current = $null
foreach ($line in [System.IO.File]::ReadAllLines($languagesPath, $utf8)) {
    if ($line -match '^- code:\s*(\S+)\s*$') {
        $current = [ordered]@{ code = $Matches[1]; slug = ''; endonym = ''; dir = 'ltr' }
        $languages.Add($current)
    } elseif ($null -ne $current -and $line -match '^\s+(slug|endonym|dir):\s*(.+?)\s*$') {
        $current[$Matches[1]] = $Matches[2]
    }
}
if ($languages.Count -eq 0) { Stop-CannotRun '_data/languages.yml lists no language' }

$translations = @{}
foreach ($lang in $languages) {
    if ($handPages.Contains($lang.slug)) { continue }
    $dataPath = Join-Path $dataDir "$($lang.slug).json"
    if (-not (Test-Path -LiteralPath $dataPath -PathType Leaf)) { continue }
    try {
        $translations[$lang.slug] = [System.IO.File]::ReadAllText($dataPath, $utf8) | ConvertFrom-Json -AsHashtable
    } catch {
        Stop-CannotRun "_data/landing/$($lang.slug).json is not valid JSON: $($_.Exception.Message)"
    }
}

# A language has a landing page when it is hand-maintained or owns a data file.
$present = @($languages | Where-Object { $handPages.Contains($_.slug) -or $translations.ContainsKey($_.slug) })

function Get-PageFile($lang) {
    if ($lang.slug -eq 'en') { return 'index.html' }
    return "index-$($lang.slug).html"
}

function Get-PageUrl($lang) {
    if ($lang.slug -eq 'en') { return $siteUrl }
    return $siteUrl + (Get-PageFile $lang)
}

# ---- regions ---------------------------------------------------------------------------------

function Get-AlternatesRegion([string]$indent) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("$indent<!-- lang-alternates:begin - rendered by scripts/site/generate-landing-pages.ps1 from _data/languages.yml -->")
    foreach ($lang in $present) {
        $lines.Add("$indent<link rel=`"alternate`" hreflang=`"$($lang.code)`" href=`"$(Get-PageUrl $lang)`">")
    }
    $lines.Add("$indent<link rel=`"alternate`" hreflang=`"x-default`" href=`"$siteUrl`">")
    $lines.Add("$indent<!-- lang-alternates:end -->")
    return $lines -join "`n"
}

function Get-LangListRegion([string]$indent, $pageLang, [string]$label) {
    $items = foreach ($lang in $present) {
        $attrs = "lang=`"$($lang.code)`" dir=`"$($lang.dir)`""
        if ($lang.slug -eq $pageLang.slug) {
            "<span $attrs aria-current=`"page`">$($lang.endonym)</span>"
        } else {
            "<a href=`"$(Get-PageFile $lang)`" hreflang=`"$($lang.code)`" $attrs>$($lang.endonym)</a>"
        }
    }
    $lines = @(
        "$indent<!-- lang-list:begin - rendered by scripts/site/generate-landing-pages.ps1 from _data/languages.yml -->"
        "$indent<nav class=`"lang-list`" aria-label=`"$label`">"
        ($items | ForEach-Object { "$indent    $_" })
        "$indent</nav>"
        "$indent<!-- lang-list:end -->"
    )
    return $lines -join "`n"
}

function Set-Regions([string]$html, $pageLang, [string]$label) {
    $alt = [regex]::Match($html, '(?s)(?<indent>[ \t]*)<!-- lang-alternates:begin\b.*?<!-- lang-alternates:end -->')
    if (-not $alt.Success) {
        $alt = [regex]::Match($html, '(?m)(?<indent>^[ \t]*)<link rel="alternate" hreflang="[^"]*" href="[^"]*">(\r?\n[ \t]*<link rel="alternate" hreflang="[^"]*" href="[^"]*">)*')
    }
    if (-not $alt.Success) { throw "no hreflang block and no lang-alternates region" }
    $html = $html.Remove($alt.Index, $alt.Length).Insert($alt.Index, (Get-AlternatesRegion $alt.Groups['indent'].Value))

    $list = [regex]::Match($html, '(?s)(?<indent>[ \t]*)<!-- lang-list:begin\b.*?<!-- lang-list:end -->')
    if ($list.Success) {
        $html = $html.Remove($list.Index, $list.Length).Insert($list.Index, (Get-LangListRegion $list.Groups['indent'].Value $pageLang $label))
    } else {
        $anchor = [regex]::Match($html, '(?m)^(?<indent>[ \t]*)<div class="footer-bottom">')
        if (-not $anchor.Success) { throw 'no footer-bottom to place the lang-list region before' }
        $html = $html.Insert($anchor.Index, (Get-LangListRegion $anchor.Groups['indent'].Value $pageLang $label) + "`n")
    }
    return $html
}

# ---- segmentation ----------------------------------------------------------------------------

$inlineTags = @('a', 'abbr', 'b', 'bdi', 'br', 'cite', 'code', 'em', 'i', 'kbd', 'mark', 'q', 's', 'small', 'span', 'strong', 'sub', 'sup', 'time', 'u', 'wbr')
$textAttributes = 'alt|title|aria-label|placeholder'
$metaNames = @('description', 'keywords', 'twitter:title', 'twitter:description', 'og:title', 'og:description', 'og:image:alt')
$tokenPattern = '(?s)<!-- lang-list:begin\b.*?<!-- lang-list:end -->|<!--.*?-->|<script\b[^>]*>.*?</script>|<style\b[^>]*>.*?</style>|<svg\b.*?</svg>|<[^>]+>|[^<]+'
$literalPattern = "'((?:[^'\\\r\n]|\\.)*)'"

function Get-Key([string]$text) { return ([regex]::Replace($text, '\s+', ' ')).Trim() }

function Test-HasLetter([string]$fragment) { return ([regex]::Replace($fragment, '<[^>]+>|&[#a-zA-Z0-9]+;', '')) -match '\p{L}' }

function Test-JsCandidate([string]$before, [string]$value) {
    if (-not (Test-HasLetter $value)) { return $false }
    if ($before -cmatch '(label|title)\s*:\s*$' -or $before -cmatch '(innerText|textContent|innerHTML)\s*\+?=\s*$') { return $true }
    # Lower-case literals are the feature-explorer keyword filters, matched against the English
    # FEATURES page every generated language links to - translating one breaks its filter.
    return ($value -cmatch '^[A-Z][a-z]+ ' -and $value -notmatch '[=(]')
}

# Walks the page once. $onText / $onAttr / $onJs return a replacement or $null (keep).
function Convert-Page([string]$html, [scriptblock]$onText, [scriptblock]$onAttr, [scriptblock]$onJs) {
    $out = [System.Text.StringBuilder]::new()
    $run = [System.Text.StringBuilder]::new()
    $flush = {
        if ($run.Length -eq 0) { return }
        $raw = $run.ToString()
        [void]$run.Clear()
        $m = [regex]::Match($raw, '(?s)^(\s*)(.*?)(\s*)$')
        $body = $m.Groups[2].Value
        $replacement = $null
        if ($body.Length -gt 0 -and (Test-HasLetter $body)) { $replacement = & $onText (Get-Key $body) }
        if ($null -ne $replacement) {
            [void]$out.Append($m.Groups[1].Value).Append($replacement).Append($m.Groups[3].Value)
        } else {
            [void]$out.Append($raw)
        }
    }
    foreach ($tok in [regex]::Matches($html, $tokenPattern)) {
        $t = $tok.Value
        if ($t.StartsWith('<!--') -or $t.StartsWith('<style') -or $t.StartsWith('<svg')) {
            & $flush; [void]$out.Append($t); continue
        }
        if ($t.StartsWith('<script')) {
            & $flush
            $open = [regex]::Match($t, '^<script\b[^>]*>').Value
            if ($open -match '\bsrc=') { [void]$out.Append($t); continue }
            if ($open -match 'application/ld\+json') {
                $t = [regex]::Replace($t, '("description":\s*")((?:[^"\\]|\\.)*)(")', {
                        param($d)
                        $r = & $onAttr (Get-Key $d.Groups[2].Value)
                        if ($null -eq $r) { return $d.Value }
                        return $d.Groups[1].Value + ($r -replace '\\', '\\' -replace '"', '\"') + $d.Groups[3].Value
                    })
                [void]$out.Append($t); continue
            }
            $t = [regex]::Replace($t, $literalPattern, {
                    param($l)
                    $before = $t.Substring([Math]::Max(0, $l.Index - 40), [Math]::Min(40, $l.Index))
                    $r = & $onJs $l.Groups[1].Value $before
                    if ($null -eq $r) { return $l.Value }
                    return "'" + ($r -replace "\\", "\\" -replace "'", "\'") + "'"
                })
            [void]$out.Append($t); continue
        }
        if ($t.StartsWith('<')) {
            $name = [regex]::Match($t, '^</?([a-zA-Z0-9]+)').Groups[1].Value.ToLowerInvariant()
            $tag = [regex]::Replace($t, "\b($textAttributes)=`"([^`"]*)`"", {
                    param($a)
                    $r = & $onAttr (Get-Key $a.Groups[2].Value)
                    if ($null -eq $r) { return $a.Value }
                    return $a.Groups[1].Value + '="' + ($r -replace '"', '&quot;') + '"'
                })
            if ($name -eq 'meta') {
                $metaName = [regex]::Match($tag, '\b(?:name|property)="([^"]+)"').Groups[1].Value
                if ($metaNames -contains $metaName) {
                    $tag = [regex]::Replace($tag, '(\bcontent=")([^"]*)(")', {
                            param($c)
                            $r = & $onAttr (Get-Key $c.Groups[2].Value)
                            if ($null -eq $r) { return $c.Value }
                            return $c.Groups[1].Value + ($r -replace '"', '&quot;') + $c.Groups[3].Value
                        })
                }
            }
            if ($inlineTags -contains $name) { [void]$run.Append($tag) } else { & $flush; [void]$out.Append($tag) }
            continue
        }
        [void]$run.Append($t)
    }
    & $flush
    return $out.ToString()
}

$source = [System.IO.File]::ReadAllText($sourcePath, $utf8)
$eol = if ($source.Contains("`r`n")) { "`r`n" } else { "`n" }
$source = $source -replace "`r`n", "`n"
$enLang = $languages | Where-Object { $_.slug -eq 'en' } | Select-Object -First 1
if ($null -eq $enLang) { Stop-CannotRun '_data/languages.yml has no en entry' }

# ---- extract ---------------------------------------------------------------------------------

$catalogText = [System.Collections.Generic.List[string]]::new()
$catalogJs = [System.Collections.Generic.List[string]]::new()
$seenText = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
$seenJs = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
$sourceRendered = Set-Regions $source $enLang 'Languages'
$null = Convert-Page $sourceRendered `
    { param($k) if (-not $seenText.ContainsKey($k)) { $seenText[$k] = 1; $catalogText.Add($k) }; $null } `
    { param($k) if ($k -match '\p{L}' -and -not $seenText.ContainsKey($k)) { $seenText[$k] = 1; $catalogText.Add($k) }; $null } `
    { param($v, $before) if ((Test-JsCandidate $before $v) -and -not $seenJs.ContainsKey($v)) { $seenJs[$v] = 1; $catalogJs.Add($v) }; $null }

if ($Extract) {
    if (-not (Test-Path -LiteralPath $dataDir)) { New-Item -ItemType Directory -Path $dataDir | Out-Null }
    $catalog = [ordered]@{
        '_comment' = 'GENERATED by scripts/site/generate-landing-pages.ps1 -Extract from index.html - the source catalog; a translation is _data/landing/<slug>.json with the same keys'
        text       = $catalogText
        js         = $catalogJs
    }
    [System.IO.File]::WriteAllText((Join-Path $dataDir 'en.json'), (($catalog | ConvertTo-Json -Depth 5) -replace "`r`n", "`n") + "`n", $utf8)
    Write-Host "generate-landing-pages: extracted $($catalogText.Count) text and $($catalogJs.Count) js segment(s) to _data/landing/en.json" -ForegroundColor Green
    exit 0
}

# ---- generate --------------------------------------------------------------------------------

function Get-DocLinkMap([string]$slug) {
    $map = @{}
    $suffix = "-$slug.md"
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $Root 'docs') -Recurse -File -Filter "*$suffix") {
        $enFile = Join-Path $file.DirectoryName ($file.Name.Substring(0, $file.Name.Length - $suffix.Length) + '.md')
        if (-not (Test-Path -LiteralPath $enFile -PathType Leaf)) { continue }
        $enLink = [regex]::Match([System.IO.File]::ReadAllText($enFile, $utf8), '(?m)^permalink:\s*/(\S+)').Groups[1].Value
        $trLink = [regex]::Match([System.IO.File]::ReadAllText($file.FullName, $utf8), '(?m)^permalink:\s*/(\S+)').Groups[1].Value
        if (-not $enLink -or -not $trLink) { continue }
        $map[$enLink] = $trLink
        if ($enLink.EndsWith('/')) { $map[$enLink + 'index.html'] = $trLink }
    }
    return $map
}

$expected = [ordered]@{}
$staleKeys = [System.Collections.Generic.List[string]]::new()

foreach ($lang in $present) {
    $file = Get-PageFile $lang
    if ($handPages.Contains($lang.slug)) {
        $path = Join-Path $Root $file
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Stop-CannotRun "$file not found" }
        $html = [System.IO.File]::ReadAllText($path, $utf8) -replace "`r`n", "`n"
        $expected[$file] = Set-Regions $html $lang $handPages[$lang.slug]
        continue
    }

    $data = $translations[$lang.slug]
    $text = if ($data.ContainsKey('text') -and $data.text) { $data.text } else { @{} }
    $js = if ($data.ContainsKey('js') -and $data.js) { $data.js } else { @{} }
    $label = if ($data.ContainsKey('languages_label') -and $data.languages_label) { $data.languages_label } else { 'Languages' }
    $ogLocale = if ($data.ContainsKey('og_locale') -and $data.og_locale) { $data.og_locale } else { $lang.code -replace '-', '_' }
    foreach ($k in $text.Keys) { if (-not $seenText.ContainsKey($k)) { $staleKeys.Add("_data/landing/$($lang.slug).json text: $k") } }
    foreach ($k in $js.Keys) { if (-not $seenJs.ContainsKey($k)) { $staleKeys.Add("_data/landing/$($lang.slug).json js: $k") } }

    $html = Convert-Page $sourceRendered `
        { param($k) if ($text.ContainsKey($k)) { $text[$k] } else { $null } } `
        { param($k) if ($text.ContainsKey($k)) { $text[$k] } else { $null } } `
        { param($v, $before) if ($js.ContainsKey($v)) { $js[$v] } else { $null } }

    $url = Get-PageUrl $lang
    $html = $html.Replace('<html lang="en">', "<html lang=`"$($lang.code)`" dir=`"$($lang.dir)`">")
    $html = [regex]::Replace($html, '<link rel="canonical" href="[^"]*">', "<link rel=`"canonical`" href=`"$url`">")
    $html = [regex]::Replace($html, '<meta property="og:url" content="[^"]*">', "<meta property=`"og:url`" content=`"$url`">")
    $html = [regex]::Replace($html, '"url": "https://serzhyale\.github\.io/FastMediaSorter_mob_v2/"', "`"url`": `"$url`"")
    $html = $html.Replace('"inLanguage": "en"', "`"inLanguage`": `"$($lang.code)`"")
    $html = [regex]::Replace($html, '(?m)^([ \t]*)<meta property="og:locale" content="[^"]*">(\r?\n[ \t]*<meta property="og:locale:alternate" content="[^"]*">)*', {
            param($m)
            $i = $m.Groups[1].Value
            "$i<meta property=`"og:locale`" content=`"$ogLocale`">`n$i<meta property=`"og:locale:alternate`" content=`"en_US`">"
        })
    $html = $html.Replace('<a class="brand" href="index.html">', "<a class=`"brand`" href=`"$file`">")
    $html = $html.Replace('<a href="index.html" class="on" data-lang="en">', '<a href="index.html" data-lang="en">')
    $links = Get-DocLinkMap $lang.slug
    $html = [regex]::Replace($html, 'href="(docs/[^"#]+)', {
            param($h)
            if ($links.ContainsKey($h.Groups[1].Value)) { return 'href="' + $links[$h.Groups[1].Value] }
            return $h.Value
        })
    $html = Set-Regions $html $lang $label
    $expected[$file] = $html
}

$stale = [System.Collections.Generic.List[string]]::new()
$written = 0
foreach ($file in $expected.Keys) {
    $path = Join-Path $Root $file
    $want = $expected[$file]
    $fileEol = $eol
    $have = $null
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $have = [System.IO.File]::ReadAllText($path, $utf8)
        $fileEol = if ($have.Contains("`r`n")) { "`r`n" } else { "`n" }
    }
    $want = $want -replace "`n", $fileEol
    if ($have -ceq $want) { continue }
    if ($Check) { $stale.Add($file); continue }
    [System.IO.File]::WriteAllText($path, $want, $utf8)
    $written++
    if (-not $Quiet) { Write-Host "  wrote $file" }
}

if ($Check) {
    foreach ($s in $stale) { Write-Host "  STALE $s - run scripts/site/generate-landing-pages.ps1" -ForegroundColor Red }
    foreach ($k in $staleKeys) { Write-Host "  STALE-KEY $k" -ForegroundColor Red }
    if ($stale.Count -gt 0 -or $staleKeys.Count -gt 0) {
        Write-Host "generate-landing-pages: FAIL ($($stale.Count) stale page(s), $($staleKeys.Count) stale key(s))" -ForegroundColor Red
        exit 1
    }
    Write-Host "generate-landing-pages: PASS ($($expected.Count) pages current)" -ForegroundColor Green
    exit 0
}

foreach ($k in $staleKeys) { Write-Host "  stale key (the English page no longer has it): $k" -ForegroundColor Yellow }
Write-Host "generate-landing-pages: OK ($($expected.Count) pages, $written written)" -ForegroundColor Green
exit 0
