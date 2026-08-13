# S0120 — Memory Growth Endurance Scenarios: Tactical Plan

**Ticket:** S0120  
**Status:** Done  
**Phases:** 4 / 4 done  
**Last updated:** 2026-05-09  
**Strategic spec:** `PLAN/S0120_memory-growth-endurance-scenarios.md`

## Goal

Establish a repeatable endurance-testing framework for memory stability across image, audio, video, and large-folder browse/sort scenarios. Deliver a debug-only `MemoryEnduranceTracker` that logs structured checkpoint data to Logcat, wire it into the four target surfaces, and produce a runbook for manual verification. Device test results drive follow-up tickets for any failing surface.

## Phases

| # | File | Scope | Status |
|---|------|-------|--------|
| 1 | [phase-1-contract.md](phase-1-contract.md) | Pass/fail contract + scenario matrix (docs) | [x] done |
| 2 | [phase-2-tracker.md](phase-2-tracker.md) | `MemoryEnduranceTracker` implementation | [x] done |
| 3 | [phase-3-integration.md](phase-3-integration.md) | Wire checkpoints into player/viewer/browse | [x] done |
| 4 | [phase-4-runbook.md](phase-4-runbook.md) | Manual test runbook (authored; device execution remains manual) | [x] done |

## Pass/fail contract summary

- **PLATEAU**: retained heap delta < 15% between cycles after 30 s cooldown.
- **SUSPICIOUS**: delta 15–40%, stabilises after 3+ cycles.
- **FAIL**: delta > 40% per cycle OR monotonic growth across 5+ consecutive cycles.

## Follow-up tickets

Failing surfaces get individual follow-up tickets after Phase 4. No blanket refactor in this ticket.

## Closed state

Phase 4 is complete because the runbook document is authored.

Future on-device sweeps remain manual and use the runbook as a follow-up artifact; they do not reopen the tactical plan.

## Revision History

- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: consistency)
	- Applied: 1. Proposed (DISCUSS): 0.
