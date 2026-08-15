# Tactical Plan: S0267 - cloud-auth-unified-storage-research

**Strategic spec:** [`../S0267_cloud-auth-unified-storage-research.md`](../S0267_cloud-auth-unified-storage-research.md)
**Feature:** Decompose the approved Hybrid Mirror recommendation into a child-spec pack and rollout order
**Tier:** 3 - Moderate (ad-hoc, research-only)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-05-22

> **Scope:** tactical, English, doc-only decomposition. This plan does not implement production code and does not allocate child tickets yet. Its output is the handoff pack required to open the follow-on strategic specs from strategic §11.3.

---

## Context Summary

- Strategic §6 is fully resolved. There are no open research blockers inside S0267 itself.
- Strategic §11.2 selected **Go - Hybrid Mirror (variant B)**.
- Strategic §11.3 explicitly says implementation must be split into separate strategic tickets after `/spec-tech S0267`.
- The doc pack produced here must make those follow-on `/spec` runs deterministic: exact child slugs, scope boundaries, dependency order, validation class, and source anchors.
- S0267 stays research-only. No production `.kt`, `.xml`, `.gradle`, or Room changes belong to this tactical plan.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | child-spec-matrix | - | ✅ Done | 3/3 | [PHASE_01__child-spec-matrix.md](PHASE_01__child-spec-matrix.md) |
| 02 | prompt-pack | 01 | ✅ Done | 3/3 | [PHASE_02__prompt-pack.md](PHASE_02__prompt-pack.md) |
| 03 | rollout-sequencing | 01, 02 | ✅ Done | 3/3 | [PHASE_03__rollout-sequencing.md](PHASE_03__rollout-sequencing.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] Strategic §6 research is fully resolved.
- [x] Strategic §11.2 recommendation is fixed to Hybrid Mirror.
- [x] Child-ticket decomposition target is known from strategic §11.3.
- [x] This tactical plan is doc-only. No UI ambiguity gate is needed because no user-facing implementation happens inside S0267.

---

## Completion Gate

- [x] All four phases show ✅ Done.
- [x] `CHILD_SPECS.md`, `PROMPTS.md`, and `ROLLOUT_ORDER.md` exist under `PLAN/S0267_cloud-auth-unified-storage-research/`.
- [x] The child-spec pack lists all required follow-on slugs from strategic §11.3 and the optional post-release auditor ticket (6 required + 1 optional = 7).
- [x] `dev/CHANGELOG.md` has an entry for every modified tactical or strategic spec file (expected: ≥6 S0267 entries | actual: 23 entries).
- [x] No production file outside `PLAN/` was changed by this tactical plan (pre-existing dirty VR/build.gradle.kts files are unrelated to S0267 work, documented in Phase 04 Step 04.3 log).
- [x] `/spec-check S0267 --strategic` returns `Verified` (2026-05-22, PASS 13 / WARN 0 / FAIL 0).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to `BlockQuestions` or `BlockByOtherTask`.
5. All done: run `/spec-check S0267 --strategic`.

---

## Blockers Log

- 2026-05-20 - none.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech` for research-only decomposition.
- 2026-05-22 - All four phases executed by `/spec-dev`. `CHILD_SPECS.md`, `PROMPTS.md`, `ROLLOUT_ORDER.md` written. `/spec-check S0267 --strategic` returned `Verified` (PASS 13 / WARN 0 / FAIL 0 / EXEMPT 1).
