# Tactical Plan: S0454 - spec-catalog journal compaction

**Ticket:** S0454
**Strategic spec:** `PLAN/S0454_spec-catalog-journal-compaction.md`
**Status:** Tactical

Split the spec-catalog journal into an active file (non-Archived) and an archive file (Archived), behind the shared `_lib.ps1` layer, so the hot read path scans only active tickets while id resolution and full reviews stay transparent.

## Design invariants

- Active journal `PLAN/spec-catalog.jsonl` holds only non-`Archived` records.
- Archive journal `PLAN/spec-catalog-archive.jsonl` holds only `Archived` records.
- `Read-Catalog` default = active only. `Read-Catalog -IncludeArchived` = active + archive merged, sorted by id.
- `Find-Record` resolves against active, falls back to archive on miss.
- `Write-Catalog` writes the active journal only - it never sees or drops archive rows.
- Archive journal mutated only by `archive.ps1` (append) and the one-time migration.
- Backward compatible: archive file absent -> behaviour identical to today.
- Atomic writes (temp + `Move-Item -Force`), UTF-8 no BOM, stable key order - same as active.

## Phases

- PHASE_01 - `_lib.ps1` active/archive split (core library layer).
- PHASE_02 - `archive.ps1` moves the record to the archive file.
- PHASE_03 - one-time migration of existing `Archived` records out of the active journal.
- PHASE_04 - consumer audit; add `-IncludeArchived` to overview commands; confirm mutators are archive-safe.
- PHASE_05 - validation sweep + `SCHEMA.md` documentation.

## Validation

- Each phase ends with a script run, exit 0.
- Final: `validate.ps1` exit 0; `select.ps1 -Id <archived id>` resolves via fallback; active journal line count ~= active ticket count.
