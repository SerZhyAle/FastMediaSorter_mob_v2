# Tactical Plan: S1471 - stream-shortcut-starts-background-playback

**Strategic spec:** [`../S1471_stream-shortcut-starts-background-playback.md`](../S1471_stream-shortcut-starts-background-playback.md)
**Research inputs:** none - findings recorded inline in strategic §1-§3
**Feature:** Stream shortcut starts background playback instead of opening the Streams screen
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | headless-play-manager | - | ✅ Done | 2/2 | [PHASE_01__headless-play-manager.md](PHASE_01__headless-play-manager.md) |
| 02 | shortcut-trampoline | 01 | ✅ Done | 5/5 | [PHASE_02__shortcut-trampoline.md](PHASE_02__shortcut-trampoline.md) |
| 03 | launcher-tile-path | 02 | ✅ Done | 1/1 | [PHASE_03__launcher-tile-path.md](PHASE_03__launcher-tile-path.md) |
| 04 | pinned-shortcut-migration | 02 | ✅ Done | 2/2 | [PHASE_04__pinned-shortcut-migration.md](PHASE_04__pinned-shortcut-migration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - strategic §6 carries no Open research item; the code facts Phase 01 builds on were read directly and are cited in strategic §1-§2.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 mandates no showcase update for this ticket.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - two new public classes are introduced.
- [ ] `/spec-check S1471` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1471`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-09 - Phases 03-05 executed by `/spec-all`. The phase-boundary audit found four P1 defects in the Phase 01-02 code that shipped before this run, all fixed here and recorded in the strategic spec's `## Last Audit`: a permanent service binding that disarmed every `stopSelf()`, a stop branch that left the media notification up, a connection failure that left an invisible Activity resumed on top of the launcher, and a toggle that misfired throughout buffering. Ticket moved to `BlockNeedUserTest`.
