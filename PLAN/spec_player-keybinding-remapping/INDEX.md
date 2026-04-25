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
| --- | --- | --- | --- | ---: | --- |
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

All ten strategic §10 UI Ambiguity Gate items **resolved on 2026-04-25**. See `../spec_player-keybinding-remapping.md` §10 for full resolution text. Quick reference:

- [x] **Merge policy:** Replace. User binding replaces all defaults for that command on the device.
- [x] **Max bindings per command per device:** 2.
- [x] **Conflict policy:** Block at capture (commit button disabled).
- [x] **Capture timeout:** 30 seconds.
- [x] **Unrecognised trigger display:** Both label + raw code (`"Unknown keyboard key [10045]"`).
- [x] **Modifier capture policy:** Always capture modifiers.
- [x] **Analog threshold UX:** Fixed global `±0.7`.
- [x] **Reset confirmation granularity:** Single + Group instant; Global requires confirmation.
- [x] **Undo window:** Immediate commit, no undo snackbar. **→ Phase 07 Step 07.6 is `[-] skipped`.**
- [x] **Per-profile support:** Single profile in v1. No `profileId` column in schema.

No further blockers. Phase 01 may start immediately; Phases 02+ unblocked.

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

Empty on first write. Append one bullet per blocker as issues arise, format `- YYYY-MM-DD — Phase NN blocked: <cause>. Next action: <who/what/when>.`

---

## Change Log

- 2026-04-25 — Initial tactical plan authored by `/spec-tech`.
- 2026-04-25 — Phase 01 content derived from standalone file `PLAN/spec_player-keybinding-phase1-preparation.md` (pending removal — see Proposal P-1 in that file).
