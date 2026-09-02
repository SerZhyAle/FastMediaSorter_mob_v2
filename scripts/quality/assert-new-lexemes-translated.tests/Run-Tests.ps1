#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for module-isolated new-lexeme remedy artifacts (S2362).

.NOTES
    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$sandbox = Join-Path $repoRoot 'temp/S2362/assert-new-lexemes-translated-tests'
if (Test-Path -LiteralPath $sandbox) {
    Remove-Item -LiteralPath $sandbox -Recurse -Force
}

$qualityDir = Join-Path $sandbox 'scripts/quality'
$libDir = Join-Path $qualityDir 'lib'
$utilsDir = Join-Path $sandbox 'scripts/utils'
New-Item -ItemType Directory -Path $libDir, $utilsDir -Force | Out-Null

Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/quality/assert-new-lexemes-translated.ps1') `
    -Destination $qualityDir
Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1') `
    -Destination $libDir

$fakeProducer = @'
[CmdletBinding()]
param(
    [string]$Module,
    [string[]]$SourceSet,
    [string]$BaselinePath,
    [string]$FingerprintsPath,
    [string]$OutDir,
    [switch]$Quiet
)

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
$textPath = Join-Path $OutDir 'new_lexemes_en.txt'
$indexPath = Join-Path $OutDir 'new_lexemes_index.jsonl'
if ($Module -eq 'app_v2') {
    [System.IO.File]::WriteAllText($textPath, "Phone source text`n", $utf8)
    $record = '{"line":1,"set":"main","file":"strings.xml","kind":"string","key":"phone_only","slot":"","formats":"","en":"Phone source text"}'
    [System.IO.File]::WriteAllText($indexPath, "$record`n", $utf8)
    exit 3
}

[System.IO.File]::WriteAllText($textPath, '', $utf8)
[System.IO.File]::WriteAllText($indexPath, '', $utf8)
exit 0
'@
[System.IO.File]::WriteAllText(
    (Join-Path $utilsDir 'list-new-lexemes.ps1'),
    $fakeProducer,
    [System.Text.UTF8Encoding]::new($false)
)

$baseline = Join-Path $qualityDir 'locale-untranslated-baseline.txt'
$fingerprints = Join-Path $qualityDir 'locale-source-fingerprints.json'
[System.IO.File]::WriteAllText($baseline, '', [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($fingerprints, '', [System.Text.UTF8Encoding]::new($false))

$gate = Join-Path $qualityDir 'assert-new-lexemes-translated.ps1'
& pwsh -NoProfile -File $gate -Module app_v2 -BaselinePath $baseline -FingerprintsPath $fingerprints -Quiet
$phoneExit = $LASTEXITCODE
& pwsh -NoProfile -File $gate -Module wear -BaselinePath $baseline -FingerprintsPath $fingerprints -Quiet
$wearExit = $LASTEXITCODE
$explicitOut = Join-Path $sandbox 'explicit-output'
& pwsh -NoProfile -File $gate -Module app_v2 -BaselinePath $baseline -FingerprintsPath $fingerprints `
    -OutDir $explicitOut -Quiet
$explicitExit = $LASTEXITCODE

$phoneText = Join-Path $sandbox 'temp/S1627/app_v2/new_lexemes_en.txt'
$phoneIndex = Join-Path $sandbox 'temp/S1627/app_v2/new_lexemes_index.jsonl'
$wearText = Join-Path $sandbox 'temp/S1627/wear/new_lexemes_en.txt'
$wearIndex = Join-Path $sandbox 'temp/S1627/wear/new_lexemes_index.jsonl'

$failures = [System.Collections.Generic.List[string]]::new()
if ($phoneExit -ne 1) { $failures.Add("phone gate exit: expected 1, actual $phoneExit") }
if ($wearExit -ne 0) { $failures.Add("wear gate exit: expected 0, actual $wearExit") }
if ($explicitExit -ne 1) { $failures.Add("explicit output gate exit: expected 1, actual $explicitExit") }
if (-not (Test-Path -LiteralPath $phoneText)) { $failures.Add("missing phone text artifact: $phoneText") }
if (-not (Test-Path -LiteralPath $phoneIndex)) { $failures.Add("missing phone index artifact: $phoneIndex") }
if (-not (Test-Path -LiteralPath $wearText)) { $failures.Add("missing wear text artifact: $wearText") }
if (-not (Test-Path -LiteralPath $wearIndex)) { $failures.Add("missing wear index artifact: $wearIndex") }
if ((Test-Path -LiteralPath $phoneText) -and (Get-Item -LiteralPath $phoneText).Length -eq 0) {
    $failures.Add('the clean wear pass truncated the phone text artifact')
}
if ((Test-Path -LiteralPath $phoneIndex) -and (Get-Item -LiteralPath $phoneIndex).Length -eq 0) {
    $failures.Add('the clean wear pass truncated the phone index artifact')
}
if ((Test-Path -LiteralPath $wearText) -and (Get-Item -LiteralPath $wearText).Length -ne 0) {
    $failures.Add('the wear text artifact is not isolated and empty')
}
if (-not (Test-Path -LiteralPath (Join-Path $explicitOut 'new_lexemes_en.txt'))) {
    $failures.Add('an explicit -OutDir was not preserved as the artifact directory')
}
if (Test-Path -LiteralPath (Join-Path $explicitOut 'app_v2/new_lexemes_en.txt')) {
    $failures.Add('the module was incorrectly appended to an explicit -OutDir')
}

$producerSource = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/utils/list-new-lexemes.ps1') -Raw
if ($producerSource -notmatch 'temp/S1627/\$Module') {
    $failures.Add('list-new-lexemes.ps1 does not module-qualify its default output directory')
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Host "FAIL: $failure" -ForegroundColor Red }
    exit 1
}

Write-Host 'assert-new-lexemes-translated tests: PASS - phone and wear remedy artifacts remain isolated.' -ForegroundColor Green
exit 0
