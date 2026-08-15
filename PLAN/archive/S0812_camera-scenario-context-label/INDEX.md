# Tactical Plan: S0812 - camera-scenario-context-label

**Strategic spec:** [`../S0812_camera-scenario-context-label.md`](../S0812_camera-scenario-context-label.md)
**Research inputs:** none (inline research 2026-07-01)
**Feature:** Camera scenario context label
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Blocked - device test (BlockNeedUserTest)
**Phases:** 3 / 3 done
**Last updated:** 2026-07-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scenario-contract | - | ✅ Done | 4/4 | [PHASE_01__scenario-contract.md](PHASE_01__scenario-contract.md) |
| 02 | scenario-label-ui | 01 | ✅ Done | 4/4 | [PHASE_02__scenario-label-ui.md](PHASE_02__scenario-label-ui.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - not edited per-spec (owned by `/skill-release`); capability recorded in `docs/ALL_FEATURES.jsonl` instead.
- [ ] `dev/CHANGELOG.md` has entry for the change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `CameraScenario` class).
- [ ] `/spec-check S0812` returns `Verified`.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0812`.

---

## Blockers Log

- 2026-07-01 - All code implemented and standard-debug build PASS. Status `BlockNeedUserTest`: needs on-device visual verification (label appears for OCR/translate flow, hidden for generic capture). No device online at close - deferred to `/spec-sweep`.
- 2026-07-01 - Scoped detekt gate flags pre-existing drift in `CameraCaptureActivity.kt` (supertype-signature stale baseline + pre-existing import/return/magic-number debt), none on S0812 lines. Parked as S0844.

---

## Change Log

- 2026-07-01 - Initial tactical plan authored by `/spec-tech`.
