#requires -Version 7.0
<#
.SYNOPSIS
    S3452 conformance gate for contract PAGE-CONTENT 1.1: no emoji, and the above-the-fold order.

.DESCRIPTION
    The two emoji the landing carried sat inside the feature explorer's script, not in the markup a
    reader skims, and were found only by reading the script (S3425). The sideload pages linked their
    header button to `#download` and had no tagline long after they moved onto the kit (S3426). This
    gate holds the points a pattern can judge; the acceptance test (an unfamiliar visitor can say what
    the product is) needs a human and is not judged here.

    Two finding kinds, per page:
      EMOJI - a pictographic code point (U+1F000-U+1FAFF, U+2600-U+27BF, the U+FE0F presentation
              selector) anywhere in the file, markup and inline scripts alike, or its escaped form:
              a `\uD83C`-`\uD83E` surrogate or `\u{1F...}` escape in a script, a `&#x1F...;` or
              `&#126xxx;`-`&#129xxx;` entity in markup.
      ORDER - the sticky `site-header` has no link to `#get`; the page has not exactly one H1; no
              element carries `id="get"`; no tagline (`class="subtitle"`) or no lead
              (`class="whatfor"` / `class="nolegal-intro"`) stands between the H1 and `#get`; or the
              sequence site-header, H1, `#get`, site-footer is broken.

    Not judged, recorded in the catalog registry instead: the landing places its scenario cards after
    `#get` (a dated section 3 exception). Out of scope by decision: `broadcast-import.html`, the
    noindex fallback of the live-broadcast `intent://` link - it hands a payload to the app and is not
    a product page a visitor reads.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject and the verdict line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-page-content.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. A finding reaches a user only when the site is
    published, beside assert-page-style.ps1. Runs from assert-release-scope-gates.ps1; placement row
    in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - every page passed every check.
      1 - at least one EMOJI or ORDER finding.
      2 - cannot verify: the root or a page is missing.
#>
[CmdletBinding()]
param(
    [string]$Root,
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

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-page-content: CANNOT VERIFY - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

Write-Host "subject: root=$Root pages=$($pages.Count)"

$failures = [System.Collections.Generic.List[string]]::new()
function Add-Finding([string]$kind, [string]$message) {
    $script:failures.Add(('{0,-6} {1}' -f $kind, $message))
    if (-not $Quiet) { Write-Host "  FAIL [$kind] $message" -ForegroundColor Red }
}

$escapedEmoji = '\\u[dD]83[c-eC-E]|\\u\{1[fF][0-9a-fA-F]{3}\}|&#[xX]1[fF][0-9a-fA-F]{3};|&#12[6-9][0-9]{3};'

function Get-LineNumber([string]$text, [int]$index) {
    return ([regex]::Matches($text.Substring(0, $index), "`n")).Count + 1
}

foreach ($page in $pages) {
    $full = Join-Path $Root $page
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { Stop-CannotVerify "page not found: $page" }
    $html = Get-Content -LiteralPath $full -Raw -Encoding utf8

    $offset = 0
    foreach ($rune in $html.EnumerateRunes()) {
        $v = $rune.Value
        if (($v -ge 0x1F000 -and $v -le 0x1FAFF) -or ($v -ge 0x2600 -and $v -le 0x27BF) -or $v -eq 0xFE0F) {
            Add-Finding 'EMOJI' ('{0}:{1}: U+{2:X4}; use a monochrome SVG beside a text label' -f $page, (Get-LineNumber $html $offset), $v)
        }
        $offset += $rune.Utf16SequenceLength
    }
    foreach ($m in [regex]::Matches($html, $escapedEmoji)) {
        Add-Finding 'EMOJI' ('{0}:{1}: escaped emoji {2}' -f $page, (Get-LineNumber $html $m.Index), $m.Value)
    }

    $header = [regex]::Match($html, '(?s)<header\b[^>]*class="site-header"[^>]*>(.*?)</header>')
    $h1s = [regex]::Matches($html, '<h1\b')
    $get = [regex]::Match($html, '\bid="get"')
    $footer = [regex]::Match($html, '<footer\b[^>]*class="site-footer"')

    if (-not $header.Success) { Add-Finding 'ORDER' "${page}: no sticky site-header" }
    elseif ($header.Groups[1].Value -notmatch 'href="#get"') { Add-Finding 'ORDER' "${page}: the site-header has no link to #get" }
    if ($h1s.Count -ne 1) { Add-Finding 'ORDER' "${page}: $($h1s.Count) H1 elements; a product page has exactly one" }
    if (-not $get.Success) { Add-Finding 'ORDER' "${page}: no element carries id=`"get`"" }
    if (-not $footer.Success) { Add-Finding 'ORDER' "${page}: no site-footer" }
    if (-not ($header.Success -and $h1s.Count -eq 1 -and $get.Success -and $footer.Success)) { continue }

    $h1 = $h1s[0].Index
    if (-not ($header.Index -lt $h1 -and $h1 -lt $get.Index -and $get.Index -lt $footer.Index)) {
        Add-Finding 'ORDER' "${page}: the sequence site-header, H1, #get, site-footer is broken"
        continue
    }
    $aboveGet = $html.Substring($h1, $get.Index - $h1)
    if ($aboveGet -notmatch 'class="subtitle"') { Add-Finding 'ORDER' "${page}: no tagline (class=`"subtitle`") between the H1 and #get" }
    if ($aboveGet -notmatch 'class="(whatfor|nolegal-intro)"') { Add-Finding 'ORDER' "${page}: no what-it-is lead (whatfor / nolegal-intro) between the H1 and #get" }
}

if ($failures.Count -gt 0) {
    Write-Host "assert-page-content: FAIL ($($failures.Count) finding(s))" -ForegroundColor Red
    if ($Quiet) { $failures | ForEach-Object { Write-Host "  $_" } }
    exit 1
}
Write-Host "assert-page-content: PASS ($($pages.Count) pages)" -ForegroundColor Green
exit 0
