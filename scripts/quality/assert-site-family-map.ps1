#requires -Version 7.0
<#
.SYNOPSIS
    S3454 conformance gate for contract SITE-FAMILY-MAP 1.1: the footer grid and the one contact.

.DESCRIPTION
    The map gained StreamsPlayer and OneClickRunner on 2026-09-22 and the six product pages, each
    carrying a hand-copied grid, caught up only through two separate tickets (S3425, S3426). The grid
    now renders from scripts/site/family-footer.json; this gate holds both ends of that chain.

    Four finding kinds:
      RENDER  - a page's footer block differs from what scripts/site/render-family-footer.ps1 renders
                from the source, or carries no markers (a hand edit, or a source edit not rendered).
      MAP     - a row of the catalog map's section 2, other than this product, has no source entry
                with the same URL; or the source carries a URL the map does not list (rule 5: a URL
                enters the footer only through the map).
      SELF    - this product's map row is absent, or its URL differs from `_config.yml` url + baseurl,
                the canonical address the site publishes (the repository-to-catalog direction: a
                mismatch is raised with the map owner, never fixed by editing the map from here).
      CONTACT - a page or README.md does not state `sza@ukr.net` and `github.com/SerZhyAle`, or states
                another email address (rule 3).

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER CatalogRoot
    The shared contracts catalog. Falls back to FMS_CONTRACTS_ROOT in the process, then at user
    scope. The catalog location is named in CLAUDE.md only, so this gate never spells it.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject and the verdict line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-site-family-map.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. A finding reaches a visitor only when the site is
    published, and the map half changes on the hub's clock, which no changed file here names. Runs
    from assert-release-scope-gates.ps1; placement row in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - every check passed.
      1 - at least one RENDER, MAP, SELF or CONTACT finding.
      2 - cannot verify: the root, a page, README.md, _config.yml, the source, the renderer or the
          catalog map is missing or unreadable.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string]$CatalogRoot = $env:FMS_CONTRACTS_ROOT,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$pages = @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html')
$contactEmail = 'sza@ukr.net'
$contactGitHub = 'github.com/SerZhyAle'

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-site-family-map: CANNOT VERIFY - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { Stop-CannotVerify 'no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT' }
$mapPath = Join-Path $CatalogRoot 'product-web-pages/SITE-FAMILY-MAP.md'
if (-not (Test-Path -LiteralPath $mapPath -PathType Leaf)) { Stop-CannotVerify "catalog map not readable: $mapPath" }

$sourcePath = Join-Path $Root 'scripts/site/family-footer.json'
$rendererPath = Join-Path $PSScriptRoot '../site/render-family-footer.ps1'
$configPath = Join-Path $Root '_config.yml'
foreach ($required in $sourcePath, $rendererPath, $configPath, (Join-Path $Root 'README.md')) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { Stop-CannotVerify "not found: $required" }
}

Write-Host "subject: root=$Root catalog=$CatalogRoot pages=$($pages.Count)"

$failures = [System.Collections.Generic.List[string]]::new()
function Add-Finding([string]$kind, [string]$message) {
    $script:failures.Add(('{0,-7} {1}' -f $kind, $message))
    if (-not $Quiet) { Write-Host "  FAIL [$kind] $message" -ForegroundColor Red }
}

function ConvertTo-UrlKey([string]$url) { return $url.Trim().TrimEnd('/').ToLowerInvariant() }

# RENDER - the renderer is the one definition of the block, so the gate asks it rather than re-deriving it.
$renderOutput = & pwsh -NoProfile -File $rendererPath -Root $Root -Check -Quiet 2>&1
$renderExit = $LASTEXITCODE
if ($renderExit -eq 2) { Stop-CannotVerify "renderer cannot render: $(($renderOutput | Select-Object -Last 1))" }
if ($renderExit -ne 0) {
    foreach ($line in $renderOutput) {
        $text = "$line".Trim()
        if ($text -match '^\S+\.html: ') { Add-Finding 'RENDER' "$text; run scripts/site/render-family-footer.ps1" }
    }
}

