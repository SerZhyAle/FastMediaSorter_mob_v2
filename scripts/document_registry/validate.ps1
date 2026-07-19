<#
.SYNOPSIS
    Validate docs/DOCUMENT_REGISTRY.jsonl and its registered files.

.DESCRIPTION
    Exit codes: 0 = valid, 1 = validation errors, 2 = invalid invocation or unreadable registry.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Get-Matches {
    param([string] $Pattern)
    $fullPattern = Join-Path $RepoRoot ($Pattern -replace '/', [IO.Path]::DirectorySeparatorChar)
    @(Get-ChildItem -Path $fullPattern -File -ErrorAction SilentlyContinue)
}

try {
    $registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
    if (-not (Test-Path -LiteralPath $registryPath)) { throw "Registry not found: $registryPath" }
    $errors = [System.Collections.Generic.List[string]]::new()
    $records = [System.Collections.Generic.List[object]]::new()
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $registryPath -Encoding utf8) {
        $lineNumber++
        if (-not $line.Trim()) { continue }
        try { $records.Add(($line | ConvertFrom-Json)) } catch { $errors.Add("L${lineNumber}: invalid JSON") }
    }
    $required = @('id', 'title', 'category', 'audience', 'paths', 'published', 'indexable', 'product_areas', 'update_triggers', 'generated')
    $seen = @{}
    foreach ($record in $records) {
        foreach ($field in $required) {
            if (-not ($record.PSObject.Properties.Name -contains $field)) { $errors.Add("$($record.id): missing $field") }
        }
        if ($record.id -notmatch '^[a-z0-9]+(?:-[a-z0-9]+)*$') { $errors.Add("$($record.id): invalid id") }
        if ($seen.ContainsKey($record.id)) { $errors.Add("$($record.id): duplicate id") } else { $seen[$record.id] = $true }
        if ($record.indexable -and (-not $record.published -or -not $record.url)) { $errors.Add("$($record.id): indexable record needs published=true and url") }
        foreach ($relativePath in @($record.paths)) {
            if ($relativePath -match '(^|/|\\)\.\.($|/|\\)' -or [IO.Path]::IsPathRooted($relativePath)) {
                $errors.Add("$($record.id): path escapes repo: $relativePath")
                continue
            }
            if ((Get-Matches -Pattern $relativePath).Count -eq 0) { $errors.Add("$($record.id): no file matches $relativePath") }
        }
    }
    if ($errors.Count -gt 0) {
        Write-Host "Document registry FAILED: $($errors.Count) error(s)" -ForegroundColor Red
        $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        exit 1
    }
    Write-Host "Document registry PASS: $($records.Count) record(s)" -ForegroundColor Green
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 2
}
