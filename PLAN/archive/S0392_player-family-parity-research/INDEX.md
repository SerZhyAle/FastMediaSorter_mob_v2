# Tactical Plan: S0392 - player-family-parity-research

**Strategic spec:** [`../S0392_player-family-parity-research.md`](../S0392_player-family-parity-research.md)
**Feature:** Research the full functional divergence of the standalone player family vs the in-app etalon, then a prioritized catch-up roadmap.
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Implemented - MATRIX.md + ROADMAP.md produced; awaiting owner review before cutting catch-up tickets
**Phases:** 2 / 2 done
**Last updated:** 2026-06-10

> **Scope:** This spec's deliverable is research, not feature code. Phase A produces a verifiable divergence matrix; Phase B produces a catch-up roadmap (tickets + host-seam fundamental). The catch-up implementation itself is the spawned tickets, not this spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Deliverable | File |
|---|-------|-----------|--------|-------------|------|
| A | divergence-map | - | ✅ Done | [`MATRIX.md`](MATRIX.md) | [PHASE_A__divergence-map.md](PHASE_A__divergence-map.md) |
| B | catchup-roadmap | A | ✅ Done | [`ROADMAP.md`](ROADMAP.md) | [PHASE_B__catchup-roadmap.md](PHASE_B__catchup-roadmap.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done`

---

## Completion Gate

- [x] `MATRIX.md` exists: capability × host × status × blocker, covering command panel + type-specific actions + navigation/paging + input/gestures.
- [x] `ROADMAP.md` exists: prioritized catch-up tickets (R0-R8), host-seam fundamental first, each item classified cheap-now vs needs-seam.
- [x] Strategic spec §14 + §6 research items resolved from the matrix.
- [ ] Owner reviews matrix + roadmap before any catch-up ticket is cut.

---

## Method

- Phase A is fed by two read-only research passes (in-app etalon inventory + standalone-hosts inventory), synthesized into `MATRIX.md`.
- Phase B groups matrix gaps into tickets, ordered by value × cost, with the binding-agnostic host-seam as the enabling fundamental.

---

## Change Log

- 2026-06-10 - Tactical plan authored; Phase A research dispatched (2 parallel read-only agents).
