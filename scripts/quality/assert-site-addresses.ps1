<#
.SYNOPSIS
    Holds the site's address rules: the not-found page, the forwarders of moved or retired pages, the
    retirement of a page together with its capability, the addresses held outside the site and the form of
    the language suffix (S4097, S4101).

.DESCRIPTION
    Contract SITE-STRUCTURE 0.1, rules 8, 13 and 15. Dimensions, each judging one subject:

      locale-scheme  docs/site-address-groups.json: every published address that ends in a language suffix
                  (`_RU`, `.ru`, `-ru`, an English `_EN` and so on, the languages of _data/languages.yml)
                  belongs to a declared page group and spells the suffix in that group's form, today
                  `<name>-<lang>.html` everywhere. Forwarders at the old addresses are exempt.
      held-addresses  docs/site-held-addresses.jsonl against the holders it names (app and watch sources,
                  listings, READMEs): every site address a holder carries is listed, every listed address
                  is still carried and by a declared holder, answers a published page and is not itself a
                  forwarder, every sibling-site address is listed, and the help-link resolver's pages,
                  translated languages and their siblings match the page manifest and the published set.
                  A new holder is one glob in the list's `holders` record; a new address one `address`
                  record naming its holders. The site root is written '/'.

      not-found   404.html: the file exists, declares /404.html, is noindex, offers the search, the
                  portal home and the landing, carries the en/ru/uk blocks and builds every address
                  with relative_url (the host serves it from any request depth).
      redirects   docs/site-redirects.jsonl: each record is well-formed, its forwarder page is current,
                  its target exists, its source is not a live page, no record chains into another and
                  the source is absent from the search index and the sitemap.
      retirement  a page with is_published false has a redirect record; a page whose every coverage row
                  is removed is unpublished; no html under documentation/ is outside the recipe outputs,
                  the declared generator hubs and the forwarders.

    PER-TICKET by Rule 33: the subject is a set of files whose author knows what they meant, and the
    corpus gates beside it (assert-docs-search, assert-docs-crosslinks) are placed the same way.

.PARAMETER Dimension
    not-found | redirects | retirement | held-addresses | locale-scheme | all (default).

.PARAMETER RepoRoot
    Repository root; overridable so the tests can judge a synthetic site.

.NOTES
    Exit codes:
      0  every requested dimension passed
      1  at least one finding
      2  could not verify: an input file is missing or unreadable, or an unknown dimension was named
#>
[CmdletBinding()]
param(
    [ValidateSet('not-found', 'redirects', 'retirement', 'held-addresses', 'locale-scheme', 'all')]
    [string] $Dimension = 'all',
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
. (Join-Path $PSScriptRoot '../docs/lib/site-addresses.ps1')
. (Join-Path $PSScriptRoot '../docs/lib/held-addresses.ps1')
. (Join-Path $PSScriptRoot '../docs/lib/doc-stamp.ps1')
. (Join-Path $PSScriptRoot '../docs/Read-DocumentationSearchIndex.ps1')

Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = $Dimension; files = '404.html,docs/site-redirects.jsonl,docs/site-held-addresses.jsonl,docs/site-address-groups.json,documentation' })

$findings = [System.Collections.Generic.List[string]]::new()

