# Tactical Plan: S1915 - blockneedusertest-without-a-packaging-build

**Strategic spec:** [`../S1915_blockneedusertest-without-a-packaging-build.md`](../S1915_blockneedusertest-without-a-packaging-build.md)
**Research inputs:** none - the strategic §6 items were resolved by reading the facade and the closing gates directly; findings are recorded in §4 and the ADRs.
**Feature:** resource-link gate in the closure facade
**Tier:** 2 - Significant (ad-hoc)
**Priority:** 65
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resource-link-gate | - | ✅ Done | 5/5 | [PHASE_01__resource-link-gate.md](PHASE_01__resource-link-gate.md) |
| 02 | measure-and-manifest-coverage | 01 | ✅ Done | 3/3 | [PHASE_02__measure-and-manifest-coverage.md](PHASE_02__measure-and-manifest-coverage.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** strategic §6 item 5 - does the resource-processing task also fail on a malformed manifest. **Answered 2026-08-21: it does** - a broken `android:icon` reference in the manifest returned exit 1 with an AAPT line naming the manifest, so no extra task was added. Answered by measurement in Phase 02, not before Phase 01: Phase 01 ships the gate with the task list the project already calls a "resources/manifest" check, and Phase 02 either confirms that name or adds the missing task. Phase 01 does not depend on the answer.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES."
- [x] `dev/CHANGELOG.md` has entry for every modified file - seven rows carry this ticket, and each of the seven changed files appears in one of them.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin touched.
- [x] `/spec-check S1915` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1915`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech`.
