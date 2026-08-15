# Tactical Plan: S1415 - launcher-taskbar-status-area-config

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Research inputs:** [`research/03__bluetooth-state-without-permission.md`](research/03__bluetooth-state-without-permission.md)
**Feature:** Configurable status area on the launcher taskbar
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | tray-indicator-settings | - | ✅ Done | 5/5 | [PHASE_01__tray-indicator-settings.md](PHASE_01__tray-indicator-settings.md) |
| 02 | settings-toggles | 01 | ✅ Done | 4/4 | [PHASE_02__settings-toggles.md](PHASE_02__settings-toggles.md) |
| 03 | tray-slots-and-battery | 01 | ✅ Done | 6/6 | [PHASE_03__tray-slots-and-battery.md](PHASE_03__tray-slots-and-battery.md) |
| 04 | bluetooth-indicator | 03 | ✅ Done | 4/4 | [PHASE_04__bluetooth-indicator.md](PHASE_04__bluetooth-indicator.md) |
| 05 | sim-signal-indicators | 03 | ✅ Done | 6/6 | [PHASE_05__sim-signal-indicators.md](PHASE_05__sim-signal-indicators.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6.1 and §6.2 were already Resolved; §6.3 was resolved on 2026-08-06 by the research artifact
linked above - the Bluetooth state reads out of `Settings.Global.BLUETOOTH_ON` and needs no new permission.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 contains FEATURES sentence (not "Без изменений"); skip otherwise.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1415` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1415`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
