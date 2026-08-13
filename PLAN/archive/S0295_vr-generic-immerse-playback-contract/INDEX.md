# Tactical Plan: S0295 - vr-generic-immerse-playback-contract

**Strategic spec:** [`../S0295_vr-generic-immerse-playback-contract.md`](../S0295_vr-generic-immerse-playback-contract.md)
**Feature:** Generic immerse playback contract
**Tier:** 3 - Moderate
**Priority:** 80
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | contracts-models | - | ✅ Done | 4/4 | [PHASE_01__contracts-models.md](PHASE_01__contracts-models.md) |
| 02 | activity-contract | 01 | ✅ Done | 4/4 | [PHASE_02__activity-contract.md](PHASE_02__activity-contract.md) |
| 03 | launch-orchestration | 01,02 | ✅ Done | 3/3 | [PHASE_03__launch-orchestration.md](PHASE_03__launch-orchestration.md) |
| 04 | settings-integration | 02,03 | ✅ Done | 3/3 | [PHASE_04__settings-integration.md](PHASE_04__settings-integration.md) |
| 05 | validation-cleanup | all | ✅ Done | 2/2 | [PHASE_05__validation-cleanup.md](PHASE_05__validation-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Design:** strategic §4 decisions resolved on 2026-05-24. Keep `DiagnosticXrActivity` name in this ticket; use explicit `VrLaunchMode` instead of sentinel-uri; short-circuit `VIDEO`/`GIF` in preflight use-case with an Activity-side defensive fallback; manual Quest verification replaces a dedicated instrumented round-trip test for now.
- [x] **Dependency:** `S0291` is still `Tactical`, but the current `DiagnosticXrActivity` + `XrEntryGatewayImpl` baseline already compiles and is sufficient for contract wiring. Final device acceptance of `S0295` still exercises the same lifecycle surface.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` intentionally unchanged per strategic §8.
- [x] `dev/CHANGELOG.md` has an entry for every modified code/spec/config file.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0 after Kotlin changes.
- [x] Standard debug build passes.
- [x] noLegal debug build passes because `src/vr/` is touched.
- [x] `/spec-check S0295` returns `Verified`, `Partial`, or `Broken` and writes the inline audit block.
- [x] Manual Quest verification items, if still required, are recorded explicitly rather than hidden in status text.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0295`.

---

## Blockers Log

- 2026-05-24 - Initial tactical plan authored by `/spec-all` resume run after repairing the owner-input gate and promoting `S0295` to `Approved`.

---

## Change Log

- 2026-05-24 - Initial tactical plan authored by `/spec-tech` inside `/spec-all`.
