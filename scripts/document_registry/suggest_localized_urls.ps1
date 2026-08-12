<#
.SYNOPSIS
    Suggest localized_urls for registry entries by scanning source files for permalink front matter.

.DESCRIPTION
    For each record in docs/DOCUMENT_REGISTRY.jsonl that declares languages but lacks localized_urls,
    the script will scan the patterns in `paths`, read YAML front matter from matching files, and
    collect any `permalink` values. Output is a JSON object mapping record id -> localized_urls map.

    This script does not modify the registry; it only prints suggested mappings for manual review.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Get-PermalinkFromFile {
    param([string]$FilePath)
    if (-not (Test-Path -LiteralPath $FilePath)) { return $null }
    try {
        $lines = Get-Content -LiteralPath $FilePath -Encoding utf8 -TotalCount 200
    } catch { return $null }
    if ($lines.Count -eq 0) { return $null }
    if ($lines[0].Trim() -ne '---') { return $null }
    $fm = @()
    for ($i=1; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq '---') { break }
        $fm += $lines[$i]
    }
    foreach ($l in $fm) {
        if ($l -match 'permalink\s*:\s*(.+)$') {
            $val = $matches[1].Trim()
            $val = $val.Trim([char[]]@([char]39, [char]34))
            return $val
        }
    }
    return $null
}

$registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
if (-not (Test-Path -LiteralPath $registryPath)) { Write-Error "Registry not found: $registryPath" -ErrorAction Continue; exit 2 }
$records = @(Get-Content -LiteralPath $registryPath -Encoding utf8 | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })

$suggestions = @{}
foreach ($record in $records) {
    if (-not $record.published -or -not $record.indexable) { continue }
    if (-not $record.languages -or $record.languages.Count -le 1) { continue }
    if ($record.localized_urls) { continue }
    $constructed = @{}
    foreach ($p in @($record.paths)) {
        $pattern = Join-Path $RepoRoot $p
        $files = @()
        try { $files = Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue } catch { }
        if (-not $files -or $files.Count -eq 0) {
            try { $files = Get-ChildItem -Path $pattern -File -Recurse -ErrorAction SilentlyContinue } catch { }
        }
        foreach ($f in $files) {
            $perm = Get-PermalinkFromFile -FilePath $f.FullName
            if ($perm) {
                $fileName = $f.Name
                $lang = $null
                if ($fileName -imatch '\.ru\.md$' -or $fileName -imatch '_ru\.md$') { $lang = 'ru' }
                elseif ($fileName -imatch '\.uk\.md$' -or $fileName -imatch '_uk\.md$') { $lang = 'uk' }
                elseif ($fileName -imatch '\.en\.md$' -or $fileName -imatch '_en\.md$') { $lang = 'en' }
                elseif ($fileName -imatch '\.md$') { $lang = 'en' }
                if ($lang -and ($record.languages -contains $lang)) { $constructed[$lang] = $perm }
            }
        }
    }
    if ($constructed.Keys.Count -gt 0) { $suggestions[$record.id] = $constructed }
}

# Print suggestions as JSON for easy consumption
Write-Host (ConvertTo-Json $suggestions -Depth 5 -Compress)

# Also print a human-friendly report
foreach ($k in $suggestions.Keys) {
    Write-Host "Record: $k"
    foreach ($lang in $suggestions[$k].Keys) {
        Write-Host "  $lang -> $($suggestions[$k][$lang])"
    }
    Write-Host ""
}

exit 0
