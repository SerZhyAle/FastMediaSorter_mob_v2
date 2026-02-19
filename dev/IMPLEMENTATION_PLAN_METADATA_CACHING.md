# Developer Specification: Metadata Caching and Rendering

## 1. Document Control

- **Document ID:** FMS-META-CACHE-SPEC-001
- **Status:** Draft for implementation
- **Target module:** `app_v2`
- **Primary owners:** Domain/Data/UI maintainers
- **Last updated:** 2026-02-19

## 2. Goal

Persist and display extended media metadata in cached file lists for resources with file-list caching enabled, without blocking UI and without breaking backward compatibility of cached payloads.

## 3. Scope

### In Scope

1. Metadata extraction during scan/index flow.
2. Metadata persistence in existing cached file-list payload (GZIP JSON/BLOB).
3. Metadata rendering in file list item UI.
4. Optional sorting/filtering extensions based on metadata.

### Out of Scope

1. New standalone metadata database tables.
2. Foreground services for metadata extraction.
3. Full-text search index for metadata.
4. Metadata editing/writing back into media files.

## 4. Functional Requirements

### FR-1: Metadata Extraction Trigger

1. Metadata extraction **must run only when** resource option `rememberFileList` is enabled.
2. Extraction **must execute on** `Dispatchers.IO`.
3. Extraction failures **must not fail** the whole scan; fallback to partial/no metadata per file.

### FR-2: Audio Metadata

For audio files, extract when available:

1. `artist`
2. `album`
3. `title`
4. `duration` (already present in model, ensure population consistency)

Source API: `MediaMetadataRetriever` (`METADATA_KEY_ARTIST`, `METADATA_KEY_ALBUM`, `METADATA_KEY_TITLE`, `METADATA_KEY_DURATION`).

### FR-3: Video Metadata

For video files, extract when available:

1. `width`
2. `height`
3. `duration`
4. `videoRotation` (if already modeled)

Source API: `MediaMetadataRetriever` (`METADATA_KEY_VIDEO_WIDTH`, `METADATA_KEY_VIDEO_HEIGHT`, `METADATA_KEY_DURATION`, `METADATA_KEY_VIDEO_ROTATION`).

### FR-4: Image Metadata

For image files, extract when available:

1. `width`
2. `height`
3. `exifDateTime` (normalized to epoch millis if parser exists; otherwise keep current project representation)

Source API: `ExifInterface` (`TAG_IMAGE_WIDTH`, `TAG_IMAGE_LENGTH`/`TAG_IMAGE_WIDTH` fallback, `TAG_DATETIME_ORIGINAL`).

### FR-5: Persistence and Backward Compatibility

1. New metadata fields must be nullable.
2. Existing cached entries must remain readable without migration failures.
3. Unknown/missing JSON fields must be ignored safely.

### FR-6: UI Rendering

In list item binding:

1. Audio display format: `"{artist} - {title} • {duration}"` (fallbacks allowed if one part missing).
2. Video display format: `"{width}x{height} • {duration}"`.
3. Image display format: `"{width}x{height} • {dateTaken}"`.
4. If no metadata available, keep current default info text behavior.

### FR-7: Sorting/Filtering (Optional Flagged Delivery)

If implemented in this iteration:

1. Add sort modes for artist/duration/dateTaken.
2. Null-safe comparator behavior is mandatory.
3. Existing sort behavior must remain unchanged for resources lacking metadata.

## 5. Data Contract Changes

## 5.1 Domain Model (`MediaFile`)

File: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`

Required additions (if not already present):

```kotlin
val artist: String? = null
val album: String? = null
val title: String? = null
```

Existing fields to be populated consistently (already present in project model):
`duration`, `width`, `height`, `exifDateTime`, `videoRotation`.

## 5.2 Serialization Rules

1. JSON/BLOB schema version bump is not required if parser is tolerant.
2. Any parser config change must preserve reading legacy payloads.

## 6. Architecture and Integration Points

### 6.1 Candidate Integration Points

1. `GetMediaFilesUseCase`
2. Repository/scanner layer creating `MediaFile` instances

### 6.2 New/Updated Helper

Introduce or extend `MediaMetadataHelper` with API:

```kotlin
interface MediaMetadataHelper {
    suspend fun extractAudio(path: String): AudioMeta?
    suspend fun extractVideo(path: String): VideoMeta?
    suspend fun extractImage(path: String): ImageMeta?
}
```

Requirements:

1. Fail-fast per file, no crash propagation.
2. Ensure retriever/resources are always released.

## 7. Performance and Reliability Requirements (NFR)

1. No main-thread blocking during extraction.
2. Scan throughput degradation should be bounded; target overhead <= 20% for local storage baseline.
3. OOM safety: avoid retaining large metadata retriever objects; process file-by-file.
4. Corrupted media/EXIF must be logged (Timber) and skipped.

## 8. UI Specification

### 8.1 Layout

File: `app_v2/src/main/res/layout/item_media_file.xml`

1. Reuse existing secondary info text where possible.
2. Do not add extra rows if current item layout can render metadata in existing info line.

### 8.2 Adapter

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`

