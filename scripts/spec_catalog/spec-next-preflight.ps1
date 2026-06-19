# spec-next-preflight.ps1 - One-shot selection preflight for /spec-next
#
# Collapses the per-round boilerplate that /spec-next used to run as 4+ separate
# calls (search.ps1 rank + skip-cache list + preview.ps1 per candidate + drift-check.ps1)
# into a single read-only invocation that returns:
#   - the full ranked eligible list (priority desc, updated desc, id asc),
#   - the active persistent skip-cache and which ranked ids it removed,
#   - candidates auto-skipped while walking down the list (the skill persists
#     these to skip-cache itself - this script never mutates),
#   - the selected ticket with its full preview.ps1 payload + drift-check verdict.
#
# READ-ONLY by contract: no writes to the catalog, the skip-cache, or any spec.
# That keeps `/spec-next --dry` pure and leaves every mutation (skip-cache add,
# status sync, status transitions) owned by the skill / by /spec-all.
#
# Usage:
#   pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1
#   pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Exclude S0506,S0508
#   pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Format table
#
# -Exclude carries the in-memory "processed this run" set so a follow-up call
# returns the next candidate in one shot (no manual re-rank needed).
#
# Exit codes: 0 always (no candidate is a valid state, reported as selected=null).
#   2 - usage error only.

[CmdletBinding()]
param(
    [string[]]$Exclude = @(),
    [int]$MaxScan = 25,
    [switch]$NoDrift,
    [ValidateSet('json', 'table')]
    [string]$Format = 'json'
)

. (Join-Path $PSScriptRoot '_lib.ps1')

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}
$previewPath = Join-Path $PSScriptRoot 'preview.ps1'
$driftPath = Join-Path $PSScriptRoot 'drift-check.ps1'
$skipCachePath = Join-Path $PSScriptRoot 'skip-cache.ps1'

$eligibleStatuses = @(
    'Draft', 'Approved', 'Tactical', 'In Progress',
    'Implemented', 'Partial', 'Broken', 'BlockByOtherTask'
)
$excludeSet = @{}
# Split comma-joined elements: native pwsh parses `-Exclude S0506,S0508` into an array, but
# invoking via `pwsh -File ... -Exclude "S0506,S0508"` binds the whole CSV as a single element.
foreach ($e in $Exclude) {
    foreach ($id in ($e -split ',')) { if ($id.Trim()) { $excludeSet[$id.Trim()] = $true } }
}

# 1. Read active catalog + filter to eligible statuses.
# NB: Read-Catalog returns the array via the ,$arr anti-unroll idiom, so it must
# be captured by direct assignment - @(Read-Catalog) would collapse to 1 element.
$all = Read-Catalog
$eligible = @($all | Where-Object { $eligibleStatuses -contains $_.status })

