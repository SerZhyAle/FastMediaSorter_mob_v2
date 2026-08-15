# Phase 03 - Media Tab Propagation

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 + owner pilot sign-off
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Apply the validated weighted-row density pattern and left-alignment (R1, R2, R5) to the four Media sub-fragment landscape layouts.

---

## Prerequisites

- [x] Phase 02 ✅ Done.
- [x] **Owner approved the General pilot landscape screenshots.** (owner sign-off 2026-06-23 via `/spec-all`)
- [x] Backup any touched file >500 LOC to `temp/` before editing. (none in this phase >500 LOC)

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_images.xml` | Modified | ≤ 220 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout-land/fragment_settings_video.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout-land/fragment_settings_documents.xml` | Modified | ≤ 200 |

> Landscape-only by scope; portrait `res/layout/fragment_settings_*` not mirrored. R5 only retargets horizontal centering - `center_vertical` row alignment stays.

---

## Steps

### Step 03.1 - Images landscape (R2, R5)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_images.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Left-align the music-source block: change `tvSelectedMusicSource` from `android:gravity="center"` to `gravity="start"` and remove `android:layout_gravity="center"` from `btnSelectMusicSource` (defaults to start). Keep existing paired toggle rows; pack any remaining solo compact toggles up to the row budget where it reads cleanly.

**Verification:**

- `Grep` - `fragment_settings_images.xml` has no `android:layout_gravity="center"` on `MaterialButton` and no `android:gravity="center"` on `tvSelectedMusicSource`.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. `tvSelectedMusicSource` -> `gravity="start"`; `layout_gravity="center"` removed from `btnSelectMusicSource`. Image toggles already paired (no solo toggles to pack). `.\a.ps1 fc` SUCCESSFUL.

---

### Step 03.2 - Audio landscape (R2, R5)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_audio.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Left-align the photos-source block and player button: remove `android:gravity="center"` from `tvSelectedPhotosSource` and `android:layout_gravity="center"` from `btnSelectPhotosSource` and `btnSetDefaultAudioPlayer`. Pack compact toggles into weighted rows where they sit solo.

**Verification:**

- `Grep` - `fragment_settings_audio.xml` has no `android:layout_gravity="center"` and no `android:gravity="center"` on the named views.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. `tvSelectedPhotosSource` -> `gravity="start"`; `layout_gravity="center"` removed from `btnSelectPhotosSource` + `btnSetDefaultAudioPlayer`. Packed `rowSupportAudio` | `rowSearchAudioCoversOnline` into one weighted 2-up row (nextFocus wired). `.\a.ps1 fc` SUCCESSFUL.

---

### Step 03.3 - Video landscape (R2, R5)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_video.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Remove `android:layout_gravity="center"` from `btnSetDefaultVideoPlayer` (left-align). Pack compact toggle rows into weighted rows up to the budget where they sit solo.

**Verification:**

- `Grep` - `fragment_settings_video.xml` has no `android:layout_gravity="center"` on `MaterialButton`.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. `layout_gravity="center"` removed from `btnSetDefaultVideoPlayer`. Packed `rowSupportVideos` | `rowShowVideoThumbnails` into one weighted 2-up row (nextFocus wired). `.\a.ps1 fc` SUCCESSFUL.

---

### Step 03.4 - Documents landscape (R1, R2)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_documents.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> No centering offenders here. Pack compact toggle rows into weighted horizontal rows of up to 4 to cut vertical scroll, left-packed. Preserve `nextFocus*` for D-pad order.

**Verification:**

- `Grep` - at least one weighted multi-child row added (manual confirm of packing).
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. `fragment_settings_documents.xml` already carries three weighted 2-up rows (master + dependent: text/line-numbers, pdf/thumbnails, epub/office). Predicate satisfied as-is; regrouping was rejected because each pair gates the dependent toggle's visibility. No centering offenders. No edit needed.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project builds - run `/build`. (`.\a.ps1 fc` + final `.\a.ps1 d` SUCCESSFUL)
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Media tab dense + left-aligned. Same pattern applies to Phase 04.

---

## Rollback Plan

Restore touched files from `temp/` backups - layout-only.
