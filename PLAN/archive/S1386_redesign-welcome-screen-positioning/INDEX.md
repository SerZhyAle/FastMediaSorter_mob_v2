# Tactical Plan: S1386 - redesign-welcome-screen-positioning

**Strategic spec:** [`../S1386_redesign-welcome-screen-positioning.md`](../S1386_redesign-welcome-screen-positioning.md)
**Research inputs:** none
**Feature:** Welcome showcase states the app's roles instead of a sample of its capabilities
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-04

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | role-strings | - | ✅ Done | 3/3 | [PHASE_01__role-strings.md](PHASE_01__role-strings.md) |
| 02 | role-showcase | 01 | ✅ Done | 4/4 | [PHASE_02__role-showcase.md](PHASE_02__role-showcase.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items 1 and 2 are Resolved (owner, 2026-08-04): direction A - roles instead of capabilities; brand and protocol names stay in the tile's second line.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not updated here; strategic §8 describes a user-visible change, which reaches the showcase through `docs/ALL_FEATURES.jsonl` and `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1386` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1386`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-04 - Initial tactical plan authored by `/spec-tech`.
