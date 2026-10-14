<#
.SYNOPSIS
    Quality gate: every active feature is mapped to a documentation page that exists and is published.

.DESCRIPTION
    Part of S2945 (Documentation Corpus Foundation). Checks docs/coverage-manifest.jsonl against
    docs/ALL_FEATURES*.jsonl and docs/docs-pages-manifest.jsonl.

    Refused:
      - an active feature with no coverage row;
      - a non-excluded row with no page_id, or a page_id the page manifest does not know;
      - an excluded row with no exclusion_reason;
      - (S4102) a non-excluded row whose page is retired (is_published false), or whose page file is
        missing on disk in any of the core three locales - the canonical path and its -ru / -uk
        siblings (SITE-STRUCTURE rule 12);
      - (S4102) a removed feature without a coverage row, or mapped as live (not excluded, or its
        row's status is not removed), so a removed capability is never counted as documented
        coverage (SITE-STRUCTURE rule 13);
      - (S4102) a coverage row whose feature is in neither inventory.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; a test passes a fixture tree.

.PARAMETER VerboseOutput
    Print the per-ticket and per-area breakdown even with -SummaryOnly.

.PARAMETER SummaryOnly
    Print the totals and the verdict only.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-docs-coverage.ps1 -SummaryOnly

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. Runs from assert-release-scope-gates.ps1.

    Exit codes (CLAUDE.md Rule 7):
      0 - every check passed.
      1 - at least one finding, or the coverage or page manifest is missing.
#>

[CmdletBinding()]
param (
    [string]$Root,
    [switch]$VerboseOutput,
    [switch]$SummaryOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = if ($Root) { (Resolve-Path -LiteralPath $Root).Path } else { (Resolve-Path "$PSScriptRoot/../..").Path }
$docsDir = Join-Path $repoRoot 'docs'

$mainFeaturesPath = Join-Path $docsDir 'ALL_FEATURES.jsonl'
$noLegalFeaturesPath = Join-Path $docsDir 'ALL_FEATURES_noLegal.jsonl'
$coverageManifestPath = Join-Path $docsDir 'coverage-manifest.jsonl'
$pageManifestPath = Join-Path $docsDir 'docs-pages-manifest.jsonl'

if (-not (Test-Path $coverageManifestPath)) {
    Write-Error "assert-docs-coverage: Coverage manifest missing at $coverageManifestPath"
    exit 1
}

if (-not (Test-Path $pageManifestPath)) {
    Write-Error "assert-docs-coverage: Page manifest missing at $pageManifestPath"
    exit 1
}

# 1. Load the feature inventory: active records to be covered, removed ones to be excluded.
$features = @{}
$removedFeatures = @{}
$knownFeatures = @{}
foreach ($inventoryPath in @($mainFeaturesPath, $noLegalFeaturesPath)) {
    if (-not (Test-Path $inventoryPath)) { continue }
    Get-Content $inventoryPath | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $f = ConvertFrom-Json $_
            $status = if ($f.status) { $f.status } else { "active" }
            $knownFeatures[$f.id] = $true
            if ($status -eq "active") {
                $features[$f.id] = $f
            } elseif ($status -eq "removed") {
                $removedFeatures[$f.id] = $f
            }
        }
    }
}

# 2. Load page manifest
$pages = @{}
Get-Content $pageManifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $p = ConvertFrom-Json $_
        if ($p.page_id) {
            $pages[$p.page_id] = $p
        }
    }
}

# A page file is checked once however many rows map to it.
$pageFileVerdict = @{}
function Get-PageFileFinding([object]$page) {
    if ($pageFileVerdict.ContainsKey($page.page_id)) { return $pageFileVerdict[$page.page_id] }
    $finding = $null
    if ($page.is_published -eq $false) {
        $finding = "page '$($page.page_id)' is retired (is_published false)"
    } elseif ([string]::IsNullOrWhiteSpace($page.canonical_path)) {
        $finding = "page '$($page.page_id)' has no canonical_path"
    } else {
        $missing = [System.Collections.Generic.List[string]]::new()
        $base = $page.canonical_path -replace '\.html$', ''
        foreach ($rel in @($page.canonical_path, "$base-ru.html", "$base-uk.html")) {
            if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $rel) -PathType Leaf)) { $missing.Add($rel) }
        }
        if ($missing.Count -gt 0) { $finding = "page '$($page.page_id)' file missing: $($missing -join ', ')" }
    }
    $pageFileVerdict[$page.page_id] = $finding
    return $finding
}

