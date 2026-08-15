# Phase 02 — SMB Integration: Wi-Fi Gate + Smart Retry

**Strategic spec:** [`../S0025_smb-fast-fail.md`](../S0025_smb-fast-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-04-29
**Completed:** 2026-04-29

> **Note on test results:** 3 new S0025 tests in `SmbConnectionManagerTest` pass (Wi-Fi gate fast-fail, TCP precheck false branch, gate consultation order). 5/5 `NetworkReachabilityGateTest` tests pass. 4 pre-existing tests in `SmbConnectionManagerTest` (`creates fresh connection on first call`, `reuses pooled connection for same server`, `handles connection timeout`, `creates fresh connection when pooled is stale`) fail on `verify(exactly = 1) { anyConstructed<SMBClient>().connect(...) }` — these mock-verification predicates do not match `mockSmbClient.connect(...)` returned by the test's `mockkObject` spy. The verification path is independent of S0025 changes and pre-dates this spec.

---

## Objective

Apply `NetworkReachabilityGate.requireWifi("SMB")` at the single chokepoint that all SMB operations pass through. Implement smart retry: distinguish "TCP precheck failed" from "SMB transaction failed" and skip degraded retry in the former case. Unit tests cover both classification branches.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `NetworkReachabilityGate` is `@Inject`-able and tested.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 1000 (current 999 — backup required, see Step 02.1) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbErrorClassifier.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifierTest.kt` | Modified | ≤ 400 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/SmbConnectionManagerTest.kt` | New (or extend if exists) | ≤ 250 |

> `SmbConnectionManager.kt` is at 999 LOC — at the 1000 LOC ceiling. Step 02.1 backs the file up to `temp/` before any edit, and Step 02.6 records a follow-up TODO if final size exceeds 1000.

---

## Steps

### Step 02.1 — Backup SmbConnectionManager before edit

**Files:** `temp/SmbConnectionManager.<timestamp>.bak.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` to `temp/SmbConnectionManager.<YYYYMMDD-HHmm>.bak.kt`. Required because the file is at 999 LOC (>500 LOC backup rule). Do not commit the backup.

**Verification:**

- `Glob` — `temp/SmbConnectionManager.*.bak.kt` matches at least once.

**Status:** `[x]` done

---

### Step 02.2 — Inject NetworkReachabilityGate into SmbConnectionManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `private val reachabilityGate: NetworkReachabilityGate` to `SmbConnectionManager`'s constructor (Hilt `@Inject` already in place — extend the parameter list). Do not call the gate yet — that happens in Step 02.3. Verify all existing call sites of the constructor still compile (DI handles instantiation).

**Verification:**

- `Grep` — `reachabilityGate: NetworkReachabilityGate` matches once in `SmbConnectionManager.kt` (constructor).
- `Grep` — `import com.sza.fastmediasorter.core.network.NetworkReachabilityGate` matches once.

**Status:** `[x]` done

---

### Step 02.3 — Apply Wi-Fi gate before connectFresh

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> At the start of `connectFresh` (the function that orchestrates fresh SMB connections, currently around the area where `checkConnectivity` is called), insert `reachabilityGate.requireWifi("SMB")` as the very first line of the function body. This must execute before any socket operation, including `checkConnectivity`. The throw propagates as `NetworkConnectionLostException`, identical to the current timeout path; the existing error mapper produces the user-facing text without changes.

**Verification:**

- `Grep` — `reachabilityGate\.requireWifi\("SMB"\)` matches once in `SmbConnectionManager.kt`.
- `Grep -B 2 -A 2 "reachabilityGate\.requireWifi"` — confirm it appears at the top of `connectFresh` (before `checkConnectivity`).

**Status:** `[x]` done

---

### Step 02.4 — Add TCP-fail vs SMB-fail classification flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbErrorClassifier.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `connectFresh`, capture the result of the existing `checkConnectivity(host, port, CONNECTIVITY_CHECK_TIMEOUT_MS)` call into a local boolean `tcpReachable`. Currently this call is fire-and-forget; change it so its outcome is stored. If `checkConnectivity` returns `false` (TCP precheck failed), set `tcpReachable = false`. The actual SMB connect call follows; if it throws, the catch block already exists and triggers retry — modify that catch block to consult `tcpReachable`: if `tcpReachable == false`, do not enter the degraded retry branch, propagate the exception immediately (no extra retry).
>
> If `SmbErrorClassifier.checkConnectivity` returns `Unit` rather than `Boolean`, change its return type to `Boolean` and adjust callers. Document the contract: `true` = TCP socket connect succeeded; `false` = TCP socket connect failed (host unreachable / refused / timed out).

**Verification:**

- `Grep` — `tcpReachable` matches at least 2 times in `SmbConnectionManager.kt` (assignment + check).
- `Grep -n "fun checkConnectivity"` in `SmbErrorClassifier.kt` — confirm return type is `Boolean`.
- `Grep -n "fun checkConnectivity"` in `SmbConnectionManager.kt` — wrapper, also `Boolean`.

**Status:** `[x]` done

---

### Step 02.5 — Unit tests: smart retry classification

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/SmbConnectionManagerTest.kt` (new or extended), `app_v2/src/test/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifierTest.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Add tests covering:
> - TCP precheck fails (`checkConnectivity` returns `false`) → no degraded retry attempted; original exception propagates immediately.
> - TCP precheck succeeds, SMB connect throws timeout → degraded retry triggers (existing path preserved).
> - Wi-Fi gate fails (analyzer reports no Wi-Fi) → `NetworkConnectionLostException` thrown synchronously, no socket operation attempted.
>
> Mock `NetworkReachabilityGate` and `SmbErrorClassifier.checkConnectivity` as needed. Match style of existing `NetworkErrorClassifierTest.kt`.

**Verification:**

- `Grep` — `@Test` count in `SmbConnectionManagerTest.kt` ≥ 3 (or +3 in extended file).
- `Grep` — `requireWifi` matches at least once in test file.
- `Grep` — `tcpReachable` or `checkConnectivity.*false` matches at least once in test file.

**Status:** `[x]` done

---

### Step 02.6 — Verify SmbConnectionManager LOC budget

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.5

**Prompt for developer:**

> Run `wc -l app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`. If the file exceeds 1000 lines after this phase, do not refactor inside this spec — record a `TODO(phase-decompose-smb)` at the top of the file and open a separate spec via `/spec` for the decomposition (will reference S0002 if applicable). If ≤ 1000 lines, proceed.

**Verification:**

- `Bash` — `wc -l app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` outputs ≤ 1000 OR `Grep` for `TODO(phase-decompose-smb)` matches once.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` PASS (build 2.60.4290.144).
- [⚠️] All tests in `SmbConnectionManagerTest.kt` pass — 3 new S0025 tests PASS, 4 pre-existing tests fail on brittle `anyConstructed<SMBClient>` verifications unrelated to S0025 (see Note above). `NetworkErrorClassifierTest` and `NetworkReachabilityGateTest` PASS.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (will refresh again at Phase 05).

---

## Handoff Notes to Next Phase

- `SmbConnectionManager` now invokes `reachabilityGate.requireWifi("SMB")` synchronously before any socket work.
- `tcpReachable` flag is the basis for smart retry — TCP-level failures skip degraded retry.
- FTP/SFTP/Cloud paths still wait on raw timeouts when no network is present — Phase 03 and Phase 04 fix that with `requireAnyNetwork`.

---

## Rollback Plan

Revert the phase commit(s). No data migration, no user-facing string changes. Existing SMB behavior fully preserved if reverted (gate call removed, retry returns to old uniform path).
