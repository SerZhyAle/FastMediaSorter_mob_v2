# Tactical Plan: vr-xr-cold-start

**Strategic spec:** [`../spec_vr-xr-cold-start.md`](../spec_vr-xr-cold-start.md)
**Feature:** VR XR Cold Start Latency Analysis & Resolution
**Tier:** 2 — Easy
**Status:** Not started
**Phases:** 1 / 4 done
**Last updated:** 2026-04-27

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stage-instrumentation | — | ✅ Done | 4/4 | [PHASE_01__stage-instrumentation.md](PHASE_01__stage-instrumentation.md) |
| 02 | measurement-run | 01 | ⬜ Not started | 0/5 | [PHASE_02__measurement-run.md](PHASE_02__measurement-run.md) |
| 03 | optimization-or-backlog | 02 | ⬜ Not started | 0/3 | [PHASE_03__optimization-or-backlog.md](PHASE_03__optimization-or-backlog.md) |
| 04 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 has no blockers — instrumentation does not require prior measurement data.
Phase 03 is blocked by the manual Phase 02 results.

- [ ] **Research §6.1:** Stage breakdown of the 1093 ms — resolved by Phase 02 manual run on Quest 3.
- [ ] **Research §6.2:** Whether optimization is warranted — resolved by Phase 02 analysis.
- [ ] **Research §6.3:** Whether pre-warming is safe — addressed in Phase 03 if the optimization path is chosen.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated only if Phase 03 produces a user-visible performance improvement.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after all `.kt` changes.
- [ ] `/spec-check vr-xr-cold-start` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check vr-xr-cold-start`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-04-27 — Initial tactical plan authored by `/spec-tech`.
