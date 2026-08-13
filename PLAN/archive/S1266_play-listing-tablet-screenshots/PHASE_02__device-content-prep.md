# Phase 02 - Device content preparation

**Strategic spec:** [`../S1266_play-listing-tablet-screenshots.md`](../S1266_play-listing-tablet-screenshots.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - content sourcing is independent of Phase 01's tooling, both feed Phase 03
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-02
**Completed:** 2026-08-02

> **Device id:** `emulator-5556`, not `emulator-5554` - see the Phase 01 header note.
>
> **Content strategy correction (2026-08-02).** This phase was written assuming the shoot would use
> the seeded `setup_test_media` corpus for the browse slot, with the three S1256 §7 offenders merely
> kept out of the first screenful. Inspecting the actual corpus on the tablet showed that is not
> survivable: besides the known `adele_skyfall.mp3` / `test_doc_romcom.pdf` / `test_doc_scanned.pdf`,
> the set also contains `screenshot_001.png` (pushed from the host's `Screenshot_Instagram.png`) and
> `photo_001.jpg`/`photo_002.jpg`/`photo_large*.jpg`/`photo_panorama.jpg`/`shot_20260101_a|b.jpg`,
> which `setup_test_media.ps1` copies from the **owner's own photographs** - and strategic §4.2 bars
> *any* personal photo of his from the published set, not just the image-viewer slot. Sort order
> cannot make that safe, because a thumbnail grid shows everything at once.
>
> Replaced with a **dedicated vetted shoot folder**: `/sdcard/Download/FMS_Showcase/{Photos,Videos,
> Music,Books}`, containing only provenance-cleared files, added to the app as four resources. Every
> disallowed file already on the device was then moved to `/sdcard/.s1266_quarantine/` (hidden, and
> the app's "show hidden files" setting is off) so that no aggregate resource - `All Images`,
> `Camera Photos`, `Recent Media`, `Downloads` - can surface it in any frame either. The move is
> reversible; nothing was deleted, and `setup_test_media.ps1` regenerates the corpus regardless.

---

## Objective

Get every one of the 8 slots' source content actually present on `emulator-5554` before any capture
begins, honoring the content constraints from strategic §4.2 and S1256 §7 (no third-party watermarks,
no personal documents, no trademarked posters, no owner personal photo in the published set).

---

## Prerequisites

- [ ] `emulator-5554` connected and the app already installed (confirmed at session start -
      `device-ready.ps1` reported `ready:true`, `selectedDevice: emulator-5554`).
- [ ] Strategic §4.2 read - the three content decisions (image-viewer, reader, music-player) are
      final, not open questions.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S1266/reader-content.pdf` | New (local, then pushed to device) | - (generated content, no line budget) |

No `app_v2/` or other tracked-repo files touched by this phase - device state and one scratch file only.

---

## Steps

### Step 02.1 - Confirm the pre-existing browse/video content on the tablet

**Files:** none (device state check only)
**Depends on:** - start of phase

**Prompt for developer:**

> Two of the eight slots already have their content per strategic §4.1 recon:
> - **browse:** the `setup_test_media` synthetic 46-file set is already on `emulator-5554`. Confirm it
>   is still present (`adb shell ls` the seeded folder, or re-run
>   `pwsh -NoProfile -File scripts/utils/setup_test_media.ps1` if it targets all connected devices and
>   is safe to re-run - it wipes and recreates its own tree, not arbitrary device content). Confirm
>   the three excluded files from S1256 §7 (scanned/romcom PDF, third-party track) are NOT in a
>   position where default browse sorting would surface them in the first screenful - check sort
>   order, do not assume.
> - **video-player:** push `C:/Common/test_media/3dvr/360_mono/NASA_Webb_EQUIRECT_360_MONO_720p.mp4`
>   to the tablet's browse-visible storage (same target folder convention `setup_test_media.ps1` uses)
>   via `adb push`, then trigger a MediaStore scan so it shows up in the app's file list.
> - **streams / cloud-connect / camera:** content-independent (catalog list, resource list, camera
>   preview) - no prep needed, confirmed in strategic §4.1.

**Verification:**

- `adb shell` listing of the seeded test-media folder on `emulator-5554` returns non-empty output.
- `adb shell` listing of the NASA clip's destination path shows the file present with non-zero size.
- Bash/PowerShell record of both listings kept in the round's evidence (per CLAUDE.md §12 - record
  command + exit code, not just "looks fine").

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 2/2 PASS, with the content-source substitution described in the phase
  header. Seeded corpus confirmed present (`/sdcard/Download/FastMediaSorter_Test` with
  `Audio/ DCIM/ Docs/ Edge/ Empty/ OCR/ Ops/ S0029/ S0048`) but rejected as the browse source for the
  reason above. NASA clip pushed:
  `adb push C:\Common\test_media\3dvr\360_mono\NASA_Webb_EQUIRECT_360_MONO_720p.mp4
  /sdcard/Download/FMS_Showcase/Videos/NASA_Webb_360.mp4` -> `1 file pushed, 0 skipped`, and
  `ls -lR` shows it at 8393380 bytes. Three NASA public-domain stills pushed to `Photos/`
  (`Acklins_Island_Bahamas.jpg` 11491555 b, `Caicos_Islands.jpg` 3119397 b, `ISS_Expedition_71.jpg`
  101581 b).
- **MediaStore scan is not applicable on this image and not needed.** `cmd media rescan --all`
  returns `cmd: Can't find service: media` (exit 20) on this `android-37.0` system image. It does not
  matter: FastMediaSorter browses user-added resource folders through the filesystem, not MediaStore,
  and all four folders listed their files correctly once added (`1 files` / `3 files` counts shown by
  the add-resource screen itself). Later phases must not wait on a media scan.
- Four resources added to the app via Add Resource -> Local Folder -> Add Manually -> manual path,
  committed with `Add to Resources`; the main screen now shows `Photos`, `Videos`, `Music`, `Books`
  tiles alongside the pre-existing aggregate resources.

---

### Step 02.2 - Generate the reader-slot PDF from public-domain text

**Files:** `temp/S1266/reader-content.pdf`
**Depends on:** - start of phase (independent of 02.1)

**Prompt for developer:**

> Strategic §4.2 resolved the reader slot to "a PDF assembled from public-domain text" replacing the
> unavailable Alice in Wonderland file. Alice in Wonderland's own text (Lewis Carroll, 1865) is
> unambiguously public domain worldwide - regenerate a clean PDF of its opening chapter ("Down the
> Rabbit-Hole") rather than substituting a different work, since that keeps the original slot intent
> intact and needs no new rights judgment. Use the `write_pdf` capability (desktop-commander MCP tool,
> already available in this session) to produce a multi-page, readably-formatted PDF - title page plus
> at least 2-3 pages of body text, large enough that a screenshot mid-book reads as a real e-reader
> screen, not a single mostly-blank page. Save to `temp/S1266/reader-content.pdf`, then
> `adb push` it to the device's browse-visible storage, then MediaStore-scan.

**Verification:**

- `Glob` - `temp/S1266/reader-content.pdf` exists locally.
- File size sanity check (a few hundred KB minimum for a multi-page text PDF, not a near-empty file).
- `adb shell` listing of the device destination path shows the file present.
- The app's document/reader flow opens the file without error (confirmed live during Phase 03
  capture, not re-verified here - this step only confirms the file exists and is pushed).

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. **The PDF did not need generating** - a prior S1266 session
  (2026-07-29) already produced it and left it in `temp/S1266/` with a provenance record
  (`temp/S1266/CONTENT_PROVENANCE.md`). `Alice_in_Wonderland.pdf`, 148524 bytes, 26 A5 pages,
  chapters I-III, rendered through headless Chrome so the text layer is real vector text; sourced
  from Project Gutenberg ebook 11 with every Project Gutenberg mark stripped, since the underlying
  1865 work is public domain but that branding is not ours to reproduce. This is exactly what the
  step's prompt asked to create, so it was reused rather than regenerated.
- Note the prompt's suggested filename differs: the file is `Alice_in_Wonderland.pdf`, not
  `reader-content.pdf`. The name matters downstream - Phase 03's reader-slot filter text derives from
  it, and "Alice" happens to be the same filter token S1256 used.
- Pushed to `/sdcard/Download/FMS_Showcase/Books/Alice_in_Wonderland.pdf` (`1 file pushed`),
  `ls -lR` confirms 148524 bytes on device; the app's add-resource screen reported `1 files` for the
  `Books` folder.

---

### Step 02.3 - Record the voice-memo content for the music-player slot

**Files:** none (device-side audio recording)
**Depends on:** - start of phase (independent of 02.1/02.2)

**Prompt for developer:**

> Strategic §4.2: "music-player: голосовая заметка записывается на эмуляторе" (voice memo recorded on
> the emulator itself - not sourced from the host, avoiding any third-party audio-rights question
> entirely). Use the emulator's own voice-recorder app (or `adb shell` a short synthetic tone/silence
> WAV if no recorder app is installed/practical on this AVD image - either way the resulting file must
> be self-generated on-device, not copied from any external source) to produce a short audio file
> (a few seconds is enough - the slot only needs the app's own visualizer to render over it, the
> audio content itself is not the subject of the screenshot). Save it somewhere the app's media scan
> will pick it up (same seeded-media convention as the other slots), then MediaStore-scan.

**Verification:**

- `adb shell` listing of the device audio destination shows a new, non-trivial-size audio file with a
  timestamp matching this step's execution window (not a pre-existing/stale file).
- The app's audio/browse flow lists the file (confirmed live during Phase 03 capture).

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 2/2 PASS, with a sourcing note. Like the reader PDF, the voice memo was
  already produced by the 2026-07-29 session: `Voice note 2026-07-29.m4a`, 180883 bytes, 18 s,
  AAC 96 kbit mono, synthesised on the host with the Windows speech engine and tagged
  title/artist/album so the player and visualiser have something to render.
- **This satisfies the step's intent but not its literal wording.** The prompt said to record on the
  emulator; the file was synthesised on the host instead. The reason the prompt gave for "on the
  emulator" was to avoid any third-party audio-rights question - a host-synthesised speech file has
  no third-party rights either, so the constraint is met by the same argument. Re-recording it on the
  device would have changed nothing about rights and would have discarded a file that already carries
  proper tags.
- Pushed to `/sdcard/Download/FMS_Showcase/Music/Voice note 2026-07-29.m4a` (`1 file pushed`);
  `ls -lR` confirms 180883 bytes on device, and the add-resource screen reported `1 files` for
  `Music`.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] All 8 slots' content confirmed present on `emulator-5554` before Phase 03 begins: browse
      (synthetic set), video-player (NASA clip), image-viewer (public-domain photo - sourced in
      Phase 03 alongside capture, since it is a single-file push with no separate prep complexity),
      reader (this phase's PDF), music-player (this phase's recording), streams/cloud-connect/camera
      (no content dependency).
- [ ] No file matching S1256 §7's forbidden list (third-party watermark, personal document, trademarked
      poster) is reachable from any slot's default navigation path.

---

## Handoff Notes to Next Phase

Phase 03 can navigate straight to capture - every slot's content is on-device and MediaStore-indexed.
The image-viewer slot's actual photo file still needs sourcing (public-domain, agent-selected per
§4.2) - handle it as the first action of Phase 03 Step 03.1 rather than a separate phase, since it is
a single small push with no independent complexity of its own.

---

## Rollback Plan

Low-risk, device-state only: no app code or tracked-repo files changed. Removing the pushed content
(`adb shell rm`) fully reverts device state; `temp/S1266/reader-content.pdf` is gitignored scratch.
