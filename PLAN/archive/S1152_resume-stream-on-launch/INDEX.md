# Tactical Plan: S1152 - resume-stream-on-launch

**Strategic spec:** [`../S1152_resume-stream-on-launch.md`](../S1152_resume-stream-on-launch.md)
**Research inputs:** none
**Feature:** Resume last stream on app launch (mirror media resume)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stream-resume-store | - | ✅ Done | 4/4 | [PHASE_01__stream-resume-store.md](PHASE_01__stream-resume-store.md) |
| 02 | save-on-play | 01 | ✅ Done | 3/3 | [PHASE_02__save-on-play.md](PHASE_02__save-on-play.md) |
| 03 | resume-on-launch | 01, 02 | ✅ Done | 3/3 | [PHASE_03__resume-on-launch.md](PHASE_03__resume-on-launch.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no Open research items (design derives from established project patterns).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here (release-owned; strategic §8 sentence is consumed by `/skill-release` from the `ALL_FEATURES` diff).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S1152` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1152`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-26 - Resume policy tightened after the owner's device report (strategic §3.1 item 2): only a playing radio station is ever recorded, starting a video stream or leaving the screen with nothing playing clears the record, and a legacy non-AUDIO record left by an older build is dropped on the next launch instead of reopening the streams screen. Touches `StreamsActivity` (Phase 02 surface) and `MainResumePlaybackHelper` (Phase 03 surface); no new phase.
