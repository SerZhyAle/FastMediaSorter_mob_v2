# Tactical Plan: S1359 - minigame-restart-level-command

**Strategic spec:** [`../S1359_minigame-restart-level-command.md`](../S1359_minigame-restart-level-command.md)
**Research inputs:** none - all three §6 items were Resolved before planning.
**Feature:** Restart the current mini-game level on demand
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | engine-voluntary-restart | - | ✅ Done | 2/2 | [PHASE_01__engine-voluntary-restart.md](PHASE_01__engine-voluntary-restart.md) |
| 02 | viewmodel-command | 01 | ✅ Done | 1/1 | [PHASE_02__viewmodel-command.md](PHASE_02__viewmodel-command.md) |
| 03 | button-and-strings | 02 | ✅ Done | 2/2 | [PHASE_03__button-and-strings.md](PHASE_03__button-and-strings.md) |
| 04 | key-binding | 02, 03 | ✅ Done | 3/3 | [PHASE_04__key-binding.md](PHASE_04__key-binding.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 research items are Resolved.

---

## Decisions fixed by this plan

- The voluntary branch normalises the live state through the engine's own private `asGameOver()` and then calls the existing `restartLevel()`. Strategic §5 forbids faking the status from the UI, and reusing the same chain is what makes "costs exactly as much as dying" true by construction rather than by a second scoring path.
- Price equality is proven by a unit test, not only on device. `GameRulesEngineTest` already exercises `restartLevel`, so §11 criterion 2 gets mechanical evidence and only the three UI criteria go to the device.
- The button sits in the action row as the owner ruled, laid out as a second row under `btnGameReset` so the existing `GridLayout` constraint (`Top_toBottomOf="@id/btnGameMode"`) still clears every action button and no barrier is needed.
- No flavor work. Strategic §3 records that the mini-game compiles into every flavor and availability is the runtime `embeddedGameEnabled` setting, which the new command inherits unchanged.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here. Per CLAUDE.md §11 the showcase is `/skill-release`-owned; the per-spec record goes to `docs/ALL_FEATURES.jsonl` in Phase 04.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1359` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1359`.

---

## Blockers Log

- 2026-08-05 - Phase 04 held: §11 criterion 3 (reachable by D-pad and gamepad) is not met and cannot be met by the button. The game screen consumes every D-pad direction before the focus system sees it, so `nextFocus*` is inert - see PHASE_03 "Criterion 3 is not met" and strategic §6 item 4. Next: the owner picks the key and the gamepad button, then one step in `GameInputManager.handleKeyEvent` plus this phase's closure. The inventory record is deliberately not written yet - it would claim a capability whose input half is open.
- 2026-08-06 - Cleared. Owner picked `R` and `BUTTON_Y`; the binding became its own Phase 04 and the docs closure moved to Phase 05.

---

## Change Log

- 2026-08-05 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-06 - Phase 04 (key-binding) inserted after the owner resolved strategic §6 item 4; the former Phase 04 renumbered to 05.
