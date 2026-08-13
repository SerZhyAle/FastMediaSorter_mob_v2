# Phase 04 - Classifier unit tests

**Strategic spec:** [`../S1055_sftp-reconnect-failure-classification.md`](../S1055_sftp-reconnect-failure-classification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** -
**Completed:** 2026-07-15

---

## Objective

Lock the four-way classification in unit tests so a future JSch upgrade or heuristic edit cannot silently re-fold auth-reject or host-key-change into a transient outcome.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifierTest.kt` | Modified | ≤ 500 |

---

## Steps

### Step 04.1 - Add four-way classification test cases

**Files:** `test/.../NetworkErrorClassifierTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Following the existing test style in the file, add cases asserting:
> - host-key: `classify(JSchException("reject HostKey: host"))` is `NetworkHostKeyChangedException` AND `isTransient` is false; also `classify(HostKeyMismatchException(expected="SHA256:a", actual="SHA256:b"))` (message contains "host-key mismatch") maps to `NetworkHostKeyChangedException`.
> - auth-reject: `classify(JSchException("Auth fail"))` and `classify(JSchException("USERAUTH fail"))` are `NetworkAccessDeniedException` AND `isTransient` is false.
> - wrapped cause: `classify(IOException("Failed to establish SFTP connection: Auth fail", JSchException("Auth fail")))` still resolves to `NetworkAccessDeniedException` (cause-chain recursion reaches the new branch).
> - regression - transport still transient: `classify(IOException("Connection reset by peer"))` stays `NetworkConnectionLostException` with `isTransient` true.
> - regression - SFTP status not swallowed: an `SftpException`-style "permission denied" file message does not classify as `NetworkHostKeyChangedException` (a host-key false-positive would be the security-relevant failure). Use `com.jcraft.jsch.JSchException` and `com.jcraft.jsch.SftpException` as available on the test classpath, or a plain `Exception(message)` where only the message drives classification.

**Verification:**

- `Grep` - `NetworkHostKeyChangedException` and `NetworkAccessDeniedException` both referenced in the test file.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*NetworkErrorClassifierTest*"` passes (run via `/build` conventions / `a.ps1`, never a second concurrent gradle).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] `Step 04.1` is `[x] done`.
- [ ] Targeted unit test run is green.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the test file.

---

## Handoff Notes to Next Phase

Classification is proven. Phase 05 regenerates the catalog and closes bookkeeping.

---

## Rollback Plan

Revert the phase commit - tests only, no production code touched.
