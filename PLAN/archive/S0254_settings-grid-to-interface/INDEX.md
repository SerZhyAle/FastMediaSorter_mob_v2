# Tactical Plan: S0254 - settings-grid-to-interface

**Strategic spec:** [`../S0254_settings-grid-to-interface.md`](../S0254_settings-grid-to-interface.md)
**Feature:** Move all elements of Playback → Grid View block into General → Interface block, portrait + landscape.
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** ✅ Done (repair complete; awaiting on-device verification)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | add-to-interface | - | ✅ Done | 7/7 | [PHASE_01__add-to-interface.md](PHASE_01__add-to-interface.md) |
| 02 | remove-from-playback | 01 | ✅ Done | 6/6 | [PHASE_02__remove-from-playback.md](PHASE_02__remove-from-playback.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 5/5 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |
| 04 | repair-current-settings-ui | 03 | ✅ Done | 4/4 | [PHASE_04__repair-current-settings-ui.md](PHASE_04__repair-current-settings-ui.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items are either Resolved or scoped into Phase 03 as cleanup decisions. No human-input gates before Phase 01.

- Strategic §6.1 (fate of orphan string `settings_category_grid_view`) - resolved in Phase 03 by audit-then-decide.
- Strategic §6.2 (orphan `KEY_GRID_VIEW_EXPANDED` SharedPreferences key) - resolved in Phase 03 with the chosen "leave-as-is" policy; no migration code introduced.
- Strategic §6.3 - already Resolved (insert at end of containerInterface).
- Strategic §6.4 - already Resolved (move entire containerGridView contents).
- Repair Phase 04 - added after the 2026-05-30 Broken audit; no new UX decisions are open because strategic §2/§5 still define the target placement and current implementation uses `SettingsToggleRow` rows instead of the obsolete `switch*` ids.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **skip** (strategic §8 says "Без изменений в docs/FEATURES").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (touched Kotlin files: GeneralSettingsFragment, PlaybackSettingsFragment, SettingsSearchIndex).
- [ ] `dev/FUNCTIONALITY.log` has one `CHANGE` entry for S0254 (settings reorg).
- [ ] `/spec-check S0254` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0254`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-01 - Repair Phase 04 added by `/spec-update` after Broken audit; status re-opened for `/spec-dev`.

## Revision History

- **2026-06-01** - by `/spec-update` (`gpt-5-codex`, focus: tactical repair)
  - Applied: 1. Proposed (DISCUSS): 0.
  - Force-locked override: owner requested an executable repair phase for the current `SettingsToggleRow` implementation.
