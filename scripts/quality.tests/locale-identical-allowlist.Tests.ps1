# requires -Version 7.0
<#
.SYNOPSIS
    S3309: tests for the per-locale scope of the English-identical allow-list.

.DESCRIPTION
    The allow-list entitles a key to equal its English source. Before S3309 that entitlement was
    key-level, so one locale's legitimate identical exempted every other locale's untranslated copy
    of the same key. These tests pin the two entry shapes, the locale-aware predicate and the round
    trip through the writer, so a library that answers per key again fails here rather than in a
    release sweep.

.OUTPUTS
    Exit codes:
      0 - every assertion passed.
      1 - at least one assertion failed.
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1')

$script:pass = 0
$script:fail = 0

function Assert-Equal {
    param([Parameter(Mandatory)][string]$Name, $Expected, $Actual)
    if ($Expected -eq $Actual) {
        Write-Host ("PASS | {0} | {1}" -f $Name, $Actual)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | expected: {1} | actual: {2}" -f $Name, $Expected, $Actual)
        $script:fail++
    }
}

$scratchDir = Join-Path $repoRoot 'temp/scratch/s3309-allowlist'
if (Test-Path -LiteralPath $scratchDir) { Remove-Item -LiteralPath $scratchDir -Recurse -Force }
New-Item -ItemType Directory -Path $scratchDir -Force | Out-Null

function Write-Fixture {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Body)
    $path = Join-Path $scratchDir $Name
    [System.IO.File]::WriteAllText($path, $Body, [System.Text.UTF8Encoding]::new($false))
    return $path
}

Write-Host "`n=== Test 1: a legacy string array entitles every locale ===" -ForegroundColor Cyan
$legacyPath = Write-Fixture -Name 'legacy.json' -Body '["app_name", "audio"]'
$legacy = Get-LocaleIdenticalAllowlist -Path $legacyPath

Assert-Equal "Both legacy keys load" 2 $legacy.Count
Assert-Equal "Legacy key entitled in de" $true (Test-LocaleIdenticalAllowlisted -Key 'app_name' -Locale 'de' -Allowlist $legacy)
Assert-Equal "Legacy key entitled in ar" $true (Test-LocaleIdenticalAllowlisted -Key 'app_name' -Locale 'ar' -Allowlist $legacy)
Assert-Equal "Unlisted key entitled nowhere" $false (Test-LocaleIdenticalAllowlisted -Key 'browse_title' -Locale 'de' -Allowlist $legacy)

Write-Host "`n=== Test 2: a scoped object entitles only the locales it names ===" -ForegroundColor Cyan
$scopedPath = Write-Fixture -Name 'scoped.json' -Body @'
[
  "app_name",
  { "key": "camera_mode_photo", "locales": ["fr", "it"] },
  { "key": "browse_menu_group_text", "locales": [] }
]
'@
$scoped = Get-LocaleIdenticalAllowlist -Path $scopedPath

Assert-Equal "All three entries load" 3 $scoped.Count
Assert-Equal "Scoped key entitled in fr" $true (Test-LocaleIdenticalAllowlisted -Key 'camera_mode_photo' -Locale 'fr' -Allowlist $scoped)
Assert-Equal "Scoped key REFUSED in ar" $false (Test-LocaleIdenticalAllowlisted -Key 'camera_mode_photo' -Locale 'ar' -Allowlist $scoped)
Assert-Equal "Empty locales array reads as unscoped" $true (Test-LocaleIdenticalAllowlisted -Key 'browse_menu_group_text' -Locale 'hi' -Allowlist $scoped)
Assert-Equal "Bare string beside objects stays unscoped" $true (Test-LocaleIdenticalAllowlisted -Key 'app_name' -Locale 'ar' -Allowlist $scoped)

Write-Host "`n=== Test 3: the key-level question is answered by its own function ===" -ForegroundColor Cyan
Assert-Equal "Scoped key is present without naming a locale" $true (Test-LocaleIdenticalAllowlistHasKey -Key 'camera_mode_photo' -Allowlist $scoped)
Assert-Equal "Unlisted key is absent" $false (Test-LocaleIdenticalAllowlistHasKey -Key 'browse_title' -Allowlist $scoped)

Write-Host "`n=== Test 4: both shapes survive a save and load round trip ===" -ForegroundColor Cyan
$roundTripPath = Join-Path $scratchDir 'round-trip.json'
Save-LocaleIdenticalAllowlist -Allowlist $scoped -Path $roundTripPath
$reloaded = Get-LocaleIdenticalAllowlist -Path $roundTripPath
$savedText = Get-Content -LiteralPath $roundTripPath -Raw -Encoding UTF8

