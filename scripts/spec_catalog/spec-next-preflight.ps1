# spec-next-preflight.ps1 - One-shot selection preflight for /spec-next
#
# Collapses the per-round boilerplate that /spec-next used to run as 4+ separate
# calls (search.ps1 rank + skip-cache list + preview.ps1 per candidate + drift-check.ps1)
# into a single read-only invocation that returns:
#   - the full ranked eligible list, ordered by the owner's release plan
#     (PLAN/RELEASE_QUEUE.md: package asc, then line order inside the package),
#     with catalog priority desc / updated desc / id asc only as the fallback for a
#     ticket the queue does not list,
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
#   pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Exclude S0506 S0508
#   pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Format table
#
# -Exclude carries the in-memory "processed this run" set so a follow-up call
# returns the next candidate in one shot (no manual re-rank needed). CSV,
# repeated values, and space-separated ids are normalized into one set.
#
# Exit codes: 0 always (no candidate is a valid state, reported as selected=null).
#   2 - usage error only.

[CmdletBinding(PositionalBinding = $false)]
param(
    [string[]]$Exclude = @(),
    [switch]$NoDrift,
    [ValidateSet('json', 'table')]
    [string]$Format = 'json',
    [int]$MaxScan = 25,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AdditionalExclude = @()
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
$normalizedExcludeIds = New-Object System.Collections.Generic.List[string]
# Split comma-joined elements: native pwsh parses `-Exclude S0506,S0508` into an array, but
# invoking via `pwsh -File ... -Exclude "S0506,S0508"` binds the whole CSV as a single element.
foreach ($e in (@($Exclude) + @($AdditionalExclude))) {
    foreach ($id in ($e -split ',')) { if ($id.Trim()) { $excludeSet[$id.Trim()] = $true } }
}
foreach ($id in $excludeSet.Keys | Sort-Object) {
    $normalizedExcludeIds.Add($id)
}

# 1. Read active catalog + filter to eligible statuses.
# NB: Read-Catalog returns the array via the ,$arr anti-unroll idiom, so it must
# be captured by direct assignment - @(Read-Catalog) would collapse to 1 element.
$all = Read-Catalog
$eligible = @($all | Where-Object { $eligibleStatuses -contains $_.status })

# 2. Rank by the owner's release plan first. The catalog knows priority but not intent:
# PLAN/RELEASE_QUEUE.md says which package a ticket ships in and in what order inside it,
# and that decision outranks priority. Priority survives only as the fallback ordering for
# a ticket the queue does not list (and for a queue-less checkout, where the map is empty).
$queueRank = @{}
$queueRel = @{}
$queueSide = @{}
$queueSeq = 0
foreach ($line in (Read-ReleaseQueue)) {
    if ($line.Kind -ne 'ticket') { continue }
    if ($queueRank.ContainsKey($line.Id)) { continue }
    $queueRank[$line.Id] = $queueSeq++
    $queueRel[$line.Id] = [string]$line.Release
    $queueSide[$line.Id] = 'queue'
}
# The ready side (RELEASE_READY.md) holds finished content - an `Implemented` row there still
# needs its audit, but it is not planned work, so it sorts after every numbered package.
foreach ($line in (Read-ReleaseReady)) {
    if ($line.Kind -ne 'ticket') { continue }
    if ($queueSide.ContainsKey($line.Id)) { continue }
    $queueRank[$line.Id] = $queueSeq++
    $queueRel[$line.Id] = [string]$line.Release
    $queueSide[$line.Id] = 'ready'
}
# Buckets past the last real package, so numbered work always precedes them.
$relReadySide = 9000     # finished content awaiting its audit - valuable, but not planned work
$relParked = 9100        # `--` in the queue: drive it only when nothing in a package is eligible
$relOffQueue = 9500      # in neither file: nothing was sorted, fall back to priority
function Get-QueueRelBucket {
    param([Parameter(Mandatory)][string] $Id)
    if (-not $queueRel.ContainsKey($Id)) { return $relOffQueue }
    if ($queueSide[$Id] -eq 'ready') { return $relReadySide }
    $rel = $queueRel[$Id]
    if ($rel -match '^\d+$') { return [int]$rel }
    return $relParked
}
function Get-QueueLineOrder {
    param([Parameter(Mandatory)][string] $Id)
    if ($queueRank.ContainsKey($Id)) { return $queueRank[$Id] }
    return [int]::MaxValue
}
$ranked = @($eligible | Sort-Object `
    @{ Expression = { Get-QueueRelBucket -Id $_.id }; Descending = $false }, `
    @{ Expression = { Get-QueueLineOrder -Id $_.id }; Descending = $false }, `
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
        # release = the package this ticket belongs to ('--' parked, null = in neither file).
        # side    = 'queue' (work left), 'ready' (finished content awaiting audit), null.
        release  = $(if ($queueRel.ContainsKey($_.id)) { $queueRel[$_.id] } else { $null })
        side     = $(if ($queueSide.ContainsKey($_.id)) { $queueSide[$_.id] } else { $null })
        queue_order = $(if ($queueRank.ContainsKey($_.id)) { $queueRank[$_.id] } else { $null })
    }
})

$skipCacheOut = [PSCustomObject]@{}
foreach ($k in $skipCache.Keys) {
    Add-Member -InputObject $skipCacheOut -MemberType NoteProperty -Name $k -Value $skipCache[$k]
}

$result = [PSCustomObject]@{
    total          = $all.Count
    eligible_count = $eligible.Count
    order_source   = 'PLAN/RELEASE_QUEUE.md (release package, then line order); catalog priority is the fallback'
    current_release = (Get-CurrentRelease)
    ranked         = $rankedOut
    skip_cache     = $skipCacheOut
    skip_cached_ids = @($skipCachedIds)
    excluded_ids   = @($normalizedExcludeIds)
    auto_skipped   = @($autoSkipped)
    malformed      = @($malformed)
    selected       = $selected
}

if ($Format -eq 'json') {
    $result | ConvertTo-Json -Depth 8 -Compress
} else {
    Write-Host "spec-next preflight" -ForegroundColor Cyan
    Write-Host "  eligible: $($eligible.Count) / total: $($all.Count) | live ranked: $($rankedOut.Count)" -ForegroundColor DarkGray
    Write-Host "  order: PLAN/RELEASE_QUEUE.md (package, then line order) | current release: $(Get-CurrentRelease)" -ForegroundColor DarkGray
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
        $selRel = if ($queueRel.ContainsKey($selected.id)) { $queueRel[$selected.id] } else { 'not in queue' }
        Write-Host "  SELECTED: $($selected.id) (rel $selRel, $($selected.status), pri $($selected.priority), tier $($selected.tier)) - $($selected.name)" -ForegroundColor Green
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
