# Tactical Plan: S0054 — m2ts-playback-support

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Feature:** Full .m2ts / Blu-ray Transport Stream playback
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 7 done (all steps done; phases 03/04/06/07 pending manual device tests)
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ts-format-detector | — | ✅ Done | 3/3 | [PHASE_01__ts-format-detector.md](PHASE_01__ts-format-detector.md) |
| 02 | unified-bd-ts-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__unified-bd-ts-wiring.md](PHASE_02__unified-bd-ts-wiring.md) |
| 03 | local-playback-fix | 01, 02 | 🚧 In Progress | 1/1 | [PHASE_03__local-playback-fix.md](PHASE_03__local-playback-fix.md) |
| 04 | cloud-playback-fix | 01, 02 | 🚧 In Progress | 2/2 | [PHASE_04__cloud-playback-fix.md](PHASE_04__cloud-playback-fix.md) |
| 05 | string-fixes | — | ✅ Done | 1/1 | [PHASE_05__string-fixes.md](PHASE_05__string-fixes.md) |
| 06 | audio-track-diagnostics | 05 | 🚧 In Progress | 2/2 | [PHASE_06__audio-track-diagnostics.md](PHASE_06__audio-track-diagnostics.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items (Q1–Q6) resolved 2026-05-03. No blockers before Phase 01.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0054` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0054`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-04 — Field-log follow-up: scoped adjacent non-TS defects into S0076 (DVD VOB playback route) and S0077 (BDMV/DVD thumbnail routing); S0054 verification remains limited to TS containers only.

---

## Revision History

- **2026-05-04** — by `/spec-update` (`GPT-5.4`, focus: consistency, completeness)
	- Applied: 2. Proposed (DISCUSS): 0.
	- Notes: Tactical index now points to adjacent follow-up tickets from the 2026-05-04 field log without changing S0054 phase structure.
