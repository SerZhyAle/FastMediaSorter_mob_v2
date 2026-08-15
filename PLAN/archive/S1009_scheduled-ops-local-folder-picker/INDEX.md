# Tactical Plan: S1009 - scheduled-ops-local-folder-picker

**Strategic spec:** [`../S1009_scheduled-ops-local-folder-picker.md`](../S1009_scheduled-ops-local-folder-picker.md)
**Research inputs:** [`research/01__hidden-resource-and-picker-audit.md`](research/01__hidden-resource-and-picker-audit.md)
**Feature:** Local folder as scheduled-op sender/receiver (hidden-resource foundation)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 5 / 5 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | hidden-resource-foundation | - | ✅ Done | 7/7 | [PHASE_01__hidden-resource-foundation.md](PHASE_01__hidden-resource-foundation.md) |
| 02 | visibility-filter | 01 | ✅ Done | 5/5 | [PHASE_02__visibility-filter.md](PHASE_02__visibility-filter.md) |
| 03 | scheduled-op-picker | 01, 02 | ✅ Done | 5/5 | [PHASE_03__scheduled-op-picker.md](PHASE_03__scheduled-op-picker.md) |
| 04 | orphan-cleanup | 01, 03 | ✅ Done | 4/4 | [PHASE_04__orphan-cleanup.md](PHASE_04__orphan-cleanup.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 research item is Resolved (items 1/2/4/5 by owner quiz 2026-07-18; items 3/6/7 confirmed in §3.3; item 8 - Home name-filter vs search - resolved 2026-07-24 by owner principle, encoded in Phase 02 Step 02.3). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` records the delivered capability (Phase 05) - `docs/FEATURES*.md` is NOT edited per-spec (it is `/skill-release`-owned, CLAUDE.md §11).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (one logical entry per phase).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new migration + use-case classes).
- [ ] Room schema `44.json` committed; `AppDatabaseSchemaExportTest` green.
- [ ] `/spec-check S1009` returns `Verified` (device-test gate applies if picker/migration need on-device confirmation).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec is blocked, set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S1009`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech` (via `/spec-all`). Migration re-planned `43 -> 44` (was `40 -> 41`); strings phase dropped (reuse existing `local_folder` + `error_folder_not_writable`); visibility filter scoped to UI use-case boundary; name-search left unfiltered per owner. Pre-existing FTS-cleanup bug parked as S1159.
