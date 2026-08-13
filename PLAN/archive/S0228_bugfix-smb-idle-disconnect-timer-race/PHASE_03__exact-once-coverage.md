# Phase 03 — Exact-once coverage (Pillar C)

**Strategic spec:** [`../S0228_bugfix-smb-idle-disconnect-timer-race.md`](../S0228_bugfix-smb-idle-disconnect-timer-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Add deterministic unit coverage for stale-generation suppression and freeze the SMB-side transport-key contract so the later `BlockNeedUserTest` pass has both in-process and log-based evidence targets.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Strategic §6.3 is Resolved — the manual log predicate for exact-once SMB acceptance is frozen.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/test/java/com/sza/fastmediasorter/data/network/smb/SmbConnectionManagerTest.kt` is 441 LOC and must stay ≤ 500 LOC after this phase to avoid a backup step.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImplTest.kt` | Modified | ≤ 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/smb/SmbConnectionManagerTest.kt` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 — Add stale-generation timeout test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImplTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `fun \`touch suppresses stale timeout callback from older generation\`() = runTest { .. }` using the existing `StandardTestDispatcher(testScheduler)` pattern. Sequence: arm `smb@test` for `1_000`, advance `900`, touch the same transport, advance the old deadline by `100` and assert zero fires, then advance the new deadline and assert exactly one fire. No `Thread.sleep`, no real dispatchers.

**Verification:**

- `Grep` — `touch suppresses stale timeout callback from older generation` matches exactly once in `IdleDisconnectPolicyImplTest.kt`.
- `Grep` — `policy.touch("smb@test")` matches at least 1 time in `IdleDisconnectPolicyImplTest.kt`.
- `Grep` — `Thread.sleep` returns zero hits in `IdleDisconnectPolicyImplTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImplTest.kt` (+19 LOC, stale-generation regression test added, file diagnostics clean). Dev log recorded.

---

### Step 03.2 — Add disarm-after-touch regression test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImplTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `fun \`disarm cancels latest generation after touch\`() = runTest { .. }`. Sequence: arm, touch, disarm before the rearmed deadline, advance until idle, assert callback count remains zero. This protects against cancelling only the original job while leaving the latest generation armed.

**Verification:**

- `Grep` — `disarm cancels latest generation after touch` matches exactly once in `IdleDisconnectPolicyImplTest.kt`.
- `Grep` — `policy.disarm("smb@test")` matches at least 2 times in `IdleDisconnectPolicyImplTest.kt`.
- `Grep` — `advanceUntilIdle()` matches at least 3 times in `IdleDisconnectPolicyImplTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImplTest.kt` (+17 LOC, disarm-after-touch regression test added, file diagnostics clean). Dev log recorded.

---

### Step 03.3 — Freeze the SMB transport-key contract in manager tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/smb/SmbConnectionManagerTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Introduce a named field `private lateinit var mockIdleDisconnectPolicy: IdleDisconnectPolicy` and pass it into `SmbConnectionManager` instead of constructing an inline relaxed mock. Add `fun \`withConnection touches and arms the same SMB transport key\`() = runBlocking { .. }` that performs one successful `withConnection` call, captures the `transport` argument used for both `touch(..)` and `arm(..)`, and asserts both calls use the exact key string `smb@testserver:445:testshare:testuser:` with `idleMs == 30_000L`. This is the SMB-specific predicate Phase 04 will later rely on during live logcat verification.

**Verification:**

- `Grep` — `private lateinit var mockIdleDisconnectPolicy: IdleDisconnectPolicy` matches exactly once in `SmbConnectionManagerTest.kt`.
- `Grep` — `withConnection touches and arms the same SMB transport key` matches exactly once in `SmbConnectionManagerTest.kt`.
- `Grep` — `smb@testserver:445:testshare:testuser:` matches exactly once in `SmbConnectionManagerTest.kt`.
- `Grep` — `30_000L` matches at least once in `SmbConnectionManagerTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/data/network/smb/SmbConnectionManagerTest.kt` (+30 LOC, named idle policy mock and SMB transport-key contract test added, file diagnostics clean). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `./build-debug.PS1` PASS (`assembleStandardDebug`, 46s, 2026-05-16).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep` for `touch suppresses stale timeout callback from older generation` returns exactly one match in `IdleDisconnectPolicyImplTest.kt`.
- [x] `Grep` for `disarm cancels latest generation after touch` returns exactly one match in `IdleDisconnectPolicyImplTest.kt`.
- [x] `Grep` for `withConnection touches and arms the same SMB transport key` returns exactly one match in `SmbConnectionManagerTest.kt`.
- [x] `Grep` for `Log\.d\(` returns zero hits in both modified test files.
- [x] Dev log entries added for `IdleDisconnectPolicyImplTest.kt` and `SmbConnectionManagerTest.kt` via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 03, the shared idle layer has deterministic unit coverage for exact-once semantics and the SMB manager test freezes the transport-key + idle-window contract used by manual verification. A device pass is still required to prove the real SMB stack no longer bursts `timeout fired` lines for one idle window.

---

## Rollback Plan

Revert the Phase 03 commit(s) — test-only scope, no user-facing or schema impact.