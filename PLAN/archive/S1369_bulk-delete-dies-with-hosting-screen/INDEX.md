# Tactical Plan: S1369 - bulk-delete-dies-with-hosting-screen

**Strategic spec:** [`../S1369_bulk-delete-dies-with-hosting-screen.md`](../S1369_bulk-delete-dies-with-hosting-screen.md)
**Feature:** Persistent Browse bulk deletion
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 3 done
**Last updated:** 2026-08-03

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | worker-delete-contract | - | Not started | 0/3 | [PHASE_01__worker-delete-contract.md](PHASE_01__worker-delete-contract.md) |
| 02 | browse-delete-handoff | 01 | Not started | 0/3 | [PHASE_02__browse-delete-handoff.md](PHASE_02__browse-delete-handoff.md) |
| 03 | regression-cleanup | 02 | Not started | 0/3 | [PHASE_03__regression-cleanup.md](PHASE_03__regression-cleanup.md) |

## Pre-Implementation Blockers

- [x] Research: real Browse deletion path and current worker capabilities are recorded in `temp/S1369/research.md`.

## Completion Gate

- [ ] All phases show Done.
- [ ] Kotlin catalog regenerated for `app_v2` if public symbols change.
- [ ] Dev log has one entry for this ticket.
- [ ] `/spec-check S1369` returns `Verified` or documents the device-only verification gate.

## Change Log

- 2026-08-03 - Tactical plan authored by `/spec-all`.

