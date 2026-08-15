# Phase 01 — surface-map

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Produce the target-surface matrix, first-frame hook inventory, and measurement protocol scaffold for every host covered by S0196.

---

## Prerequisites

- [ ] Strategic spec `Status:` is `Tactical`.
- [ ] `temp/S0196/` is reserved for this research run.
- [ ] Working tree is clean or on a feature branch.
- [ ] No production code changes are planned inside this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0196/01_surface_matrix.md` | New | ≤ 250 |
| `temp/S0196/01_render_hooks.md` | New | ≤ 220 |
| `temp/S0196/01_protocol.md` | New | ≤ 220 |

> Research-only phase. No source edits expected.

---

## Steps

### Step 01.1 — Catalog target hosts and first meaningful content

**Files:** `PLAN/S0196_activity-render-priority-research.md`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt`, `temp/S0196/01_surface_matrix.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `temp/S0196/01_surface_matrix.md`. Enumerate every target surface from strategic §5.2 and record host type, flavor coverage, primary content, secondary UI that may be delayed, entrypoint file, layout file, and required cold/warm/cache modes. Use the exact host names found in source.

**Verification:**

- `Glob` — `temp/S0196/01_surface_matrix.md` exists.
- `Grep` — `PlayerActivity` present in that file.
- `Grep` — `BrowseActivity` present in that file.
- `Grep` — `ResourceTypeSelectorDialog` present in that file.
- `Grep` — `VrPlayerActivity` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: temp/S0196/01_surface_matrix.md (+177 LOC). Dev log recorded.

---

### Step 01.2 — Record current first-frame hooks and overlay anchors

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`, `app_v2/src/main/res/layout-land/activity_player_unified.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt`, `temp/S0196/01_render_hooks.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `temp/S0196/01_render_hooks.md`. Record every existing `ViewStub`, delayed inflate path, explicit post-inflate callback, and explicit absence or presence of `reportFullyDrawn()` and idle handlers in the audited hosts. Include the player `lyricsViewerStub` path and note whether any non-player host already ships a comparable delayed-inflate mechanism.

**Verification:**

- `Glob` — `temp/S0196/01_render_hooks.md` exists.
- `Grep` — `lyricsViewerStub` present in that file.
- `Grep` — `reportFullyDrawn: not found` present in that file.
- `Grep` — `idle handler: not found` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: temp/S0196/01_render_hooks.md (+89 LOC). Dev log recorded.

---

### Step 01.3 — Define the measurement protocol and artifact map

**Files:** `temp/S0196/01_protocol.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `temp/S0196/01_protocol.md`. Fix the measurement matrix for cold start, warm reopen, cold cache, and warm cache; name the single reference device and build; define the required raw artifacts for every run: trace, log, screen recording, and frame notes. Do not collect measurements in this step.

**Verification:**

- `Glob` — `temp/S0196/01_protocol.md` exists.
- `Grep` — `Cold start` present.
- `Grep` — `Warm reopen` present.
- `Grep` — `Cold cache` present.
- `Grep` — `Warm cache` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: temp/S0196/01_protocol.md (+125 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `temp/S0196/` contains the three phase artefacts.
- [x] Strategic §5.2 target surfaces all appear in `01_surface_matrix.md`.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] No build required — research-only artefacts, no production code changed.

---

## Handoff Notes to Next Phase

Phase 02 and Phase 03 consume the matrix and protocol unchanged. Phase 04 must not invent new host names or measurement modes that are absent from `01_surface_matrix.md`.

---

## Rollback Plan

Delete `temp/S0196/01_*` files — no production code or persisted app data changed.