# Tactical Plan: S0379 - standard-nolegal-storage-surface

**Strategic spec:** [`../S0379_standard-nolegal-storage-surface.md`](../S0379_standard-nolegal-storage-surface.md)
**Feature:** standard vs noLegal storage surface
**Tier:** 4 - Strategic, ad-hoc
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 2/2 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | saf-file-destinations | 01 | ✅ Done | 2/2 | [PHASE_02__saf-file-destinations.md](PHASE_02__saf-file-destinations.md) |
| 03 | overlay-seams | 02 | ✅ Done | 2/2 | [PHASE_03__overlay-seams.md](PHASE_03__overlay-seams.md) |
| 04 | docs-catalog-cleanup | 01-03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 items are resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated if the final implementation changes public user-facing behavior.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated if the final implementation adds noLegal-only user-facing behavior.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` changes.
- [ ] `/spec-check S0379` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0379`.

---

## Blockers Log

- 2026-06-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-07 - Phase 03 complete: added a conservative main-source restricted-tree destination seam plus a noLegal-only override; `standardDebug` and `noLegalDebug` both passed.
- 2026-06-07 - Phase 04 complete: updated public/noLegal storage docs, corrected hidden-files wording, reran catalog sync, and recorded dev-log closure for touched files.

---

## Change Log

- 2026-06-07 - Initial tactical plan authored by `/spec-tech`.
