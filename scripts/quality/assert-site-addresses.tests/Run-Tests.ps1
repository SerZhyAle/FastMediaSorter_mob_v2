<#
.SYNOPSIS
    S4101 - test suite for the held-addresses and locale-scheme dimensions of scripts/quality/assert-site-addresses.ps1.

.DESCRIPTION
    # Subject: scripts/docs/lib/held-addresses.ps1, scripts/docs/lib/site-addresses.ps1

    Subject: the dimension that compares docs/site-held-addresses.jsonl with the sources that hold the
    site's addresses, and the dimension that holds every language suffix to its group's declared form. Each case builds a small site in a temporary directory (two portal pages with ru/uk
    siblings, one Kotlin holder with a prefix constant, one resolver, a list that describes them), runs the
    gate on it and asserts the exit code and the finding. The negative cases assert that the gate refuses:
    a gate whose refusal was never seen may be green forever.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-site-addresses.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: the gate script is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$gate = Join-Path $RepoRoot 'scripts/quality/assert-site-addresses.ps1'
if (-not (Test-Path -LiteralPath $gate)) { Write-Error "missing: $gate" -ErrorAction Continue; exit 2 }

$script:failures = 0
function Assert-That([bool]$Condition, [string]$Name) {
    if ($Condition) { Write-Host "  ok   $Name" -ForegroundColor Green }
    else { Write-Host "  FAIL $Name" -ForegroundColor Red; $script:failures++ }
}

function Invoke-Gate([string]$Root, [string]$Dimension = 'held-addresses') {
    $output = & pwsh -NoProfile -File $gate -RepoRoot $Root -Dimension $Dimension 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}

function Write-Text([string]$Root, [string]$Relative, [string]$Text) {
    $path = Join-Path $Root $Relative
    $null = New-Item -ItemType Directory -Force (Split-Path -Parent $path)
    [IO.File]::WriteAllText($path, $Text, [Text.UTF8Encoding]::new($false))
}

$base = 'https://serzhyale.github.io/FastMediaSorter_mob_v2'

