# Tactical Plan: player-keybinding-remapping

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Feature:** PLAYER-KEYBINDING — Custom Playback Controls Remapping
**Tier:** 4 — Strategic (high risk)
**Status:** Not started
**Phases:** 0 / 8 done
**Last updated:** 2026-04-25

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_player-keybinding-remapping.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | preparation-inventory | — | ⬜ Not started | 0/8 | [PHASE_01__preparation-inventory.md](PHASE_01__preparation-inventory.md) |
| 02 | foundation | 01 | ⬜ Not started | 0/8 | [PHASE_02__foundation.md](PHASE_02__foundation.md) |
| 03 | keyboard-migration | 02 | ⬜ Not started | 0/6 | [PHASE_03__keyboard-migration.md](PHASE_03__keyboard-migration.md) |
| 04 | input-devices-migration | 02 | ⬜ Not started | 0/5 | [PHASE_04__input-devices-migration.md](PHASE_04__input-devices-migration.md) |
| 05 | vr-migration | 02 | ⬜ Not started | 0/4 | [PHASE_05__vr-migration.md](PHASE_05__vr-migration.md) |
| 06 | remapping-ui | 02 | ⬜ Not started | 0/7 | [PHASE_06__remapping-ui.md](PHASE_06__remapping-ui.md) |
| 07 | reset-conflict-polish | 03, 04, 05, 06 | ⬜ Not started | 0/6 | [PHASE_07__reset-conflict-polish.md](PHASE_07__reset-conflict-polish.md) |
| 08 | docs-catalog-cleanup | all | ⬜ Not started | 0/5 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

The strategic spec §10 UI Ambiguity Gate lists ten open decisions. Each must have an explicit resolution line (no `?` / `TBD` tokens) in `../spec_player-keybinding-remapping.md` §10 **before the listed phase may start**.

Phase 01 (preparation/inventory) may proceed before any of these are resolved — it is pure research.

- [ ] **Merge policy for overrides.** Replace-all vs. additive. Blocks Phase 02 (binding-resolution contract).
- [ ] **Max bindings per command per device category.** Recommended cap: two. Blocks Phase 02 (schema) and Phase 06 (row layout).
- [ ] **Conflict policy.** Block / flag / silently-overwrite. Blocks Phase 07 (conflict visualiser).
- [ ] **Capture timeout.** Indefinite or N seconds. Blocks Phase 06 (capture mode).
- [ ] **Unrecognised trigger display.** Raw code / friendly fallback / both. Blocks Phase 06 (row label).
- [ ] **Modifier capture policy.** Plain-keys-only vs. always-capture-modifiers. Blocks Phase 06 (capture mode).
- [ ] **Analog threshold UX.** Fixed vs. user-adjustable. Blocks Phase 02 (schema) and Phase 06 (row layout).
- [ ] **Reset confirmation granularity.** Per single / group / global. Blocks Phase 07 (reset UX).
- [ ] **Undo window.** Time-limited snackbar vs. immediate commit. Blocks Phase 07 (reset UX) and Phase 06 (override edit).
- [ ] **Per-profile support.** Single profile v1 — recommendation accepted? Blocks Phase 02 (persistence) and Phase 06 (UI).

---

## Completion Gate

The feature is Done when **every** item below is ticked:

- [ ] All phases show ✅ Done in the Phase Overview.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with the remapping feature entry.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (one per `add_to_dev_log.ps1` invocation).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — every new class has `role` + `status` set via `set.ps1`.
- [ ] `/spec-check player-keybinding-remapping` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. **Before starting a phase:** flip its row to `🚧 In Progress` in the Phase Overview. Update `Phases: X/N done` at the top.
2. **During a phase:** inside the phase file, flip each step's `Status:` line to `[~] in progress` when you start it, `[x] done` when its Verification passes. Never flip a step to `[x]` on intent — only on verified signal.
3. **On phase completion:** confirm every step is `[x]`, then confirm every item in the phase's "Phase Done Criteria". Flip the phase row in this INDEX to `✅ Done` and bump the counter.
4. **If blocked:** flip the row to `⛔ Blocked`, add a bullet to "Blockers Log" below with date + cause + next action.
5. **On all phases done:** flip this file's top `Status:` to `Done` and run `/spec-check player-keybinding-remapping` for the final audit.

---

## Blockers Log

*(Empty on first write — append as issues arise.)*

---

## Change Log

- 2026-04-25 — Initial tactical plan authored by `/spec-tech`.
- 2026-04-25 — Phase 01 content derived from standalone file `PLAN/spec_player-keybinding-phase1-preparation.md` (pending removal — see Proposal P-1 in that file).
