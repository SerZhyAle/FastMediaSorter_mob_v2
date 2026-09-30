#requires -Version 7.0
<#
.SYNOPSIS
    S2972 gate: every external link of the user documentation corpus still answers.

.DESCRIPTION
    Collects the external http(s) links written in the Markdown recipes under docs/content/recipes/
    and the <a href> anchors of the generated pages under documentation/, requests each distinct
    address once (HEAD, then GET when the server refuses HEAD) and fails on an address that is gone:
    404, 410, or a host that does not resolve. A dead link must be found by this check, not by a
    reader.

    Answers that do not prove a page is gone - 401/403/429, a 5xx, a timeout - are reported as
    warnings and do not fail the gate: storefronts and help centres routinely refuse scripted
    clients, and a transient outage is not a documentation defect.

    Addresses on the project's own site are skipped: they are judged against the page manifest by
    assert-docs-crosslinks.ps1, and a page added by the same change answers 404 until the site is
    published. Placeholders written as examples (user:password@host, example.com) are skipped too.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape.

.PARAMETER Quiet
    Print only dead links, warnings and the verdict line.

.PARAMETER TimeoutSec
    Per-request timeout.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-docs-external-links.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. Its subject is the outside internet, which changes on
    no ticket's clock; a dead link reaches a reader only when the site is published.

    Exit codes:
      0  every external link answers, or answers with a non-fatal warning
      1  at least one external link is dead (404/410 or unresolvable host)
      2  cannot verify: no link could be requested at all (no network), or the corpus is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Gate,
    [switch] $Quiet,
    [int] $TimeoutSec = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'docs-external-links'; files = 'docs/content/recipes/*.md,documentation/**/*.html' })

$recipeRoot = Join-Path $RepoRoot 'docs/content/recipes'
$pageRoot = Join-Path $RepoRoot 'documentation'
if (-not (Test-Path -LiteralPath $recipeRoot) -or -not (Test-Path -LiteralPath $pageRoot)) {
    Write-Host 'assert-docs-external-links: FAIL (cannot verify) - docs/content/recipes/ or documentation/ is missing' -ForegroundColor Red
    exit 2
}

$siteBase = ([string]((Get-Content (Join-Path $RepoRoot '.sza-profile.json') -Raw | ConvertFrom-Json).site.baseUrl)).TrimEnd('/')
$placeholder = '(?i)(user:password@|://host[/:]|example\.(com|org|net))'

# url -> list of repo-relative files that carry it
$sources = [ordered]@{}
function Add-Link([string]$url, [string]$file) {
    $clean = $url.TrimEnd('.', ',', ';', ':', '`', '*')
    if ($clean -match $placeholder) { return }
    if ($clean.StartsWith($siteBase, [System.StringComparison]::OrdinalIgnoreCase)) { return }
    if (-not $sources.Contains($clean)) { $sources[$clean] = [System.Collections.Generic.List[string]]::new() }
    if (-not $sources[$clean].Contains($file)) { $sources[$clean].Add($file) }
}

function Get-RelativePath([string]$full) {
    return $full.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
}

foreach ($md in Get-ChildItem -LiteralPath $recipeRoot -Filter *.md -File) {
    $text = Get-Content -LiteralPath $md.FullName -Raw -Encoding utf8
    foreach ($m in [regex]::Matches($text, 'https?://[^\s\)\]"''<>]+')) { Add-Link $m.Value (Get-RelativePath $md.FullName) }
}
foreach ($page in Get-ChildItem -LiteralPath $pageRoot -Recurse -Filter *.html -File) {
    $text = Get-Content -LiteralPath $page.FullName -Raw -Encoding utf8
    foreach ($m in [regex]::Matches($text, '<a\s[^>]*href="(https?://[^"]+)"')) {
        Add-Link ([System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value)) (Get-RelativePath $page.FullName)
    }
}

if (-not $Quiet) { Write-Host "Checking $($sources.Count) distinct external link(s).." }

$results = $sources.Keys | ForEach-Object -ThrottleLimit 8 -Parallel {
    $url = $_
    $timeout = $using:TimeoutSec
    $headers = @{ 'User-Agent' = 'Mozilla/5.0 (compatible; FastMediaSorter-docs-linkcheck)' }
    $status = $null
    $failure = $null
    foreach ($method in 'Head', 'Get') {
        try {
            $resp = Invoke-WebRequest -Uri $url -Method $method -Headers $headers -TimeoutSec $timeout `
                -MaximumRedirection 8 -SkipHttpErrorCheck -ErrorAction Stop
            $status = [int]$resp.StatusCode
            $failure = $null
        } catch {
            $status = $null
            $failure = $_.Exception.Message
        }
        # Many servers answer HEAD with 403/404/405 while GET succeeds; only GET is the verdict then.
        if ($null -ne $status -and $status -lt 400) { break }
    }
    [pscustomobject]@{ Url = $url; Status = $status; Failure = $failure }
}

$dead = [System.Collections.Generic.List[object]]::new()
$warn = [System.Collections.Generic.List[object]]::new()
$answered = 0
foreach ($r in $results) {
    if ($null -ne $r.Status) {
        $answered++
        if ($r.Status -in 404, 410) { $dead.Add($r) }
        elseif ($r.Status -ge 400) { $warn.Add($r) }
        continue
    }
    if ($r.Failure -match '(?i)(No such host|Name or service not known|nodename nor servname|could not be resolved)') {
        $dead.Add($r)
    } else {
        $warn.Add($r)
    }
}

if ($sources.Count -gt 0 -and $answered -eq 0 -and $dead.Count -eq 0) {
    Write-Host 'assert-docs-external-links: FAIL (cannot verify) - no link answered at all; is the network down?' -ForegroundColor Red
    exit 2
}

foreach ($w in $warn) {
    $why = if ($null -ne $w.Status) { "HTTP $($w.Status)" } else { $w.Failure }
    Write-Host "  [WARN] $($w.Url) - $why (in $($sources[$w.Url] -join ', '))" -ForegroundColor Yellow
}
foreach ($d in $dead) {
    $why = if ($null -ne $d.Status) { "HTTP $($d.Status)" } else { $d.Failure }
    Write-Host "  [DEAD] $($d.Url) - $why (in $($sources[$d.Url] -join ', '))" -ForegroundColor Red
}

if ($dead.Count -gt 0) {
    Write-Host "assert-docs-external-links: FAIL ($($dead.Count) dead of $($sources.Count) link(s)) - fix the recipe source and regenerate with scripts/docs/generate-docs-pages.ps1" -ForegroundColor Red
    exit 1
}
Write-Host "assert-docs-external-links: PASS ($($sources.Count) link(s), $($warn.Count) warning(s))" -ForegroundColor Green
exit 0
