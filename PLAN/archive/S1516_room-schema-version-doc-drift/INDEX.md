# Tactical Plan: S1516 - room-schema-version-doc-drift

**Strategic spec:** [`../S1516_room-schema-version-doc-drift.md`](../S1516_room-schema-version-doc-drift.md)
**Research inputs:** [`research/01__current-versus-historical-room-schema.md`](research/01__current-versus-historical-room-schema.md)
**Feature:** Room schema documentation drift protection
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | documentation-baselines | - | ✅ Done | 3/3 | [PHASE_01__documentation-baselines.md](PHASE_01__documentation-baselines.md) |
| 02 | schema-pin-regression | 01 | ✅ Done | 2/2 | [PHASE_02__schema-pin-regression.md](PHASE_02__schema-pin-regression.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 1/1 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic research item 6.1 is resolved in the linked research artifact.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged because strategic §8 says no user-facing feature was added.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `/spec-check S1516` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1516`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