Assert-Equal "Entry count survives" $scoped.Count $reloaded.Count
Assert-Equal "Scope survives for fr" $true (Test-LocaleIdenticalAllowlisted -Key 'camera_mode_photo' -Locale 'fr' -Allowlist $reloaded)
Assert-Equal "Scope survives for ar" $false (Test-LocaleIdenticalAllowlisted -Key 'camera_mode_photo' -Locale 'ar' -Allowlist $reloaded)
Assert-Equal "Unscoped entry stays a bare string" $true ($savedText -match '"app_name"\s*,')
Assert-Equal "Scoped entry carries its locales" $true ($savedText -match '"locales"')

Write-Host "`n=== Test 5: a single scoped entry is still written as an array ===" -ForegroundColor Cyan
$singlePath = Join-Path $scratchDir 'single.json'
$single = [ordered]@{ 'lone_key' = (New-LocaleIdenticalAllowlistScope -Locales @('de')) }
Save-LocaleIdenticalAllowlist -Allowlist $single -Path $singlePath
$singleText = (Get-Content -LiteralPath $singlePath -Raw -Encoding UTF8).Trim()
$singleReloaded = Get-LocaleIdenticalAllowlist -Path $singlePath

Assert-Equal "One-entry file is a JSON array" $true ($singleText.StartsWith('['))
Assert-Equal "One-entry file reloads its only key" 1 $singleReloaded.Count
Assert-Equal "One-entry scope survives" $true (Test-LocaleIdenticalAllowlisted -Key 'lone_key' -Locale 'de' -Allowlist $singleReloaded)

Write-Host "`n=== Test 6: a plain key list still saves as unscoped entries ===" -ForegroundColor Cyan
$plainPath = Join-Path $scratchDir 'plain.json'
Save-LocaleIdenticalAllowlist -Allowlist @('zebra_key', 'alpha_key') -Path $plainPath
$plainReloaded = Get-LocaleIdenticalAllowlist -Path $plainPath

Assert-Equal "Both plain keys load back" 2 $plainReloaded.Count
Assert-Equal "Plain keys are sorted on disk" 'alpha_key' (@($plainReloaded.Keys)[0])
Assert-Equal "Plain key entitled everywhere" $true (Test-LocaleIdenticalAllowlisted -Key 'zebra_key' -Locale 'ja' -Allowlist $plainReloaded)

Write-Host "`n=== Test 7: a malformed or absent file degrades to an empty container ===" -ForegroundColor Cyan
$brokenPath = Write-Fixture -Name 'broken.json' -Body '{ this is not json'
# The warning below is the expected output of this case, not a failure: the loader degrades to an
# empty container rather than throwing, so a hand-edited file cannot take a release gate down.
$broken = Get-LocaleIdenticalAllowlist -Path $brokenPath
$absent = Get-LocaleIdenticalAllowlist -Path (Join-Path $scratchDir 'no-such-file.json')

Assert-Equal "Malformed file yields an empty container" 0 $broken.Count
Assert-Equal "Empty container is not null" $true ($null -ne $broken)
Assert-Equal "Absent file yields an empty container" 0 $absent.Count
Assert-Equal "Empty container entitles nothing" $false (Test-LocaleIdenticalAllowlisted -Key 'app_name' -Locale 'de' -Allowlist $absent)

Write-Host "`n=== Test 8: an entry repeated with different scopes keeps the wider one ===" -ForegroundColor Cyan
$dupePath = Write-Fixture -Name 'dupe.json' -Body @'
[
  { "key": "shared_key", "locales": ["fr"] },
  { "key": "shared_key", "locales": ["it"] }
]
'@
$dupe = Get-LocaleIdenticalAllowlist -Path $dupePath

Assert-Equal "Duplicate key collapses to one entry" 1 $dupe.Count
Assert-Equal "First scope survives the merge" $true (Test-LocaleIdenticalAllowlisted -Key 'shared_key' -Locale 'fr' -Allowlist $dupe)
Assert-Equal "Second scope survives the merge" $true (Test-LocaleIdenticalAllowlisted -Key 'shared_key' -Locale 'it' -Allowlist $dupe)
Assert-Equal "Unnamed locale is still refused" $false (Test-LocaleIdenticalAllowlisted -Key 'shared_key' -Locale 'ar' -Allowlist $dupe)

Remove-Item -LiteralPath $scratchDir -Recurse -Force

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "PASS: $script:pass | FAIL: $script:fail"
if ($script:fail -gt 0) { exit 1 }
exit 0
