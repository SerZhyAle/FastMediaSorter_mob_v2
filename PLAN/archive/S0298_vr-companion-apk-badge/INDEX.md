# Tactical Plan: S0298 - vr-companion-apk-badge

**Strategic spec:** [../S0298_vr-companion-apk-badge.md](../S0298_vr-companion-apk-badge.md)
**Feature:** VR companion APK badge in noLegal Browse
**Tier:** 3 - Strategic
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | browse-hooks | - | ✅ Done | 2/2 | [PHASE_01__browse-hooks.md](PHASE_01__browse-hooks.md) |
| 02 | apk-classification | 01 | ✅ Done | 2/2 | [PHASE_02__apk-classification.md](PHASE_02__apk-classification.md) |
| 03 | nolegal-badge-ui | 02 | ✅ Done | 2/2 | [PHASE_03__nolegal-badge-ui.md](PHASE_03__nolegal-badge-ui.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **UI clarification:** badge placement, visibility, fallback UX, and accessibility are fixed in strategic §0 and mockups exist in `temp/sketches/S0298_*.png`.
- [x] **Flavor boundary:** `src/main` stays on interface/no-op/IDs only; noLegal owns classification runtime and layout overrides.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `/spec-check S0298` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- 2026-05-25 - Initial tactical plan authored by `/spec-tech`-equivalent execution.

---

## Change Log

- 2026-05-25 - Initial tactical plan authored for implementation handoff.
