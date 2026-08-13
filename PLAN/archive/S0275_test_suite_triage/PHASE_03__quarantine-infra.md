# Phase 03 - quarantine-infra

**Strategic spec:** [`../S0275_test_suite_triage.md`](../S0275_test_suite_triage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce a JUnit 4-compatible quarantine mechanism and a registry file so legacy failures can be excluded from the default `standard` suite without becoming invisible.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] The set of `quarantine-candidate` classes in `temp/S0275_class_triage.md` is stable enough to justify infrastructure.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/testinfra/QuarantineLegacyFailure.kt` | New | ≤ 80 |
| `dev/test-quarantine/S0275_standard_unit_quarantine.md` | New | ≤ 300 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split first.

---

## Steps

### Step 03.1 - Add the JUnit 4 quarantine marker and dedicated Gradle task

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/test/java/com/sza/fastmediasorter/testinfra/QuarantineLegacyFailure.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a JUnit 4 category marker named `QuarantineLegacyFailure`. Wire `:app_v2:testStandardDebugUnitTest` to exclude it by default, and register a dedicated task `testStandardDebugQuarantineUnitTest` that runs only the quarantined classes. Preserve IDE single-class runs and per-class XML generation.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/testinfra/QuarantineLegacyFailure.kt` exists.
- `Grep` - `interface QuarantineLegacyFailure` matches exactly once in that file.
- `Grep` - `testStandardDebugQuarantineUnitTest` matches in `app_v2/build.gradle.kts`.
- `Grep` - `QuarantineLegacyFailure` matches in `app_v2/build.gradle.kts`.

**Status:** `[ ]` not done

---

### Step 03.2 - Create the quarantine registry and entry format

**Files:** `dev/test-quarantine/S0275_standard_unit_quarantine.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `dev/test-quarantine/S0275_standard_unit_quarantine.md` as the source of truth for quarantined classes. Each entry must include class name, failure reason, linked ticket or follow-up, and the command that re-runs the quarantined class or suite.

**Verification:**

- `Glob` - `dev/test-quarantine/S0275_standard_unit_quarantine.md` exists.
- `Grep` - `Class | Reason | Follow-up` header present.
- `Grep` - `testStandardDebugQuarantineUnitTest` present in the file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] The default `standard` suite excludes quarantine by category.
- [ ] The dedicated quarantine task exists and is executable.
- [ ] `dev/test-quarantine/S0275_standard_unit_quarantine.md` is the only registry for quarantined classes.
- [ ] `dev/CHANGELOG.md` has an entry for every touched file via `add_to_dev_log.ps1` / `post-change.ps1`.

---

## Handoff Notes to Next Phase

Phase 04 applies `fixed` / `deleted` / `quarantined` to every remaining red class and then proves the default suite is green.

---

## Rollback Plan

Revert the Gradle task and category marker; delete the registry file if quarantine is abandoned.