function Test-NotFoundPage {
    $path = Join-Path $RepoRoot '404.html'
    if (-not (Test-Path -LiteralPath $path)) {
        $findings.Add('not-found: 404.html is missing from the repository root.')
        return
    }
    $text = [IO.File]::ReadAllText($path)
    $head = ($text -split "`n" | Select-Object -First 6) -join "`n"
    if ($head -notmatch '(?m)^permalink:\s*/404\.html\s*$') {
        $findings.Add('not-found: 404.html must declare "permalink: /404.html" in its front matter.')
    }
    if ($text -notmatch '<meta\s+name="robots"\s+content="[^"]*noindex') {
        $findings.Add('not-found: 404.html must carry <meta name="robots" content="noindex">.')
    }
    if ($text -notmatch 'data-search-trigger') {
        $findings.Add('not-found: 404.html must offer the documentation search (data-search-trigger).')
    }
    if ($text -notmatch "\{\{\s*'/documentation/'\s*\|\s*relative_url\s*\}\}") {
        $findings.Add('not-found: 404.html must link the documentation home.')
    }
    if ($text -notmatch "href=`"\{\{\s*'/'\s*\|\s*relative_url\s*\}\}`"") {
        $findings.Add('not-found: 404.html must link the landing page.')
    }
    foreach ($lang in 'en', 'ru', 'uk') {
        if ($text -notmatch "<section[^>]*lang=`"$lang`"") {
            $findings.Add("not-found: 404.html lacks the $lang text block (<section lang=`"$lang`">).")
        }
    }
    if ($text -notmatch "src=`"\{\{\s*'/documentation/assets/search\.js'\s*\|\s*relative_url\s*\}\}`"") {
        $findings.Add('not-found: 404.html must load search.js through relative_url.')
    }
    foreach ($m in [regex]::Matches($text, '(?:href|src)="([^"]*)"')) {
        $value = $m.Groups[1].Value
        if ($value -match '^(https?://|#|mailto:)' -or $value -match "^\{\{\s*'/[^']*'\s*\|\s*relative_url\s*\}\}$") { continue }
        $findings.Add("not-found: 404.html address '$value' is not absolute or relative_url-built; the host serves the page from any depth.")
    }
}


function Get-LiveAddresses {
    # Addresses a page answers today: every recipe output and every page the manifest still publishes.
    $live = @{}
    foreach ($key in (Get-RecipeOutputs -RepoRoot $RepoRoot).Keys) { $live[$key] = $true }
    $pagesPath = Join-Path $RepoRoot 'docs/docs-pages-manifest.jsonl'
    foreach ($page in (Read-JsonlRecords -Path $pagesPath)) {
        if ($page.is_published -and $page.canonical_path) { $live[[string]$page.canonical_path] = $true }
    }
    return $live
}

function Test-Redirects {
    $records = Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/site-redirects.jsonl')
    $live = Get-LiveAddresses
    $fromSet = @{}
    foreach ($record in $records) { if ($record.PSObject.Properties.Name -contains 'from_path') { $fromSet[[string]$record.from_path] = $true } }

    $searchPaths = @{}
    $searchIndex = Join-Path $RepoRoot 'documentation/assets/search-index.json'
    if (Test-Path -LiteralPath $searchIndex) {
        foreach ($page in (Read-DocumentationSearchIndex -Path $searchIndex)) { $searchPaths[[string]$page.url] = $true }
    }
    $sitemapPath = Join-Path $RepoRoot 'sitemap.xml'
    $sitemap = if (Test-Path -LiteralPath $sitemapPath) { [IO.File]::ReadAllText($sitemapPath) } else { '' }

    $seen = @{}
    foreach ($record in $records) {
        $problems = Test-RedirectRecord -Record $record
        if ($problems.Count -gt 0) {
            $findings.Add("redirects: record '$($record.from_path)': $($problems -join '; ').")
            continue
        }
        $from = [string]$record.from_path
        if ($seen.ContainsKey($from)) { $findings.Add("redirects: from_path '$from' is listed twice.") }
        $seen[$from] = $true

        $target = Join-Path $RepoRoot $from
        if (-not (Test-Path -LiteralPath $target)) {
            $findings.Add("redirects: forwarder '$from' is missing; run scripts/docs/generate-site-redirects.ps1.")
        } elseif ((Remove-DocStamp -Text ([IO.File]::ReadAllText($target))) -cne (Get-ForwarderHtml -Record $record)) {
            $findings.Add("redirects: forwarder '$from' differs from its record; run scripts/docs/generate-site-redirects.ps1.")
        }
        if ($live.ContainsKey($from)) {
            $findings.Add("redirects: '$from' is still a live page; retire it before listing it as a forwarder.")
        }
        if ($null -ne $record.to_path) {
            $to = [string]$record.to_path
            if (-not (Test-SiteAddressExists -RepoRoot $RepoRoot -Address $to)) {
                $findings.Add("redirects: '$from' forwards to '$to', which no page answers.")
            }
            if ($fromSet.ContainsKey($to)) {
                $findings.Add("redirects: '$from' forwards to '$to', itself a forwarder; point it at the final page.")
            }
        }
        if ($searchPaths.ContainsKey($from)) {
            $findings.Add("redirects: '$from' is still in the search index; run scripts/docs/generate-docs-search-index.ps1.")
        }
        if ($sitemap.Contains("/$from</loc>")) {
            $findings.Add("redirects: '$from' is announced in sitemap.xml; run scripts/document_registry/generate.ps1.")
        }
    }

    $registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
    if (Test-Path -LiteralPath $registryPath) {
        $registryText = [IO.File]::ReadAllText($registryPath)
        if ((Get-RegistryWithForwarderExclusions -RegistryText $registryText -Records $records) -cne $registryText) {
            $findings.Add('redirects: docs/DOCUMENT_REGISTRY.jsonl forwarder sitemap_exclude rows differ from the manifest; run scripts/docs/generate-site-redirects.ps1.')
        }
    }
}

function Test-Retirement {
    $records = Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/site-redirects.jsonl')
    $fromSet = @{}
    foreach ($record in $records) { if ($record.PSObject.Properties.Name -contains 'from_path') { $fromSet[[string]$record.from_path] = $true } }
    $pages = Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/docs-pages-manifest.jsonl')
    $coverage = Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/coverage-manifest.jsonl')

    $byPage = @{}
    foreach ($row in $coverage) {
        if ([string]::IsNullOrWhiteSpace([string]$row.page_id)) { continue }
        if (-not $byPage.ContainsKey($row.page_id)) { $byPage[$row.page_id] = [System.Collections.Generic.List[object]]::new() }
        $byPage[$row.page_id].Add($row)
    }
    foreach ($page in $pages) {
        $rows = @(if ($byPage.ContainsKey($page.page_id)) { $byPage[$page.page_id] })
        if ($page.is_published -and $rows.Count -gt 0 -and @($rows | Where-Object { $_.status -ne 'removed' }).Count -eq 0) {
            $findings.Add("retirement: page '$($page.page_id)' documents only removed capabilities and is still published; run scripts/docs/retire-docs-page.ps1.")
        }
        if (-not $page.is_published -and -not $fromSet.ContainsKey([string]$page.canonical_path)) {
            $findings.Add("retirement: unpublished page '$($page.page_id)' has no redirect record for '$($page.canonical_path)'.")
        }
    }

    $recipeOut = Get-RecipeOutputs -RepoRoot $RepoRoot
    foreach ($file in (Get-SiteHtmlFiles -RepoRoot $RepoRoot)) {
        $rel = Get-RepoRelativePath -RepoRoot $RepoRoot -FullName $file.FullName
        if ($rel -match '^documentation/(assets|images)/') { continue }
        $head = [IO.File]::ReadAllText($file.FullName)
        $isForwarder = $head -match $script:ForwarderMarkerRegex
        if ($isForwarder) {
            if (-not $fromSet.ContainsKey($rel)) {
                $findings.Add("retirement: '$rel' is a forwarder page that no record in docs/site-redirects.jsonl lists.")
            }
            continue
        }
        if ($rel -notmatch '^documentation/') { continue }
        if ($recipeOut.ContainsKey($rel) -or $rel -match $script:DeclaredHubRegex) { continue }
        $findings.Add("retirement: '$rel' is no recipe output, declared hub or forwarder; delete it or retire it with scripts/docs/retire-docs-page.ps1.")
    }
}

function Stop-CouldNotVerify([string]$Message) {
    Write-Host "assert-site-addresses: COULD NOT VERIFY - $Message" -ForegroundColor Yellow
    exit 2
}

function Test-GlobMatchesAny([string]$Path, [object[]]$Globs) {
    foreach ($glob in $Globs) { if ($Path -match (ConvertTo-GlobRegex -Glob ([string]$glob))) { return $true } }
    return $false
}

function Test-HeldAddressRecordShape($Record, [int]$Number) {
    $names = $Record.PSObject.Properties.Name
    $where = "held-addresses: list record $Number"
    $kind = if ($names -contains 'kind') { [string]$Record.kind } else { '' }
    $required = switch ($kind) {
        'holders' { @('files') }
        'address' { @('address', 'holders') }
        'sibling' { @('address', 'holders') }
        'resolver' { @('holder', 'manifest', 'languages') }
        default { $null }
    }
    if ($null -eq $required) { $findings.Add("$where has kind '$kind'; expected holders, address, sibling or resolver."); return $false }
    foreach ($field in $required) {
        if ($names -notcontains $field) { $findings.Add("$where ($kind) lacks '$field'."); return $false }
    }
    if ($kind -in 'address', 'sibling') {
        $address = [string]$Record.address
        if ($kind -eq 'address' -and ($address -match '^(https?:|//)' -or $address -match '[#?]' -or ($address.StartsWith('/') -and $address -ne '/'))) {
            $findings.Add("$where address '$address' must be site-relative without a scheme, fragment or query (the site root is '/').")
            return $false
        }
        if ($kind -eq 'sibling' -and ($address -notmatch '^https://serzhyale\.github\.io/' -or $address.StartsWith("$script:SiteBaseUrl/"))) {
            $findings.Add("$where sibling '$address' must be a full URL of another serzhyale.github.io site.")
            return $false
        }
        if (@($Record.holders).Count -eq 0) { $findings.Add("$where '$address' names no holder."); return $false }
    }
    return $true
}

function Test-HeldAddresses {
    $listPath = Join-Path $RepoRoot 'docs/site-held-addresses.jsonl'
    if (-not (Test-Path -LiteralPath $listPath)) { Stop-CouldNotVerify 'docs/site-held-addresses.jsonl is missing.' }
    $manifestPath = Join-Path $RepoRoot 'docs/docs-pages-manifest.jsonl'
    if (-not (Test-Path -LiteralPath $manifestPath)) { Stop-CouldNotVerify 'docs/docs-pages-manifest.jsonl is missing.' }

    $records = Read-JsonlRecords -Path $listPath
    $valid = [System.Collections.Generic.List[object]]::new()
    $number = 0
    foreach ($record in $records) {
        $number++
        if (Test-HeldAddressRecordShape -Record $record -Number $number) { $valid.Add($record) }
    }
    $holderRecords = @($valid | Where-Object { $_.kind -eq 'holders' })
    if ($holderRecords.Count -ne 1) {
        $findings.Add("held-addresses: the list needs exactly one 'holders' record, found $($holderRecords.Count).")
        return
    }
    $holderFiles = Resolve-HolderFiles -RepoRoot $RepoRoot -Globs @($holderRecords[0].files | ForEach-Object { [string]$_ })
    $literals = Get-HeldAddressLiterals -RepoRoot $RepoRoot -Files $holderFiles

    $published = Get-PublishedAddresses -RepoRoot $RepoRoot
    $redirects = @{}
    foreach ($r in (Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/site-redirects.jsonl'))) {
        if ($r.PSObject.Properties.Name -contains 'from_path') { $redirects[[string]$r.from_path] = $r }
    }

    $listed = @{ address = @{}; sibling = @{} }
    foreach ($record in $valid) {
        if ($record.kind -notin 'address', 'sibling') { continue }
        $key = [string]$record.address
        if ($listed[$record.kind].ContainsKey($key)) { $findings.Add("held-addresses: '$key' is listed twice."); continue }
        $listed[$record.kind][$key] = $record
    }

    foreach ($pair in @(@('address', $literals.Site), @('sibling', $literals.Sibling))) {
        $kind = $pair[0]; $found = $pair[1]
        foreach ($address in ($found.Keys | Sort-Object)) {
            if (-not $listed[$kind].ContainsKey($address)) {
                $findings.Add("held-addresses: '$address' is held by $(@($found[$address])[0]) but is not in docs/site-held-addresses.jsonl.")
            }
        }
        foreach ($address in ($listed[$kind].Keys | Sort-Object)) {
            $record = $listed[$kind][$address]
            $files = @(if ($found.ContainsKey($address)) { $found[$address] })
            if ($files.Count -eq 0) {
                $findings.Add("held-addresses: '$address' is listed but no holder carries it; remove the record or restore the holder.")
                continue
            }
            foreach ($file in $files) {
                if (-not (Test-GlobMatchesAny -Path $file -Globs @($record.holders))) {
                    $findings.Add("held-addresses: '$address' is carried by $file, which the record does not name.")
                }
            }
            foreach ($glob in @($record.holders)) {
                if (-not ($files | Where-Object { $_ -match (ConvertTo-GlobRegex -Glob ([string]$glob)) })) {
                    $findings.Add("held-addresses: holder '$glob' of '$address' matches no file that carries it.")
                }
            }
            if ($kind -ne 'address') { continue }
            $normalized = if ($address -eq '/') { 'index.html' } elseif ($address.EndsWith('/')) { $address + 'index.html' } else { $address }
            if ($redirects.ContainsKey($normalized)) {
                $target = $redirects[$normalized].to_path
                $fix = if ($null -ne $target) { "repoint the holder to '$target'" } else { 'the page was retired; drop the holder' }
                $findings.Add("held-addresses: '$address' is a forwarder, not a page; $fix.")
            } elseif (-not $published.ContainsKey($normalized)) {
                $findings.Add("held-addresses: '$address' (held by $($files[0])) answers no published page.")
            }
        }
    }

    foreach ($record in @($valid | Where-Object { $_.kind -eq 'resolver' })) { Test-ResolverRecord -Record $record -Published $published }
}

function Test-ResolverRecord($Record, $Published) {
    $holder = [string]$Record.holder
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $holder))) {
        $findings.Add("held-addresses: resolver holder '$holder' does not exist.")
        return
    }
    $manifestPath = Join-Path $RepoRoot ([string]$Record.manifest)
    if (-not (Test-Path -LiteralPath $manifestPath)) { Stop-CouldNotVerify "resolver manifest '$($Record.manifest)' is missing." }
    $manifest = @{}
    foreach ($page in (Read-JsonlRecords -Path $manifestPath)) { $manifest[[string]$page.page_id] = $page }

    $facts = Get-ResolverFacts -RepoRoot $RepoRoot -HolderPath $holder
    if ($null -eq $facts.Languages) {
        $findings.Add("held-addresses: $holder declares no TRANSLATED_LANGUAGES set; the gate cannot judge the translations.")
        return
    }
    $declared = (@($facts.Languages) | Sort-Object) -join ','
    $listedLanguages = (@($Record.languages) | Sort-Object) -join ','
    if ($declared -cne $listedLanguages) {
        $findings.Add("held-addresses: $holder translates '$declared' but the list says '$listedLanguages'.")
    }
    foreach ($page in $facts.Pages) {
        $entry = $manifest[$page.PageId]
        $path = "documentation/$($page.Slug).html"
        if ($null -eq $entry) { $findings.Add("held-addresses: resolver page '$($page.PageId)' is not in the page manifest."); continue }
        if ([string]$entry.canonical_path -cne $path) {
            $findings.Add("held-addresses: resolver page '$($page.PageId)' is '$path' but the manifest says '$($entry.canonical_path)'.")
        }
        if (-not $entry.is_published) { $findings.Add("held-addresses: resolver page '$($page.PageId)' is unpublished in the manifest.") }
        if (-not $Published.ContainsKey($path)) { $findings.Add("held-addresses: resolver page '$path' answers no published page.") }
        foreach ($lang in $facts.Languages) {
            $sibling = $path -replace '\.html$', "-$lang.html"
            if (-not $Published.ContainsKey($sibling)) {
                $findings.Add("held-addresses: resolver page '$($page.PageId)' has no published '$lang' sibling '$sibling'.")
            }
        }
    }
}

