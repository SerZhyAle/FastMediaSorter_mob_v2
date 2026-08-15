# Tactical Plan: S0652 - statistics-sorted-counter-semantics

**Strategic spec:** [`../S0652_statistics-sorted-counter-semantics.md`](../S0652_statistics-sorted-counter-semantics.md)
**Research inputs:** [`research/01__stats-sorted-dataflow.md`](research/01__stats-sorted-dataflow.md)
**Feature:** Statistics "Sorted" headline metric - honest wording + flush-on-open
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 70
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | flush-on-open | - | ✅ Done | 1/1 | [PHASE_01__flush-on-open.md](PHASE_01__flush-on-open.md) |
| 02 | honest-wording | - | ✅ Done | 2/2 | [PHASE_02__honest-wording.md](PHASE_02__honest-wording.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All strategic §6 research items are Resolved (see research/01).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (GetStatisticsUseCase constructor changed).
- [x] `docs/ALL_FEATURES.jsonl` contains the S0652 fix record and passes validation.
- [ ] `/spec-check S0652` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, then run `/spec-check S0652`.

---

## Blockers Log

- 2026-06-23 - Historical blocker: `kaptGenerateStubsStandardDebugKotlin` crashed the Gradle/kapt daemon during the first `/spec-dev` run. Resolved on 2026-06-24 by rerunning the cheaper mixed gate `pwsh -NoProfile -File .\a.ps1 fc` -> PASS (`compileStandardDebugKotlin` + `processStandardDebugResources`).
- 2026-06-24 - `docs/ALL_FEATURES.jsonl` validation was briefly blocked by three pre-existing invalid ids (`s0638.*`, `s0645.*`, `s0616.*`). Normalized to area-based ids and re-ran the validator -> PASS.

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-24 - Final tactical sync: Phase 03 closed, ALL_FEATURES record added, catalog regenerated, build gate revalidated via `a.ps1 fc`.
