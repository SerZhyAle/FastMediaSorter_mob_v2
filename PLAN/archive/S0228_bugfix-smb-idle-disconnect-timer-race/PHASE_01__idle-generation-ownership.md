# Phase 01 — Idle generation ownership (Pillar A)

**Strategic spec:** [`../S0228_bugfix-smb-idle-disconnect-timer-race.md`](../S0228_bugfix-smb-idle-disconnect-timer-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Replace the shared idle policy's implicit "last timer wins" assumption with explicit per-transport ownership state so every rearm has a unique generation token and later phases can suppress stale callbacks without touching SMB call sites yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none for this phase).
- [ ] Strategic §6.1 is Resolved — generation token is the chosen ownership primitive.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` remains under 500 LOC after this phase (backup not required).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` | Modified | ≤ 160 |

---

## Steps

### Step 01.1 — Introduce explicit timer state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the current `TimerEntry` ownership model with `private data class TimerState(val generation: Long, val idleMs: Long, val callback: suspend () -> Unit)`. Keep the `Job` map if it is still the smallest implementation, but the latest state for each transport must be stored explicitly in a `ConcurrentHashMap<String, TimerState>`. Do not change the public `IdleDisconnectPolicy` interface and do not add protocol-specific branches.

**Verification:**

- `Grep` — `private data class TimerState` matches exactly once in `IdleDisconnectPolicyImpl.kt`.
- `Grep` — `val generation: Long` matches exactly once in `IdleDisconnectPolicyImpl.kt`.
- `Grep` — `ConcurrentHashMap<String, TimerState>` matches exactly once in `IdleDisconnectPolicyImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` (+7 LOC, TimerState introduced, explicit state map renamed). Dev log recorded.

---

### Step 01.2 — Mint a fresh generation on every rearm

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a private helper `nextGeneration(transport: String): Long` and call it from both `arm` and `touch` before rescheduling the timeout. `arm` stores the latest callback and idle window together with the fresh generation; `touch` reuses the stored callback and idle window, but still advances generation so any older job loses ownership. Keep the existing `touch ignored` fast return when the transport has no live state.

**Verification:**

- `Grep` — `private fun nextGeneration` matches exactly once in `IdleDisconnectPolicyImpl.kt`.
- `Grep` — `nextGeneration(` matches at least 2 times in `IdleDisconnectPolicyImpl.kt`.
- `Grep` — `IdleDisconnect: touch ignored` still matches exactly once in `IdleDisconnectPolicyImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` (+14 LOC, monotonic generation helper added and wired into arm/touch). Dev log recorded.

---

### Step 01.3 — Gate timeout firing by latest generation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Update the scheduling helper so each launched job captures the transport generation at schedule time, re-reads the current state after `delay`, and returns early when the current state is missing or generation-mismatched. Only the latest generation may remove state, log `IdleDisconnect: timeout fired`, and invoke the timeout callback. Stale generations must log `IdleDisconnect: stale timeout dropped` at `Timber.d` and exit without side effects.

**Verification:**

- `Grep` — `IdleDisconnect: stale timeout dropped` matches exactly once in `IdleDisconnectPolicyImpl.kt`.
- `Grep` — `IdleDisconnect: timeout fired` still matches exactly once in `IdleDisconnectPolicyImpl.kt`.
- `Grep` — `states.remove(` matches at least once in `IdleDisconnectPolicyImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` (+12 LOC, stale-generation suppression added in restartTimer, file diagnostics clean). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `./build-debug.PS1` PASS (`assembleStandardDebug`, 39s, 2026-05-16).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep` for `IdleDisconnect: stale timeout dropped` returns exactly one match in `IdleDisconnectPolicyImpl.kt`.
- [x] `Grep` for `Log\.d\(` returns zero hits in `IdleDisconnectPolicyImpl.kt`.
- [x] Dev log entry added for `IdleDisconnectPolicyImpl.kt` via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 04.

---

## Handoff Notes to Next Phase

After Phase 01, the shared idle layer owns exact generation state per transport-key. The SMB path in Phase 02 must stay blind to timer generations and accept only a single callback entrypoint from the shared layer.

---

## Rollback Plan

Revert the Phase 01 commit(s) — no public API change, no Room/Hilt/UI impact.