# PHASE_03 - one-time migration of existing Archived records

Move the ~431 existing `Archived` rows out of the active journal into the archive journal.

## Steps

- [ ] Write `scripts/spec_catalog/migrate-archive-split.ps1` (idempotent, repeatable):
  - read active via `Read-JsonlFile $script:CatalogPath` (raw, no archive merge);
  - partition into `active = status != Archived`, `toArchive = status == Archived`;
  - merge `toArchive` into the archive journal via `Add-ArchiveRecord` per record (replace-by-id keeps it idempotent);
  - `Write-Catalog` the `active` partition back to the active journal;
  - print counts moved / remaining.
- [ ] Run it once.
- [ ] Re-run it to confirm idempotency (second run moves 0).

## Verification

- [ ] After run: `(Read-Catalog).Count` ~= active ticket count (~32, no `Archived`).
- [ ] `(Read-Catalog -IncludeArchived).Count` == pre-migration total (463), no records lost.
- [ ] `select.ps1 -Status Archived` (active only) returns none; `-IncludeArchived` path returns the archived set.
- [ ] Second migration run reports 0 moved.
- [ ] `validate.ps1` exit 0.
