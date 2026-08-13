# Tactical Plan: S0989 - vr-diagxr-activity-decompose

**Strategic spec:** [`../S0989_vr-diagxr-activity-decompose.md`](../S0989_vr-diagxr-activity-decompose.md)
**Research inputs:** none
**Feature:** Decompose DiagnosticXrActivity (>1500 LOC)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (BlockNeedUserTest - awaiting on-Quest smoke)
**Phases:** 6 / 6 done
**Last updated:** 2026-07-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stereo-config-resolver | - | ✅ Done | 2/2 | [PHASE_01__stereo-config-resolver.md](PHASE_01__stereo-config-resolver.md) |
| 02 | hud-banner-renderer | 01 | ✅ Done | 2/2 | [PHASE_02__hud-banner-renderer.md](PHASE_02__hud-banner-renderer.md) |
| 03 | texture-decoder | 01 | ✅ Done | 2/2 | [PHASE_03__texture-decoder.md](PHASE_03__texture-decoder.md) |
| 04 | playback-controller | 02, 03 | ✅ Done | 2/2 | [PHASE_04__playback-controller.md](PHASE_04__playback-controller.md) |
| 05 | panel-return-dispatcher | 04 | ✅ Done | 2/2 | [PHASE_05__panel-return-dispatcher.md](PHASE_05__panel-return-dispatcher.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 has no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new flavor-only classes).
- [ ] `DiagnosticXrActivity.kt` < 1500 LOC.
- [ ] `standard debug` and `vr debug` both compile.
- [ ] `/spec-check S0989` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0989`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-21 - Initial tactical plan authored by `/spec-tech`.
