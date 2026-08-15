#requires -Version 7.0
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/doc-icon-gate-routing.ps1')

foreach ($path in @(
    'docs/icons/doc-icon-map.json',
    'docs/icons/doc/folder.png',
    'scripts/docs/export-doc-icon-pngs.ps1',
    'scripts/docs/lib/vectordrawable-svg.ps1',
    'index-ru.html',
    'docs/howto/index-uk.md',
    'docs/DOCS_MAP.md',
    'docs/SETTINGS_REFERENCE_RU.md'
)) {
    if (-not (Test-DocIconGateRoute -ChangedFiles @($path))) {
        throw "Expected icon-gate route for $path."
    }
}

foreach ($path in @(
    'docs/README.md',
    'docs/BUILD_TEST_FAST_PATH.md',
    'app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt',
    'scripts/document_registry/generate.ps1'
)) {
    if (Test-DocIconGateRoute -ChangedFiles @($path)) {
        throw "Unexpected icon-gate route for $path."
    }
}

if (-not (Test-DocIconGateRoute -ChangedFiles @('docs/README.md', 'docs/icons/doc-icon-map.json'))) {
    throw 'Expected the changed set to route when one path is relevant.'
}

Write-Output 'doc-icon-gate routing tests: PASS (8 included, 4 excluded, changed-set coverage)'
