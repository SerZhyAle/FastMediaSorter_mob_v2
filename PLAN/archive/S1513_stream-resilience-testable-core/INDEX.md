# Tactical Plan: S1513 - stream-resilience-testable-core

**Strategic spec:** [`../S1513_stream-resilience-testable-core.md`](../S1513_stream-resilience-testable-core.md)
**Research inputs:** none as files - strategic §4 carries the AS-IS survey taken from the tree on 2026-08-11
**Feature:** Stream resilience rules as a pure, unit-testable core
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 65
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resilience-primitives | - | ✅ Done | 3/3 | [PHASE_01__resilience-primitives.md](PHASE_01__resilience-primitives.md) |
| 02 | stall-rule | 01 | ✅ Done | 4/4 | [PHASE_02__stall-rule.md](PHASE_02__stall-rule.md) |
| 03 | video-retry-policy | 01 | ✅ Done | 3/3 | [PHASE_03__video-retry-policy.md](PHASE_03__video-retry-policy.md) |
| 04 | audio-retry-policies | 01 | ✅ Done | 4/4 | [PHASE_04__audio-retry-policies.md](PHASE_04__audio-retry-policies.md) |
| 05 | docs-catalog-cleanup | 01, 02, 03, 04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - every strategic §6 item is Resolved (2026-08-11).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds public classes.
- [ ] `/spec-check S1513` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1513`.

---

## Ordering rationale

Phase 01 fixes the primitives every later phase consumes. Phases 02-04 are independent of each other - each
extracts one site's decision and rewires that site alone, so any one of them can land without the others.
Phase 02 comes first among them because S1467 waits on it (strategic §6.1).

**The characterization rule, binding on phases 02-04.** In each of those phases the test that pins today's
behaviour is written and passing BEFORE the production code moves. Strategic §2 forbids a behaviour change,
and a test written after the move can only prove the new code agrees with itself.

---

## Blockers Log

- none.

---

## Change Log

- 2026-08-11 - Initial tactical plan authored by `/spec-tech`.
