# Tactical Plan: S1035 - edge-gesture-config-dialog

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Research inputs:** none
**Feature:** Вынос настроек краевых жестов в отдельный диалог
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device verification - BlockNeedUserTest)
**Phases:** 6 / 6 done
**Last updated:** 2026-07-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings-and-schema-view | - | 🚧 In Progress | 1/3 | [PHASE_01__strings-and-schema-view.md](PHASE_01__strings-and-schema-view.md) |
| 02 | dialog-layout | 01 | ⬜ Not started | 0/3 | [PHASE_02__dialog-layout.md](PHASE_02__dialog-layout.md) |
| 03 | dialog-fragment-and-manager | 02 | ⬜ Not started | 0/4 | [PHASE_03__dialog-fragment-and-manager.md](PHASE_03__dialog-fragment-and-manager.md) |
| 04 | settings-tab-entry | 03 | ⬜ Not started | 0/3 | [PHASE_04__settings-tab-entry.md](PHASE_04__settings-tab-entry.md) |
| 05 | settings-search-sync | 04 | ⬜ Not started | 0/2 | [PHASE_05__settings-search-sync.md](PHASE_05__settings-search-sync.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 items Resolved by owner 2026-07-13.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here; strategic §8 candidate is `/skill-release`-owned (populated from `ALL_FEATURES` diff). Only `docs/ALL_FEATURES.jsonl` is written (Phase 06).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new dialog fragment + manager + schema view).
- [ ] Settings-doc-sync gate passes (`docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` + annotations regenerated - Rule 22).
- [ ] `/spec-check S1035` returns `Verified` (or `BlockNeedUserTest` while device verification pending).
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1035`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-13 - Initial tactical plan authored by `/spec-tech`.
