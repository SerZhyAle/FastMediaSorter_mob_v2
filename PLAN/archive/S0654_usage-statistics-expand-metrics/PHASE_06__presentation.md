# Phase 06 - Presentation

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** Phase 07
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Surface every new metric on the dashboard and in the TXT report: add localized labels, render rows under the right category with zero-row hiding, and mark categories available when they carry new keys.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done (Phase 05 ✅ Done or ⏭️ Skipped).
- [ ] §6.3 categorization decision known (default = reuse existing categories).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/BuildStatisticsReportUseCase.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsViewModel.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetStatisticsUseCase.kt` | Modified | ≤ 200 |

> Only metrics whose emission phase shipped get a row. If Phase 05 was skipped, omit undo / OCR / translation labels and rows.

---

## Steps

### Step 06.1 - Add localized metric labels (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one `statistics_metric_*` label per new metric across all three locales in lockstep using `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>`. Keys: `statistics_metric_files_renamed`, `statistics_metric_favorites_added`, `statistics_metric_favorites_removed`, `statistics_metric_slideshow_sessions`, `statistics_metric_slideshow_images`, `statistics_metric_scheduled_runs`, `statistics_metric_scheduled_files`, `statistics_metric_streams_audio_played`, `statistics_metric_streams_video_played` (or single `statistics_metric_streams_played` per §6.2), `statistics_metric_streams_added`, `statistics_metric_playlists_imported`, `statistics_metric_gif_frames_saved`, and second-wave `statistics_metric_undo`, `statistics_metric_ocr_scans`, `statistics_metric_translations` if shipped. If §6.3 adds a new section, also add its `statistics_category_*` title in three locales. Strings pass `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Verification:**

- `Grep` - `statistics_metric_files_renamed` present in all three `strings.xml` files.
- `Grep` - `statistics_metric_streams_added` present in all three.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_metric_"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[ ]` not done

---

### Step 06.2 - Render new rows in dashboard and report

**Files:** `ui/statistics/BuildStatisticsReportUseCase.kt`, `ui/statistics/StatisticsViewModel.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a labeled metric row for each new key in its category, following the existing `countLine(..)` pattern (count, or count + secondary for `SCHEDULED_TASK_FILES_PROCESSED`). Default category mapping (§6.3 reuse): `FILES_RENAMED` -> OPERATIONS; `FAVORITES_ADDED` / `FAVORITES_REMOVED` -> OPERATIONS; `SLIDESHOW_SESSIONS` / `SLIDESHOW_IMAGES_SHOWN` / `GIF_FRAMES_SAVED` -> VIEWING; `SCHEDULED_TASKS_RUN` (+files secondary) -> OPERATIONS; stream-play key(s) -> VIEWING; `STREAMS_ADDED` / `PLAYLISTS_IMPORTED` -> SOURCES; second-wave `UNDO_OPERATIONS` -> OPERATIONS, `OCR_SCANS` / `TEXT_TRANSLATIONS` -> VIEWING. Mirror the same rows in both the dashboard category builder and the TXT report so they stay identical. Keep zero-row hiding. If §6.3 chose new sections, route streams / recognition rows there instead.

**Verification:**

- `Grep` - `StatsKey.FILES_RENAMED` referenced in `BuildStatisticsReportUseCase.kt`.
- `Grep` - `StatsKey.STREAMS_ADDED` referenced in both `BuildStatisticsReportUseCase.kt` and `StatisticsViewModel.kt`.
- `/build` - `.\a.ps1 fc` compiles (code + resources).

**Status:** `[ ]` not done

---

### Step 06.3 - Mark categories available for new keys

**Files:** `domain/usecase/GetStatisticsUseCase.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Ensure `availableCategories()` includes every category that now carries a new metric so the dashboard/report show its section. If §6.3 introduced a new `StatsCategory`, add it to the availability logic and to the category title/order resolution. Categories with only zero values stay hidden via the existing zero-row rule, not via availability.

**Verification:**

- `Grep` - `availableCategories` present in `GetStatisticsUseCase.kt`.
- `/build` - `.\a.ps1 fc` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_metric_"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All shipped metrics are visible on the dashboard and in the report, localized, with zero-row hiding intact. The report privacy invariant is unchanged (no new identifiers/content). Final phase handles docs + catalog.

---

## Rollback Plan

Revert phase commit(s) - new strings and rows only; restoring prior strings.xml and the two render files reverts the surface with no data impact.
