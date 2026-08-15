# Tactical Plan: S1325 - folder-selection-copy-move

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Research inputs:** [`research/01__saf-tree-destination.md`](research/01__saf-tree-destination.md), [`research/02__local-write-path-parity.md`](research/02__local-write-path-parity.md), [`research/03__current-state-directory-ops.md`](research/03__current-state-directory-ops.md), [`research/04__cancellation-semantics.md`](research/04__cancellation-semantics.md)
**Feature:** Folder rows behave like file rows - selection, per-row operations menu, recursive copy and move across every resource type
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | item-operation-policy | - | ✅ Done | 3/3 | [PHASE_01__item-operation-policy.md](PHASE_01__item-operation-policy.md) |
| 02 | directory-operation-guards | 01 | ✅ Done | 5/5 | [PHASE_02__directory-operation-guards.md](PHASE_02__directory-operation-guards.md) |
| 03 | cross-type-tree-transfer | 02 | ✅ Done | 5/5 | [PHASE_03__cross-type-tree-transfer.md](PHASE_03__cross-type-tree-transfer.md) |
| 04 | directory-progress-cancel | 03 | ✅ Done | 4/4 | [PHASE_04__directory-progress-cancel.md](PHASE_04__directory-progress-cancel.md) |
| 05 | folder-row-reachability | 01 | ✅ Done | 5/5 | [PHASE_05__folder-row-reachability.md](PHASE_05__folder-row-reachability.md) |
| 06 | folder-menu-and-destinations | 01, 02, 05 | ✅ Done | 4/4 | [PHASE_06__folder-menu-and-destinations.md](PHASE_06__folder-menu-and-destinations.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items 1, 2 and 4 are Resolved with artifacts under `research/`; no item is Open.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 declares a new capability, so the showcase sentence is owned by `/skill-release` from the `ALL_FEATURES` diff; this plan only adds the `docs/ALL_FEATURES.jsonl` record.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed.
- [ ] `/spec-check S1325` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1325`.

---

## Blockers Log

- none yet

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
