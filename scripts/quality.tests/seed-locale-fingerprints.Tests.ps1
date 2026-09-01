#requires -Version 7.0
<#
.SYNOPSIS
    S2327: tests that seed-locale-tranche.ps1 records provenance for the text it writes.

.DESCRIPTION
    Writing a locale file is half of translating a key; the other half is recording which English
    text the translation answers. Without it list-new-lexemes.ps1 reports a fully translated key as
    untranslated, and the round trip sends it to the translator again.

    Everything here runs against a fixture module under temp/scratch and a scratch registry, so the
    shipped 4 MB store is never opened for writing by a test.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every assertion passed.
      1  at least one assertion failed.
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

$moduleRel = 'temp/scratch/s2327-fixture'
$fixtureRoot = Join-Path $repoRoot $moduleRel
$valuesDir = Join-Path $fixtureRoot 'src/main/res/values'
$sourceFile = 'strings_s2327.xml'
$storePath = Join-Path $repoRoot 'temp/scratch/s2327-fingerprints.json'
$mapDir = Join-Path $repoRoot 'temp/scratch/s2327-maps'
$seeder = Join-Path $repoRoot 'scripts/utils/seed-locale-tranche.ps1'
$pwshExe = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
$utf8 = [System.Text.UTF8Encoding]::new($false)

if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
if (Test-Path -LiteralPath $mapDir) { Remove-Item -LiteralPath $mapDir -Recurse -Force }
New-Item -ItemType Directory -Path $valuesDir -Force | Out-Null
New-Item -ItemType Directory -Path $mapDir -Force | Out-Null

# s2327_quoted carries AAPT's backslash escape on purpose: the recorded hash must be taken from the
# decoded plain text the exporter puts in its `en` field, not from the raw element body.
$source = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="s2327_plain">Refresh now</string>
    <string name="s2327_quoted">It\'s here</string>
    <string name="s2327_fmt">Found %1$s items</string>
    <plurals name="s2327_files">
        <item quantity="one">%1$d file</item>
        <item quantity="other">%1$d files</item>
    </plurals>
</resources>
'@
[System.IO.File]::WriteAllText((Join-Path $valuesDir $sourceFile), $source, $utf8)

function Write-Map {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][hashtable]$Map)
    $path = Join-Path $mapDir "$Name.json"
    [System.IO.File]::WriteAllText($path, ($Map | ConvertTo-Json -Depth 5), $utf8)
    return $path
}

function Invoke-Seeder {
    param([Parameter(Mandatory)][string]$MapPath, [switch]$Merge, [switch]$DryRun, [string]$Store = $storePath)
    $seedArgs = @(
        '-NoProfile', '-File', $seeder,
        '-Module', $moduleRel, '-SourceFile', $sourceFile, '-Locale', 'de',
        '-MapPath', $MapPath, '-FingerprintsPath', $Store
    )
    if ($Merge) { $seedArgs += '-Merge' }
    if ($DryRun) { $seedArgs += '-DryRun' }
    & $pwshExe @seedArgs | Out-Null
    return $LASTEXITCODE
}

function Get-Identity {
    param([Parameter(Mandatory)][string]$Key, [string]$Slot)
    return (Get-LocaleUnitId -Module $moduleRel -Set 'main' -File $sourceFile -Key $Key -Slot $Slot)
}

function Get-StoredHash {
    param([Parameter(Mandatory)][string]$Identity, [string]$Store = $storePath)
    $fp = Get-LocaleSourceFingerprints -Path $Store
    if (-not $fp.ContainsKey('de')) { return '' }
    if (-not $fp['de'].ContainsKey($Identity)) { return '' }
    return $fp['de'][$Identity]
}

Write-Host "`n=== Test 1: a seeded key is recorded with its English fingerprint ===" -ForegroundColor Cyan
if (Test-Path -LiteralPath $storePath) { Remove-Item -LiteralPath $storePath -Force }
$map1 = Write-Map -Name 'run1' -Map @{ s2327_plain = 'Jetzt aktualisieren' }
$exit1 = Invoke-Seeder -MapPath $map1
Assert-Equal "Seeding a single key exits 0" 0 $exit1
Assert-Equal "The seeded key has a fingerprint" `
(Get-EnglishStringFingerprint -Text 'Refresh now') `
(Get-StoredHash -Identity (Get-Identity -Key 's2327_plain'))

