# Tactical Plan: S1445 - atlas-tile-random-access

**Strategic spec:** [`../S1445_atlas-tile-random-access.md`](../S1445_atlas-tile-random-access.md)
**Feature:** Random-access tile payload for the stream artwork atlases
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 70
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File | F3 driveability |
|---|-------|-----------|--------|------:|------|-----------------|
| 01 | tile-pack-reader | - | ✅ Done | 3 | [PHASE_01__tile-pack-reader.md](PHASE_01__tile-pack-reader.md) | Autonomous |
| 02 | slicer-pack-preference | 01 | ✅ Done | 4 | [PHASE_02__slicer-pack-preference.md](PHASE_02__slicer-pack-preference.md) | Autonomous |
| 03 | offline-tile-pack-publish | 01 | ✅ Done | 4 | [PHASE_03__offline-tile-pack-publish.md](PHASE_03__offline-tile-pack-publish.md) | Autonomous (needs `ffmpeg` + `gh`) |
| 04 | docs-and-closure | 02, 03 | ✅ Done | 3 | [PHASE_04__docs-and-closure.md](PHASE_04__docs-and-closure.md) | Device-gated verdict |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Decisions fixed by this plan

- Pack entry name is the slot index as a plain decimal string with no extension, so the container is image-format agnostic and the reader never parses a file name.
- The container is a ZIP with stored (uncompressed) entries: the tiles are already compressed images, and stored entries keep `ZipFile` random access cheap.
- One new reader class serves both payloads; the two existing slicers keep their sheet path as the fallback and gain no other behaviour.
- The payload keeps its existing `DeliverableSet`; only the descriptor's file list and pins change, under a new `-v2` asset name.
- No cleanup step for the old sheet: `RealDeliverableSetDownloader.promote` replaces the whole payload directory, so a re-download drops it.
- The offline pack is cut from the already published sheet with one `ffmpeg untile` pass, so the tile indices cannot drift from the published `url -> index` sidecar.
- No Room change, no new layout, no new setting, no new string.

---

## Pre-Implementation Blockers

Strategic §6 carries no blocking research item - the diagnosis is a measurement (§1).

- [x] **Research:** cost model of `decodeRegion` on the published sheet - measured 2026-08-06, recorded in strategic §1.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new class in Phase 01).
- [x] `docs/ALL_FEATURES.jsonl` carries the Streams `FIX` record (Phase 04).
- [ ] `/spec-check S1445` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-all` (Stage F2).
- 2026-08-06 - All four phases executed; packs published (preview 1881 entries / 10,840,856 B, logo 1838 entries / 5,782,986 B); ticket parked at BlockNeedUserTest.
