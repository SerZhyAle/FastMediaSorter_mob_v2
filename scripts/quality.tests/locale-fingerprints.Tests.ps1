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
        'main|strings.xml|btn_ok' = '1111222233334444'
        'main|strings.xml|btn_cancel' = '5555666677778888'
    }
    'es' = @{
        'main|strings.xml|btn_ok' = '1111222233334444'
    }
}

Save-LocaleSourceFingerprints -Fingerprints $store -Path $tempStorePath
Assert-Equal "Store file created" $true (Test-Path -LiteralPath $tempStorePath)

$loaded = Get-LocaleSourceFingerprints -Path $tempStorePath
Assert-Equal "Loaded locale count" 2 $loaded.Count
Assert-Equal "Loaded de entry value" '1111222233334444' $loaded['de']['main|strings.xml|btn_ok']

Update-LocaleSourceFingerprint -Fingerprints $loaded -Locale 'de' -Identity 'main|strings.xml|btn_retry' -Hash '9999aaaabbbbcccc'
Assert-Equal "Updated entry exists" '9999aaaabbbbcccc' $loaded['de']['main|strings.xml|btn_retry']

Rename-LocaleSourceFingerprint -Fingerprints $loaded -OldIdentity 'main|strings.xml|btn_ok' -NewIdentity 'main|strings.xml|btn_confirm'
Assert-Equal "Old identity removed in de" $false $loaded['de'].ContainsKey('main|strings.xml|btn_ok')
Assert-Equal "New identity present in de" '1111222233334444' $loaded['de']['main|strings.xml|btn_confirm']
Assert-Equal "Old identity removed in es" $false $loaded['es'].ContainsKey('main|strings.xml|btn_ok')
Assert-Equal "New identity present in es" '1111222233334444' $loaded['es']['main|strings.xml|btn_confirm']

Remove-LocaleSourceFingerprint -Fingerprints $loaded -Identity 'main|strings.xml|btn_cancel' -Locale 'de'
Assert-Equal "Removed identity from de" $false $loaded['de'].ContainsKey('main|strings.xml|btn_cancel')

if (Test-Path -LiteralPath $tempStorePath) { Remove-Item -LiteralPath $tempStorePath -Force }

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "PASS: $script:pass | FAIL: $script:fail"
if ($script:fail -gt 0) { exit 1 }
exit 0
