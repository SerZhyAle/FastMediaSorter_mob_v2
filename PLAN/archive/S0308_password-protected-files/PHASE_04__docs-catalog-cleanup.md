# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0308_password-protected-files.md`](../S0308_password-protected-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** final verification
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Finish documentation, catalog sync, and validation for S0308.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CATALOG/app_v2.md` | Regenerated | - |
| `PLAN/S0308_password-protected-files.md` | Modified | - |
| `PLAN/S0308_password-protected-files/INDEX.md` | Modified | - |

---

## Steps

### Step 04.1 - Update feature docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 03 done

**Prompt for developer:**

> Add one concise user-facing bullet to the archive/document areas describing password-protected ZIP support and unsupported-protection fallback for documents. Keep EN/RU/UK mirrors aligned and avoid overclaiming PDF/Office/EPUB password opening.

**Verification:**

- `Grep` - `password-protected` exists in `docs/FEATURES.md`.
- `Grep` - `паролем` exists in `docs/FEATURES_RU.md`.
- `Grep` - `паролем` exists in `docs/FEATURES_UK.md`.

**Evidence:**

- `grep_search`: expected `password-protected` in `docs/FEATURES.md` | actual present.
- `grep_search`: expected `паролем` in `docs/FEATURES_RU.md` | actual present.
- `grep_search`: expected `паролем` in `docs/FEATURES_UK.md` | actual present.
- `grep_search`: expected `zip4j` in `docs/TECH_STACK.md` and `dev/TECH_REQUIREMENTS.md` | actual present.

**Status:** `[x]` done

---

### Step 04.2 - Refresh catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the catalog sync wrapper for `app_v2` and keep generated JSONL/Markdown in sync.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `ExtractArchiveUseCase` exists in `dev/CATALOG/app_v2.md`.

**Evidence:**

- `Command`: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` equivalent wrapper call exited 0.
- `grep_search`: expected `ExtractArchiveUseCase` exists in `dev/CATALOG/app_v2.md` | actual present.

**Status:** `[x]` done

---

### Step 04.3 - Run validation

**Files:** no source edits expected
**Depends on:** Step 04.2

**Prompt for developer:**

> Run focused unit tests for archive extraction and the standard debug build through the project build prompt/script route. Capture command exit codes in the step log.

**Verification:**

- `Command` - archive extraction unit tests exit 0.
- `Command` - standard debug build exits 0.

**Evidence:**

- `gradlew`: `JAVA_HOME=C:\Program Files\Java\jdk-17; .\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.usecase.ExtractArchiveUseCaseTest"` exit 0.
- `gradlew`: `JAVA_HOME=C:\Program Files\Java\jdk-17; .\gradlew.bat assembleStandardDebug '-Pchaquopy.enabled=false' --configuration-cache` exit 0.
- First build attempt failed only because PowerShell parsed unquoted `-Pchaquopy.enabled=false` as a Gradle task; rerun with quoting passed.

**Status:** `[x]` done

---

### Step 04.4 - Close tactical metadata

**Files:** `PLAN/S0308_password-protected-files.md`, `PLAN/S0308_password-protected-files/INDEX.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Mark all phases done, update the tactical index completion gate, and prepare S0308 for `/spec-check`. Do not set strategic status to Verified.

**Verification:**

- `Grep` - `**Status:** Tactical` exists in `PLAN/S0308_password-protected-files.md` before `/spec-dev` final status transition.
- `Grep` - `Status: Done` exists in `PLAN/S0308_password-protected-files/INDEX.md`.

**Evidence:**

- `grep_search`: expected `**Status:** Implemented` in strategic spec | actual present after this update.
- `grep_search`: expected `**Status:** Done` in `INDEX.md` | actual present after this update.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - standard debug build exits 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commits. Documentation and generated catalog can be regenerated from source state.