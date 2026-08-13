# Tactical Plan: S1210 - edge-gesture-cancel-target-menu

**Strategic spec:** [`../S1210_edge-gesture-cancel-target-menu.md`](../S1210_edge-gesture-cancel-target-menu.md)
**Research inputs:** none
**Feature:** Explicit cancel target on the edge-gesture hint, selection committed on finger lift
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done - awaiting on-device test
**Phases:** 3 / 3 done
**Last updated:** 2026-07-27

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | hint-cancel-target | - | ✅ Done | 3/3 | [PHASE_01__hint-cancel-target.md](PHASE_01__hint-cancel-target.md) |
| 02 | selection-on-release | 01 | ✅ Done | 5/5 | [PHASE_02__selection-on-release.md](PHASE_02__selection-on-release.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 capability is recorded in `docs/ALL_FEATURES.jsonl`, the showcase is release-owned.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1210` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1210`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-27 - Initial tactical plan authored by `/spec-tech`.
