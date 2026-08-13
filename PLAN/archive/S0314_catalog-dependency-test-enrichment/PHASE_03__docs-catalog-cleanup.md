# Phase 03 - docs-catalog-cleanup

**Ticket:** S0314
**Owns:** Document the enriched schema and query surface in the catalog README; record the dev-log placeholder. Always the final phase.
**Files Touched:** `dev/CATALOG/README.md`

> Depends on Phase 01 (field) and Phase 02 (flag) being done. Field/flag names assume the BLK-01/BLK-02 defaults; substitute owner-confirmed names if they differed.

---

## Context (static facts established by audit)

- `dev/CATALOG/README.md` carries a "Record fields" table that already documents `hasTests` and `injected`, and a `query.ps1` filter list that already names the supported flags.
- README is a tracked doc (not gitignored); the `<module>.jsonl` / `<module>.md` indexes it points to are gitignored regenerated outputs.

---

## Steps

- [x] **S03.1 - Document the new dependency field.** Add a `constructorDeps` row to the "Record fields" table, marked `auto`, describing it as all constructor parameter types (superset of `injected`).
  - Verification: `Grep` for `constructorDeps` in `dev/CATALOG/README.md` returns at least 1 match.
  - Result: `constructorDeps` `auto` row added after `injected` (README line 30), described as a superset of `injected` capturing every constructor param, imports excluded. `expected: >=1 | actual: 4`.

- [x] **S03.2 - Refresh the test-coverage field description.** Update the `hasTests` row to reflect the hardened extraction (test root resolved per source root, both naming conventions matched), so the doc no longer implies a `src/main`-only mapping.
  - Verification: `Grep` for `hasTests` in `dev/CATALOG/README.md` returns at least 1 match; the row no longer states the coverage is limited to `src/main` sources.
  - Result: `hasTests` row (README line 31) rewritten - test root resolved from the file's own `src/<root>/` (flavor roots named), matches both `<ClassName>Test.kt` and same-relative-path mirror; explicitly "not from `src/main` alone". `expected: >=1 & no src/main-only claim | actual: row present, src/main-only restriction removed`.

- [x] **S03.3 - Document the new query flag.** Add `-DependsOn` to the `query.ps1` supported-filters list and add a usage example mirroring the `-Injected` example.
  - Verification: `Grep` for `-DependsOn` in `dev/CATALOG/README.md` returns at least 1 match.
  - Result: `-DependsOn` added to the supported-filters list (README line 117), a usage example mirroring `-Injected` (line 106), and an `-Injected` vs `-DependsOn` semantics note (line 121). `expected: >=1 | actual: 3`.

- [x] **S03.4 - Append-only note.** State explicitly in the README that test and dependency fields were added append-only and that existing field names are preserved (cross-references the ADR-1 decision).
  - Verification: `Grep` for `append-only` in `dev/CATALOG/README.md` returns at least 1 match.
  - Result: append-only note added after the Record-fields table (README line 43), citing S0314 ADR-1, stating no field was renamed/reordered/removed and that `constructorDeps` is recomputed (never merged as manual). `expected: >=1 | actual: 1`.

- [x] **S03.5 - Dev-log placeholder.** Record that the dev-log entry for S0314 is owned centrally (handled by the operator, not this tactical author), so no `add_to_dev_log.ps1` call is made from inside the plan.
  - Verification: this step is a note; mark done once the README edits above are complete. No executable artifact is changed by this step.
  - Result: note only. Dev log + functionality log + spec-catalog status transition are owned centrally by the operator (per executor prohibitions); the executor made no `add_to_dev_log.ps1`, `add_to_functionality_log.ps1`, or spec-catalog mutator call. README edits (S03.1-S03.4) complete.

---

## Phase Done Criteria

At least the following invariants hold (all must be true):

1. `dev/CATALOG/README.md` documents `constructorDeps` as an auto field in the Record-fields table.
2. The `hasTests` README row reflects the hardened per-source-root extraction and no longer implies `src/main`-only coverage.
3. `dev/CATALOG/README.md` lists `-DependsOn` among `query.ps1` filters with a usage example.
4. The README states the enrichment is append-only with existing field names preserved.
