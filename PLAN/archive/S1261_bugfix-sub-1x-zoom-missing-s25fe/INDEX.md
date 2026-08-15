# Tactical Plan: S1261 - bugfix-sub-1x-zoom-missing-s25fe

**Strategic spec:** [`../S1261_bugfix-sub-1x-zoom-missing-s25fe.md`](../S1261_bugfix-sub-1x-zoom-missing-s25fe.md)
**Research inputs:** [`research/01__s25fe-camera-report.md`](research/01__s25fe-camera-report.md)
**Feature:** Sub-1x zoom restored on Galaxy S25 FE; honest equivalent multipliers; main-lens start
**Tier:** 3 - Moderate (ad-hoc, bugfix)
**Priority:** 90
**Status:** Done (pending owner device pass)
**Phases:** 4 / 4 done
**Last updated:** 2026-07-28

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec and research 01 (defects D1-D3).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | app-side-diagnostics | - | ✅ Done | 3/3 | [PHASE_01__app-side-diagnostics.md](PHASE_01__app-side-diagnostics.md) |
| 02 | honest-equivalents | 01 | ✅ Done | 4/4 | [PHASE_02__honest-equivalents.md](PHASE_02__honest-equivalents.md) |
| 03 | device-floor-zoom-row | 02 | ✅ Done | 4/4 | [PHASE_03__device-floor-zoom-row.md](PHASE_03__device-floor-zoom-row.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items are both Resolved (device report captured in research 01).

Acceptance constraint (not a start blocker): final verification is owner-run on the S25 FE (no adb on that phone) - the ticket parks in `BlockNeedUserTest` after Phase 04 with a re-captured System info report as evidence.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*` untouched (strategic §8: no changes - regression restoration).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added).
- [ ] `/spec-check S1261` returns `Verified` after owner's device pass.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log, set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, transition ticket to `BlockNeedUserTest`, then `/spec-check S1261` after the owner's device pass.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-07-28 - Initial tactical plan authored by `/spec-tech` from the S25 FE device report.
- 2026-07-28 - Phases 01-03 implemented in one pass (spec-next loop). Deviations recorded in the
  phase files: `select()` dedups on focal OR equivalent (not equivalent-only) so the fused-camera
  sub-lens and the standalone ultra-wide never both appear when the sensor size is unreadable;
  `parentLogicalMinZoom` falls back to Camera2 `CONTROL_ZOOM_RATIO_RANGE.lower` for unbound
  cameras; the start-lens rule lives in `CameraLensEnumerationManager.initialLensIndex` shared by
  the session and the report; Phase 01's emulator predicate folded into Step 03.4's single pass.
  Line budgets: session manager 910 (plan said <=900), enumeration manager 205 (<=180) - soft plan
  estimates exceeded by the shared selector + fallback; project ceiling 1500 untouched.
