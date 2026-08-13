# Tactical Plan: S0351 - widget-audio-now-playing

**Strategic spec:** [`../S0351_widget-audio-now-playing.md`](../S0351_widget-audio-now-playing.md)
**Feature:** Audio Now Playing widget
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. The first version is service-owned, snapshot-backed, and avoids provider-side heavy bitmap work.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | widget-surface | - | Done | 3/3 | [PHASE_01__widget-surface.md](PHASE_01__widget-surface.md) |
| 02 | service-snapshot | 01 | Done | 3/3 | [PHASE_02__service-snapshot.md](PHASE_02__service-snapshot.md) |
| 03 | provider-manifest | 02 | Done | 3/3 | [PHASE_03__provider-manifest.md](PHASE_03__provider-manifest.md) |
| 04 | docs-catalog-cleanup | 03 | Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

---

## Pre-Implementation Blockers

None. Strategic research items are resolved in `PLAN/S0351_widget-audio-now-playing.md`.

---

## Completion Gate

- [x] All phases show Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Standard debug build passes.
- [x] `/spec-check S0351` records final status.

---

## Blockers Log

- 2026-06-04 - Favorite action is bounded by safe snapshot identity; unavailable identity disables the action instead of inserting incomplete favorites.

---

## Change Log

- 2026-06-04 - Initial tactical plan authored by `/spec-all`.
