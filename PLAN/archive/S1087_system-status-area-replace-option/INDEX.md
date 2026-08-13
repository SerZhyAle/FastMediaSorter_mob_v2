# Tactical Plan: S1087 - system-status-area-replace-option

**Strategic spec:** [`../S1087_system-status-area-replace-option.md`](../S1087_system-status-area-replace-option.md)
**Research inputs:** none
**Feature:** Launcher status-area replacement preference
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 3 done
**Last updated:** 2026-07-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | persisted-preference | - | ✅ Done | 2/2 | [PHASE_01__persisted-preference.md](PHASE_01__persisted-preference.md) |
| 02 | launcher-status-presentation | 01 | ✅ Done | 3/3 | [PHASE_02__launcher-status-presentation.md](PHASE_02__launcher-status-presentation.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

## Pre-Implementation Blockers

- [x] **Decision:** owner-approved default and mutually exclusive status-area policy are recorded in strategic §3.3 and §6.

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] Settings manifest and settings references regenerated.
- [ ] `docs/ALL_FEATURES.jsonl` contains the changed launcher capability.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated for public launcher API changes.
- [ ] `/spec-check S1087` returns `Verified`.

## Blockers Log

- None.

## Change Log

- 2026-07-18 - Initial tactical plan authored by `/spec-tech`.
