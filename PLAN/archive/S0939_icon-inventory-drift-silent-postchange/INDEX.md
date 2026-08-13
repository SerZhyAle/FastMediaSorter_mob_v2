# Tactical Plan: S0939 - icon-inventory-drift-silent-postchange

**Strategic spec:** [`../S0939_icon-inventory-drift-silent-postchange.md`](../S0939_icon-inventory-drift-silent-postchange.md)
**Feature:** Cheap local drift detection for settings-driven icon inventory changes
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 35
**Status:** Done
**Phases:** 1 / 1 done
**Last updated:** 2026-07-04

> **Scope:** tactical, English, developer handoff. Keep the fix cheap: no new gradle step in post-change, no schema changes, no product UI behavior changes.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-icon-gate | - | ✅ Done | 3/3 | [PHASE_01__settings-icon-gate.md](PHASE_01__settings-icon-gate.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. The fix is script/doc only and compile-free by design.

---

## Completion Gate

- [x] Phase 01 shows ✅ Done.
- [x] `assert-icon-inventory-sync.ps1` performs a cheap settings-source-vs-inventory freshness check before the heavy opt-in export test.
- [x] `post-change.ps1` runs the icon-inventory gate for settings layout edits and merged string-table edits, not only docs/icon files.
- [x] Validation commands for the touched scripts pass and are recorded in the final report.
- [x] Strategic spec status can advance beyond `Tactical` based on the validation outcome.

---

## Change Log

- 2026-07-04 - Tactical plan authored for the lightweight settings icon-inventory drift gate.
- 2026-07-04 - Phase 01 completed: cheap settings-source freshness scan added, post-change trigger widened, docs updated, validation green.
