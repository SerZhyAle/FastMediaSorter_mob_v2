<#
.SYNOPSIS
    Shared helpers of the released-address record: the pages a site tree publishes and the section anchors
    of each (S4256).

.DESCRIPTION
    Dot-sourced by scripts/docs/record-released-addresses.ps1, which writes docs/site-released-addresses.jsonl
    at the release step, and by scripts/quality/assert-released-addresses.ps1, which reads it. Both compute a
    page's anchors with Get-PageAnchors, so a page the record captured and the page the gate inspects are
    judged by one function - an approximation of the renderer stays harmless as long as it is the same one.

    Requires scripts/docs/lib/site-addresses.ps1 to be dot-sourced first (Get-PublishedAddresses,
    Read-JsonlRecords, $script:ForwarderMarkerRegex). Returns values and never exits.
#>
Set-StrictMode -Version Latest

$script:ReleasedRecordPath = 'docs/site-released-addresses.jsonl'

# GFM header id as kramdown's GFM parser builds it (Jekyll's default input): the rendered heading text
# lower-cased, every character that is not a word character, a hyphen or a space removed, spaces turned
# into hyphens. Inline markup is reduced to its text first, since the id is built from the rendered text.
$script:InlineLinkRegex = [regex]::new('!?\[([^\]]*)\](?:\([^)]*\)|\[[^\]]*\])', [Text.RegularExpressions.RegexOptions]::Compiled)
$script:InlineMarkupRegex = [regex]::new('<[^>]+>|[`*]', [Text.RegularExpressions.RegexOptions]::Compiled)
$script:NonIdCharRegex = [regex]::new('[^\p{L}\p{M}\p{Nd}\p{Pc}\- ]', [Text.RegularExpressions.RegexOptions]::Compiled)

function ConvertTo-GfmHeadingId {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $t = $script:InlineMarkupRegex.Replace($script:InlineLinkRegex.Replace($Text, '$1'), '')
    return $script:NonIdCharRegex.Replace($t.ToLowerInvariant(), '').Replace(' ', '-')
}

# Section anchors a page offers, found in one compiled pass each: in markup the lower-case id of a tag that is
# not a control, a script target or an embedded resource, and the id or name of an <a>. A camel-case id is a
# script hook (themeBtn, langMenu), not a place a link lands, and a value built by Liquid ('{') is no literal
# anchor. In Markdown also kramdown {#id} attribute lists and the GFM id of every ATX heading outside a fenced
# block that has no explicit one. A per-tag loop in PowerShell took 4.6 s over the 644 sitemap pages.
$script:AnchorAttributeRegex = [regex]::new(
    '<(?:(?!(?:button|input|select|textarea|option|label|form|canvas|svg|path|img|picture|source|video|audio|iframe|script|style|template|meta|link)\b)[a-zA-Z][\w-]*[^>]*?\sid|a\b[^>]*?\s(?:id|name))\s*=\s*["'']([^"''{}\sA-Z]+)["'']',
    [Text.RegularExpressions.RegexOptions]::Compiled -bor [Text.RegularExpressions.RegexOptions]::CultureInvariant)
$script:FencedBlockRegex = [regex]::new('(?ms)^[ ]{0,3}(```|~~~).*?^[ ]{0,3}\1[^\r\n]*', [Text.RegularExpressions.RegexOptions]::Compiled)
$script:KramdownIdRegex = [regex]::new('\{:?\s*#([A-Za-z][\w\-]*)[^}\r\n]*\}', [Text.RegularExpressions.RegexOptions]::Compiled)
$script:AtxHeadingRegex = [regex]::new('(?m)^[ ]{0,3}#{1,6}[ \t]+(.+?)[ \t]*#*[ \t]*\r?$', [Text.RegularExpressions.RegexOptions]::Compiled)

# Ordinal order, so the record and its re-run are byte-identical on every machine and culture.
function ConvertTo-OrdinalSortedArray {
    param([System.Collections.Generic.IEnumerable[string]]$Values)
    $array = [string[]]@([System.Linq.Enumerable]::Distinct([System.Linq.Enumerable]::ToArray($Values), [StringComparer]::Ordinal))
    [Array]::Sort($array, [StringComparer]::Ordinal)
    return , $array
}

