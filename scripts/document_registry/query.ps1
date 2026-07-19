<#
.SYNOPSIS
    Query the document registry by text, product area, trigger, or publication state.

.DESCRIPTION
    Exit codes: 0 = matches printed, 1 = no matches, 2 = invalid invocation.
#>
[CmdletBinding()]
param(
    [string] $Text,
    [string] $ProductArea,
    [string] $Trigger,
    [ValidateSet('any', 'public', 'internal')]
    [string] $Publication = 'any',
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

try {
    $registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
    if (-not (Test-Path -LiteralPath $registryPath)) {
        throw "Registry not found: $registryPath"
    }
    $records = @(Get-Content -LiteralPath $registryPath -Encoding utf8 | Where-Object { $_.Trim() } |
        ForEach-Object { $_ | ConvertFrom-Json })
    $matches = @($records | Where-Object {
        $record = $_
        $haystack = (($record.id, $record.title, $record.category, $record.audience,
                $record.product_areas, $record.update_triggers, $record.paths) -join ' ').ToLowerInvariant()
        $textMatch = -not $Text -or $haystack.Contains($Text.ToLowerInvariant())
        $areaMatch = -not $ProductArea -or $record.product_areas -contains $ProductArea
        $triggerMatch = -not $Trigger -or $record.update_triggers -contains $Trigger
        $publicationMatch = $Publication -eq 'any' -or
            ($Publication -eq 'public' -and $record.published) -or
            ($Publication -eq 'internal' -and -not $record.published)
        $textMatch -and $areaMatch -and $triggerMatch -and $publicationMatch
    })
    if ($matches.Count -eq 0) {
        Write-Host 'No document registry records matched.' -ForegroundColor Yellow
        exit 1
    }
    $matches | Sort-Object id | ForEach-Object {
        $areas = $_.product_areas -join ','
        $triggers = $_.update_triggers -join ','
        Write-Output ("{0} | {1} | areas={2} | triggers={3}" -f $_.id, $_.title, $areas, $triggers)
    }
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 2
}
