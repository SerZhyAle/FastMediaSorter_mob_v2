# Phase 01 — Foundations

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 7 / 7
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

Introduce mutation domain model, single PathNormalizer, in-memory MutationJournal, and Hilt wiring. No callers yet — Player and Browse continue to use legacy `EXTRA_MODIFIED_FILES` path until Phase 02/03.

---

## Prerequisites

- [ ] Working tree clean or on feature branch (`DEBUG-v004` per current session).
- [ ] Strategic §6 items 1–5 are Resolved (see INDEX Pre-Implementation Blockers).
- [ ] `assembleStandardDebug` baseline passes (sanity check via `.\a.ps1 dq`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/Mutation.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/MutationJournal.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/mutation/InMemoryMutationJournal.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/path/PathNormalizer.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/path/CanonicalPathNormalizer.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/MutationJournalModule.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/PathNormalizerModule.kt` | New | ≤ 40 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/mutation/InMemoryMutationJournalTest.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/path/CanonicalPathNormalizerTest.kt` | New | ≤ 200 |

All paths are in `src/main/java/` and `src/test/java/` — no flavor source sets. The bug is reproducible across all flavors and the fix is shared (§3.2 strategic spec).

---

## Steps

### Step 01.1 — Create `Mutation` sealed class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/Mutation.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a `sealed class Mutation` in package `com.sza.fastmediasorter.domain.mutation` with four `data class` variants:
> - `Delete(val resourceId: Long, val canonicalPath: String, val opId: String, val timestampMs: Long)`
> - `Move(val resourceId: Long, val oldCanonicalPath: String, val newCanonicalPath: String, val opId: String, val timestampMs: Long)` — `newCanonicalPath` may point to a different `resourceId` if the move target is another resource; in that case carry both `srcResourceId` (alias of `resourceId`) and `dstResourceId: Long` as separate fields.
> - `Rename(val resourceId: Long, val oldCanonicalPath: String, val newCanonicalPath: String, val opId: String, val timestampMs: Long)` — same resource, name change only.
> - `BatchDelete(val resourceId: Long, val canonicalPaths: List<String>, val opId: String, val timestampMs: Long)` — `MediaStore.createDeleteRequest` returns a single result for a list of files.
>
> Add a sealed `val opId: String` and `val timestampMs: Long` accessor (declare via abstract `val` on the sealed class). `opId` is a UUID generated at creation time (caller-side), used by Reconciler to mark applied. `timestampMs` is `System.currentTimeMillis()` at the moment of the successful operation.
>
> No Hilt annotations. No serialization. Plain Kotlin data classes — journal is in-memory only (§6 Item 4 resolution).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/Mutation.kt` exists.
- `Grep` — `sealed class Mutation` matches once.
- `Grep` — `data class Delete` matches once.
- `Grep` — `data class Move` matches once.
- `Grep` — `data class Rename` matches once.
- `Grep` — `data class BatchDelete` matches once.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 7/7 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/Mutation.kt` (+58 LOC). Dev log recorded.

---

### Step 01.2 — Create `MutationJournal` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/MutationJournal.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `interface MutationJournal` in `com.sza.fastmediasorter.domain.mutation`. Methods:
>
> ```kotlin
> fun record(mutation: Mutation)
> fun pendingFor(resourceId: Long, sinceAppliedSeq: Long): List<MutationEntry>
> fun markApplied(resourceId: Long, opIds: Collection<String>)
> fun lastAppliedSeq(resourceId: Long): Long
> fun clearResource(resourceId: Long)
> ```
>
> Add `data class MutationEntry(val seq: Long, val mutation: Mutation, val applied: Boolean)` in the same file (or as a top-level type).
>
> `seq` is a monotonically-increasing per-resource counter assigned on `record()`. `pendingFor` returns entries with `seq > sinceAppliedSeq` and `applied == false`, ordered by `seq` ascending. `markApplied` flips `applied = true` for the given `opIds` and updates `lastAppliedSeq` to the max seq among applied entries.
>
> No implementation here — pure interface. No imports beyond `Mutation`.

**Verification:**

- `Glob` — `MutationJournal.kt` exists.
- `Grep` — `interface MutationJournal` matches once.
- `Grep` — `fun record(mutation: Mutation)` matches once.
- `Grep` — `fun pendingFor(resourceId: Long, sinceAppliedSeq: Long): List<MutationEntry>` matches once.
- `Grep` — `data class MutationEntry` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 5/5 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/mutation/MutationJournal.kt` (+45 LOC). Dev log recorded.

---

### Step 01.3 — Create `InMemoryMutationJournal` implementation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/mutation/InMemoryMutationJournal.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `class InMemoryMutationJournal @Inject constructor()` in package `com.sza.fastmediasorter.data.mutation` implementing `MutationJournal`. Annotate with `@Singleton`. Internal storage:
>
> ```kotlin
> private val byResource = ConcurrentHashMap<Long, ResourceJournal>()
>
> private class ResourceJournal {
>     val entries = mutableListOf<MutationEntry>()   // append-only
>     var seqCounter = 0L
>     var lastAppliedSeq = 0L
> }
> ```
>
> Use `synchronized(resourceJournalInstance)` for `record / pendingFor / markApplied` to keep ops atomic. `clearResource` removes the resource entry (used for pull-to-refresh — drops pending and resets seq).
>
> Logging: `Timber.d("MutationJournal: record %s seq=%d resource=%d", mutation::class.simpleName, seq, resourceId)` on `record`; `Timber.d("MutationJournal: pending %d entries for resource=%d sinceSeq=%d", n, rid, since)` on `pendingFor`. No `Log.d` calls.
>
> Bounded memory: cap entries-per-resource at 512; if exceeded, drop the oldest already-applied entries first; never drop pending. Surface a `Timber.w` on cap hit.

**Verification:**

- `Glob` — `InMemoryMutationJournal.kt` exists.
- `Grep` — `class InMemoryMutationJournal @Inject constructor()` matches once.
- `Grep` — `@Singleton` present at file top (before the class).
- `Grep` — `: MutationJournal {` matches once.
- `Grep` — `ConcurrentHashMap` import present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 6/6 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/mutation/InMemoryMutationJournal.kt` (+124 LOC). Dev log recorded.

---

### Step 01.4 — Create `PathNormalizer` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/path/PathNormalizer.kt`
**Depends on:** — independent of 01.1–01.3

**Prompt for developer:**

> Create `interface PathNormalizer` in `com.sza.fastmediasorter.domain.path`. Single method:
>
> ```kotlin
> fun canonical(rawPath: String, resourceType: ResourceType): String
> ```
>
> Import `com.sza.fastmediasorter.data.local.entity.ResourceType` (or the project's existing enum location — verify via `Grep -rn "enum class ResourceType"` first). Idempotent — `canonical(canonical(x, rt), rt) == canonical(x, rt)` is a Verification contract for `CanonicalPathNormalizer`.

**Verification:**

- `Glob` — `PathNormalizer.kt` exists.
- `Grep` — `interface PathNormalizer` matches once.
- `Grep` — `fun canonical(rawPath: String, resourceType: ResourceType): String` matches once.
- `Grep` — exactly one `import` for `ResourceType`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/path/PathNormalizer.kt` (+18 LOC). ResourceType resolved to `domain.model.ResourceType` (not `data.local.entity` as prompt hint suggested). Dev log recorded.

---

### Step 01.5 — Create `CanonicalPathNormalizer` implementation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/path/CanonicalPathNormalizer.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Create `class CanonicalPathNormalizer @Inject constructor() : PathNormalizer` in package `com.sza.fastmediasorter.core.path`. Annotate `@Singleton`. Implement `canonical` per resource type:
>
> - `LOCAL`: if starts with `content://` → `Uri.parse(rawPath).path ?: rawPath` then strip query/fragment; else `File(rawPath).canonicalPath` (catches `..` and symlinks); trailing slash removed unless root.
> - `SMB`: strip `smb://` scheme, lowercase host, collapse double slashes, URL-decode segments via `Uri.decode`.
> - `SFTP` / `FTP`: same as SMB but with `sftp://` / `ftp://`.
> - `GOOGLE_DRIVE`: strip leading `drive://` if present; for Drive `fileId` is the canonical form — if `rawPath` looks like `gdrive:/<fileId>` or just `<fileId>` (no slash) return the `fileId` as is.
> - `DROPBOX`: lowercase the entire path (Dropbox is case-insensitive), strip trailing slash, ensure leading `/`.
> - `ONE_DRIVE`: strip `onedrive://` prefix; Graph API paths are case-insensitive — lowercase the path component, keep `itemId` (no slash, all chars) as is.
>
> For unknown / null paths return the trimmed input unchanged. Never throw — return `rawPath` on any parse error after `Timber.w("PathNormalizer: failed to canonicalize %s for %s", rawPath, resourceType)`.

**Verification:**

- `Glob` — `CanonicalPathNormalizer.kt` exists.
- `Grep` — `class CanonicalPathNormalizer @Inject constructor() : PathNormalizer` matches once.
- `Grep` — `@Singleton` present.
- `Grep` — `when (resourceType)` matches once.
- `Grep` — branch keywords `LOCAL`, `SMB`, `SFTP`, `FTP`, `GOOGLE_DRIVE`, `DROPBOX`, `ONE_DRIVE` each appear at least once.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 6/6 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/core/path/CanonicalPathNormalizer.kt` (+139 LOC). `CLOUD` branch dispatches by raw-path prefix because project has no per-provider `ResourceType` — provider lives on `MediaResource.cloudProvider`, unavailable at this layer. Dev log recorded.

---

### Step 01.6 — Hilt module: bind journal and normalizer

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/core/di/MutationJournalModule.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/di/PathNormalizerModule.kt`

**Depends on:** Steps 01.3, 01.5

**Prompt for developer:**

> Create two `@Module @InstallIn(SingletonComponent::class)` files in `com.sza.fastmediasorter.core.di`:
>
> 1. `MutationJournalModule` — abstract module with `@Binds @Singleton abstract fun bindMutationJournal(impl: InMemoryMutationJournal): MutationJournal`.
> 2. `PathNormalizerModule` — abstract module with `@Binds @Singleton abstract fun bindPathNormalizer(impl: CanonicalPathNormalizer): PathNormalizer`.
>
> Verify both impls already carry `@Singleton` (steps 01.3, 01.5) — `@Binds` requires the scope on the impl, not on the binding method, but adding `@Singleton` on the binding is idiomatic in this repo (see existing `RepositoryModule.kt` pattern).

**Verification:**

- `Glob` — both new files exist.
- `Grep` — `@Binds` matches once in `MutationJournalModule.kt` and once in `PathNormalizerModule.kt`.
- `Grep` — `@InstallIn(SingletonComponent::class)` matches once in each file.
- Build sanity: `.\a.ps1 dq` exit 0 (Hilt graph compiles with no missing bindings).

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/core/di/MutationJournalModule.kt` (+20 LOC), `app_v2/src/main/java/com/sza/fastmediasorter/core/di/PathNormalizerModule.kt` (+20 LOC). `assembleStandardDebug` BUILD SUCCESSFUL in 47s. Dev log recorded.

---

### Step 01.7 — Unit tests for journal and normalizer

**Files:**
- `app_v2/src/test/java/com/sza/fastmediasorter/data/mutation/InMemoryMutationJournalTest.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/core/path/CanonicalPathNormalizerTest.kt`

**Depends on:** Steps 01.3, 01.5

**Prompt for developer:**

> Cover the following with JUnit 4 + Truth (existing test style in repo — check `app_v2/src/test/java/.../usecase/*Test.kt` for pattern):
>
> **InMemoryMutationJournalTest:**
> - record 3 mutations → `pendingFor(rid, 0)` returns 3 entries in seq order
> - `markApplied(rid, opIds=[e1, e2])` → `pendingFor(rid, 0)` returns 1 entry; `lastAppliedSeq(rid)` = 2
> - `clearResource(rid)` resets to seq 0, `pendingFor` empty
> - cap test: record 600 BatchDelete entries on a single resource → entries.size ≤ 512, all pending preserved
> - concurrency smoke: 10 threads each calling record() 50 times → final entries.size = 500, all unique seq
>
> **CanonicalPathNormalizerTest:**
> - LOCAL: `/sdcard/DCIM/IMG.jpg` == `/sdcard/DCIM/IMG.jpg` (no-op)
> - LOCAL: `content://media/external/images/12345` → path-only form
> - LOCAL: `/sdcard/DCIM/../DCIM/IMG.jpg` == `/sdcard/DCIM/IMG.jpg`
> - SMB: `smb://Server/Share/Folder%20A/file.jpg` and `smb://server/Share/Folder A/file.jpg` produce equal output
> - DROPBOX: `/Photos/IMG.JPG` == `/photos/img.jpg`
> - GOOGLE_DRIVE: `gdrive:/abc123` and `abc123` produce equal output
> - idempotency: for each case, `canonical(canonical(x, rt), rt) == canonical(x, rt)`

**Verification:**

- `Glob` — both test files exist.
- `Grep` — `@Test` matches ≥ 5 times in each file.
- Build sanity: `.\a.ps1 dq` exit 0 (test compilation included in assemble).

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 3/3 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/data/mutation/InMemoryMutationJournalTest.kt` (6 @Test, +145 LOC), `app_v2/src/test/java/com/sza/fastmediasorter/core/path/CanonicalPathNormalizerTest.kt` (7 @Test, +108 LOC). `.\a.ps1 dq` BUILD SUCCESSFUL in 27s. Note: `.\a.ps1 dq` runs `assembleStandardDebug` and does NOT compile `src/test/`; explicit `compileStandardDebugUnitTestKotlin` exposes a PRE-EXISTING `Unresolved reference 'VrTaskTransition'` in `VrTaskTransitionTest.kt` that is unrelated to S0242 (file last touched in commit e7c20d95, before Phase 01). Our two test files compile cleanly past that gate (the Kotlin compiler reports all errors per file; only the unrelated file is flagged). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `.\a.ps1 dq` (quiet debug) exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` + `render.ps1`.

---

## Handoff Notes to Next Phase

After Phase 01: `MutationJournal` is wired in DI but has zero callers. Player still uses `modifiedFiles + EXTRA_MODIFIED_FILES` from `PlayerLifecycleManager`. Browse still uses structural-equality fast-path in `BrowseStateSyncManager`. Phase 02 starts wiring the Player side; Phase 03 wires Browse — they are independent and may proceed in parallel after this phase merges.

---

## Rollback Plan

Revert phase commit(s). Pure additive change — no existing API touched. No data migration, no user-facing surface, no DI changes that affect existing graphs (new modules add bindings; no `@Provides` for already-bound types).
