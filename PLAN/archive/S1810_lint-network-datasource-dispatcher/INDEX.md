# Tactical Plan: S1810 - lint-network-datasource-dispatcher

**Strategic spec:** [`../S1810_lint-network-datasource-dispatcher.md`](../S1810_lint-network-datasource-dispatcher.md)
**Research inputs:** none (settled in strategic spec §6)
**Feature:** Lint detector for unconfined network data source calls
**Tier:** 3 - Tactical (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-19

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | detector-and-registry | - | ✅ Done | 3/3 | [PHASE_01__detector-and-registry.md](PHASE_01__detector-and-registry.md) |
| 02 | unit-tests | 01 | ✅ Done | 4/4 | [PHASE_02__unit-tests.md](PHASE_02__unit-tests.md) |
| 03 | codebase-validation-and-closure | 02 | ✅ Done | 3/3 | [PHASE_03__codebase-validation-and-closure.md](PHASE_03__codebase-validation-and-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. S1812 is Verified and research items 1-3 are Resolved.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `.\a.ps1 flr` exits 0 (all lint-rules tests pass).
- [x] `/spec-check S1810` returns `Verified` (or `Implemented`).
- [x] Strategic spec `Status:` advanced to `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1810`.

---

## Blockers Log

- 2026-08-19 - Unblocked: S1812 reached Verified.

---

## Change Log

- 2026-08-19 - Initial tactical plan authored by `/spec-all`.
