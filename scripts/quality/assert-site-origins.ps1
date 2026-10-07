#requires -Version 7.0
<#
.SYNOPSIS
    Holds the site's third-party origins: every host a published page contacts is declared, named
    on the privacy pages, and an advertising host loads only on the landing (S4098).

.DESCRIPTION
    Contract SITE-EXPERIENCE 0.1, rule 14. Three dimensions, each judging one subject:

      declared     every third-party host a published file loads has a record in the declaration
                   (UNDECLARED), and every record's host is still loaded by some file or by the Jekyll
                   theme it names (STALE)
      privacy      each privacy page named by the install-trust declaration names every declared host
      advertising  a host declared Advertising loads only from a page matching AdvertisingPages; a
                   trust or privacy page, and a layout or include such a page renders through, never
                   loads one

    Run-time hosts (RuntimeOrigins in the declaration, S4111) are contacted by a declared script only
    while the page runs, so they are recorded from a browser sweep - no read of the files sees them.
    Each names a static Origins host as LoadedBy, or fails as RUNTIME under the declared dimension. It
    counts as declared, is never STALE, must be named on every privacy page, and inherits Advertising
    from its loader.

    The published set is what Jekyll publishes: the repository root minus the `exclude:` list of
    `_config.yml`, minus every path segment starting with '.' or '_' apart from the top-level
    `_layouts` and `_includes` templates. Files read: *.html, *.md, *.css, *.js.

    Only RESOURCE LOADS count, never navigation: an <a href> to a foreign host is a click the
    visitor makes, not a request the page makes. Counted: src/poster/srcset of script, img, iframe,
    source, video, audio, embed, track and input; <link href> other than canonical, alternate and the
    other navigational rel values; Markdown images; CSS @import and url() in .css files, <style>
    blocks and style attributes; literal URLs passed to fetch, fetchWithTimeout, sendBeacon,
    EventSource, WebSocket, importScripts, import() and XMLHttpRequest.open in .js files and inline
    <script> blocks. HTML comments, Markdown code fences and inline code spans are removed first, so
    an example in a document is not a load.

    PER-TICKET by Rule 33, wired beside assert-site-addresses.ps1: jekyll-gh-pages.yml publishes the
    tree with no Android release in between, and the author who added a host knows why.

.PARAMETER Dimension
    declared | privacy | advertising | all (default).

.PARAMETER Root
    Repository root; overridable so the tests can judge a synthetic site.

.PARAMETER Declaration
    The origins declaration. Defaults to scripts/quality/site-origins.psd1 beside this script.

.PARAMETER TrustDeclaration
    The install-trust declaration that names the trust and privacy pages. Defaults to
    scripts/quality/install-trust.psd1 beside this script.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-site-origins.ps1 -Dimension privacy

.NOTES
    Exit codes:
      0  every requested dimension passed
      1  at least one finding
      2  could not verify: a declaration or _config.yml is missing or unreadable, or the root is absent
#>
[CmdletBinding()]
param(
    [ValidateSet('declared', 'privacy', 'advertising', 'all')]
    [string] $Dimension = 'all',
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [string] $Declaration,
    [string] $TrustDeclaration
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')

if (-not $Declaration) { $Declaration = Join-Path $PSScriptRoot 'site-origins.psd1' }
if (-not $TrustDeclaration) { $TrustDeclaration = Join-Path $PSScriptRoot 'install-trust.psd1' }

Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = $Dimension; files = 'published-site' })

function Exit-CouldNotVerify([string]$Why) {
    Write-Host "assert-site-origins: COULD NOT VERIFY - $Why" -ForegroundColor Yellow
    exit 2
}

if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Exit-CouldNotVerify "root $Root does not exist" }
$Root = (Resolve-Path -LiteralPath $Root).Path

try {
    $decl = Import-PowerShellDataFile -LiteralPath $Declaration
    $trust = Import-PowerShellDataFile -LiteralPath $TrustDeclaration
}
catch {
    Exit-CouldNotVerify "a declaration is unreadable: $($_.Exception.Message)"
}

