# Tactical Plan: S1601 - network-monitor-ui-polish

**Strategic spec:** [`../S1601_network-monitor-ui-polish.md`](../S1601_network-monitor-ui-polish.md)
**Research inputs:** [`research/01__current-ui-and-data-flow.md`](research/01__current-ui-and-data-flow.md)
**Feature:** Network Monitor UI polish
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | summary-session-state | - | ✅ Done | 3/3 | [PHASE_01__summary-session-state.md](PHASE_01__summary-session-state.md) |
| 02 | section-recovery-order | 01 | ✅ Done | 3/3 | [PHASE_02__section-recovery-order.md](PHASE_02__section-recovery-order.md) |
| 03 | icons-path-readability | 02 | ✅ Done | 3/3 | [PHASE_03__icons-path-readability.md](PHASE_03__icons-path-readability.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

## Pre-Implementation Blockers

- [x] **Research:** Current UI and data flow - resolved in `research/01__current-ui-and-data-flow.md`.

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged because strategic §8 excludes UX-polish entries.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] `/spec-check S1601` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

## How to Track Progress

1. Use `scripts/spec_catalog/plan-tick.ps1` after every verified step group.
2. Mark a phase done only after its build, audit and dev-log entries pass.
3. Record deferred device verification as a manual item without blocking independent phases.

## Blockers Log

- None.

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-all`.
