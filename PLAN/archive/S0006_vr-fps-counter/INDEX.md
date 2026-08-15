# Tactical Plan: S0006 — vr-fps-counter

**Strategic spec:** [`../S0006_vr-fps-counter.md`](../S0006_vr-fps-counter.md)
**Feature:** VR FPS counter in immersive HUD
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-04-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | hud-top-right-placement | — | ✅ Done | 2/2 | [PHASE_01__hud-top-right-placement.md](PHASE_01__hud-top-right-placement.md) |
| 02 | frozen-value-semantics | 01 | ✅ Done | 2/2 | [PHASE_02__frozen-value-semantics.md](PHASE_02__frozen-value-semantics.md) |
| 03 | off-toggle-hud-clear | 02 | ✅ Done | 2/2 | [PHASE_03__off-toggle-hud-clear.md](PHASE_03__off-toggle-hud-clear.md) |
| 04 | settings-disabled-when-vr-off | 03 | ✅ Done | 3/3 | [PHASE_04__settings-disabled-when-vr-off.md](PHASE_04__settings-disabled-when-vr-off.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items are Resolved (`Status: Approved` block in strategic spec).

- [x] §6.1 averaging window — Resolved: 500 ms time window.
- [x] §6.2 freeze behaviour — Resolved: hold last valid value.
- [x] §6.3 visibility on global VR=OFF — Resolved: row visible but disabled with hint.
- [x] §6.4 HUD placement — Resolved: top-right corner of HUD plane.
- [x] §6.5 hot toggle — Resolved: applies on next immersive entry; OFF still clears label once at runtime.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with the new VR FPS counter bullet.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `/spec-check S0006` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0006`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech`. Most foundations already exist in code (settings flag, DataStore, native counter, HUD state); plan focuses on closing five concrete gaps.
