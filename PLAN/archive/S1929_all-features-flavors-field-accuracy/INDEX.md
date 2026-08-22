# Tactical Plan: S1929 - all-features-flavors-field-accuracy

**Strategic spec:** [`../S1929_all-features-flavors-field-accuracy.md`](../S1929_all-features-flavors-field-accuracy.md)
**Research inputs:** none as files - the three §6 items were answered from the validator, the writer and the generated flavor matrix; the per-record judgement is recorded as evidence in phase 02.
**Feature:** gate-aware `flavors` validation for the feature inventory
**Tier:** 2 - Small
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | gate-field-and-check | - | ✅ Done | 4/4 | [PHASE_01__gate-field-and-check.md](PHASE_01__gate-field-and-check.md) |
| 02 | reconcile-wear-records | 01 | ✅ Done | 2/2 | [PHASE_02__reconcile-wear-records.md](PHASE_02__reconcile-wear-records.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 items are Resolved before Phase 01 starts.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: the showcase is `/skill-release`-owned and never edited per spec.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin touched.
- [x] `/spec-check S1929` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1929`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech`.
