# Tactical Plan: S1627 - strings-thirteen-locales-by-default

**Strategic spec:** [`../S1627_strings-thirteen-locales-by-default.md`](../S1627_strings-thirteen-locales-by-default.md)
**Research inputs:** none - both §6 items were resolved inline in the strategic spec, one by design choice and one by measurement
**Feature:** A UI string reaches the release in all thirteen declared locales, enforced at the pre-release stage
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Locale set

Thirteen declared locales: strict `en`, `ru`, `uk` plus best-effort `ar`, `bn`, `de`, `es`, `fr`, `hi`, `it`, `pt`, `ur`, `zh-Hans`.
No script in this plan hard-codes that list - all of them read `res/xml/locales_config.xml` through `scripts/utils/locale-set.ps1`, which is what keeps strategic §11 criterion 5 true.

---

## Measured baseline (2026-08-14)

- `app_v2` `main` corpus: 4468 keys counted by `check_strings_localized.ps1`; per-locale untranslated 89-100 across the ten best-effort locales.
- That residue is 91 keys never exported by design (88 symbol-only values, one carrying escaped markup, plus flavor-set equivalents) and 19 keys rejected by the placeholder guard, owned by `S1626`.
- Flavor sets: `vr` 56 keys, `noLegal` 17 keys in two files, all ten locales present since 2026-08-14.
- `wear` module: 96 keys, only `values-ru` and `values-uk` exist. Out of scope - `S1628`.
- Existing route: `locale-bulk-export.ps1` produces the flat file, `locale-bulk-import.ps1` returns it per locale with a line-count refusal and a per-line placeholder check.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | new-lexeme-list | - | ✅ Done | 2/2 | [PHASE_01__new-lexeme-list.md](PHASE_01__new-lexeme-list.md) |
| 02 | prerelease-gate | 01 | ✅ Done | 2/2 | [PHASE_02__prerelease-gate.md](PHASE_02__prerelease-gate.md) |
| 03 | everyday-signal | 01 | ✅ Done | 2/2 | [PHASE_03__everyday-signal.md](PHASE_03__everyday-signal.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase 01 produces the baseline and the list producer; phases 02 and 03 both consume them and are independent of each other.

Phase 01 shipped 2 steps rather than the planned 3: measurement removed the exporter change the first step called for. The exporter's skip set never reaches the export, so subtracting it would have been a no-op; the baseline holds the 19 identities that *are* exported and untranslated instead. Recorded in that phase's Objective.

---

## Pre-Implementation Blockers

None. Both strategic §6 items are Resolved - the historical boundary is a named baseline file (§6.1), and `wear` is out of scope and parked as `S1628` (§6.2).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; strategic §8 states no new capability appears. `docs/ALL_FEATURES.jsonl` likewise: this ticket ships a development-process gate, not a user-visible capability.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin touched.
- [ ] `/spec-check S1627` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1627`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-14 - All four phases implemented by `/spec-all`. Phase 01 re-planned mid-flight on a measurement; `S1629` parked for an unrelated dead string key surfaced by `.\a.ps1 fg`.
