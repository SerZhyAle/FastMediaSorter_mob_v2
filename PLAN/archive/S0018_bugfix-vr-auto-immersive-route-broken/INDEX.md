# Tactical Plan: bugfix-vr-auto-immersive-route-broken

**Strategic spec:** [`../S0018_bugfix-vr-auto-immersive-route-broken.md`](../S0018_bugfix-vr-auto-immersive-route-broken.md)
**Feature:** Restore the `vrAutoImmersive=false` contract — stereo content stays on the flat panel; eliminate route/reason desync; eliminate no-op writes from settings UI.
**Tier:** 3 — Moderate
**Status:** Done (Phase 05 deferred to manual on-device validation)
**Phases:** 5 / 6 done + 1 deferred-manual
**Last updated:** 2026-04-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pin-route-decision-contract | — | ✅ Done | 4/4 | [PHASE_01__pin-route-decision-contract.md](PHASE_01__pin-route-decision-contract.md) |
| 02 | defensive-route-invariant | 01 | ✅ Done | 3/3 | [PHASE_02__defensive-route-invariant.md](PHASE_02__defensive-route-invariant.md) |
| 03 | settings-noop-write-guard | — | ✅ Done | 3/3 | [PHASE_03__settings-noop-write-guard.md](PHASE_03__settings-noop-write-guard.md) |
| 04 | atomic-route-reason-logging | 01 | ✅ Done | 3/3 | [PHASE_04__atomic-route-reason-logging.md](PHASE_04__atomic-route-reason-logging.md) |
| 05 | on-device-validation | 02, 03, 04 | ⏭️ Skipped (manual) | 0/2 | [PHASE_05__on-device-validation.md](PHASE_05__on-device-validation.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 has three Open research items. Each is resolvable from the codebase and is itself a step inside the phases below — no human decision is required to start Phase 01.

- [x] **Research §6.1** — Source of route/reason desync. Resolved by Phase 01 (matrix tests pin helper contract; if green, helper is correct and the bug is in coordinator or stale binary, both addressed by Phase 02 / Phase 05).
- [x] **Research §6.2** — Behavior for VR photo with `vrAutoImmersive=false`. Resolved as "treat photo identical to video stereo content: panel fallback when auto-immersive is off." Encoded in Phase 01 step 01.2.
- [x] **Research §6.3** — No-op writes origin. Resolved by Phase 03 (idempotent guard at repository level; UI-side guard out of scope).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` not modified (strategic §8: no user-facing change).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if any new helper files were added.
- [ ] `/spec-check S0018` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0018`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
