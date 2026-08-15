# Tactical Plan: S0104 — playback-order-mode

**Strategic spec:** [`../S0104_playback-order-mode.md`](../S0104_playback-order-mode.md)
**Feature:** Playback Order Mode cycling button in the player command panel
**Tier:** 2 — Easy
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 4 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | model-and-prefs | — | ✅ Done | 4/4 | [PHASE_01__model-and-prefs.md](PHASE_01__model-and-prefs.md) |
| 02 | navigation-logic | 01 | ✅ Done | 4/4 | [PHASE_02__navigation-logic.md](PHASE_02__navigation-logic.md) |
| 03 | ui-button-and-icon | 02 | ✅ Done | 8/8 | [PHASE_03__ui-button-and-icon.md](PHASE_03__ui-button-and-icon.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No unchecked blockers — both §6 research items in the strategic spec are resolved:

- **§6.1 Button placement** — Resolved: add separate `btnPlaybackOrderCmd` as first button in the adaptive center group of `topCommandPanel`; `btnSlideShow` (in the secondary bottom bar) is untouched.
- **§6.2 SHUFFLE + audio service** — Resolved: ViewModel manages shuffle indices for all manual navigation; `AudioServiceController.applyPlaybackOrderMode()` sets `shuffleModeEnabled`+`repeatMode` on the MediaController so background auto-advance also shuffles. Manual prev/next in service mode goes through `PlayerNavigationCoordinator`, which updates the file index, and the service is reloaded at that index via the normal `playAudioPlaylistWithMetadata` idempotency path.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0104` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0104`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
