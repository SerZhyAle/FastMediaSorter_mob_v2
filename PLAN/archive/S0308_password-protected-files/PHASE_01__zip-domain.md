# Phase 01 - ZIP Domain Support

**Strategic spec:** [`../S0308_password-protected-files.md`](../S0308_password-protected-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Add password-aware ZIP extraction support while preserving existing safety guards.

---

## Prerequisites

- [ ] Working tree is clean or only contains S0308 changes.
- [ ] Back up `app_v2/build.gradle.kts` to `temp/` before editing because the file is larger than 500 lines.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | existing >500 - backup required |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt` | Modified | ≤ 420 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCaseTest.kt` | Modified | ≤ 260 |

---

## Steps

### Step 01.1 - Add ZIP password dependency

**Files:** `app_v2/build.gradle.kts`
**Depends on:** start of phase

**Prompt for developer:**

> Add a single permissive ZIP library dependency for encrypted ZIP handling. Keep the dependency near the existing document/archive support block. Do not add PDF or Office engines in this phase.

**Verification:**

- `Grep` - `net.lingala.zip4j:zip4j` matches exactly once in `app_v2/build.gradle.kts`.
- `Grep` - no `implementation(".*(pdfbox|mupdf|itext|poi-ooxml)` dependency is added in `app_v2/build.gradle.kts`.

**Evidence:**

- `grep_search`: expected `net.lingala.zip4j:zip4j` count 1 | actual 1.
- `grep_search`: expected prohibited PDF/Office dependency count 0 | actual 0. Existing non-dependency comment mentioning PDFBox is ignored by the dependency-scoped predicate.

**Status:** `[x]` done

---

### Step 01.2 - Make extraction password-aware

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend ZIP extraction with non-stored password input. Add explicit preflight helpers for encrypted archives and wrong passwords so Browse can ask before creating the target directory. Preserve path traversal, max-entry, max-depth, max-uncompressed-size, no-space, charset fallback for non-encrypted ZIPs, cancellation, and `content://` input support. Never log password values.

**Verification:**

- `Grep` - `fun isPasswordRequired` exists in `ExtractArchiveUseCase.kt`.
- `Grep` - `password_invalid` exists in `ExtractArchiveUseCase.kt`.
- `Grep` - `password_required` exists in `ExtractArchiveUseCase.kt`.
- `Grep` - `MAX_UNCOMPRESSED_SIZE` still exists in `ExtractArchiveUseCase.kt`.
- `Grep` - `sanitizeEntryPath` still exists in `ExtractArchiveUseCase.kt`.
- `Grep` - `Log.d(` returns zero hits in `ExtractArchiveUseCase.kt`.

**Evidence:**

- `grep_search`: expected `fun isPasswordRequired` present | actual present.
- `grep_search`: expected `password_required` present | actual present.
- `grep_search`: expected `password_invalid` present | actual present.
- `grep_search`: expected `MAX_UNCOMPRESSED_SIZE` present | actual present.
- `grep_search`: expected `sanitizeEntryPath` present | actual present.
- `grep_search`: expected `Log.d(` count 0 | actual 0.

**Status:** `[x]` done

---

### Step 01.3 - Cover encrypted ZIP extraction

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCaseTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add JVM tests that create encrypted ZIP fixtures through the selected ZIP library. Cover correct password success, missing password `password_required`, wrong password `password_invalid`, and path traversal still skipped after encrypted extraction.

**Verification:**

- `Grep` - `password_required` exists in `ExtractArchiveUseCaseTest.kt`.
- `Grep` - `password_invalid` exists in `ExtractArchiveUseCaseTest.kt`.
- `Grep` - `createEncryptedZip` exists in `ExtractArchiveUseCaseTest.kt`.

**Evidence:**

- `grep_search`: expected `password_required` present | actual present.
- `grep_search`: expected `password_invalid` present | actual present.
- `grep_search`: expected `createEncryptedZip` present | actual present.
- `gradlew`: `JAVA_HOME=C:\Program Files\Java\jdk-17; .\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.usecase.ExtractArchiveUseCaseTest"` exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - targeted unit test command compiled `standardDebug` and exited 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.

---

## Handoff Notes to Next Phase

Phase 02 consumes the explicit archive password preflight and failure codes.

---

## Rollback Plan

Revert phase commits and remove the ZIP library dependency; no schema or persistent data migration is involved.