# Phase 01 — Default Value Flip

**Strategic spec:** [`../S0133_accept-shared-files-default-on.md`](../S0133_accept-shared-files-default-on.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Flip the default value of `acceptSharedFiles` from `false` to `true` at both source-of-truth points (domain model + DataStore read fallback). No runtime behavior changes yet — this only changes what new (key-absent) installs report.

---

## Prerequisites

- [x] No phases in "Depends on" — foundation.
- [x] Strategic §6 research items Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 250 (existing) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 (existing 685) |

> `SettingsRepositoryImpl.kt` is 685 LOC — no growth in this phase, no backup required.

---

## Steps

### Step 01.1 — Flip default in domain model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AppSettings.kt`, change the default of the `acceptSharedFiles` field from `false` to `true`. Add a one-line `// S0133: default ON` comment immediately above the field.

**Verification:**

- `Grep -n "val acceptSharedFiles: Boolean = true"` returns exactly one hit in `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`.
- `Grep -n "val acceptSharedFiles: Boolean = false"` returns zero hits in that file.
- `Grep -n "S0133"` matches at least once in that file (provenance comment).

**Status:** `[x]` done

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: `AppSettings.kt` (+1 LOC comment, default flipped). Dev log recorded.

---

### Step 01.2 — Flip DataStore read fallback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `SettingsRepositoryImpl.kt` change the line `acceptSharedFiles = preferences[KEY_ACCEPT_SHARED_FILES] ?: false` to use `?: true`. Existing users with the key explicitly stored in DataStore are unaffected — the change only flips the value returned when the key is absent.

**Verification:**

- `Grep -n "acceptSharedFiles = preferences\[KEY_ACCEPT_SHARED_FILES\] \?: true"` returns exactly one hit.
- `Grep -n "acceptSharedFiles = preferences\[KEY_ACCEPT_SHARED_FILES\] \?: false"` returns zero hits.
- `Grep -n "Log\.d\("` returns zero hits in this file (Timber-only rule).

**Status:** `[x]` done

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: `SettingsRepositoryImpl.kt` (line 351 fallback flipped, +S0133 inline note). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `BUILD SUCCESSFUL` (verified 2026-05-10).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry to be added in Phase 03 step 03.3 (consolidated bulk run).
- [x] No public API changed → catalog regen optional in this phase (deferred to Phase 03).

---

## Handoff Notes to Next Phase

After Phase 01: a fresh install (no DataStore key for `accept_shared_files`) yields `acceptSharedFiles = true` in `AppSettings`. The system component aliases are still `enabled="false"` in the manifest — Phase 02 wires the bootstrap that reconciles them with DataStore on every process start.

---

## Rollback Plan

Revert phase commit — both files return to `false` defaults. No data migration touched, no schema bumped, no user-visible surface changed yet.
