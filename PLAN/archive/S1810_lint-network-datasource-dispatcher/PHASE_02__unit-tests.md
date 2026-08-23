# Phase 02 - Unit Tests

**Strategic spec:** [`../S1810_lint-network-datasource-dispatcher.md`](../S1810_lint-network-datasource-dispatcher.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Add network library test stubs and comprehensive positive and negative unit test cases for `NetworkDataSourceDispatcherDetector` in `CustomLintRulesTest.kt`.

---

## Prerequisites

- [ ] Phase 01 is Done.

---

## Files Touched

- `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt` (modified)

---

## Steps

### Step 02.1 - Add network library test stubs

**Files:** `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** none

**Prompt for developer:**

> Add Kotlin/Java stubs for `com.hierynomus.smbj.SMBClient`, `org.apache.commons.net.ftp.FTPClient`, and `com.jcraft.jsch.ChannelSftp` / `Session` in `CustomLintRulesTest.kt`.

**Why:**

> Lint tests compile synthetic test snippets against stubs to resolve method owners.

**Verification:**

- `Grep` - `com.hierynomus` matches in `CustomLintRulesTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Added stubs, positive/negative test cases in CustomLintRulesTest and verified 37/37 tests pass

---

### Step 02.2 - Add positive test cases (unconfined network calls report error)

**Files:** `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add tests where unconfined suspend functions or unconfined helpers call smbj / ftp / sftp methods and assert that `NetworkDataSourceDispatcher` issue is reported.

**Why:**

> Proves detector flags real unconfined network calls.

**Verification:**

- Unit test asserts issue reported on unconfined SMB/FTP/SFTP calls.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Added stubs, positive/negative test cases in CustomLintRulesTest and verified 37/37 tests pass

---

### Step 02.3 - Add negative test cases (confined network calls pass cleanly)

**Files:** `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add tests where calls are enclosed in `withContext(Dispatchers.IO)`, or inside a private helper called exclusively from `withContext(Dispatchers.IO)`, or using a field-stored `Dispatchers.IO` dispatcher, and assert that 0 issues are reported.

**Why:**

> Proves detector produces zero false positives for properly confined calls.

**Verification:**

- Unit test asserts 0 warnings / 0 errors on confined network calls.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Added stubs, positive/negative test cases in CustomLintRulesTest and verified 37/37 tests pass

---

### Step 02.4 - Run lint-rules test suite

**Files:** none (execution only)
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `.\a.ps1 flr` and verify all tests pass.

**Why:**

> Proves new detector test cases pass and existing 30 detector tests remain green.

**Verification:**

- `.\a.ps1 flr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Added stubs, positive/negative test cases in CustomLintRulesTest and verified 37/37 tests pass

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Dev log entry added for every file in "Files Touched".
