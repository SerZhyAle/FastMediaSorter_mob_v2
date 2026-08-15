# Tactical Plan: S0307 - emulator-user-test-sweep

**Strategic spec:** [`../S0307_emulator-user-test-sweep.md`](../S0307_emulator-user-test-sweep.md)
**Feature:** Agent-driven emulator verification sweep for non-VR `BlockNeedUserTest` tickets
**Tier:** 4 - Strategic
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step produces evidence or an explicit blocker. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | bootstrap-discovery | - | ✅ Done | 3/3 | [PHASE_01__bootstrap-discovery.md](PHASE_01__bootstrap-discovery.md) |
| 02 | route-matrix | 01 | ✅ Done | 3/3 | [PHASE_02__route-matrix.md](PHASE_02__route-matrix.md) |
| 03 | fixtures-build-plan | 02 | ✅ Done | 3/3 | [PHASE_03__fixtures-build-plan.md](PHASE_03__fixtures-build-plan.md) |
| 04 | emulator-execution | 03 | ✅ Done | 5/5 | [PHASE_04__emulator-execution.md](PHASE_04__emulator-execution.md) |
| 05 | verdict-report | 04 | ✅ Done | 4/4 | [PHASE_05__verdict-report.md](PHASE_05__verdict-report.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 research items are resolved by Phase 01..03 artifacts. The initial offline blocker was superseded when `emulator-5554` reached `device` state.

---

## Completion Gate

- [x] All phases show ✅ Done, or Phase 04/05 records a hard external blocker with no unsafe status changes.
- [x] Every target ticket has an evidence bundle, an explicit blocker, or remains untouched with a reason.
- [x] No VR/3D/headset-only tickets are status-mutated by this sweep.
- [x] `temp/s0307/05_sweep_report.md` exists and links to all generated evidence.
- [x] `dev/CHANGELOG.md` has entries for tactical plan files and strategic status changes.
- [x] S0307 `## Last Audit` records the partial execution outcome in the strategic spec.
- [x] Strategic spec remains `In Progress` because only S0254 and S0165 had enough evidence for mutation; remaining routes still need focused passes.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log. If the whole spec is blocked, set journal status to `BlockExternal` or `BlockQuestions`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0307`.

---

## Blockers Log

- 2026-05-30 - Bootstrap note: `adb devices -l` reported `emulator-5554 offline` after adb server restart. Phase 04 cannot execute until a device is online.
- 2026-05-30 - Phase 04 blocked: `temp/s0307/04_device_ready.txt` records `device_state=offline`; target ticket mutations remain 0. Next: reconnect or restart emulator until `adb devices -l` reports `device`.
- 2026-05-30 - Superseded: `emulator-5554` returned to `device`; standardDebug partial execution completed. Remaining routes need focused noLegal/local-service/external/fixture passes.

---

## Change Log

- 2026-05-30 - Initial tactical plan authored by `/spec-tech` under `/spec-all`.
- 2026-05-30 - Partial standardDebug emulator execution completed; S0254 moved to `Broken`, S0165 moved to `Verified`, remaining verdicts left as evidence candidates or blockers.
