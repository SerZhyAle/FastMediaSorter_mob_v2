# Tactical Plan: vr-controls-panel-flow-restoration

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Feature:** Restore the «settings → apply → 3D» scenario in VR. Replace MainActivity exit target with PlayerActivity. Add «apply and 3D» combo button. Add prev/next without exit from immersive. Extend S0009 HUD with passive playback indicators. Defer interactive HUD controls to S0024.
**Tier:** 4 — Strategic
**Status:** Done (Phase 05 deferred to S0024)
**Phases:** 5 / 6 done + 1 deferred (Phase 05 → S0024)
**Last updated:** 2026-04-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | exit-target-redirect | — | ✅ Done | 3/3 | [PHASE_01__exit-target-redirect.md](PHASE_01__exit-target-redirect.md) |
| 02 | apply-and-3d-button | — | ✅ Done | 3/3 | [PHASE_02__apply-and-3d-button.md](PHASE_02__apply-and-3d-button.md) |
| 03 | immersive-prev-next | 01 | ✅ Done | 3/3 | [PHASE_03__immersive-prev-next.md](PHASE_03__immersive-prev-next.md) |
| 04 | hud-passive-content | — | ✅ Done | 3/3 | [PHASE_04__hud-passive-content.md](PHASE_04__hud-passive-content.md) |
| 05 | interactive-hud-controls | S0024 | ⏭️ Deferred | 0/0 | [PHASE_05__interactive-hud-controls.md](PHASE_05__interactive-hud-controls.md) |
| 06 | docs-catalog-cleanup | 01–04 | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Deferred`

---

## Pre-Implementation Blockers

Strategic §6 has 5 Closed decisions (all resolved by owner 2026-04-28). Strategic §«Proposed Structural Changes» has 3 proposals:

- [x] §6 #1 — exit target = existing PlayerActivity (Phase 01 implements).
- [x] §6 #2 — full immersive HUD scope: passive part landed via Phase 04, interactive part deferred to S0024 / Phase 05.
- [x] §6 #3 — context via intent-extras + in-memory; no DataStore (consumed in Phases 01+03).
- [x] §6 #4 — «apply and 3D» combo button (Phase 02).
- [x] §6 #5 — VR-photo symmetric; prev/next applies to both (Phase 03).
- [x] **P-1** — S0009 ADR-3 transitional guard removal — recorded as follow-up; requires `/spec-update S0009 --force-locked` AFTER S0019 lands.
- [x] **P-2** — interactive ray-input dependency split into S0024 (allocated, Status: Approved). Phase 05 of this spec is the placeholder waiting on S0024 implementation.
- [x] **P-3** — §6 heading kept as «Закрытые решения» for readability; auditor can rely on absence of `Status: Open` markers.

---

## Completion Gate

- [ ] Phases 01–04 + 06 show ✅ Done. Phase 05 stays ⏭️ Deferred until S0024 lands.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated for the user-visible surface (exit-to-player flow + «apply and 3D» button + immersive prev/next + passive HUD indicators).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0019` returns `Verified` (Phase 05 deferred = MANUAL but not FAIL).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress`, then `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.

---

## Blockers Log

- (none — Phase 05 is intentionally deferred, not blocked)

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech` (via `/spec-all`). Decomposed into 4 doable + 1 deferred (waits on S0024) + 1 cleanup phase.
