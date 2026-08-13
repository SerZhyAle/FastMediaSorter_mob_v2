# Tactical Plan: S1141 - streams-split-pinned-list

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Research inputs:** [`research/01__streams-list-architecture.md`](research/01__streams-list-architecture.md)
**Feature:** Streams screen split into two stacked, independently-scrollable, collapsible sections (pinned top, main bottom)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-07-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | section-resources | - | ✅ Done | 2/2 | [PHASE_01__section-resources.md](PHASE_01__section-resources.md) |
| 02 | layout-two-sections | 01 | ✅ Done | 3/3 | [PHASE_02__layout-two-sections.md](PHASE_02__layout-two-sections.md) |
| 03 | sections-manager | 02 | ✅ Done | 4/4 | [PHASE_03__sections-manager.md](PHASE_03__sections-manager.md) |
| 04 | collapsible-headers | 03 | ✅ Done | 2/2 | [PHASE_04__collapsible-headers.md](PHASE_04__collapsible-headers.md) |
| 05 | single-playback | 03 | ✅ Done | 2/2 | [PHASE_05__single-playback.md](PHASE_05__single-playback.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 research item is Resolved (`research/01__streams-list-architecture.md`); all design details resolved by defaults recorded in strategic §6/§9.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - not touched here (strategic §8 routes the showcase through `/skill-release`; developer inventory `docs/ALL_FEATURES.jsonl` is updated in Phase 06).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class `StreamsSectionsManager`).
- [ ] `/spec-check S1141` returns `Verified` (later - after device test).
- [ ] Strategic spec `Status:` advanced by the pipeline (`BlockNeedUserTest` after Phase 06, then `/spec-check` on device pass).

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-23 - Initial tactical plan authored by `/spec-tech`.
