# requires -Version 7.0
<#
.SYNOPSIS
    S1824: tests for English string fingerprinting and stale translation detection.
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

Write-Host "`n=== Test 1: Get-EnglishStringFingerprint formatting and normalization ===" -ForegroundColor Cyan
$h1 = Get-EnglishStringFingerprint -Text "Refresh"
$h2 = Get-EnglishStringFingerprint -Text "Check channels"
$h1WithWhitespace = Get-EnglishStringFingerprint -Text "  Refresh `r`n`t "

Assert-Equal "Fingerprint length is 16" 16 $h1.Length
Assert-Equal "Fingerprint is lowercase hex" $true ($h1 -match '^[0-9a-f]{16}$')
Assert-Equal "Different texts produce different hashes" $false ($h1 -eq $h2)
Assert-Equal "Whitespace normalization produces identical hash" $h1 $h1WithWhitespace

Write-Host "`n=== Test 2: Store CRUD operations ===" -ForegroundColor Cyan
$tempStorePath = Join-Path $repoRoot 'temp/scratch/test-fps.json'
if (Test-Path -LiteralPath $tempStorePath) { Remove-Item -LiteralPath $tempStorePath -Force }

$store = @{
    'de' = @{
        'app_v2|main|strings.xml|btn_ok' = '1111222233334444'
        'app_v2|main|strings.xml|btn_cancel' = '5555666677778888'
    }
    'es' = @{
        'app_v2|main|strings.xml|btn_ok' = '1111222233334444'
    }
}

Save-LocaleSourceFingerprints -Fingerprints $store -Path $tempStorePath
Assert-Equal "Store file created" $true (Test-Path -LiteralPath $tempStorePath)

$loaded = Get-LocaleSourceFingerprints -Path $tempStorePath
Assert-Equal "Loaded locale count" 2 $loaded.Count
Assert-Equal "Loaded de entry value" '1111222233334444' $loaded['de']['app_v2|main|strings.xml|btn_ok']

Update-LocaleSourceFingerprint -Fingerprints $loaded -Locale 'de' -Identity 'app_v2|main|strings.xml|btn_retry' -Hash '9999aaaabbbbcccc'
Assert-Equal "Updated entry exists" '9999aaaabbbbcccc' $loaded['de']['app_v2|main|strings.xml|btn_retry']

Rename-LocaleSourceFingerprint -Fingerprints $loaded -OldIdentity 'app_v2|main|strings.xml|btn_ok' -NewIdentity 'app_v2|main|strings.xml|btn_confirm'
Assert-Equal "Old identity removed in de" $false $loaded['de'].ContainsKey('app_v2|main|strings.xml|btn_ok')
Assert-Equal "New identity present in de" '1111222233334444' $loaded['de']['app_v2|main|strings.xml|btn_confirm']
Assert-Equal "Old identity removed in es" $false $loaded['es'].ContainsKey('app_v2|main|strings.xml|btn_ok')
Assert-Equal "New identity present in es" '1111222233334444' $loaded['es']['app_v2|main|strings.xml|btn_confirm']

Remove-LocaleSourceFingerprint -Fingerprints $loaded -Identity 'app_v2|main|strings.xml|btn_cancel' -Locale 'de'
Assert-Equal "Removed identity from de" $false $loaded['de'].ContainsKey('app_v2|main|strings.xml|btn_cancel')

if (Test-Path -LiteralPath $tempStorePath) { Remove-Item -LiteralPath $tempStorePath -Force }

Write-Host "`n=== Test 3: Get-LocaleUnitId builds the module-qualified identity (S1858) ===" -ForegroundColor Cyan

Assert-Equal "Identity without a slot" 'wear|main|strings.xml|app_name' (Get-LocaleUnitId -Module wear -Set main -File strings.xml -Key app_name)
Assert-Equal "Identity with a slot" 'app_v2|main|strings.xml|n_items|other' (Get-LocaleUnitId -Module app_v2 -Set main -File strings.xml -Key n_items -Slot other)
Assert-Equal "Empty slot collapses to the short form" 'app_v2|vr|strings.xml|k' (Get-LocaleUnitId -Module app_v2 -Set vr -File strings.xml -Key k -Slot '')

