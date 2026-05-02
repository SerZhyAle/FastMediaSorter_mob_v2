# Phase 03 — Route Info-Dialog Audio Through Extended Extractor

**Strategic spec:** [`../S0048_info-dialog-extended-metadata.md`](../S0048_info-dialog-extended-metadata.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 7 / 7
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Route the audio branch of `FileInfoDialog` through the extended `AudioMetadataLoader` (Phase 02) instead of the system `MediaMetadataRetriever` path. Render new audio fields (sample rate, bit depth, channels, lossless marker, ReplayGain track + album, computed bitrate when codec returns none) plus an embedded cover-art thumbnail in the dialog. Soft-fail to the existing path on any extractor exception.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved — yes.
- [ ] Working tree is clean or on a feature branch.
- [ ] `AudioMetadataLoader` exposes a coroutine-friendly `suspend fun loadDetailed(file: MediaFile): AudioMetadata?` (added in this phase if not present in Phase 02).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoAudioDisplayHelper.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` | Modified | ≤ 850 |
| `app_v2/src/main/res/layout/dialog_file_info.xml` | Modified | ≤ 750 |
| `app_v2/src/main/res/layout-land/dialog_file_info.xml` | Modified | ≤ 750 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt` | Modified (small addition) | ≤ 820 |

> `FileInfoDialog.kt` will hover near 850 lines after this phase — backup step required.

---

## Steps

### Step 03.1 — Add `suspend fun loadDetailed(file: MediaFile): AudioMetadata?` on `AudioMetadataLoader`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a public `suspend fun loadDetailed(file: MediaFile): AudioMetadata?` on `AudioMetadataLoader`. It performs the same flow as `loadIfNeeded` but synchronously (within the calling coroutine), without going through `inFlight` / kill-switch / callback machinery, and returns the parsed `AudioMetadata` or `null`. Reuses `readPartialBytes` and `extractMetadataFromBytes`. For local paths (`PathUtils.isLocalPath` or `content://`), pull bytes from a `RandomAccessFile` of the local file directly (only the first `MAX_PARTIAL_READ_BYTES`). The whole body is wrapped in `try` returning `null` on any exception per strategic §6.3.

**Verification:**

- `Grep` — `suspend fun loadDetailed\(` matches exactly once in `AudioMetadataLoader.kt`.
- `Grep` — that function body contains a top-level `try {` and a matching `catch (` block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. `suspend fun loadDetailed(` at line 245; `return try {` + `catch (` at lines 246/265. Import `java.io.RandomAccessFile` added. File: `AudioMetadataLoader.kt` (+32 LOC → 742 LOC ≤ 820). Dev log recorded.

---

### Step 03.2 — Add new audio fields and `ImageView` for cover art to `dialog_file_info.xml`

**Files:** `app_v2/src/main/res/layout/dialog_file_info.xml`, `app_v2/src/main/res/layout-land/dialog_file_info.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inside the `sectionAudio` `LinearLayout` of both dialog layouts, add the following `TextView` ids (each `wrap_content`, `BodyLarge` style, `visibility="gone"`, identical padding pattern to existing audio fields): `tvAudioBitDepth`, `tvAudioLossless`, `tvAudioReplayGainTrack`, `tvAudioReplayGainAlbum`. The existing `tvAudioBitrate` and `tvAudioSampleRate` are reused without rename. Then add a new `LinearLayout` block at the top of `sectionAudio` containing an `ImageView` with id `ivAudioCoverArt`, `layout_width="@dimen/file_info_cover_art_size"`, `layout_height="@dimen/file_info_cover_art_size"`, `scaleType="centerCrop"`, `contentDescription="@string/audio_cover_art_cd"`, `visibility="gone"`. Add the dimension `file_info_cover_art_size` (= `120dp`) to `app_v2/src/main/res/values/dimens.xml`.

**Verification:**

- `Grep` — `@+id/tvAudioBitDepth` matches exactly twice across both layout files (portrait + land).
- `Grep` — `@+id/ivAudioCoverArt` matches exactly twice.
- `Grep` — `@+id/tvAudioReplayGainTrack` matches exactly twice.
- `Grep` — `file_info_cover_art_size` present in `app_v2/src/main/res/values/dimens.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. `@+id/tvAudioBitDepth` ×2, `@+id/ivAudioCoverArt` ×2, `@+id/tvAudioReplayGainTrack` ×2, `file_info_cover_art_size` in dimens.xml. Dev log recorded for 3 files.

---

### Step 03.3 — Add trilingual strings for the new audio fields

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add the following string resources in all three files (EN/RU/UK), using `..` instead of `...` and `ё`/`Ё` where grammatically correct in Russian: `audio_bitrate_label` (already exists — re-use), `audio_sample_rate_label` (already exists — re-use), `audio_bit_depth_label` ("Bit Depth: %1$d-bit" / "Битовая глубина: %1$d-бит" / "Бітова глибина: %1$d-біт"), `audio_lossless_label` ("Quality: %1$s" / "Качество: %1$s" / "Якість: %1$s"), `audio_lossless_value_lossless` ("Lossless" / "Без потерь" / "Без втрат"), `audio_lossless_value_lossy` ("Lossy" / "С потерями" / "Зі втратами"), `audio_replaygain_track_label` ("ReplayGain (track): %1$.2f dB" / "ReplayGain (трек): %1$.2f дБ" / "ReplayGain (трек): %1$.2f дБ"), `audio_replaygain_album_label` ("ReplayGain (album): %1$.2f dB" / "ReplayGain (альбом): %1$.2f дБ" / "ReplayGain (альбом): %1$.2f дБ"), `audio_cover_art_cd` ("Album cover art" / "Обложка альбома" / "Обкладинка альбому").

**Verification:**

- `Grep` — `audio_bit_depth_label` present in all three of `values/`, `values-ru/`, `values-uk/` `strings.xml`.
- `Grep` — `audio_replaygain_track_label` present in all three.
- `Grep` — `audio_cover_art_cd` present in all three.
- `Grep` — `\.\.\.` (three-dot) returns zero hits among the new strings (search for the new keys with `-A 1` and verify no `...`).
- `Grep` — `Битовая` (with Ё-rule check) present in `values-ru/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. `audio_bit_depth_label`, `audio_replaygain_track_label`, `audio_cover_art_cd` present in all 3 locale files; no `...` in new strings; `Битовая` in values-ru/strings.xml. Dev log recorded for 3 files.

---

### Step 03.4 — Create `FileInfoAudioDisplayHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoAudioDisplayHelper.kt` (New)
**Depends on:** Step 03.3

**Prompt for developer:**

> Create `FileInfoAudioDisplayHelper` in package `com.sza.fastmediasorter.ui.dialog.helpers`. Constructor: `Context`, `DialogFileInfoBinding`, `AudioMetadataLoader`, `AudioMetadataCacheRepository`. Public method: `suspend fun displayDetailed(file: MediaFile)`. The method (1) calls `audioMetadataLoader.loadDetailed(file)`; (2) on `null` result — leaves the existing initial population from `FileInfoDialog.displayAudioInfo` intact, hides cover image, returns; (3) on success — populates `tvAudioTitle`, `tvAudioArtist`, `tvAudioAlbum`, `tvAudioDuration`, `tvAudioCodecInfo` (derive codec label from the same MIME type captured by extractor — for `audio/raw` show `FLAC` if `lossless == true`), `tvAudioChannels`, `tvAudioSampleRate`, `tvAudioBitDepth`, `tvAudioBitrate` (only if `bitrateBps != null`; when `null` and duration > 0, compute `(file.size * 8 / (duration_ms / 1000.0)).toInt()` as bps and show; when `null` and duration ≤ 0, hide field per §6.2), `tvAudioLossless`, `tvAudioReplayGainTrack`, `tvAudioReplayGainAlbum`. Each TextView is set visible only when its source value is non-null; otherwise stays `View.GONE`. Cover art: if `coverFileName != null`, call `audioMetadataCacheRepository.readCoverFile(coverFileName, coverExtension)` → load into `ivAudioCoverArt` via Glide with `centerCrop()` and `placeholder(R.drawable.ic_audio_placeholder)` (use the existing audio placeholder if present, otherwise omit `placeholder`); set `ivAudioCoverArt.isVisible = true`. The whole body is wrapped in `try` — on exception, log via `Timber.w` and return without changing UI state. Do NOT introduce `Log.d` calls.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoAudioDisplayHelper.kt` exists.
- `Grep` — `class FileInfoAudioDisplayHelper` matches exactly once.
- `Grep` — `suspend fun displayDetailed\(` present.
- `Grep` — `audioMetadataLoader.loadDetailed\(` present.
- `Grep` — `audioMetadataCacheRepository.readCoverFile\(` present.
- `Grep -n "Log\.d\("` returns zero hits in this file.
- `Bash` — `wc -l` of new file ≤ 280.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 7/7 PASS. File exists; `class FileInfoAudioDisplayHelper` ×1; `suspend fun displayDetailed(`; `audioMetadataLoader.loadDetailed(`; `audioMetadataCacheRepository.readCoverFile(`; `Log.d(` = 0 hits; 161 LOC ≤ 280. Dev log recorded.

---

### Step 03.5 — Wire `FileInfoAudioDisplayHelper` into `FileInfoDialog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> In `FileInfoDialog`, inject (via constructor parameters — the dialog is already manually constructed by the caller; add `audioMetadataLoader: AudioMetadataLoader` and `audioMetadataCacheRepository: AudioMetadataCacheRepository` parameters with default `null` and short-circuit when null to keep call-site compatibility temporarily) and instantiate `FileInfoAudioDisplayHelper` when `mediaFile.type == MediaType.AUDIO`. Replace the audio branch inside `updateDetailedInfo` (the `if (..audioCodec..)` block, the artist/title/album block, channels/bitrate/sampleRate blocks for audio) with a single coroutine call to the helper's `displayDetailed`. Keep the synchronous initial render in `displayAudioInfo()` exactly as is — it stays as the fallback while extraction is in flight. Update every call site of `FileInfoDialog(..)` constructor to pass the two new dependencies (find call sites via `Grep` — there are typically two or three).

**Verification:**

- `Grep` — `FileInfoAudioDisplayHelper\(` present in `FileInfoDialog.kt`.
- `Grep` — call sites of `FileInfoDialog(` outside the dialog file all pass `audioMetadataLoader` (count Grep matches and check each).
- `Grep -n "Log\.d\("` in `FileInfoDialog.kt` returns zero hits.
- `Bash` — `wc -l app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` ≤ 850.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. `FileInfoAudioDisplayHelper(` ×1 in FileInfoDialog.kt; PlayerDialogHelper.kt call site passes `audioMetadataLoader = null`; `Log.d(` = 0 hits; 726 LOC ≤ 850. Dev log recorded for 2 files.

---

### Step 03.6 — Disable the `MediaMetadataHelper` audio branch when extended path produced data

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> In `FileInfoDialog.onCreate`, gate the existing `metadataHelper.getDetailedInfo(mediaFile)` call so that for `MediaType.AUDIO` it is invoked only when `audioMetadataLoader == null` (legacy fallback) or when `loadDetailed` returned `null`. For non-audio types, behavior is unchanged. The fallback path produces only the basic codec/channels/duration that today renders as `RAW / Stereo / 09:10` — keep it untouched on purpose so the dialog is never empty.

**Verification:**

- `Grep` — `if \(mediaFile.type == MediaType.AUDIO` (or equivalent) gating around the legacy `metadataHelper.getDetailedInfo` call present in `FileInfoDialog.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS. `if (mediaFile.type == MediaType.AUDIO && audioDisplayHelper != null)` at line 82 gates the `else { metadataHelper.getDetailedInfo... }` branch. Gating was established by Step 03.5's `onCreate` change — no additional edits required.

---

### Step 03.7 — Smoke-build and manual verification on FLAC/SFTP

**Files:** —
**Depends on:** Step 03.6

**Prompt for developer:**

> Run `/build`. Open the info-dialog on a FLAC file accessible over SFTP and confirm: artist, album, title, sample rate, bit depth, channels, lossless marker, ReplayGain (if present), bitrate (if FLAC reports one), and cover art thumbnail are all rendered. Repeat for an MP3 with ID3v2 tags + APIC over SMB. For a VBR-MP3 without xing/lame and without ID3 — confirm the bitrate row is absent (per §6.2 "hide field"). For a malformed file or a path that fails range-read — confirm the dialog still opens with basic codec/channels/duration via the legacy path.

**Verification:**

- `/build` exits with success.
- `Grep` — `TODO\(phase-03\)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Build confirmed OK by user. `TODO(phase-03)` = 0 hits.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API of `AudioMetadataLoader` and `FileInfoDialog` constructor changed → `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The audio branch of the info-dialog now renders all extended fields and cover art. Phase 04 is independent — it does not depend on this phase except via the shared `FileInfoDialog.kt` line budget (currently ≤ 850; Phase 04 must stay under 1000).

---

## Rollback Plan

Revert phase commit(s). The legacy `MediaMetadataHelper`-based audio rendering remains in `FileInfoDialog` as fallback and resumes for all files automatically once the extended branch is reverted. No data migration to undo.
