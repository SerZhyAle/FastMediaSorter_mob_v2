# PHASE_04 - consumer audit + `-IncludeArchived` flags

Confirm every script that dot-sources `_lib.ps1` behaves correctly under the split. Add archive visibility only where a full view is the intent.

## Steps

- [ ] `select.ps1` - add `[switch] $IncludeArchived`; pass through to `Read-Catalog`. Default stays active-only. (`-Id` already resolves archived via `Find-Record` for single-id lookups; for `-Status Archived` listing the user passes `-IncludeArchived`.)
- [ ] `search.ps1` - add `[switch] $IncludeArchived`; pass through. Default active-only.
- [ ] `stats.ps1` - read with `-IncludeArchived` so status distribution still reports the archive bucket (stats is a full-catalog overview by intent).
- [ ] `validate.ps1` - validate both files: run existing record checks over `Read-Catalog -IncludeArchived`; additionally assert no `Archived` record sits in the active journal and no non-`Archived` record sits in the archive journal (split invariant check).
- [ ] Audit remaining mutators (`update.ps1`, `complete.ps1`, `close.ps1`, `close-and-log.ps1`, `bulk-update.ps1`, `delete.ps1`, `insert.ps1`): each does active read-modify-write. Confirm none needs to mutate an `Archived` record on the hot path; archived-id mutation is out of normal flow. Record findings inline here.
- [ ] `preview.ps1` / `drift-check.ps1` / `skip-cache.ps1` - use `Find-Record`/active reads; confirm no regression.

## Verification

- [ ] `select.ps1 -Status Archived -IncludeArchived` lists the archived set; without the flag lists none.
- [ ] `stats.ps1` still shows the Archived count.
- [ ] `validate.ps1` exit 0 and reports the split invariant as held.
- [ ] `search.ps1 -Format json` (default) returns only active records.
