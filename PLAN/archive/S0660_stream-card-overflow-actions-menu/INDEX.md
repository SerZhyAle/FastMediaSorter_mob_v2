# Tactical Plan: S0660 - stream-card-overflow-actions-menu

**Strategic spec:** [`../S0660_stream-card-overflow-actions-menu.md`](../S0660_stream-card-overflow-actions-menu.md)
**Research inputs:** none (5 §6 forks resolved via `/spec-quiz`, recorded in strategic §3.3 + Quiz decisions)
**Feature:** Меню действий на карточке трансляции (overflow command surface)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 4 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | edit-data-path | - | ✅ Done | 3/3 | [PHASE_01__edit-data-path.md](PHASE_01__edit-data-path.md) |
| 02 | menu-strings | - | ✅ Done | 1/1 | [PHASE_02__menu-strings.md](PHASE_02__menu-strings.md) |
| 03 | overflow-commands | 01, 02 | ✅ Done | 3/3 | [PHASE_03__overflow-commands.md](PHASE_03__overflow-commands.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All five strategic §6 research items are `Status: Resolved` (see strategic §3.3 + Quiz decisions block). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here; capability recorded in `docs/ALL_FEATURES.jsonl` (FEATURES showcase is `/skill-release`-owned per CLAUDE.md §11).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `UpdateStreamSourceUseCase` is public API).
- [ ] `/spec-check S0660` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec blocks, set the journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0660`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
