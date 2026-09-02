#requires -Version 7.0
<#
.SYNOPSIS
    S2340 parity gate: the Play store listing carries every locale the app declares.

.DESCRIPTION
    Wear App Quality Guidelines WO-G2 requires the Play listing to "be localized in languages
    offered by the app". The app declares its languages once, in
    app_v2/src/main/res/xml/locales_config.xml (S1190: added there and nowhere else). The listing
    declares its own set a second time, in the LOCALES dict of
    scripts/release/publish-play-listing.py - and nothing compared the two copies, so the first grew
    to thirteen while the second sat at three. The drift was found by reading Google's guideline, not
    by any check in this repository.

    The reason it could not be found mechanically is that the only observer was publication itself:
    the publisher iterates its dict rather than the directory listing, so a locale folder with no row
    is skipped in silence, and publication is owner-gated and rare. This gate is the observer that
    runs on a shorter schedule than that.

    Three distinct failures, each named separately because they call for different repairs:
      1. PARITY   - a locale declared in locales_config.xml that no listing folder serves.
                    Repair: write the three texts, then add the dict row.
      2. COMPLETE - a folder named in LOCALES missing title.txt, short_description.txt or
                    full_description.txt. The publisher exits 1 on this for EVERY locale, not just
                    the incomplete one (publish-play-listing.py load_listing), so one missing file
                    blocks the whole listing.
      3. LIMIT    - a text file over the Play maximum (30 / 80 / 4000). Counted the way the
                    publisher counts it: strip surrounding whitespace, then count code points, which
                    is what Python's len() reports on the string Play receives.

    A dict row serving no declared locale is reported under PARITY too. The two declarations
    disagreeing in that direction is still drift - it means the listing offers a language the app
    does not - and finding it here is cheaper than finding it in the Console.

    THE APP-LOCALE-TO-FOLDER TABLE IS EXPLICIT ON PURPOSE. Play's listing languages are a fixed list,
    not free-form BCP-47, and the code cannot be derived from the app's locale tag: 'uk' takes no
    region while 'de' requires one, 'ar' and 'ur' forbid one, and Chinese has no script-only code at
    all, so the app's 'zh-Hans' has to be mapped onto the Simplified regional variant 'zh-CN'. A
    derived mapping would be wrong for five of the thirteen. Adding a language to the app therefore
    means adding a row here as well - which is the point: this gate refuses until the listing follows.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape. This gate is fail-closed by default,
    so the switch changes nothing - a violation exits 1 with or without it.

.PARAMETER Quiet
    Print only the expected/actual summary line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-play-listing-locales.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE, not per ticket. Its subject is the whole listing tree
    against the whole locale declaration - a state an unrelated ticket neither created nor can
    repair - and between releases the defect cannot reach a user, because publication is the only
    thing that exposes it. Wiring it into post-change.ps1 would redden whichever session closed next
    over debt belonging to nobody in the room, which is the class S1939 measured at 68 of 191 red
    lines. It runs from assert-release-scope-gates.ps1, beside assert-new-lexemes-translated.ps1 -
    the gate of the same shape, judging the same thirteen-locale set for strings.xml.

    Exit codes (CLAUDE.md Rule 7):
      0 - every declared locale is served, every mapped folder is complete and inside its limits.
      1 - a parity, completeness or character-limit violation.
      2 - cannot verify: locales_config.xml or publish-play-listing.py is missing or unreadable.