$configPath = Join-Path $Root '_config.yml'
if (-not (Test-Path -LiteralPath $configPath)) { Exit-CouldNotVerify '_config.yml is missing - the published set cannot be derived' }
$configLines = [IO.File]::ReadAllLines($configPath)

$declared = @{}
foreach ($origin in @($decl.Origins)) { $declared[([string]$origin.Host).ToLowerInvariant()] = $origin }
$runtimeOrphans = [System.Collections.Generic.List[string]]::new()
foreach ($origin in @($decl.RuntimeOrigins)) {
    if (-not $origin) { continue }
    $runtimeHost = ([string]$origin.Host).ToLowerInvariant()
    $loader = ([string]$origin.LoadedBy).ToLowerInvariant()
    if (-not $declared.ContainsKey($loader) -or $declared[$loader].Runtime) {
        $runtimeOrphans.Add("$runtimeHost (LoadedBy '$loader')")
        continue
    }
    if ($declared.ContainsKey($runtimeHost)) { continue }
    $declared[$runtimeHost] = @{ Host = $runtimeHost; Advertising = [bool]$declared[$loader].Advertising; Runtime = $true }
}
$firstParty = @($decl.FirstParty | ForEach-Object { ([string]$_).ToLowerInvariant() })
$advertisingPages = @($decl.AdvertisingPages)
$trustPages = @(@($trust.Pages) | ForEach-Object { $_.Path; $_.Privacy } | Where-Object { $_ } | Sort-Object -Unique)
$privacyPages = @(@($trust.Pages) | ForEach-Object { $_.Privacy } | Where-Object { $_ } | Sort-Object -Unique)

$findings = [System.Collections.Generic.List[string]]::new()

function Get-JekyllExcludes {
    $excludes = [System.Collections.Generic.List[string]]::new()
    $inList = $false
    foreach ($line in $configLines) {
        if ($line -match '^exclude:\s*$') { $inList = $true; continue }
        if (-not $inList) { continue }
        if ($line -match '^\s+-\s+(.+?)\s*$') { $excludes.Add(($Matches[1].Trim('"', "'")).TrimEnd('/')); continue }
        if ($line -match '^\S') { $inList = $false }
    }
    return $excludes
}

function Get-ConfigTheme {
    foreach ($line in $configLines) {
        if ($line -match '^(remote_)?theme:\s*(\S+)') { return $Matches[2].Trim('"', "'") }
    }
    return $null
}

function Get-PublishedFiles {
    $excludes = Get-JekyllExcludes
    $files = [System.Collections.Generic.List[string]]::new()
    $extensions = @('.html', '.md', '.css', '.js')
    foreach ($item in Get-ChildItem -LiteralPath $Root -Force) {
        $name = $item.Name
        if ($excludes -contains $name) { continue }
        if ($name.StartsWith('.')) { continue }
        if ($name.StartsWith('_') -and $name -notin @('_layouts', '_includes')) { continue }
        if (-not $item.PSIsContainer) {
            if ($extensions -contains $item.Extension.ToLowerInvariant()) { $files.Add($name) }
            continue
        }
        foreach ($file in Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Force) {
            if ($extensions -notcontains $file.Extension.ToLowerInvariant()) { continue }
            $relative = [IO.Path]::GetRelativePath($Root, $file.FullName) -replace '\\', '/'
            $segments = $relative.Split('/')
            $hidden = $false
            for ($i = 1; $i -lt $segments.Count; $i++) {
                if ($segments[$i].StartsWith('.') -or $segments[$i].StartsWith('_')) { $hidden = $true; break }
            }
            if (-not $hidden) { $files.Add($relative) }
        }
    }
    return $files
}

