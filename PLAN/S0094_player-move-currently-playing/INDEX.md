# Tactical Plan: S0094 — player-move-currently-playing

**Strategic spec:** [`../S0094_player-move-currently-playing.md`](../S0094_player-move-currently-playing.md)
**Feature:** Move currently-playing file — immediate stop, optimistic navigate, path-based list reconciliation
**Tier:** 3 — Moderate
**Priority:** 55
**Status:** Not started
**Phases:** 6 / 6 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | path-based-removal | — | ✅ Done | 3/3 | [PHASE_01__path-based-removal.md](PHASE_01__path-based-removal.md) |
| 02 | pre-move-stop-navigate | 01 | ✅ Done | 6/6 | [PHASE_02__pre-move-stop-navigate.md](PHASE_02__pre-move-stop-navigate.md) |
| 03 | service-silent-io-skip | — | ✅ Done | 2/2 | [PHASE_03__service-silent-io-skip.md](PHASE_03__service-silent-io-skip.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |
| 05 | pre-delete-stop-navigate | 01 | ✅ Done | 6/6 | [PHASE_05__pre-delete-stop-navigate.md](PHASE_05__pre-delete-stop-navigate.md) |
| 06 | pre-rename-in-place-update | 01 | ✅ Done | 6/6 | [PHASE_06__pre-rename-in-place-update.md](PHASE_06__pre-rename-in-place-update.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open §6 research items — all resolved before tactical authoring.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 04).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 04).
- [ ] `/spec-check S0094` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set journal status via `update.ps1 -Id S0094 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0094`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-05 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-05 — Added Phase 05 (delete) and Phase 06 (rename); phase count 4 → 6.
