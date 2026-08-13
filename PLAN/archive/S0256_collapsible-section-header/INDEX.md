# Tactical Plan: S0256 — collapsible-section-header

**Strategic spec:** [`../S0256_collapsible-section-header.md`](../S0256_collapsible-section-header.md)
**Feature:** Unified expandable group header with optional help icon
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation | — | ✅ Done | 6/6 | [PHASE_01__foundation.md](PHASE_01__foundation.md) |
| 02 | settings-migration | 01 | ✅ Done | 6/6 | [PHASE_02__settings-migration.md](PHASE_02__settings-migration.md) |
| 03 | addresource-migration | 01 | ✅ Done | 3/3 | [PHASE_03__addresource-migration.md](PHASE_03__addresource-migration.md) |
| 04 | resourceeditor-migration | 01 | ✅ Done | 3/3 | [PHASE_04__resourceeditor-migration.md](PHASE_04__resourceeditor-migration.md) |
| 05 | player-and-dialog-migration | 01 | ✅ Done | 3/3 | [PHASE_05__player-and-dialog-migration.md](PHASE_05__player-and-dialog-migration.md) |
| 06 | item-level-and-programmatic | 01 | ✅ Done | 3/3 | [PHASE_06__item-level-and-programmatic.md](PHASE_06__item-level-and-programmatic.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 02–06 are independent of each other after Phase 01 is done; they may be executed in any order, but each landing PR should keep its phase atomic.

---

## Pre-Implementation Blockers

All §6 research items in the strategic spec are `Resolved`. No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — **no update required** (strategic §8 says "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Release-note line added describing the unified header style switch (visible visual change on screens that today use chevron-style or compact headers).
- [ ] `/spec-check S0256` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Inventory artefact

A frozen inventory of all collapsible groups in the app at the moment this plan was authored is available at:

`temp/research/collapsible_groups_inventory.md`

It is not part of the spec — it is a reference for developers executing phases 02–06.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0256`.

---

## Blockers Log

(empty)

---

## Change Log

- 2026-05-19 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-19 — Phase 01 implemented: component, tests, catalog sync, and dev log completed.
- 2026-05-19 — Phase 04 implemented in code: ResourceEditor headers migrated, residual dropdown icon reuse removed, catalog sync completed.
- 2026-05-19 — Phase 05 implemented in code: Player Copy/Move headers and ScheduledOperationDialog conditions header migrated, catalog sync completed.
- 2026-05-19 — Phase 06 implemented in code: DuplicateGroupAdapter and KeybindingListAdapter migrated; component accessibility-label API extended with test coverage.
- 2026-05-19 — Phase 07 completed: final audits passed, unused indicator resources removed, release notes updated, tactical plan closed.
- 2026-05-19 — Phase 01 closed as Done. Build gate failure was caused by pre-existing broken tests outside the S0256 scope (known repo-level technical debt — `testStandardDebugUnitTest` carries ~26 broken tests unrelated to S0256). S0256 own tests pass; `assembleStandardDebug` compiles cleanly.
- 2026-05-19 — Phase files 02–07 authored.
- 2026-05-19 — Phase 02.2 corrected: stale `headerGridView` reference removed after validating current Playback settings layout against the codebase.
