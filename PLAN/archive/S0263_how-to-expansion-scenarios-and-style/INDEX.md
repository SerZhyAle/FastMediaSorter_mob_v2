# Tactical Plan: S0263 - how-to-expansion-scenarios-and-style

**Strategic spec:** [`../S0263_how-to-expansion-scenarios-and-style.md`](../S0263_how-to-expansion-scenarios-and-style.md)
**Feature:** HOW_TO expansion with scenario diversity and richer editorial structure
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | english-how-to-expansion | - | ✅ Done | 2/2 | [PHASE_01__english-how-to-expansion.md](PHASE_01__english-how-to-expansion.md) |
| 02 | localized-mirrors | 01 | ✅ Done | 2/2 | [PHASE_02__localized-mirrors.md](PHASE_02__localized-mirrors.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** First-wave scope fixed at 8 new scenarios. See strategic §6.1.
- [x] **Research:** Editorial diversity fixed at 4 canonical patterns. See strategic §6.2.
- [x] **Research:** Priority scenario set fixed as a balanced portfolio. See strategic §6.3.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` update evaluated against strategic §8 and skipped as not required.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check <S0263>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0263>`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - All tactical phases completed; ready for `/spec-check`.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - Documentation implementation completed across EN/RU/UK HOW_TO mirrors.