$hostPattern = '(?:https?:|wss?:)?//([A-Za-z0-9.-]+\.[A-Za-z]{2,})'
$options = [Text.RegularExpressions.RegexOptions]'IgnoreCase, Compiled'
# The published set is 39 MB, and six whole-file regex passes over it took 3.8 s (measured
# 2026-10-06). One pass finds the loading tags whose attributes hold '//'; every other pass runs only
# when an ordinal probe or an earlier candidate says it can find something. That brings the gate to
# 1.9 s, most of it the one pass and process start.
$rxTagExt = [regex]::new('<(script|img|iframe|source|video|audio|embed|track|input|link)\b([^>]*?//[^>]*)>', $options)
$rxBodyTag = [regex]::new('<(script|style)\b[^>]*>', $options)
$rxSrc = [regex]::new('\b(?:src|poster)\s*=\s*["'']?' + $hostPattern, $options)
$rxSrcset = [regex]::new('\bsrcset\s*=\s*"([^"]*)"', $options)
$rxHref = [regex]::new('\bhref\s*=\s*["'']?' + $hostPattern, $options)
$rxRel = [regex]::new('\brel\s*=\s*["'']?([^"''>]+)', $options)
$rxStyleAttr = [regex]::new('\bstyle\s*=\s*"([^"]*)"', $options)
$rxMdImage = [regex]::new('!\[[^\]]*\]\(\s*<?' + $hostPattern, $options)
$rxCss = [regex]::new('(?:@import\s+(?:url\(\s*)?|url\(\s*)["'']?' + $hostPattern, $options)
$rxJsCall = [regex]::new('\b(?:fetch|fetchWithTimeout|sendBeacon|EventSource|WebSocket|importScripts|import)\s*\(\s*["''`]' + $hostPattern, $options)
$rxXhrOpen = [regex]::new('\.open\(\s*["''][A-Za-z]+["'']\s*,\s*["''`]' + $hostPattern, $options)
$rxAnyHost = [regex]::new($hostPattern, $options)
$rxFence = [regex]::new('(?ms)^[ \t]*(```|~~~).*?^[ \t]*\1', [Text.RegularExpressions.RegexOptions]'Compiled')
$rxCodeSpan = [regex]::new('`[^`\r\n]+`', [Text.RegularExpressions.RegexOptions]'Compiled')
$navigationalRel = @('canonical', 'alternate', 'author', 'license', 'me', 'help', 'search', 'bookmark', 'prev', 'next', 'external', 'shortlink')
$jsProbes = @('fetch', 'sendBeacon', 'EventSource', 'WebSocket', 'importScripts', 'import(', '.open(')

function Add-Hosts([System.Collections.Generic.HashSet[string]]$Set, [Text.RegularExpressions.MatchCollection]$Found) {
    foreach ($m in $Found) { [void]$Set.Add($m.Groups[1].Value.ToLowerInvariant()) }
}

function Get-InertSpans([string]$Text, [bool]$Markdown) {
    # HTML comments, Markdown fences and code spans render as nothing or as text, never as a load.
    $spans = [System.Collections.Generic.List[int[]]]::new()
    $start = $Text.IndexOf('<!--', [StringComparison]::Ordinal)
    while ($start -ge 0) {
        $end = $Text.IndexOf('-->', $start + 4, [StringComparison]::Ordinal)
        if ($end -lt 0) { $end = $Text.Length }
        $spans.Add(@($start, $end))
        $start = $Text.IndexOf('<!--', $end, [StringComparison]::Ordinal)
    }
    if ($Markdown) {
        foreach ($m in $rxFence.Matches($Text)) { $spans.Add(@($m.Index, ($m.Index + $m.Length))) }
        foreach ($m in $rxCodeSpan.Matches($Text)) { $spans.Add(@($m.Index, ($m.Index + $m.Length))) }
    }
    return , $spans
}

function Test-Inert([System.Collections.Generic.List[int[]]]$Spans, [int]$Index) {
    foreach ($span in $Spans) { if ($Index -ge $span[0] -and $Index -lt $span[1]) { return $true } }
    return $false
}

function Add-JsHosts([System.Collections.Generic.HashSet[string]]$Set, [string]$Code) {
    Add-Hosts $Set $rxJsCall.Matches($Code)
    Add-Hosts $Set $rxXhrOpen.Matches($Code)
}

