# Tactical Plan: S1168 - webcam-resource-source-category

**Strategic spec:** [`../S1168_webcam-resource-source-category.md`](../S1168_webcam-resource-source-category.md)
**Research inputs:** none (findings folded into strategic §4/§9)
**Feature:** Topic facet exposed as a streams filter control
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | filter-state-and-session | - | ✅ Done | 3/3 | [PHASE_01__filter-state-and-session.md](PHASE_01__filter-state-and-session.md) |
| 02 | filter-dialog-topic-row | 01 | ✅ Done | 4/4 | [PHASE_02__filter-dialog-topic-row.md](PHASE_02__filter-dialog-topic-row.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no open research items.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped here; strategic §8 text is consumed by `/skill-release` from the `ALL_FEATURES` diff, not edited per-spec.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1168` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1168`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
