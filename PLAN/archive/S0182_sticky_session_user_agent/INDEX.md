# Tactical Plan: S0182 — sticky-session-user-agent

**Strategic spec:** [../S0182_sticky_session_user_agent.md](../S0182_sticky_session_user_agent.md)
**Feature:** Sticky session User-Agent across download stacks
**Tier:** 3 — Moderate
**Priority:** 80
**Status:** BlockNeedUserTest
**Phases:** 3 / 3 done
**Last updated:** 2026-05-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fallback-alignment | — | ✅ Done | 2/2 | [PHASE_01__fallback-alignment.md](PHASE_01__fallback-alignment.md) |
| 02 | regression-tests | 01 | ✅ Done | 2/2 | [PHASE_02__regression-tests.md](PHASE_02__regression-tests.md) |
| 03 | validation-and-catalog | 01, 02 | ✅ Done | 2/2 | [PHASE_03__validation-and-catalog.md](PHASE_03__validation-and-catalog.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** local-only review confirmed the persisted `User-Agent` design already uses a dedicated accessor, not account DTO expansion. Strategic §6 Q1 resolved 2026-05-13.
- [x] **Research:** yt-dlp probe is zero-network suitability matching and does not need sticky-UA parameters. Strategic §6 Q2 resolved 2026-05-13.
- [x] **Scope:** the only remaining behavioural drift is the shared HTTP fallback UA plus missing regression tests. Strategic §6 Q3 resolved 2026-05-13.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` remain untouched because strategic §8 classifies this as a bug fix, not a new feature.
- [x] `dev/CHANGELOG.md` has an entry for every modified file touched in the executed phases.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` are regenerated after Kotlin changes.
- [x] Targeted unit tests for sticky-UA session binding pass.
- [x] noLegal compile check for the touched extraction path passes.
- [x] Ticket moves into `BlockNeedUserTest` for on-device verification of the shared mobile fallback UA on Meta/Instagram/TikTok download flows. Owner-driven on-device check required before `/spec-check` moves it to `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip the row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, and bump the counter.
4. If blocked: flip the row to `⛔ Blocked`, add a bullet to Blockers Log, and mirror the matching `Block*` journal status if the whole ticket stops.
5. After local validation: keep the ticket in an active execution state until manual/on-device verification is completed and `/spec-check` audits the result.

---

## Blockers Log

- 2026-05-13 — Execution resumed from a stale `BlockNeedUserTest` state because strategic acceptance and remaining implementation scope diverged.
- 2026-05-13 — Phase 03 validation is blocked by pre-existing compile errors in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCaseTest.kt`; production compile for the touched S0182 slice passed.
- 2026-05-13 — Blocker resolved: `DiscoverNetworkResourcesUseCaseTest` rewritten to wrap suspend `probePorts` calls in `runTest(UnconfinedTestDispatcher())`; targeted test run for the three classes now returns BUILD SUCCESSFUL. Phase 03 closed.

---

## Change Log

- 2026-05-13 — Initial tactical plan authored for the remaining S0182 alignment work.
- 2026-05-13 — Phase 01 and Phase 02 completed; Phase 03 entered `In Progress` with unit-test execution blocked by an unrelated existing test compile failure.