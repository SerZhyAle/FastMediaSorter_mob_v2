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

# 3.5. Load ticket leases held by OTHER live sessions (S1437, read-only consume).
# Our own leases must not exclude us, or a resuming session cannot get back to its own ticket.
# A missing or failing lease store yields an empty set: the picker still works without it.
$leaseSet = @{}
$leasedDetail = @()
$leasePath = Join-Path $PSScriptRoot 'ticket-lease.ps1'
if (Test-Path -LiteralPath $leasePath) {
    $leaseRaw = & $pwshExe -NoProfile -File $leasePath -Verb Status -Json 2>$null
    if ($leaseRaw) {
        try {
            foreach ($l in @($leaseRaw | ConvertFrom-Json)) {
                if ($l.mine) { continue }
                $leaseSet[[string]$l.id] = $l
            }
        } catch {
            # Malformed lease output is non-fatal for selection; surface as no leases.
            $leaseSet = @{}
        }
    }
}

# 4. Drop skip-cached + excluded + leased ids from the ranked list (record what was dropped)
#
# S1864: one cached reason is deliberately NOT honoured here. `blocker-not-verified` is the
# only skip verdict about ANOTHER ticket's status, so it goes stale from work that never
# touches the cached ticket - the blocker ships and its dependent stays hidden for the rest
# of the 7-day TTL. Measured: S1714 sat cached as "Depends on S1704(BlockNeedUserTest)" while
# S1704's code was already in the tree. Caching it bought nothing either way, since step 5
# re-derives the same verdict from preview.ps1 on every run. Every other reason is a judgement
# about the ticket itself (tier-5 epic, owner gate, drift needing review) and still holds.
$skipCachedIds = @()
$skipCacheOverriddenIds = @()
$rankedLive = New-Object System.Collections.Generic.List[object]
foreach ($r in $ranked) {
    if ($skipCache.Contains($r.id)) {
        $cachedReason = [string]$skipCache[$r.id].reason
        if ($cachedReason -like 'blocker-not-verified*') {
            $skipCacheOverriddenIds += $r.id
        }
        else {
            $skipCachedIds += $r.id
            continue
        }
    }
    if ($excludeSet.ContainsKey($r.id)) { continue }
    if ($leaseSet.ContainsKey($r.id)) {
        $held = $leaseSet[$r.id]
        $leasedDetail += [PSCustomObject]@{
            id                = [string]$r.id
            sessionId         = [string]$held.sessionId
            host              = [string]$held.host
            reason            = [string]$held.reason
            last_seen_minutes = $held.lastSeenMinutes
        }
        continue
    }
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

    # S1429: the third proof that landed work is accounted for. A Tier-3 ticket records progress in
    # its tactical INDEX, not in the strategic spec, so a gate that reads only `## Last Audit` and
    # `Implementation State` calls every in-flight tactical ticket unaccounted.
    #
    # S1634: "fresh" is now measured against the working tree instead of against a commit date. The
    # question has not changed - can this plan vouch for the marked code, or was it abandoned before
    # that code was written - but the old answer came from `git log`, and in a repository with one
    # developer committing the whole tree under a timestamp subject, a single routine snapshot dated
    # later than every plan made every in-flight ticket stale at once. The tree answers the same
    # question directly: compare when the plan was last written to when the marked sources were last
    # written. A plan touched no earlier than its marked code is accounting for it; a plan that has
    # not been touched since that code appeared is exactly the abandoned case the rule guards.
    $tacticalIndex = $null
    if ($selected.file) {
        $specRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $indexRel = ($selected.file -replace '\.md$', '') + '/INDEX.md'
        $indexPath = Join-Path $specRoot $indexRel
        if (Test-Path -LiteralPath $indexPath) {
            $indexText = Get-Content -LiteralPath $indexPath -Raw
            $updatedMatch = [regex]::Match($indexText, '(?m)^\*\*Last updated:\*\*\s*(\d{4}-\d{2}-\d{2})')
            $phasesMatch = [regex]::Match($indexText, '(?m)^\*\*Phases:\*\*\s*(\d+)\s*/\s*(\d+)\s*done')
            $lastUpdated = if ($updatedMatch.Success) { $updatedMatch.Groups[1].Value } else { $null }

            # The plan's own last-write time, not the `Last updated:` text: plan-tick.ps1 rewrites
            # this file on every step it ticks, so the file time tracks real plan activity and cannot
            # be left behind by a hand edit that forgot the header.
            $planWrittenAt = (Get-Item -LiteralPath $indexPath).LastWriteTimeUtc

            # The newest write among the sources actually carrying this ticket's inline markers. Only
            # those files matter: an unrelated source edited today says nothing about this plan.
            $markedWrittenAt = $null
            if ($drift -and $drift.code_markers) {
                foreach ($markerFile in @($drift.code_markers | ForEach-Object { $_.file } | Sort-Object -Unique)) {
                    $markerPath = Join-Path $specRoot $markerFile
                    if (Test-Path -LiteralPath $markerPath) {
                        $written = (Get-Item -LiteralPath $markerPath).LastWriteTimeUtc
                        if (-not $markedWrittenAt -or $written -gt $markedWrittenAt) { $markedWrittenAt = $written }
                    }
                }
            }

            # No markers means nothing to vouch for, so a plan that exists is proof enough; the
            # DRIFT verdict that consults this proof cannot even be reached in that case.
            $fresh = -not $markedWrittenAt -or $planWrittenAt -ge $markedWrittenAt

            $tacticalIndex = [PSCustomObject]@{
                path              = $indexRel
                last_updated      = $lastUpdated
                phases_done       = if ($phasesMatch.Success) { [int]$phasesMatch.Groups[1].Value } else { $null }
                phases_total      = if ($phasesMatch.Success) { [int]$phasesMatch.Groups[2].Value } else { $null }
                plan_written_at   = $planWrittenAt.ToString('yyyy-MM-dd HH:mm:ss')
                marked_written_at = if ($markedWrittenAt) { $markedWrittenAt.ToString('yyyy-MM-dd HH:mm:ss') } else { $null }
                fresh             = $fresh
            }
        }
    }
    Add-Member -InputObject $selected -NotePropertyName 'tactical_index' -NotePropertyValue $tacticalIndex -Force
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
    # S1864: cached under a stale `blocker-not-verified` and re-derived live instead of dropped.
    # Named in the payload so an operator sees the cache was overridden, not silently bypassed.
    skip_cache_overridden_ids = @($skipCacheOverriddenIds)
    excluded_ids   = @($normalizedExcludeIds)
    leased_ids     = @($leasedDetail)
    auto_skipped   = @($autoSkipped)
    malformed      = @($malformed)
    selected       = $selected
    # S1437: why nothing was selected, when nothing was. 'all-leased' means the queue is BUSY,
    # not finished - siblings hold every candidate and re-running later gets one. Reporting that
    # as plain exhaustion would tell the owner the work is done while three sessions are on it.
    selected_none_reason = $(
        if ($selected) { $null }
        elseif ($eligible.Count -eq 0) { 'queue-exhausted' }
        elseif ($rankedLive.Count -eq 0 -and $leasedDetail.Count -gt 0) { 'all-leased' }
        else { 'no-candidate' }
    )
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
    if ($skipCacheOverriddenIds.Count -gt 0) {
        Write-Host "  skip-cache overridden (blocker verdict re-derived live): $($skipCacheOverriddenIds -join ', ')" -ForegroundColor DarkGray
    }
    foreach ($l in $leasedDetail) {
        $seen = if ($null -ne $l.last_seen_minutes) { "last seen $($l.last_seen_minutes) min ago" } else { 'last seen unknown' }
        Write-Host "  [leased] $($l.id) - held by session $($l.sessionId) on $($l.host) ($seen)" -ForegroundColor DarkCyan
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
            Write-Host "    drift: $($selected.drift.verdict) (markers=$($selected.drift.markers_count))" -ForegroundColor DarkGray
        }
    } else {
        switch ($result.selected_none_reason) {
            'all-leased' {
                Write-Host "  SELECTED: none - every eligible ticket is held by a live sibling session." -ForegroundColor Yellow
                Write-Host "            The queue is busy, not finished. Re-run later to pick one up." -ForegroundColor Yellow
            }
            'queue-exhausted' { Write-Host "  SELECTED: none (eligible set exhausted)" -ForegroundColor DarkGray }
            default           { Write-Host "  SELECTED: none (no candidate survived the walk)" -ForegroundColor DarkGray }
        }
    }
}

exit 0