function Add-LinkHost([System.Collections.Generic.HashSet[string]]$Set, [string]$Attrs) {
    $href = $rxHref.Match($Attrs)
    if (-not $href.Success) { return }
    $hrefHost = $href.Groups[1].Value.ToLowerInvariant()
    if (-not (Test-ThirdParty $hrefHost)) { return }
    $rel = $rxRel.Match($Attrs)
    if ($rel.Success) {
        $words = $rel.Groups[1].Value.ToLowerInvariant().Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
        $loading = @($words | Where-Object { $navigationalRel -notcontains $_ })
        if ($words.Count -gt 0 -and $loading.Count -eq 0) { return }
    }
    [void]$Set.Add($hrefHost)
}

function Get-LoadedHosts([string]$Relative, [string]$Text) {
    $set = [System.Collections.Generic.HashSet[string]]::new()
    $ext = [IO.Path]::GetExtension($Relative).ToLowerInvariant()
    if ($ext -eq '.css') { Add-Hosts $set $rxCss.Matches($Text); return $set }
    if ($ext -eq '.js') { Add-JsHosts $set $Text; return $set }
    $markdown = $ext -eq '.md'
    $ci = [StringComparison]::OrdinalIgnoreCase
    $hasUrl = $Text.IndexOf('url(', $ci) -ge 0 -or $Text.IndexOf('@import', $ci) -ge 0
    $jsBody = $false
    if ($Text.IndexOf('<script', $ci) -ge 0) {
        foreach ($probe in $jsProbes) { if ($Text.IndexOf($probe, $ci) -ge 0) { $jsBody = $true; break } }
    }
    $styleBody = $hasUrl -and $Text.IndexOf('<style', $ci) -ge 0
    $tags = @($rxTagExt.Matches($Text))
    $bodies = if ($jsBody -or $styleBody) { @($rxBodyTag.Matches($Text)) } else { @() }
    $styles = if ($hasUrl) { @($rxStyleAttr.Matches($Text)) } else { @() }
    $images = if ($markdown -and $Text.IndexOf('![', [StringComparison]::Ordinal) -ge 0) { @($rxMdImage.Matches($Text)) } else { @() }
    if ($tags.Count + $bodies.Count + $styles.Count + $images.Count -eq 0) { return $set }
    # Computed only once a candidate exists: the Markdown fence and code-span passes cost 0.3 s
    # over the corpus, and almost no page has a candidate to test against them.
    $inert = Get-InertSpans $Text $markdown
    foreach ($tag in $tags) {
        if (Test-Inert $inert $tag.Index) { continue }
        $attrs = $tag.Groups[2].Value
        if ($tag.Groups[1].Value -ieq 'link') { Add-LinkHost $set $attrs; continue }
        Add-Hosts $set $rxSrc.Matches($attrs)
        foreach ($m in $rxSrcset.Matches($attrs)) { Add-Hosts $set $rxAnyHost.Matches($m.Groups[1].Value) }
    }
    foreach ($tag in $bodies) {
        if (Test-Inert $inert $tag.Index) { continue }
        $name = $tag.Groups[1].Value.ToLowerInvariant()
        if (($name -eq 'script' -and -not $jsBody) -or ($name -eq 'style' -and -not $styleBody)) { continue }
        $bodyStart = $tag.Index + $tag.Length
        $bodyEnd = $Text.IndexOf("</$name", $bodyStart, [StringComparison]::OrdinalIgnoreCase)
        if ($bodyEnd -lt 0) { $bodyEnd = $Text.Length }
        $body = $Text.Substring($bodyStart, $bodyEnd - $bodyStart)
        if ($name -eq 'style') { Add-Hosts $set $rxCss.Matches($body) } else { Add-JsHosts $set $body }
    }
    foreach ($m in $styles) {
        if (-not (Test-Inert $inert $m.Index)) { Add-Hosts $set $rxCss.Matches($m.Groups[1].Value) }
    }
    foreach ($m in $images) {
        if (-not (Test-Inert $inert $m.Index)) { [void]$set.Add($m.Groups[1].Value.ToLowerInvariant()) }
    }
    return $set
}

function Test-ThirdParty([string]$HostName) {
    foreach ($own in $firstParty) {
        if ($HostName -eq $own -or $HostName.EndsWith('.' + $own)) { return $false }
    }
    return $true
}

function Test-AdvertisingPage([string]$Relative) {
    foreach ($glob in $advertisingPages) { if ($Relative -like $glob) { return $true } }
    return $false
}

