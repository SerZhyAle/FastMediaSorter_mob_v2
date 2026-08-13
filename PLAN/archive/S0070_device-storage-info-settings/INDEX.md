# Tactical Plan: S0070 — device-storage-info-settings

**Strategic spec:** [`../S0070_device-storage-info-settings.md`](../S0070_device-storage-info-settings.md)
**Feature:** Device storage availability indicator in General Settings with manual refresh.
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-usecase-and-viewmodel | — | ✅ Done | 4/4 | [PHASE_01__domain-usecase-and-viewmodel.md](PHASE_01__domain-usecase-and-viewmodel.md) |
| 02 | fragment-integration | 01 | ✅ Done | 3/3 | [PHASE_02__fragment-integration.md](PHASE_02__fragment-integration.md) |
| 03 | ui-layout | 02 | ✅ Done | 3/3 | [PHASE_03__ui-layout.md](PHASE_03__ui-layout.md) |
| 04 | string-resources | 01 | ✅ Done | 3/3 | [PHASE_04__string-resources.md](PHASE_04__string-resources.md) |
| 05 | docs-catalog-cleanup | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Error handling behavior — **Resolved 2026-05-03:** show "Unavailable" text + log error via Timber. Panel remains visible on error.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [ ] `dev/CHANGELOG.md` has entries for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if `GetDeviceStorageUseCase` is new public API.
- [ ] `/spec-check S0070` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0070`.

---

## Blockers Log

(none yet)

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-03 — Blocker resolved: error handling → show "Unavailable" + Timber log.
