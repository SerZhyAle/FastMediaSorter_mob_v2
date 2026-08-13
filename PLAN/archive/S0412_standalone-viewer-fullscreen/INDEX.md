# Tactical Plan: S0412 - standalone-viewer-fullscreen

**Strategic spec:** [`../S0412_standalone-viewer-fullscreen.md`](../S0412_standalone-viewer-fullscreen.md)
**Research inputs:** none (§6 items resolved by codebase inspection — see strategic §6 for findings)
**Feature:** Fullscreen mode button in standalone viewer (images, video, documents)
**Tier:** 2 - Easy
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-06-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Pre-Implementation Blockers

Strategic §6 open items — both resolved by codebase inspection before authoring this plan:

- [x] **§6.1 Swipe behavior:** `StandaloneFullscreenManager` already uses `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Bars appear transiently on edge-swipe; command panel stays hidden. No code change needed.
- [x] **§6.2 Exit icon:** `app_v2/src/main/res/drawable/ic_fullscreen_exit.xml` exists. Use it.

No blockers remain. Phase 01 may start immediately.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fullscreen-panel-integration | — | ✅ Done | 5 / 5 | [PHASE_01__fullscreen-panel-integration.md](PHASE_01__fullscreen-panel-integration.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 4 / 4 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated — strategic §8 contains new feature sentences.
- [ ] `dev/CHANGELOG.md` has entries for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public method `setFullscreenCallbacks` added to StandaloneViewManager).
- [ ] `/spec-check S0412` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0412`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-06-13 — Initial tactical plan authored by `/spec-tech`.