$files = Get-PublishedFiles
$loads = @{}
foreach ($relative in $files) {
    $text = [IO.File]::ReadAllText((Join-Path $Root $relative))
    if ($text.IndexOf('//', [StringComparison]::Ordinal) -lt 0) { continue }
    $hosts = @(Get-LoadedHosts $relative $text | Where-Object { Test-ThirdParty $_ })
    if ($hosts.Count -gt 0) { $loads[$relative] = $hosts }
}

$runDeclared = $Dimension -in @('declared', 'all')
$runPrivacy = $Dimension -in @('privacy', 'all')
$runAdvertising = $Dimension -in @('advertising', 'all')

if ($runDeclared) {
    foreach ($orphan in $runtimeOrphans) {
        $findings.Add("[RUNTIME] $orphan is a run-time host whose loader has no record in Origins - declare the loader or drop the record")
    }
    $seen = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($relative in ($loads.Keys | Sort-Object)) {
        foreach ($hostName in $loads[$relative]) {
            [void]$seen.Add($hostName)
            if (-not $declared.ContainsKey($hostName)) {
                $findings.Add("[UNDECLARED] ${relative}: loads $hostName, which has no record in $(Split-Path -Leaf $Declaration)")
            }
        }
    }
    $theme = Get-ConfigTheme
    foreach ($hostName in ($declared.Keys | Sort-Object)) {
        $viaTheme = $declared[$hostName].Theme -and $theme -and ($declared[$hostName].Theme -eq $theme)
        # A run-time host is invisible to this read by definition; its record comes from a browser sweep.
        if ($declared[$hostName].Runtime) { continue }
        if (-not $seen.Contains($hostName) -and -not $viaTheme) {
            $findings.Add("[STALE] $hostName is declared, but no published file loads it and no configured theme names it - remove the record and its privacy text")
        }
    }
}

if ($runPrivacy) {
    foreach ($page in $privacyPages) {
        $path = Join-Path $Root $page
        if (-not (Test-Path -LiteralPath $path)) {
            $findings.Add("[PRIVACY] $page is missing - the declared hosts have no page to be named on")
            continue
        }
        $text = [IO.File]::ReadAllText($path)
        foreach ($hostName in ($declared.Keys | Sort-Object)) {
            if ($text.IndexOf($hostName, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
                $findings.Add("[PRIVACY] ${page}: does not name $hostName - describe its purpose and what it receives")
            }
        }
    }
}

if ($runAdvertising) {
    $adHosts = @($declared.Keys | Where-Object { $declared[$_].Advertising })
    $layoutAds = @{}
    foreach ($relative in ($loads.Keys | Sort-Object)) {
        $ads = @($loads[$relative] | Where-Object { $adHosts -contains $_ })
        if ($ads.Count -eq 0) { continue }
        if ($relative -match '^_(layouts|includes)/') { $layoutAds[$relative] = $ads }
        if (Test-AdvertisingPage $relative) { continue }
        $what = if ($trustPages -contains $relative) { 'a trust or privacy page' } else { 'a page whose role carries no advertising' }
        $findings.Add("[ADVERTISING] ${relative}: $what loads $($ads -join ', ')")
    }
    foreach ($page in $trustPages) {
        $path = Join-Path $Root $page
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $head = ([IO.File]::ReadAllLines($path) | Select-Object -First 12) -join "`n"
        if ($head -notmatch '(?m)^layout:\s*["'']?([A-Za-z0-9_-]+)') { continue }
        $layout = "_layouts/$($Matches[1]).html"
        if ($layoutAds.ContainsKey($layout)) {
            $findings.Add("[ADVERTISING] ${page}: a trust or privacy page renders through $layout, which loads $($layoutAds[$layout] -join ', ')")
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Host "assert-site-origins: FAIL ($($findings.Count) finding(s))" -ForegroundColor Red
    foreach ($finding in $findings) { Write-Host "  $finding" }
    exit 1
}
Write-Host "assert-site-origins: PASS ($($files.Count) published files, $($declared.Count) declared hosts, $($loads.Count) files load one)"
exit 0
