# Tactical Plan: S0039 — bugfix-vr-panel-swapchain-regression

**Strategic spec:** [`../S0039_bugfix-vr-panel-swapchain-regression.md`](../S0039_bugfix-vr-panel-swapchain-regression.md)
**Feature:** VR panel swapchain regression fix
**Tier:** 3 — Moderate
**Priority:** 80
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-04-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fix-panel-samplecount | — | ✅ Done | 2/2 | [PHASE_01__fix-panel-samplecount.md](PHASE_01__fix-panel-samplecount.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — root cause confirmed: `sc.sampleCount` not set in `createPanelSwapchainImpl`, defaults to 0, `xrCreateSwapchain` returns `XR_ERROR_VALIDATION_FAILURE (-1)`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S0039` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0039`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-04-30 — Initial tactical plan authored by `/spec-tech`.
