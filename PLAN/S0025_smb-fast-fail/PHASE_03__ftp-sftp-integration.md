# Phase 03 — FTP/SFTP Integration: No-Network Gate

**Strategic spec:** [`../S0025_smb-fast-fail.md`](../S0025_smb-fast-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Apply `NetworkReachabilityGate.requireAnyNetwork(label)` at the chokepoint of FTP and SFTP connection paths, so a tap on an FTP/SFTP resource without any network fails immediately. No Wi-Fi gate for these — they can legitimately work over cellular.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt` | Modified | ≤ 920 (current 906 — backup required) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 640 (current 628 — backup required) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/ftp/FtpClientTest.kt` | New (or extend if exists) | ≤ 150 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpClientTest.kt` | New (or extend if exists) | ≤ 150 |

---

## Steps

### Step 03.1 — Backup large files before edit

**Files:** `temp/FtpClient.<timestamp>.bak.kt`, `temp/SftpClient.<timestamp>.bak.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `FtpClient.kt` (906 LOC) and `SftpClient.kt` (628 LOC) into `temp/` with timestamp suffix. Both exceed the 500 LOC backup threshold.

**Verification:**

- `Glob` — `temp/FtpClient.*.bak.kt` matches at least once.
- `Glob` — `temp/SftpClient.*.bak.kt` matches at least once.

**Status:** `[x]` done

---

### Step 03.2 — Apply gate in FtpClient connection path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `private val reachabilityGate: NetworkReachabilityGate` to `FtpClient`'s constructor (Hilt). Identify the single internal function that opens a fresh FTP connection (typically named `connect`, `establishConnection`, `openSession`, or similar — not the higher-level operations). Insert `reachabilityGate.requireAnyNetwork("FTP")` as the first statement of that function. Background uploads / downloads driven by WorkManager that already check for network constraints via WorkManager's own `setRequiredNetworkType` should not be affected — verify by searching for `setRequiredNetworkType` usage and ensuring those callers wrap into the same `connect` function (so WorkManager's constraint defers the call entirely; the gate then never throws because by then network IS available).

**Verification:**

- `Grep` — `reachabilityGate\.requireAnyNetwork\("FTP"\)` matches once in `FtpClient.kt`.
- `Grep` — `reachabilityGate: NetworkReachabilityGate` matches once in `FtpClient.kt` constructor.
- `Grep` — `Log\.d\(` returns zero hits in modified function bodies.

**Status:** `[x]` done

---

### Step 03.3 — Apply gate in SftpClient / SftpConnectionPool

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Identify which class actually opens the SFTP socket — typically `SftpConnectionPool` (488 LOC) holds the JSch `Session.connect()` call, and `SftpClient` delegates to it. Inject `NetworkReachabilityGate` into whichever class owns the `Session.connect()` call. Insert `reachabilityGate.requireAnyNetwork("SFTP")` as the first statement of the function that creates a fresh session. If both classes can independently create sessions, gate both; deduplication (factor into a shared helper) is out of scope for this phase.

**Verification:**

- `Grep` — `reachabilityGate\.requireAnyNetwork\("SFTP"\)` matches at least once across `SftpClient.kt` and `SftpConnectionPool.kt`.
- `Grep` — `reachabilityGate: NetworkReachabilityGate` matches in the file(s) modified.

**Status:** `[x]` done

---

### Step 03.4 — Tests for FTP/SFTP gate

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/ftp/FtpClientTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpClientTest.kt`
**Depends on:** Steps 03.2, 03.3

**Prompt for developer:**

> For FTP and SFTP test files (create if absent, extend if present): add tests that mock `NetworkReachabilityGate` to throw `NetworkConnectionLostException` from `requireAnyNetwork(...)`, and assert the connect function propagates the exception without attempting any socket operation. Use the existing pool / mock socket infrastructure of these test classes if available.

**Verification:**

- `Grep` — `requireAnyNetwork` matches at least once in `FtpClientTest.kt`.
- `Grep` — `requireAnyNetwork` matches at least once in `SftpClientTest.kt`.
- `Grep` — `NetworkConnectionLostException` matches at least once in each test file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] FTP/SFTP test files pass.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- All synchronous FTP/SFTP entry points now reject immediately when no transport is active.
- Cloud paths (Phase 04) need similar treatment but with extra care for WorkManager-driven background tasks that should queue, not fail.

---

## Rollback Plan

Revert the phase commit(s). FTP/SFTP behavior identical to current implementation when reverted.
