<#
.SYNOPSIS
    S4256 - test suite for scripts/quality/assert-released-addresses.ps1 and its writer
    scripts/docs/record-released-addresses.ps1.

.DESCRIPTION
    # Subject: scripts/docs/lib/released-addresses.ps1, scripts/docs/record-released-addresses.ps1

    Each case builds a small site in a temporary directory (a Markdown page with a permalink, an HTML page,
    a contributor file the sitemap does not announce, and the sitemap), records it as a release with the
    writer, changes the tree the way a later ticket would, and runs the gate. The negative cases assert that
    the gate refuses: a gate whose refusal was never seen may be green forever.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-released-addresses.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: the gate or the writer is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$gate = Join-Path $RepoRoot 'scripts/quality/assert-released-addresses.ps1'
$writer = Join-Path $RepoRoot 'scripts/docs/record-released-addresses.ps1'
foreach ($p in $gate, $writer) { if (-not (Test-Path -LiteralPath $p)) { Write-Error "missing: $p" -ErrorAction Continue; exit 2 } }

$script:failures = 0
function Assert-That([bool]$Condition, [string]$Name) {
    if ($Condition) { Write-Host "  ok   $Name" -ForegroundColor Green }
    else { Write-Host "  FAIL $Name" -ForegroundColor Red; $script:failures++ }
}

function Invoke-Gate([string]$Root) {
    $output = & pwsh -NoProfile -File $gate -RepoRoot $Root 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}

function Invoke-Writer([string]$Root, [string]$Release, [switch]$Check) {
    $argv = @('-NoProfile', '-File', $writer, '-Release', $Release, '-SiteRoot', $Root, '-RecordRoot', $Root)
    if ($Check) { $argv += '-Check' }
    $output = & pwsh @argv 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}

function Write-Text([string]$Root, [string]$Relative, [string]$Text) {
    $path = Join-Path $Root $Relative
    $null = New-Item -ItemType Directory -Force (Split-Path -Parent $path)
    [IO.File]::WriteAllText($path, $Text, [Text.UTF8Encoding]::new($false))
}

function Read-Record([string]$Root) {
    $map = @{}
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $Root 'docs/site-released-addresses.jsonl'))) {
        if ($line) { $o = $line | ConvertFrom-Json; $map[[string]$o.address] = $o }
    }
    return $map
}

$base = 'https://serzhyale.github.io/FastMediaSorter_mob_v2'
$page = "---`nlayout: doc`npermalink: /docs/portal/home.html`n---`n# Home network`n`n## Who shares`n`ntext`n`n## Step by step`n`n## Step by step`n`n## Кто открывает`n`n``````text`n## not a heading`n```````n"
$html = "<html><body><main id=`"main-content`"><button id=`"themeBtn`">t</button><h2 id=`"why`">Why</h2><div id=`"dropbox`">x</div><a name=`"legacy-spot`"></a></main></body></html>`n"

function New-Site {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("site-released-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Force $root
    Write-Text $root 'docs/portal/home.md' $page
    Write-Text $root 'documentation/demo/page.html' $html
    Write-Text $root 'docs/CONTRIBUTING_NOTES.md' "# Notes`n`n## Free to change`n"
    Write-Text $root 'docs/site-redirects.jsonl' ''
    Write-Text $root 'sitemap.xml' ("<urlset>`n<url><loc>$base/docs/portal/home.html</loc></url>`n<url><loc>$base/documentation/demo/page.html</loc></url>`n</urlset>`n")
    $r = Invoke-Writer $root '1.0'
    if ($r.Exit -ne 0) { throw "writer failed on a fresh site: $($r.Output)" }
    return $root
}

