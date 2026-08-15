# Tactical Plan: S0238 — image-player-vr-entry-button

**Strategic spec:** [`../S0238_image-player-vr-entry-button.md`](../S0238_image-player-vr-entry-button.md)
**Feature:** Discoverable VR-entry button in image-player toolbar + user-initiated stereo auto-detection at tap.
**Tier:** 3 — Feature
**Priority:** 75
**Status:** Implemented
**Phases:** 1 / 1 done
**Last updated:** 2026-05-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | image-toolbar-vr-button | — | ✅ Done | 4/4 | [PHASE_01__image-toolbar-vr-button.md](PHASE_01__image-toolbar-vr-button.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

*(none — all design decisions captured in strategic spec §4)*

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `assembleVrUnlicensedDebug` returns exit 0.
- [ ] `testStandardDebugUnitTest --tests com.sza.fastmediasorter.ui.player.StereoDetectorUserInitiatedTest` — all 7 cases pass (per-class XML report shows `0 failures`).
- [ ] No new locale keys added — locale-audit smoke check still green.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` changes.
- [ ] Strategic spec `Status:` advanced to `Implemented` (auto via `/spec-dev`) → then `BlockNeedUserTest` for on-device verification.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set journal status via `update.ps1 -Status Block*`.
5. All done: flip `Status:` to `Implemented`, then `BlockNeedUserTest` (Timber tag inserted at entry point). After on-device pass, `/spec-check S0238` flips to `Verified`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-05-17 — Initial tactical plan authored. Spun off from S0132 §4 after device session 2026-05-17 revealed the gap.
