# Phase 01 - Foundations

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Extend the metric catalog, event vocabulary and sink fold logic with all new keys/events; no emission and no UI yet. After this phase the sink can fold every new event into its aggregate key, and absent keys still read as zero.

---

## Prerequisites

- [ ] Pre-Implementation Blockers in INDEX resolved (waves composition, stream granularity, categorization). Their answers fix the exact key set added here.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/stats/StatsModels.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/stats/StatsSink.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/stats/StatsSinkImpl.kt` | Modified | ≤ 500 |

---

## Steps

### Step 01.1 - Add new scalar keys to StatsKey

**Files:** `domain/stats/StatsModels.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Append the new counter ids to the `StatsKey` enum (first wave; include second-wave keys only if §6.1 keeps them in scope): `FILES_RENAMED`, `FAVORITES_ADDED`, `FAVORITES_REMOVED`, `SLIDESHOW_SESSIONS`, `SLIDESHOW_IMAGES_SHOWN`, `SCHEDULED_TASKS_RUN`, `SCHEDULED_TASK_FILES_PROCESSED`, `STREAMS_AUDIO_PLAYED`, `STREAMS_VIDEO_PLAYED`, `STREAMS_ADDED`, `PLAYLISTS_IMPORTED`, `GIF_FRAMES_SAVED`. Second wave (only if in scope): `UNDO_OPERATIONS`, `OCR_SCANS`, `TEXT_TRANSLATIONS`. Append only - never reorder or rename existing entries. If §6.2 chose a single key, add `STREAMS_PLAYED` instead of the two stream-play keys.

**Verification:**

- `Grep` - `FILES_RENAMED` present in `StatsModels.kt`.
- `Grep` - `STREAMS_ADDED` and `PLAYLISTS_IMPORTED` present.
- `Grep` - `SLIDESHOW_SESSIONS` and `SLIDESHOW_IMAGES_SHOWN` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Add RENAME action to FileOpAction

**Files:** `domain/stats/StatsSink.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `RENAME` to the `FileOpAction` enum so renames flow through the existing `StatsEvent.FileOp` event with type + count, reusing the action x media-type matrix path (strategic ADR-1). Do not introduce a separate rename event.

**Verification:**

- `Grep` - `enum class FileOpAction` line in `StatsSink.kt` includes `RENAME`.

**Status:** `[ ]` not done

---

### Step 01.3 - Add new event variants to StatsEvent

**Files:** `domain/stats/StatsSink.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add the new marker events to the `StatsEvent` sealed interface for behaviors that are not file operations: `Favorite(added: Boolean)`, `SlideshowStarted`, `SlideAdvanced`, `ScheduledRun(filesProcessed: Long)`, `StreamPlayed(kind: ViewKind)`, `StreamAdded`, `PlaylistImported(count: Long)`, `GifFrameSaved`. Second wave only if in scope (§6.1): `UndoPerformed`, `OcrScan`, `TextTranslated`. Append only - existing variants and emitters untouched. Each event must carry only scalar/enum payload - no path, name, or content (strategic §5.4 privacy).

**Verification:**

- `Grep` - `StreamPlayed` and `ScheduledRun` present in `StatsSink.kt`.
- `Grep` - `data class Favorite` present.
- `Grep` - `GifFrameSaved` present.

**Status:** `[ ]` not done

---

### Step 01.4 - Fold new events into aggregate deltas

**Files:** `data/stats/StatsSinkImpl.kt`
**Depends on:** Step 01.1, 01.2, 01.3

**Prompt for developer:**

> In the event-to-delta fold, map each new event to its key(s): `FileOp(RENAME, ..)` -> `FILES_RENAMED` (count) and the existing matrix; `Favorite(added=true/false)` -> `FAVORITES_ADDED` / `FAVORITES_REMOVED`; `SlideshowStarted` -> `SLIDESHOW_SESSIONS`; `SlideAdvanced` -> `SLIDESHOW_IMAGES_SHOWN`; `ScheduledRun` -> `SCHEDULED_TASKS_RUN` (+1) and `SCHEDULED_TASK_FILES_PROCESSED` (+filesProcessed); `StreamPlayed(VIDEO/AUDIO)` -> `STREAMS_VIDEO_PLAYED` / `STREAMS_AUDIO_PLAYED` (or the single `STREAMS_PLAYED` per §6.2); `StreamAdded` -> `STREAMS_ADDED`; `PlaylistImported` -> `PLAYLISTS_IMPORTED` (+count); `GifFrameSaved` -> `GIF_FRAMES_SAVED`. Second-wave events to `UNDO_OPERATIONS` / `OCR_SCANS` / `TEXT_TRANSLATIONS` if in scope. Keep the fold a `when` over the sealed interface so the compiler flags any unmapped event.

**Verification:**

- `Grep` - `STREAMS_VIDEO_PLAYED` (or `STREAMS_PLAYED`) referenced in `StatsSinkImpl.kt`.
- `Grep` - `SLIDESHOW_SESSIONS` referenced in `StatsSinkImpl.kt`.
- `/build` - `.\a.ps1 fk` compiles (exhaustive `when` over StatsEvent passes).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

New keys and events exist and fold correctly; the sink no-ops when opt-in is off. Emission phases (02-05) only need to inject `StatsSink` and call `record(<event>)` at completion points - no further sink/key changes.

---

## Rollback Plan

Revert phase commit(s) - additive enum/event/fold changes only, no data migration or user-facing surface changed.