1. Add metadata formatter methods per media type.
2. Use deterministic fallback order (artist -> title -> filename; metadata missing -> legacy info text).

## 9. Logging and Diagnostics

1. Use `Timber` only.
2. Log extraction errors at `d/w` level with file path hash or shortened path (avoid sensitive leakage in release logs).
3. Add counters (optional) for extracted/failed metadata items per scan session.

## 10. Acceptance Criteria

1. Resource with `rememberFileList=true` shows metadata after scan and after app restart from cache.
2. Resource with `rememberFileList=false` does not perform extended extraction path.
3. Audio/video/image metadata renders according to FR-6 formats.
4. Missing metadata does not produce crashes or blank critical UI states.
5. Build passes: `assembleStandardDebug`.
6. No new lint errors in touched files.

## 11. Test Matrix

### 11.1 Unit Tests

1. Metadata formatters: complete, partial, empty input cases.
2. Null-safe sort comparators (if FR-7 delivered).

### 11.2 Integration/Manual Tests

1. Audio with full tags, partial tags, no tags.
2. Video with and without rotation metadata.
3. Images with EXIF datetime and without EXIF.
4. Corrupted files and unsupported formats.
5. Restart app and validate cached metadata persistence.

## 12. Rollout Strategy

1. Implement extraction + rendering first.
2. Keep sorting/filtering extension behind separate commit/feature switch if needed.
3. Validate on local + SMB/SFTP/FTP resource paths where metadata extraction is supported by current pipeline.

## 13. Risks and Mitigations

1. **Risk:** Scan slowdown on large libraries.  
   **Mitigation:** IO dispatcher, per-file timeout strategy (optional), progressive rendering.
2. **Risk:** Inconsistent metadata across protocols.  
   **Mitigation:** protocol-specific guardrails and fallback to legacy UI info.
3. **Risk:** Legacy cache parsing regressions.  
   **Mitigation:** backward compatibility tests with old cache payload samples.

## 14. Definition of Done

1. All mandatory FRs implemented.
2. Acceptance criteria satisfied.
3. Manual smoke test completed on at least one resource per type (audio/video/image).
4. Spec-linked implementation notes added to PR description.

## 15. Consolidated Delivery Scope (Spec + Task + Todo)

This execution plan consolidates:

1. `dev/IMPLEMENTATION_PLAN_METADATA_CACHING.md`
2. `dev/TASK_METADATA_CACHING.md`
3. `dev/todo.md`

Mandatory combined outcomes:

1. Metadata extraction/persistence/rendering for cached file lists (`rememberFileList=true`).
2. Metadata-based sorting/filtering (artist/title/duration/dateTaken) with null-safe behavior.
3. Resource profile selection during resource creation (`Audio Library`, `Video Library`, `Photo Storage`, `Documents`, `All Files`) with automatic type filter presets.
4. Foundation for profile-specific Browse behavior (current iteration: profile field + routing hooks; full dedicated screens can be delivered incrementally).

## 16. Step-by-Step Developer Execution Plan

Rule for all steps:

1. Execute step prompt.
2. Verify expected result.
3. Run build.
4. Commit.

---

### Step 1 — Domain Contract Foundation

**Prompt**

1. Extend `MediaFile` with nullable audio metadata fields:
   - `artist: String? = null`
   - `album: String? = null`
   - `title: String? = null`
2. Extend `SortMode` with metadata sorts:
   - `ARTIST_ASC`, `ARTIST_DESC`
   - `TITLE_ASC`, `TITLE_DESC`
   - `DURATION_ASC`, `DURATION_DESC`
   - `DATE_TAKEN_ASC`, `DATE_TAKEN_DESC`
3. Update all in-memory sort points (`BrowseFileListManager`, use-case sort fallbacks) with null-safe comparators.
4. Preserve backward compatibility of cache JSON (new fields nullable, default values only).

**Expected Result**

1. Code compiles without constructor breakages.
2. Existing cache entries deserialize without migration.
3. New sort modes are accepted and do not crash on null metadata.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(metadata-cache): add audio metadata fields and null-safe metadata sort modes`

---

### Step 2 — Metadata Extractor Service (IO-only)

**Prompt**

1. Add/extend metadata extraction service (`MediaMetadataHelper` or dedicated helper) with focused methods:
   - `extractAudio(path)`
   - `extractVideo(path)`
   - `extractImage(path)`
2. Use `MediaMetadataRetriever` for audio/video and `ExifInterface` for images.
3. Ensure strict safety:
   - `Dispatchers.IO`
   - per-file try/catch
   - guaranteed retriever release
   - Timber debug/warn logs without sensitive data leakage.

**Expected Result**

1. Extraction is isolated and reusable.
2. Corrupted media never crashes scan flow.
3. Helper returns partial metadata when only some fields are available.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(metadata-cache): add resilient IO metadata extractor for audio video image`

---

