# Tactical Plan: S0671 - standard-mediaprojection-capture-suite

**Strategic spec:** [`../S0671_standard-mediaprojection-capture-suite.md`](../S0671_standard-mediaprojection-capture-suite.md)
**Research inputs:** [`research/01__mediaprojection-play-policy.md`](research/01__mediaprojection-play-policy.md), [`research/02__postprocess-storage-play-policy.md`](research/02__postprocess-storage-play-policy.md)
**Feature:** Ship MediaProjection screen-capture + post-processing suite to the Play standard flavor
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done - all phases implemented; spec advanced to BlockNeedUserTest (standard on-device gate)
**Phases:** 3 / 3 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | capture-flag-split | - | ✅ Done | 3/3 | [PHASE_01__capture-flag-split.md](PHASE_01__capture-flag-split.md) |
| 02 | prominent-disclosure-gate | 01 | ✅ Done | 4/4 | [PHASE_02__prominent-disclosure-gate.md](PHASE_02__prominent-disclosure-gate.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No `Status: Open` §6 research items - both are Resolved. Implementation may start at Phase 01.

> The Play-submission obligations below are RELEASE gates, not implementation blockers. The code (flag split, disclosure) may be implemented and device-tested with a debug build first; the standard RELEASE build must not be published until these are complete. Tracked in the Completion Gate.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] Capture suite mounts in `standard` independently of the edge-gesture overlay; the edge-gesture overlay (`src/standardScreenCapture`, SYSTEM_ALERT_WINDOW + SPECIAL_USE) stays OFF in `standard` (deferred to S0672).
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here (strategic §8 routes the showcase sentence through `/skill-release`; only `docs/ALL_FEATURES.jsonl` is written).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] **Manual Play release gate (external, not code):** Play Console foreground-service declaration for the `mediaProjection` use case completed (functionality description + deferral behavior + demo video); privacy policy published; Data safety section updated for screen-content collection. See `research/01__mediaprojection-play-policy.md`.
- [ ] `/spec-check S0671` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0671`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-25 - Implemented all 3 phases; spec -> BlockNeedUserTest. Notable implementation notes:
  - Phase 01 split the former single `fms.screenCapture` gate into the shipped MediaProjection suite (`fms.screenCapture=on`) and the deferred standard edge overlay (`fms.edgeGestureOverlay=off`), so `standard` now mounts `src/screenCapture` without `src/standardScreenCapture`.
  - Phase 02 added a persisted `screenCaptureDisclosureAccepted` flag in the existing screenshot settings store and inserted the first-run prominent disclosure into `ScreenCaptureConsentActivity` before `createScreenCaptureIntent()`, with one `Timber.d("S0671: ..")` probe for the device tester.
  - Phase 03 recorded the capability in `docs/ALL_FEATURES.jsonl`, regenerated the settings manifest/reference docs, refreshed `dev/CATALOG/app_v2.*`, and confirmed the manual Play release gate remains external to the code change.
