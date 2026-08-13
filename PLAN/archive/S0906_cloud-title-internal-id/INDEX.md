# Tactical Plan: S0906 - cloud-title-internal-id

**Strategic spec:** [`../S0906_cloud-title-internal-id.md`](../S0906_cloud-title-internal-id.md)
**Research inputs:** [`research/01__cloud-title-root-cause.md`](research/01__cloud-title-root-cause.md)
**Feature:** Browse screen - cloud resource title/breadcrumb display
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | navigation-name-tracking | - | ✅ Done | 2/2 | [PHASE_01__navigation-name-tracking.md](PHASE_01__navigation-name-tracking.md) |
| 02 | rewire-title-breadcrumb-consumers | 01 | ✅ Done | 3/3 | [PHASE_02__rewire-title-breadcrumb-consumers.md](PHASE_02__rewire-title-breadcrumb-consumers.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 1/1 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 research item is Resolved (see research artifact).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8: "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (no public API surface changed, but scan kept current per project convention).
- [ ] `/spec-check S0906` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0906`.

---

## Blockers Log

None.

---

## Change Log

- 2026-07-03 - Initial tactical plan authored by `/spec-tech`.
