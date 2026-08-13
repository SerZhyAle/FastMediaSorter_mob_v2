# Tactical Plan: S0050 — player-black-screen-mode

**Strategic spec:** [`../S0050_player-black-screen-mode.md`](../S0050_player-black-screen-mode.md)
**Feature:** Black Screen button in audio/video player
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Implemented
**Phases:** 5 / 5 done
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-foundation | — | ✅ Done | 5/5 | [PHASE_01__settings-foundation.md](PHASE_01__settings-foundation.md) |
| 02 | command-panel-button | 01 | ✅ Done | 6/6 | [PHASE_02__command-panel-button.md](PHASE_02__command-panel-button.md) |
| 03 | black-screen-overlay | 02 | ✅ Done | 5/5 | [PHASE_03__black-screen-overlay.md](PHASE_03__black-screen-overlay.md) |
| 04 | keybinding-registration | 03 | ✅ Done | 4/4 | [PHASE_04__keybinding-registration.md](PHASE_04__keybinding-registration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved. No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (§8 of strategic spec).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0050` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0050`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-02 — Initial tactical plan authored by `/spec-tech`.
