# Tactical Plan: S1094 - launcher-clock-widget-functional

**Strategic spec:** [`../S1094_launcher-clock-widget-functional.md`](../S1094_launcher-clock-widget-functional.md)
**Research inputs:** none
**Feature:** Launcher: functional, large, seconds-showing clock gadget
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 2 / 2 done
**Last updated:** 2026-07-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | clock-functional | - | ✅ Done | 6/6 | [PHASE_01__clock-functional.md](PHASE_01__clock-functional.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - S1093 (the resize mechanism this builds on) is implemented; strategic §6 lists no open research items.

---

## Design decision (non-blocking)

- **Min-span decoupled from default-span.** `LauncherGadget` gains `minSpanW`/`minSpanH` (the resize floor) with default getters returning `defaultSpanW`/`defaultSpanH`, so only the clock changes: default `4x2` (bigger seed) with min `2x1` (floor). Every other gadget keeps min = default = its current size. `LauncherResizeManager` reads the floor from `minSpan*` instead of `defaultSpan*` - this re-touches S1093's floor source (both tickets are still BlockNeedUserTest, so no released behavior changes).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip here; strategic §8 has a FEATURES sentence but FEATURES is release-owned (recorded to ALL_FEATURES in Phase 02).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (gadget contract changed).
- [ ] `/spec-check S1094` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1094`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-21 - Initial tactical plan authored by `/spec-tech`.
