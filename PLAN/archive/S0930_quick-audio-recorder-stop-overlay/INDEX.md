# Tactical Plan: S0930 - quick-audio-recorder-stop-overlay

**Strategic spec:** [`../S0930_quick-audio-recorder-stop-overlay.md`](../S0930_quick-audio-recorder-stop-overlay.md)
**Research inputs:** [`research/01__overlay-hosting-mechanism.md`](research/01__overlay-hosting-mechanism.md)
**Feature:** Floating stop indicator for the headless quick audio recorder
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-04

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 2/2 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | flavor-overlay-impl | 01 | ✅ Done | 2/2 | [PHASE_02__flavor-overlay-impl.md](PHASE_02__flavor-overlay-impl.md) |
| 03 | service-wiring | 01, 02 | ✅ Done | 2/2 | [PHASE_03__service-wiring.md](PHASE_03__service-wiring.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are `Resolved` (see research artifact above).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 has a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added).
- [ ] `/spec-check S0930` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0930`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-tech`.
