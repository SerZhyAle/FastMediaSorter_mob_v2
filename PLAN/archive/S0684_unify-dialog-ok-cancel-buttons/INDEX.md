# Tactical Plan: S0684 - unify-dialog-ok-cancel-buttons

**Strategic spec:** [`../S0684_unify-dialog-ok-cancel-buttons.md`](../S0684_unify-dialog-ok-cancel-buttons.md)
**Research inputs:** [`research/01__cancel-color-and-red-semantics.md`](research/01__cancel-color-and-red-semantics.md) · [`research/03__cancel-size-single-source-lever.md`](research/03__cancel-size-single-source-lever.md) · [`research/06__standard-codification-and-gate.md`](research/06__standard-codification-and-gate.md)
**Feature:** Unified OK/Cancel dialog buttons (pink tonal smaller cancel)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | cancel-style-foundation | - | ✅ Done | 4/4 | [PHASE_01__cancel-style-foundation.md](PHASE_01__cancel-style-foundation.md) |
| 02 | codify-standard-and-gate | 01 | ✅ Done | 5/5 | [PHASE_02__codify-standard-and-gate.md](PHASE_02__codify-standard-and-gate.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (quiz 2026-06-25 + workflow research). Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - SKIP (strategic §8 = "Без изменений"; visual unification, not a new capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated only if a public Kotlin API changed (this spec is resource/doc/script-only - expected: no catalog change).
- [ ] `/spec-check S0684` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log; if the whole spec is blocked, set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0684`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`.
