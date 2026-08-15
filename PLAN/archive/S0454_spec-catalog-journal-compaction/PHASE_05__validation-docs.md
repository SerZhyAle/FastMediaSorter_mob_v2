# PHASE_05 - validation sweep + docs

## Steps

- [ ] Full validation: `validate.ps1` exit 0.
- [ ] Round-trip smoke: insert a throwaway id, archive it, confirm it left active / entered archive, `select -Id` resolves it, `delete.ps1` cleans it up.
- [ ] Regression: run `search.ps1 -Format json`, `select.ps1 -Format json`, `preview.ps1 -Id <active id>`, `stats.ps1` - all exit 0 with expected shapes.
- [ ] Document the split in `scripts/spec_catalog/SCHEMA.md`: active vs archive file, `-IncludeArchived` semantics, `Find-Record` fallback, `archive.ps1` move behaviour.
- [ ] Add `.gitignore` check: archive journal tracked the same way as the active journal (both under `PLAN/`); confirm no accidental ignore.
- [ ] Dev log each touched file.

## Verification

- [ ] `validate.ps1` exit 0.
- [ ] `SCHEMA.md` contains an "Archive split" section (grep).
- [ ] No `Archived` rows remain in `spec-catalog.jsonl`.
