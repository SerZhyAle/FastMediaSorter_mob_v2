#requires -Version 7.0
<#
.SYNOPSIS
    Contract gate: every language and country cell of the published stream catalog is a fixed point of
    the publisher's fold (STREAM-BANK 2.3, amendment O).

.DESCRIPTION
    The catalog's `language` column holds names of existing languages from a closed vocabulary, one per
    language, and its `country` column holds officially assigned ISO 3166-1 alpha-2 codes or nothing. The
    fold that produces those values lives in scripts/streams/modules/StreamPublisher.Facets.ps1 and this
    gate holds no copy of any table: it runs each distinct cell through that fold and reports every cell
    the fold would change, with the number of rows it sits on and what the fold turns it into.

    A cell that is not a fixed point means the file was written by a collection run or an edit that
    skipped the fold; the fix is the rewrite mode of scripts/streams/collect-stream-candidates.ps1
    (-NormalizeFacets, no -Publish), never an edit of the CSV by hand.

.PARAMETER CatalogPath
    The catalog CSV. Defaults to delivery/stream-catalog/streams.csv under the repository root.

.PARAMETER RepoRoot
    The repository root the facet module is read from. Defaults to the root this script sits under.

.PARAMETER Quiet
    Print only the verdict line, not the per-value offenders.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every language and country cell is a fixed point of the fold.
      1  at least one cell is not; the offenders are printed.
      2  invalid input: the catalog has no `language` or `country` column, or cannot be parsed.
      3  could not verify: the catalog file or the facet module is absent.
#>
[CmdletBinding()]
param(
    [string]$CatalogPath,
    [string]$RepoRoot,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not $CatalogPath) { $CatalogPath = Join-Path $RepoRoot 'delivery/stream-catalog/streams.csv' }
$modulePath = Join-Path $RepoRoot 'scripts/streams/modules/StreamPublisher.Facets.ps1'

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'streams'; scope = 'catalog-facets'; files = 'delivery/stream-catalog/streams.csv' })

if (-not (Test-Path -LiteralPath $CatalogPath)) {
    Write-Host "assert-stream-facet-values: COULD NOT VERIFY - catalog not found at $CatalogPath"
    exit 3
}
if (-not (Test-Path -LiteralPath $modulePath)) {
    Write-Host "assert-stream-facet-values: COULD NOT VERIFY - facet module not found at $modulePath"
    exit 3
}

. $modulePath

try {
    $rows = @(Import-Csv -LiteralPath $CatalogPath -Encoding utf8)
} catch {
    Write-Host "assert-stream-facet-values: invalid input - the catalog cannot be parsed: $($_.Exception.Message)"
    exit 2
}
if ($rows.Count -eq 0 -or -not ($rows[0].PSObject.Properties.Name -contains 'language') -or
    -not ($rows[0].PSObject.Properties.Name -contains 'country')) {
    Write-Host 'assert-stream-facet-values: invalid input - the catalog has no language or country column'
    exit 2
}

$offenders = [System.Collections.Generic.List[object]]::new()
foreach ($facet in @(
        @{ Name = 'language'; Fold = { param($v) Get-CanonicalLanguages -Languages $v } },
        @{ Name = 'country'; Fold = { param($v) Get-CanonicalCountry -Country $v } })) {
    $groups = $rows | Where-Object { $_.($facet.Name) -and $_.($facet.Name).Trim() } |
        Group-Object -Property $facet.Name
    foreach ($group in $groups) {
        $folded = & $facet.Fold $group.Name
        if ($folded -cne $group.Name) {
            $offenders.Add([pscustomobject]@{
                    Facet = $facet.Name; Value = $group.Name; Rows = $group.Count; Folds = $folded
                })
        }
    }
}

if ($offenders.Count -eq 0) {
    Write-Host ("assert-stream-facet-values: PASS - {0} row(s), every language and country cell is a fixed point of the fold." -f $rows.Count)
    exit 0
}

if (-not $Quiet) {
    foreach ($o in ($offenders | Sort-Object Facet, @{ Expression = 'Rows'; Descending = $true })) {
        Write-Host ("  {0}: '{1}' on {2} row(s) folds to '{3}'" -f $o.Facet, $o.Value, $o.Rows, $o.Folds)
    }
}
Write-Host ("assert-stream-facet-values: FAIL - {0} distinct cell(s) are not fixed points; run collect-stream-candidates.ps1 -NormalizeFacets (no -Publish) and review its move report." -f $offenders.Count)
exit 1
