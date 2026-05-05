# Phase 01 — test-constants-registry

**Strategic spec:** [`../S0095_integration-test-review.md`](../S0095_integration-test-review.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Create the centralized `TestFixtures` object and a `TESTING_PREREQUISITES.md` file that together form the single source of truth for all androidTest constants and device setup requirements.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/androidTest/java/com/sza/fastmediasorter/` directory exists (it does — verified by existing tests).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/TestFixtures.kt` | New | ≤ 120 |
| `app_v2/src/androidTest/TESTING_PREREQUISITES.md` | New | ≤ 80 |

---

## Steps

### Step 1.1 — Create TestFixtures object

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/TestFixtures.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a Kotlin `object TestFixtures` in package `com.sza.fastmediasorter` inside the `androidTest` source set. It must contain: (a) `DEFAULT_USER` = `"test-default-user"`, (b) `DEFAULT_SHARE_PATH` = `"/test-share"`, (c) `TEST_SMB_RESOURCE_NAME` = `"Test-SMB"`, (d) `TEST_SFTP_RESOURCE_NAME` = `"Test-SFTP"`, (e) `TEST_FTP_RESOURCE_NAME` = `"Test-FTP"`, (f) `TEST_LOCAL_FOLDER` = `"/storage/emulated/0/TestMedia"`, (g) `TEST_CLOUD_RESOURCE_NAME` = `"Test-Cloud"`, (h) a helper `fun createTempFile(dir: File, name: String, content: String = "test"): File` that writes content and returns the file. Use no Android-specific imports in the object itself — it must compile as a pure Kotlin object.

**Verification:**

- `Glob` — `app_v2/src/androidTest/java/com/sza/fastmediasorter/TestFixtures.kt` exists.
- `Grep` — `object TestFixtures` matches exactly once in that file.
- `Grep` — `DEFAULT_USER` present in that file.
- `Grep` — `fun createTempFile` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: TestFixtures.kt (new, 20 LOC). Dev log recorded.

---

### Step 1.2 — Create TESTING_PREREQUISITES.md

**Files:** `app_v2/src/androidTest/TESTING_PREREQUISITES.md`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `app_v2/src/androidTest/TESTING_PREREQUISITES.md`. It must document: (1) Which local folder must exist on the test device before running instrumentation tests (`TestFixtures.TEST_LOCAL_FOLDER`); (2) Which tests require real network resources and are guarded by `@NetworkRequired` — these are skipped automatically on devices without the flag; (3) The exact string values of `TestFixtures.DEFAULT_USER`, `TEST_SMB_RESOURCE_NAME`, etc. so a new test environment can be configured without reading source code; (4) How to add a test that requires device-specific setup: annotate with `@NetworkRequired`, wrap assertions with `assumeTrue(isNetworkTestEnabled())`.

**Verification:**

- `Glob` — `app_v2/src/androidTest/TESTING_PREREQUISITES.md` exists.
- `Grep` — `TEST_LOCAL_FOLDER` present in that file.
- `Grep` — `@NetworkRequired` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: TESTING_PREREQUISITES.md (new, 57 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`TestFixtures` is now the canonical source for all string literals used across androidTest. Phase 02 replaces hardcoded literals in the existing two test files with references to this object.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
