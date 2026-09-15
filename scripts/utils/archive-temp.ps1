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
    - Loose root files move once older than -OlderThanDays, and so does EVERY unprotected
      directory - not only a dated one. Until S3030 this line promised the narrower reading, and
      that is what hid the exposure S3030 repaired: the age rule reaches any directory the inventory
      does not protect, which at the time included the device registry.
    - Nothing is deleted; everything lands under temp/archive/<stamp>/ by category.

.PARAMETER OlderThanDays
  Age threshold for loose files and for every unprotected directory. Default 7.

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
    [string]$Stamp,
    [string]$RepoRoot,
    [string]$TempDir,
    [string]$SelectCli
)

$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$temp = if ($TempDir) { (Resolve-Path -LiteralPath $TempDir).Path } else { Join-Path $repoRoot 'temp' }
$selectCli = if ($SelectCli) { (Resolve-Path -LiteralPath $SelectCli).Path } else { Join-Path $repoRoot 'scripts/spec_catalog/select.ps1' }

if (-not (Test-Path -LiteralPath $temp)) {
    Write-Error "temp/ not found at $temp" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $selectCli)) {
    Write-Error "spec catalog CLI not found at $selectCli" -ErrorAction Continue
    exit 2
}

# What may live at temp/ root is declared in ONE place and read here (S3030). S2170 established the
# principle for the coordination names - they are DERIVED from the domain table, never listed, after
# a hand-kept list let the split land with five live queue directories unprotected, so a sweep could
# have moved a running session's queue away. S3030 finished the job: the whole set moved into the
# inventory, because the part that stayed hand-kept here omitted the release-freeze pair CLAUDE.md
# Rule 10 declares legitimate and seven live coordination directories, DEVICE.REGISTRY among them -
# the roster Rule 35 reads, which is idle for a week in normal use and so was squarely in reach of
# the age rule below.
. (Join-Path $PSScriptRoot 'temp-root-inventory.ps1')
$tempRootInventory = Get-TempRootInventory -RepoRoot $repoRoot

$protectedDirs = @($tempRootInventory.FixedDirs)
$protectedFiles = @($tempRootInventory.FixedFiles)
# FixedFilePatterns only. RetainedFilePatterns is deliberately NOT protected here: those shapes are
# legal at the root BECAUSE the age rule below carries them away, so protecting them would stop the
# retention that is the whole reason they are allowed, and the per-run check logs would accumulate
# without bound. The gate reads that member as "legal"; this consumer reads it as "sweepable". Turn
# markers (S2405) are in it for the same reason - agent-lock.ps1 sweeps its own, and a week-old one
# is dead.
$protectedFilePatterns = @($tempRootInventory.FixedFilePatterns)

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
