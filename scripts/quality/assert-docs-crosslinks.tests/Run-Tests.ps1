# Run-Tests.ps1 (S3540) - regression suite for assert-docs-crosslinks.ps1.
#
# The live tree shows the gate one state only, so every case builds a synthetic Jekyll source
# under temp/scratch (a _config.yml, a page manifest, pages with and without permalinks), runs the
# gate against it with -RepoRoot and -BaselinePath, and removes the tree in a finally block.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-docs-crosslinks.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-docs-crosslinks.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Write-TreeFile([string]$root, [string]$rel, [string]$text) {
    $full = Join-Path $root $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $full) | Out-Null
    [System.IO.File]::WriteAllText($full, $text)
}

function New-Tree([hashtable]$pages, [string[]]$baseline) {
    $root = Join-Path $repoRoot ("temp/scratch/crosslinks-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    Write-TreeFile $root '_config.yml' "baseurl: `"/FastMediaSorter_mob_v2`"`nexclude:`n  - PLAN/`n"
    Write-TreeFile $root 'docs/docs-pages-manifest.jsonl' '{"page_id":"planned.page","canonical_path":"documentation/planned.html","ticket":"S0001","title":"Planned"}'
    Write-TreeFile $root 'docs/PRIVACY_POLICY.md' "---`npermalink: /docs/PRIVACY_POLICY.html`n---`n# Privacy`n"
    Write-TreeFile $root 'docs/PRIVACY_POLICY_RU.md' "---`npermalink: /docs/PRIVACY_POLICY.ru.html`n---`n# Privacy`n"
    Write-TreeFile $root 'PLAN/excluded.html' '<p>not published</p>'
    foreach ($k in $pages.Keys) { Write-TreeFile $root $k $pages[$k] }
    Write-TreeFile $root 'baseline.txt' ("# test baseline`n" + ($baseline -join "`n"))
    return $root
}

function Invoke-Gate([string]$root, [bool]$fragments = $false) {
    [string[]]$extra = if ($fragments) { @('-CheckFragments') } else { @() }
    $out = & $pwshExe -NoProfile -File $gateScript -RepoRoot $root -BaselinePath (Join-Path $root 'baseline.txt') @extra 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $out }
}

function Test-Case([string]$name, [hashtable]$pages, [string[]]$baseline, [int]$expectedExit, [string]$expectedText, [bool]$fragments = $false) {
    $root = New-Tree $pages $baseline
    try {
        $r = Invoke-Gate $root $fragments
        $ok = $r.Exit -eq $expectedExit -and (-not $expectedText -or $r.Output.Contains($expectedText))
        Assert-That $name $ok "expected exit $expectedExit '$expectedText', got exit $($r.Exit):`n$($r.Output)"
    }
    finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
}

function New-Page([string]$permalink, [string]$body) {
    $fm = if ($permalink) { "---`npermalink: $permalink`nlayout: null`n---`n" } else { '' }
    return "$fm<html><body>$body</body></html>"
}

Write-Host "=== assert-docs-crosslinks regression suite ===" -ForegroundColor Cyan

$section = New-Page '/documentation/sec/page.html' '<a href="../index.html">home</a> <a href="../../docs/PRIVACY_POLICY.html">p</a> <a href="https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.ru.html">ru</a> <a href="https://example.com/x">ext</a> <a href="#top">top</a>'
$index = New-Page '/documentation/' '<a href="sec/page.html">page</a> <img src="img/a.png">'
$clean = @{ 'documentation/index.html' = $index; 'documentation/sec/page.html' = $section; 'documentation/img/a.png' = 'png' }

Test-Case 'clean tree passes' $clean @() 0 'PASS'

$physical = $clean.Clone()
$physical['documentation/sec/other.html'] = New-Page '/documentation/sec/other.html' '<a href="../../docs/PRIVACY_POLICY_RU.html">physical path of a permalink page</a>'
Test-Case 'a permalink page is not reachable by its physical path' $physical @() 1 'docs/PRIVACY_POLICY_RU.html'

