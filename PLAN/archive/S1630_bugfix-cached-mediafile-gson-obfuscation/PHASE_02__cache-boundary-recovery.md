# Phase 02 - Cache Boundary Recovery

**Strategic spec:** [`../S1630_bugfix-cached-mediafile-gson-obfuscation.md`](../S1630_bugfix-cached-mediafile-gson-obfuscation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Reject an incomplete cached payload at the persistence boundary and prove the affected statistics path receives a normal cache miss instead of an invalid object.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt` | Modified | ≤ 80 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/CachedFileListRepositoryTest.kt` | Modified | ≤ 100 |

---

## Steps

### Step 02.1 - Invalidate incomplete cached payloads

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Validate every deserialized cached media file before exposing the list. If a payload is malformed or lacks a required value, delete that resource's snapshot, return null as a cache miss, and keep the failure contained at the repository boundary. Apply the same safe deserialization path to cache patch operations where an invalid list would otherwise be re-saved.

**Why:**

An incompatible cached blob is derived data and must be discarded before a later consumer can dereference a null required property and crash the resource screen.

**Verification:**

- `Grep` - `getCachedFiles` validates decoded entries before returning them.
- `Grep` - invalid payload handling calls the DAO deletion for the affected resource.
- `Grep` - no `Log.d(` exists in the modified Kotlin file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Verified invalid snapshots are discarded before reads or patch operations expose their entries.
- 2026-08-14 - Verified cache-read validation, invalidation, and cancellation-safe narrow exception handling.
- 2026-08-14 - All three Grep predicates confirmed against the live file; no `Log.d(` present.

---

### Step 02.2 - Cover malformed successful deserialization

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/CachedFileListRepositoryTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add JVM tests for a syntactically valid compressed JSON list that omits a required media-file field. Assert that the repository returns a cache miss and deletes the affected snapshot. Keep the existing valid round-trip and corrupt-byte tests intact.

**Why:**

The release crash occurs after successful Gson parsing, so a corrupt-byte test alone cannot prove the new protection catches the mapping-mismatch shape.

**Verification:**

- `Grep` - a test fixture contains JSON with an omitted required media-file field.
- `Grep` - the test asserts both null result and `deleteByResourceId` invocation.
- `Grep` - existing valid round-trip test remains present.
- Targeted standard release R8 task exits 0 and its mapping or seeds output retains the model's persisted field names.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Added a GZIP fixture whose entry omits `path`: Gson parses it, the required value stays unset. Read path returns null and calls `deleteByResourceId`; the patch path additionally proves the snapshot is never re-saved.
- 2026-08-14 - `check-standard-fast.ps1 -Mode Unit -Tests "*CachedFileListRepositoryTest*"` exit 0; `tests="13" skipped="0" failures="0" errors="0"`.
- 2026-08-14 - `:app_v2:minifyStandardReleaseWithR8` exit 0; `seeds.txt` retains `com.sza.fastmediasorter.domain.model.MediaFile: java.lang.String path` and every other persisted field under its source name.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1` - written by the Phase 03 scoped closure.
- [x] `CachedFileListRepositoryTest` passes and targeted standard release R8 evidence is recorded.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The cache boundary now treats incompatible snapshots as misses. Final validation must prove the narrow keep rule on the minified standard release path.

---

## Rollback Plan

Revert phase commit(s); cached data is disposable and will be rebuilt by a later scan.
