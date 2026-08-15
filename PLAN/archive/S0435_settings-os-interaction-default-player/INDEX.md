# Tactical Plan: S0435 - settings-os-interaction-default-player

**Strategic spec:** [`../S0435_settings-os-interaction-default-player.md`](../S0435_settings-os-interaction-default-player.md)
**Research inputs:** [`research/01__settings-registration-reuse.md`](research/01__settings-registration-reuse.md)
**Feature:** Default-player registration in settings + OS-interaction group
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 70
**Status:** Done (awaiting on-device test - journal: BlockNeedUserTest)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 2/2 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | layout-restructure | 01 | ✅ Done | 2/2 | [PHASE_02__layout-restructure.md](PHASE_02__layout-restructure.md) |
| 03 | manager-fragment-wiring | 02 | ✅ Done | 4/4 | [PHASE_03__manager-fragment-wiring.md](PHASE_03__manager-fragment-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research blockers. Strategic §6 item 1 is Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager class).
- [ ] `/spec-check S0435` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0435`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-15 - Initial tactical plan authored by `/spec-tech`.
