# Tactical Plan: S0488 - pin-all-files-resource-first

**Strategic spec:** [`../S0488_pin-all-files-resource-first.md`](../S0488_pin-all-files-resource-first.md)
**Research inputs:** [`research/01__top-entry-precedence.md`](research/01__top-entry-precedence.md), [`research/02__drag-gesture-pinned-item.md`](research/02__drag-gesture-pinned-item.md)
**Feature:** Ресурс «Все файлы» всегда первым в списке ресурсов
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 3 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ordering-pin | - | ✅ Done | 3/3 | [PHASE_01__ordering-pin.md](PHASE_01__ordering-pin.md) |
| 02 | drag-robustness | 01 | ✅ Done | 3/3 | [PHASE_02__drag-robustness.md](PHASE_02__drag-robustness.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 research items are Resolved (see `research/01__top-entry-precedence.md`, `research/02__drag-gesture-pinned-item.md`).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 is "Без изменений".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public extension on `MediaResource`).
- [ ] `/spec-check S0488` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0488`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
