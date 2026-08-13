# Tactical Plan: S0106 — player-image-crop

**Strategic spec:** [`../S0106_player-image-crop.md`](../S0106_player-image-crop.md)
**Feature:** Image Crop & Compress in Player
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Implemented
**Phases:** 5 / 5 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | — | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | crop-engine | 01 | ✅ Done | 5/5 | [PHASE_02__crop-engine.md](PHASE_02__crop-engine.md) |
| 03 | crop-overlay-view | 01 | ✅ Done | 3/3 | [PHASE_03__crop-overlay-view.md](PHASE_03__crop-overlay-view.md) |
| 04 | player-integration | 02, 03 | ✅ Done | 6/6 | [PHASE_04__player-integration.md](PHASE_04__player-integration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

_(none — all §6 research items resolved)_

> **§6.1 resolved:** do NOT copy EXIF tags to crop/compress output. EXIF orientation is still read for coordinate mapping but not written to the output file.
> **§6.2 resolved (revised 2026-05-06):** if the new file lands in the same parent folder (writable source) — player reloads and navigates to the new file. If the file lands in Downloads (read-only source) — show Toast `"Файл <name> создан"` and remain on the original.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 05).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 05).
- [ ] `/spec-check S0106` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0106`.

---

## Blockers Log

_(none yet)_

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
