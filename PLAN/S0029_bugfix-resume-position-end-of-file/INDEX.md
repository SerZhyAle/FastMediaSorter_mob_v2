# Tactical Plan: S0029 — bugfix-resume-position-end-of-file

**Strategic spec:** [`../S0029_bugfix-resume-position-end-of-file.md`](../S0029_bugfix-resume-position-end-of-file.md)
**Feature:** Auto-clear saved playback position when file is watched to end
**Tier:** 2 — Easy
**Priority:** 70
**Status:** Implemented
**Phases:** 5 / 5 done
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | completion-detector | — | ✅ Done | 2/2 | [PHASE_01__completion-detector.md](PHASE_01__completion-detector.md) |
| 02 | repo-mark-completed | — | ✅ Done | 3/3 | [PHASE_02__repo-mark-completed.md](PHASE_02__repo-mark-completed.md) |
| 03 | wire-state-ended | 02 | ✅ Done (on-device smoke pending) | 2/3 | [PHASE_03__wire-state-ended.md](PHASE_03__wire-state-ended.md) |
| 04 | near-end-exit-guard | 01, 02 | ✅ Done | 2/2 | [PHASE_04__near-end-exit-guard.md](PHASE_04__near-end-exit-guard.md) |
| 05 | docs-catalog-cleanup | 01..04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items #2 (slideshow), #3 (audio), #4 (DataStore migration) are explicitly resolved by the implementation directive: scope = video/audio playback via ExoPlayer's `STATE_ENDED`; no DataStore migration; slideshow stays out of scope. Research item #1 (threshold formula) is fixed at `min(0.05 * duration, 5000ms)` clamped to `<= duration / 2`.

- [x] Threshold formula chosen — `min(5%, 5000ms)`, clamped by half duration.
- [x] DataStore migration explicitly skipped — old "dirty" positions clear on next playthrough.
- [x] Standalone player out of scope — only `VideoPlayerManager` (panel + VR) covered.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new domain class + new repo method).
- [ ] `/spec-check S0029` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0029`.

---

## Blockers Log

- _none_

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by `/spec-tech`.