# -Module is mandatory so no caller can rebuild the pre-S1858 format by omitting it.
$moduleOmitted = $false
try { Get-LocaleUnitId -Set main -File strings.xml -Key app_name -ErrorAction Stop | Out-Null }
catch { $moduleOmitted = $true }
Assert-Equal "Omitting -Module is a binding error" $true $moduleOmitted

Write-Host "`n=== Test 4: schema version marker round trip (S1858) ===" -ForegroundColor Cyan
$schemaPath = Join-Path $repoRoot 'temp/scratch/test-fps-schema.json'
if (Test-Path -LiteralPath $schemaPath) { Remove-Item -LiteralPath $schemaPath -Force }

Save-LocaleSourceFingerprints -Fingerprints @{ 'de' = @{ 'wear|main|strings.xml|a' = '1111222233334444' } } -Path $schemaPath
Assert-Equal "Written store declares schema v2" 2 (Get-LocaleFingerprintsSchemaVersion -Path $schemaPath)

$schemaLoaded = Get-LocaleSourceFingerprints -Path $schemaPath
Assert-Equal "Schema marker is not returned as a locale" $false $schemaLoaded.ContainsKey('__schema')
Assert-Equal "Locale count excludes the marker" 1 $schemaLoaded.Count

# A store written before S1858 carries no marker; it must read as v1 so callers can refuse it.
$legacyPath = Join-Path $repoRoot 'temp/scratch/test-fps-legacy.json'
$utf8NoBomTest = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($legacyPath, '{"de":{"main|strings.xml|a":"1111222233334444"}}', $utf8NoBomTest)
Assert-Equal "Markerless store reads as v1" 1 (Get-LocaleFingerprintsSchemaVersion -Path $legacyPath)

foreach ($p in @($schemaPath, $legacyPath)) { if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Force } }

Write-Host "`n=== Test 5: two modules sharing a key name do not collide (S1858) ===" -ForegroundColor Cyan
# The regression this ticket exists for. app_v2 and wear both ship src/main/res/values/strings.xml
# with an app_name of different English text; before the module segment they addressed one slot and
# whichever imported last silently overwrote the other's provenance.
$collisionPath = Join-Path $repoRoot 'temp/scratch/test-fps-collision.json'
if (Test-Path -LiteralPath $collisionPath) { Remove-Item -LiteralPath $collisionPath -Force }

$appHash = Get-EnglishStringFingerprint -Text 'Fast Media Sorter & Organizer'
$wearHash = Get-EnglishStringFingerprint -Text 'FastMedia Wear'
Assert-Equal "The two modules' English texts differ" $false ($appHash -eq $wearHash)

$collisionStore = @{}
$appId = Get-LocaleUnitId -Module app_v2 -Set main -File strings.xml -Key app_name
$wearId = Get-LocaleUnitId -Module wear -Set main -File strings.xml -Key app_name
Update-LocaleSourceFingerprint -Fingerprints $collisionStore -Locale 'de' -Identity $appId -Hash $appHash
Update-LocaleSourceFingerprint -Fingerprints $collisionStore -Locale 'de' -Identity $wearId -Hash $wearHash

Save-LocaleSourceFingerprints -Fingerprints $collisionStore -Path $collisionPath
$collisionLoaded = Get-LocaleSourceFingerprints -Path $collisionPath

Assert-Equal "Both identities survive one store" 2 $collisionLoaded['de'].Count
Assert-Equal "app_v2 keeps its own hash" $appHash $collisionLoaded['de'][$appId]
Assert-Equal "wear keeps its own hash" $wearHash $collisionLoaded['de'][$wearId]

if (Test-Path -LiteralPath $collisionPath) { Remove-Item -LiteralPath $collisionPath -Force }

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "PASS: $script:pass | FAIL: $script:fail"
if ($script:fail -gt 0) { exit 1 }
exit 0
