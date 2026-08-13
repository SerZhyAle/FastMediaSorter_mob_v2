# Phase 02 — Fastlane metadata (English, en-US)

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 6 / 6
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Create the canonical fastlane metadata tree at `<root>/fastlane/metadata/android/en-US/` with `title.txt`, `short_description.txt`, `full_description.txt`, app icon, feature graphic, and phone screenshots. English texts pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist and honestly disclose proprietary integrations (Google Drive / OneDrive / Dropbox / Cast / ML Kit) so IzzyOnDroid card describes the STANDARD edition without false advertising.

---

## Prerequisites

- [x] Phase 01 ✅ Done (LICENSE present, repo metadata set).
- [x] Strategic §6.6 resolved → BCP47 locale (`en-US`).
- [x] Strategic §6.8 resolved → `<root>/fastlane/` (shared, not per-flavor).
- [ ] Source icon `app_v2/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (or equivalent ≥ 512×512 px asset) is available.
- [ ] Phone screenshots (1..8 candidates) collected from a recent STANDARD build — owner supplies, or step 02.5 uses a fallback note.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `fastlane/metadata/android/en-US/title.txt` | New | 1 line, ≤ 50 chars |
| `fastlane/metadata/android/en-US/short_description.txt` | New | 1 line, ≤ 80 chars |
| `fastlane/metadata/android/en-US/full_description.txt` | New | ≤ 4000 chars |
| `fastlane/metadata/android/en-US/images/icon.png` | New | PNG ≥ 512×512 |
| `fastlane/metadata/android/en-US/images/featureGraphic.png` | New | PNG 1024×500 |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` .. `N.png` | New | 4–8 PNG files |

> No `.kt` files touched.

---

## Steps

### Step 02.1 — Write `title.txt`

**Files:** `fastlane/metadata/android/en-US/title.txt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the file with **exactly** one line, no trailing newline beyond a single LF:
>
> ```
> FastMediaSorter
> ```
>
> Length budget: ≤ 50 characters. Actual length: 15 characters.

**Verification:**

- `Glob` — file exists.
- `Bash` — `wc -c < fastlane/metadata/android/en-US/title.txt` returns ≤ 51 (accounting for trailing LF).
- expected content: `FastMediaSorter\n` | actual: 16 bytes (`FastMediaSorter` + LF), within 51-byte budget.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. File created (16 bytes). Dev log recorded.

---

### Step 02.2 — Write `short_description.txt` (EN)

**Files:** `fastlane/metadata/android/en-US/short_description.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create the file with **exactly** one line:
>
> ```
> Sort, view and play photos, videos, audio across local, network and cloud.
> ```
>
> Length: 74 characters (cap is 80). Tone: declarative, no marketing fluff. **Run `COMMUNICATION_POLICY.md` §6 tone checklist** (friendly, plain, accessible, no jargon, no negative framing). Do not include emoji.

**Verification:**

- `Glob` — file exists.
- `Bash` — `wc -c < fastlane/metadata/android/en-US/short_description.txt` returns ≤ 81 (≤ 80 chars + LF).
- expected: file is ≤ 80 chars | actual: measured length.
- Strings pass COMMUNICATION_POLICY §6 checklist (manual review).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. File created (75 bytes, 74 chars). Tone checklist passed: declarative, no jargon, no emoji. Dev log recorded.

---

### Step 02.3 — Write `full_description.txt` (EN)

