# Tactical Plan: S0820 - video-fullscreen-open-option

**Strategic spec:** [`../S0820_video-fullscreen-open-option.md`](../S0820_video-fullscreen-open-option.md)
**Research inputs:** [`research/01__player-launch-fullscreen-integration-point.md`](research/01__player-launch-fullscreen-integration-point.md), [`research/02__device-profile-defaults-and-migration.md`](research/02__device-profile-defaults-and-migration.md)
**Feature:** Open video files in fullscreen mode (Video settings toggle)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-07-02

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-model-persistence | - | ✅ Done | 4/4 | [PHASE_01__settings-model-persistence.md](PHASE_01__settings-model-persistence.md) |
| 02 | device-profile-defaults | 01 | ✅ Done | 3/3 | [PHASE_02__device-profile-defaults.md](PHASE_02__device-profile-defaults.md) |
| 03 | video-settings-ui | 01 | ✅ Done | 4/4 | [PHASE_03__video-settings-ui.md](PHASE_03__video-settings-ui.md) |
| 04 | player-launch-fullscreen-gate | 01 | ✅ Done | 3/3 | [PHASE_04__player-launch-fullscreen-gate.md](PHASE_04__player-launch-fullscreen-gate.md) |
| 05 | docs-catalog-cleanup | 01,02,03,04 | ✅ Done | 5/5 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase 02, 03 and 04 each depend only on Phase 01 (the shared `AppSettings` field) - they touch disjoint files and carry no ordering constraint relative to each other beyond that.

---

## Pre-Implementation Blockers

None - both strategic §6 research items are `Resolved` (see research artifacts linked above).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched directly by this plan; `/skill-release` picks up the `docs/ALL_FEATURES.jsonl` record added in Phase 05.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via dev log).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 05).
- [ ] `/spec-check S0820` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0820`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-07-02 - Initial tactical plan authored by `/spec-tech`.
