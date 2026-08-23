# Tactical Plan: S1840 - stream-publisher-script-oversized-untested

**Strategic spec:** [`../S1840_stream-publisher-script-oversized-untested.md`](../S1840_stream-publisher-script-oversized-untested.md)
**Research inputs:** [`research/01__publisher-boundaries-and-test-harness.md`](research/01__publisher-boundaries-and-test-harness.md)
**Feature:** Stream publisher maintainability and regression coverage
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-20

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-contracts | - | ✅ Done | 4/4 | [PHASE_01__shared-contracts.md](PHASE_01__shared-contracts.md) |
| 02 | discovery-probes | 01 | ✅ Done | 4/4 | [PHASE_02__discovery-probes.md](PHASE_02__discovery-probes.md) |
| 03 | artwork-delivery | 02 | ✅ Done | 4/4 | [PHASE_03__artwork-delivery.md](PHASE_03__artwork-delivery.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

## Pre-Implementation Blockers

- None. Research items are resolved in `research/01__publisher-boundaries-and-test-harness.md`.

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `scripts/streams/collect-stream-candidates.ps1` and every extracted module are at or below 1,500 lines.
- [x] Publisher Pester tests pass without network access.
- [x] `scripts/quality/assert-stream-asset-revisions.ps1` passes.
- [x] `/spec-check S1840` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`.
2. During phase: flip steps to `[~]` and `[x]` only through `plan-tick.ps1` after verification.
3. On phase completion: confirm all criteria, then flip row to `✅ Done`.
4. All done: run `/spec-check S1840`.

## Blockers Log

- None.

## Change Log

- 2026-08-20 - Initial tactical plan authored by `/spec-tech`.
