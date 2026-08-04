<#
.SYNOPSIS
    Query the document registry by text, product area, trigger, or publication state.

.DESCRIPTION
    "No records matched" is a normal answer to a query, not a failure, so it exits 0 like any
    other successful lookup. Non-zero is reserved for the query not being answerable at all.

    Exit codes: 0 = query answered (matches printed, or the no-match message),
                2 = invalid invocation / registry unreadable.
    A rejected parameter value (ValidateSet) is refused by the PowerShell host before the body
    runs and surfaces as the host's own exit 1 - that path is outside this script's contract.
#>
[CmdletBinding()]
param(
    # -Query is the canonical free-text parameter shared by the catalog and registry
    # query CLIs; -Text was this script's former spelling and stays as an alias.
    [Alias('Text','Search','Name')]
    [string] $Query,
    [string] $ProductArea,
    [string] $Trigger,
    [ValidateSet('any', 'public', 'internal')]
    [string] $Publication = 'any',
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/document_registry/query.ps1'
    exit $LASTEXITCODE
}

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
        $textMatch = -not $Query -or $haystack.Contains($Query.ToLowerInvariant())
        $areaMatch = -not $ProductArea -or $record.product_areas -contains $ProductArea
        $triggerMatch = -not $Trigger -or $record.update_triggers -contains $Trigger
        $publicationMatch = $Publication -eq 'any' -or
            ($Publication -eq 'public' -and $record.published) -or
            ($Publication -eq 'internal' -and -not $record.published)
        $textMatch -and $areaMatch -and $triggerMatch -and $publicationMatch
    })
    if ($matches.Count -eq 0) {
        Write-Host 'No document registry records matched.' -ForegroundColor Yellow
        exit 0
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