# MAP / SELF - section 2 of the catalog map against the source.
$mapText = Get-Content -LiteralPath $mapPath -Raw -Encoding utf8
$section = [regex]::Match($mapText, '(?s)\n## 2\.[^\n]*\n(.*?)(\n## |\z)')
if (-not $section.Success) { Stop-CannotVerify "catalog map has no section 2: $mapPath" }
$mapRows = [ordered]@{}
foreach ($m in [regex]::Matches($section.Groups[1].Value, '(?m)^\|\s*(?<tool>[^|]+?)\s*\|\s*[^|]*?\|\s*(?<url>https?://[^\s|]+)\s*\|')) {
    $mapRows[(ConvertTo-UrlKey $m.Groups['url'].Value)] = $m.Groups['tool'].Value
}
if ($mapRows.Count -eq 0) { Stop-CannotVerify "catalog map section 2 lists no URL rows: $mapPath" }

try {
    $source = Get-Content -LiteralPath $sourcePath -Raw -Encoding utf8 | ConvertFrom-Json
} catch {
    Stop-CannotVerify "source is not valid JSON: $($_.Exception.Message)"
}
$selfKey = ConvertTo-UrlKey "$($source.self)"
$sourceKeys = @($source.tools | ForEach-Object { ConvertTo-UrlKey "$($_.url)" })

foreach ($key in $mapRows.Keys) {
    if ($key -eq $selfKey) { continue }
    if ($sourceKeys -notcontains $key) {
        Add-Finding 'MAP' "map row '$($mapRows[$key])' ($key) is missing from scripts/site/family-footer.json"
    }
}
foreach ($key in $sourceKeys) {
    if (-not $mapRows.Contains($key)) { Add-Finding 'MAP' "scripts/site/family-footer.json links $key, which the map does not list" }
    if ($key -eq $selfKey) { Add-Finding 'MAP' "scripts/site/family-footer.json lists this product ($key) in its own footer" }
}

$config = Get-Content -LiteralPath $configPath -Raw -Encoding utf8
$cfgUrl = [regex]::Match($config, '(?m)^url:\s*"?(?<v>[^"\r\n]+)"?').Groups['v'].Value
$cfgBase = [regex]::Match($config, '(?m)^baseurl:\s*"?(?<v>[^"\r\n]*)"?').Groups['v'].Value
$canonicalKey = ConvertTo-UrlKey ($cfgUrl.TrimEnd('/') + $cfgBase)
if ($selfKey -ne $canonicalKey) {
    Add-Finding 'SELF' "family-footer.json self is $selfKey but _config.yml publishes $canonicalKey"
}
if (-not $mapRows.Contains($canonicalKey)) {
    Add-Finding 'SELF' "the map has no row for this product's canonical address $canonicalKey; raise it with the map owner"
}

# CONTACT - rule 3 on every surface this repository owns.
$emailPattern = '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}'
foreach ($file in @($pages + 'README.md')) {
    $full = Join-Path $Root $file
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { Stop-CannotVerify "page not found: $file" }
    $text = Get-Content -LiteralPath $full -Raw -Encoding utf8
    if (-not $text.Contains($contactEmail)) { Add-Finding 'CONTACT' "${file}: does not state $contactEmail" }
    if ($text.IndexOf($contactGitHub, [StringComparison]::OrdinalIgnoreCase) -lt 0) { Add-Finding 'CONTACT' "${file}: does not link $contactGitHub" }
    $others = @([regex]::Matches($text, $emailPattern) | ForEach-Object { $_.Value } |
            Where-Object { $_ -ne $contactEmail } | Sort-Object -Unique)
    foreach ($other in $others) { Add-Finding 'CONTACT' "${file}: states $other; the one contact is $contactEmail" }
}

if ($failures.Count -gt 0) {
    Write-Host "assert-site-family-map: FAIL ($($failures.Count) finding(s))" -ForegroundColor Red
    if ($Quiet) { $failures | ForEach-Object { Write-Host "  $_" } }
    exit 1
}
Write-Host "assert-site-family-map: PASS ($($pages.Count) pages, $($mapRows.Count) map rows)" -ForegroundColor Green
exit 0
