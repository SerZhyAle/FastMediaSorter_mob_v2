# Tactical Plan: S1559 - listener-symmetry-full-scan-scope

**Strategic spec:** [`../S1559_listener-symmetry-full-scan-scope.md`](../S1559_listener-symmetry-full-scan-scope.md)
**Research inputs:** [`research/01__flavor-source-set-imbalance.ps1`](research/01__flavor-source-set-imbalance.ps1), [`research/02__discount-candidates.ps1`](research/02__discount-candidates.ps1)
**Feature:** listener-symmetry ratchet gate
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | safe-discount-and-benign-forms | - | ✅ Done | 2/2 | [PHASE_01__safe-discount-and-benign-forms.md](PHASE_01__safe-discount-and-benign-forms.md) |
| 02 | one-scope-rule-for-both-modes | 01 | ✅ Done | 3/3 | [PHASE_02__one-scope-rule-for-both-modes.md](PHASE_02__one-scope-rule-for-both-modes.md) |
| 03 | regression-suite-and-closure | 02 | ✅ Done | 3/3 | [PHASE_03__regression-suite-and-closure.md](PHASE_03__regression-suite-and-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are `Resolved` by measurement, and the measurement is what chose this plan's order: the discount fix must land before the scope widening, or the widened count would sit above the baseline.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 records no user-facing change.
- [ ] `dev/CHANGELOG.md` has entry for the change.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin touched.
- [ ] `/spec-check S1559` returns `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1559`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
