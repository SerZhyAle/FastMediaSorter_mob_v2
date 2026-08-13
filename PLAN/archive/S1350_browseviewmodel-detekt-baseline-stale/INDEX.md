# Tactical Plan: S1350 - browseviewmodel-detekt-baseline-stale

**Strategic spec:** [`../S1350_browseviewmodel-detekt-baseline-stale.md`](../S1350_browseviewmodel-detekt-baseline-stale.md)
**Research inputs:** none - both §6 research items resolved inline in the strategic spec (§5.1 grouping table, §7 risk row 4); no separate research artifact file.
**Feature:** BrowseViewModel LongParameterList structural fix (dependency-holder pattern)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-02

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dependency-holder-declarations | - | ✅ Done | 1/1 | [PHASE_01__dependency-holder-declarations.md](PHASE_01__dependency-holder-declarations.md) |
| 02 | constructor-rewire | 01 | ✅ Done | 3/3 | [PHASE_02__constructor-rewire.md](PHASE_02__constructor-rewire.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 research items are `Status: Resolved`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped, strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed (6 new classes).
- [x] `/spec-check S1350` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1350`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-02 - Initial tactical plan authored by `/spec-tech`.
