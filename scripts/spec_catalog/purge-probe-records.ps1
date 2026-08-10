<#
.SYNOPSIS
  One-off (idempotent) cleanup: remove preview.tests probe records from the ARCHIVE journal.
.DESCRIPTION
  Until S1534 the preview.tests harness inserted its throwaway probes into the production catalog
  with ids from the real next-id.ps1. delete.ps1 is a soft delete, so each probe left a row in
  PLAN/spec-catalog-archive.jsonl for good - 21 of them by 2026-08-09 - and select.ps1 -Id answered
  an id lookup with a test fixture. The harness no longer writes the real journals; this removes
  what it already wrote.

  Deliberately NOT a general-purpose purge. A -Purge switch on delete.ps1 was rejected in S1534
  section 3.1: it would put an irreversible operation into production tooling to compensate for a
  test defect. This script removes only rows that are simultaneously status 'Archived' AND named
  '^preview-tests-probe', and nothing else can be passed to it.

  Every purged id is first recorded in PLAN/spec-catalog-burned-ids.jsonl. New-CatalogId reads that
  registry alongside both journals, so an id cannot return to circulation just because its row went
  away - two tickets answering to one id would make every dev-log row and commit message naming it
  ambiguous after the fact. validate.ps1 reads the same registry to tell a deliberate hole in the id
  sequence from a lost record.

  Guard: New-CatalogId is computed before and after the rewrite, and a move restores the original
  archive and fails. With the registry written first this should be unreachable - it stays as the
  second belt, because the failure it catches is silent and permanent.
.NOTES
  Exit codes:
    0 - purged, or nothing to purge (idempotent re-run).
    1 - error (archive unreadable, lock timeout, write failure).
    3 - guard tripped: the purge would have changed the next allocatable id. Archive restored.
#>
[CmdletBinding()]
param(
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/spec_catalog/purge-probe-records.ps1'
    exit $LASTEXITCODE
}

trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

$probeNamePattern = '^preview-tests-probe'

Enter-CatalogLock

$archiveBefore = Read-JsonlFile -Path (Get-ArchivePath)
$doomed = @($archiveBefore | Where-Object { $_.status -eq 'Archived' -and $_.name -match $probeNamePattern })

if ($doomed.Count -eq 0) {
    Exit-CatalogLock
    Write-Output "Nothing to purge - the archive holds 0 preview-tests-probe records."
    exit 0
}

$idBefore = New-CatalogId
$doomedIds = @($doomed | ForEach-Object { $_.id })
$kept = @($archiveBefore | Where-Object { $doomedIds -notcontains $_.id })

# Registry BEFORE the archive rewrite, so a crash between the two leaves the ids doubly held rather
# than free: New-CatalogId reads the registry, so the maximum can no longer fall when the rows go.
$burnedAdded = Add-BurnedIds -Ids $doomedIds -Reason 'preview.tests probe record (S1534)'

Write-ArchiveCatalog -Records ([object[]]$kept)

$idAfter = New-CatalogId
if ($idAfter -ne $idBefore) {
    Write-ArchiveCatalog -Records ([object[]]$archiveBefore)
    Exit-CatalogLock
    Write-Error ("Aborted: purging would move the next id from $idBefore to $idAfter, " +
        "handing a burned id back to a future ticket. Archive restored unchanged.") -ErrorAction Continue
    exit 3
}

Exit-CatalogLock

Write-Output ("Purged {0} probe record(s): {1}" -f $doomed.Count, ($doomedIds -join ', '))
Write-Output ("Recorded {0} id(s) in {1}." -f $burnedAdded, (Split-Path -Leaf (Get-BurnedIdsPath)))
Write-Output ("Archive now {0} record(s). Next allocatable id unchanged at {1}." -f $kept.Count, $idAfter)