function Test-LocaleScheme {
    $groupsPath = Join-Path $RepoRoot 'docs/site-address-groups.json'
    if (-not (Test-Path -LiteralPath $groupsPath)) { Stop-CouldNotVerify 'docs/site-address-groups.json is missing.' }
    $declaration = [IO.File]::ReadAllText($groupsPath) | ConvertFrom-Json
    $languagesPath = Join-Path $RepoRoot ([string]$declaration.languages_source)
    if (-not (Test-Path -LiteralPath $languagesPath)) { Stop-CouldNotVerify "language list '$($declaration.languages_source)' is missing." }
    $slugs = [System.Collections.Generic.List[string]]::new()
    foreach ($line in [IO.File]::ReadAllLines($languagesPath)) {
        if ($line -match '^\s*slug:\s*(\S+)\s*$') { $slugs.Add($Matches[1].ToLowerInvariant()) }
    }
    if ($slugs.Count -eq 0) { Stop-CouldNotVerify "no slug found in '$($declaration.languages_source)'." }
    $groups = @($declaration.groups)
    foreach ($group in $groups) {
        if ([string]$group.form -cne '-<lang>') { Stop-CouldNotVerify "group '$($group.id)' declares form '$($group.form)'; only '-<lang>' is supported." }
    }
    $suffix = [regex]::new('(?<sep>[-_.])(?<lang>' + (($slugs | Sort-Object { $_.Length } -Descending | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\.html$', 'IgnoreCase')

    $forwarders = @{}
    foreach ($r in (Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/site-redirects.jsonl'))) {
        if ($r.PSObject.Properties.Name -contains 'from_path') { $forwarders[[string]$r.from_path] = $true }
    }
    foreach ($address in ((Get-PublishedAddresses -RepoRoot $RepoRoot).Keys | Sort-Object)) {
        if ($forwarders.ContainsKey($address)) { continue }
        $m = $suffix.Match($address)
        if (-not $m.Success) { continue }
        $group = $groups | Where-Object { $address -match [string]$_.match } | Select-Object -First 1
        if ($null -eq $group) {
            $findings.Add("locale-scheme: '$address' carries a language suffix but belongs to no group of docs/site-address-groups.json.")
            continue
        }
        $lang = $m.Groups['lang'].Value
        if ($m.Groups['sep'].Value -ne '-' -or $lang -cne $lang.ToLowerInvariant() -or $lang -eq 'en') {
            $name = ($address -replace '^.*/', '') -replace '\.html$', ''
            $stem = $name.Substring(0, $name.Length - $lang.Length - 1)
            $expected = if ($lang -ieq 'en') { "$stem.html (English is the unsuffixed page)" } else { "$stem-$($lang.ToLowerInvariant()).html" }
            $findings.Add("locale-scheme: '$address' (group $($group.id)) spells its language suffix other than '-<lang>'; publish it as $expected and leave a forwarder (scripts/docs/migrate-locale-addresses.ps1).")
        }
    }
}

$all = $Dimension -eq 'all'
if ($all -or $Dimension -eq 'not-found') { Test-NotFoundPage }
if ($all -or $Dimension -eq 'redirects') { Test-Redirects }
if ($all -or $Dimension -eq 'retirement') { Test-Retirement }
if ($all -or $Dimension -eq 'held-addresses') { Test-HeldAddresses }
if ($all -or $Dimension -eq 'locale-scheme') { Test-LocaleScheme }

if ($findings.Count -gt 0) {
    Write-Host "assert-site-addresses: FAILED ($($findings.Count) finding(s))" -ForegroundColor Red
    foreach ($f in $findings | Select-Object -First 20) { Write-Host "  - $f" -ForegroundColor Red }
    if ($findings.Count -gt 20) { Write-Host "  ... and $($findings.Count - 20) more." -ForegroundColor Red }
    exit 1
}
Write-Host "assert-site-addresses: PASS (dimension: $Dimension)" -ForegroundColor Green
exit 0
