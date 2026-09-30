#requires -Version 7.0
<#
.SYNOPSIS
    S4009 gate: the watch face's Google Play page answers before any surface links to it ships.

.DESCRIPTION
    The phone app, the watch app, the READMEs, the Wear portal, the install guides, the recipes and
    the landing pages all link to the FastMediaSorter Watch Face page on Google Play. Links to a page
    that does not exist send every reader to a 404, so a release that carries those links must not
    ship until the owner has published the face and its store page answers.

    The gate runs in two stages:
      1. Asks git which tracked user-visible surface mentions the face package id. None - the tree
         carries no link, there is nothing to protect, PASS without touching the network.
      2. Requests the face's Play address with a plain HTTPS GET (redirects followed, browser-like
         user agent). Only HTTP 200 counts as live; any other status is a page a reader cannot use.

    Play answers an unpublished or not-yet-approved package with 404, which is the expected state
    between the face upload and the owner's review submission.

.PARAMETER Url
    The address to request. Defaults to the face's Play page; the contract suite points it at an
    unresolvable host to prove the cannot-verify path.

.PARAMETER PackageId
    The face package id searched for in the surfaces. The contract suite passes an id no file
    carries to prove the no-link path.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape.

.PARAMETER Quiet
    Print only the verdict line and its reason.

.PARAMETER TimeoutSec
    Request timeout.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-watchface-listing-live.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. Its subject is a page on Google Play, which goes live
    on the owner's store review clock rather than on any ticket's; a link reaches a reader only when
    a release or the site is published; the finding names the address and the status it answered.

    Exit codes:
      0  the face's Play page answers HTTP 200, or no tracked surface links to it
      1  a surface links to the face and its Play page does not answer HTTP 200
      2  cannot verify: git is unavailable, or the request got no HTTP answer at all (no network)
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $PackageId = 'com.sza.fastmediasorter.watchface',
    [string] $Url = '',
    [switch] $Gate,
    [switch] $Quiet,
    [int] $TimeoutSec = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'watchface-listing-live'; files = 'user-visible surfaces' })

if ([string]::IsNullOrWhiteSpace($Url)) {
    $Url = "https://play.google.com/store/apps/details?id=$PackageId"
}

# The user-visible surfaces. The face's own listing tree (play/watchface/), the specs and the
# scripts name the package id too, but none of them is a link a reader follows.
$surfaces = @(
    ':(glob)*.html',
    ':(glob)README*.md',
    'docs/README*.md',
    'docs/wear',
    'docs/howto',
    'docs/content',
    'documentation',
    'play/listing',
    'fastlane/metadata',
    'app_v2/src',
    ':(exclude)app_v2/src/test',
    ':(exclude)app_v2/src/androidTest',
    'wear/src',
    ':(exclude)wear/src/test',
    ':(exclude)wear/src/androidTest'
)

$linked = & git -C $RepoRoot grep -l -F -e $PackageId -- @surfaces 2>&1
$grepCode = $LASTEXITCODE
# git grep: 0 = matches, 1 = no match, anything else = git itself failed.
if ($grepCode -gt 1) {
    Write-Host "assert-watchface-listing-live: FAIL (cannot verify) - git grep exited $grepCode`: $($linked | Out-String)" -ForegroundColor Red
    exit 2
}
$linkedFiles = @($linked | Where-Object { $_ -is [string] -and $_.Trim() -ne '' })
if ($grepCode -eq 1 -or $linkedFiles.Count -eq 0) {
    Write-Host "assert-watchface-listing-live: PASS (no tracked user-visible surface links to $PackageId)" -ForegroundColor Green
    exit 0
}

if (-not $Quiet) {
    Write-Host "$($linkedFiles.Count) tracked surface(s) link to $PackageId, e.g. $(($linkedFiles | Select-Object -First 3) -join ', ')"
    Write-Host "Requesting $Url .."
}

$headers = @{
    'User-Agent'      = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
    'Accept-Language' = 'en-US,en;q=0.9'
}
$status = $null
$failure = $null
try {
    $resp = Invoke-WebRequest -Uri $Url -Method Get -Headers $headers -TimeoutSec $TimeoutSec `
        -MaximumRedirection 8 -SkipHttpErrorCheck -ErrorAction Stop
    $status = [int]$resp.StatusCode
}
catch {
    $failure = $_.Exception.Message
}

if ($null -eq $status) {
    Write-Host "assert-watchface-listing-live: FAIL (cannot verify) - no HTTP answer from $Url`: $failure" -ForegroundColor Red
    exit 2
}

if ($status -ne 200) {
    Write-Host "assert-watchface-listing-live: FAIL - $Url answered HTTP $status, and $($linkedFiles.Count) tracked surface(s) link to it." -ForegroundColor Red
    Write-Host '  Links to a page that does not exist send every reader to a 404. Publish the watch face on Google Play (play/watchface/README.md) and re-run until it answers 200; the release does not ship before that.' -ForegroundColor Red
    exit 1
}

Write-Host "assert-watchface-listing-live: PASS ($Url answered HTTP 200; $($linkedFiles.Count) surface(s) link to it)" -ForegroundColor Green
exit 0
