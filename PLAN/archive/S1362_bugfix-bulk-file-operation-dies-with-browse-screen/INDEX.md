# Tactical Plan: S1362 - browse background transfer survives lifecycle cleanup

**Strategic spec:** [`../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md`](../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md)
**Research inputs:** [`research/01__browse-shutdown-worker-cancellation.md`](research/01__browse-shutdown-worker-cancellation.md)
**Feature:** Browse background transfer lifecycle fix
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** In progress
**Phases:** 2 / 3 done
**Last updated:** 2026-08-03

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | transfer-aware-shutdown | - | ✅ Done | 2/2 | [PHASE_01__transfer-aware-shutdown.md](PHASE_01__transfer-aware-shutdown.md) |
| 02 | transfer-aware-startup | 01 | ✅ Done | 1/1 | [PHASE_02__transfer-aware-startup.md](PHASE_02__transfer-aware-startup.md) |
| 03 | docs-catalog-cleanup | 01,02 | 🔄 In progress | 1/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

## Pre-Implementation Blockers

- [x] **Research:** Browse shutdown and worker cancellation. See strategic §6.1.

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `/spec-check S1362` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

## Blockers Log

- none

## Change Log

- 2026-08-03 - Initial tactical plan authored by `/spec-next`.
