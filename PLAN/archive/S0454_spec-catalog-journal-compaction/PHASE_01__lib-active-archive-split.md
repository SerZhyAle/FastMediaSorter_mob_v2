# PHASE_01 - `_lib.ps1` active/archive split

Centralise the two-file model in `scripts/spec_catalog/_lib.ps1`. No consumer edits in this phase.

## Steps

- [ ] Add `$script:ArchivePath = Join-Path $repoRoot 'PLAN\spec-catalog-archive.jsonl'` next to `$script:CatalogPath`.
- [ ] Add `Get-ArchivePath` returning `$script:ArchivePath`.
- [ ] Extract the existing JSONL-parse loop from `Read-Catalog` into a private `Read-JsonlFile -Path <p>` helper (returns sorted-by-id object array; missing file -> empty array). Keep the line-number parse-error message.
- [ ] Rewrite `Read-Catalog` to take an optional `[switch] $IncludeArchived`:
  - default: return `Read-JsonlFile -Path $script:CatalogPath` (active only).
  - `-IncludeArchived`: concat active + `Read-JsonlFile -Path $script:ArchivePath`, sort by id, return.
- [ ] Rewrite `Find-Record` to search active first, then archive on miss (read archive only when needed).
- [ ] Add `Write-ArchiveCatalog -Records` mirroring `Write-Catalog` but targeting `$script:ArchivePath` (same ordered-key shaping, atomic temp+Move, UTF-8 no BOM). Refactor the shared shaping into a private `Format-CatalogLines -Records` used by both writers to avoid duplication.
- [ ] Add `Add-ArchiveRecord -Record`: read archive, append/replace by id, `Write-ArchiveCatalog`.
- [ ] `Write-Catalog` continues to write `$script:CatalogPath` only (no behaviour change beyond using the shared formatter).

## Verification

- [ ] `pwsh -NoProfile -Command ". scripts/spec_catalog/_lib.ps1; (Read-Catalog).Count"` runs, exit 0, returns current active count (before migration: full count).
- [ ] `pwsh -NoProfile -Command ". scripts/spec_catalog/_lib.ps1; (Read-Catalog -IncludeArchived).Count"` equals current total.
- [ ] `Find-Record -Id <any existing id>` returns the record.
- [ ] `Get-Command Write-ArchiveCatalog, Add-ArchiveRecord, Get-ArchivePath` all resolve after dot-sourcing.