# 3. Load coverage manifest and match
$coverageEntries = @{}
$coverageByTicket = @{}
$coverageByArea = @{}
$errors = [System.Collections.Generic.List[string]]::new()

Get-Content $coverageManifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $c = ConvertFrom-Json $_
        $featId = $c.feature_id
        $coverageEntries[$featId] = $c

        if (-not $knownFeatures.ContainsKey($featId)) {
            $errors.Add("Coverage row '$featId' names a feature in neither inventory.")
        }

        if ($removedFeatures.ContainsKey($featId) -and (-not $c.is_excluded -or $c.status -ne 'removed')) {
            $errors.Add("Feature '$featId' is removed but its coverage row is not excluded with status removed.")
        }

        # Validate page_id exists in page manifest unless excluded
        if (-not $c.is_excluded) {
            if ([string]::IsNullOrWhiteSpace($c.page_id)) {
                $errors.Add("Feature '$featId' is active but has no page_id assigned.")
            } elseif (-not $pages.ContainsKey($c.page_id)) {
                $errors.Add("Feature '$featId' references unknown page_id '$($c.page_id)'.")
            } else {
                $pageFinding = Get-PageFileFinding $pages[$c.page_id]
                if ($pageFinding) { $errors.Add("Feature '$featId': $pageFinding.") }
            }
        } else {
            if ([string]::IsNullOrWhiteSpace($c.exclusion_reason)) {
                $errors.Add("Feature '$featId' is excluded but has no exclusion_reason.")
            }
        }

        # Track ticket & area metrics
        $t = if ($c.assigned_ticket) { $c.assigned_ticket } else { "Unassigned" }
        if (-not $coverageByTicket.ContainsKey($t)) { $coverageByTicket[$t] = 0 }
        $coverageByTicket[$t]++

        $a = if ($c.area) { $c.area } else { "Unknown" }
        if (-not $coverageByArea.ContainsKey($a)) { $coverageByArea[$a] = 0 }
        $coverageByArea[$a]++
    }
}

# 4. Check for unmapped active and removed features
foreach ($id in $features.Keys) {
    if (-not $coverageEntries.ContainsKey($id)) {
        $errors.Add("Active feature '$id' is missing from coverage-manifest.jsonl.")
    }
}
foreach ($id in $removedFeatures.Keys) {
    if (-not $coverageEntries.ContainsKey($id)) {
        $errors.Add("Removed feature '$id' has no coverage row marking it excluded.")
    }
}

# 5. Output report
Write-Host "=== Documentation Feature Coverage Report ===" -ForegroundColor Cyan
Write-Host "Total active features in inventory: $($features.Count)"
Write-Host "Total removed features in inventory: $($removedFeatures.Count)"
Write-Host "Total entries in coverage manifest: $($coverageEntries.Count)"
Write-Host "Total planned documentation pages:  $($pages.Count)"
Write-Host "Page files checked (core three):    $($pageFileVerdict.Count)"

if ($VerboseOutput -or -not $SummaryOnly) {
    Write-Host "`n--- Coverage by Thematic Ticket ---" -ForegroundColor Yellow
    foreach ($t in ($coverageByTicket.Keys | Sort-Object)) {
        Write-Host "  $t : $($coverageByTicket[$t]) features"
    }

    Write-Host "`n--- Coverage by Feature Area ---" -ForegroundColor Yellow
    foreach ($a in ($coverageByArea.Keys | Sort-Object)) {
        Write-Host "  $a : $($coverageByArea[$a]) features"
    }
}

if ($errors.Count -gt 0) {
    Write-Host "`nassert-docs-coverage: FAILED ($($errors.Count) errors found):" -ForegroundColor Red
    foreach ($err in $errors | Select-Object -First 20) {
        Write-Host "  - $err" -ForegroundColor Red
    }
    if ($errors.Count -gt 20) {
        Write-Host "  .. and $($errors.Count - 20) more errors." -ForegroundColor Red
    }
    exit 1
}

Write-Host "`nassert-docs-coverage: PASS (100% coverage verified, 0 errors)" -ForegroundColor Green
exit 0
