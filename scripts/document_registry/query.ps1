<#
.SYNOPSIS
    Query the document registry by text, product area, trigger, or publication state.

.DESCRIPTION
    "No records matched" is a normal answer to a query, not a failure, so it exits 0 like any
    other successful lookup. Non-zero is reserved for the query not being answerable at all.

    A query that misses is answered, never left empty (S1597). An exact miss on -ProductArea /
    -Trigger is retried through a resolution ladder - case, singular/plural and separator form,
    then the opposite facet, then substring - and the applied resolution is printed above the
    matches so a substituted query can never read as an exact hit. Still nothing: the vocabulary
    of both facets is printed, so the caller can re-query in the same turn. The vocabulary is
    derived from the registry itself, never from a list held in this script.

    Exit codes: 0 = query answered (matches printed, or the no-match message plus vocabulary),
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
    # Print the known product areas and update triggers, then exit without searching.
    [switch] $ListVocabulary,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/document_registry/query.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'

function Get-Vocabulary {
    param([object[]] $Records, [string] $Field)
    $counts = @{}
    foreach ($record in $Records) {
        foreach ($value in @($record.$Field)) {
            if (-not $value) { continue }
            $counts[$value] = 1 + [int]$counts[$value]
        }
    }
    $counts
}

function Format-Vocabulary {
    param([hashtable] $Counts)
    (($Counts.Keys | Sort-Object | ForEach-Object { "$_($($Counts[$_]))" }) -join ', ')
}

function Get-NormalForm {
    param([string] $Value)
    # Fold case, treat spaces/underscores as hyphens, and drop a trailing plural 's' so
    # 'Device Test', 'device_test' and 'spec' all reduce onto the same key as their
    # registry spelling. Deliberately crude: the vocabulary is ~30 short kebab-case words.
    $folded = $Value.Trim().ToLowerInvariant() -replace '[\s_]+', '-'
    $folded -replace 's$', ''
}

function Resolve-FacetValue {
    param([string] $Value, [hashtable] $Primary, [hashtable] $Secondary, [string] $SecondaryName)
    $normal = Get-NormalForm $Value
    foreach ($candidate in ($Primary.Keys | Sort-Object)) {
        if ((Get-NormalForm $candidate) -eq $normal) {
            return [pscustomobject]@{ Facet = 'primary'; Value = $candidate; How = 'form' }
        }
    }
    foreach ($candidate in ($Secondary.Keys | Sort-Object)) {
        if ((Get-NormalForm $candidate) -eq $normal) {
            return [pscustomobject]@{ Facet = 'secondary'; Value = $candidate; How = $SecondaryName }
        }
    }
    foreach ($candidate in ($Primary.Keys | Sort-Object)) {
        if ((Get-NormalForm $candidate).Contains($normal) -or $normal.Contains((Get-NormalForm $candidate))) {
            return [pscustomobject]@{ Facet = 'primary'; Value = $candidate; How = 'substring' }
        }
    }
    foreach ($candidate in ($Secondary.Keys | Sort-Object)) {
        if ((Get-NormalForm $candidate).Contains($normal) -or $normal.Contains((Get-NormalForm $candidate))) {
            return [pscustomobject]@{ Facet = 'secondary'; Value = $candidate; How = $SecondaryName }
        }
    }
    return $null
}

try {
    $registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
    if (-not (Test-Path -LiteralPath $registryPath)) {
        throw "Registry not found: $registryPath"
    }
    $records = @(Get-Content -LiteralPath $registryPath -Encoding utf8 | Where-Object { $_.Trim() } |
        ForEach-Object { $_ | ConvertFrom-Json })

    $areaVocabulary = Get-Vocabulary -Records $records -Field 'product_areas'
    $triggerVocabulary = Get-Vocabulary -Records $records -Field 'update_triggers'
    $vocabularyLines = @(
        ("areas={0}" -f (Format-Vocabulary $areaVocabulary)),
        ("triggers={0}" -f (Format-Vocabulary $triggerVocabulary))
    )

    if ($ListVocabulary) {
        $vocabularyLines | ForEach-Object { Write-Output $_ }
        exit 0
    }

    $findMatches = {
        param([string] $Area, [string] $Trig)
        @($records | Where-Object {
            $record = $_
            $haystack = (($record.id, $record.title, $record.category, $record.audience,
                    $record.product_areas, $record.update_triggers, $record.paths) -join ' ').ToLowerInvariant()
            $textMatch = -not $Query -or $haystack.Contains($Query.ToLowerInvariant())
            $areaMatch = -not $Area -or $record.product_areas -contains $Area
            $triggerMatch = -not $Trig -or $record.update_triggers -contains $Trig
            $publicationMatch = $Publication -eq 'any' -or
                ($Publication -eq 'public' -and $record.published) -or
                ($Publication -eq 'internal' -and -not $record.published)
            $textMatch -and $areaMatch -and $triggerMatch -and $publicationMatch
        })
    }

    $hits = & $findMatches $ProductArea $Trigger
    $resolutionLines = @()

    if ($hits.Count -eq 0 -and ($ProductArea -or $Trigger)) {
        $resolvedArea = $ProductArea
        $resolvedTrigger = $Trigger
        if ($ProductArea) {
            $hit = Resolve-FacetValue -Value $ProductArea -Primary $areaVocabulary -Secondary $triggerVocabulary -SecondaryName 'trigger'
            if ($hit) {
                if ($hit.Facet -eq 'primary') {
                    $resolvedArea = $hit.Value
                    $resolutionLines += "resolved: -ProductArea '$ProductArea' -> area '$($hit.Value)'"
                } else {
                    $resolvedArea = ''
                    $resolvedTrigger = $hit.Value
                    $resolutionLines += "resolved: -ProductArea '$ProductArea' -> trigger '$($hit.Value)'"
                }
            }
        }
        if ($Trigger) {
            $hit = Resolve-FacetValue -Value $Trigger -Primary $triggerVocabulary -Secondary $areaVocabulary -SecondaryName 'area'
            if ($hit) {
                if ($hit.Facet -eq 'primary') {
                    $resolvedTrigger = $hit.Value
                    $resolutionLines += "resolved: -Trigger '$Trigger' -> trigger '$($hit.Value)'"
                } else {
                    $resolvedTrigger = ''
                    $resolvedArea = $hit.Value
                    $resolutionLines += "resolved: -Trigger '$Trigger' -> area '$($hit.Value)'"
                }
            }
        }
        if ($resolutionLines.Count -gt 0) {
            $hits = & $findMatches $resolvedArea $resolvedTrigger
        }
    }

    if ($hits.Count -eq 0) {
        Write-Host 'No document registry records matched.' -ForegroundColor Yellow
        Write-Host 'Known values (re-query with one of these):' -ForegroundColor Yellow
        $vocabularyLines | ForEach-Object { Write-Output $_ }
        exit 0
    }
    $resolutionLines | ForEach-Object { Write-Output $_ }
    $hits | Sort-Object id | ForEach-Object {
        $areas = $_.product_areas -join ','
        $triggers = $_.update_triggers -join ','
        Write-Output ("{0} | {1} | areas={2} | triggers={3}" -f $_.id, $_.title, $areas, $triggers)
    }
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 2
}
