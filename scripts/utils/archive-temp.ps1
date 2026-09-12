<#
.SYNOPSIS
  Archives stale scratch artifacts out of temp/ into temp/archive/<stamp>/.

.DESCRIPTION
  temp/ accumulates three kinds of content: fixed infrastructure that must never move,
  per-ticket scratch directories, and loose one-off artifacts (backups, screenshots,
  logcat dumps, ad-hoc scripts). This script moves only the stale part aside, so the
  live working set stays readable.

  Policy:
    - Protected root entries (locks, queues, leases, done/, scratch/, current logs,
      stream-catalog files, gradle-tmp, and the named generator working dirs) never move.
    - temp/Sxxxx*/ moves only when the ticket is Verified, Archived, or absent from
      PLAN/spec-catalog.jsonl - a live ticket keeps its artifacts.
    - Loose root files and dated one-off directories move once older than -OlderThanDays.
    - Nothing is deleted; everything lands under temp/archive/<stamp>/ by category.

.PARAMETER OlderThanDays
  Age threshold for loose files and dated one-off directories. Default 7.

.PARAMETER IncludeScratch
  Also apply the age rule to entries inside temp/scratch/. The directory itself always
  survives - only its stale contents move, into archive/<stamp>/scratch/.

.PARAMETER DryRun
  Report what would move and exit without touching the filesystem.

.PARAMETER Stamp
  Override the archive subdirectory name. Default: yyyyMMdd_HHmmss at run time.

.NOTES
  Exit codes:
    0 - completed (or dry run completed)
    1 - a move failed
    2 - could not verify: repo root or spec catalog CLI not found
#>
[CmdletBinding()]
param(
    [int]$OlderThanDays = 7,
    [switch]$IncludeScratch,
    [switch]$DryRun,
    [string]$Stamp
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$temp = Join-Path $repoRoot 'temp'
$selectCli = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'

if (-not (Test-Path -LiteralPath $temp)) {
    Write-Error "temp/ not found at $temp" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $selectCli)) {
    Write-Error "spec catalog CLI not found at $selectCli" -ErrorAction Continue
    exit 2
}

# Fixed infrastructure per CLAUDE.md Rule 1 plus the working dirs live scripts write into.
# S2170: the coordination names are DERIVED from the domain table, never listed here. A hand-kept
# list is what let the split land with five live queue directories unprotected - the archiver still
# knew only the two pre-split names, so a sweep could have moved a running session's queue away.
. (Join-Path $PSScriptRoot 'agent-lock-domains.ps1')
$lockDomainNames = @(Get-AgentLockDomainNames)

$protectedDirs = @(
    'archive', 'done', 'scratch', 'gradle-tmp', 'SPEC-TICKET.LEASES',
    'sessions', 'metrics', 'test-devices', 'play-shots', 'play-shots-tablet',
    'channel-preview-frames', 'channel-preview-publish',
    'stream-logo-src', 'stream-logo-publish',
    'tile-pack-publish', 'tile-pack-channel-preview-tiles', 'tile-pack-stream-logo-tiles'
)
$protectedDirs += @($lockDomainNames | ForEach-Object { "$($_.ToUpper()).QUEUE" })

$protectedFiles = @(
    'spec-all-queue.lock', 'spec-next-skip-cache.json',
    'current.log', 'stream-catalog-liveness.csv', 'stream-catalog.zip', '.gitignore'
)
$protectedFiles += @($lockDomainNames | ForEach-Object { "$($_.ToUpper()).LOCK" })
# Live logcat sinks the log tooling appends to. Turn markers (S2405) are swept by agent-lock.ps1.
$protectedFilePatterns = @('fastmediasorter_*.log')

Write-Host 'Reading spec catalog..'
$specs = & $selectCli -Format json | ConvertFrom-Json
# A ticket absent from the catalog output is Archived or gone - either way its scratch is stale.
$liveIds = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]($specs | Where-Object { $_.status -ne 'Verified' } | ForEach-Object { $_.id })
)
Write-Host "  live tickets: $($liveIds.Count) of $($specs.Count) catalog rows"

