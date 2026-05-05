# Tactical Plan: S0075 — device-reach-google-play

**Strategic spec:** [`../S0075_device-reach-google-play.md`](../S0075_device-reach-google-play.md)
**Feature:** Expand Google Play device reach via manifest hardening
**Tier:** 1 — Quick Win
**Priority:** 50
**Status:** In Progress
**Phases:** 2 / 3 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-hardening | — | ✅ Done | 4/4 | [PHASE_01__manifest-hardening.md](PHASE_01__manifest-hardening.md) |
| 02 | console-verification | 01 (+ publish) | ⬜ Not started | 0/2 | [PHASE_02__console-verification.md](PHASE_02__console-verification.md) |
| 03 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.2:** Add `android.hardware.touchscreen android:required="false"` — **Resolved.** Keyboard/mouse/remote support is already in place; no degradation expected. Phase 01 Step 1.2 unblocked.
- [x] **Research §6.1:** Baseline excluded-device count — **Resolved.** Count is not required before the fix; proceeding without measurement.
- [x] **Research §6.3:** Determine how to maximize VR flavor device reach (Meta Horizon Store vs Google Play rules; which OpenXR `<uses-feature>` can be relaxed). **Resolved.** (VR Union Manifest already handles `required="false"` correctly for both channels, no further changes required).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no change required (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` — no regen needed (no `.kt` files changed).
- [ ] `/spec-check S0075` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0075`.

---

## Blockers Log

- 2026-05-04 — INDEX created.
- 2026-05-04 — §6.1 and §6.2 resolved by owner; Phase 01 blockers cleared. §6.3 (VR) remains open but does not block Phase 01.
- 2026-05-05 — §6.3 resolved (Union Manifest handles channels gracefully, no manifest changes needed).

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
