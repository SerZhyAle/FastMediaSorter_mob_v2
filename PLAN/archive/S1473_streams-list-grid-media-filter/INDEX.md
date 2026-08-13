# Tactical Plan: S1473 - streams-list-grid-media-filter

**Strategic spec:** [`../S1473_streams-list-grid-media-filter.md`](../S1473_streams-list-grid-media-filter.md)
**Research inputs:** [`research/01__landscape-command-label-width.md`](research/01__landscape-command-label-width.md) · [`research/02__active-state-color-token.md`](research/02__active-state-color-token.md) · [`research/03__facet-application-and-focus-order.md`](research/03__facet-application-and-focus-order.md)
**Feature:** Streams window - command-row overflow, landscape command labels, inline media-kind filter
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | command-row-overflow | - | ✅ Done | 3/3 | [PHASE_01__command-row-overflow.md](PHASE_01__command-row-overflow.md) |
| 02 | landscape-command-labels | 01 | ✅ Done | 3/3 | [PHASE_02__landscape-command-labels.md](PHASE_02__landscape-command-labels.md) |
| 03 | single-facet-apply | - | ✅ Done | 2/2 | [PHASE_03__single-facet-apply.md](PHASE_03__single-facet-apply.md) |
| 04 | inline-media-kind-trigger | 03 | ✅ Done | 6/6 | [PHASE_04__inline-media-kind-trigger.md](PHASE_04__inline-media-kind-trigger.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 research items are Resolved and their artifacts are linked above.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 names a user-visible capability, which is recorded in `docs/ALL_FEATURES.jsonl` and published by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - Phase 02 and Phase 04 add classes.
- [ ] `/spec-check S1473` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1473`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-tech`.
