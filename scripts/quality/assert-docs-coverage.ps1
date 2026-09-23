# Quality Gate: Assert 100% Feature Documentation Coverage
# Part of S2945 (Documentation Corpus Foundation)
# Checks docs/coverage-manifest.jsonl against docs/ALL_FEATURES*.jsonl and docs/docs-pages-manifest.jsonl

[CmdletBinding()]
param (
    [switch]$VerboseOutput,
    [switch]$SummaryOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
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

# 1. Load active feature inventory
$features = @{}
if (Test-Path $mainFeaturesPath) {
    Get-Content $mainFeaturesPath | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $f = ConvertFrom-Json $_
            $status = if ($f.status) { $f.status } else { "active" }
            if ($status -eq "active") {
                $features[$f.id] = $f
            }
        }
    }
}

if (Test-Path $noLegalFeaturesPath) {
    Get-Content $noLegalFeaturesPath | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $f = ConvertFrom-Json $_
            $status = if ($f.status) { $f.status } else { "active" }
            if ($status -eq "active") {
                $features[$f.id] = $f
            }
        }
    }
}

# 2. Load page manifest
$validPageIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
Get-Content $pageManifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $p = ConvertFrom-Json $_
        if ($p.page_id) {
            $null = $validPageIds.Add($p.page_id)
        }
    }
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

        # Validate page_id exists in page manifest unless excluded
        if (-not $c.is_excluded) {
            if ([string]::IsNullOrWhiteSpace($c.page_id)) {
                $errors.Add("Feature '$featId' is active but has no page_id assigned.")
            } elseif (-not $validPageIds.Contains($c.page_id)) {
                $errors.Add("Feature '$featId' references unknown page_id '$($c.page_id)'.")
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

# 4. Check for unmapped active features
$missingFeatures = [System.Collections.Generic.List[string]]::new()
foreach ($id in $features.Keys) {
    if (-not $coverageEntries.ContainsKey($id)) {
        $missingFeatures.Add($id)
    }
}

if ($missingFeatures.Count -gt 0) {
    foreach ($m in $missingFeatures) {
        $errors.Add("Active feature '$m' is missing from coverage-manifest.jsonl.")
    }
}

# 5. Output report
Write-Host "=== Documentation Feature Coverage Report ===" -ForegroundColor Cyan
Write-Host "Total active features in inventory: $($features.Count)"
Write-Host "Total entries in coverage manifest: $($coverageEntries.Count)"
Write-Host "Total planned documentation pages:  $($validPageIds.Count)"

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
        Write-Host "  ... and $($errors.Count - 20) more errors." -ForegroundColor Red
    }
    exit 1
}

Write-Host "`nassert-docs-coverage: PASS (100% coverage verified, 0 errors)" -ForegroundColor Green
exit 0
