<#
.SYNOPSIS
    Build locale navigation and shared shell metadata for documentation pages.
.DESCRIPTION
    Dot-sourced library used by the owning documentation generators; it returns HTML and never exits.
#>
. (Join-Path $PSScriptRoot 'lib/portal-glyphs.ps1')
# Shared generated shell: locale links must match the published page, not a query parameter.
function ConvertTo-DocumentationShell {
    param([string]$Html, [string]$RelativePath, [string]$DocumentationRoot)
    $locale = if ($RelativePath -match '-ru\.html$') { 'ru' } elseif ($RelativePath -match '-uk\.html$') { 'uk' } else { 'en' }
    $stem = $RelativePath -replace '-(ru|uk)\.html$', '.html'
    $pageFile = Join-Path $DocumentationRoot $RelativePath
    $baseUri = [Uri]::new([IO.Path]::GetFullPath($pageFile))
    $labels = @{ en = 'English'; ru = 'Русский'; uk = 'Українська' }
    $alternates = [ordered]@{}
    $menu = [Text.StringBuilder]::new()
    foreach ($code in 'en', 'ru', 'uk') {
        $target = if ($code -eq 'en') { $stem } else { $stem -replace '\.html$', "-$code.html" }
        if ($target -eq $RelativePath -or (Test-Path -LiteralPath (Join-Path $DocumentationRoot $target))) {
            $alternates[$code] = $target
            $targetUri = [Uri]::new([IO.Path]::GetFullPath((Join-Path $DocumentationRoot $target)))
            $href = $baseUri.MakeRelativeUri($targetUri).ToString()
            $active = if ($code -eq $locale) { ' active' } else { '' }
            $current = if ($code -eq $locale) { ' aria-current="page"' } else { '' }
            $null = $menu.AppendLine("                        <a href=`"$href`" class=`"doc-lang-item$active`" data-lang=`"$code`"$current><span>$($labels[$code])</span><span class=`"lang-code`">$code</span></a>")
        }
    }
    $menuHtml = $menu.ToString().TrimEnd()
    $Html = [regex]::Replace($Html, '(?s)(<div class="doc-lang-menu" id="langMenu")[^>]*>.*?</div>', {
        param($match)
        return $match.Groups[1].Value + '>' + "`n$menuHtml`n                    </div>"
    })
    $Html = [regex]::Replace($Html, '(?s)(<button class="doc-lang-btn" id="langBtn"[^>]*>).*?</button>', {
        param($match)
        return $match.Groups[1].Value + $locale.ToUpperInvariant() + '</button>'
    })
    # PAGE-STYLE 4.3 (1.4): the theme control names itself in visible text beside the app.theme
    # glyph; the bare glyph alone did not.
    $themeLabels = @{ en = 'Theme'; ru = 'Тема'; uk = 'Тема' }
    $themeBody = (Get-PortalGlyph -Meaning 'app.theme') + '<span>' + $themeLabels[$locale] + '</span>'
    $Html = [regex]::Replace($Html, '(?s)(<button class="doc-theme-btn" id="themeBtn"[^>]*>).*?</button>', {
        param($match)
        return $match.Groups[1].Value + $themeBody + '</button>'
    })
    # Localized navigation stays on its matching translated page, including sidebar and footer links.
    $Html = [regex]::Replace($Html, '<a\b[^>]*href="([^"?#]+\.html)([?#][^"]*)?"[^>]*>', {
        param($match)
        if ($match.Value -match '\bdata-lang=') { return $match.Value }
        $href = $match.Groups[1].Value
        if ($href -match '^(?:[a-z]+:|/|\{)' -or $locale -eq 'en') { return $match.Value }
        $plain = $href -replace '-(ru|uk)\.html$', '.html'
        $translated = $plain -replace '\.html$', "-$locale.html"
        $resolved = [Uri]::new($baseUri, $translated)
        if ($resolved.IsFile -and (Test-Path -LiteralPath $resolved.LocalPath)) {
            $oldAttribute = 'href="' + $href + $match.Groups[2].Value + '"'
            $newAttribute = 'href="' + $translated + $match.Groups[2].Value + '"'
            return $match.Value.Replace($oldAttribute, $newAttribute)
        }
        return $match.Value
    })
    # SITE-EXPERIENCE rule 15: every page opens with one skip link past the chrome, in the page's
    # language. It follows the date stamp when one is there, so generated and hand-kept pages agree.
    $skipLabels = @{ en = 'Skip to content'; ru = 'Перейти к содержимому'; uk = 'Перейти до вмісту' }
    $skipLink = '<a class="doc-skip-link" href="#main-content">' + $skipLabels[$locale] + '</a>'
    $Html = [regex]::Replace($Html, '\r?\n[ \t]*<a class="doc-skip-link"[^>]*>[^<]*</a>', '')
    $bodyOpen = [regex]::new('(?i)<body(?:\s[^>]*)?>(?:\r?\n[ \t]*<div class="doc-stamp"[^>]*>[^<]*</div>)?')
    $Html = $bodyOpen.Replace($Html, { param($match) $match.Value + "`n    " + $skipLink }, 1)
    # Emit reciprocal language metadata from real counterparts, even for manually maintained hubs.
    $canonical = [regex]::Match($Html, '<link rel="canonical" href="([^"]+)"')
    if ($canonical.Success) {
        $site = $canonical.Groups[1].Value -replace '/documentation/.*$', ''
        $Html = [regex]::Replace($Html, '\s*<link rel="alternate" hreflang="[^"]+" href="[^"]+">', '')
        $head = [Text.StringBuilder]::new()
        foreach ($code in $alternates.Keys) {
            $null = $head.AppendLine("    <link rel=`"alternate`" hreflang=`"$code`" href=`"$site/documentation/$($alternates[$code])`">")
        }
        $default = if ($alternates.Contains('en')) { $alternates['en'] } else { $RelativePath }
        $null = $head.Append("    <link rel=`"alternate`" hreflang=`"x-default`" href=`"$site/documentation/$default`">")
        $alternateHtml = $head.ToString()
        $Html = [regex]::Replace($Html, '<link rel="canonical" href="[^"]+">', {
            param($match)
            return $match.Value + "`n" + $alternateHtml
        })
    }
    return $Html
}
