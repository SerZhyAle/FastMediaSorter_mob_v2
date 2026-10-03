#requires -Version 7.0
<#
.SYNOPSIS
    S3453 conformance gate for contract PAGE-STYLE 1.2: language switcher, pre-paint resolver, locale row, kit stylesheet.

.DESCRIPTION
    The product site's six pages were moved onto the family kit by reading (S3425, S3426), and
    nothing noticed the sideload pages carried a "UK" button, no theme and no kit header for as long
    as they existed. This gate holds the points a pattern can judge; everything else in PAGE-STYLE
    (layout, contrast, spacing) needs a rendered browser and is not judged here. S4059 raised it
    from 1.0 to 1.2 after it had PASSed for a day while the 1.2 resolver and locale row were absent.

    Judged pages: the six hand-maintained ones, plus every other `index-*.html` at the root - the
    landing in a further locale, written by scripts/site/generate-landing-pages.ps1.

    Four finding kinds, per page:
      LANG   - the switcher has no `data-lang="ua"` link labelled UA, a language link is labelled UK,
               the pre-kit `lang-switcher` markup is back, the pre-paint does not read `sza-lang` or
               does not map a stored `uk` to `ua`, no script writes `sza-lang`, or a link carries a
               `data-lang` other than ru / en / ua (sections 4.2 and 7: the key is origin-shared).
      THEME  - no <script> reading `sza-theme` precedes the first stylesheet link, so the page paints
               in the default theme and then flips, or that script does not validate the stored
               value to dark / light (PAGE-STYLE section 7).
      LOCALE - a link to another locale (`<a hreflang>`) stands after the first H1, so further
               locales are not the row under the header, or a further-locale link carries
               `data-lang` and would be written to `sza-lang` (section 4.2).
      KIT   - a page links `sza-kit.css` and the linked file is not byte-identical to the catalog's
              reference; or no page links it and the catalog registry's section 3 holds no open,
              unexpired `PAGE-STYLE` exception for the FastMediaSorter website naming `sza-kit.css`.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER CatalogRoot
    The shared contracts catalog. Falls back to FMS_CONTRACTS_ROOT in the process, then at user
    scope. CLAUDE.md is the one file that names its location.

.PARAMETER Today
    The date an exception's `until` is judged against, as yyyy-MM-dd. Defaults to today.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject and the verdict line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-page-style.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. A finding reaches a user only when the site is
    published, and the KIT half judges an external catalog whose exception can expire on a date no
    changed file names. Runs from assert-release-scope-gates.ps1; placement row in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - every page passed every check.
      1 - at least one LANG, THEME, LOCALE or KIT finding.
      2 - cannot verify: the root, a page, the catalog, its registry or its reference kit is missing.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string]$CatalogRoot = $env:FMS_CONTRACTS_ROOT,
    [string]$Today,
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

$handPages = @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html')
$product = 'FastMediaSorter website'

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-page-style: COULD NOT VERIFY - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

# A session started before the user variable was set still finds it.
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { Stop-CannotVerify 'no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT' }
$registryPath = Join-Path $CatalogRoot '_meta/REGISTRY.md'
$referencePath = Join-Path $CatalogRoot 'product-web-pages/reference/sza-kit.css'
foreach ($required in $registryPath, $referencePath) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { Stop-CannotVerify "catalog file not found: $required" }
}

$todayDate = if ($Today) { [datetime]::ParseExact($Today, 'yyyy-MM-dd', $null) } else { (Get-Date).Date }

$generatedPages = @(Get-ChildItem -LiteralPath $Root -File -Filter 'index-*.html' |
        Where-Object { $handPages -notcontains $_.Name } | Sort-Object Name | ForEach-Object Name)
$pages = @($handPages) + $generatedPages

Write-Host "subject: root=$Root catalog=$CatalogRoot pages=$($pages.Count)"

$failures = [System.Collections.Generic.List[string]]::new()
function Add-Finding([string]$kind, [string]$message) {
    $script:failures.Add(('{0,-6} {1}' -f $kind, $message))
    if (-not $Quiet) { Write-Host "  FAIL [$kind] $message" -ForegroundColor Red }
}

$referenceHash = (Get-FileHash -LiteralPath $referencePath -Algorithm SHA256).Hash
$kitLinked = $false

