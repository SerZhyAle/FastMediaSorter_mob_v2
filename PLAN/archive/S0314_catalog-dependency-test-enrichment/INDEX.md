# Tactical Plan: S0314 - Catalog dependency and test enrichment

**Ticket:** S0314
**Strategic spec:** `PLAN/S0314_catalog-dependency-test-enrichment.md`
**Parent umbrella:** S0311 (agent tooling)
**Tier:** 3 - Moderate, ad-hoc
**Scope:** Extend the class-catalog PowerShell tooling (`dev/CATALOG/scripts/*.ps1`) so the catalogue answers untested-class and dependency questions without a global grep, append-only, app and Wear records kept separate.

> All work is confined to `dev/CATALOG/scripts/*.ps1`, `dev/CATALOG/README.md`, and (read-only reference) `scripts/catalog_sync.ps1`. No app source change. No Gradle build. No Room. Timber / landscape / flavor rules are N/A for this ticket.

---

## Premise Correction (read before Phase 01)

The strategic spec (S0314 §1, §2.1, §11.1 and parent S0311 §3.3, §5.2) asserts that the catalogue has **no** test-coverage field and that Phase 01 must **create** `hasTests`. This is empirically false in the current tree:

- `dev/CATALOG/scripts/scan.ps1` defines `Test-HasTests` and writes `hasTests` on every record.
- `dev/CATALOG/scripts/query.ps1` exposes `-Tests` / `-NoTests` filtering on `hasTests`.
- `dev/CATALOG/scripts/render.ps1` surfaces a `tests` flag from `hasTests`.
- `dev/CATALOG/README.md` documents `hasTests` in the Record-fields table.

