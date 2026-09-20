<#
.SYNOPSIS
    S0440 Phase 02 - settings annotations coverage/parity checker.

.DESCRIPTION
    Cross-checks docs/settings/settings-manifest.json against
    docs/settings/settings-annotations.json. Fails (exit 1) when:
      - either file is not strictly valid JSON,
      - any manifest key is missing from the annotations,
      - any annotation key is orphaned (not in the manifest),
      - any annotation lacks a non-empty en/ru/uk value.
    Manifest keys are deduped (the same view id can appear in more than one
    section). On success exits 0 with a one-line summary. This is both the
    coverage gate and the "index of existing settings documentation"
    deliverable (strategic S0440 goal 2).
#>
param(
    [string] $ManifestPath = (Join-Path $PSScriptRoot '..\..\docs\settings\settings-manifest.json'),
    [string] $AnnotationsPath = (Join-Path $PSScriptRoot '..\..\docs\settings\settings-annotations.json')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $ManifestPath)) { Write-Host "Manifest not found: $ManifestPath" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $AnnotationsPath)) { Write-Host "Annotations not found: $AnnotationsPath" -ForegroundColor Red; exit 1 }

# S3345: ConvertFrom-Json below is tolerant of escapes JSON does not define - it read
# "прив\'язується" as "прив'язується" for years while python json and JSON.parse refused the whole
# file at that byte, so every PowerShell gate passed and no other consumer could open the artifact.
# Parse strictly first, with the parser that says which byte is wrong, and stop before the tolerant
# read hides it again.
function Assert-StrictJson([string] $path) {
    try {
        $doc = [System.Text.Json.JsonDocument]::Parse((Get-Content $path -Raw))
        $doc.Dispose()
    } catch {
        $inner = $_.Exception
        while ($inner.InnerException) { $inner = $inner.InnerException }
        # JsonException.LineNumber is 0-based; report the line an editor shows.
        $where = if ($inner -is [System.Text.Json.JsonException] -and $null -ne $inner.LineNumber) {
            " (file line $($inner.LineNumber + 1))"
        } else { '' }
        Write-Host "INVALID JSON: $path$where" -ForegroundColor Red
        Write-Host "  $($inner.Message)" -ForegroundColor Red
        Write-Host "  PowerShell's ConvertFrom-Json accepts this; python json, JSON.parse and every other strict reader do not." -ForegroundColor Red
        Write-Host "settings annotations: FAIL" -ForegroundColor Red
        exit 1
    }
}

Assert-StrictJson $ManifestPath
Assert-StrictJson $AnnotationsPath

$manifest = (Get-Content $ManifestPath -Raw | ConvertFrom-Json).entries
$annot = Get-Content $AnnotationsPath -Raw | ConvertFrom-Json

# The same view id can appear in multiple sections, so dedup before comparing.
$manifestKeys = $manifest.key | Sort-Object -Unique
$annotKeys = $annot.PSObject.Properties.Name

$missing = @($manifestKeys | Where-Object { $_ -notin $annotKeys })
$orphan  = @($annotKeys | Where-Object { $_ -notin $manifestKeys })

$emptyVals = @()
foreach ($k in $annotKeys) {
    $v = $annot.$k
    if ([string]::IsNullOrWhiteSpace($v.en) -or
        [string]::IsNullOrWhiteSpace($v.ru) -or
        [string]::IsNullOrWhiteSpace($v.uk)) {
        $emptyVals += $k
    }
}

$fail = $false
if ($missing.Count) {
    $fail = $true
    Write-Host "MISSING annotations for $($missing.Count) manifest key(s):" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_" }
}
if ($orphan.Count) {
    $fail = $true
    Write-Host "ORPHAN annotation(s) not in manifest ($($orphan.Count)):" -ForegroundColor Red
    $orphan | ForEach-Object { Write-Host "  $_" }
}
if ($emptyVals.Count) {
    $fail = $true
    Write-Host "EMPTY en/ru/uk for $($emptyVals.Count) key(s):" -ForegroundColor Red
    $emptyVals | ForEach-Object { Write-Host "  $_" }
}

if ($fail) {
    Write-Host "settings annotations: FAIL" -ForegroundColor Red
    exit 1
}

Write-Host "settings annotations: OK - $($manifestKeys.Count) unique keys, all en/ru/uk present, 0 orphans." -ForegroundColor Green
exit 0