foreach ($page in $pages) {
    $full = Join-Path $Root $page
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { Stop-CannotVerify "page not found: $page" }
    $html = Get-Content -LiteralPath $full -Raw -Encoding utf8

    if ($html -notmatch 'data-lang="ua"[^>]*>\s*UA\s*<') {
        Add-Finding 'LANG' "${page}: no data-lang=`"ua`" link labelled UA"
    }
    if ($html -match 'data-lang="[^"]*"[^>]*>\s*UK\s*<') {
        Add-Finding 'LANG' "${page}: a language link is labelled UK; the family label is UA"
    }
    if ($html -match 'lang-switcher') {
        Add-Finding 'LANG' "${page}: pre-kit lang-switcher markup; use the kit's .seg switcher"
    }

    $stylesheet = [regex]::Match($html, '<link\b[^>]*rel="stylesheet"[^>]*>')
    $prePaint = [regex]::Match($html, '(?s)<script\b[^>]*>(?:(?!</script>).)*sza-theme(?:(?!</script>).)*</script>')
    if (-not $prePaint.Success) {
        Add-Finding 'THEME' "${page}: no <script> reads sza-theme"
    }
    else {
        $resolver = $prePaint.Value
        if ($stylesheet.Success -and $prePaint.Index -gt $stylesheet.Index) {
            Add-Finding 'THEME' "${page}: the sza-theme pre-paint script stands after the first stylesheet link"
        }
        if ($resolver -notmatch "!==?\s*'dark'" -or $resolver -notmatch "!==?\s*'light'") {
            Add-Finding 'THEME' "${page}: the pre-paint accepts any stored sza-theme; validate it to dark / light (section 7)"
        }
        if ($resolver -notmatch 'sza-lang') {
            Add-Finding 'LANG' "${page}: the pre-paint does not read sza-lang (section 7)"
        }
        elseif ($resolver -notmatch "===?\s*'uk'") {
            Add-Finding 'LANG' "${page}: the pre-paint does not map a stored uk to ua (section 7)"
        }
    }
    if ($html -notmatch "setItem\(\s*'sza-lang'") {
        Add-Finding 'LANG' "${page}: no script writes sza-lang when RU / EN / UA is chosen (section 4.2)"
    }
    foreach ($m in [regex]::Matches($html, '<a\b[^>]*\bdata-lang="([^"]*)"')) {
        if (@('ru', 'en', 'ua') -cnotcontains $m.Groups[1].Value) {
            Add-Finding 'LANG' "${page}: a link carries data-lang=`"$($m.Groups[1].Value)`"; sza-lang holds only ru / en / ua"
        }
    }

    $h1 = [regex]::Match($html, '<h1\b')
    foreach ($m in [regex]::Matches($html, '<a\b[^>]*\bhreflang="([^"]*)"[^>]*>')) {
        $code = $m.Groups[1].Value
        if ($h1.Success -and $m.Index -gt $h1.Index) {
            Add-Finding 'LOCALE' "${page}: the hreflang=`"$code`" link stands after the H1; further locales are a row under the header"
        }
        if (@('ru', 'en', 'uk') -notcontains $code -and $m.Value -match '\bdata-lang=') {
            Add-Finding 'LOCALE' "${page}: the further-locale link hreflang=`"$code`" carries data-lang and would be written to sza-lang"
        }
    }

    foreach ($m in [regex]::Matches($html, '<link\b[^>]*href="([^"]*sza-kit\.css)"')) {
        $kitLinked = $true
        $href = $m.Groups[1].Value
        $kitPath = Join-Path $Root $href
        if (-not (Test-Path -LiteralPath $kitPath -PathType Leaf)) {
            Add-Finding 'KIT' "${page}: links $href, which does not exist"
        }
        elseif ((Get-FileHash -LiteralPath $kitPath -Algorithm SHA256).Hash -ne $referenceHash) {
            Add-Finding 'KIT' "${page}: $href differs from the catalog reference kit; replace it with the reference file"
        }
    }
}

if (-not $kitLinked) {
    $registry = Get-Content -LiteralPath $registryPath -Raw -Encoding utf8
    $section = [regex]::Match($registry, '(?ms)^##[ \t]+3\.[^\r\n]*\r?$(.*?)(?=^##[ \t]|\z)')
    $open = $null
    if ($section.Success) {
        foreach ($line in ($section.Groups[1].Value -split '\r?\n')) {
            if ($line -notmatch '^\|') { continue }
            $cells = @($line.Trim().Trim('|') -split '\|' | ForEach-Object { $_.Trim() })
            if ($cells.Count -lt 5) { continue }
            if ($cells[0] -notmatch '`PAGE-STYLE`' -or $cells[1] -ne $product) { continue }
            if ($cells[2] -notmatch 'sza-kit\.css' -or $cells[2].StartsWith('~~') -or $line -match '\*\*CLOSED') { continue }
            $open = $cells[-1]
            break
        }
    }
    if ($null -eq $open) {
        Add-Finding 'KIT' "no page links sza-kit.css and the registry's section 3 has no open PAGE-STYLE exception for $product naming it"
    }
    elseif ($open -notmatch '^\d{4}-\d{2}-\d{2}$') {
        Add-Finding 'KIT' "the PAGE-STYLE exception for $product has no yyyy-MM-dd until date (found '$open')"
    }
    elseif ([datetime]::ParseExact($open, 'yyyy-MM-dd', $null) -lt $todayDate) {
        Add-Finding 'KIT' "the PAGE-STYLE exception for $product expired on $open; vendor the reference kit or renew the exception"
    }
    elseif (-not $Quiet) {
        Write-Host "  kit: not vendored, registry exception open until $open"
    }
}

if ($failures.Count -gt 0) {
    Write-Host "assert-page-style: FAIL ($($failures.Count) finding(s))" -ForegroundColor Red
    if ($Quiet) { $failures | ForEach-Object { Write-Host "  $_" } }
    exit 1
}
Write-Host "assert-page-style: PASS ($($pages.Count) pages)" -ForegroundColor Green
exit 0
