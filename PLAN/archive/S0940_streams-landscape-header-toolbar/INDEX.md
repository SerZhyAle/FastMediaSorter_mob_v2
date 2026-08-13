# Tactical Plan: S0940 - streams-landscape-header-toolbar

**Strategic spec:** [`../S0940_streams-landscape-header-toolbar.md`](../S0940_streams-landscape-header-toolbar.md)
**Research inputs:** [`research/03__rotation-no-recreate.md`](research/03__rotation-no-recreate.md)
**Feature:** Relocate streams controls into the header in landscape
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-04

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | header-slots | - | ✅ Done | 2/2 | [PHASE_01__header-slots.md](PHASE_01__header-slots.md) |
| 02 | placement-manager | 01 | ✅ Done | 3/3 | [PHASE_02__placement-manager.md](PHASE_02__placement-manager.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (see strategic §6; rotation mechanism in `research/03__rotation-no-recreate.md`). Exact landscape width tuning (search collapse threshold) is device-test tuning under BlockNeedUserTest, not a pre-implementation blocker.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 is "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager class = public API change).
- [ ] `/spec-check S0940` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0940`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-tech`.
