<#
.SYNOPSIS
    Shared helpers of the site-address tooling: the redirect manifest, the forwarder page it produces and
    the sets of pages the site publishes (S4097).

.DESCRIPTION
    Dot-sourced by scripts/docs/generate-site-redirects.ps1, scripts/docs/retire-docs-page.ps1 and
    scripts/quality/assert-site-addresses.ps1, so the page the generator writes and the page the gate
    compares against come from one function. Returns values and never exits.
#>
Set-StrictMode -Version Latest

$script:SiteBaseUrl = 'https://serzhyale.github.io/FastMediaSorter_mob_v2'
$script:ForwarderMarkerRegex = '<meta\s+name="fms-forwarder"\s+content="(moved|retired)"'
# Hubs and indexes written by their own generators or by hand, not by generate-docs-pages.ps1.
$script:DeclaredHubRegex = '^documentation/((index|overview|subject-index)(-ru|-uk)?\.html|design-system/index\.html|general/glossary(-ru|-uk)?\.html)$'

function Read-JsonlRecords {
    param([Parameter(Mandatory)][string]$Path)
    $records = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return , $records }
    foreach ($line in [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $records.Add(($line | ConvertFrom-Json))
    }
    return , $records
}

function Get-ForwarderAddressError {
    param([string]$Address, [string]$Field)
    if ([string]::IsNullOrWhiteSpace($Address)) { return "$Field is empty" }
    if ($Address -match '^(/|[a-zA-Z]+:)' -or $Address.Contains('\') -or $Address -match '(^|/)\.\.(/|$)') {
        return "$Field '$Address' must be a site-relative path without '..' or a leading slash"
    }
    if ($Address -notmatch '\.html$') { return "$Field '$Address' must end in .html" }
    return $null
}

function Test-RedirectRecord {
    param([Parameter(Mandatory)]$Record)
    $errors = [System.Collections.Generic.List[string]]::new()
    $props = $Record.PSObject.Properties.Name
    foreach ($name in 'from_path', 'to_path', 'reason', 'ticket', 'date') {
        if ($props -notcontains $name) { $errors.Add("missing field '$name'") }
    }
    if ($errors.Count -gt 0) { return , $errors }
    $fromError = Get-ForwarderAddressError -Address $Record.from_path -Field 'from_path'
    if ($fromError) { $errors.Add($fromError) }
    if ($null -ne $Record.to_path) {
        $toError = Get-ForwarderAddressError -Address $Record.to_path -Field 'to_path'
        if ($toError) { $errors.Add($toError) }
        if ($Record.to_path -eq $Record.from_path) { $errors.Add('to_path equals from_path') }
    }
    if ([string]::IsNullOrWhiteSpace($Record.reason)) { $errors.Add('reason is empty') }
    if ($Record.ticket -notmatch '^S\d{4}$') { $errors.Add("ticket '$($Record.ticket)' is not an Sxxxx id") }
    if ($Record.date -notmatch '^\d{4}-\d{2}-\d{2}$') { $errors.Add("date '$($Record.date)' is not yyyy-MM-dd") }
    return , $errors
}

function Get-RelativeAddress {
    param([Parameter(Mandatory)][string]$From, [Parameter(Mandatory)][string]$To)
    $base = [Uri]::new("http://site.invalid/$From")
    $target = [Uri]::new("http://site.invalid/$To")
    $relative = $base.MakeRelativeUri($target).ToString()
    if ([string]::IsNullOrEmpty($relative)) { return [IO.Path]::GetFileName($To) }
    return $relative
}

# Byte-exact page for a record. No front matter, so Jekyll copies it verbatim and the file's own path is
# its address; the page therefore never declares a permalink and never reaches the sitemap.
function Get-ForwarderHtml {
    param([Parameter(Mandatory)]$Record)
    $from = [string]$Record.from_path
    $moved = $null -ne $Record.to_path
    $homeRel = Get-RelativeAddress -From $from -To 'documentation/index.html'
    $landingRel = Get-RelativeAddress -From $from -To 'index.html'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('<!DOCTYPE html>')
    $lines.Add('<html lang="en">')
    $lines.Add('<head>')
    $lines.Add('    <meta charset="UTF-8">')
    $lines.Add('    <meta name="viewport" content="width=device-width, initial-scale=1.0">')
    $lines.Add('    <meta name="color-scheme" content="light dark">')
    $lines.Add('    <meta name="robots" content="noindex">')
    if ($moved) {
        $toRel = Get-RelativeAddress -From $from -To ([string]$Record.to_path)
        $lines.Add('    <meta name="fms-forwarder" content="moved">')
        $lines.Add('    <title>Page moved - Fast Media Sorter</title>')
        $lines.Add("    <link rel=`"canonical`" href=`"$script:SiteBaseUrl/$($Record.to_path)`">")
        $lines.Add("    <meta http-equiv=`"refresh`" content=`"0; url=$toRel`">")
        $lines.Add('</head>')
        $lines.Add('<body style="font-family: system-ui, sans-serif; max-width: 40rem; margin: 3rem auto; padding: 0 1rem;">')
        $lines.Add("    <p lang=`"en`">This page has moved. <a href=`"$toRel`">Open the new address</a>.</p>")
        $lines.Add("    <p lang=`"ru`">Эта страница переехала. <a href=`"$toRel`">Открыть новый адрес</a>.</p>")
        $lines.Add("    <p lang=`"uk`">Ця сторінка переїхала. <a href=`"$toRel`">Відкрити нову адресу</a>.</p>")
    } else {
        $lines.Add('    <meta name="fms-forwarder" content="retired">')
        $lines.Add('    <title>Page removed - Fast Media Sorter</title>')
        $lines.Add('</head>')
        $lines.Add('<body style="font-family: system-ui, sans-serif; max-width: 40rem; margin: 3rem auto; padding: 0 1rem;">')
        $lines.Add("    <p lang=`"en`">This page was removed together with the feature it described. Go to the <a href=`"$homeRel`">documentation home</a> or the <a href=`"$landingRel`">landing page</a>.</p>")
        $lines.Add("    <p lang=`"ru`">Эта страница убрана вместе с функцией, которую она описывала. Перейдите на <a href=`"$homeRel`">главную документации</a> или на <a href=`"$landingRel`">главную страницу сайта</a>.</p>")
        $lines.Add("    <p lang=`"uk`">Цю сторінку прибрано разом з функцією, яку вона описувала. Перейдіть на <a href=`"$homeRel`">головну документації</a> або на <a href=`"$landingRel`">головну сторінку сайту</a>.</p>")
    }
    $lines.Add('</body>')
    $lines.Add('</html>')
    return (($lines -join "`n") + "`n")
}

# Every documentation/ page generate-docs-pages.ps1 produces, keyed by its repo-relative path.
function Get-RecipeOutputs {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $outputs = @{}
    foreach ($dir in 'recipes', 'recipes-ru', 'recipes-uk') {
        $contentDir = Join-Path $RepoRoot "docs/content/$dir"
        if (-not (Test-Path -LiteralPath $contentDir)) { continue }
        foreach ($file in Get-ChildItem -LiteralPath $contentDir -Filter *.md -File) {
            $text = [IO.File]::ReadAllText($file.FullName)
            $pageId = if ($text -match '(?m)^page_id:\s*["'']?([^"''\r\n]+?)["'']?\s*$') { $Matches[1] } else { $null }
            $path = if ($text -match '(?m)^canonical_url:\s*["'']?(documentation/[^"''\s]+\.html)') {
                $Matches[1]
            } else {
                'documentation/' + [IO.Path]::ChangeExtension($file.Name, '.html')
            }
            $outputs[$path] = [pscustomobject]@{ PageId = $pageId; Source = "docs/content/$dir/$($file.Name)" }
        }
    }
    return $outputs
}

$script:PublishedAddressCache = @{}

# The permalink of a page, read from the first lines of its front matter; $null for a file without one.
function Read-FrontMatterPermalink {
    param([Parameter(Mandatory)][string]$FullName)
    $reader = [IO.StreamReader]::new($FullName)
    try {
        if ($reader.ReadLine() -notmatch '^---\s*$') { return $null }
        for ($i = 0; $i -lt 80; $i++) {
            $line = $reader.ReadLine()
            if ($null -eq $line -or $line -match '^---\s*$') { return $null }
            if ($line -match '^permalink:\s*["'']?([^"''\s]+)') { return $Matches[1] }
        }
        return $null
    } finally { $reader.Dispose() }
}

# Every address the site answers, as a hashtable from the site-relative address ('docs/howto/index-uk.html')
# to the repo-relative file that serves it. Jekyll's source is the repository root: _config.yml `exclude`
# entries and `_`/`.`-prefixed paths are not published, a page with a `permalink:` is served there and any
# other file at its own path. Same rules as the published set of scripts/quality/assert-docs-crosslinks.ps1.
function Get-PublishedAddresses {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    if ($script:PublishedAddressCache.ContainsKey($root)) { return $script:PublishedAddressCache[$root] }
    $exclude = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($n in 'node_modules', 'vendor', 'Gemfile', 'Gemfile.lock') { [void]$exclude.Add($n) }
    $configPath = Join-Path $root '_config.yml'
    if (Test-Path -LiteralPath $configPath) {
        $inExclude = $false
        foreach ($line in [IO.File]::ReadAllLines($configPath)) {
            if ($line -match '^exclude:\s*$') { $inExclude = $true; continue }
            if ($inExclude) {
                if ($line -match '^\s+-\s+["'']?([^"''\s#]+)') { [void]$exclude.Add($Matches[1].TrimEnd('/')) }
                elseif ($line -match '^\S') { $inExclude = $false }
            }
        }
    }
    $addresses = @{}
    $top = Get-ChildItem -LiteralPath $root -Force | Where-Object { $_.Name -notmatch '^[._]' -and -not $exclude.Contains($_.Name) }
    foreach ($entry in $top) {
        $files = if ($entry.PSIsContainer) {
            Get-ChildItem -LiteralPath $entry.FullName -Recurse -File -Force |
                Where-Object { $_.FullName.Substring($root.Length) -notmatch '[\\/][._]' }
        } else { @($entry) }
        foreach ($file in $files) {
            $rel = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
            $isPage = $file.Extension -in '.md', '.markdown', '.html'
            $permalink = if ($isPage) { Read-FrontMatterPermalink -FullName $file.FullName } else { $null }
            $address = if ($permalink) { $permalink.TrimStart('/') }
            elseif ($file.Extension -in '.md', '.markdown') { $rel -replace '\.(md|markdown)$', '.html' }
            else { $rel }
            if ($address -eq '' -or $address.EndsWith('/')) { $address += 'index.html' }
            $addresses[$address] = $rel
        }
    }
    $script:PublishedAddressCache[$root] = $addresses
    return $addresses
}

# True when the repository serves something at the address (a trailing slash means its index page).
function Test-SiteAddressExists {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$Address)
    $normalized = $Address.TrimStart('/')
    if ($normalized -eq '' -or $normalized.EndsWith('/')) { $normalized += 'index.html' }
    return (Get-PublishedAddresses -RepoRoot $RepoRoot).ContainsKey($normalized)
}

# Html files that can carry a forwarder: the documentation tree, docs/ and the site root.
function Get-SiteHtmlFiles {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $files = [System.Collections.Generic.List[object]]::new()
    $docsTree = Join-Path $RepoRoot 'documentation'
    if (Test-Path -LiteralPath $docsTree) {
        foreach ($f in Get-ChildItem -LiteralPath $docsTree -Recurse -Filter *.html -File) { $files.Add($f) }
    }
    $docsDir = Join-Path $RepoRoot 'docs'
    if (Test-Path -LiteralPath $docsDir) {
        foreach ($f in Get-ChildItem -LiteralPath $docsDir -Filter *.html -File) { $files.Add($f) }
    }
    foreach ($f in Get-ChildItem -LiteralPath $RepoRoot -Filter *.html -File) { $files.Add($f) }
    return , $files
}

function Get-RepoRelativePath {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$FullName)
    $rootFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    return $FullName.Substring($rootFull.Length + 1).Replace('\', '/')
}

# The registry refuses a file under an indexable record that neither declares a permalink nor is named in
# sitemap_exclude, and a forwarder has no permalink by design. The rows below are therefore derived from the
# manifest, never hand-written, so a new forwarder needs no registry edit of its own.
$script:ForwarderExcludeReason = 'Forwarder left at a moved or retired address; it announces nothing and only answers old links'
$script:ForwarderExcludeRecordId = 'docs-corpus-pages'
$script:ForwarderExcludePathRegex = '^documentation/[^/]+/[^/]+\.html$'

function Find-JsonArrayEnd {
    param([Parameter(Mandatory)][string]$Text, [Parameter(Mandatory)][int]$OpenIndex)
    $depth = 0
    $inString = $false
    for ($i = $OpenIndex; $i -lt $Text.Length; $i++) {
        $c = $Text[$i]
        if ($inString) {
            if ($c -eq '\') { $i++ } elseif ($c -eq '"') { $inString = $false }
            continue
        }
        if ($c -eq '"') { $inString = $true }
        elseif ($c -eq '[') { $depth++ }
        elseif ($c -eq ']') { $depth--; if ($depth -eq 0) { return $i } }
    }
    return -1
}

# Returns the registry text with the forwarder rows of the manifest merged into the record's
# sitemap_exclude; every other row and every other byte of the file is kept.
function Get-RegistryWithForwarderExclusions {
    param([Parameter(Mandatory)][string]$RegistryText, [Parameter(Mandatory)]$Records)
    $lineMatch = [regex]::Match($RegistryText, '(?m)^\{"id":"' + [regex]::Escape($script:ForwarderExcludeRecordId) + '".*$')
    if (-not $lineMatch.Success) { throw "registry record '$script:ForwarderExcludeRecordId' not found" }
    $line = $lineMatch.Value
    $suffix = if ($line.EndsWith("`r")) { "`r" } else { '' }
    $body = $line.Substring(0, $line.Length - $suffix.Length)
    $record = $body | ConvertFrom-Json
    $existing = @()
    if ($record.PSObject.Properties.Name -contains 'sitemap_exclude') { $existing = @($record.sitemap_exclude) }
    $rows = [System.Collections.Generic.List[string]]::new()
    foreach ($row in $existing) {
        if ($row.reason -ceq $script:ForwarderExcludeReason) { continue }
        $rows.Add(($row | ConvertTo-Json -Compress))
    }
    $forwarders = @($Records | Where-Object { $_.from_path -match $script:ForwarderExcludePathRegex } |
            ForEach-Object { $_.from_path } | Sort-Object -Unique)
    foreach ($path in $forwarders) {
        $rows.Add(([ordered]@{ path = $path; reason = $script:ForwarderExcludeReason } | ConvertTo-Json -Compress))
    }
    $newArray = '[' + ($rows -join ',') + ']'
    $key = '"sitemap_exclude":['
    $keyAt = $body.IndexOf($key, [StringComparison]::Ordinal)
    if ($keyAt -ge 0) {
        $open = $keyAt + $key.Length - 1
        $close = Find-JsonArrayEnd -Text $body -OpenIndex $open
        if ($close -lt 0) { throw "registry record '$script:ForwarderExcludeRecordId' has an unterminated sitemap_exclude" }
        $newBody = $body.Substring(0, $open) + $newArray + $body.Substring($close + 1)
    } elseif ($rows.Count -gt 0) {
        $newBody = $body.Substring(0, $body.Length - 1) + ',"sitemap_exclude":' + $newArray + '}'
    } else {
        $newBody = $body
    }
    return $RegistryText.Substring(0, $lineMatch.Index) + $newBody + $suffix + $RegistryText.Substring($lineMatch.Index + $lineMatch.Length)
}
