#!/usr/bin/env pwsh
#requires -Version 7.0
<#
.SYNOPSIS
    Read the version the public Google Play store page actually serves - anonymous, credential-free.

.DESCRIPTION
    The Play Developer API answers what each track HOLDS. It does not answer what the store
    SERVES, and the two disagree: measured 2026-08-31, the production track reported
    2.60.8250.134 as completed while the public page served 2.60.8151.948 from 2026-08-15. A
    record built from the API alone therefore reads green while users receive a two-week-old
    build (S1256, S2272).

    This reader closes that gap. It fetches the store listing the way an anonymous visitor does -
    no service account, no .venv, no edit session - and reports the version string the markup
    carries. It is the producer of the `Public serve` row of docs/PLAY_PUBLISHING_STATE.md.

    Version detection is layered and refusal-biased, because a bare four-segment scan of this page
    returns five candidates: the served version, two unrelated Play feature-rollout versions, an
    "0.0.0.0" server stamp, and a fragment of an SVG path. Layer 1 reads Play's own data-callback
    key `"141"`, which holds the version alone. Layer 2 drops the key and matches the surrounding
    `[[["<version>"]]]` shape, which the noise candidates do not have. Anything still ambiguous
    exits 2 with the candidates named. The numeric key is Google's and may change without notice;
    when it does, this degrades to layer 2 and then to a refusal, never to a wrong answer - a wrong
    version in the state record is worse than a missing one, because that row is read as measured
    fact.

.PARAMETER Package
    Application id to read. Defaults to com.sza.fastmediasorter.

.PARAMETER Json
    Emit an object for a caller that parses it - package, servedVersion, updatedOn, sourceUrl and
    measuredUtc. Default output is a single human-readable line.

.PARAMETER RequireVersionAbove
    Turn the read into an assertion: exit 1 unless the served version is strictly above this one.
    This is the one-command form of S2272 acceptance criterion 5.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-public-serve.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-public-serve.ps1 -Json

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-public-serve.ps1 -RequireVersionAbove 2.60.8151.948

.NOTES
    Exit codes:
      0 - the served version was read (and the -RequireVersionAbove assertion held, when given)
      1 - the -RequireVersionAbove assertion failed: the page still serves that version or older
      2 - could not verify: the request failed, no version was found, or several candidates were
          ambiguous
#>
[CmdletBinding()]
param(
    [string] $Package = 'com.sza.fastmediasorter',
    [switch] $Json,
    [string] $RequireVersionAbove
)

$ErrorActionPreference = 'Stop'

$measuredUtc = [DateTime]::UtcNow.ToString('yyyy-MM-dd')
$sourceUrl = "https://play.google.com/store/apps/details?id=$Package&hl=en&gl=US"
$userAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'

try {
    $response = Invoke-WebRequest -Uri $sourceUrl -UserAgent $userAgent -TimeoutSec 45 -MaximumRedirection 5
} catch {
    $msg = "read-play-public-serve: request to $sourceUrl failed - $($_.Exception.Message)"
    Write-Error $msg -ErrorAction Continue
    exit 2
}

if ($response.StatusCode -ne 200) {
    $msg = "read-play-public-serve: $sourceUrl returned HTTP $($response.StatusCode), expected 200."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$html = [string] $response.Content

# Every call site re-wraps the result in @(). PowerShell unrolls a single-element array on return,
# and the scalar that arrives is a string whose .Count is 1 and whose [0] is its first character -
# so an un-wrapped call reports the version "2" and exits 0.
function Get-DistinctCapture {
    param([string] $Text, [string] $Pattern)
    return @([regex]::Matches($Text, $Pattern) |
        ForEach-Object { $_.Groups[1].Value } |
        Sort-Object -Unique)
}

$versionPattern = '\d+\.\d+\.\d+\.\d+'
$servedVersion = $null
$detectedBy = $null

$keyed = @(Get-DistinctCapture -Text $html -Pattern "`"141`":\[\[\[`"($versionPattern)`"\]\]")
if ($keyed.Count -eq 1) {
    $servedVersion = $keyed[0]
    $detectedBy = 'data-callback key 141'
} else {
    $shaped = @(Get-DistinctCapture -Text $html -Pattern "\[\[\[`"($versionPattern)`"\]\]")
    if ($shaped.Count -eq 1) {
        $servedVersion = $shaped[0]
        $detectedBy = 'version-shaped data node'
    } else {
        $loose = @(Get-DistinctCapture -Text $html -Pattern "($versionPattern)")
        $found = @(if ($shaped.Count -gt 0) { $shaped } else { $loose })
        $msg = if ($found.Count -eq 0) {
            "read-play-public-serve: no version string found on $sourceUrl. The listing markup " +
                'changed, or the page did not render a version.'
        } else {
            "read-play-public-serve: $($found.Count) ambiguous version candidates on $sourceUrl - " +
                "$($found -join ', '). Refusing to guess."
        }
        Write-Error $msg -ErrorAction Continue
        exit 2
    }
}

$updatedOn = $null
$updatedMatch = [regex]::Match($html, '"146":\[\["([A-Z][a-z]{2} \d{1,2}, \d{4})"')
if ($updatedMatch.Success) {
    $updatedOn = $updatedMatch.Groups[1].Value
}

$result = [ordered] @{
    package       = $Package
    servedVersion = $servedVersion
    updatedOn     = $updatedOn
    detectedBy    = $detectedBy
    sourceUrl     = $sourceUrl
    measuredUtc   = $measuredUtc
}

if ($Json) {
    [pscustomobject] $result | ConvertTo-Json -Depth 3
} else {
    $updatedText = if ($updatedOn) { ", updated on $updatedOn" } else { '' }
    Write-Host "$Package serves $servedVersion$updatedText (measured $measuredUtc UTC)."
}

if ($RequireVersionAbove) {
    $servedParsed = $null
    $requiredParsed = $null
    if (-not [version]::TryParse($servedVersion, [ref] $servedParsed) -or
        -not [version]::TryParse($RequireVersionAbove, [ref] $requiredParsed)) {
        $msg = "read-play-public-serve: cannot compare '$servedVersion' with " +
            "'$RequireVersionAbove' - one of them is not a parseable version."
        Write-Error $msg -ErrorAction Continue
        exit 2
    }
    if ($servedParsed -le $requiredParsed) {
        $msg = "read-play-public-serve: the store still serves $servedVersion, which is not above " +
            "$RequireVersionAbove."
        Write-Error $msg -ErrorAction Continue
        exit 1
    }
    Write-Host "read-play-public-serve: $servedVersion is above $RequireVersionAbove."
}

exit 0
