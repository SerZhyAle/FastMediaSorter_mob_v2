# Phase 06 - Round-trip tests

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 0 / 1
**Started:** -
**Completed:** -

---

## Objective

Prove that mapping each payload section to backup form and back is lossless, including secret sections and old-version tolerance.

---

## Prerequisites

- [ ] Phase 04 ✅ Done.
- [ ] Phase 05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/BackupMapperRoundTripTest.kt` | New | ≤ 260 |

---

## Steps

### Step 06.1 - BackupMapper round-trip unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/BackupMapperRoundTripTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add JUnit tests (pure, no Android framework) covering: (a) network credential → backup → entity preserves server/port/username/domain/shareName and plaintext password; (b) web auth session ↔ backup preserves host/accountId/userAgent and cookie name/value/domain/path; (c) settings → backup → settings preserves `defaultUser`/`defaultPassword`; (d) a `BackupPayload` Gson-deserialized from a v4 JSON string (no `networkCredentials`/`webAuthSessions` nodes) yields null secret sections without throwing. Mock or stub Android-only crypto by asserting at the mapper boundary that does not require Keystore where possible; if `CryptoHelper` needs Android, restrict that assertion to fields that survive without it.

**Verification:**

- `Glob` - `BackupMapperRoundTripTest.kt` exists.
- `Grep` - `class BackupMapperRoundTripTest` matches once.
- `Grep` - `@Test` matches at least 4 times.
- Build: `gradlew testStandardDebugUnitTest --tests "*BackupMapperRoundTripTest"` - the new class passes (per-class XML report).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step 06.1 is `[x] done`.
- [ ] New test class passes (per-class report green).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for the test file.

---

## Handoff Notes to Next Phase

Round-trip parity proven. Phase 07 finalizes docs, strings, catalog, changelog.

---

## Rollback Plan

Revert phase commit - test-only change.