The existing `hasTests` is narrow: it maps only `src\main\` to `src\test\` / `src\androidTest\` and assumes a `<ClassName>Test.kt` filename, so flavor-source classes and non-`*Test.kt` coverage are reported as untested.

This tactical plan therefore **does not create** `hasTests`. It (a) hardens the existing field and (b) adds the genuinely-new dependency metadata. Whether to keep the field name `hasTests` or rename it is an owner decision recorded in BLK-01 below; Phase 01 must not start until BLK-01 is resolved.

---

## Default approach where research influenced structure (S0314 §6, parent S0311 §6)

§6 "Catalog dependency schema" is `Status: Open`. Chosen default for this plan, marked as an assumption (BLK-02):

- Reuse the existing `@Inject constructor(..)` extraction style already proven by `Get-Injected`.
- Add one new append-only field `constructorDeps` holding **all** constructor parameter types (not only `@Inject`-annotated), so non-Hilt and `@Inject`-free classes still expose their collaborators.
- Do **not** parse `import` statements in this iteration (high noise, low signal for navigation). Imports are recorded as a deferred option in BLK-02, not implemented.
- Query gains a dependency filter answering "which records depend on a given type by constructor", complementing the existing `-Injected`.

This default keeps the schema append-only (`injected` is untouched; `constructorDeps` is additive) and keeps extraction cheap enough for the post-`.kt` sync ritual.

---

## Phase Overview

| Phase | Slug | Owns | Files Touched | Status |
|------:|------|------|---------------|--------|
| 01 | schema-enrich | Harden `hasTests`; add append-only `constructorDeps`; preserve manual + existing fields | `dev/CATALOG/scripts/scan.ps1` | Done (8/8 steps) |
| 02 | query-flags | Add dependency + untested-domain query flags answering the navigation questions without grep | `dev/CATALOG/scripts/query.ps1` | Done (7/7 steps) |
| 03 | docs-catalog-cleanup | Document the enriched schema in the catalog README; dev-log placeholder | `dev/CATALOG/README.md` | Done (5/5 steps) |

Phase 03 is always final.

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker below is unchecked. Each maps to a strategic `Status: Open` research item or to the premise contradiction.

- [x] **BLK-01 - Field-name decision for test coverage.** The spec mandates creating `hasTests`, but `hasTests` already exists. Owner must confirm one of: (a) keep the name `hasTests` and reinterpret "create" as "harden" (default assumed by this plan); (b) rename to a new field and keep `hasTests` as an append-only alias. Resolution sets whether Phase 01 touches only the extraction logic or also introduces a second field name. Source: S0314 §2.1 / §11.1 vs codebase; S0311 §3.3 / §5.2.
- [x] **BLK-02 - Dependency field naming and extraction scope (S0314 §6 / S0311 §6, `Status: Open`).** Owner must confirm the field name and what it captures. Default assumed by this plan: field `constructorDeps`, capturing all constructor parameter types, reusing the `Get-Injected` parsing style, imports excluded. Alternatives on record: `dependsOn`, `repositoryDeps`, source-tagged multi-field (`import` vs `constructor` vs `injected`). Resolution sets the Phase 01 field name and the Phase 02 filter name.

Resolution of a blocker is recorded in the Blockers Log with date and decision, then its checkbox is ticked.

---

## Completion Gate

S0314 is complete only when all of the following hold:

- All three phases meet their Phase Done Criteria. — MET (01: 8/8, 02: 7/7, 03: 5/5).
- A `-NoProfile` scan dry-run on `app_v2` produces records carrying both the test-coverage field and the new dependency field; exit code 0. — MET: scan exit 0, 1234 files -> 1500 records; `RECORDS_MISSING_constructorDeps=0`, `RECORDS_MISSING_hasTests=0`; 806 non-empty `constructorDeps`, 317 `hasTests=true`.
- A `-NoProfile` query dry-run on `app_v2` answers an untested-domain-class question and a dependency question, each returning at least one record from a fixture-free real scan, without invoking `rg` / `grep` / `Select-String`; exit code 0. — MET: `-Layer domain -NoTests` -> 165 rows (0 violations); `-DependsOn ResourceDao` -> 7 rows (0 violations); child-process exit 0; no grep used.
- Existing record field names (`path`, `class`, `layer`, `loc`, `lastTouched`, `noFlavors`, `injected`, `hasTests`, `coroutines`, `usesTimber`, `sideEffects`, `userFeedback`, `status`, `role`, `functions`) are all still present and unchanged in name (append-only proven). — MET: serialized field order `..,injected,constructorDeps,hasTests,..`; `MISSING_ORIGINAL_FIELDS=` (empty).
- A record carrying a manual `role` and a manual `status` survives a rescan unchanged (manual-preservation proven). — MET: sentinel role+status on `AppShortcutsManager.kt` survived rescan (`RESULT=PASS`); `constructorDeps` recomputed, not copied.
- App (`app_v2`) and Wear (`wear`) JSONL outputs remain separate files; no record crosses modules. — MET: `wear.jsonl` separate (81 records); `app_records_with_wear_path=0`. (Wear index still on the pre-enrichment schema until its own `scan.ps1 -Module wear` regen — owner-run, gitignored index.)
- `dev/CATALOG/README.md` documents every new field and query flag. — MET: `constructorDeps` row (auto), hardened `hasTests` row, `-DependsOn` filter + example, append-only/ADR-1 note.

---

## How to Track Progress

- Each phase file carries a `## Steps` checklist and a `## Phase Done Criteria` block.
- A step that edits a `.ps1` records `expected: X | actual: Y` for its structural check and the dry-run exit code, per CLAUDE.md Validation Requirements.
- Mark a phase `Done` in the Phase Overview table only after every step box is ticked and the Phase Done Criteria invariants are all satisfied.
- `dev/CATALOG/<module>.jsonl` and `.md` are gitignored regenerated indexes - never wait on a git commit for them and never assert them as committed artefacts.
- The owner (this plan's caller) runs `scan.ps1` / `query.ps1` / `catalog_sync.ps1`; phase steps that "verify a scan" describe the command and its expected output, they do not require the spec author to mutate the local catalogue.

---

## Blockers Log

| ID | Opened | Status | Decision |
|----|--------|--------|----------|
| BLK-01 | 2026-05-31 | Resolved | Owner accepted default (§0 autonomy): keep `hasTests`, harden extraction for flavor roots, no rename |
| BLK-02 | 2026-05-31 | Resolved | Owner accepted default (§0 autonomy): `constructorDeps` (all ctor param types, `Get-Injected` style, imports excluded) |

---

## Change Log

- 2026-05-31 - Tactical plan created by `/spec-tech` (Claude Opus 4.8). 3 phases. Recorded premise correction: `hasTests` already exists end-to-end, so Phase 01 hardens rather than creates it (BLK-01). Default dependency schema = additive `constructorDeps` reusing `Get-Injected` style, imports excluded (BLK-02).
