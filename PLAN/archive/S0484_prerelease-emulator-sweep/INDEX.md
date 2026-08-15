# Tactical Plan: S0484 - prerelease-emulator-sweep

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Research inputs:** [`research/01__perf-metrics-thresholds.md`](research/01__perf-metrics-thresholds.md) · [`research/02__resource-import-reachability.md`](research/02__resource-import-reachability.md) · [`research/03__settings-apply.md`](research/03__settings-apply.md) · [`research/04__standalone-player-intent.md`](research/04__standalone-player-intent.md) · [`research/05__log-verdict-markers.md`](research/05__log-verdict-markers.md) · [`research/06__catalog-mutation-rules.md`](research/06__catalog-mutation-rules.md)
**Feature:** Skill /spec-prerelease - end-to-end pre-release emulator sweep
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 6 / 6 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | environment-prepare | - | ✅ Done | 5/5 | [PHASE_01__environment-prepare.md](PHASE_01__environment-prepare.md) |
| 02 | resources-settings | 01 | ✅ Done | 4/4 | [PHASE_02__resources-settings.md](PHASE_02__resources-settings.md) |
| 03 | perf-measure | 01 | ✅ Done | 3/3 | [PHASE_03__perf-measure.md](PHASE_03__perf-measure.md) |
| 04 | verdict-aggregator | 03 | ✅ Done | 3/3 | [PHASE_04__verdict-aggregator.md](PHASE_04__verdict-aggregator.md) |
| 05 | skill-orchestrator | 01,02,03,04 | ✅ Done | 5/5 | [PHASE_05__skill-orchestrator.md](PHASE_05__skill-orchestrator.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All six strategic §6 items are **Resolved** (artifacts under `research/`). No open blockers remain; all phases may proceed in dependency order.

- [x] **Research §6.1:** Perf metrics and PASS thresholds - `research/01__perf-metrics-thresholds.md`.
- [x] **Research §6.2:** Predefined-resource import path + endpoint reachability - `research/02__resource-import-reachability.md`.
- [x] **Research §6.3:** Settings to change + apply/verify method - `research/03__settings-apply.md`.
- [x] **Research §6.4:** Standalone-player launch intent + return-to-app path - `research/04__standalone-player-intent.md`.
- [x] **Research §6.5:** Log markers (failure vs expected fallback) - `research/05__log-verdict-markers.md`.
- [x] **Research §6.6:** `/spec-draft` dedup + BlockNeedUserTest update rules - `research/06__catalog-mutation-rules.md`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений", developer tooling).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - skip (no `.kt` changes; skill + PowerShell only).
- [ ] `/spec-check S0484` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0484`.

---

## Blockers Log

- 2026-06-17 - Plan authored with all six §6 research items Open. Phases 02-05 cannot start until their gating research artifacts exist. Phase 01 unblocked.
- 2026-06-17 - All six §6 items Resolved (research artifacts written). No open blockers; all phases unblocked. Key decision: where no clean adb path exists (resource import, DataStore settings), drive the UI via mobile-mcp - keeps ADR-2 (no app code) and exercises real user paths.

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-17 - Phase 01 done (validated on emulator-5554). Re-scoped Phase 02/05 after manifest check confirmed `ResourceImportActivity` (exported, mime `application/vnd.fms.resources+xml`): import via intent-push + one UI confirm tap; `configure.ps1` owns adb-scriptable work (reachability, import trigger, theme/language), skill (Phase 05) owns the confirm tap + DataStore toggles + listing verification. research/02 updated.
