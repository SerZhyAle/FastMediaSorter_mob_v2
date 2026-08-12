[CmdletBinding()]
param()

# One-time (idempotent) migration: relocate all Archived records out of the
# active journal into the archive journal. Safe to re-run - a second pass moves
# zero. See PLAN/S0454_spec-catalog-journal-compaction.md.

trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

# S1437: the active and archive writes below share one critical section - a concurrent mutator
# between them would be overwritten by whichever of the two landed last.
Enter-CatalogLock

# Direct assignment - Read-JsonlFile uses the ,$arr anti-unroll idiom.
$activeRaw = Read-JsonlFile -Path (Get-CatalogPath)
$toArchive = @($activeRaw | Where-Object { $_.status -eq 'Archived' })
$keepActive = @($activeRaw | Where-Object { $_.status -ne 'Archived' })

if ($toArchive.Count -eq 0) {
    Write-Output "Nothing to migrate - active journal has 0 Archived records."
    exit 0
}

# Batch merge into the archive (one write), then trim the active journal (one write).
$existingArchive = Read-JsonlFile -Path (Get-ArchivePath)
$movingIds = @($toArchive | ForEach-Object { $_.id })
$mergedArchive = @($existingArchive | Where-Object { $movingIds -notcontains $_.id }) + $toArchive
Write-ArchiveCatalog -Records ([object[]]$mergedArchive)
Write-Catalog -Records ([object[]]$keepActive)

Exit-CatalogLock

$archiveCount = (Read-JsonlFile -Path (Get-ArchivePath)).Count
Write-Output ("Migrated {0} Archived record(s). Active now {1}, archive now {2}." -f `
    $toArchive.Count, $keepActive.Count, $archiveCount)
exit 0
