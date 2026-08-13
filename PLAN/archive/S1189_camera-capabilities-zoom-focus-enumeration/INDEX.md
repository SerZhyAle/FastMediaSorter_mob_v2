# Tactical Plan: S1189 - camera-capabilities-zoom-focus-enumeration

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Research inputs:** [`research/01__as-is-camera-capability-pipeline.md`](research/01__as-is-camera-capability-pipeline.md)
**Feature:** Camera capabilities matched to device hardware (lens set, zoom floor, macro, photo resolutions)
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Awaiting device verification
**Phases:** 7 / 7 done
**Last updated:** 2026-07-25

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | camera-hardware-inventory | - | ✅ Done | 5/5 | [PHASE_01__camera-hardware-inventory.md](PHASE_01__camera-hardware-inventory.md) |
| 02 | lens-entry-enumeration | 01 | ✅ Done | 4/4 | [PHASE_02__lens-entry-enumeration.md](PHASE_02__lens-entry-enumeration.md) |
| 03 | session-binds-lens-entries | 02 | ✅ Done | 5/5 | [PHASE_03__session-binds-lens-entries.md](PHASE_03__session-binds-lens-entries.md) |
| 04 | macro-lens-selection | 03 | ✅ Done | 4/4 | [PHASE_04__macro-lens-selection.md](PHASE_04__macro-lens-selection.md) |
| 05 | full-resolution-photo-sizes | 03 | ✅ Done | 3/3 | [PHASE_05__full-resolution-photo-sizes.md](PHASE_05__full-resolution-photo-sizes.md) |
| 06 | lens-labels-accessibility | 03, 04 | ✅ Done | 4/4 | [PHASE_06__lens-labels-accessibility.md](PHASE_06__lens-labels-accessibility.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 carries no item with `Status: Open` - all three were closed by design decision and are confirmed by observation on a multi-lens device at acceptance, not before implementation.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 carries a FEATURES sentence, so the capability is recorded in `docs/ALL_FEATURES.jsonl`; the showcase files stay owned by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed.
- [ ] `/spec-check S1189` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1189`.

---

## Blockers Log

- 2026-07-25 - Awaiting acceptance on a multi-lens device. An emulator (`emulator-5554`) appeared mid-session and the debug APK was installed and launched on it: no fatal crash, app stable, so the change does not break startup. That is the whole extent of what an AVD can prove here - it exposes no physical sub-lenses, so criteria 1-5 are unreachable on it, and `CameraCaptureActivity` is not exported so the screen cannot even be opened directly (`am start` denied). Criterion 6 (single-back-camera device behaves as before) needs a UI walk through onboarding; criteria 1-5 need the POCO from §0.
- 2026-07-25 - Phases 01-03 were briefly blocked on S1191 (detekt was not running repo-wide, so no file could be closed). S1191 is fixed and Implemented; the block was lifted the same session.

---

## Change Log

- 2026-07-25 - Initial tactical plan authored by `/spec-tech`.
