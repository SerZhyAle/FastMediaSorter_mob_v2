# Tactical Plan: S0268 - agent-continuity-layer

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Feature:** Agent Continuity Layer
**Tier:** 1 - Major (системная инфраструктура)
**Priority:** 70
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 1/1 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | bootstrap-packet | 01 | ✅ Done | 2/2 | [PHASE_02__bootstrap-packet.md](PHASE_02__bootstrap-packet.md) |
| 03 | resume-layer | 01 | ✅ Done | 4/4 | [PHASE_03__resume-layer.md](PHASE_03__resume-layer.md) |
| 04 | request-logger | 01 | ✅ Done | 3/3 | [PHASE_04__request-logger.md](PHASE_04__request-logger.md) |
| 05 | request-digest | 04 | ✅ Done | 2/2 | [PHASE_05__request-digest.md](PHASE_05__request-digest.md) |
| 06 | dirty-tree-guard | 01 | ✅ Done | 2/2 | [PHASE_06__dirty-tree-guard.md](PHASE_06__dirty-tree-guard.md) |
| 07 | docs-catalog-cleanup | 02,03,04,05,06 | ✅ Done | 2/2 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All six §6 research items are resolved tactically in Phase 01.1 by writing concrete decisions into `scripts/agent_continuity/README.md`. Boxes below stay ticked because the resolutions are mechanical from the strategic spec content and require no additional owner input.

- [x] **Research §6.1:** Reanimate vs replace `scripts/log-ai-request.ps1` - **REPLACE.** Existing script uses XML router-logging contract incompatible with the session-activity contract in strategic §5.3. New utility lives at `scripts/agent_continuity/request-log.ps1`. Legacy script kept on disk but marked deprecated in `scripts/agent_continuity/README.md`; physical removal deferred to a follow-up cleanup spec.
- [x] **Research §6.2:** Agent id source for snapshot filename - **Combined.** Resolution order inside `session-snapshot.ps1`: `-Agent` parameter overrides; otherwise environment variable `AGENT_NAME` if set; otherwise literal `agent`. No harness changes required.
- [x] **Research §6.3:** Snapshot trigger - **Skill-driven (option а).** Each significant skill calls `session-snapshot.ps1` as a documented post-step. In-spec integration target is `/spec-dev` (added by Phase 03.4), demonstrating the contract end-to-end.
- [x] **Research §6.4:** High-risk overlap file list - **Minimum list embedded in `dirty-tree-guard.ps1`:** `CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`. Extensible via `-ExtraHighRiskPaths` parameter without code change.
- [x] **Research §6.5:** Sxxxx candidate source for bootstrap - **Option (г) combined.** Explicit `-Ticket` parameter overrides; fallback heuristic = the spec record from `PLAN/spec-catalog.jsonl` with the most-recent `updated` field whose status is one of `Draft`, `Approved`, `Tactical`, `In Progress`, `BlockNeedUserTest`.
- [x] **Research §6.6:** Request log format - **Single JSONL.** Path: `dev/agent-continuity/requests.jsonl`. Directory gitignored by Phase 04.2. Append-only line writes; trivially digestible by Phase 05.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **skip;** strategic §8 says "Не затрагивает пользовательскую функциональность".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `.\scripts\add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/<module>.jsonl` - **skip;** no `.kt` files touched.
- [ ] `/spec-check S0268` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0268`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech` (driven from `/spec-all S0268`).
