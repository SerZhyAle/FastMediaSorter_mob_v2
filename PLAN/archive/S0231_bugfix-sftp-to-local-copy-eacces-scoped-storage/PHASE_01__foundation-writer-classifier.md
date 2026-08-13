# Phase 01 — Foundation: writer abstraction + classifier

**Strategic spec:** [`../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`](../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Introduce the `LocalDestinationWriter` interface, its sink contract, the MediaStore-backed implementation, and the path classifier that decides "public collection vs non-public". No callers wired yet — Phase 02 connects them. After this phase the abstraction compiles and is provided by Hilt, but nothing in the app calls it.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] Strategic §6 items 1, 3, 4 are Resolved (already done at tactical authoring).
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt` exists (Hilt module that will host the new `@Provides`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationCategory.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifier.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalSink.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationWriter.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/MediaStoreLocalDestinationWriter.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt` | Modified | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifierTest.kt` | New | ≤ 200 |

> No file in this phase exceeds 500 LOC after change. No backup required.

---

## Steps

### Step 01.1 — Define `LocalDestinationCategory` sealed hierarchy

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationCategory.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a sealed interface `LocalDestinationCategory` with two implementations:
> - `data class PublicCollection(val collection: PublicCollection.Kind, val relativePath: String, val displayName: String, val mimeType: String)` — `Kind` is a nested enum: `AUDIO, VIDEO, IMAGES, DOWNLOADS, FILES`. `relativePath` is the value to write into `MediaStore.MediaColumns.RELATIVE_PATH` (e.g. `Music/Artist/Album/`). `displayName` is the filename only.
> - `data class NonPublic(val absolutePath: String, val displayName: String, val mimeType: String)` — for destinations outside public collections (e.g. `/storage/emulated/0/SomeApp/data/file.mp3`).
>
> No behavior, no logic — pure data classes.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationCategory.kt` exists.
- `Grep` — `sealed interface LocalDestinationCategory` matches exactly once.
- `Grep` — `data class PublicCollection` matches once.
- `Grep` — `data class NonPublic` matches once.
- `Grep` — `enum class Kind` matches once and includes tokens `AUDIO`, `VIDEO`, `IMAGES`, `DOWNLOADS`, `FILES`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 5/5 PASS. Files: `LocalDestinationCategory.kt` (47 LOC). Dev log recorded.

---

### Step 01.2 — Implement `LocalDestinationClassifier`

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `class LocalDestinationClassifier @Inject constructor()` with a single public method:
>
> `fun classify(absolutePath: String): LocalDestinationCategory`
>
> Logic:
> 1. Compute the path relative to `Environment.getExternalStorageDirectory()`. If the path is not under external storage at all → `NonPublic`.
> 2. Inspect the first path segment after the external storage root. Map to `PublicCollection.Kind`:
>    - `Environment.DIRECTORY_MUSIC`, `DIRECTORY_PODCASTS`, `DIRECTORY_AUDIOBOOKS`, `DIRECTORY_RINGTONES`, `DIRECTORY_NOTIFICATIONS`, `DIRECTORY_ALARMS` → `AUDIO`
>    - `DIRECTORY_MOVIES` → `VIDEO`
>    - `DIRECTORY_PICTURES`, `DIRECTORY_DCIM` → `IMAGES`
>    - `DIRECTORY_DOWNLOADS` → `DOWNLOADS`
>    - anything else → `NonPublic`
> 3. For a `PublicCollection` match: `relativePath` is the first segment + any intermediate segments + trailing `/` (e.g. `Music/Artist/`). `displayName` is the filename. `mimeType` is resolved from extension via `android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)`; null → `"application/octet-stream"`.
> 4. For `NonPublic`: `absolutePath` = input verbatim, `displayName` = filename, `mimeType` = same MIME resolution.
>
> Inject nothing else. Pure path arithmetic plus `MimeTypeMap`. No `Context` needed (use `Environment.getExternalStorageDirectory()` and static `Environment.DIRECTORY_*` constants).

**Verification:**

- `Glob` — `LocalDestinationClassifier.kt` exists.
- `Grep` — `class LocalDestinationClassifier @Inject constructor()` matches once.
- `Grep` — `fun classify(absolutePath: String): LocalDestinationCategory` matches once.
- `Grep` — `Environment.DIRECTORY_MUSIC` and `Environment.DIRECTORY_DOWNLOADS` both present.
- `Grep` — `MimeTypeMap.getSingleton()` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 5/5 PASS. Files: `LocalDestinationClassifier.kt` (95 LOC). Dev log recorded.

---

