# Tactical Plan: S1103 - launcher-cell-actions-and-app-shortcuts (Part 1)

**Strategic spec:** [`../S1103_launcher-cell-actions-and-app-shortcuts.md`](../S1103_launcher-cell-actions-and-app-shortcuts.md)
**Research inputs:** none
**Feature:** Launcher: internal functions as desktop cells (launch panel + scheduled operations)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-07-22

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec. **Part 2 (third-party app-shortcuts) is delegated to S0427 (BlockByOtherTask) and is NOT in this plan** - strategic §11 criterion 3 stays open until S0427 ships a contract.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | launch-panel-route | - | ✅ Done | 3/3 | [PHASE_01__launch-panel-route.md](PHASE_01__launch-panel-route.md) |
| 02 | scheduled-op-command | - | ✅ Done | 3/3 | [PHASE_02__scheduled-op-command.md](PHASE_02__scheduled-op-command.md) |
| 03 | scheduled-op-ui | 02 | ✅ Done | 4/4 | [PHASE_03__scheduled-op-ui.md](PHASE_03__scheduled-op-ui.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None for Part 1. Part 2 (app-shortcuts, strategic §11.3) is out of scope - blocked on S0427.

---

## Design decisions (non-blocking)

- **Launch-panel route** is added to the shared `InternalRouteCatalog`, so it also appears in the app-launch panel's own tile editor (a self-referential "open the panel" tile). Accepted as harmless rather than adding a per-consumer visibility filter to the shared picker.
- **Scheduled-op cell** is a new `LauncherCellCommand.ScheduledOp(operationId)` (prefix `op:`) - a real per-instance parameter, so it needs its own sealed case (not an `InternalRouteCatalog` key, which carries no per-cell id). Its label is synthesized live from the operation (type + source/target resource names), since a `ScheduledOperation` persists no display name.
- **Scheduled-op tap = confirm -> background + toast** (owner 2026-07-22): a scheduled op can COPY/MOVE/DELETE files, so the tap is intercepted in the ViewModel/Activity with a confirmation dialog before `ExecuteScheduledOperationUseCase` runs in the background; a result toast follows. It never routes through the generic `ExecuteLauncherCommandUseCase.launch` (which stays a no-op branch for this command).

---

## Completion Gate

- [ ] All Part-1 phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip here; FEATURES is release-owned (recorded to ALL_FEATURES in Phase 04).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new command variant + new picker).
- [ ] `/spec-check S1103` audits Part 1; criterion 3 (app-shortcuts) noted as deferred to S0427.
- [ ] Strategic spec `Status:` set by `/spec-check` (likely `Partial` until S0427 closes criterion 3).

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1103`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-22 - Initial tactical plan authored by `/spec-tech` (Part 1 only; Part 2 -> S0427).
