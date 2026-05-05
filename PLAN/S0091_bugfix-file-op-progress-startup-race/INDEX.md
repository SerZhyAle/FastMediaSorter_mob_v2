# Tactical Plan: S0091 — bugfix-file-op-progress-startup-race

**Strategic spec:** [`../S0091_bugfix-file-op-progress-startup-race.md`](../S0091_bugfix-file-op-progress-startup-race.md)
**Feature:** file operation progress dialog lifecycle safety
**Tier:** 1 — Quick Win
**Priority:** 95
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dialog-lifecycle-guard | — | ✅ Done | 2/2 | [PHASE_01__dialog-lifecycle-guard.md](PHASE_01__dialog-lifecycle-guard.md) |
| 02 | validation-catalog-changelog | 01 | ✅ Done | 3/3 | [PHASE_02__validation-catalog-changelog.md](PHASE_02__validation-catalog-changelog.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 is resolved and the user explicitly prioritised immediate execution.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (bugfix only; see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated after the `.kt` change.
- [ ] Focused compile validation passes for the touched Kotlin slice.
- [ ] Strategic spec `Status:` advanced to `Implemented` or `Verified` when validation is complete.
- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (bugfix only; see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated after the `.kt` change.
- [x] Focused compile validation passes for the touched Kotlin slice.
- [x] Strategic spec `Status:` advanced to `Implemented` when validation completed.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` only after verification passes.
3. On phase completion: confirm every step `[x]`, flip row to `✅ Done`, bump the phase counter.
4. If blocked: flip row to `⛔ Blocked`, log the blocker, and update catalog status if needed.

---

## Blockers Log

- *(none)*

---

## Change Log

- 2026-05-05 — Initial tactical plan authored from log-backed investigation.
- 2026-05-05 — Implementation completed: dialog guard, compile validation, dev log, and catalog refresh.