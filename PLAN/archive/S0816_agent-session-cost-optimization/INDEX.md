# Tactical Plan: S0816 - agent-session-cost-optimization

**Strategic spec:** [`../S0816_agent-session-cost-optimization.md`](../S0816_agent-session-cost-optimization.md)
**Research inputs:** [`research/01__usage-levers-and-boundaries.md`](research/01__usage-levers-and-boundaries.md)
**Feature:** Agent session cost optimization playbook
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-30

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec. Docs-only change set - no Kotlin, no build, no device test.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | agent-cost-playbook | - | ✅ Done | 5/5 | [PHASE_01__agent-cost-playbook.md](PHASE_01__agent-cost-playbook.md) |
| 02 | discoverability-anchor | 01 | ✅ Done | 3/3 | [PHASE_02__discoverability-anchor.md](PHASE_02__discoverability-anchor.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are `Status: Resolved` (see [`research/01__usage-levers-and-boundaries.md`](research/01__usage-levers-and-boundaries.md)). No blockers.

- [x] **Research:** §6.1-§6.5 resolved from in-repo evidence.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - skip (strategic §8 = "Без изменений"; internal dev tooling, no shipped capability).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` - skip (no `.kt` public-API change).
- [ ] `/spec-check S0816` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0816`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-30 - Initial tactical plan authored by `/spec-tech`.
