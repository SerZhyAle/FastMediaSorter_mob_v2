# Tactical Plan: S0440 - settings-declaration-docs

**Strategic spec:** [`../S0440_settings-declaration-docs.md`](../S0440_settings-declaration-docs.md)
**Research inputs:** none
**Feature:** Settings reference docs caught up to actual UI, kept in sync by a drift gate
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented
**Phases:** 5 / 5 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-export | - | ✅ Done | 4/4 | [PHASE_01__manifest-export.md](PHASE_01__manifest-export.md) |
| 02 | annotations-coverage | 01 | ✅ Done | 3/3 | [PHASE_02__annotations-coverage.md](PHASE_02__annotations-coverage.md) |
| 03 | reference-generation | 01, 02 | ✅ Done | 4/4 | [PHASE_03__reference-generation.md](PHASE_03__reference-generation.md) |
| 04 | drift-gate | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__drift-gate.md](PHASE_04__drift-gate.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No `Open` research items in strategic §6 (all resolved at approval). No blockers.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] ~~`docs/FEATURES.md` + `_RU.md` + `_UK.md` updated~~ - SUPERSEDED by CLAUDE.md §11: `FEATURES*` is `/skill-release`-owned, never per-spec. Capability recorded in `docs/ALL_FEATURES.jsonl` (`settings-navigation.settings-reference-page`); `/skill-release` will emit the FEATURES sentence from the inventory diff.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (test/util classes live in src/test - not indexed by the production-source scanner by design).
- [ ] `/spec-check S0440` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0440`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
