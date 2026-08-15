# Tactical Plan: S0222 — play-console-large-screen-orientation

**Strategic spec:** [`../S0222_play-console-large-screen-orientation.md`](../S0222_play-console-large-screen-orientation.md)
**Feature:** Remove `screenOrientation="sensor"` from all `src/main` activities to satisfy Play Console large-screen warning.
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 40
**Status:** BlockNeedUserTest
**Phases:** 1 / 2 done (Phase 02 Step 02.1 PASS; Step 02.2 deferred to operator device audit)
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-orientation-removal | — | ✅ Done | 2/2 | [PHASE_01__manifest-orientation-removal.md](PHASE_01__manifest-orientation-removal.md) |
| 02 | build-and-device-audit | 01 | 🚧 In Progress (manual gate) | 1/2 | [PHASE_02__build-and-device-audit.md](PHASE_02__build-and-device-audit.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items resolved on 2026-05-16:

1. **PlayerActivity reverse portrait** — ADR-1 closes this: remove `screenOrientation` entirely. `configChanges` already handles orientation updates in-place; reverse-portrait is now allowed and visual regressions (immersive bar interplay) fall under Phase 02 device audit.
2. **Screens without layout-land** — deferred to Phase 02 manual audit. Findings spawn child tickets per strategic §3.1.2.
3. **`configChanges` without `screenOrientation`** — `android:configChanges="orientation|..."` is independent of `android:screenOrientation`. The former tells Android to deliver `onConfigurationChanged` instead of recreating the activity, regardless of which orientations are allowed. Both can coexist; removing `screenOrientation` does not require touching `configChanges`.

No remaining blockers — Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skipped (strategic §8: "Без изменений в docs/FEATURES").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated only if `.kt` files modified.
- [ ] `assembleStandardDebug` passes.
- [ ] On-device landscape audit recorded under Phase 02 with explicit screen-by-screen results.
- [ ] `/spec-check S0222` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0222`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-all` (resume from Approved).
- 2026-05-16 — Phase 01 Done. Phase 02 Step 02.1 PASS (build). Step 02.2 deferred to operator device audit; status moved to `BlockNeedUserTest` with Timber probe in `MainActivity.onCreate`.
