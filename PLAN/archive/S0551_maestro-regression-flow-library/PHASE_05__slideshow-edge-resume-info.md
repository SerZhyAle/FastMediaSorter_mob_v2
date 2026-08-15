# Phase 05 - Slideshow, Edge Cases, Resume, Info Dialog

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 06, 07
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Real-oracle flows for slideshow, edge cases (no-extension / large video), resume-position, and the file-info metadata dialog.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Seeded media: image set for slideshow, `Edge/file_without_extension`, `Edge/video_large_200mb.mkv`, `S0029/long.mp4`, metadata files (`test.flac`, `test_cbr.mp3`, `test_vbr.mp3`).
- [ ] Marker/id reference: `research/02`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `maestro/features/slideshow/slideshow_basic.yaml` | New | ≤ 80 |
| `maestro/features/edge/edge_cases.yaml` | New | ≤ 90 |
| `maestro/features/player/player_resume.yaml` | New | ≤ 80 |
| `maestro/features/player/player_info_dialog.yaml` | New | ≤ 80 |

---

## Steps

### Step 05.1 - New `slideshow_basic` flow

**Files:** `maestro/features/slideshow/slideshow_basic.yaml`
**Depends on:** - start of phase

**Prompt for developer:**

> Open an image, tap `btnSlideshowCmd` (or overlay `btnSlideShow`) to start the slideshow. Assert started via marker `SlideshowController: Starting slideshow` / `PlayerUiStateCoordinator: Slideshow auto-start COMPLETE` and `photoView` visible. Wait for an auto-advance (assert the displayed image changed or the slideshow button reflects active state), then stop. Crash guard (matrix Block 3 / S0550 neighbourhood).

**Verification:**

- `Glob` - `maestro/features/slideshow/slideshow_basic.yaml` exists.
- `Grep` - `btnSlideshowCmd` or `btnSlideShow` present.
- On-device: `run-tests.ps1 -Suite maestro/features/slideshow/slideshow_basic.yaml -Json` → `{"pass":true}` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS + validated GREEN on emulator-5556. Reveals auto-hidden controls (tap photoView), starts slideshow via `btnSlideshowCmd`, asserts surface stays + no crash - directly exercises the S0550 slideshow-callback path. Files: maestro/features/slideshow/slideshow_basic.yaml.

---

### Step 05.2 - New `edge_cases` flow

**Files:** `maestro/features/edge/edge_cases.yaml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Two sub-scenarios: (a) `Edge/file_without_extension` appears in browse and opens a fallback view in the player without crash (matrix 15.3) - assert `mediaContentArea` visible + crash guard. (b) `Edge/video_large_200mb.mkv` opens without OOM (15.5) - assert `playerView` visible + crash guard. Element + crash-guard oracle.

**Verification:**

- `Glob` - `maestro/features/edge/edge_cases.yaml` exists.
- `Grep` - `file_without_extension` and `video_large` literals present.

**Status:** `[x]` done

---

### Step 05.3 - New `player_resume` flow

**Files:** `maestro/features/player/player_resume.yaml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Play `S0029/long.mp4` partway, leave the player, reopen the same file, and assert the resume affordance appears (resume dialog or resumed position) by element (matrix S0029 R3 - resume mid). Resume dialog has no marker - element-only oracle; keep timing tolerant.

**Verification:**

- `Glob` - `maestro/features/player/player_resume.yaml` exists.
- `Grep` - `long.mp4` literal present.

**Status:** `[x]` done

---

### Step 05.4 - New `player_info_dialog` flow

**Files:** `maestro/features/player/player_info_dialog.yaml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Open `test.flac`, tap `btnInfoCmd`, assert the info dialog shows by `assertVisible tvFileName` + `tvFileSize` (matrix S0047/48 I1; the dialog has no rendered marker - element oracle). Assert a metadata section (`sectionAudio`) visible. Close via `btnClose`.

**Verification:**

- `Glob` - `maestro/features/player/player_info_dialog.yaml` exists.
- `Grep` - `btnInfoCmd`, `tvFileName`, `btnClose` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [ ] `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features -Json` → the slideshow/edge/player additions pass on a clean seeded emulator.
- [x] `Grep` for `optional: true` on any proof assertion in these files returns zero hits.
- [x] Dev log entry added for every file in Files Touched.

**Validation note:** static implementation checks pass. Full on-device suite proof remains pending.

---

## Handoff Notes to Next Phase

Core matrix coverage complete across Phases 02-05. Phase 06 wires the full suite into `/spec-prerelease`; Phase 07 deletes slop docs and rewrites the README to the real flow set.

---

## Rollback Plan

Revert the phase commit; the four new flows disappear. No app surface touched.
