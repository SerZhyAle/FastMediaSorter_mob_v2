# Phase 02 — Extend `AudioMetadataLoader` with Technical Fields and Cover Art

**Strategic spec:** [`../S0048_info-dialog-extended-metadata.md`](../S0048_info-dialog-extended-metadata.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Extend the existing partial-stream audio extractor (`AudioMetadataLoader`) to capture sample rate, bit depth, channel count, lossless marker, ReplayGain (track + album), and embedded cover-art bytes (FLAC `PictureFrame`, ID3v2 `APIC`, MP4 `covr`). Persist cover bytes via the existing `AudioMetadataCacheRepository.saveCover()` API. No DB schema change. No UI changes.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved — yes.
- [ ] Working tree is clean or on a feature branch.
- [ ] Confirm `AudioMetadataCacheRepository.saveCover(audioFileName, imageBytes, extension)` is reachable from `AudioMetadataLoader` (check Hilt graph; if a binding is missing, add a constructor parameter and adjust DI).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AudioMetadataCacheRepository.kt` | Modified | ≤ 250 |

> `AudioMetadataLoader.kt` projected from 574 to ~750 lines → backup step required.

---

## Steps

### Step 02.1 — Backup `AudioMetadataLoader.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup at `temp/AudioMetadataLoader.kt.<YYYYMMDD-HHMMSS>.bak` before extending the file (it will cross the 500-line backup threshold after edits).

**Verification:**

- `Glob` — at least one file matching `temp/AudioMetadataLoader.kt.*.bak` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS. File: `temp/AudioMetadataLoader.kt.20260502-041650.bak` (574 LOC). Dev log recorded.

---

### Step 02.2 — Extend the `AudioMetadata` data class with technical fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the inner `AudioMetadata` data class in `AudioMetadataLoader.kt` with the following nullable fields, defaulted to `null`: `sampleRateHz: Int?`, `bitDepth: Int?`, `channels: Int?`, `bitrateBps: Int?`, `lossless: Boolean?`, `replayGainTrackDb: Float?`, `replayGainAlbumDb: Float?`, `coverFileName: String?` (key used by `AudioMetadataCacheRepository`), `coverExtension: String?`. Update `hasAnyData()` so the new fields participate. Keep the existing four fields (`artist`, `album`, `title`, `duration`) unchanged. Do not persist new fields to Room — strategic §3.2 forbids schema migration.

**Verification:**

- `Grep` — `val sampleRateHz: Int\? = null` present in `AudioMetadataLoader.kt`.
- `Grep` — `val bitDepth: Int\? = null` present.
- `Grep` — `val replayGainTrackDb: Float\? = null` present.
- `Grep` — `val coverFileName: String\? = null` present.
- `Grep` — `@Database\(version` in `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/` returns the same version as before this phase started (no schema bump).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. `AudioMetadata` extended with 9 new nullable fields; `hasAnyData()` updated; Room version unchanged (26). Dev log recorded.

---

### Step 02.3 — Capture sample rate, bit depth, channels, lossless from track `Format`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `extractMetadataFromBytes`, after iterating over `metadata`, also read the `androidx.media3.common.Format` of each audio track and capture: `sampleRate` → `sampleRateHz`; `pcmEncoding`/`channelCount` → `channels`; `bitrate` → `bitrateBps` (only when `Format.NO_VALUE != value` and value > 0). Set `lossless` to `true` for sample MIME types `audio/flac`, `audio/raw`, `audio/x-wav`, `audio/x-alac`; `false` for `audio/mpeg`, `audio/mp4a-latm`, `audio/vorbis`, `audio/opus`; otherwise `null`. For FLAC, parse bit depth from the STREAMINFO block visible inside the 64 KB header (read bytes at known FLAC-marker offset and decode bits-per-sample directly — do not rely on Media3 returning it).

**Verification:**

- `Grep` — `format.sampleRate` referenced in `AudioMetadataLoader.kt`.
- `Grep` — `audio/flac` literal present.
- `Grep` — `STREAMINFO` (case-insensitive comment or constant name) present.
- `Grep` — function/property reading bits-per-sample from FLAC, e.g. `bitDepth = ` assignment, present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. Added 7 new local vars, format reading block (sampleRate/channels/bitrate/lossless/bitDepth), `parseFlacBitDepth` helper (STREAMINFO byte parsing). Updated `AudioMetadata(...)` constructor call with new fields. Dev log recorded.

---

### Step 02.4 — Capture ReplayGain from Vorbis comments and ID3 frames

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `extractFromMetadataEntry`, recognise additional Vorbis comment keys `REPLAYGAIN_TRACK_GAIN` and `REPLAYGAIN_ALBUM_GAIN` and ID3v2 frames `TXXX` with description equal to those names. Parse the value as a float in dB (strip trailing " dB" if present). Wire the parsed values into the new `replayGainTrackDb` / `replayGainAlbumDb` fields of `AudioMetadata`. Wrap the parse in `try` to keep extraction tolerant of malformed values.

**Verification:**

- `Grep` — `REPLAYGAIN_TRACK_GAIN` literal present in `AudioMetadataLoader.kt`.
- `Grep` — `REPLAYGAIN_ALBUM_GAIN` literal present.
- `Grep` — `id == "TXXX"` (TXXX handling via `TextInformationFrame`) present in `AudioMetadataLoader.kt`. Note: Media3 1.2.1 does not have a separate `TxxxFrame` class — TXXX is dispatched through `TextInformationFrame.id`.
- `Grep` — `replayGainTrackDb = ` assignment present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. Added TXXX handling in `TextInformationFrame` branch (id == "TXXX" + description check); added REPLAYGAIN keys to VorbisComment `when` block; dispatch in `extractMetadataFromBytes` already in place from Step 02.3. Dev log recorded.

---

### Step 02.5 — Capture embedded cover art and persist via `AudioMetadataCacheRepository`


**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AudioMetadataCacheRepository.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Stop returning `null` for `androidx.media3.extractor.metadata.flac.PictureFrame` in `extractFromMetadataEntry`. Instead, capture its `pictureData` (FLAC `METADATA_BLOCK_PICTURE`). Also recognise `androidx.media3.extractor.metadata.id3.ApicFrame` (ID3v2 APIC — present in MP3 and OGG-encapsulated streams). Note: Media3 1.2.1 does not expose MP4 `covr` atoms as a distinct `Metadata.Entry` — skip MP4 cover art (it is not reachable via `MetadataRetriever.retrieveMetadata()`). Derive a stable cache key (e.g. SHA-256 of the file path, hex, first 16 bytes), determine the file extension from the picture's `mimeType` (`image/jpeg` → `jpg`, `image/png` → `png`, fallback `jpg`), and call `AudioMetadataCacheRepository.saveCover(cacheKey, bytes, extension)`. Set `coverFileName` and `coverExtension` on the returned `AudioMetadata`. If the picture's byte array size exceeds `MAX_COVER_BYTES = 4 * 1024 * 1024`, skip it and leave `coverFileName = null`. Inject `AudioMetadataCacheRepository` as a constructor dependency on `AudioMetadataLoader` — both classes use `@Singleton @Inject constructor`, so no new `@Provides` or `@Module` is required. Also change the signature of `extractMetadataFromBytes` to accept a `filePath: String` parameter (needed for the cache key), update the single call site accordingly. Add a public method `readCoverFile(coverFileName: String, coverExtension: String): java.io.File?` on `AudioMetadataCacheRepository` that returns the existing cover file or `null`.

**Verification:**

- `Grep` — `pictureData` referenced in `AudioMetadataLoader.kt`.
- `Grep` — `ApicFrame` referenced in `AudioMetadataLoader.kt`.
- `Grep` — `audioMetadataCacheRepository.saveCover\(` present.
- `Grep` — `MAX_COVER_BYTES` constant defined.
- `Grep` — `fun readCoverFile\(` matches exactly once in `AudioMetadataCacheRepository.kt`.
- `Grep` — `is androidx.media3.extractor.metadata.flac.PictureFrame -> null` returns zero hits in `AudioMetadataLoader.kt` (the previous explicit-skip line is gone).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 6/6 PASS. Added `AudioMetadataCacheRepository` constructor param; `MAX_COVER_BYTES`; `extractCoverArtEntry` helper (PictureFrame + ApicFrame); `coverCacheKey` (SHA-256 hex); cover art loop with saveCover; updated `AudioMetadata` constructor; removed `PictureFrame -> null` branch; changed `extractMetadataFromBytes` signature + call site; added `readCoverFile` to repository. AudioMetadataLoader.kt = 710 LOC (≤ 800); AudioMetadataCacheRepository.kt = 154 LOC (≤ 250). Dev log recorded.

---

### Step 02.6 — Smoke-build and confirm zero regression on enrichment callback

**Files:** —
**Depends on:** Step 02.5

**Prompt for developer:**

> Run `/build`. Verify that the existing scroll-idle enrichment of audio rows in browse view still applies artist/album/title — extension must not break the legacy code path. The `loadIfNeeded(file, onLoaded)` API, `applyMetadata`, and the Room-cache write must remain compatible (technical fields are simply ignored by the existing `MediaFile.copy(..)` in `applyMetadata`).

**Verification:**

- `/build` exits with success.
- `Grep` — `TODO\(phase-02\)` returns zero hits in `app_v2/src/main/java/`.
- `Grep -n "Log\.d\("` in `AudioMetadataLoader.kt` returns zero hits.
- `Grep -n "Log\.d\("` in `AudioMetadataCacheRepository.kt` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Static checks PASS: `TODO(phase-02)` = 0 hits; `Log.d` = 0 hits in both files. Build not yet executed — awaiting user-initiated build run before flipping to `[x] done`.
- 2026-05-02 — Verification 4/4 PASS. Build confirmed OK by user (fix: `entry.description?.uppercase() ?: return null` — `description` is nullable in Kotlin interop). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API of `AudioMetadataLoader` and `AudioMetadataCacheRepository` changed → `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`AudioMetadataLoader` now returns extended `AudioMetadata` (sample rate, bit depth, channels, bitrate, lossless flag, ReplayGain, cover-file reference). The cover bytes live on disk under `audio_metadata_cache/` keyed by SHA-256 of the path. Phase 03 consumes these fields directly from `getCachedMetadata(path)` and renders them in the dialog. The Room cache continues to store only artist/album/title/duration — no migration needed.

---

## Rollback Plan

Revert phase commit(s). The Room schema is untouched. Cached cover files in `audio_metadata_cache/` orphan harmlessly and will be evicted by the existing 30-day TTL cleanup.
