# Tactical Plan: S0082 — chromeos-support

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Feature:** Chrome OS support (ARC++ compatibility)
**Tier:** 4 — Strategic
**Priority:** 50
**Status:** Verified
**Phases:** 6 / 6 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | env-detection-foundation | — | ✅ Done | 2/2 | [PHASE_01__env-detection-foundation.md](PHASE_01__env-detection-foundation.md) |
| 02 | saf-folder-access | 01 | ✅ Done | 4/4 | [PHASE_02__saf-folder-access.md](PHASE_02__saf-folder-access.md) |
| 03 | cast-availability-guard | 01 | ✅ Done | 3/3 | [PHASE_03__cast-availability-guard.md](PHASE_03__cast-availability-guard.md) |
| 04 | keyboard-defaults | 01 | ✅ Done | 2/2 | [PHASE_04__keyboard-defaults.md](PHASE_04__keyboard-defaults.md) |
| 05 | first-launch-banner | 01 | ✅ Done | 3/3 | [PHASE_05__first-launch-banner.md](PHASE_05__first-launch-banner.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker is unchecked.

- [ ] **Research §6.1:** Confirm whether `MANAGE_EXTERNAL_STORAGE` is silently restricted or fully blocked on Chrome OS — required before Phase 02. Smoke-test: install a debug build on a Chromebook or ARC++ emulator; attempt to select a folder from DCIM. See strategic §6.1.
- [ ] **Research §6.2:** Confirm whether NanoHTTPD port 8765 is reachable from a Chromecast on the same LAN when the app runs inside ARC++. Required before Phase 03 (but Phase 03 implements graceful degradation regardless of result). See strategic §6.2.
- [ ] **Research §6.3:** Confirm whether `android:defaultWidth` in `<activity>` layout tag can be overridden at runtime on Chrome OS without impacting VR flavor window size. Required before Phase 05 (banner placement). See strategic §6.3.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 06).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0082` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0082`.

---

## Blockers Log

*(none yet)*

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
