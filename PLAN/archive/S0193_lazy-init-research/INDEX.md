# Tactical Plan: S0193 — lazy-init-research

**Strategic spec:** [`../S0193_lazy-init-research.md`](../S0193_lazy-init-research.md)
**Feature:** Lazy / on-demand feature initialization research
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Done
**Phases:** 4 / 5 done (Phase 02 skipped — principle-first path; deferred for posterity)
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. This is a pure research spec — no production code changes. All phases produce documented findings; Phase 04 produces the recommendation and, if warranted, child spec tickets.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | audit-init-points | — | ✅ Done | 5/5 | [PHASE_01__audit-init-points.md](PHASE_01__audit-init-points.md) |
| 02 | measure-startup-costs | 01 | ⏭️ Skipped | 0/3 | [PHASE_02__measure-startup-costs.md](PHASE_02__measure-startup-costs.md) |
| 03 | lazy-mechanisms-eval | 01 | ✅ Done | 4/4 | [PHASE_03__lazy-mechanisms-eval.md](PHASE_03__lazy-mechanisms-eval.md) |
| 04 | recommendation | 03 | ✅ Done | 3/3 | [PHASE_04__recommendation.md](PHASE_04__recommendation.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All open research items from strategic §6 must be resolved in Phases 01–03 before Phase 04 (recommendation) can begin.

- [x] **§6.1** — Real heap weight of the network stack at startup — Phase 02 skipped; marked as Resolved (Skipped) in strategic spec.
- [x] **§6.2** — Applicability of `Lazy<T>` injection to Application-level Hilt fields — resolved in Phase 01 Step 01.1 (Phase 03 will finalize per-field verdicts).
- [x] **§6.3** — DFM viability for VR / noLegal sideload — resolved in Phase 03 Step 03.3: not recommended.
- [x] **§6.4** — Cold start delta: standard vs lite vs photos — Phase 02 skipped; marked as Resolved (Skipped) in strategic spec.
- [x] **§6.5** — noLegal native library load point (`System.loadLibrary` timing) — resolved in Phase 01 Step 01.4: all noLegal native code is already lazy.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` — no update required (strategic §8: "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every research artifact written.
- [x] Strategic spec §6 items are all updated to `Resolved` with findings.
- [x] Phase 04 recommendation is committed and, if child specs are created, their ids are listed in strategic §10.
- [x] `/spec-check S0193` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to the appropriate `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0193`. (✅ S0193 Closed)

---

## Blockers Log

_(none yet)_

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-14 — Phase 01 complete (static audit). §6.2 and §6.5 resolved. Key finding: 13 of 17 Application-level `@Inject` fields are safe for direct `dagger.Lazy<T>`; 4 require lifecycle-observer refactor. noLegal native code already correctly lazy.
- 2026-05-14 — Phase 03 complete (mechanism evaluation). §6.3 resolved (DFM not recommended). Applicability matrix produced. Phase 02 skipped per principle-first path agreed with owner.
- 2026-05-14 — Phase 04 complete (recommendation). ADR-3 added to strategic spec. Child specs S0194 (lazy-hilt-singletons) and S0195 (network-first-use-trigger) created as Draft stubs.
