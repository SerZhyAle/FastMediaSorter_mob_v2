# Tactical Plan: S0088 — test-vr-video-layer-geometry-snapshot

**Strategic spec:** [`../S0088_test-vr-video-layer-geometry-snapshot.md`](../S0088_test-vr-video-layer-geometry-snapshot.md)
**Feature:** VR fisheye V-axis regression test + VideoLayerGeometry snapshot tests
**Tier:** 4 — Low
**Priority:** 15
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | refactor-shader-access | — | ✅ Done | 1/1 | [PHASE_01__refactor-shader-access.md](PHASE_01__refactor-shader-access.md) |
| 02 | unit-tests | 01 | ✅ Done | 2/2 | [PHASE_02__unit-tests.md](PHASE_02__unit-tests.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Research items from strategic §6 resolved by code inspection (2026-05-05):

- [x] **Research §6.1:** `fisheyeFragSrc` is a local `val` inside `initGl()` — requires extraction to `companion object val FISHEYE_FRAG_SRC` (no new dependency; handled in Phase 01).
- [x] **Research §6.2:** Geometry factory is `DefaultVrLayerFactory` (separate injectable class at `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt`) — directly instantiable in JVM tests without refactoring.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`VrStereoRenderer` public API changed).
- [ ] `/spec-check S0088` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0088`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-05 — Initial tactical plan authored by `/spec-tech`.
