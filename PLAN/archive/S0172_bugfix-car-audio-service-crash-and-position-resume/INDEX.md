# S0172 — Tactical Plan: Car Audio Service Crash + SFTP Position Resume

**Strategic spec:** `PLAN/S0172_bugfix-car-audio-service-crash-and-position-resume.md`
**Status:** Tactical
**Last updated:** 2026-05-12

---

## Phase Overview

| # | Phase | Status | Key deliverable |
|---|-------|--------|-----------------|
| 01 | [startForeground fix](PHASE_01__startforeground-fix.md) | Not started | `AudioPlaybackService.onCreate()` calls `startForeground` immediately before MediaSession init |
| 02 | [Service position save](PHASE_02__service-position-save.md) | Not started | Periodic + on-stop position save for SFTP audio inside `AudioPlaybackService` |
| 03 | [Position restore on load](PHASE_03__position-restore-on-load.md) | Not started | `PlayerMediaLoaderManager` reads saved position and seeks after `prepare()` |
| 04 | [Docs, catalog, cleanup](PHASE_04__docs-catalog-cleanup.md) | Not started | Strings audit, `docs/FEATURES*.md`, catalog sync, dev log |

---

## Module scope

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
- `app_v2/src/main/res/values/strings.xml` (EN)
- `app_v2/src/main/res/values-ru/strings.xml` (RU)
- `app_v2/src/main/res/values-uk/strings.xml` (UK)
- `dev/CATALOG/app_v2.jsonl` + `app_v2.md`
- `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`

## Flavors

`standard`, `legacy` only. Phases are flavor-agnostic (no `BuildConfig` gate needed).

## Dependencies

Phase 02 depends on Phase 01 being complete (service must reach foreground before position-save logic runs reliably).
Phase 03 is independent of Phase 02 (can be done in parallel, but sequential is safer for a single developer).
Phase 04 depends on all prior phases.

---

## Progress tracking

Mark each step `[x]` in the phase file as you complete it.
