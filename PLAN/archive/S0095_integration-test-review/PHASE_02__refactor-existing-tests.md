# Phase 02 — refactor-existing-tests

**Strategic spec:** [`../S0095_integration-test-review.md`](../S0095_integration-test-review.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Replace every hardcoded test-resource string in the two existing androidTest files with references to `TestFixtures`, leaving test logic unchanged.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`TestFixtures.kt` exists and compiles).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/FileOperationsInstrumentationTest.kt` | Modified | ≤ 200 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/settings/DefaultCredentialsInputTest.kt` | Modified | ≤ 100 |

> Both files are under 500 lines — no backup step required.

---

## Steps

### Step 2.1 — Refactor FileOperationsInstrumentationTest

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/FileOperationsInstrumentationTest.kt`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> In `FileOperationsInstrumentationTest`, replace the private `createTestFile` helper with a call to `TestFixtures.createTempFile(testDir, name, content)`. Import `com.sza.fastmediasorter.TestFixtures`. The inline content strings `"Original content"`, `"Move this"`, `"Delete this"`, `"New content"`, `"Old content"`, `"Content 1"`, `"Content 2"`, `"Content 3"` and all plain filename literals like `"source.txt"`, `"moveme.txt"` may remain — they describe local test logic, not device-specific resources. Remove the private `createTestFile` function from the class. All six existing tests must still compile and pass.

**Verification:**

- `Grep` — `TestFixtures.createTempFile` present at least once in `FileOperationsInstrumentationTest.kt`.
- `Grep` — `private fun createTestFile` is **absent** from `FileOperationsInstrumentationTest.kt`.
- `Grep` — `import com.sza.fastmediasorter.TestFixtures` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: FileOperationsInstrumentationTest.kt (modified). Dev log recorded.

---

### Step 2.2 — Refactor DefaultCredentialsInputTest

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/settings/DefaultCredentialsInputTest.kt`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> In `DefaultCredentialsInputTest`, replace the hardcoded string `"s0090-user"` (used in `typeText(...)` and `withText(...)` assertions) with `TestFixtures.DEFAULT_USER`. Import `com.sza.fastmediasorter.TestFixtures`. Remove the `DEFAULT_USER_ACCEPTS_INLINE_TEXT_INPUT` companion constant — it no longer serves as the test data source; if it is used only as a non-empty guard, replace the `check(...)` call with `check(TestFixtures.DEFAULT_USER.isNotEmpty())`. The existing single test must still compile and pass.

**Verification:**

- `Grep` — `"s0090-user"` is **absent** from `DefaultCredentialsInputTest.kt`.
- `Grep` — `TestFixtures.DEFAULT_USER` present at least twice (typeText + withText) in `DefaultCredentialsInputTest.kt`.
- `Grep` — `import com.sza.fastmediasorter.TestFixtures` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: DefaultCredentialsInputTest.kt (modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Both existing androidTest files now consume `TestFixtures`. Phases 03–05 can each add new test classes following the same pattern without touching these files again.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
