# Tactical Plan: S0328 - color-theme-setting

**Strategic spec:** [`../S0328_color-theme-setting.md`](../S0328_color-theme-setting.md)
**Feature:** Цветовая тема интерфейса (Auto / Light / Dark)
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Implemented
**Phases:** 4 / 4 done
**Last updated:** 2026-06-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-persistence | - | ✅ Done | 4/4 | [PHASE_01__domain-persistence.md](PHASE_01__domain-persistence.md) |
| 02 | theme-apply-point | 01 | ✅ Done | 2/2 | [PHASE_02__theme-apply-point.md](PHASE_02__theme-apply-point.md) |
| 03 | settings-ui | 02 | ✅ Done | 4/4 | [PHASE_03__settings-ui.md](PHASE_03__settings-ui.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Все исследовательские пункты стратегической §6 разрешены (Resolved) - блокеров нет.

- [x] **Research:** Поведение применения темы - resolved (apply-on-restart, существующий паттерн). См. стратегическую §6.1 / ADR-2.
- [x] **Research:** Форма контрола - resolved (Spinner как у языка). См. стратегическую §6.2.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - обновить (strategic §8 предписывает новую способность).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0328` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0328`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-01 - Initial tactical plan authored by `/spec-tech`.
