# Tactical Plan: S0649 - settings-operations-additional-programs-group

**Strategic spec:** [`../S0649_settings-operations-additional-programs-group.md`](../S0649_settings-operations-additional-programs-group.md)
**Research inputs:** none
**Feature:** New collapsible "Additional programs and scenarios" group in Settings -> Operations
**Tier:** Ad-hoc settings-UI
**Priority:** 55
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | add-group-header-string | - | ✅ Done | 1/1 | [PHASE_01__add-group-header-string.md](PHASE_01__add-group-header-string.md) |
| 02 | regroup-rows-and-register | 01 | ✅ Done | 3/3 | [PHASE_02__regroup-rows-and-register.md](PHASE_02__regroup-rows-and-register.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- (none - strategic §6 items resolved via quiz 2026-06-23)

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8 contains no FEATURES sentence; pure settings regrouping, no new capability).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `/spec-check S0649` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0649`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech`.