function New-Site {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("site-held-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Force $root
    Write-Text $root 'index.html' "<html></html>`n"
    Write-Text $root 'documentation/index.html' "<html></html>`n"
    foreach ($suffix in '', '-ru', '-uk') { Write-Text $root "documentation/demo/page$suffix.html" "<html></html>`n" }
    Write-Text $root 'docs/docs-pages-manifest.jsonl' ('{"page_id":"demo.page","canonical_path":"documentation/demo/page.html","title":"P","ticket":"S0001","category":"demo","is_published":true}' + "`n")
    Write-Text $root 'docs/site-redirects.jsonl' ''
    Write-Text $root 'app/Links.kt' ("const val DOCS = `"$base/documentation`"`n" +
        "val home = `"`$DOCS/index.html`"`n" +
        "val page = `"$base/documentation/demo/page.html#intro`"`n" +
        "val sib = `"https://serzhyale.github.io/Other/x.html`"`n")
    Write-Text $root 'app/Resolver.kt' ("val TRANSLATED_LANGUAGES: Set<String> = setOf(`"ru`", `"uk`")`n" +
        "val p = page(`"demo.page`", `"demo/page`")`n")
    Write-List $root
    return $root
}

function Write-List([string]$Root, [string[]]$Extra = @()) {
    $lines = @(
        '{"kind":"holders","files":["app/**/*.kt","README.md"]}',
        '{"kind":"address","address":"documentation/index.html","holders":["app/Links.kt"]}',
        '{"kind":"address","address":"documentation/demo/page.html","holders":["app/Links.kt"]}',
        '{"kind":"sibling","address":"https://serzhyale.github.io/Other/x.html","holders":["app/Links.kt"]}',
        '{"kind":"resolver","holder":"app/Resolver.kt","manifest":"docs/docs-pages-manifest.jsonl","languages":["ru","uk"]}') + $Extra
    Write-Text $Root 'docs/site-held-addresses.jsonl' (($lines -join "`n") + "`n")
}

function New-SchemeSite([string[]]$Pages, [string]$Redirects = '') {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("site-scheme-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Force $root
    Write-Text $root '_data/languages.yml' "- code: en`n  slug: en`n- code: ru`n  slug: ru`n- code: uk`n  slug: uk`n- code: zh-Hans`n  slug: zh-hans`n"
    Write-Text $root 'docs/site-address-groups.json' ('{"languages_source":"_data/languages.yml","groups":[' +
        '{"id":"landing","match":"^(index|nolegal)(-[a-z0-9-]+)?\\.html$","form":"-<lang>"},' +
        '{"id":"portal","match":"^(documentation/|docs/(howto|wear|launcher)/)","form":"-<lang>"},' +
        '{"id":"reference","match":"^docs/","form":"-<lang>"}]}')
    Write-Text $root 'docs/site-redirects.jsonl' $Redirects
    foreach ($page in $Pages) { Write-Text $root $page "<html></html>`n" }
    return $root
}

$sites = [System.Collections.Generic.List[string]]::new()
try {
    Write-Host 'case: clean site (prefix constant expands, fragment stripped)'
    $site = New-Site; $sites.Add($site)
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 0) 'gate passes on a clean synthetic site'

    Write-Host 'case: held address whose page is missing'
    $site = New-Site; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'documentation/demo/page.html')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'documentation/demo/page\.html' \(held by app/Links\.kt\) answers no published page") 'gate refuses a held address no page answers'

    Write-Host 'case: new literal the list lacks'
    $site = New-Site; $sites.Add($site)
    Add-Content -LiteralPath (Join-Path $site 'app/Links.kt') "val more = `"$base/documentation/new.html`""
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'documentation/new\.html' is held by app/Links\.kt but is not in docs/site-held-addresses\.jsonl") 'gate refuses an address the list lacks'

    Write-Host 'case: listed address nobody holds'
    $site = New-Site; $sites.Add($site)
    Write-List $site @('{"kind":"address","address":"documentation/ghost.html","holders":["app/Links.kt"]}')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'documentation/ghost\.html' is listed but no holder carries it") 'gate refuses a stale record'

    Write-Host 'case: holder the record does not name'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'README.md' "[site]($base/documentation/index.html)`n"
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'documentation/index\.html' is carried by README\.md, which the record does not name") 'gate refuses an unnamed holder'

    Write-Host 'case: holder pattern that matches nothing'
    $site = New-Site; $sites.Add($site)
    $list = Join-Path $site 'docs/site-held-addresses.jsonl'
    [IO.File]::WriteAllText($list, ([IO.File]::ReadAllText($list) -replace '"address":"documentation/index.html","holders":\["app/Links.kt"\]', '"address":"documentation/index.html","holders":["app/Links.kt","docs/none.md"]'))
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "holder 'docs/none\.md' of 'documentation/index\.html' matches no file") 'gate refuses a holder pattern that matches no carrier'

    Write-Host 'case: held address that is a forwarder'
    $site = New-Site; $sites.Add($site)
    Add-Content -LiteralPath (Join-Path $site 'docs/site-redirects.jsonl') '{"from_path":"documentation/index.html","to_path":"documentation/demo/page.html","reason":"test","ticket":"S0001","date":"2026-10-06"}'
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'documentation/index\.html' is a forwarder, not a page; repoint the holder to 'documentation/demo/page\.html'") 'gate refuses a held forwarder and names its target'

    Write-Host 'case: resolver page without a translation'
    $site = New-Site; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'documentation/demo/page-uk.html')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "has no published 'uk' sibling 'documentation/demo/page-uk\.html'") 'gate refuses a resolver page whose declared translation is missing'

    Write-Host 'case: resolver language set differs from the list'
    $site = New-Site; $sites.Add($site)
    $list = Join-Path $site 'docs/site-held-addresses.jsonl'
    [IO.File]::WriteAllText($list, ([IO.File]::ReadAllText($list) -replace '"languages":\["ru","uk"\]', '"languages":["ru"]'))
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "translates 'ru,uk' but the list says 'ru'") 'gate refuses a language set that differs from the list'

    Write-Host 'case: resolver page missing from the manifest'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'docs/docs-pages-manifest.jsonl' ''
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "resolver page 'demo\.page' is not in the page manifest") 'gate refuses a resolver page the manifest lacks'

    Write-Host 'case: malformed record and duplicate'
    $site = New-Site; $sites.Add($site)
    Write-List $site @('{"kind":"address","address":"https://x.example/a","holders":["app/Links.kt"]}', '{"kind":"address","address":"documentation/index.html","holders":["app/Links.kt"]}')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'must be site-relative' -and $r.Output -match "'documentation/index\.html' is listed twice") 'gate refuses a scheme in an address and a duplicate record'

    Write-Host 'case: locale-scheme - hyphen form passes'
    $site = New-SchemeSite @('index.html', 'index-zh-hans.html', 'docs/FAQ.html', 'docs/FAQ-ru.html', 'documentation/a/b-uk.html', 'docs/howto/index-ru.html', 'styles.css'); $sites.Add($site)
    $r = Invoke-Gate $site 'locale-scheme'
    Assert-That ($r.Exit -eq 0) 'locale-scheme passes on hyphen-form siblings'

    Write-Host 'case: locale-scheme - legacy forms'
    $site = New-SchemeSite @('docs/FAQ_RU.html', 'docs/PRIVACY.uk.html', 'docs/DOWNLOADS_EN.html', 'docs/OK-ru.html'); $sites.Add($site)
    $r = Invoke-Gate $site 'locale-scheme'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'docs/FAQ_RU\.html' \(group reference\).*publish it as FAQ-ru\.html") 'locale-scheme refuses _RU'
    Assert-That ($r.Output -match "'docs/PRIVACY\.uk\.html'.*publish it as PRIVACY-uk\.html") 'locale-scheme refuses .uk'
    Assert-That ($r.Output -match "'docs/DOWNLOADS_EN\.html'.*DOWNLOADS\.html \(English is the unsuffixed page\)") 'locale-scheme refuses an English suffix'
    Assert-That ($r.Output -notmatch 'OK-ru') 'locale-scheme leaves the hyphen form alone'

    Write-Host 'case: locale-scheme - forwarder at the old address is exempt'
    $site = New-SchemeSite @('docs/FAQ_RU.html', 'docs/FAQ-ru.html') '{"from_path":"docs/FAQ_RU.html","to_path":"docs/FAQ-ru.html","reason":"t","ticket":"S0001","date":"2026-10-06"}'; $sites.Add($site)
    $r = Invoke-Gate $site 'locale-scheme'
    Assert-That ($r.Exit -eq 0) 'locale-scheme skips a listed forwarder'

    Write-Host 'case: locale-scheme - suffixed address in no group'
    $site = New-SchemeSite @('assets/page-ru.html'); $sites.Add($site)
    $r = Invoke-Gate $site 'locale-scheme'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "'assets/page-ru\.html' carries a language suffix but belongs to no group") 'locale-scheme refuses a suffixed address outside every group'

    Write-Host 'case: locale-scheme - missing declaration'
    $site = New-SchemeSite @('docs/FAQ.html'); $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'docs/site-address-groups.json')
    $r = Invoke-Gate $site 'locale-scheme'
    Assert-That ($r.Exit -eq 2 -and $r.Output -match 'COULD NOT VERIFY') 'locale-scheme exits 2 and says COULD NOT VERIFY when the declaration is missing'

    Write-Host 'case: the list is missing'
    $site = New-Site; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'docs/site-held-addresses.jsonl')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 2 -and $r.Output -match 'COULD NOT VERIFY') 'gate exits 2 and says COULD NOT VERIFY when the list is missing'
} finally {
    foreach ($s in $sites) { Remove-Item -LiteralPath $s -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($script:failures -gt 0) { Write-Host "assert-site-addresses tests: FAIL ($script:failures)" -ForegroundColor Red; exit 1 }
Write-Host 'assert-site-addresses tests: PASS' -ForegroundColor Green
exit 0
