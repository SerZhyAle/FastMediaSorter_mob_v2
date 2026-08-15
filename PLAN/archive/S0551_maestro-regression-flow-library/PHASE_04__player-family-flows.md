# Phase 04 - Player Family Flows

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 06, 07
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Real-oracle flows for the player family: video playback, image view, audio + lyrics, and documents (PDF/EPUB/TXT), replacing the dropped fictitious `media_play` / `image_view` smoke flows.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Seeded media present: `video_sample.mp4`, `photo_001.jpg`, `Audio/frank_sinatra_My_way.mp3` (+ `.lrc`), `Docs/test_doc_romcom.pdf`, `test_book.epub`, `readme.txt`.
- [ ] Marker/id reference: `research/02` (player ids + markers).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `maestro/smoke/media_play.yaml` | Deleted | - |
| `maestro/smoke/image_view.yaml` | Deleted | - |
| `maestro/features/player/player_video.yaml` | New | ≤ 80 |
| `maestro/features/player/player_image.yaml` | New | ≤ 70 |
| `maestro/features/player/player_audio_lyrics.yaml` | New | ≤ 80 |
| `maestro/features/player/player_documents.yaml` | New | ≤ 100 |

---

## Steps

### Step 04.1 - Drop fictitious smoke flows

**Files:** `maestro/smoke/media_play.yaml`, `maestro/smoke/image_view.yaml`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete both fictitious `optional`-only flows; they are replaced by the real player flows below. Remove any references to them from `maestro/README.md`'s smoke table (the README rewrite lands in Phase 07; here just delete the files).

**Verification:**

- `Glob` - `maestro/smoke/media_play.yaml` does not exist.
- `Glob` - `maestro/smoke/image_view.yaml` does not exist.

**Status:** `[x]` done

---

### Step 04.2 - New `player_video` flow

**Files:** `maestro/features/player/player_video.yaml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Open `video_sample.mp4` from the browse list. Wait for playback to be ready (marker `VideoPlayerManager: Playback ready` / first frame `onRenderedFirstFrame`) and `assertVisible playerView`. Toggle `btnPlayPause`. Crash guard via `assertNotVisible` on crash text (matrix 2.5).

**Verification:**

- `Glob` - `maestro/features/player/player_video.yaml` exists.
- `Grep` - `playerView` and `btnPlayPause` present, not under `optional: true`.

**Status:** `[x]` done

---

### Step 04.3 - New `player_image` flow (S0550 regression)

**Files:** `maestro/features/player/player_image.yaml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Open the large seeded image `photo_001.jpg` (the S0550 crash file) in the player. Assert `photoView` visible and a crash guard `assertNotVisible` on the crash-activity text - this flow is the deterministic S0550 regression. Swipe to the next image and assert `photoView` still visible. Image has no completion marker - element oracle only.

**Verification:**

- `Glob` - `maestro/features/player/player_image.yaml` exists.
- `Grep` - `photoView` present.
- `Grep` - `photo_001` literal present (the S0550 file).
- On-device: `run-tests.ps1 -Suite maestro/features/player/player_image.yaml -Json` → `{"pass":true}` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS + validated GREEN on emulator-5556. Opens photo_001.jpg, `photoView` renders, no crash prompt - the S0550 regression. Authored against live build (player controls auto-hide; nav via Локальные -> Все изображения -> tap file). Files: maestro/features/player/player_image.yaml.

---

### Step 04.4 - New `player_audio_lyrics` flow

**Files:** `maestro/features/player/player_audio_lyrics.yaml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Open `Audio/frank_sinatra_My_way.mp3`. Assert audio playback started (marker `AudioPlaybackService: playAudioPlaylist`). Tap `btnLyricsCmd`; assert the lyrics viewer shows (marker `LyricsManager: Showing lyrics viewer` and/or a lyrics container element) since a sibling `.lrc` exists (matrix 2.6).

**Verification:**

- `Glob` - `maestro/features/player/player_audio_lyrics.yaml` exists.
- `Grep` - `btnLyricsCmd` present.

**Status:** `[x]` done

---

### Step 04.5 - New `player_documents` flow

**Files:** `maestro/features/player/player_documents.yaml`
**Depends on:** Step 04.4

**Prompt for developer:**

> Three sub-scenarios (matrix 2.8): open `Docs/test_doc_romcom.pdf` and assert `pdfScrollRecyclerView` visible (marker `PdfViewerManager: firstPageRendered`); open `test_book.epub` and assert `epubWebView` visible (marker `EpubViewerManager: firstChapterRendered`); open `readme.txt` and assert the text viewer container visible (element-only - no TXT marker). Crash guard each.

**Verification:**

- `Glob` - `maestro/features/player/player_documents.yaml` exists.
- `Grep` - `pdfScrollRecyclerView` and `epubWebView` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [ ] `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features\player -Json` → suite pass on a clean seeded emulator.
- [x] The S0550 regression flow (`player_image`) is present and asserts no crash on `photo_001.jpg`.
- [x] Dev log entry added for every file in Files Touched.

**Validation note:** static implementation checks pass. Full on-device suite proof remains pending.

---

## Handoff Notes to Next Phase

Player open + marker patterns set (video/audio/pdf/epub markers; image/txt element-only). Slideshow/info/resume (Phase 05) builds on the player-open preamble.

---

## Rollback Plan

Revert the phase commit; the two deleted smoke flows return and four new player flows disappear. No app surface touched.
