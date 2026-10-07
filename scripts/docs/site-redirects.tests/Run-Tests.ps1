<#
.SYNOPSIS
    S4097 - test suite for the forwarder generator, the retire command and the site-address gate.

.DESCRIPTION
    Subject: scripts/docs/generate-site-redirects.ps1, scripts/docs/retire-docs-page.ps1,
    scripts/docs/lib/site-addresses.ps1, scripts/quality/assert-site-addresses.ps1

    The mechanism has no live record in the repository, so only a synthetic site proves it works. Each case
    builds a small site in a temporary directory (two pages with en/ru/uk copies, a registry line, the real
    404.html), runs the tool on it and asserts the exit code and the effect. The negative cases assert that
    the gate refuses: a gate whose refusal was never seen may be green forever.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/site-redirects.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: a script under test or the real 404.html is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$generator = Join-Path $RepoRoot 'scripts/docs/generate-site-redirects.ps1'
$retire = Join-Path $RepoRoot 'scripts/docs/retire-docs-page.ps1'
$gate = Join-Path $RepoRoot 'scripts/quality/assert-site-addresses.ps1'
$notFound = Join-Path $RepoRoot '404.html'
foreach ($required in @($generator, $retire, $gate, $notFound)) {
    if (-not (Test-Path -LiteralPath $required)) { Write-Error "missing: $required" -ErrorAction Continue; exit 2 }
}

$script:failures = 0
function Assert-That([bool]$Condition, [string]$Name) {
    if ($Condition) { Write-Host "  ok   $Name" -ForegroundColor Green }
    else { Write-Host "  FAIL $Name" -ForegroundColor Red; $script:failures++ }
}

function Invoke-Tool([string]$Script, [string[]]$Arguments) {
    $output = & pwsh -NoProfile -File $Script @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}

function Write-Text([string]$Root, [string]$Relative, [string]$Text) {
    $path = Join-Path $Root $Relative
    $null = New-Item -ItemType Directory -Force (Split-Path -Parent $path)
    [IO.File]::WriteAllText($path, $Text, [Text.UTF8Encoding]::new($false))
}

