# Tactical Plan: S0036 — vr-android-xr-sdk-compat

**Strategic spec:** [`../S0036_vr-android-xr-sdk-compat.md`](../S0036_vr-android-xr-sdk-compat.md)
**Feature:** Android XR SDK Compatibility
**Tier:** 3 — Moderate
**Priority:** 65
**Status:** Not started
**Phases:** 0 / 3 done
**Last updated:** 2026-04-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-xr-properties | — | ⬜ Not started | 0/3 | [PHASE_01__manifest-xr-properties.md](PHASE_01__manifest-xr-properties.md) |
| 02 | activity-config-overlay | 01 | ⬜ Not started | 0/4 | [PHASE_02__activity-config-overlay.md](PHASE_02__activity-config-overlay.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ⬜ Not started | 0/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see strategic §6, dated 2026-04-30). No blockers — Phase 01 may begin.

- [x] Q1 — `PROPERTY_XR_ACTIVITY_START_MODE` is the public mechanism (see strategic §6 Q1).
- [x] Q2 — `EmbeddingMixedHandler` / `WindowHierarchyInfo` are XR Shell internals; treat residual logs as system noise (see strategic §6 Q2).
- [x] Q3 — won't-fix without real Android XR device (see strategic §6 Q3).
- [x] Q4 — `configChanges` set is `orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|navigation|uiMode|fontScale` (see strategic §6 Q4).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — **no change** (strategic §8: infrastructure-only, user invisible).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (manifest + cleanup phase deliverables).
- [ ] `dev/CATALOG/app_v2.jsonl` — **no regen required** (no `.kt` files modified — manifest-only change).
- [ ] `/spec-check S0036` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0036`.

---

## Blockers Log

- 2026-04-30 — none.

---

## Change Log

- 2026-04-30 — Initial tactical plan authored by `/spec-tech`.
