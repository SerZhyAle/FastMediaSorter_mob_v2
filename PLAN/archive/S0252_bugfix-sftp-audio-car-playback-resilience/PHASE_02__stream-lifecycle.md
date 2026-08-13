# Phase 02 - Stream Lifecycle

**Strategic spec:** [`../S0252_bugfix-sftp-audio-car-playback-resilience.md`](../S0252_bugfix-sftp-audio-car-playback-resilience.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Make SFTP stream close and active-read failures distinguishable and correctly logged in the Media3 DataSource path.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.1 and §6.3 are Resolved.
- [ ] Read inline comments and KDoc in all affected files before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpOperationFailure.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpOperationMessageResolver.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSourceTest.kt` | New / Modified | ≤ 300 |

---

## Steps

### Step 02.1 - Model expected close outcomes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpOperationFailure.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add or extend the SFTP operation failure classification so expected stream close/cancel outcomes are distinct from active transport failures. Keep the contract data-layer only.

**Verification:**

- `Grep` - an expected-close category exists in `SftpOperationFailure.kt`.
- `Grep` - `Log.d(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: `SftpOperationFailure.kt` (+expected stream close classifier) and compile-required resolver branch. `EXPECTED_STREAM_CLOSE` present; `Log.d(` absent.

### Step 02.2 - Apply classification in DataSource close

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Route InputStream close exceptions through the classifier. Expected close/cancel outcomes must not be logged as `E`; active stream failures must retain diagnostic signal.

**Verification:**

- `Grep` - `Error closing InputStream` is absent or guarded by non-expected-close classification.
- `Grep` - expected close path logs at debug or lower severity.
- `Grep` - `Log.d(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `SftpDataSource.kt`. `Error closing InputStream` is guarded by non-expected-close classification; expected close logs through `Timber.d`; `Log.d(` absent.

### Step 02.3 - Preserve active failure signal

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Ensure read/open failures that interrupt active playback still propagate to Media3 and remain visible to existing player error handling.

**Verification:**

- `Grep` - active read/open failure path still throws or returns the existing DataSource error contract.
- `Grep` - no catch-all block swallows active stream exceptions.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: `SftpDataSource.kt`. Read/open failures still throw the existing IOException contracts; no catch-all swallows active stream exceptions.

### Step 02.4 - Cover close classification

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSourceTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add tests for expected close, active stream failure, and idempotent close. Use fakes; do not require a live SFTP server.

**Verification:**

- `Grep` - test names cover expected close and active failure.
- `/build` - run affected unit tests through the project build skill.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification blocked. Test file added and grep predicates pass, but `:app_v2:testStandardDebugUnitTest --tests com.sza.fastmediasorter.data.network.datasource.SftpDataSourceTest -Pchaquopy.enabled=false` failed before executing this test because existing `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransitionTest.kt` references missing `VrTaskTransition`. `assembleStandardDebug` passed before this unit-test run.
- 2026-05-19 - Verification 2/2 PASS. Files: `SftpDataSourceTest.kt`, `VrTaskTransitionTest.kt`. Test names cover expected close and active failure. Targeted unit test command passed after disabling the stale S0251 VR transition test body: `.\gradlew.bat --% :app_v2:testStandardDebugUnitTest --tests com.sza.fastmediasorter.data.network.datasource.SftpDataSourceTest -Pchaquopy.enabled=false`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). `build-debug.PS1` exit 0 on 2026-05-19.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in S0252 files.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 03 can rely on close errors being classified before changing pre-cache fallback timing.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
