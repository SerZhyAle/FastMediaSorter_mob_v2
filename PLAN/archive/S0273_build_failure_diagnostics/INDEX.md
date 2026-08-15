# Tactical Plan: S0273 - build-failure-diagnostics

**Strategic spec:** [`../S0273_build_failure_diagnostics.md`](../S0273_build_failure_diagnostics.md)
**Feature:** Build failure diagnostics
**Tier:** 2 - Moderate (developer tooling)
**Priority:** 55
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | failure-parser | - | ✅ Done | 2/2 | [PHASE_01__failure-parser.md](PHASE_01__failure-parser.md) |
| 02 | launcher-integration | 01 | ✅ Done | 2/2 | [PHASE_02__launcher-integration.md](PHASE_02__launcher-integration.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 research items were resolved in the approved strategic spec on 2026-05-20.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <S0273>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0273>`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech` via `/spec-all`. Next: execute Phase 01.
- 2026-05-20 - Phase 01 started. Next: Step 01.1.
- 2026-05-20 - Phase 01 completed. Next: Phase 02.
- 2026-05-20 - Phase 02 started. Next: Step 02.1.
- 2026-05-20 - Phase 02 completed. Next: Phase 03.
- 2026-05-20 - Phase 03 completed. Next: `/spec-check S0273`.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
