# Tactical Plan: S0442 - settings-pages-rename-regroup

**Strategic spec:** [`../S0442_settings-pages-rename-regroup.md`](../S0442_settings-pages-rename-regroup.md)
**Research inputs:** none
**Feature:** Settings pages rename and groups reorganization (Playback→Player, Operations→Management)
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** In Progress
**Phases:** 5 / 5 done
**Last updated:** 2026-06-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings-and-resources | - | ✅ Done | 4/4 | [PHASE_01__strings-and-resources.md](PHASE_01__strings-and-resources.md) |
| 02 | viewmodel-reset-methods | - | ✅ Done | 3/3 | [PHASE_02__viewmodel-reset-methods.md](PHASE_02__viewmodel-reset-methods.md) |
| 03 | operations-layout-and-code | 01, 02 | ✅ Done | 8/8 | [PHASE_03__operations-layout-and-code.md](PHASE_03__operations-layout-and-code.md) |
| 04 | playback-layout-and-code | 03 | ✅ Done | 6/6 | [PHASE_04__playback-layout-and-code.md](PHASE_04__playback-layout-and-code.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` / `_RU.md` / `_UK.md` — **skip**: strategic §8 states "Без изменений"; docs rewrite is a separate future task.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public methods added to SettingsViewModel).
- [ ] `/spec-check S0442` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0442`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-06-15 - Initial tactical plan authored by `/spec-tech`.