function Get-PageAnchors {
    param([Parameter(Mandatory)][string]$FullName)
    $text = [IO.File]::ReadAllText($FullName)
    $anchors = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($m in $script:AnchorAttributeRegex.Matches($text)) { [void]$anchors.Add([Net.WebUtility]::HtmlDecode($m.Groups[1].Value)) }
    if ([IO.Path]::GetExtension($FullName) -notin '.md', '.markdown') { return (ConvertTo-OrdinalSortedArray $anchors) }

    $prose = $script:FencedBlockRegex.Replace($text, '')
    foreach ($m in $script:KramdownIdRegex.Matches($prose)) { [void]$anchors.Add($m.Groups[1].Value) }
    $used = @{}
    foreach ($m in $script:AtxHeadingRegex.Matches($prose)) {
        $heading = $m.Groups[1].Value
        if ($script:KramdownIdRegex.IsMatch($heading)) { continue }
        $id = ConvertTo-GfmHeadingId -Text $heading
        if ($id -eq '') { continue }
        # kramdown suffixes a repeated id with -1, -2, .. in document order.
        $final = $id
        if ($used.ContainsKey($id)) { $final = "$id-$($used[$id])"; $used[$id]++ } else { $used[$id] = 1 }
        [void]$anchors.Add($final)
    }
    return (ConvertTo-OrdinalSortedArray $anchors)
}

# The addresses the site announces: every <loc> of its sitemap.xml, site-relative, a trailing slash read as
# its index page. $null when the tree has no sitemap.
function Get-SitemapAddresses {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $path = Join-Path $RepoRoot 'sitemap.xml'
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $set = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($m in [regex]::Matches([IO.File]::ReadAllText($path), '<loc>\s*([^<\s]+)\s*</loc>')) {
        $loc = [Net.WebUtility]::HtmlDecode($m.Groups[1].Value)
        if (-not $loc.StartsWith($script:SiteBaseUrl, [StringComparison]::Ordinal)) { continue }
        $address = $loc.Substring($script:SiteBaseUrl.Length).TrimStart('/')
        if ($address -eq '' -or $address.EndsWith('/')) { $address += 'index.html' }
        [void]$set.Add([Uri]::UnescapeDataString($address))
    }
    return , $set
}

# Every page a release carries, as an ordered map from its site-relative address to the repo-relative file
# that serves it: a published .md, .markdown or .html address that the sitemap announces. The sitemap is the
# line between a page and the contributor files Jekyll also serves (docs/DEV_OPS.md and the like), which no
# outside surface is offered; it already leaves forwarders out, and the marker test repeats that for a tree
# whose sitemap is stale. $null when the tree has no sitemap.
function Get-ReleasedPageSet {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $announced = Get-SitemapAddresses -RepoRoot $root
    if ($null -eq $announced) { return $null }
    $pages = [ordered]@{}
    $published = Get-PublishedAddresses -RepoRoot $root
    foreach ($address in ($published.Keys | Sort-Object)) {
        if (-not $announced.Contains($address)) { continue }
        $rel = [string]$published[$address]
        if ([IO.Path]::GetExtension($rel) -notin '.md', '.markdown', '.html') { continue }
        $full = Join-Path $root $rel
        if ([IO.Path]::GetExtension($rel) -eq '.html') {
            $head = [IO.File]::ReadAllText($full)
            if ($head.Length -gt 4096) { $head = $head.Substring(0, 4096) }
            if ($head -match $script:ForwarderMarkerRegex) { continue }
        }
        $pages[$address] = $rel
    }
    return $pages
}

# The snapshot the release writes: one entry per page with its anchors.
function Get-ReleasedPageSnapshot {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $pages = Get-ReleasedPageSet -RepoRoot $root
    if ($null -eq $pages) { return $null }
    $snapshot = [ordered]@{}
    foreach ($address in $pages.Keys) {
        $snapshot[$address] = [pscustomobject]@{
            File    = $pages[$address]
            Anchors = Get-PageAnchors -FullName (Join-Path $root $pages[$address])
        }
    }
    return $snapshot
}

function ConvertTo-ReleasedRecordLine {
    param([Parameter(Mandatory)][string]$Address, [Parameter(Mandatory)][string]$Release, [string[]]$Anchors)
    $sorted = if ($Anchors) { ConvertTo-OrdinalSortedArray $Anchors } else { [string[]]@() }
    $record = [ordered]@{ address = $Address; release = $Release; anchors = $sorted }
    return ($record | ConvertTo-Json -Compress -Depth 4)
}
