# Tactical Plan: S0259 - settings-toggle-row-general-destinations

**Strategic spec:** [`../S0259_settings-toggle-row-general-destinations.md`](../S0259_settings-toggle-row-general-destinations.md)
**Feature:** Migrate general and destinations settings screens to SettingsToggleRow
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | destinations-migration | - | ✅ Done | 4/4 | [PHASE_01__destinations-migration.md](PHASE_01__destinations-migration.md) |
| 02 | general-migration | 01 | ✅ Done | 4/4 | [PHASE_02__general-migration.md](PHASE_02__general-migration.md) |
| 03 | search-index-sweep | 02 | ✅ Done | 2/2 | [PHASE_03__search-index-sweep.md](PHASE_03__search-index-sweep.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** `TODO(S0254)` placeholders in `SettingsSearchIndex.kt` are now implementation work for this spec, not a separate unresolved design gate.
- [x] **UI:** portrait/landscape counterparts exist for both target layouts and must be migrated in the same steps.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [ ] `/spec-check S0259` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0259`.

---

## Blockers Log

- 2026-05-19 - none.

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-19 - All four implementation phases completed; awaiting `/spec-check S0259`.
