# Tactical Plan: S0146 - bugfix-player-stale-initial-file-path-reload

**Strategic spec:** [`../S0146_bugfix-player-stale-initial-file-path-reload.md`](../S0146_bugfix-player-stale-initial-file-path-reload.md)
**Feature:** Player position restore after file sort/delete — eliminate spurious cache scope mismatch and stale-path reload
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | cache-scope-and-stale-path | - | ✅ Done | 4/4 | [PHASE_01__cache-scope-and-stale-path.md](PHASE_01__cache-scope-and-stale-path.md) |
| 02 | position-fallback | 01 | ✅ Done | 2/2 | [PHASE_02__position-fallback.md](PHASE_02__position-fallback.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

---

## Pre-Implementation Blockers

All §6 research items resolved inline from code:

- [x] **§6.1 — Stale initial path source:** `initialFilePath` lives in the `PlayerMediaFilesLoader` constructor field, populated from `SavedStateHandle` once at ViewModel init. Survives rotation (ViewModel lives on), and survives process-death recreation (Hilt re-populates from Intent extras). Fix: `initialFilePathIsStale` boolean field on the loader; resets on ViewModel recreation, prevents repeat mismatch within the same session.
- [x] **§6.2 — Real scope-mismatch criterion:** if `cachedFiles` contains the *current* (actually-playing) file, the cache covers the right scope even when `initialFilePath` is absent. Fix: add `cacheMatchesCurrentFile` check alongside the existing `cacheMatchesInitialFile`.
- [x] **§6.3 — Nearest-by-order fallback:** `stateFlow.value.currentIndex` (captured before reload) is already available without disk I/O. `currentIndexBeforeReload.coerceIn(0, size-1)` replaces the unconditional `0` fallback.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` not required — no user-facing feature (§8 of strategic spec).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (PlayerMediaFilesLoader modified).
- [ ] `/spec-check S0146` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0146`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-10 - Initial tactical plan authored by `/spec-all`.
