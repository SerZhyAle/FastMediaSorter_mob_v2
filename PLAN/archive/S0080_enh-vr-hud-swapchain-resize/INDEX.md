# Tactical Plan: S0080 — enh-vr-hud-swapchain-resize

**Strategic spec:** [`../S0080_enh-vr-hud-swapchain-resize.md`](../S0080_enh-vr-hud-swapchain-resize.md)
**Feature:** VR HUD swapchain scaling proportional to eye buffer
**Tier:** 3 — Moderate
**Priority:** 70
**Status:** Not started
**Phases:** 2 / 2 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | hud-swapchain-scaling | — | ✅ Done | 3/3 | [PHASE_01__hud-swapchain-scaling.md](PHASE_01__hud-swapchain-scaling.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Открытых вопросов нет. Реализация не заблокирована.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no update needed (internal rendering fix; not user-facing).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `VrHudRenderer` changed — constructor signature).
- [ ] `/spec-check S0080` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0080`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
