# Tactical Plan: vr-immersive-toggle

**Strategic spec:** [`../spec_vr-immersive-toggle.md`](../spec_vr-immersive-toggle.md)
**Feature:** VR Immersive Toggle — rename "3D VR" button to "Immersive", show for all video
**Tier:** 1 — Quick Win (ad-hoc)
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-04-25

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_vr-immersive-toggle.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resource-rename | — | ✅ Done | 3/3 | [PHASE_01__resource-rename.md](PHASE_01__resource-rename.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

- [x] **Research §6.1: Visibility for 2D** — Resolved before Phase 01 start. `CommandPanelController.kt:363` already sets `btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO`, which covers all video types including flat MONO. No code change needed for visibility.
- [x] **Research §6.2: Icon contains "3D" glyph** — Confirmed: `ic_vr_3d.xml` has an explicit `<!-- "3D" text indicator at top -->` path element. Phase 01 Step 1.2 replaces it.

---

## Completion Gate

The feature is Done when **every** item below is ticked:

- [ ] All phases show ✅ Done in the Phase Overview.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (VR Edition section — §8 of strategic spec).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed (not expected for this feature — string/drawable change only).
- [ ] `/spec-check vr-immersive-toggle` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. **Before starting a phase:** flip its row to `🚧 In Progress`. Update `Phases: X/2 done` at the top.
2. **During a phase:** inside the phase file, flip each step's `Status:` to `[~] in progress` when started, `[x] done` when its Verification passes.
3. **On phase completion:** confirm every step is `[x]`, confirm phase Done Criteria, flip row to `✅ Done`, bump counter.
4. **If blocked:** flip row to `⛔ Blocked`, append to Blockers Log below.
5. **On all phases done:** flip top `Status:` to `Done`, run `/spec-check vr-immersive-toggle`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-04-25 — Initial tactical plan authored by `/spec-tech`.
