# Tactical Plan: S1334 - browsestatesyncmanager-detekt-baseline-stale

**Strategic spec:** [`../S1334_browsestatesyncmanager-detekt-baseline-stale.md`](../S1334_browsestatesyncmanager-detekt-baseline-stale.md)
**Research inputs:** [`research/01__dependency-holder-precedent.md`](research/01__dependency-holder-precedent.md), [`research/02__baseline-drift-classification-design.md`](research/02__baseline-drift-classification-design.md)
**Feature:** Detekt-baseline drift/dead-entry diagnostic + BrowseStateSyncManager dependency-holder fix
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 35
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | baseline-drift-audit | - | ✅ Done | 2/2 | [PHASE_01__baseline-drift-audit.md](PHASE_01__baseline-drift-audit.md) |
| 02 | dependency-holder-refactor | - | ✅ Done | 2/2 | [PHASE_02__dependency-holder-refactor.md](PHASE_02__dependency-holder-refactor.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01 and 02 are independent pillars (neither produces an artifact the other consumes) and may
be implemented in either order; numbering is not a dependency.

---

## Pre-Implementation Blockers

No blockers - both strategic §6 research items are `Resolved` (artifacts linked above).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 states no user-visible capability.
- [x] `dev/CHANGELOG.md` has entry for every modified file (via `add_to_dev_log.ps1`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - new class (`BrowseStateSyncUseCases`) and a changed constructor signature.
- [ ] `/spec-check S1334` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

No `BlockNeedUserTest` transition is planned for this ticket: both pillars are mechanically verifiable
(build success, detekt-clean, static classification output) with no new device-only-observable
behavior - `BrowseStateSyncManager`'s runtime behavior is unchanged, only its constructor shape is.
Do not insert `Timber.d("S1334: ...")` probe tags per CLAUDE.md "Debug Verification Tags" - tags bind
to `BlockNeedUserTest`, which this ticket never enters.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1334`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-01 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-01 - Phase 02 surfaced an unrelated, pre-existing drifted baseline entry on
  `BrowseViewModel`'s own constructor (same root cause, different class) - parked as **S1350**,
  out of this ticket's scope. Did not block Phase 02's completion.