#>
[CmdletBinding()]
param(
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

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$localesConfig = Join-Path $repoRoot 'app_v2/src/main/res/xml/locales_config.xml'
$publisher = Join-Path $repoRoot 'scripts/release/publish-play-listing.py'
$listingRoot = Join-Path $repoRoot 'play/listing'

# App locale (locales_config.xml) -> folder under play/listing/. See the header: Play's codes are a
# fixed list and are not derivable from the app's tag, so this correspondence is data, not a rule.
$FolderForAppLocale = [ordered]@{
    'en'      = 'en-US'
    'ru'      = 'ru-RU'
    'uk'      = 'uk-UA'
    'zh-Hans' = 'zh-CN'
    'hi'      = 'hi-IN'
    'es'      = 'es-419'
    'fr'      = 'fr-FR'
    'ar'      = 'ar'
    'bn'      = 'bn-BD'
    'pt'      = 'pt-BR'
    'ur'      = 'ur'
    'de'      = 'de-DE'
    'it'      = 'it-IT'
}

$Limits = [ordered]@{
    'title.txt'             = 30
    'short_description.txt' = 80
    'full_description.txt'  = 4000
}

function Measure-PlayLength {
    <#
    .SYNOPSIS
        Character count as the publisher sees it: trimmed, code points not UTF-16 units.
    #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    # A surrogate pair is one code point, and Python's len() - the arbiter in publish-play-listing.py
    # - counts it as one. Collapsing each pair to a single unit makes .Length agree with it.
    return ($Text.Trim() -replace '[\uD800-\uDBFF][\uDC00-\uDFFF]', '.').Length
}

if (-not (Test-Path -LiteralPath $localesConfig)) {
    Write-Host "assert-play-listing-locales: CANNOT VERIFY - missing $localesConfig" -ForegroundColor Yellow
    exit 2
}
if (-not (Test-Path -LiteralPath $publisher)) {
    Write-Host "assert-play-listing-locales: CANNOT VERIFY - missing $publisher" -ForegroundColor Yellow
    exit 2
}

# Declared app locales.
try {
    [xml]$xml = Get-Content -LiteralPath $localesConfig -Raw
}
catch {
    Write-Host "assert-play-listing-locales: CANNOT VERIFY - $localesConfig is not valid XML: $_" -ForegroundColor Yellow
    exit 2
}
$declared = @($xml.'locale-config'.locale | ForEach-Object { $_.name })
if ($declared.Count -eq 0) {
    Write-Host "assert-play-listing-locales: CANNOT VERIFY - no <locale> elements in $localesConfig" -ForegroundColor Yellow
    exit 2
}

# The publisher's LOCALES dict, read as text: importing the module would need its Google API
# dependencies present, which a quality gate must not require.
$publisherText = Get-Content -LiteralPath $publisher -Raw
$dictMatch = [regex]::Match($publisherText, '(?s)^LOCALES\s*=\s*\{(.*?)\}', 'Multiline')
if (-not $dictMatch.Success) {
    Write-Host "assert-play-listing-locales: CANNOT VERIFY - no LOCALES dict found in $publisher" -ForegroundColor Yellow
    exit 2
}
$playCodeForFolder = [ordered]@{}
foreach ($pair in [regex]::Matches($dictMatch.Groups[1].Value, "'([^']+)'\s*:\s*'([^']+)'")) {
    $playCodeForFolder[$pair.Groups[1].Value] = $pair.Groups[2].Value
}
if ($playCodeForFolder.Count -eq 0) {
    Write-Host "assert-play-listing-locales: CANNOT VERIFY - LOCALES dict in $publisher holds no rows" -ForegroundColor Yellow
    exit 2
}

$failures = [System.Collections.Generic.List[string]]::new()
function Add-Failure([string]$kind, [string]$message) {
    $script:failures.Add(("{0,-8} {1}" -f $kind, $message))
    if (-not $Quiet) { Write-Host "  FAIL [$kind] $message" -ForegroundColor Red }
}

# 1. PARITY - every declared app locale reaches a published folder.
$servedFolders = [System.Collections.Generic.HashSet[string]]::new()
foreach ($locale in $declared) {
    if (-not $FolderForAppLocale.Contains($locale)) {
        Add-Failure 'PARITY' ("app locale '$locale' is declared in locales_config.xml but this gate " +
            "carries no Play folder for it - add its row to `$FolderForAppLocale after looking the " +
            'code up in support.google.com/googleplay/android-developer/answer/9844778')
        continue
    }
    $folder = $FolderForAppLocale[$locale]
    [void]$servedFolders.Add($folder)
    if (-not $playCodeForFolder.Contains($folder)) {
        Add-Failure 'PARITY' ("app locale '$locale' has no listing: '$folder' is absent from LOCALES " +
            'in publish-play-listing.py, so the publisher skips it silently (WO-G2 requires the ' +
            'listing to be localized in the languages the app offers)')
    }
}

# 1b. PARITY, the other direction - a published locale the app does not offer.
foreach ($folder in $playCodeForFolder.Keys) {
    if (-not $servedFolders.Contains($folder)) {
        Add-Failure 'PARITY' ("LOCALES publishes '$folder' but no locale in locales_config.xml maps " +
            'to it - the listing offers a language the app does not')
    }
}

# 2 + 3. COMPLETE and LIMIT, over the folders the publisher actually reads.
foreach ($folder in $playCodeForFolder.Keys) {
    $dir = Join-Path $listingRoot $folder
    if (-not (Test-Path -LiteralPath $dir)) {
        Add-Failure 'COMPLETE' ("LOCALES names '$folder' but play/listing/$folder does not exist - " +
            'the publisher exits 1 for every locale, not just this one')
        continue
    }
    foreach ($file in $Limits.Keys) {
        $path = Join-Path $dir $file
        if (-not (Test-Path -LiteralPath $path)) {
            Add-Failure 'COMPLETE' ("play/listing/$folder is missing $file - the publisher exits 1 " +
                'for every locale, not just this one')
            continue
        }
        $length = Measure-PlayLength ([IO.File]::ReadAllText($path))
        if ($length -gt $Limits[$file]) {
            Add-Failure 'LIMIT' ("play/listing/$folder/$file is $length characters, over the Play " +
                "maximum of $($Limits[$file])")
        }
    }
}

$summary = ('assert-play-listing-locales: expected: 0 | actual: {0} violation(s) over {1} declared ' +
    'locale(s) and {2} published folder(s)') -f $failures.Count, $declared.Count, $playCodeForFolder.Count
Write-Host $summary

if ($failures.Count -gt 0) {
    Write-Host ('assert-play-listing-locales: FAIL - the app locale declaration and the Play listing ' +
        'have parted. locales_config.xml is the authority (S1190); bring the listing to it, never ' +
        'the other way round.') -ForegroundColor Red
    exit 1
}

if (-not $Quiet) {
    Write-Host ("assert-play-listing-locales: PASS - all $($declared.Count) declared locales are " +
        'published, complete and inside the Play limits.') -ForegroundColor Green
}
exit 0
