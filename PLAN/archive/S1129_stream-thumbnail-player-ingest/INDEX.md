# Tactical Plan: S1129 - stream-thumbnail-player-ingest

**Strategic spec:** [`../S1129_stream-thumbnail-player-ingest.md`](../S1129_stream-thumbnail-player-ingest.md)
**Feature:** Adopt a viewed stream frame as its grid thumbnail
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** In progress
**Phases:** 3 / 4 done
**Last updated:** 2026-07-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ingest-contract | - | ✅ Done | 4/4 | [PHASE_01__ingest-contract.md](PHASE_01__ingest-contract.md) |
| 02 | player-capture | 01 | ✅ Done | 4/4 | [PHASE_02__player-capture.md](PHASE_02__player-capture.md) |
| 03 | grid-handoff | 02 | ✅ Done | 4/4 | [PHASE_03__grid-handoff.md](PHASE_03__grid-handoff.md) |
| 04 | docs-catalog-cleanup | all | 🚧 In Progress | 0/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` - `🚧 In Progress` - `✅ Done` - `⛔ Blocked` - `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** choose the player surface capture mechanism - resolved in
  `research/01__frame-capture-seam.md`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` records the delivered capability.
- [ ] `dev/CHANGELOG.md` has the logical ticket entry.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1129` returns `Verified` or records the exact device-only gate.
- [ ] Strategic spec status is advanced by the catalog CLI.

---

## How to Track Progress

1. Mark a phase `🚧 In Progress` before its first edit.
2. Mark a step `[x] done` only after its verification passes.
3. Run the phase-boundary code audit before starting the next phase.
4. Mark a phase `✅ Done` only after every step and phase criterion passes.

---

## Blockers Log

- 2026-07-20 - Phase 04 static/build closure complete; final VIDEO/RTSP thumbnail scenario waits
  for mobile-mcp configuration. ADB launch smoke passed on `emulator-5554`.

---

## Change Log

- 2026-07-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-20 - Phase 01 completed; ingest contract, implementation, DI binding, tests, and boundary audit passed.
- 2026-07-20 - Phase 02 completed; one-shot player capture, lifecycle ownership, wiring, and minified smoke passed.
- 2026-07-20 - Phase 03 completed; result handoff, shared grid ingest, probe, and listener symmetry passed.