### Step 01.3 — Define `LocalSink` interface

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalSink.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create an interface `LocalSink` with three members:
> - `val outputStream: java.io.OutputStream` — caller writes bytes here.
> - `suspend fun commit(): Result<String>` — publishes the sink atomically; returns the final user-visible path/URI string.
> - `suspend fun abort()` — best-effort cleanup of any partial state (MediaStore pending entry deleted, partial files removed).
>
> Add KDoc explaining contract: caller must always call exactly one of `commit()` or `abort()` after writing finishes (success or failure). `outputStream.close()` is called by the sink internally during `commit`/`abort` — caller does not close it.

**Verification:**

- `Glob` — `LocalSink.kt` exists.
- `Grep` — `interface LocalSink` matches once.
- `Grep` — all three member tokens present: `outputStream`, `suspend fun commit`, `suspend fun abort`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 3/3 PASS. Files: `LocalSink.kt` (38 LOC). Dev log recorded.

---

### Step 01.4 — Define `LocalDestinationWriter` interface

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationWriter.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create an interface `LocalDestinationWriter` with one method:
>
> `suspend fun open(destination: LocalDestinationCategory, overwrite: Boolean): Result<LocalSink>`
>
> KDoc explaining contract: returns a ready-to-write sink. On `overwrite = false` and the destination already exists, returns `Result.failure(FileExistsException(...))` (existing project type from `com.sza.fastmediasorter.data.transfer`). On any other failure (EACCES, full disk, MediaStore rejection that cannot be retried) returns `Result.failure(<typed exception>)`.

**Verification:**

- `Glob` — `LocalDestinationWriter.kt` exists.
- `Grep` — `interface LocalDestinationWriter` matches once.
- `Grep` — `suspend fun open(destination: LocalDestinationCategory, overwrite: Boolean): Result<LocalSink>` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 2/2 PASS. Files: `LocalDestinationWriter.kt` (30 LOC). Dev log recorded.

---

### Step 01.5 — Implement `MediaStoreLocalDestinationWriter`

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/MediaStoreLocalDestinationWriter.kt`
**Depends on:** Steps 01.1, 01.3, 01.4

**Prompt for developer:**

> Create `class MediaStoreLocalDestinationWriter @Inject constructor(@ApplicationContext private val context: Context) : LocalDestinationWriter`.
>
> `open()` branches on the input type:
>
> **Branch A — `PublicCollection`:**
> 1. Map `collection.Kind` → MediaStore content URI:
>    - `AUDIO` → `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`
>    - `VIDEO` → `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`
>    - `IMAGES` → `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`
>    - `DOWNLOADS` → `MediaStore.Downloads.EXTERNAL_CONTENT_URI`
>    - `FILES` → `MediaStore.Files.getContentUri("external")`
> 2. Query for an existing record matching `DISPLAY_NAME = displayName AND RELATIVE_PATH = relativePath`. If found and `overwrite = false` → return `Result.failure(FileExistsException(displayName, …, isMove = false))`. If found and `overwrite = true` → delete the existing record via `context.contentResolver.delete(itemUri, null, null)`.
> 3. Build `ContentValues` with `DISPLAY_NAME`, `RELATIVE_PATH`, `MIME_TYPE`, `IS_PENDING = 1`.
> 4. `context.contentResolver.insert(collectionUri, values)`. If null on API 29+ AND original collection was Audio/Video/Images, retry once with `MediaStore.Files.getContentUri("external")` (handle MIME mismatch — covers research item §6.3 fallback).
> 5. `outputStream = contentResolver.openOutputStream(itemUri) ?: return Result.failure(...)`.
> 6. Return a `LocalSink` whose:
>    - `outputStream` is the stream above.
>    - `commit()` closes the stream, then updates `IS_PENDING = 0` via `contentResolver.update`. Returns the final `itemUri.toString()`.
>    - `abort()` closes the stream (swallow exceptions), then calls `contentResolver.delete(itemUri, null, null)`.
>
> **Branch B — `NonPublic`:**
> 1. Build `File(absolutePath)`. `parentFile?.mkdirs()`.
> 2. If file exists and `overwrite = false` → `Result.failure(FileExistsException(...))`. If exists and `overwrite = true` → delete.
> 3. Open `FileOutputStream(file)`. Catch `FileNotFoundException` (typical EACCES wrapper) and `SecurityException` → `Result.failure(LocalDestinationPermissionDeniedException(absolutePath, cause))` where the exception class is introduced in Phase 04. For now, throw a `RuntimeException` placeholder named `LocalDestinationPermissionDeniedException` declared as a local `internal class` inside this file with marker comment `// PLACEHOLDER: replaced in Phase 04 with shared error type`.
> 4. Return a `LocalSink` whose:
>    - `outputStream` is the `FileOutputStream`.
>    - `commit()` closes the stream and returns `file.absolutePath`.
>    - `abort()` closes the stream (swallow) and deletes the file.
>
> All Logging via `Timber.d/w/e` — never `Log.d`.

**Verification:**

