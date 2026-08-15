# Tactical Plan: S0375 - video-recording-destination-resource

**Strategic spec:** [../S0375_video-recording-destination-resource.md](../S0375_video-recording-destination-resource.md)
**Feature:** Video recording destination resource
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-surface | - | ✅ Done | 3/3 | [PHASE_01__settings-surface.md](PHASE_01__settings-surface.md) |
| 02 | save-routing | 01 | ✅ Done | 2/2 | [PHASE_02__save-routing.md](PHASE_02__save-routing.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Decision:** Selector lives inside `layoutVideoCaptureOptions`, below the `open in player` row, and is visible only when video recording is enabled.
- [x] **Decision:** Invalid or missing selected target degrades to the fallback label and runtime fallback; no blocking warning state.
- [x] **Decision:** Eligibility filter matches the existing capture selectors: writable, non-virtual resources only.
- [x] **Decision:** Feature/docs copy states that a usable current resource remains primary and `Movies` is the device fallback when current and selected targets are unusable.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0375` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0375`.

---

## Blockers Log

- 2026-06-07 - Initial tactical plan authored.
- 2026-06-07 - Implementation completed; awaiting `/spec-check S0375`.

---

## Change Log

- 2026-06-07 - Initial tactical plan authored for S0375.
- 2026-06-07 - All tactical phases implemented and build-validated.