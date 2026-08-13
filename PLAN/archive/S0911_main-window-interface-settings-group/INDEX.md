# Tactical Plan: S0911 - main-window-interface-settings-group

**Strategic spec:** [`../S0911_main-window-interface-settings-group.md`](../S0911_main-window-interface-settings-group.md)
**Research inputs:** [`research/01__main-window-settings-candidates.md`](research/01__main-window-settings-candidates.md)
**Feature:** New "Main window interface" settings section (General tab) hosting the relocated programs-panel, streams-panel, and resource-ops-overflow toggles
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-07-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | new-section-scaffold | - | ✅ Done | 3/3 | [PHASE_01__new-section-scaffold.md](PHASE_01__new-section-scaffold.md) |
| 02 | move-resource-ops-toggle | 01 | ✅ Done | 1/1 | [PHASE_02__move-resource-ops-toggle.md](PHASE_02__move-resource-ops-toggle.md) |
| 03 | move-programs-panel-toggle | 01 | ✅ Done | 2/2 | [PHASE_03__move-programs-panel-toggle.md](PHASE_03__move-programs-panel-toggle.md) |
| 04 | move-streams-panel-toggle | 01,03 | ✅ Done | 2/2 | [PHASE_04__move-streams-panel-toggle.md](PHASE_04__move-streams-panel-toggle.md) |
| 05 | docs-catalog-cleanup | 02,03,04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 research items are `Resolved` (see research artifact).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] Strategic §8 states "Без изменений в docs/FEATURES" - `docs/FEATURES*.md` and `docs/ALL_FEATURES.jsonl` stay untouched (pure internal reorg, no new user-visible capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/settings/settings-manifest.json` and `docs/SETTINGS_REFERENCE*.md` regenerated; `docs/settings/settings-annotations.json` has an entry for the new section header.
- [ ] `/spec-check S0911` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0911`.

---

## Blockers Log

None.

---

## Change Log

- 2026-07-03 - Initial tactical plan authored by `/spec-tech`.
