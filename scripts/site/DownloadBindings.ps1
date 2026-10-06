<#
.SYNOPSIS
    Bind verified APK URLs after translation while keeping translation keys version-independent.
.DESCRIPTION
    Dot-sourced landing generator helper; it returns HTML and does not exit.
#>
function Set-EditionDownloads {
    param([string]$Html, [hashtable]$Editions, [switch]$Normalize)
    return [regex]::Replace($Html, '(?s)<article\b[^>]*data-download-edition="(?<edition>[^"]+)"[^>]*>.*?</article>', {
        param($card)
        $body = $card.Value
        $entry = if ($Normalize) { $null } else { $Editions[$card.Groups['edition'].Value] }
        $body = [regex]::Replace($body, '<a\b(?=[^>]*\bdata-edition-apk\b)[^>]*>', {
            param($link)
            $tag = $link.Value -replace '\s+href="[^"]*"', '' -replace '\s+hidden(?=\s|>)', ''
            if ($entry) {
                if ($entry.url -notmatch '^https://github\.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/') { throw 'Invalid edition APK URL' }
                return $tag.Substring(0, $tag.Length - 1) + ' href="' + [Net.WebUtility]::HtmlEncode($entry.url) + '">'
            }
            return $tag.Substring(0, $tag.Length - 1) + ' hidden>'
        })
        $body = [regex]::Replace($body, '(?s)(<p\b[^>]*class="edition-build"[^>]*>).*?</p>', {
            param($status)
            $open = $status.Groups[1].Value
            $kind = if ($entry) { 'ready' } else { 'missing' }
            $label = [regex]::Match($open, "data-$kind-label=`"([^`"]*)`"").Groups[1].Value
            $text = $label
            if ($entry) {
                $text += ' ' + [Net.WebUtility]::HtmlEncode($entry.version)
                if ($entry.prerelease) { $text += ' (' + [regex]::Match($open, 'data-preview-label="([^"]*)"').Groups[1].Value + ')' }
            }
            return $open + $text + '</p>'
        })
        return $body
    })
}