**Files:** `fastlane/metadata/android/en-US/full_description.txt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create the file with the body below. Length budget: ≤ 4000 characters. The text MUST:
>
> 1. Lead with the user-facing value (what the app does).
> 2. Enumerate the main feature areas from `docs/FEATURES.md` Table of Contents (sections 1, 2, 3, 5, 7, 9, 11..14, 15, 16) — one short bullet each.
> 3. **Honestly disclose proprietary integrations** in a dedicated `Non-Free dependencies` paragraph (Google Drive / OneDrive / Dropbox / Google Cast / ML Kit Translation + OCR / Play Services Wear / Play In-App Review) — this is what justifies the `NonFreeDep` + `NonFreeNet` Anti-Features in the IzzyOnDroid recipe.
> 4. Mention the FLOSS components used (Tesseract OCR, SMBJ, JSch, Apache Commons Net, ExoPlayer, Glide, OkHttp) so the FOSS audience sees the open foundation.
> 5. End with project links (GitHub repo URL, license = Apache 2.0).
>
> Suggested skeleton (≤ 4000 chars target — adjust as needed):
>
> ```text
> FastMediaSorter is an Android tool for organizing media files across local
> storage, network shares and cloud services in one place. It combines a file
> browser, image viewer, video and audio player, slideshow, PDF/EPUB reader,
> OCR and translation — without forcing you to install separate apps for each
> source.
>
> What it can do:
>
> • Connect local folders, SMB/CIFS shares, FTP, SFTP, Google Drive, OneDrive
>   and Dropbox side by side.
> • Browse with thumbnails, advanced sort and filters, multi-select, drag-to-
>   reorder, full keyboard and gamepad navigation.
> • Copy/move/rename/delete across protocols, including cross-cloud transfers
>   with progress and ETA. Move to Trash with 5-minute recovery window.
> • Image viewer with rotation, color filters, OCR and AR-style translation
>   overlay, draw annotations, crop and compressed copy.
> • Video player on ExoPlayer (Media3) with Picture-in-Picture, sleep timer,
>   per-file position memory, 3D/stereo and 360° detection.
> • Audio player with background playback, lyrics search, album art lookup,
>   visualizers and Chromecast output.
> • Slideshow with background music, time interval per resource, video-to-end
>   mode, configurable transitions.
> • PDF and EPUB readers with TTS, themes, search, in-place translation.
> • Text editor with charset detection, markdown rendering, syntax highlight.
> • Home-screen widgets: resource launch, continue reading, random music,
>   camera photos. Quick Settings audio tile for background playback control.
> • Settings search across every entry. Comprehensive keyboard, TV remote and
>   gamepad shortcuts.
>
> Non-free dependencies in this build (declared via Anti-Features):
>
> This is the STANDARD edition, published as-is on IzzyOnDroid. It depends on
> proprietary SDKs to deliver some of its features:
>
> • Google Drive, OneDrive and Dropbox cloud integration (proprietary SDKs).
> • Google Cast (Chromecast output) — proprietary SDK.
> • ML Kit Translation, Text Recognition and Language Identification — Google
>   on-device proprietary libraries.
> • Google Play Services (auth + wearable bridge for the Wear OS companion).
> • Google Play In-App Review.
>
> All of the above are optional inside the app — local, SMB, FTP, SFTP usage,
> the image/video/audio/PDF/EPUB viewers, OCR via Tesseract and the slideshow
> work without ever calling a proprietary service.
>
> Free components used by the app: Tesseract OCR, SMBJ, JSch (SFTP), Apache
> Commons Net (FTP), ExoPlayer / Media3, Glide, OkHttp, BouncyCastle, Markwon,
> EPUB4j. The application source code is published under Apache License 2.0.
>
> Source code and issues: https://github.com/SerZhyAle/FastMediaSorter_mob_v2
> ```
>
> **Run `COMMUNICATION_POLICY.md` §6 tone checklist** for the entire body before committing.

**Verification:**

- `Glob` — file exists.
- `Bash` — `wc -c < fastlane/metadata/android/en-US/full_description.txt` returns ≤ 4000 (cap).
- `Grep` — `Non-free dependencies` matches exactly once.
- `Grep` — `Apache License 2.0` matches exactly once.
- `Grep` — `https://github.com/SerZhyAle/FastMediaSorter_mob_v2` matches exactly once.
- expected length: 1500..4000 chars | actual: 2673 chars, within budget.
- Strings pass COMMUNICATION_POLICY §6 checklist (manual review).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 6/6 PASS. File created (2673 chars). Grep markers `Non-free dependencies`=1, `Apache License 2.0`=1, GitHub URL=1. Tone checklist passed: honest disclosure of proprietary SDKs, FOSS components listed, declarative copy. Dev log recorded.

---

### Step 02.4 — Place app icon at fastlane image path

**Files:** `fastlane/metadata/android/en-US/images/icon.png`
**Depends on:** Step 02.1

**Prompt for developer:**

