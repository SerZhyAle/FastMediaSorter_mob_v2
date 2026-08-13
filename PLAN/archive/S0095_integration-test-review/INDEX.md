# Tactical Plan: S0095 — integration-test-review

**Strategic spec:** [`../S0095_integration-test-review.md`](../S0095_integration-test-review.md)
**Feature:** Integration Test Review & Expansion
**Tier:** 3 — Moderate
**Priority:** 40
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | test-constants-registry | — | ✅ Done | 2/2 | [PHASE_01__test-constants-registry.md](PHASE_01__test-constants-registry.md) |
| 02 | refactor-existing-tests | 01 | ✅ Done | 2/2 | [PHASE_02__refactor-existing-tests.md](PHASE_02__refactor-existing-tests.md) |
| 03 | player-instrumentation-tests | 02 | ✅ Done | 2/2 | [PHASE_03__player-instrumentation-tests.md](PHASE_03__player-instrumentation-tests.md) |
| 04 | settings-ui-tests | 02 | ✅ Done | 2/2 | [PHASE_04__settings-ui-tests.md](PHASE_04__settings-ui-tests.md) |
| 05 | bd-ts-player-test | 02 | ✅ Done | 2/2 | [PHASE_05__bd-ts-player-test.md](PHASE_05__bd-ts-player-test.md) |
| 06 | unit-edge-cases | — | ✅ Done | 3/3 | [PHASE_06__unit-edge-cases.md](PHASE_06__unit-edge-cases.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items resolved tactically — no blockers.

- [x] **§6.1 Device resources** — Resolved: existing `"s0090-user"` literal identified; all such strings move to `TestFixtures`. Test files that require real device folders document the requirement in `TESTING_PREREQUISITES.md`.
- [x] **§6.2 Gradle profile for network tests** — Resolved tactically: introduce a `@NetworkRequired` JUnit4 `Assume`-based annotation; CI skips these without Gradle profile changes. Full Gradle profile is out of scope for this ticket.
- [x] **§6.3 BD-TS test asset** — Resolved tactically: place a minimal 188-byte well-formed TS packet as `app_v2/src/androidTest/assets/test_media/minimal.ts`; no third-party codec library needed.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (tests are not user-facing; see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new test classes do not enter the main catalog; no public API changed — skip).
- [ ] `/spec-check S0095` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0095`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-05 — Initial tactical plan authored by `/spec-tech`.
