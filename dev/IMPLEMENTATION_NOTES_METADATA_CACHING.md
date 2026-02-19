# Implementation Notes: Metadata Caching (FMS-SPEC-METADATA-CACHE-002)

**Status:** COMPLETE  
**Date:** 2026-02-19  
**Commits:** `c4d63e6` → `169042e` (7 commits total)

---

## Completed Steps

| Step | Description | Commit |
|------|-------------|--------|
| 1 | Domain contract — `artist`, `album`, `title` on `MediaFile`; 8 new `SortMode` entries | `c4d63e6` |
| 2-3 | `CachedMediaMetadataExtractor` + `rememberFileList` gate in `GetMediaFilesUseCase` | `306c9f0` |
| 5 | Browse UI — metadata-aware file info line in `MediaFileAdapter` | `a5098b1` |
| 6 | Sort/Filter UI — type-aware sort mode filtering in `showSortPopupMenu` | `d2859a8` |
| 7 | Resource Profile Selector — `ResourceProfile` enum, DB migration v9→10, editor UI | `5af9141` |
| 8 | Browse routing hooks — profile-aware effective sort in `BrowseViewModel.loadResource()` | `c8b5313` |
| 9 | Tests + Diagnostics — `MediaFormatUtils`, `formatMediaDuration`, extractor counters | `169042e` |

## Key Compatibility Notes

- **DB migration**: v9 → v10, `ALTER TABLE resources ADD COLUMN profile TEXT NOT NULL DEFAULT 'NONE'`
- **Cache backward compat**: `MediaFile.artist/album/title` default to `null`; old cache entries display `name` as fallback in `buildFileInfo()`
- **Profile field** stored as enum name string; parsed with `runCatching { ResourceProfile.valueOf(...) }.getOrDefault(NONE)` — safe for future values
- **Sort gate**: new metadata sorts (`ARTIST_ASC`, etc.) only shown in sort menu when resource type supports them

## Accepted Limitations (Out of Scope)

- Network resources (SMB/FTP/SFTP/Cloud): metadata NOT enriched — `isLocalPath()` guard in extractor
- Documents: no metadata extraction in this release
- Profile-specific Browse screens (audio library UI, etc.): future work — routing hook is minimal placeholder
- Embedded cover art loading: separate future task

## Smoke Test Matrix

| Scenario | Expected |
|----------|----------|
| Audio resource, `rememberFileList=true` | `artist - title • duration` shown in file list |
| Audio resource, `rememberFileList=false` | `size • date` fallback |
| AUDIO_LIBRARY profile, fresh open | Sort defaults to `ARTIST_ASC` |
| PHOTO_STORAGE profile, fresh open | Sort defaults to `DATE_TAKEN_DESC` |
| Sort menu, audio resource | Shows ARTIST/TITLE/DURATION sorts, hides DATE_TAKEN |
| Sort menu, image resource | Shows DATE_TAKEN sorts, hides ARTIST/TITLE |
| Profile selector dialog | Sets media types + allFiles per preset; resets on manual type change |
| DB upgrade from v9 | Resources load with `profile=NONE` default |
