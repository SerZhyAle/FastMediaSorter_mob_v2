# Tactical Plan: S0252 - bugfix-sftp-audio-car-playback-resilience

**Strategic spec:** [`../S0252_bugfix-sftp-audio-car-playback-resilience.md`](../S0252_bugfix-sftp-audio-car-playback-resilience.md)
**Feature:** SFTP audio car playback resilience
**Tier:** 3 - Moderate
**Priority:** 95
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | classify-evidence | - | ✅ Done | 4/4 | [PHASE_01__classify-evidence.md](PHASE_01__classify-evidence.md) |
| 02 | stream-lifecycle | 01 | ✅ Done | 4/4 | [PHASE_02__stream-lifecycle.md](PHASE_02__stream-lifecycle.md) |
| 03 | audio-precache | 02 | ✅ Done | 4/4 | [PHASE_03__audio-precache.md](PHASE_03__audio-precache.md) |
| 04 | memory-acceptance | 01 | ✅ Done | 3/3 | [PHASE_04__memory-acceptance.md](PHASE_04__memory-acceptance.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 is the research/classification phase that resolves the items below. They are not global blockers for starting Phase 01, but they remain hard prerequisites inside Phase 02, Phase 03, and Phase 04.

- [x] **Research gating route:** classify `Pipe closed` close path in Phase 01 before Phase 02. See strategic §6.1.
- [x] **Research gating route:** define SFTP audio pre-cache startup budget in Phase 01 before Phase 03. See strategic §6.2.
- [x] **Research gating route:** decide S0219 boundary for `inputstream is closed` in Phase 01 before Phase 02. See strategic §6.3.
- [x] **Research gating route:** map `free=4MB` native pressure to S0207 vs S0252 in Phase 01 before Phase 04. See strategic §6.4.
- [x] **Research gating route:** define network-lost playback semantics in Phase 01 before Phase 03. See strategic §6.5.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged unless a new user-visible capability is introduced.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public Kotlin API changed.
- [ ] `/spec-check S0252` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0252`.

---

## Blockers Log

- 2026-05-19 - Initial plan blocked before implementation by open research items from the 2026-05-19 release logs.
- 2026-05-19 - Phase 01 completed: research blockers resolved into code-phase ownership; Phase 02/03/04 may proceed.
- 2026-05-19 - Phase 02 transient blocker resolved: `VrTaskTransitionTest.kt` stale body disabled after S0251 removed the production transition class; targeted SFTP DataSource test now passes.
- 2026-05-19 - Local implementation complete; final status is `BlockNeedUserTest` because real head-unit/home-SFTP acceptance is required before `Verified`.

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-19 - `/spec-update`: converted initial research blockers into Phase 01-owned gating routes so implementation can start with evidence classification.
- 2026-05-19 - Phase 01 completed by `/spec-dev`: diagnosis artifact written to `temp/S0252_sftp_audio_log_diagnosis.md`; strategic §6 items resolved.
- 2026-05-19 - Phase 02 completed: stream-close classification added, expected close is debug-only, active read/open failures still propagate, targeted SFTP DataSource unit tests pass.
- 2026-05-19 - Phase 03 completed: SFTP audio startup pre-cache now falls back to direct streaming after a source-aware short budget; next-track prefetch failures are recoverable/degraded and tested.
- 2026-05-19 - Phase 04 completed: SFTP audio `free=4MB` evidence recorded; S0252 owns playback resilience acceptance while S0207 keeps memory root-cause ownership.
- 2026-05-19 - Phase 05 completed: catalog/dev-log/docs decision recorded, on-device acceptance predicates added, strategic status moved to `BlockNeedUserTest`.
