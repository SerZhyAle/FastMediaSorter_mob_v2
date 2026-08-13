# Tactical Plan: S1596 - ticket-session-bootstrap

**Strategic spec:** [`../S1596_ticket-session-bootstrap.md`](../S1596_ticket-session-bootstrap.md)
**Research inputs:** [`research/00__as-is-chain-and-tick-mechanics.md`](research/00__as-is-chain-and-tick-mechanics.md) · [`research/01__index-edit-composition.md`](research/01__index-edit-composition.md) · [`research/02__bootstrap-block-boundary.md`](research/02__bootstrap-block-boundary.md) · [`research/03__dead-continuity-layer.md`](research/03__dead-continuity-layer.md) · [`research/04__execution-trace-channel.md`](research/04__execution-trace-channel.md) · [`research/05__standalone-call-compat.md`](research/05__standalone-call-compat.md) · [`research/06__facade-internal-composition.md`](research/06__facade-internal-composition.md)
**Feature:** Ticket session bootstrap package and batch tactical-plan tick
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | session-bootstrap-facade | - | ✅ Done | 4/4 | [PHASE_01__session-bootstrap-facade.md](PHASE_01__session-bootstrap-facade.md) |
| 02 | batch-plan-tick | - | ✅ Done | 6/6 | [PHASE_02__batch-plan-tick.md](PHASE_02__batch-plan-tick.md) |
| 03 | dead-continuity-removal | - | ✅ Done | 2/2 | [PHASE_03__dead-continuity-removal.md](PHASE_03__dead-continuity-removal.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01, 02 and 03 are independent by construction - they touch disjoint file sets and no phase consumes a symbol another produces. Only the cleanup phase depends on all three, because it regenerates the script cheatsheet from whatever the script set ends up being.

---

## Pre-Implementation Blockers

- [x] None. All six strategic §6 research items are `Resolved` as of 2026-08-12; their artifacts are listed above and are mandatory reading before Phase 01 and Phase 02.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin touched.
- [x] `docs/SCRIPT_CHEATSHEET.md` regenerated and `assert-script-cheatsheet-sync.ps1` returns exit 0.
- [ ] `/spec-check S1596` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1596`.

---

## Cross-cutting invariants

- No component of the existing chain may lose or change a parameter or an exit code. The package is additive - four of the five components have live callers outside session start, and one runs outside Claude Code entirely (research 05).
- Neither new script re-implements session-id resolution, owner liveness or lock timings. Both dot-source the existing helpers (research 00 §4, strategic §3.2).
- No step in this plan touches Kotlin, resources or the build. `/build` is not a gate here; `post-change.ps1 -ChangeType Script -ScopeToFile` is.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-all` Stage F2.
