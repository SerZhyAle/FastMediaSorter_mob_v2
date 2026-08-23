# Phase 01 - The device lease

**Strategic spec:** [`../S1926_device-lease-for-parallel-sessions.md`](../S1926_device-lease-for-parallel-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`scripts/devtest/device-lease.ps1` claims, releases, lists, reports and sweeps device leases with the same verbs, exit codes and liveness rule as the ticket lease.

---

## Prerequisites

- [ ] Strategic §6 items 1-3 are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/device-lease.ps1` | New | ≤ 260 |
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 15 added |
| `scripts/devtest/device-lease.tests/Run-Tests.ps1` | New | ≤ 160 |

---

## Steps

### Step 01.1 - Add the `Device` timings entry

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `Device` entry to `$Script:AgentLockTimings` beside `SpecTicket`, and extend the `ValidateSet` on `Get-AgentLockTimings` to accept it. Set `LockStaleMinutes` and `ReservationMinutes` to 0 (no lock file, no queue), `SessionStaleMinutes` to the same window the ticket lease uses, and a shorter `TicketCeilingMinutes` than the ticket lease. Comment why the session window is the ticket lease's and not the code lock's.

**Why:**

Strategic §3.2 forbids writing minute values into the new script, and §6.2 resolved the window to the ticket lease's on the grounds that a session driving a scenario is silent for tens of minutes while it builds and installs - the code lock's shorter window would evict it mid-install.

**Verification:**

- `Grep` - `Device` appears as a key in `$Script:AgentLockTimings` and inside the `ValidateSet` of `Get-AgentLockTimings`.
- Run: `pwsh -NoProfile -Command ". ./scripts/utils/agent-lock.ps1; (Get-AgentLockTimings -Name Device).SessionStaleMinutes"` - expected: prints the window, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 01.1

---

### Step 01.2 - Write the lease script

**Files:** `scripts/devtest/device-lease.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Model it on `scripts/spec_catalog/ticket-lease.ps1`: verbs `Claim`, `Release`, `List`, `Status`, `Sweep`; one file per lease under `temp/DEVICE.LEASES/<serial>.json`; the claim is an atomic file creation, never a read followed by a write. Take liveness from `Get-AgentTicketLiveness` in `scripts/utils/agent-lock.ps1` - do not restate the rule. Sweep stale leases on every read path. Keep the exit codes identical: 0 done, 1 error, 3 claim lost, 4 release refused, and document them in the header per CLAUDE.md Rule 7.

**Why:**

Strategic ADR-1 chose to repeat the accepted form rather than invent a mechanism, so that callers already fluent in the ticket lease's exit codes need learn nothing new, and §3.2 forbids a third copy of the liveness rule because the two that exist would then have a third to drift from.

**Verification:**

- `Glob` - `scripts/devtest/device-lease.ps1` exists.
- `Grep` - the file references `Get-AgentTicketLiveness` and contains no independent staleness arithmetic.
- `Grep` - the header documents exit codes 0, 1, 3 and 4.
- Run: `pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Status` - expected: exit 0 on an empty store.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 01.2

---

### Step 01.3 - Prove the four outcomes

**Files:** `scripts/devtest/device-lease.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Exercise, against a fake serial so no hardware is involved: claim succeeds (0); re-claiming the same lease from the same session succeeds and does not error (0); `Status` names the holder; release succeeds (0); releasing an unheld lease behaves as the ticket lease does. Then forge a lease file owned by a foreign session id and confirm claim returns 3 and release returns 4. Record every exit code here.

**Why:**

Strategic §11.1-§11.3 are four separate claims about four exit codes, and a lease that returns 0 for everything looks identical to a working one until two sessions take one device - which is the failure this ticket exists to prevent.

**Verification:**

- Recorded in this file: the exit code observed for each of the six operations.
- `Glob` - no forged lease file remains under `temp/DEVICE.LEASES/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 01.3

---

### Step 01.4 - Prove the sweep

**Files:** `scripts/devtest/device-lease.tests/Run-Tests.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a test file asserting that a lease whose owning session is long dead is dropped by the next read, and that a lease belonging to a live session is not. Drive it through the script's own verbs rather than by re-implementing the staleness check in the test.

**Why:**

Strategic §11.4 requires eviction without a watchdog, and §7 rates "claimed and never released" the highest-probability risk - if the sweep silently stops working, a killed session holds a device until someone notices by hand.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/devtest/device-lease.tests/Run-Tests.ps1` - expected: exit 0, both cases reported passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 01.4

---

## Evidence (2026-08-21)

**Step 01.1** - `Get-AgentLockTimings -Name Device` returns `window=45 ceiling=120`, exit 0. The window matches the ticket lease; the ceiling is a quarter of it, per §6.2.

**Step 01.3 - every exit code observed, against fake serials so no hardware was touched.**

| # | Operation | Exit | Expected |
| --: | --- | --: | --: |
| 1 | claim a free device | 0 | 0 |
| 2 | re-claim the same lease, same session | 0 | 0 |
| 3 | `Status` names the held serial | - | names it |
| 4 | release own lease | 0 | 0 |
| 5 | release a lease that is not held | 0 | 0 |
| 6 | claim `../escape` as a serial | 1 | 1 |
| 7 | claim a device held by a live foreign session | 3 | 3 |
| 8 | release a device held by a live foreign session | 4 | 4 |

Rows 7 and 8 printed the holder: `already claimed by session 00000000-dead-beef-.. on OTHERHOST` and `refusing to release .. - live session .. owns it`. Row 6 is not in the strategic criteria but is load-bearing anyway: a serial becomes a file name, so a path-climbing serial must be refused before it is used as one.

**Step 01.4 - the sweep, through the script's own verbs.** `device-lease tests: PASS (stale lease swept, live lease kept, swept device re-claimable)`, exit 0. The third assertion is the one worth having: eviction that left the serial unclaimable would trade a stuck lease for a stuck device.

The lease store is empty after every probe: `leases left: 0`.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable: no Kotlin, no build file.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The lease exists and arbitrates correctly, but nothing consults it yet - the readiness probe still answers `multiple-devices`. That is Phase 02.

---

## Rollback Plan

Delete the new script, its tests and the `Device` timings entry - no existing caller reads any of them until Phase 02.
