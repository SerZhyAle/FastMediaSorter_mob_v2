# PHASE_02 - `archive.ps1` moves the record to the archive file

`archive.ps1` currently flips status in-place. Change it to physically relocate the record.

## Steps

- [ ] After building the `$archived` record (status `Archived`, priority 0, `updated` now), append it to the archive journal via `Add-ArchiveRecord -Record $archived`.
- [ ] Remove the record from the active journal: read active, drop the id, `Write-Catalog` the remainder. (Replaces the current in-place `$allRecords[$idx] = $archived; Write-Catalog`.)
- [ ] Keep the artefact moves to `temp/done/` and `Sync-SpecHeaderStatus` unchanged.
- [ ] Idempotency: if the id is already absent from active but present in archive, do not duplicate - `Add-ArchiveRecord` replaces by id; active removal is a no-op.

## Verification

- [ ] Archiving a throwaway test id (insert -> archive) removes it from `spec-catalog.jsonl` and adds it to `spec-catalog-archive.jsonl`.
- [ ] `select.ps1 -Id <that id>` still resolves (fallback).
- [ ] Re-running `archive.ps1` on the same id exits without duplicating the archive row.
- [ ] `validate.ps1` exit 0 after the round-trip.
