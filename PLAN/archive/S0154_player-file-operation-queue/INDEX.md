# Tactical Plan: S0154 — player-file-operation-queue

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Feature:** Очередь файловых операций (move / delete / rename) в плеере
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 70
**Status:** In Progress
**Phases:** 5 / 7 done
**Last updated:** 2026-05-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fileop-snapshot-model | — | ✅ Done | 3/3 | [PHASE_01__fileop-snapshot-model.md](PHASE_01__fileop-snapshot-model.md) |
| 02 | fileop-queue-and-consumer | 01 | ✅ Done | 4/4 | [PHASE_02__fileop-queue-and-consumer.md](PHASE_02__fileop-queue-and-consumer.md) |
| 03 | player-handler-enqueue | 02 | ✅ Done | 5/5 | [PHASE_03__player-handler-enqueue.md](PHASE_03__player-handler-enqueue.md) |
| 04 | rename-enqueue | 03 | ✅ Done | 3/3 | [PHASE_04__rename-enqueue.md](PHASE_04__rename-enqueue.md) |
| 05 | permission-and-empty-list | 03 | ✅ Done | 4/4 | [PHASE_05__permission-and-empty-list.md](PHASE_05__permission-and-empty-list.md) |
| 06 | error-projection-and-progress | 03, 05 | 🚧 In Progress | 2/4 | [PHASE_06__error-projection-and-progress.md](PHASE_06__error-projection-and-progress.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **S0152 must reach `Verified`** before Phase 01 starts. S0154 replaces the `moveInProgress` / `deleteInProgress` guard model that S0152 patches; starting earlier guarantees a merge conflict in `FileOperationsHandler.kt`. See strategic §10.
- [x] **Research §6.1 — queue lifetime scope.** Default decision recorded for this plan: queue lives at PlayerActivity session scope (variant (a)), constructed by `PlayerManagerInitializer`, dies with the activity, non-persistent. Confirm or override before Phase 02. See strategic §6.1.
- [x] **Research §6.2 — double-tap protection.** Default decision: optimistic removal-by-path is sufficient; no extra button debounce. Revisit only if device testing in `BlockNeedUserTest` shows a double-tap race. See strategic §6.2.
- [x] **Research §6.3 — error retry policy.** Default decision: manual retry only — failure projects a per-file message with a "retry" affordance that enqueues a fresh operation; no auto-retry. See strategic §6.3.

> The three research items above are resolved with documented defaults — they are not hard blockers. Only S0152 is a hard ordering blocker. Phase 01–02 may proceed once S0152 is `Verified` even if the operator wants to revisit a default later.

---

## Scope Note

First iteration targets the `FileOperationsHandler` flow used by `PlayerActivity`. `StandalonePlayerActivity` keeps its own `StandaloneFileOperationsHandler` and is **out of scope** for S0154 — track a follow-up spec if the same queueing is wanted there. Do not silently refactor the standalone handler in these phases.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` + `.md` regenerated (new `ui/player/fileops/` classes have `role` + `status` set).
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_queued_operation"` exits 0.
- [ ] `/spec-check S0154` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0154`.

---

## Blockers Log

- 2026-05-11 — Remaining tactical gap: Phase 06.3 still needs a non-modal S0074-compatible progress surface for the in-flight queued operation. Core queue behaviour, strings, build, docs, changelog, and catalog are already landed.

---

## Change Log

- 2026-05-11 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-11 — Status sync after implementation: Phases 01–05 done, Phase 06 narrowed to remaining progress-surface work, Phase 07 bookkeeping steps completed.
