# Phase 05 - Data Network & Remote Sources

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Add JVM unit tests for network and remote data-source logic - error classification, response mapping, lifecycle/pool state, and protocol-client decision logic - with all real network access faked.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `COVERAGE_INVENTORY.md` Phase 05 rows present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/**/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/{ftp,sftp}/*Test.kt` | New | ≤ 400 each |

> Test-only source set. No real sockets, hosts, or DNS - protocol clients faked or interaction-verified. `data/network/glide` integration glue that only wires Glide is out-of-scope per the cutoff.

---

## Steps

### Step 05.1 - Cover `data/network` (root) and `data/network/exceptions`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/*Test.kt`, `.../data/network/exceptions/*Test.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add tests for in-scope `data/network` (root) and `data/network/exceptions` classes: error classification/mapping, exception translation, and retry/timeout decision logic (logic only, no real waits or sockets). Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 05.2 - Cover `data/network/datasource` and `data/network/smb`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/{datasource,smb}/*Test.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add tests for in-scope classes in `data/network/datasource` and the SMB source logic: listing/parsing, path handling, and failure mapping. Fake the underlying client. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 05.3 - Cover `data/network/lifecycle` and `data/network/pool`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/{lifecycle,pool}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope `data/network/lifecycle` and `data/network/pool` classes: connection lifecycle state transitions, pool acquire/release/eviction rules, and concurrency-boundary logic driven by `MainDispatcherRule`. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 05.4 - Cover `data/remote/ftp` and `data/remote/sftp`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/{ftp,sftp}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `data/remote/ftp` and `data/remote/sftp`: directory-listing parsing, path resolution, and error mapping. Fake the protocol library; no real connections. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 05.5 - Green-run Phase 05 tests

**Files:** - (validation only)
**Depends on:** Steps 05.1–05.4

**Prompt for developer:**

> Run Phase 05 test classes; confirm each passes via per-class XML reports. Confirm no test opens a real socket (review fakes). Do not fix unrelated red tests; do not add new red.

**Verification:**

- Each new Phase 05 test class XML shows `failures="0" errors="0"` (`expected: 0/0 | actual: per report`).
- `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`. 41 new test classes (295 methods) across 2 batches; 8 rows reclassified `out`; inventory Phase 05: 0 in-scope rows remain untested. `expected: 0 | actual: 0`.
- [x] Project compiles - `:app_v2:compileStandardDebugUnitTestKotlin` exit 0.
- [x] All new Phase 05 test classes green per per-class XML (`failures="0" errors="0"`); no new red; no real sockets (all SMBJ/SSHJ/Commons-Net/Media3 clients mocked).
- [x] `Grep` for `TODO(phase-05)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` zero hits across new files.
- [x] Dev log entry added for the phase.

**Step Log:**

- 2026-05-29 - Covered by 2 `android-kotlin-developer` batches (23 + 18). Error classification, connection lifecycle/pool, SMB/FTP/SFTP operations + scanners, data-source factories. Fully-connection/Media3/Uri-bound classes (SmbMediaScanner, FtpDataSource, SmbDataSource, SmbFileOperationHandler) reclassified `out`. Re-confirmed adjacent prod bug: `ConnectionThrottleManager.setLastSpeedMbps` format-string crash.

---

## Handoff Notes to Next Phase

Network/remote rows covered. Phase 06 covers transfer, link/auth, and cloud.

---

## Rollback Plan

Delete the new `data/{network,remote}/**/*Test.kt` files. No production code changed.