$sites = [System.Collections.Generic.List[string]]::new()
try {
    Write-Host 'case: a fresh record of an unchanged tree'
    $site = New-Site; $sites.Add($site)
    $record = Read-Record $site
    Assert-That ($record.Count -eq 2) 'the writer records exactly the two pages the sitemap announces'
    Assert-That (-not $record.ContainsKey('docs/CONTRIBUTING_NOTES.html')) 'a contributor file the sitemap does not announce is not recorded'
    $homeAnchors = @($record['docs/portal/home.html'].anchors)
    Assert-That (($homeAnchors -join ',') -ceq 'home-network,step-by-step,step-by-step-1,who-shares,кто-открывает') 'Markdown anchors are the GFM heading ids, a repeat suffixed -1, a Cyrillic heading kept, a fenced line skipped'
    $pageAnchors = @($record['documentation/demo/page.html'].anchors)
    Assert-That (($pageAnchors -join ',') -ceq 'dropbox,legacy-spot,main-content,why') 'HTML anchors are lower-case ids and an <a> name, a camel-case button id left out'
    Assert-That ((Invoke-Gate $site).Exit -eq 0) 'the gate passes an unchanged tree'
    Assert-That ((Invoke-Writer $site '1.0' -Check).Exit -eq 0) 'a second writer run is a no-op'

    Write-Host 'case: a reworded released heading'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'docs/portal/home.md' ($page.Replace('## Who shares', '## Who shares folders'))
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'docs/portal/home\.html \(released in 1\.0\) lost anchor\(s\) #who-shares') 'the gate refuses a heading whose id changed'
    Write-Text $site 'docs/portal/home.md' ($page.Replace('## Who shares', '## Who shares folders {#who-shares}'))
    Assert-That ((Invoke-Gate $site).Exit -eq 0) 'the old id kept as a kramdown attribute passes'

    Write-Host 'case: a removed HTML anchor and a free contributor file'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'documentation/demo/page.html' ($html.Replace('<div id="dropbox">', '<div>'))
    Write-Text $site 'docs/CONTRIBUTING_NOTES.md' "# Renamed notes`n"
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'lost anchor\(s\) #dropbox' -and $r.Output -notmatch 'CONTRIBUTING') 'the gate refuses a removed id and leaves a contributor file alone'

    Write-Host 'case: a released page that stops answering'
    $site = New-Site; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'documentation/demo/page.html')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'documentation/demo/page\.html \(released in 1\.0\) no longer answers with a page') 'the gate refuses a deleted released page'

    Write-Host 'case: a released page moved behind a forwarder'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'documentation/demo/page.html' "<html><head><meta name=`"fms-forwarder`" content=`"moved`"></head></html>`n"
    Write-Text $site 'documentation/demo/new.html' $html
    Write-Text $site 'docs/site-redirects.jsonl' ('{"from_path":"documentation/demo/page.html","to_path":"documentation/demo/new.html","reason":"renamed","ticket":"S0002","date":"2026-10-15"}' + "`n")
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'was moved to documentation/demo/new\.html by S0002') 'the gate refuses a move of a released page'

    Write-Host 'case: a released page retired with its capability'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'documentation/demo/page.html' "<html><head><meta name=`"fms-forwarder`" content=`"retired`"></head></html>`n"
    Write-Text $site 'docs/site-redirects.jsonl' ('{"from_path":"documentation/demo/page.html","to_path":null,"reason":"capability removed","ticket":"S0003","date":"2026-10-15"}' + "`n")
    Assert-That ((Invoke-Gate $site).Exit -eq 0) 'a retirement forwarder passes'
    $r = Invoke-Writer $site '2.0'
    Assert-That ($r.Exit -eq 0 -and (Read-Record $site).ContainsKey('documentation/demo/page.html')) 'the writer keeps the line of a page that left the site'

    Write-Host 'case: a new page and a new anchor at the next release'
    $site = New-Site; $sites.Add($site)
    Write-Text $site 'docs/portal/clouds.md' "---`npermalink: /docs/portal/clouds.html`n---`n# Clouds`n"
    Write-Text $site 'docs/portal/home.md' ($page + "`n## Added later`n")
    Write-Text $site 'sitemap.xml' ("<urlset>`n<url><loc>$base/docs/portal/home.html</loc></url>`n<url><loc>$base/docs/portal/clouds.html</loc></url>`n<url><loc>$base/documentation/demo/page.html</loc></url>`n</urlset>`n")
    Assert-That ((Invoke-Gate $site).Exit -eq 0) 'a page no release carried yet is free'
    Assert-That ((Invoke-Writer $site '2.0' -Check).Exit -eq 1) '-Check reports that the record would grow'
    $null = Invoke-Writer $site '2.0'
    $record = Read-Record $site
    Assert-That ($record['docs/portal/clouds.html'].release -eq '2.0' -and $record['docs/portal/home.html'].release -eq '1.0') 'a new page takes the new release, an old one keeps its first'
    Assert-That (@($record['docs/portal/home.html'].anchors) -ccontains 'added-later') 'a new anchor of an old page is added'

    Write-Host 'case: the record is missing'
    $site = New-Site; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'docs/site-released-addresses.jsonl')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 2 -and $r.Output -match 'COULD NOT VERIFY') 'the gate exits 2 when the record is missing'

    Write-Host 'case: the writer on a tree without a sitemap'
    $site = New-Site; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'sitemap.xml')
    Assert-That ((Invoke-Writer $site '2.0').Exit -eq 2) 'the writer exits 2 when the tree has no sitemap'
} finally {
    foreach ($s in $sites) { Remove-Item -LiteralPath $s -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($script:failures -gt 0) { Write-Host "assert-released-addresses tests: FAIL ($script:failures)" -ForegroundColor Red; exit 1 }
Write-Host 'assert-released-addresses tests: PASS' -ForegroundColor Green
exit 0
