# Tactical Plan: S1339 - spec-next-bounded-rounds

**Strategic spec:** [`../S1339_spec-next-bounded-rounds.md`](../S1339_spec-next-bounded-rounds.md)
**Research inputs:** none - strategic spec §1-§3 already carries the measured baseline (`dev/AGENT_PROCESS_AUDIT_2026-07-31.md`) and the owner's threshold decision.
**Feature:** Bound the `/spec-next` autonomous loop by a context-token threshold, with a mechanical handoff and an unbounded `/spec-do` escape hatch.
**Tier:** 2
**Priority:** 72
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | session-state-foundation | - | ✅ Done | 4/4 | [PHASE_01__session-state-foundation.md](PHASE_01__session-state-foundation.md) |
| 02 | context-threshold-handoff | 01 | ✅ Done | 2/2 | [PHASE_02__context-threshold-handoff.md](PHASE_02__context-threshold-handoff.md) |
| 03 | wire-bounded-loop | 01, 02 | ✅ Done | 3/3 | [PHASE_03__wire-bounded-loop.md](PHASE_03__wire-bounded-loop.md) |
| 04 | spec-do-unbounded | 01, 02 | ✅ Done | 2/2 | [PHASE_04__spec-do-unbounded.md](PHASE_04__spec-do-unbounded.md) |
| 05 | docs-catalog-cleanup | 01, 02, 03, 04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic spec §8 names one prerequisite - "the statusline change (S1338 package B), which must land first" - confirmed **Landed** (`PLAN/S1338_agent-process-overhaul.md` line 327, phase 01). No open research items.

**Out-of-scope acceptance criteria (strategic §7).** Four of §7's criteria - median pre-compaction tokens falling toward 300k, p90 session request count falling, top-25-session cache_read share flattening, weekly ticket throughput not falling - are corpus-measurement criteria re-run by S1338 package A "two weeks after landing", not something a phase step can verify at implementation time. No phase covers them; `/spec-check S1339` verifies structure and behavior, not production usage patterns that have not happened yet.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched. This ticket delivers agent-tooling, not an app-visible capability (strategic §8 carries no FEATURES sentence); `close-and-log.ps1 -SkipFuncLog` confirmed the skip mechanically.
- [x] `dev/CHANGELOG.md` has entry for every modified file (19 `S1339` lines via `post-change.ps1` + `close-and-log.ps1`).
- [x] `dev/CATALOG/<module>.jsonl` - not touched. No Kotlin/app source in this ticket; `close-and-log.ps1 -SkipCatalogSync` confirmed the skip mechanically.
- [x] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` passes (`Document registry PASS: 24 record(s)`).
- [x] `/spec-check S1339` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1339`.

**No device-test gate.** This ticket has no Kotlin/`.kt` surface and nothing runs inside the app - completion is proven by direct script invocation (PowerShell exit codes and JSON output), not `BlockNeedUserTest` / `Timber.d("S1339: ..")` tags. Do not add debug-verification tags for this ticket.

---

## Blockers Log

(none yet)

---

## Change Log

- 2026-08-01 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-01 - All 5 phases implemented and verified by `/spec-dev` + `/spec-check` in one continuous run. Two real defects found and fixed mid-implementation (see Phase 01 and Phase 02 Step Logs). `/spec-check` verdict: Verified, PASS 15 / WARN 0 / FAIL 0.
