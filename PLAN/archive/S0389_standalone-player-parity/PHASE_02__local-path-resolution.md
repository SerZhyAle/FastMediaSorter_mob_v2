# Phase 02 - Local Path Resolution

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Introduce a single off-main-thread role that derives an absolute local file path (and its parent folder) from an external `content://`/`file://` URI, returning null when no real path is resolvable. Both folder paging (Phase 03) and the Open-in-FMS resolver (Phase 05) consume it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.2 (source enumerability) reviewed - this phase defines the resolution boundary.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveLocalPathFromUriUseCase.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveLocalPathFromUriUseCaseTest.kt` | New | ≤ 180 |

---

## Steps

### Step 02.1 - Add ResolveLocalPathFromUriUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveLocalPathFromUriUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ResolveLocalPathFromUriUseCase`. Input: an external `Uri`. Output: a result carrying absolute file path + parent folder path, or a clear "not local" outcome. Resolve `file://` directly; for `content://` attempt `MediaStore` `DATA` column and `DocumentsContract` path derivation; return the not-local outcome when neither yields a readable filesystem path (network/SAF-only). Run resolution on a background dispatcher. No fabricated paths - if the file is not on the local filesystem, return not-local. Inject via constructor; no Android `Log` calls, Timber only if logging at all (info level, no `Sxxxx`).

**Verification:**

- `Glob` - `ResolveLocalPathFromUriUseCase.kt` exists.
- `Grep` - `class ResolveLocalPathFromUriUseCase` matches exactly once (declaration).
- `Grep` - an `operator fun invoke(` or `suspend fun` with a `Uri` parameter is present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 4/4 PASS. Added `ResolveLocalPathFromUriUseCase` + `LocalPathResolution` (file/content/SAF-primary resolution, off Dispatchers.IO). Files: ResolveLocalPathFromUriUseCase.kt (+90 LOC). Dev log recorded.

---

### Step 02.2 - Unit-test the resolver outcomes

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveLocalPathFromUriUseCaseTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add unit tests covering: a `file://` URI resolves to path + parent; a `content://` URI without a resolvable path returns the not-local outcome. Mock the content resolver. Assert the parent-folder value used by downstream phases.

**Verification:**

- `Glob` - `ResolveLocalPathFromUriUseCaseTest.kt` exists.
- `Grep` - `class ResolveLocalPathFromUriUseCaseTest` matches once.
- Run `./gradlew.bat testStandardDebugUnitTest --tests "*ResolveLocalPathFromUriUseCaseTest*"` - per-class XML report shows all passing.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 3/3 PASS. XML report: tests=2 failures=0 errors=0. Note: had to temporarily quarantine pre-existing broken `TranslationLanguageCodeMapperTest.kt` (S0386 ML Kit moved to `translate_feature`, unrelated) to compile the test source set; restored after the run. Files: ResolveLocalPathFromUriUseCaseTest.kt (+58 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case).

---

## Handoff Notes to Next Phase

`ResolveLocalPathFromUriUseCase` is the single source of truth for "is this file local, and where". Phase 03 gates folder paging on a non-null parent folder; Phase 05 feeds the parent folder into the resource resolver chain. Non-local files deterministically short-circuit both features to their fallbacks.

---

## Rollback Plan

Revert phase commit(s) - new isolated use case + test, no call sites yet, no user-facing surface.
