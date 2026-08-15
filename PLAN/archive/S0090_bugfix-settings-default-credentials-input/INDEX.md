# Tactical Plan: S0090 - bugfix-settings-default-credentials-input

**Strategic spec:** [`../S0090_bugfix-settings-default-credentials-input.md`](../S0090_bugfix-settings-default-credentials-input.md)
**Feature:** Reliable inline editing for Default User / Default Password in General settings
**Tier:** 1 - Quick Win
**Priority:** 75
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | app-data-contract | - | ✅ Done | 2/2 | [PHASE_01__app-data-contract.md](PHASE_01__app-data-contract.md) |
| 02 | inline-editor-flow | 01 | ✅ Done | 2/2 | [PHASE_02__inline-editor-flow.md](PHASE_02__inline-editor-flow.md) |
| 03 | keyboard-guard-tests | 02 | ✅ Done | 2/2 | [PHASE_03__keyboard-guard-tests.md](PHASE_03__keyboard-guard-tests.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers - strategic §6 items were resolved inline on 2026-05-05.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` / `_RU` / `_UK` - no update required (bugfix scope only, no new user-facing affordance).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated after Kotlin file changes.
- [ ] `/spec-check S0090` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0090`.

---

## Blockers Log

- 2026-05-05 - Phase 03 blocked: `testStandardDebugUnitTest` cannot complete because unrelated `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScannerTest.kt` currently fails to compile with `No value passed for parameter 'context'`. Next: repair or isolate that pre-existing unit-test failure, then resume S0090 Phase 03 verification.

---

## Change Log

- 2026-05-05 - Initial tactical plan authored by `/spec-tech`.
