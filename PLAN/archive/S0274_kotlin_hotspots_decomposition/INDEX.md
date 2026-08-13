# Tactical Plan: S0274 - Kotlin Hotspots Decomposition

**Strategic spec:** [`../S0274_kotlin_hotspots_decomposition.md`](../S0274_kotlin_hotspots_decomposition.md)
**Feature:** Decomposition of heavy Kotlin source files in `app_v2/src/main/`
**Tier:** 2 - Moderate (architecture / build speed)
**Priority:** 50
**Status:** Not started
**Phases:** 3 / 3 done (Wave 01 only - see Wave Backlog for future iterations)
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-and-methodology | - | ✅ Done | 3/3 | [PHASE_01__foundation-and-methodology.md](PHASE_01__foundation-and-methodology.md) |
| 02 | wave-01-videoplayermanager | 01 | ✅ Done | 6/6 | [PHASE_02__wave-01-videoplayermanager.md](PHASE_02__wave-01-videoplayermanager.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers. Strategic §6 research items resolution:

- §6 #1 (one umbrella spec vs per-file): **Resolved** by `/spec-all` - S0274 stays a single umbrella with waves as phases (S0002 precedent).
- §6 #2 (VR-flavor files): **Resolved by ADR-3** - VR / noLegal / lite / photos / legacy out of scope for this spec.
- §6 #3 (gradle.properties heap reminder removal): **Tracked inside Phase 02** - decision is data-driven from Wave 01 measurement.
- §6 #4 (helpers vs use-case grain): **Tracked per-wave** - this iteration's decision lives inside Phase 02 step list.

---

## Wave Backlog (future tactical iterations)

The strategic spec lists ~16 hotspot files. This tactical iteration covers **only Wave 01** (VideoPlayerManager). Each subsequent wave gets its own phase file added to this INDEX by a follow-up `/spec-tech S0274` run after the previous wave reaches `BlockNeedUserTest` and then `Verified`.

Snapshot from `dev/CATALOG/app_v2.jsonl` on 2026-05-20 (LOC are advisory - re-measured at the start of each wave):

| Wave | File | Current LOC | Target | Notes |
|-----:|------|------------:|-------:|-------|
| 01 | `ui/player/VideoPlayerManager.kt` | 939 | ≤700 | Already heavily decomposed; relaxed target. Named in strategic §3.1 as owner-mandated starting wave. |
| 02 | `ui/player/PlayerActivity.kt` | 1726 | ≤600 | Biggest file in module. Strategic §2 goal 4: Rule 3 Activity-logic cleanup. |
| 03 | `ui/player/helpers/TextViewerManager.kt` | 1486 | ≤600 | UI viewer; standalone responsibility cluster. |
| 04 | `ui/player/helpers/PdfViewerManager.kt` | 1418 | ≤600 | UI viewer. |
| 05 | `ui/player/ImageLoadingManager.kt` | 1278 | ≤600 | Glide loading orchestrator. |
| 06 | `ui/player/helpers/EpubViewerManager.kt` | 1225 | ≤600 | UI viewer; not in original strategic §1 list - added by tactical snapshot. |
| 07 | `ui/player/CommandPanelController.kt` | 1204 | ≤600 | Player command panel. |
| 08 | `ui/player/PlayerManagerInitializer.kt` | 1175 | ≤600 | DI graph composition. |
| 09 | `ui/browse/MediaFileAdapter.kt` | 1112 | ≤600 | RecyclerView adapter. |
| 10 | `data/cloud/GoogleDriveRestClient.kt` | 1104 | ≤600 | Cloud client - higher regression risk per strategic §7. |
| 11 | `ui/player/StandalonePlayerActivity.kt` | 1071 | ≤600 | Standalone player; added by tactical snapshot. |
| 12 | `data/network/SmbConnectionManager.kt` | 1057 | ≤600 | Cloud/network client. |
| 13 | `ui/player/helpers/PlayerMediaLoaderManager.kt` | 1053 | ≤600 | Added by tactical snapshot. |
| 14 | `ui/main/MainActivity.kt` | 1042 | ≤600 | Activity-level work; added by tactical snapshot. |
| 15 | `data/cloud/DropboxClient.kt` | 1030 | ≤600 | Cloud client. |
| 16 | `ui/browse/BrowseViewModel.kt` | 900 | ≤600 | VM with nested data classes (`InlinePlayerState`, `PlaybackStatus`); added by tactical snapshot. |

Tactical-snapshot additions (`EpubViewerManager`, `StandalonePlayerActivity`, `PlayerMediaLoaderManager`, `MainActivity`, `BrowseViewModel`) are not regressions from the strategic spec - strategic §1 already disclaims the file list as "ориентир, не источник истины" and instructs tactical to re-measure. Strategic-listed but currently <900 LOC files (none observed) would be removed in the same way.

**Owner gate per future wave (no `/spec-all` shortcut):** strategic §11 #10 keeps `Verified` final until every backlog wave is closed. `Partial` is the natural intermediate state between waves.

---

## Completion Gate

- [ ] All phases in Phase Overview show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **skipped** (strategic §8: no user-visible change).
- [ ] `dev/FUNCTIONALITY.log` - **skipped** (strategic §8: no user-visible behaviour change).
- [ ] `dev/CHANGELOG.md` has one entry per modified file in Wave 01.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.
- [ ] `/spec-check S0274` returns at least `Partial` (full `Verified` requires all 16 waves per strategic §11 #10).
- [ ] Strategic spec `Status:` advanced to `Partial` (waves remaining) or `Verified` (all waves done, future iterations).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1`.
5. After Wave 01 ships and verifies on device: rerun `/spec-tech S0274` to expand the next wave from Wave Backlog into a new phase file.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech` (Wave 01 scope only; remaining 15 waves listed in Wave Backlog for future iterations).
