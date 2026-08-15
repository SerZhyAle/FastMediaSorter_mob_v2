# Tactical Plan: S0318 - playback-other-functionality-group

**Strategic spec:** [`../S0318_playback-other-functionality-group.md`](../S0318_playback-other-functionality-group.md)
**Feature:** «Прочий функционал» group in playback settings
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-31

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 1/1 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | regroup-layout | 01 | ✅ Done | 4/4 | [PHASE_02__regroup-layout.md](PHASE_02__regroup-layout.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items resolved by owner 2026-05-31. No open blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Fragment touched).
- [ ] `/spec-check S0318` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0318`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-31 - Initial tactical plan authored by `/spec-tech`.