function New-Site {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("site-redirects-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Force $root
    Copy-Item -LiteralPath $notFound -Destination (Join-Path $root '404.html')
    foreach ($lang in @(@('recipes', ''), @('recipes-ru', '-ru'), @('recipes-uk', '-uk'))) {
        foreach ($slug in 'old-feature', 'new-feature') {
            $meta = "---`npage_id: demo.$slug`ncanonical_url: documentation/demo/$slug$($lang[1]).html`n---`nBody`n"
            Write-Text $root "docs/content/$($lang[0])/demo-$slug.md" $meta
            Write-Text $root "documentation/demo/$slug$($lang[1]).html" "<html><body>$slug</body></html>`n"
        }
    }
    $pages = @(
        '{"page_id":"demo.old-feature","canonical_path":"documentation/demo/old-feature.html","title":"Old","ticket":"S0001","category":"demo","is_published":true}',
        '{"page_id":"demo.new-feature","canonical_path":"documentation/demo/new-feature.html","title":"New","ticket":"S0001","category":"demo","is_published":true}')
    Write-Text $root 'docs/docs-pages-manifest.jsonl' (($pages -join "`n") + "`n")
    Write-Text $root 'docs/coverage-manifest.jsonl' ('{"feature_id":"demo.a","status":"active","page_id":"demo.old-feature"}' + "`n" + '{"feature_id":"demo.b","status":"active","page_id":"demo.new-feature"}' + "`n")
    $registry = '{"id":"docs-corpus-pages","paths":["docs/content/recipes/demo-old-feature.md","docs/content/recipes/demo-new-feature.md","documentation/*/*.html"],"sitemap_exclude":[]}'
    Write-Text $root 'docs/DOCUMENT_REGISTRY.jsonl' ($registry + "`n")
    Write-Text $root 'docs/site-redirects.jsonl' ''
    Write-Text $root 'docs/site-held-addresses.jsonl' ('{"kind":"holders","files":["app/**/*.kt"]}' + "`n")
    Write-Text $root '_data/languages.yml' "- code: en`n  slug: en`n- code: ru`n  slug: ru`n- code: uk`n  slug: uk`n"
    Write-Text $root 'docs/site-address-groups.json' '{"languages_source":"_data/languages.yml","groups":[{"id":"portal","match":"^documentation/","form":"-<lang>"}]}'
    Write-Text $root 'sitemap.xml' "<urlset></urlset>`n"
    return $root
}

function Add-Record([string]$Root, [string]$From, $To, [string]$Reason = 'Test move of a demo page') {
    $toJson = if ($null -eq $To) { 'null' } else { '"' + $To + '"' }
    $line = '{"from_path":"' + $From + '","to_path":' + $toJson + ',"reason":"' + $Reason + '","ticket":"S4097","date":"2026-10-06"}'
    $path = Join-Path $Root 'docs/site-redirects.jsonl'
    [IO.File]::AppendAllText($path, $line + "`n", [Text.UTF8Encoding]::new($false))
}

function Invoke-Generator([string]$Root, [switch]$Check) {
    $arguments = @('-ManifestPath', (Join-Path $Root 'docs/site-redirects.jsonl'), '-SiteRoot', $Root)
    if ($Check) { $arguments += '-Check' }
    return Invoke-Tool $generator $arguments
}

function Invoke-Gate([string]$Root, [string]$Dimension = 'all') {
    return Invoke-Tool $gate @('-RepoRoot', $Root, '-Dimension', $Dimension)
}

$sites = [System.Collections.Generic.List[string]]::new()
try {
    Write-Host 'case: clean site'
    $site = New-Site; $sites.Add($site)
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 0) 'gate passes on a clean synthetic site'

    Write-Host 'case: moved forwarder'
    Add-Record $site 'documentation/demo/gone.html' 'documentation/demo/new-feature.html'
    $r = Invoke-Generator $site
    Assert-That ($r.Exit -eq 0) 'generator writes the forwarder'
    $html = Get-Content -LiteralPath (Join-Path $site 'documentation/demo/gone.html') -Raw
    Assert-That ($html -match 'rel="canonical" href="https://[^"]+/documentation/demo/new-feature\.html"') 'forwarder names the target in a canonical reference'
    Assert-That ($html -match 'http-equiv="refresh" content="0; url=new-feature\.html"') 'forwarder refreshes to the relative target'
    Assert-That ($html -match '<a href="new-feature\.html">') 'forwarder links the target'
    Assert-That ($html -match 'name="fms-forwarder" content="moved"' -and $html -match 'noindex') 'forwarder is marked and noindex'
    $registryText = Get-Content -LiteralPath (Join-Path $site 'docs/DOCUMENT_REGISTRY.jsonl') -Raw
    Assert-That ($registryText -match '"path":"documentation/demo/gone\.html"') 'registry sitemap_exclude row is derived from the manifest'
    Assert-That ((Invoke-Generator $site -Check).Exit -eq 0) '-Check passes on current forwarders'
    Assert-That ((Invoke-Gate $site).Exit -eq 0) 'gate passes with a current forwarder'

    Write-Host 'case: retired forwarder'
    Add-Record $site 'documentation/demo/removed.html' $null
    [void](Invoke-Generator $site)
    $html = Get-Content -LiteralPath (Join-Path $site 'documentation/demo/removed.html') -Raw
    Assert-That ($html -match 'content="retired"' -and $html -notmatch 'http-equiv="refresh"') 'retired page has no refresh'
    Assert-That ($html -match 'href="\.\./index\.html"' -and $html -match 'href="\.\./\.\./index\.html"') 'retired page links the documentation home and the landing page'

    Write-Host 'case: stale and malformed'
    Add-Content -LiteralPath (Join-Path $site 'documentation/demo/gone.html') -Value '<!-- edited -->'
    Assert-That ((Invoke-Generator $site -Check).Exit -eq 1) '-Check exits 1 after a forwarder was edited'
    Assert-That ((Invoke-Gate $site 'redirects').Exit -eq 1) 'gate refuses an outdated forwarder'
    [void](Invoke-Generator $site)
    $bad = New-Site; $sites.Add($bad)
    Add-Record $bad '../escape.html' 'documentation/demo/new-feature.html'
    Assert-That ((Invoke-Generator $bad).Exit -eq 2) 'generator exits 2 for a path with ..'

    Write-Host 'case: gate refusals'
    $chain = New-Site; $sites.Add($chain)
    Add-Record $chain 'documentation/demo/a.html' 'documentation/demo/b.html'
    Add-Record $chain 'documentation/demo/b.html' 'documentation/demo/new-feature.html'
    [void](Invoke-Generator $chain)
    $r = Invoke-Gate $chain 'redirects'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'itself a forwarder') 'gate refuses a chain'
    $missing = New-Site; $sites.Add($missing)
    Add-Record $missing 'documentation/demo/a.html' 'documentation/demo/nowhere.html'
    [void](Invoke-Generator $missing)
    $r = Invoke-Gate $missing 'redirects'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'no page answers') 'gate refuses a missing target'
    $live = New-Site; $sites.Add($live)
    Add-Record $live 'documentation/demo/old-feature.html' 'documentation/demo/new-feature.html'
    $r = Invoke-Generator $live
    $r = Invoke-Gate $live 'redirects'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'still a live page') 'gate refuses a forwarder over a live page'
    $rel = New-Site; $sites.Add($rel)
    $text = (Get-Content -LiteralPath (Join-Path $rel '404.html') -Raw) -replace "href=`"\{\{ '/documentation/' \| relative_url \}\}`"", 'href="documentation/index.html"'
    [IO.File]::WriteAllText((Join-Path $rel '404.html'), $text)
    $r = Invoke-Gate $rel 'not-found'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'not absolute or relative_url-built') 'gate refuses a relative href in 404.html'
    $stray = New-Site; $sites.Add($stray)
    Write-Text $stray 'documentation/demo/stray.html' "<html><body>stray</body></html>`n"
    $r = Invoke-Gate $stray 'retirement'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'no recipe output, declared hub or forwarder') 'gate refuses an orphan page'
    $removed = New-Site; $sites.Add($removed)
    Write-Text $removed 'docs/coverage-manifest.jsonl' ('{"feature_id":"demo.a","status":"removed","page_id":"demo.old-feature"}' + "`n")
    $r = Invoke-Gate $removed 'retirement'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'documents only removed capabilities') 'gate refuses a published page whose coverage rows are all removed'

    Write-Host 'case: retire command'
    $ret = New-Site; $sites.Add($ret)
    $r = Invoke-Tool $retire @('-RepoRoot', $ret, '-PageId', 'demo.old-feature', '-To', 'demo.new-feature', '-Ticket', 'S4097', '-SkipRebuild', '-WhatIf')
    Assert-That ($r.Exit -eq 0 -and (Test-Path (Join-Path $ret 'docs/content/recipes/demo-old-feature.md'))) '-WhatIf prints a plan and deletes nothing'
    $r = Invoke-Tool $retire @('-RepoRoot', $ret, '-PageId', 'demo.old-feature', '-To', 'demo.new-feature', '-Ticket', 'S4097', '-SkipRebuild')
    Assert-That ($r.Exit -eq 0) 'retire exits 0'
    Assert-That (-not (Test-Path (Join-Path $ret 'docs/content/recipes-ru/demo-old-feature.md'))) 'retire deletes the ru source'
    Assert-That (-not (Test-Path (Join-Path $ret 'documentation/demo/old-feature-uk.html'))) 'retire deletes the uk page'
    $manifest = Get-Content -LiteralPath (Join-Path $ret 'docs/docs-pages-manifest.jsonl') -Raw
    Assert-That ($manifest -match '"page_id":"demo.old-feature".*"is_published":false' -and $manifest -match '"page_id":"demo.new-feature".*"is_published":true') 'retire unpublishes only the named page'
    $records = Get-Content -LiteralPath (Join-Path $ret 'docs/site-redirects.jsonl')
    Assert-That ($records.Count -eq 3 -and ($records -match 'old-feature-ru\.html.*new-feature-ru\.html').Count -eq 1) 'retire writes one record per copy, each to the same-language successor'
    Assert-That ((Get-Content -LiteralPath (Join-Path $ret 'docs/DOCUMENT_REGISTRY.jsonl') -Raw) -notmatch 'demo-old-feature\.md') 'retire removes the source from the registry paths'
    [void](Invoke-Generator $ret)
    Assert-That ((Invoke-Gate $ret).Exit -eq 0) 'gate passes after retire and the generator'
    $again = Invoke-Tool $retire @('-RepoRoot', $ret, '-PageId', 'demo.old-feature', '-Ticket', 'S4097', '-SkipRebuild')
    Assert-That ($again.Exit -eq 2) 'retiring a retired page exits 2'
}
finally {
    foreach ($s in $sites) { if (Test-Path -LiteralPath $s) { Remove-Item -LiteralPath $s -Recurse -Force } }
}

if ($script:failures -gt 0) { Write-Host "site-redirects tests: $script:failures failure(s)" -ForegroundColor Red; exit 1 }
Write-Host 'site-redirects tests: PASS' -ForegroundColor Green
exit 0