> Copy or render a high-resolution app icon to `fastlane/metadata/android/en-US/images/icon.png`. Source options (pick the largest available square PNG):
>
> 1. `app_v2/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
> 2. `app_v2/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` (round mask is OK — IzzyOnDroid square-crops as needed)
> 3. Re-export the adaptive icon as 512×512 square PNG from Android Studio's `New > Image Asset` if no XXXHDPI file is ≥ 512px.
>
> Target dimensions: **≥ 512×512 px**, square aspect. Format: PNG.

**Verification:**

- `Glob` — `fastlane/metadata/android/en-US/images/icon.png` exists.
- `Bash` — `file fastlane/metadata/android/en-US/images/icon.png | grep -oE 'PNG image data, [0-9]+ x [0-9]+'` returns a value where both dimensions are ≥ 512.
- expected: PNG ≥ 512×512 square | actual: 512×512 (composite of adaptive_fore + adaptive_back upscaled via PowerShell HighQualityBicubic).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. File: `fastlane/metadata/android/en-US/images/icon.png` (PNG 512×512 RGBA). Generated via PowerShell .NET System.Drawing — adaptive_back layer drawn over 512×512 canvas, adaptive_fore composited on top with HighQualityBicubic interpolation. Dev log recorded.

---

### Step 02.5 — Place feature graphic

**Files:** `fastlane/metadata/android/en-US/images/featureGraphic.png`
**Depends on:** Step 02.4

**Prompt for developer:**

> Create or supply `fastlane/metadata/android/en-US/images/featureGraphic.png` at exactly **1024×500 px** (IzzyOnDroid standard). Content: app name + tagline over a clean background; reuse Play Store / Meta Horizon feature graphic if the project already maintains one — check `docs/screenshots/` and `dev/release-assets/` paths first. If no asset is available, render a placeholder using existing brand assets (icon + product name) — owner can replace later without touching the recipe.

**Verification:**

- `Glob` — `fastlane/metadata/android/en-US/images/featureGraphic.png` exists.
- `Bash` — `file fastlane/metadata/android/en-US/images/featureGraphic.png | grep -oE 'PNG image data, [0-9]+ x [0-9]+'` returns exactly `PNG image data, 1024 x 500`.
- expected: PNG 1024×500 | actual: 1024×500 (gradient background + 320×320 icon left, "FastMediaSorter" name + "Sort - View - Play - Anywhere" / "Local - SMB - FTP - SFTP - Cloud" taglines right).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. File: `fastlane/metadata/android/en-US/images/featureGraphic.png` (PNG 1024×500). Generated via PowerShell .NET System.Drawing — dark blue horizontal gradient, icon composite from step 02.4, Segoe UI Semibold 64pt title + Segoe UI 28pt taglines, anti-aliased. Owner may replace with branded version later without breaking the recipe. Dev log recorded.

---

### Step 02.6 — Add phone screenshots (4–8)

**Files:** `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` .. `N.png`
**Depends on:** Step 02.5

**Prompt for developer:**

> Add between **4 and 8** PNG phone screenshots numbered sequentially from `1.png`. Each ≥ 320 px on the short side, ≤ 3840 px on the long side, portrait orientation preferred. Suggested screen selection — pick coverage that maps to the bullets in `full_description.txt`:
>
> 1. Main resource list (multiple connected sources visible).
> 2. File browser with thumbnails and metadata overlay.
> 3. Image viewer (with rotation / filters / OCR result visible).
> 4. Video player UI (with destination buttons / Big Buttons mode).
> 5. Audio player Now Playing.
> 6. Slideshow or settings search overlay.
>
> If existing Play Store screenshots are already in `docs/screenshots/play/`, reuse them. Otherwise, capture fresh ones from a STANDARD release build on a representative device (phone, 1080×1920 or similar) and save here.

**Verification:**

- `Glob` — `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png` returns at least 4 files.
- `Bash` — `ls fastlane/metadata/android/en-US/images/phoneScreenshots/ | wc -l` returns between 4 and 8.
- `Bash` — for each file, `file <path> | grep -oE 'PNG image data, [0-9]+ x [0-9]+'` returns dimensions where shorter side is ≥ 320 and longer side is ≤ 3840.
- expected: 4..8 PNG files, each meeting the dimension bounds | actual: 4 PNGs — 3× 1080×2400 (portrait phone), 1× 472×621 (portrait phone SMB picker). All within bounds (short side ≥ 320, long side ≤ 3840).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. Files: `phoneScreenshots/1.png` (car-step3, 1080×2400), `2.png` (dl-step4, 1080×2400), `3.png` (dl-step7, 1080×2400), `4.png` (smb-step2, 472×621). Copied from `docs/howto/screenshots/`. Owner may replace / extend up to 8 screenshots later. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `fastlane/metadata/android/en-US/` tree complete (title, short, full, icon, feature graphic, ≥ 4 phone screenshots).
- [ ] All txt files within length budgets (verified with `wc -c`).
- [ ] All PNG files have correct dimensions and format (verified with `file`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Strings pass COMMUNICATION_POLICY §6 tone checklist (manual review).

---

## Handoff Notes to Next Phase

Phase 03 (RU/UK metadata) mirrors the EN structure 1:1. Translation of `short_description.txt` and `full_description.txt` follows `docs/COMMUNICATION_POLICY_RU.md` / `_UK.md` tone — not literal MT. Screenshots and images are reused from `en-US/` (no need to capture localized UI screenshots; IzzyOnDroid falls back gracefully).

---

## Rollback Plan

Revert phase commit — fastlane tree disappears with no consequences; no runtime code changed. Re-running the phase is idempotent.
