# Tactical Plan: S0045 — per-resource-sort-type-memory

**Strategic spec:** [`../S0045_per-resource-sort-type-memory.md`](../S0045_per-resource-sort-type-memory.md)
**Feature:** Per-resource sort type memory — save and restore sort mode per resource across Browse and Slideshow sessions
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Architecture Note (required reading before Phase 01)

Code audit found that Browse already fully persists sort mode:
- `BrowseSortFilterManager.setSortMode()` → `UpdateResourceUseCase` → `ResourceEntity.sortMode` in DB.
- `BrowseResourceLoadManager.loadResource()` reads `resource.sortMode` and sets it as initial state.

**The actual gap is in `PlayerMediaFilesLoader` slow path (cold start, no cache).**

`PlayerMediaFilesLoader.loadMediaFiles()` calls `GetMediaFilesUseCase` without a `sortMode` parameter,
so the use case defaults to `SortMode.NAME_ASC` regardless of what is stored in `ResourceEntity.sortMode`.
This affects: direct Slideshow launch from resource list (without Browse pre-loading the cache), app restart,
or any other path where `MediaFilesCacheManager` is empty for the resource.

Cache-hit path (Browse → Player) is already correct: Browse sorts and caches, Player reuses the cache.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | player-cold-start-sort | — | ✅ Done | 1/1 | [PHASE_01__player-cold-start-sort.md](PHASE_01__player-cold-start-sort.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers — §6 open questions do not affect the identified implementation path:
- **§6.1 Direction (asc/desc):** Not applicable — `SortMode` enum already encodes direction (e.g. `NAME_ASC`, `NAME_DESC`). Both type and direction are persisted as a single `SortMode` value. No separate storage needed.
- **§6.2 Manual mode:** `SortMode.MANUAL` is a valid enum value. Persisting and reading it follows the same code path as any other sort mode.
- **§6.3 Resource key:** Already using `resource.id: Long` (Room primary key) — the same stable identifier already used by all other per-resource settings.
- **§6.4 Ad-hoc opens:** `StandalonePlayerViewModel` / direct intent path — out of scope for this spec. No stable resource id in that path.
- **§6.5 Global reset UI:** Not requested. Out of scope.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (sort-memory bullet — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (no new public API, but `PlayerMediaFilesLoader` was modified).
- [ ] `/spec-check S0045` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0045`.

---

## Blockers Log

_No blockers recorded._

---

## Change Log

- 2026-05-01 — Initial tactical plan authored by `/spec-tech`. Architecture audit confirmed Browse save/restore already works; single gap identified in `PlayerMediaFilesLoader` slow path.
- 2026-05-02 — Both phases executed and verified by `/spec-check` (Verified, PASS 9/0/0). Owner answers to §6 incorporated into strategic spec; ADR-2 revised to reflect that `SortMode` enum already encodes type + direction + MANUAL.