Write-Host "`n=== Test 2: the hash is of the decoded text, not the raw element body ===" -ForegroundColor Cyan
$map2 = Write-Map -Name 'run2' -Map @{ s2327_quoted = 'Es ist hier' }
$exit2 = Invoke-Seeder -MapPath $map2 -Merge
Assert-Equal "Seeding an escaped-source key exits 0" 0 $exit2
# What the exporter would record as `en` for this element, reached through the shared normalizer.
Assert-Equal "Fingerprint matches the exporter's plain text" `
(Get-EnglishStringFingerprint -Text (ConvertFrom-ResourceBody 'It\''s here')) `
(Get-StoredHash -Identity (Get-Identity -Key 's2327_quoted'))
Assert-Equal "The raw body would have hashed differently" $false `
((Get-EnglishStringFingerprint -Text 'It\''s here') -eq (Get-StoredHash -Identity (Get-Identity -Key 's2327_quoted')))

Write-Host "`n=== Test 3: a -Merge passthrough keeps the provenance it already had ===" -ForegroundColor Cyan
# Run 2 carried s2327_plain through without translating it again. Its stamp must be the one run 1
# wrote, so a sentinel planted between the runs has to survive: re-stamping a carried entry is how
# stale text would be recorded as fresh.
$sentinel = 'deadbeefdeadbeef'
$store = Get-LocaleSourceFingerprints -Path $storePath
Update-LocaleSourceFingerprint -Fingerprints $store -Locale 'de' -Identity (Get-Identity -Key 's2327_plain') -Hash $sentinel
Save-LocaleSourceFingerprints -Fingerprints $store -Path $storePath
$map3 = Write-Map -Name 'run3' -Map @{ s2327_quoted = 'Es ist hier' }
$exit3 = Invoke-Seeder -MapPath $map3 -Merge
Assert-Equal "A merge run that omits the key exits 0" 0 $exit3
Assert-Equal "The carried key was not re-stamped" $sentinel (Get-StoredHash -Identity (Get-Identity -Key 's2327_plain'))

Write-Host "`n=== Test 4: a rejected translation is not recorded ===" -ForegroundColor Cyan
$map4 = Write-Map -Name 'run4' -Map @{ s2327_fmt = 'Elemente gefunden' }
$exit4 = Invoke-Seeder -MapPath $map4 -Merge
Assert-Equal "A placeholder mismatch exits 3" 3 $exit4
Assert-Equal "The rejected key has no fingerprint" '' (Get-StoredHash -Identity (Get-Identity -Key 's2327_fmt'))

Write-Host "`n=== Test 5: plurals are recorded per quantity slot ===" -ForegroundColor Cyan
$map5 = Write-Map -Name 'run5' -Map @{ s2327_files = @{ one = '%1$d Datei'; other = '%1$d Dateien' } }
$exit5 = Invoke-Seeder -MapPath $map5 -Merge
Assert-Equal "Seeding a plurals exits 0" 0 $exit5
Assert-Equal "The 'one' slot is recorded" (Get-EnglishStringFingerprint -Text '%1$d file') `
(Get-StoredHash -Identity (Get-Identity -Key 's2327_files' -Slot 'one'))
Assert-Equal "The 'other' slot is recorded" (Get-EnglishStringFingerprint -Text '%1$d files') `
(Get-StoredHash -Identity (Get-Identity -Key 's2327_files' -Slot 'other'))

Write-Host "`n=== Test 6: -DryRun writes no registry ===" -ForegroundColor Cyan
$dryStore = Join-Path $repoRoot 'temp/scratch/s2327-fingerprints-dry.json'
if (Test-Path -LiteralPath $dryStore) { Remove-Item -LiteralPath $dryStore -Force }
$map6 = Write-Map -Name 'run6' -Map @{ s2327_plain = 'Jetzt aktualisieren' }
$exit6 = Invoke-Seeder -MapPath $map6 -DryRun -Store $dryStore
Assert-Equal "-DryRun exits 0" 0 $exit6
Assert-Equal "-DryRun created no registry file" $false (Test-Path -LiteralPath $dryStore)

Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
Remove-Item -LiteralPath $mapDir -Recurse -Force
if (Test-Path -LiteralPath $storePath) { Remove-Item -LiteralPath $storePath -Force }

Write-Host ("`nTOTAL | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
