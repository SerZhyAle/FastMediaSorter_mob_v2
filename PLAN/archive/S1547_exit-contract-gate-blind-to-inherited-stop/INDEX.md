# Tactical Plan: S1547 - exit-contract-gate-blind-to-inherited-stop

**Strategic spec:** [`../S1547_exit-contract-gate-blind-to-inherited-stop.md`](../S1547_exit-contract-gate-blind-to-inherited-stop.md)
**Research inputs:** [`research/01__inherited-stop-blind-spot.ps1`](research/01__inherited-stop-blind-spot.ps1), [`research/02__runtime-exit-code-collapse.ps1`](research/02__runtime-exit-code-collapse.ps1)
**Feature:** exit-code contract gate
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | cure-the-single-multiline-site | - | ✅ Done | 1/1 | [PHASE_01__cure-the-single-multiline-site.md](PHASE_01__cure-the-single-multiline-site.md) |
| 02 | extend-rule-a-to-inherited-stop | 01 | ✅ Done | 3/3 | [PHASE_02__extend-rule-a-to-inherited-stop.md](PHASE_02__extend-rule-a-to-inherited-stop.md) |
| 03 | regression-and-closure | 02 | ✅ Done | 3/3 | [PHASE_03__regression-and-closure.md](PHASE_03__regression-and-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are `Resolved` - the debt was measured at zero real findings and the detection strategy was chosen against that measurement.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 records no user-facing change.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin touched.
- [ ] `/spec-check S1547` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1547`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