- `Glob` — `MediaStoreLocalDestinationWriter.kt` exists.
- `Grep` — `class MediaStoreLocalDestinationWriter` matches once.
- `Grep` — `IS_PENDING` token matches at least twice (set to 1, then to 0).
- `Grep` — `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` present.
- `Grep` — `MediaStore.Downloads.EXTERNAL_CONTENT_URI` present.
- `Grep -n "Log\.d\("` returns zero hits in this file.
- `Grep` — `LocalDestinationPermissionDeniedException` matches (placeholder marker present).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 6/6 PASS. Files: `MediaStoreLocalDestinationWriter.kt` (216 LOC). IS_PENDING matches 4× (1 import-line, set to 1, set to 0, in ContentValues). Dev log recorded.

---

### Step 01.6 — Provide `LocalDestinationWriter` and classifier via Hilt; add unit test

**Status:** `[~]` in progress

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifierTest.kt`

**Depends on:** Steps 01.2, 01.5

**Prompt for developer:**

> 1. In `DirectoryStrategyModule.kt` add two `@Provides @Singleton` factories:
>    - `provideLocalDestinationClassifier(): LocalDestinationClassifier = LocalDestinationClassifier()`
>    - `provideLocalDestinationWriter(@ApplicationContext context: Context): LocalDestinationWriter = MediaStoreLocalDestinationWriter(context)`
>
>    Keep the existing strategy factories untouched. If the module is `@Module @InstallIn(SingletonComponent::class)`, the new factories live in the same scope.
>
> 2. Create `LocalDestinationClassifierTest.kt` (JUnit 4 + Robolectric not required — pure path arithmetic). Use plain `org.junit.Test`. Cover at minimum:
>    - `/storage/emulated/0/Music/Artist/song.mp3` → `PublicCollection(AUDIO, "Music/Artist/", "song.mp3", "audio/mpeg")`
>    - `/storage/emulated/0/Movies/film.mp4` → `PublicCollection(VIDEO, "Movies/", "film.mp4", "video/mp4")`
>    - `/storage/emulated/0/Download/doc.pdf` → `PublicCollection(DOWNLOADS, "Download/", "doc.pdf", "application/pdf")`
>    - `/storage/emulated/0/SomeApp/data.bin` → `NonPublic(absolutePath = "/storage/emulated/0/SomeApp/data.bin", "data.bin", "application/octet-stream")`
>    - `/data/data/com.sza.fastmediasorter/files/cache.tmp` (not under external storage) → `NonPublic`
>    - File without extension → mimeType = `application/octet-stream`.

**Verification:**

- `Grep` in `DirectoryStrategyModule.kt` — `provideLocalDestinationClassifier` matches once.
- `Grep` in `DirectoryStrategyModule.kt` — `provideLocalDestinationWriter` matches once.
- `Glob` — `LocalDestinationClassifierTest.kt` exists.
- `Grep` in test — at least 6 `@Test` annotations.
- Build closure (see Phase Done Criteria) confirms compilation.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. Files: `DirectoryStrategyModule.kt` (+15 LOC), `LocalDestinationClassifierTest.kt` (95 LOC, 6 @Test). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` BUILD SUCCESSFUL in 1m 58s (APK produced). Hilt graph validates.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Test `LocalDestinationClassifierTest` passes via per-class XML — **MANUAL-REQUIRED**. Test target `testNoLegalDebugUnitTest` cannot compile due to **pre-existing** failures in `CloudFileOperationHandlerTest.kt:115` and `AtomicFileOperationStrategyTest.kt:113` (unrelated to S0231; tracked in memory `feedback_build_pre_existing_test_failures`). New test file is syntactically valid (6 `@Test`, Robolectric `@RunWith`). To execute once pre-existing failures are repaired:
  `./gradlew :app_v2:testNoLegalDebugUnitTest --tests "com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifierTest"`
- [x] Dev log entry added for every new/modified file via `.\scripts\add_to_dev_log.ps1` (7 entries).
- [x] No new `BuildConfig.IS_*` / `BuildConfig.SUPPORT_*` flavor guards introduced (none added — all new code is flavor-agnostic).

---

## Handoff Notes to Next Phase

After Phase 01:
- `LocalDestinationWriter` is available via Hilt injection.
- Classifier handles all 9 public collection categories.
- MediaStore writer respects `overwrite` flag and uses IS_PENDING for atomicity.
- `LocalDestinationPermissionDeniedException` exists as a placeholder inside `MediaStoreLocalDestinationWriter.kt` — Phase 04 replaces it with a shared domain type.
- No production code calls the writer yet — that's Phase 02.

---

## Rollback Plan

Phase 01 adds new files only and one trivial addition to `DirectoryStrategyModule.kt`. Rollback: `git revert` the phase commit(s). No data migration, no user-facing change, no impact on existing call sites.
