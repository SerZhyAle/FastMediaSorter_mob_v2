# Tactical Plan: S0114 — bugfix-smb-scan-subfolders-flat-list

**Strategic spec:** [`../S0114_bugfix-smb-scan-subfolders-flat-list.md`](../S0114_bugfix-smb-scan-subfolders-flat-list.md)
**Feature:** SMB flat list shows only root files when scan-subfolders is enabled
**Tier:** 1 — Quick Win
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | orphaned-job-fix | — | ✅ Done | 3/3 | [PHASE_01__orphaned-job-fix.md](PHASE_01__orphaned-job-fix.md) |
| 02 | cache-scan-mode-invalidation | 01 | ✅ Done | 3/3 | [PHASE_02__cache-scan-mode-invalidation.md](PHASE_02__cache-scan-mode-invalidation.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research §6.1:** Does `CachedFileListEntity` store the `scanSubdirectories` flag used during the last scan? — required before Phase 02. See strategic §6.1.
  - **Answer:** No — entity has only `resourceId`, `compressedData`, `fileCount`, `lastScanTimestamp`, `lastModifiedFolder`. The flag is absent. Phase 02 invalidates via procedural cache clear at save time (no new column needed).
- [ ] **Research §6.2:** Is the previous `loadFilesJob` cancelled before a new scan starts in `BrowseResourceLoadManager.loadMediaFiles()`? — required before Phase 01. See strategic §6.2.
  - **Answer:** No — `loadMediaFiles()` calls `setLoadFilesJobRef(filesJob)` without cancelling the prior job first. The ViewModel only cancels in `onCleared()`, `cancelScan()`, and `reloadFiles()`. Fix: add internal `currentScanJob` field to `BrowseResourceLoadManager`.

Both research items are **resolved** above. No blockers remain.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update (bug fix, no new user-facing feature; see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [ ] `/spec-check S0114` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/3 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0114`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-08 — Initial tactical plan authored by `/spec-tech`.
