<#
.SYNOPSIS
    Notify IndexNow-participating search engines that the site changed.

.DESCRIPTION
    IndexNow is a free open protocol: one request tells Bing, Yandex, Seznam and Naver that URLs
    changed, instead of waiting for a crawl. Google does not participate - it still needs Search
    Console. Ticket: S1268.

    URLs are read from the generated sitemap.xml so the payload always matches what the document
    registry publishes; there is no second list to keep in sync. Pass -Url to notify specific pages
    instead of the whole sitemap.

    The key file must stay reachable at the site root, otherwise every submission is rejected:
    https://serzhyale.github.io/FastMediaSorter_mob_v2/16c034b6579b4e3eb8cecedb80f9ac79.txt

.PARAMETER Url
    One or more URLs to submit instead of the full sitemap. Must be inside the site host.

.PARAMETER WhatIf
    Print the payload and exit without contacting the endpoint.

.EXAMPLE
    pwsh -NoProfile -File scripts/site/ping-indexnow.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/site/ping-indexnow.ps1 -Url https://serzhyale.github.io/FastMediaSorter_mob_v2/

.NOTES
    Exit codes: 0 = accepted (HTTP 200/202) or -WhatIf, 1 = endpoint rejected the submission,
    2 = invalid invocation or unreadable sitemap.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string[]] $Url
)

$ErrorActionPreference = 'Stop'

$SiteHost   = 'serzhyale.github.io'
$SiteRoot   = 'https://serzhyale.github.io/FastMediaSorter_mob_v2/'
$IndexNowKey = '16c034b6579b4e3eb8cecedb80f9ac79'
$Endpoint   = 'https://api.indexnow.org/IndexNow'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ($Url) {
    $urls = @($Url)
    $foreign = @($urls | Where-Object { $_ -notlike "$SiteRoot*" })
    if ($foreign) {
        Write-Error ("URLs outside {0} cannot be submitted with this key: {1}" -f $SiteRoot, ($foreign -join ', ')) -ErrorAction Continue
        exit 2
    }
} else {
    $sitemapPath = Join-Path $repoRoot 'sitemap.xml'
    if (-not (Test-Path -LiteralPath $sitemapPath)) {
        Write-Error "sitemap.xml not found - run scripts/document_registry/generate.ps1 first." -ErrorAction Continue
        exit 2
    }
    $sitemap = [xml](Get-Content -LiteralPath $sitemapPath -Raw -Encoding utf8)
    $urls = @($sitemap.urlset.url | ForEach-Object { $_.loc } | Where-Object { $_ })
    if (-not $urls) {
        Write-Error "sitemap.xml contains no <loc> entries." -ErrorAction Continue
        exit 2
    }
}

$body = [ordered]@{
    host        = $SiteHost
    key         = $IndexNowKey
    keyLocation = "$SiteRoot$IndexNowKey.txt"
    urlList     = $urls
} | ConvertTo-Json -Depth 3

Write-Host ("IndexNow: {0} URL(s) -> {1}" -f $urls.Count, $Endpoint)
$urls | ForEach-Object { Write-Host "  $_" }

if ($WhatIfPreference) {
    Write-Host 'WhatIf: payload not sent.'
    exit 0
}

try {
    $response = Invoke-WebRequest -Uri $Endpoint -Method Post -Body $body `
        -ContentType 'application/json; charset=utf-8' -UseBasicParsing -TimeoutSec 60
    $code = [int]$response.StatusCode
} catch {
    # A rejection carries a meaningful status (403 = key not reachable, 422 = URL/host mismatch),
    # so surface it rather than swallowing the exception.
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    Write-Error ("IndexNow submission failed (HTTP {0}): {1}" -f $code, $_.Exception.Message) -ErrorAction Continue
    exit 1
}

if ($code -eq 200 -or $code -eq 202) {
    Write-Host ("IndexNow accepted the submission (HTTP {0})." -f $code) -ForegroundColor Green
    exit 0
}

Write-Error ("IndexNow returned an unexpected status: HTTP {0}" -f $code) -ErrorAction Continue
exit 1