if (-not $Stamp) { $Stamp = (Get-Date).ToString('yyyyMMdd_HHmmss') }
$dest = Join-Path $temp "archive/$Stamp"
$cutoff = (Get-Date).AddDays(-$OlderThanDays)

function Get-Category {
    param([System.IO.FileSystemInfo]$Item)
    if ($Item.PSIsContainer) { return 'dirs' }
    switch -Regex ($Item.Name) {
        '\.(bak|backup|bak_\d+)$'          { return 'backups' }
        '^.+\.(kt|xml|json|jsonl)\.\S+$'   { return 'backups' }
        '\.(png|webp|jpg|jpeg)$'           { return 'screenshots' }
        '\.(log|logcat|err)$'              { return 'logs' }
        '\.(ps1|py|sh)$'                   { return 'scripts' }
        default                            { return 'misc' }
    }
}

$plan = [System.Collections.Generic.List[object]]::new()

foreach ($item in Get-ChildItem -LiteralPath $temp -Force) {
    $name = $item.Name

    if ($item.PSIsContainer) {
        if ($protectedDirs -contains $name) { continue }

        if ($name -match '^[Ss](?<id>\d{4})') {
            $id = 'S' + $Matches['id']
            if ($liveIds.Contains($id)) { continue }
            $plan.Add([pscustomobject]@{ Item = $item; Category = "tickets/$id"; Reason = 'ticket not live' })
            continue
        }

        if ($item.LastWriteTime -lt $cutoff) {
            $plan.Add([pscustomobject]@{ Item = $item; Category = 'dirs'; Reason = 'stale one-off dir' })
        }
        continue
    }

    if ($protectedFiles -contains $name) { continue }
    $isProtectedPattern = $false
    foreach ($pattern in $protectedFilePatterns) {
        if ($name -like $pattern) { $isProtectedPattern = $true; break }
    }
    if ($isProtectedPattern) { continue }
    if ($item.LastWriteTime -ge $cutoff) { continue }

    $plan.Add([pscustomobject]@{ Item = $item; Category = (Get-Category -Item $item); Reason = 'stale loose file' })
}

if ($IncludeScratch) {
    $scratch = Join-Path $temp 'scratch'
    if (Test-Path -LiteralPath $scratch) {
        foreach ($item in Get-ChildItem -LiteralPath $scratch -Force) {
            if ($item.LastWriteTime -ge $cutoff) { continue }
            $plan.Add([pscustomobject]@{ Item = $item; Category = 'scratch'; Reason = 'stale scratch entry' })
        }
    }
}

if ($plan.Count -eq 0) {
    Write-Host 'temp/ is already clean - nothing to archive.'
    exit 0
}

$totalMb = [math]::Round((
    $plan | ForEach-Object {
        if ($_.Item.PSIsContainer) {
            (Get-ChildItem -LiteralPath $_.Item.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
                Measure-Object -Property Length -Sum).Sum
        } else { $_.Item.Length }
    } | Measure-Object -Sum
).Sum / 1MB, 1)

Write-Host ''
Write-Host "Plan: $($plan.Count) entries, $totalMb MB -> temp/archive/$Stamp/"
$plan | Group-Object { ($_.Category -split '/')[0] } | Sort-Object Count -Descending |
    ForEach-Object { Write-Host ("  {0,-12} {1}" -f $_.Name, $_.Count) }

if ($DryRun) {
    Write-Host ''
    Write-Host 'DryRun - nothing moved.'
    exit 0
}

$moved = 0
$failed = 0
foreach ($entry in $plan) {
    $targetDir = Join-Path $dest $entry.Category
    try {
        if (-not (Test-Path -LiteralPath $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }
        Move-Item -LiteralPath $entry.Item.FullName -Destination $targetDir -Force
        $moved++
    } catch {
        Write-Warning "failed to move $($entry.Item.Name): $($_.Exception.Message)"
        $failed++
    }
}

Write-Host ''
Write-Host "Archived $moved entries ($totalMb MB) into temp/archive/$Stamp/"
if ($failed -gt 0) {
    Write-Error "$failed entries could not be moved" -ErrorAction Continue
    exit 1
}
exit 0