$relative = $clean.Clone()
$relative['documentation/sec/other.html'] = New-Page '/documentation/sec/other.html' '<a href="docs/PRIVACY_POLICY.html">resolves under sec/</a>'
Test-Case 'a relative link resolves from the page address' $relative @() 1 'documentation/sec/docs/PRIVACY_POLICY.html'

$moved = $clean.Clone()
$moved['documentation/sec/other.html'] = New-Page '/documentation/elsewhere/other.html' '<a href="../sec/page.html">page</a>'
Test-Case 'a relative link resolves from the permalink, not the file directory' $moved @() 0 'PASS'

$absolute = $clean.Clone()
$absolute['documentation/sec/other.html'] = New-Page '/documentation/sec/other.html' '<a href="/FastMediaSorter_mob_v2/docs/missing.html">m</a>'
Test-Case 'a root-relative site link is checked' $absolute @() 1 'docs/missing.html'

$excluded = $clean.Clone()
$excluded['documentation/sec/other.html'] = New-Page '/documentation/sec/other.html' '<a href="../../PLAN/excluded.html">x</a>'
Test-Case 'a file under an excluded path is not a site address' $excluded @() 1 'PLAN/excluded.html'

$bookmark = $clean.Clone()
$bookmark['documentation/sec/other.html'] = New-Page '/documentation/sec/other.html' '<a href="../planned.html">planned</a>'
Test-Case 'a manifest page is a non-fatal bookmark' $bookmark @() 0 'Bookmark: planned.page'

$broken = $clean.Clone()
$broken['documentation/sec/other.html'] = New-Page '/documentation/sec/other.html' '<a href="../gone.html">gone</a>'
Test-Case 'a new broken target fails' $broken @() 1 "'documentation/gone.html' is not a site address"
Test-Case 'a baselined broken target passes' $broken @('documentation/gone.html') 0 'baselined 1'
Test-Case 'a stale baseline row fails' $clean @('documentation/gone.html') 1 'no longer broken'

$landing = $clean.Clone()
$landing['index-xx.html'] = New-Page $null '<a href="documentation/sec/page.html">ok</a> <a href="documentation/sec/missing.html">dead</a>'
Test-Case 'a root landing page href to a missing page fails' $landing @() 1 "'documentation/sec/missing.html' is not a site address"

$landingOk = $clean.Clone()
$landingOk['index.html'] = New-Page $null '<a href="documentation/sec/page.html">ok</a> <a href="documentation/">docs</a>'
Test-Case 'a root landing page with live hrefs passes' $landingOk @() 0 'PASS'

$fragmentOk = @{ 'documentation/index.html' = New-Page '/documentation/' '<h2 id="one">One</h2><a href="#one">one</a>' }
Test-Case 'an existing same-page fragment passes' $fragmentOk @() 0 'PASS' $true
$fragmentBad = @{ 'documentation/index.html' = New-Page '/documentation/' '<a href="#missing">missing</a>' }
Test-Case 'a missing same-page fragment fails' $fragmentBad @() 1 "missing fragment 'missing'" $true
$fragmentCross = @{
    'documentation/index.html' = New-Page '/documentation/' '<a href="topic.html#two">two</a>'
    'documentation/topic.html' = New-Page '/documentation/topic.html' '<h2 id="two">Two</h2>'
}
Test-Case 'an existing cross-page fragment passes' $fragmentCross @() 0 'PASS' $true
$fragmentCross['documentation/topic.html'] = New-Page '/documentation/topic.html' '<h2 id="other">Other</h2>'
Test-Case 'a missing cross-page fragment fails' $fragmentCross @() 1 "missing fragment 'two'" $true

Write-Host ""
Write-Host "Summary: $script:pass passed, $script:fail failed" -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
