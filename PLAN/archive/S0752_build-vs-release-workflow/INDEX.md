# Tactical Plan: S0752 - build-vs-release-workflow

**Strategic spec:** [`../S0752_build-vs-release-workflow.md`](../S0752_build-vs-release-workflow.md)
**Research inputs:** [`research/01__ci-triggers-cost-map.md`](research/01__ci-triggers-cost-map.md), [`research/02__main-push-guard-mechanism.md`](research/02__main-push-guard-mechanism.md)
**Feature:** Build vs Release workflow concepts
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-27

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ci-trigger-cleanup | - | ✅ Done | 1/1 | [PHASE_01__ci-trigger-cleanup.md](PHASE_01__ci-trigger-cleanup.md) |
| 02 | main-push-guard | - | ✅ Done | 2/2 | [PHASE_02__main-push-guard.md](PHASE_02__main-push-guard.md) |
| 03 | workflow-glossary-doc | 01, 02 | ✅ Done | 1/1 | [PHASE_03__workflow-glossary-doc.md](PHASE_03__workflow-glossary-doc.md) |
| 04 | skill-glossary-links | 03 | ✅ Done | 2/2 | [PHASE_04__skill-glossary-links.md](PHASE_04__skill-glossary-links.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 1/1 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 research items are Resolved (see `research/01`, `research/02`).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - no update (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` - no regen (no Kotlin/public-API change).
- [ ] `/spec-check S0752` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0752`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-27 - Initial tactical plan authored by `/spec-tech`.
