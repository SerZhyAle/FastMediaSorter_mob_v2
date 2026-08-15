# Tactical Plan: S0417 - bugfix-batch-rename-cloud-network

**Strategic spec:** [`../S0417_bugfix-batch-rename-cloud-network.md`](../S0417_bugfix-batch-rename-cloud-network.md)
**Research inputs:** [`research/01__reuse-dialog-vs-patch-executor.md`](research/01__reuse-dialog-vs-patch-executor.md), [`research/02__undo-for-network-cloud.md`](research/02__undo-for-network-cloud.md)
**Feature:** Batch rename for cloud and network resources
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 90
**Status:** In Progress
**Phases:** 2 / 2 done
**Last updated:** 2026-06-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | unified-rename-executor | - | ✅ Done | 3/3 | [PHASE_01__unified-rename-executor.md](PHASE_01__unified-rename-executor.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Reuse common dialog vs patch executor - resolved in [`research/01`](research/01__reuse-dialog-vs-patch-executor.md). Decision: keep the current dialog, swap only the executor.
- [x] **Research:** Undo for network/cloud rename - resolved in [`research/02`](research/02__undo-for-network-cloud.md). Decision: route undo through the use case; record `(currentPath, originalName)` pairs.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `UndoCallbacks` changed).
- [ ] `/spec-check S0417` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0417`.

---

## Blockers Log

- none

---

## Change Log

- 2026-06-14 - Initial tactical plan authored by `/spec-tech`.
