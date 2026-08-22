# Tactical Plan: S1786 - ai-agent-development-efficiency-optimization

**Strategic spec:** [`../S1786_ai-agent-development-efficiency-optimization.md`](../S1786_ai-agent-development-efficiency-optimization.md)  
**Research inputs:** none  
**Feature:** AI Agent Development Efficiency and Speed Optimization  
**Tier:** 4 - Strategic (ad-hoc)  
**Priority:** 65  
**Status:** Partial  
**Phases:** 4 / 4 (all done; S1794, S1795 now Verified)
**Last updated:** 2026-08-18

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | model-routing-and-import-sorter | - | ✅ Done | 3/3 | [PHASE_01__model-routing-and-import-sorter.md](PHASE_01__model-routing-and-import-sorter.md) |
| 02 | build-digest-and-post-change-hardening | 01 | ✅ Done | 3/3 | [PHASE_02__build-digest-and-post-change-hardening.md](PHASE_02__build-digest-and-post-change-hardening.md) |
| 03 | gates-frequency-and-timeouts | 02 | ✅ Done | 3/3 | [PHASE_03__gates-frequency-and-timeouts.md](PHASE_03__gates-frequency-and-timeouts.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research Item 01:** Six failing unit tests in `testStandardDebugUnitTest` are tracked; settings manifest re-generation and investigation are isolated (Carrier: S1789).
- [x] **Research Item 02:** 186 `post-change.ps1` failures taxonomy mapped to four distinct causes (legitimate fail, dirty-tree drift, invocation error, deletion bug S1777).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (internal tooling only, no public UI feature delta).
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [x] `/spec-check S1786` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1786`.

---

## Blockers Log

- 2026-08-18, re-verification against the live tree: two shipped steps do not work and were carried out of this ticket.
  - Step 03.1 - the `class-architecture-naming` rule cannot fire. Probe: a class without the `UseCase` suffix placed in `domain/usecase/` gave `delta 0` and `assert-source-gates: PASS`. Carrier: S1794. **Resolved:** S1794 Verified 2026-08-18.
  - Step 03.3 - `measure-gate-frequency.ps1` reads `top_commands` / `top_failing_commands`, which the metrics file does not contain, so every gate reports 0 invocations. Carrier: S1795. **Resolved:** S1795 Verified 2026-08-18.
