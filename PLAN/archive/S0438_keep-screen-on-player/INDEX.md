# Tactical Plan: S0438 - keep-screen-on-player

**Strategic spec:** [`../S0438_keep-screen-on-player.md`](../S0438_keep-screen-on-player.md)
**Research inputs:** [`research/01__keep-screen-on-current-wiring.md`](research/01__keep-screen-on-current-wiring.md)
**Feature:** Зависимая настройка «Не выключать экран при работе плеера»
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-model-persistence | - | ✅ Done | 4/4 | [PHASE_01__settings-model-persistence.md](PHASE_01__settings-model-persistence.md) |
| 02 | effective-keep-awake | 01 | ✅ Done | 3/3 | [PHASE_02__effective-keep-awake.md](PHASE_02__effective-keep-awake.md) |
| 03 | settings-ui-dependent-row | 01 | ✅ Done | 3/3 | [PHASE_03__settings-ui-dependent-row.md](PHASE_03__settings-ui-dependent-row.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 item 1 is Resolved (see `research/01__keep-screen-on-current-wiring.md`).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0438` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0438`.

---

## Blockers Log

- None yet.

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
