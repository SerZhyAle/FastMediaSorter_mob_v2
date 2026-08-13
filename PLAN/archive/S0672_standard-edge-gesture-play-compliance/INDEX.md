# Tactical Plan: S0672 - standard-edge-gesture-play-compliance

**Strategic spec:** [`../S0672_standard-edge-gesture-play-compliance.md`](../S0672_standard-edge-gesture-play-compliance.md)
**Research inputs:** [`research/01__accessibility-play-policy.md`](research/01__accessibility-play-policy.md), [`research/02__overlay-specialuse-play-policy.md`](research/02__overlay-specialuse-play-policy.md)
**Feature:** Ship the standard-flavor edge-gesture family to Play - hardened invisible-strip primary path (specialUse) + Quick Settings tile fallback
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 40
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | primary-path-readiness | - | ✅ Done | 2/2 | [PHASE_01__primary-path-readiness.md](PHASE_01__primary-path-readiness.md) |
| 02 | qs-tile-fallback | - | ✅ Done | 5/5 | [PHASE_02__qs-tile-fallback.md](PHASE_02__qs-tile-fallback.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01 and 02 are independent (no shared artifacts) and may run in either order; 03 consumes both.

---

## Pre-Implementation Blockers

- [x] **Dependency: S0671 capture engine not yet Verified.** S0671 is `BlockNeedUserTest` (code-complete, awaiting on-device proof). Both phases here wire into its `ScreenCaptureConsentActivity` / `ScreenCaptureService`. The owner overrode this gate for *planning* (write the tactical plan now); confirm S0671 capture works on-device, or give an explicit "implement anyway", before starting `/spec-dev`. If S0671's engine changes during its device test, re-validate Phase 01/02 wiring.
  - **RESOLVED 2026-06-26 (owner go-ahead).** Owner gave the explicit "Запуск S0672" implement-anyway. The S0671 capture engine is already live in production (release `2.60.6251.711`, Play review passed) and was consolidated into S0672 (S0671 archived). Phase 01 touches only `OverlayHostService` FGS-start + the `specialUse` subtype, not the capture engine, so the wiring-change risk is nil for this phase.

> The two gates below are RELEASE / external gates, NOT implementation blockers. The code (FGS hardening, subtype reword, QS-tile fallback) may be implemented and device-tested with a debug build first. Tracked in the Completion Gate.
>
> - **External Play verdict (strategic §6.2, Open):** the `specialUse` declaration for the invisible strip is rated high-risk / likely-rejected by `research/02`. Submitting it is an external review round, not code. The `fms.edgeGestureOverlay` gate therefore stays `off` by default - the strip primary path is enabled only for the deliberate device-test / submission build via `-P fms.edgeGestureOverlay=on`.
> - **Play Console obligations:** `specialUse` foreground-service declaration (functionality, deferral impact, demo video of the real scenario) must be filed before the standard release ships the strip.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here (strategic §8 routes the showcase sentence through `/skill-release`; only `docs/ALL_FEATURES.jsonl` is written).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `TileService` class).
- [ ] `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` regenerated if the gesture-overlay setting's presence/behaviour in `standard` changed (Rule 22).
- [ ] **Dependency gate (external):** S0671 advanced to `Verified` before the standard release that enables either trigger ships.
- [ ] **Manual Play release gate (external, not code):** Play Console `specialUse` declaration completed (functionality description + deferral behaviour + demo video of enable -> swipe -> consent -> capture); the strip submission build is produced with `-P fms.edgeGestureOverlay=on`. If review rejects, ship the fallback build with `-P fms.edgeGestureTile=on` (no `specialUse`). See `research/02__overlay-specialuse-play-policy.md`.
- [ ] `/spec-check S0672` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0672`.

---

## Blockers Log

- 2026-06-25 - S0671 (capture-engine foundation) is `BlockNeedUserTest`, not `Verified`. Owner overrode the gate to author this plan; implementation start is gated on S0671 device-test confirmation or an explicit "implement anyway".

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech` (S0671 dependency gate overridden by owner for planning).
