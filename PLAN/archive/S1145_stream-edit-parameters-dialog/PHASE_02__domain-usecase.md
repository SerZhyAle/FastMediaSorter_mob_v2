# Phase 02 - Domain use case (explicit type + duplicate guard)

**Strategic spec:** [`../S1145_stream-edit-parameters-dialog.md`](../S1145_stream-edit-parameters-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Extend `UpdateStreamSourceUseCase` so an edit can carry an explicit media-kind override (null = auto-derive as today) and rejects a URL that collides with another stream instead of letting the unique-index write crash; cover the new logic with a real-Room unit test. No repository/DAO/schema change - reuses `getByUrl` and `updateUserFields`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCase.kt` | Modified | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCaseTest.kt` | New | ≤ 220 |

---

## Steps

### Step 02.1 - Explicit media-kind override + duplicate-URL guard

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `invoke` to `suspend operator fun invoke(source: StreamSourceEntity, url: String, title: String?, mediaKindOverride: String? = null): UpdateResult`. Keep the existing `NotEditable` (non-MANUAL) and `InvalidUrl` (unsupported scheme) guards first. Then, before writing, guard against a colliding address: `val existing = repository.getByUrl(trimmedUrl)`; if `existing != null && existing.id != source.id` return the new `UpdateResult.Duplicate`. Resolve the kind as `val resolvedKind = mediaKindOverride ?: classifier.classify(trimmedUrl)` - a null override preserves today's auto-derive (so an unchanged rtsp URL stays RTSP), a non-null override ("AUDIO"/"VIDEO") wins. Pass `resolvedKind` to `repository.updateUserFields(...)`. Add `data object Duplicate : UpdateResult` to the sealed interface. Update the class KDoc's one-liner to note the explicit-kind override and the duplicate guard (WHY: the edit dialog directly drives the unique-`url` write path, so a colliding address must be rejected, not crashed). Do not add a dispatcher or a try/catch on the DAO - the pre-check is the guard and the use case stays main-safe via the repository.

**Verification:**

- `Grep` - `mediaKindOverride: String? = null` in the `invoke` signature.
- `Grep` - `data object Duplicate : UpdateResult` present.
- `Grep` - `existing.id != source.id` present.
- `Grep` - `mediaKindOverride ?: classifier.classify` present.
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-07-22 - Added `mediaKindOverride` param + `UpdateResult.Duplicate` + `getByUrl` id-diff pre-check; kind = override ?: classify. Verification 5/5 PASS. Dev log recorded (catalog scan deferred to Phase 05).

---

### Step 02.2 - Real-Room unit test for the edit use case

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCaseTest.kt` (new)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `UpdateStreamSourceUseCaseTest` mirroring `StreamSourceCatalogMergeTest` (Robolectric + `InMemoryRoomRule`, `@Config(sdk = [34])`, `runTest`). Build the use case as `UpdateStreamSourceUseCase(StreamSourceRepository(dbRule.db, dao), StreamMediaKindClassifier())`. Seed rows via `dao.upsert(...)`. Cover:
> - explicit override honored: edit a MANUAL row with `mediaKindOverride = "AUDIO"` on a `.m3u8` URL (which auto-classifies VIDEO) -> result `Success`, persisted `mediaKind == "AUDIO"`.
> - auto derive: same row, `mediaKindOverride = null`, `.m3u8` URL -> persisted `mediaKind == "VIDEO"`; an `rtsp://` URL with null override -> persisted `"RTSP"`.
> - duplicate rejected: seed a second row (any origin) with url `B`; edit the MANUAL row's url to `B` -> result `Duplicate`, the MANUAL row's url unchanged in the DB.
> - same-url edit allowed: edit the MANUAL row keeping its own url but changing the title -> result `Success` (not `Duplicate`), title persisted.
> - `NotEditable`: a `sourceOrigin = "CATALOG"` row -> result `NotEditable`, row untouched.
> - `InvalidUrl`: a `file://` (unsupported scheme) url -> result `InvalidUrl`, row untouched.
> Use `assertEquals`/`assertTrue` from JUnit; read back via `dao.getById(...)`.

**Verification:**

- `Glob` - `UpdateStreamSourceUseCaseTest.kt` exists under `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/`.
- `Grep` - `class UpdateStreamSourceUseCaseTest` matches exactly once.
- `Grep` - `UpdateStreamSourceUseCase.UpdateResult.Duplicate` referenced in the test.
- `.\a.ps1 fu` (or `.\gradlew.bat testStandardDebugUnitTest --tests "*UpdateStreamSourceUseCaseTest*"`) - the new test class passes.

**Status:** `[x]` done

**Step Log:**

- 2026-07-22 - New real-Room test (6 cases: override honored, auto re-derive incl. rtsp->RTSP, duplicate rejected + row unchanged, same-url title edit, CATALOG NotEditable, unsupported-scheme InvalidUrl). `check-standard-fast.ps1 -Mode Unit -Tests *UpdateStreamSourceUseCaseTest*` BUILD SUCCESSFUL. Verification 4/4 PASS.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - unit-test build (`testStandardDebugUnitTest`) BUILD SUCCESSFUL, which compiles main + test.
- [x] New unit test passes (`--tests "*UpdateStreamSourceUseCaseTest*"`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to Phase 05 single scan (API still changes in Phase 03; one catalog_sync per ticket per CLAUDE.md §12).
- [x] Phase-boundary audit - no P0/P1 (Layer 1 clean; Layer 2 main-safe via repository; Layer 4 no schema change; the duplicate pre-check closes the P0 crash path).

---

## Handoff Notes to Next Phase

`UpdateStreamSourceUseCase.invoke` now takes a nullable `mediaKindOverride` and can return `Duplicate`. Phase 03 threads the override from the ViewModel and maps `Duplicate` to the new error string.

---

## Rollback Plan

Revert the use-case edit (restore the 3-arg `invoke` and drop `Duplicate`) and delete the new test file - no schema or data change.