### Step 3 — Scan Pipeline Integration (`rememberFileList` gated)

**Prompt**

1. Integrate metadata enrichment into scan/index flow only when `resource.rememberFileList == true`.
2. Enrich `MediaFile` objects before DB cached-list save.
3. For `rememberFileList == false`, skip extended extraction path entirely.
4. Keep scan resilient: metadata failure for one file must not fail entire list.

**Expected Result**

1. Cached list stores enriched metadata for remembered resources.
2. Non-remembered resources keep previous scan performance path.
3. No regressions in folder loading stability.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(metadata-cache): enrich scanned media only for rememberFileList resources`

---

### Step 4 — Protocol-Specific Coverage (SMB/SFTP/FTP/Cloud)

**Prompt**

1. Reuse existing metadata present in scanners (e.g., SMB video/EXIF).
2. Add safe extraction path for missing metadata where technically feasible per protocol.
3. Keep network overhead bounded; do not introduce blocking on main thread.
4. Log skipped/failed protocol extraction at debug level.

**Expected Result**

1. Metadata quality improves without protocol regressions.
2. Remote resources still load reliably under failures/timeouts.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(metadata-cache): improve protocol metadata enrichment with graceful fallbacks`

---

### Step 5 — Browse UI Rendering (Metadata Info Line)

**Prompt**

1. Update file info formatter in `MediaFileAdapter`:
   - Audio: `artist - title • duration` with fallback chain.
   - Video: `widthxheight • duration`.
   - Image: `widthxheight • dateTaken`.
2. Preserve existing fallback line (`size • date`) when metadata unavailable.
3. Do not add extra UI rows; reuse current info text line.

**Expected Result**

1. Metadata is visible in list items after scan and after restart from cache.
2. Legacy items still show valid fallback info.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(metadata-cache): render audio video image metadata in browse list info line`

---

### Step 6 — Metadata Sorting and Filtering Delivery

**Prompt**

1. Wire new sort modes into UI state handling and persistence.
2. Add filter hooks for metadata fields (artist/title/duration/dateTaken) in existing filter pipeline.
3. Null-safe behavior mandatory; resources without metadata must keep stable sorting behavior.

**Expected Result**

1. User can sort/filter by new metadata fields.
2. No crashes on mixed legacy/new cached entries.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(metadata-cache): add metadata sort and filter support with null-safe comparators`

---

### Step 7 — Resource Profile Selector (from todo)

**Prompt**

1. In resource creation/edit flow, add dropdown profile selector:
   - `Audio Library`, `Video Library`, `Photo Storage`, `Documents`, `All Files`.
2. On selection, apply default media type filters and related flags.
3. Persist selected profile in resource model/entity.

**Expected Result**

1. New resource can be created with profile preset in one action.
2. Filter setup is deterministic and persisted.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`feat(resource-profile): add resource profile selector with filter presets`

---

### Step 8 — Profile-Aware Browse Routing Hooks

**Prompt**

1. Add routing hooks in Browse entry flow for profile-specific behavior.
2. Keep current screens intact; introduce minimal abstraction for future dedicated profile screens.
3. Ensure current audio-specialized behavior remains compatible.

**Expected Result**

1. Profile is available as behavior switch in Browse pipeline.
2. No regression for existing resources without explicit profile.

**Build**

`./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`

**Commit**

`refactor(browse): introduce profile-aware browse routing hooks`

---

### Step 9 — Tests and Diagnostics

**Prompt**

1. Add/update unit tests:
   - metadata formatters (full/partial/empty)
   - metadata sort comparators null-safe behavior
2. Add focused diagnostics counters for extraction success/failure per scan session (debug logs).
3. Validate cache backward compatibility with old payload sample.

**Expected Result**

1. New metadata behavior is covered by deterministic tests.
2. Failures become diagnosable without noisy logs.

**Build**

`./gradlew.bat :app_v2:testStandardDebugUnitTest`

**Commit**

`test(metadata-cache): add formatter and comparator coverage plus extraction diagnostics`

---

### Step 10 — Final Verification and Release Notes

**Prompt**

1. Run final build and lint for touched areas.
2. Execute manual smoke matrix:
   - rememberFileList true/false
   - audio/video/image metadata display from fresh scan and from DB cache after restart
   - metadata sorts/filters
   - profile selector preset behavior
3. Update implementation notes for PR.

**Expected Result**

1. Acceptance criteria from spec and task are met.
2. Release notes clearly document compatibility and limitations.

**Build**

1. `./gradlew.bat :app_v2:compileStandardDebugKotlin --quiet`
2. `./gradlew.bat :app_v2:lintStandardDebug`

**Commit**

`chore(metadata-cache): finalize verification checklist and implementation notes`

## 17. Realization Start Point

Implementation starts from **Step 1** in this sequence.

## 18. Build/Commit Policy

1. No next step starts until previous step build succeeds.
2. Commit is mandatory after each successful step.
3. If build fails, fix in the same step before commit.
4. If failure is external/unrelated, document blocker in commit message footer and continue only after user confirmation.