# 2. Rank: priority desc, updated desc, id asc
$ranked = @($eligible | Sort-Object `
    @{ Expression = { [int]$_.priority }; Descending = $true }, `
    @{ Expression = { [string]$_.updated }; Descending = $true }, `
    @{ Expression = { [string]$_.id }; Descending = $false })

# 3. Load active persistent skip-cache (read-only consume)
$skipCache = [ordered]@{}
$skipRaw = & $pwshExe -NoProfile -File $skipCachePath -Action list 2>$null
if ($skipRaw -and $skipRaw.Trim() -ne '{}') {
    try {
        $parsed = $skipRaw | ConvertFrom-Json
        foreach ($prop in $parsed.PSObject.Properties) {
            $skipCache[$prop.Name] = $prop.Value
        }
    } catch {
        # Malformed cache is non-fatal for selection; surface as empty.
        $skipCache = [ordered]@{}
    }
}

# 4. Drop skip-cached + excluded ids from the ranked list (record what was dropped)
$skipCachedIds = @()
$rankedLive = New-Object System.Collections.Generic.List[object]
foreach ($r in $ranked) {
    if ($skipCache.Contains($r.id)) { $skipCachedIds += $r.id; continue }
    if ($excludeSet.ContainsKey($r.id)) { continue }
    $rankedLive.Add($r)
}

# 5. Walk down the live ranked list; preview each until one has auto_skip == null
$autoSkipped = @()
$malformed = @()
$selected = $null
$scan = 0
foreach ($cand in $rankedLive) {
    if ($scan -ge $MaxScan) { break }
    $scan++
    $pvRaw = & $pwshExe -NoProfile -File $previewPath -Id $cand.id -Format json 2>$null
    if (-not $pvRaw) { $malformed += $cand.id; continue }
    try {
        $pv = $pvRaw | ConvertFrom-Json
    } catch {
        $malformed += $cand.id; continue
    }
    if ($pv.auto_skip) {
        $autoSkipped += [PSCustomObject]@{
            id     = $cand.id
            reason = $pv.auto_skip
            detail = $pv.auto_skip_reason
        }
        continue
    }
    $selected = $pv
    break
}

# 6. For the selected candidate: drift-check verdict + file/catalog status mismatch
if ($selected) {
    # status mismatch (file frontmatter vs catalog) - skill resolves via update.ps1
    $fileStatus = $null
    if ($selected.frontmatter -and ($selected.frontmatter.PSObject.Properties.Name -contains 'Status')) {
        $fileStatus = $selected.frontmatter.Status
    }
    $mismatch = $null
    if ($fileStatus -and $fileStatus -ne $selected.status) {
        $mismatch = [PSCustomObject]@{ catalog = $selected.status; file = $fileStatus }
    }
    Add-Member -InputObject $selected -NotePropertyName 'status_mismatch' -NotePropertyValue $mismatch -Force

    $drift = $null
    if (-not $NoDrift) {
        $dfRaw = & $pwshExe -NoProfile -File $driftPath -Id $selected.id -Format json 2>$null
        if ($dfRaw) {
            try { $drift = $dfRaw | ConvertFrom-Json } catch { $drift = $null }
        }
    }
    Add-Member -InputObject $selected -NotePropertyName 'drift' -NotePropertyValue $drift -Force
}

# 7. Compose result
$rankedOut = @($rankedLive | ForEach-Object {
    [PSCustomObject]@{
        id       = $_.id
        name     = $_.name
        status   = $_.status
        priority = [int]$_.priority
        updated  = [string]$_.updated
    }
})

$skipCacheOut = [PSCustomObject]@{}
foreach ($k in $skipCache.Keys) {
    Add-Member -InputObject $skipCacheOut -MemberType NoteProperty -Name $k -Value $skipCache[$k]
}

$result = [PSCustomObject]@{
    total          = $all.Count
    eligible_count = $eligible.Count
    ranked         = $rankedOut
    skip_cache     = $skipCacheOut
    skip_cached_ids = @($skipCachedIds)
    excluded_ids   = @($Exclude)
    auto_skipped   = @($autoSkipped)
    malformed      = @($malformed)
    selected       = $selected
}

if ($Format -eq 'json') {
    $result | ConvertTo-Json -Depth 8 -Compress
} else {
    Write-Host "spec-next preflight" -ForegroundColor Cyan
    Write-Host "  eligible: $($eligible.Count) / total: $($all.Count) | live ranked: $($rankedOut.Count)" -ForegroundColor DarkGray
    if ($skipCachedIds.Count -gt 0) {
        Write-Host "  skip-cached out: $($skipCachedIds -join ', ')" -ForegroundColor DarkGray
    }
    foreach ($a in $autoSkipped) {
        Write-Host "  [auto-skip] $($a.id) - $($a.reason): $($a.detail)" -ForegroundColor Yellow
    }
    if ($malformed.Count -gt 0) {
        Write-Host "  malformed (preview failed): $($malformed -join ', ')" -ForegroundColor Red
    }
    if ($selected) {
        Write-Host "  SELECTED: $($selected.id) ($($selected.status), pri $($selected.priority), tier $($selected.tier)) - $($selected.name)" -ForegroundColor Green
        if ($selected.status_mismatch) {
            Write-Host "    status mismatch: catalog=$($selected.status_mismatch.catalog) file=$($selected.status_mismatch.file) (file authoritative)" -ForegroundColor Yellow
        }
        if ($selected.drift) {
            Write-Host "    drift: $($selected.drift.verdict) (commits=$($selected.drift.commits_count) markers=$($selected.drift.markers_count))" -ForegroundColor DarkGray
        }
    } else {
        Write-Host "  SELECTED: none (eligible set exhausted)" -ForegroundColor DarkGray
    }
}

exit 0
