# Tactical Plan: S0242 — bugfix-browse-list-sync-after-player

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Feature:** Browse list sync after returning from Player (delete / move / rename / copy)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 90
**Status:** Done — awaiting on-device verification
**Phases:** 6 / 6 done
**Last updated:** 2026-05-18

> **Scope:** tactical, English, developer handoff. Every step has a static Verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | — | ✅ Done | 7/7 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | player-journal-wiring | 01 | ✅ Done | 5/5 | [PHASE_02__player-journal-wiring.md](PHASE_02__player-journal-wiring.md) |
| 03 | browse-reconciler-resume | 01 | ✅ Done | 5/5 | [PHASE_03__browse-reconciler-resume.md](PHASE_03__browse-reconciler-resume.md) |
| 04 | quick-verifier | 03 | ✅ Done | 5/5 | [PHASE_04__quick-verifier.md](PHASE_04__quick-verifier.md) |
| 05 | source-observer-unification | 03 | ✅ Done | 3/3 | [PHASE_05__source-observer-unification.md](PHASE_05__source-observer-unification.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 Open items that block implementation. All 5 originally Open items are Resolved inline. Item 6 is Open but does not block — its default is "do not journal save-crop / save-frame; addressed in a future ticket if priority rises".

- [x] Item 1 — Mutation Journal granularity → Resolved (in-memory singleton).
- [x] Item 2 — Reconciler vs ActivityResult → Resolved (full replacement, drop `EXTRA_MODIFIED_FILES`).
- [x] Item 3 — Cloud Quick Verifier → Resolved (N=10, FTP excluded, use existing `fileExists`).
- [x] Item 4 — Process death behavior → Resolved (journal does not survive death).
- [x] Item 5 — Copy-to-same-resource → Resolved (do not journal `Copy` as `Add`).
- [x] Item 6 — `ImageCropManager` / `SaveVideoFrameManager` save-flow → Deferred: default = exclude from this ticket scope. To be revisited as a separate Sxxxx if priority rises; tracked in S0242 final report manual items.

---

## Completion Gate

- [x] All 6 phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — NOT updated. Strategic §8 says "Без изменений в docs/FEATURES" — this is a bug fix, not a new capability.
- [x] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`) — 59 S0242 entries recorded.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scan.ps1` + `render.ps1` — 1349 records (was 1340 baseline).
- [x] `dev/FUNCTIONALITY.log` has one `FIX` entry for S0242.
- [ ] `/spec-check S0242` returns `Verified` — pending on-device verification (status `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` — pending.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0242`.

---

## Blockers Log

(none yet)

---

## Change Log

- 2026-05-18 — Phase 06 complete (4/4 steps). Catalog regenerated (1349 records). Dev log verified (59 S0242 entries). Functionality log FIX entry recorded. INDEX flipped to "Done — awaiting on-device verification"; spec status moved to `BlockNeedUserTest` with debug `Timber.d("S0242: …")` tags inserted at 4 flow entry points (Player journal write, Browse Reconciler apply, Quick Verifier probe, FileObserver mutation route).
- 2026-05-18 — Initial tactical plan authored by `/spec-tech` (within `/spec-all`).
- 2026-05-18 — Phase 02 complete (5/5 steps). Player operations now journal a `Mutation` to the shared `MutationJournal` at the moment of source-confirmed success; legacy `EXTRA_MODIFIED_FILES` intent payload, `modifiedFiles` set, and `trackModifiedFile()` helper removed.
- 2026-05-18 — Phase 03 complete (5/5 steps). `BrowseReconcilerManager` introduced as the sole consumer of `MutationJournal` on the Browse side; runs unconditionally on every `onResume` before resource-settings check. Structural-equality cache-sync fast-path removed from both `BrowseStateSyncManager` (kept slim for favorites + settings reload) and `BrowseFileListManager` (dead helper). Pull-to-refresh (`BrowseRefreshManager.launchReload`) now clears the per-resource journal before the rescan so stale entries don't re-apply.
- 2026-05-18 — Phase 04 complete (5/5 steps). Background existence probe added: `QuickVerifier` interface (`domain/verifier/`), 4 strategies (`Local/Smb/Sftp/Cloud QuickVerifier` in `data/verifier/`) delegating to the existing `*OperationStrategy.exists()`, throttled via `ConnectionThrottleManager`. `QuickVerifierDispatcher` fans out by `ResourceType` (FTP excluded per §6 Item 3, no-op). `BrowseReconcilerManager.scheduleQuickVerify()` fires after every `runReconciler()` and journals missing paths as `Mutation.Delete` for next-resume cleanup. Step 04.4 Hilt module was degenerate — all bindings via constructor injection.
- 2026-05-18 — Phase 05 complete (3/3 steps). `BrowseFileObserverManager` constructor extended with `resourceId`, `MutationJournal`, `PathNormalizer` (not Hilt-injected — manually constructed by `BrowseViewModel`, so the consumer-site call was updated in lock-step). LOCAL FileObserver `onFileDeleted` + `onFileMoved` callbacks now journal `Mutation.Delete` / `Mutation.Move` alongside the legacy live-UX paths (in-memory remove / rename preserved for immediate UI feedback). `onFileCreated` keeps the legacy `scheduleReload` (journal does not model `Add` per §6 Item 5). Audit confirms `pendingFor(...)` and `markApplied(...)` each have exactly 1 caller (`BrowseReconcilerManager.kt`) — single-reader invariant intact.
