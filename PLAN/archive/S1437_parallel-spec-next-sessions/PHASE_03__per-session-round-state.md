# Phase 03 - Per-session round state

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Give every session its own round-state file and remove the initialisation refusal, so a second picker session starts normally instead of being turned away.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Phase 02 is live - the lease filter is what replaces the refusal's protection.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/spec-next-session.ps1` | Modified | ≤ 600 |

> Backup / split thresholds: 493 LOC - close to the 500 LOC line and expected to cross it, so step 03.1 carries an explicit backup sub-step. The 1500 LOC ceiling is not in reach.
>
> **Budget revised during implementation (2026-08-06):** 540 -> 600. The original figure did not allow for the `handoffAt` park-marker mechanism, which the amendment below shows was not optional - without it `--resume` either loses the round or steals a sibling's. Actual 581 LOC, well inside CLAUDE.md Rule 2's 1500 ceiling.

---

## Steps

### Step 03.1 - Key the state file to the session

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `scripts/spec_catalog/spec-next-session.ps1` to `temp/S1437/` with a timestamped name first - the file is 493 LOC and this phase pushes it past 500.
> Replace the single hardcoded `spec-next-session.json` filename with a per-session path `temp/spec-next-session.<sessionId>.json`, keeping the directory derivation from `$PSScriptRoot` as it is.
> When `CLAUDE_CODE_SESSION_ID` is absent, fall back to the same `pid-<PID>` identity `agent-lock.ps1` already uses, so the path is always resolvable.
> On `-Verb Resume` and `-Verb Init`, adopt a legacy `temp/spec-next-session.json` if one exists and no per-session file does: read it, write it to the new path, delete the old one. A session mid-round when this ships must not lose its round.

**Why:**

Strategic goal 5 requires the round state to stop being a shared resource and belong to its own session, and §1 names the single shared file as the first of the three blockers. The legacy adoption exists because §3.2 requires a single session to keep working exactly as before, with no new mandatory steps - a session that loses its round on upgrade would violate that.

**Verification:**

- `Grep` - `spec-next-session\.json` no longer appears as a bare filename literal; the path expression includes the session id.
- `Grep` - `pid-` fallback present.
- `Glob` - a timestamped backup exists under `temp/S1437/`.
- With a legacy `temp/spec-next-session.json` in place, run `-Verb Resume`, confirm the per-session file now exists, the legacy file is gone, and `round` and `processed` survived.
- Resume across a context reset: `Init` + `Record` + `Handoff` under session id A, then `Resume` under session id B - the round is recovered, `processed` survives, `previousOwner` names A.
- Live sibling protection: `Init` + `Record` under a live session, then `Resume` from a different session id with no `Handoff` - exit 1, and the sibling's state file is untouched.

**Status:** `[x]` done

---

### Step 03.2 - Remove the initialisation refusal

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Delete the `foreign-live` branch in `-Verb Init` that writes the refusal message and exits 4. With per-session files there is no foreign owner to collide with: a second session initialises its own file.
> Keep `-Force` accepted as a no-op switch with a deprecation note in the header rather than removing it, so existing invocations that pass it do not start failing on an unknown parameter.
> Update the header's exit-code list: `4` is no longer returned by this script. Do not renumber the remaining codes.

**Why:**

§1 records that this refusal is a deliberate stub placed against exactly what the owner now wants, and §10 states it may be removed only together with the lease, which Phase 02 delivered. Keeping `-Force` as an accepted no-op protects §3.2's compatibility requirement, since a stored command line carrying it must not break.

**Verification:**

- `Grep` - `refusing to overwrite state owned by` returns zero hits in the file.
- `Grep` - `exit 4` returns zero hits in the file.
- `Grep` - `-Force` still declared in the param block and mentioned in the header as a no-op.
- Run `-Verb Init` twice from two shells carrying different `CLAUDE_CODE_SESSION_ID` values - both exit 0, and two distinct state files exist.

**Status:** `[x]` done

---

### Step 03.3 - Scope reporting to the session's own state

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Point `-Verb Report` and `-Verb Handoff` at the per-session path so each session reports its own round, not a merged view. Their existing single-owner assumptions then hold unchanged.
> In `-Verb Handoff`, keep the existing call that previews the next queue entry via `spec-next-preflight.ps1`, and pass this session's processed ids as `-Exclude` exactly as today - Phase 02's lease filter handles the sibling sessions on top of that.

**Why:**

Strategic goal 5 makes the round state a per-session resource, and these two verbs are the only readers that assume there is exactly one owner for the whole file. Leaving them pointed at a merged view would report another session's processed tickets as this session's work.

**Verification:**

- `Grep` - no remaining reference to a state path that omits the session id.
- Run `-Verb Record` for one ticket in session A, then `-Verb Report` in session B - session B's report shows zero processed, not session A's ticket.
- Run `-Verb Handoff` and confirm it exits 0 and names a next ticket.

**Status:** `[x]` done

---

### Step 03.4 - Consume the shared liveness helper instead of the local copy

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Replace the body of the local `Get-OwnerCheck` with a call into `Get-AgentTicketLiveness` from `scripts/utils/agent-lock.ps1`, dot-sourcing that file as `ticket-lease.ps1` does. Keep `Get-OwnerCheck`'s own name and its `none` state - the shared helper has no `none`, so map an absent state file to `none` before delegating, and pass the remaining four verdicts through unchanged.
> Delete the now-unused local transcript-lookup helper if nothing else in the file calls it.

**Why:**

§5.1 states the liveness rule must not be written again because a third copy would drift from the two that exist, and this phase is already rewriting the file that holds one of them, so consolidating here costs nothing extra. Keeping the `none` state local is required because it describes an absent file rather than a session's liveness, which is not something the shared helper models.

**Verification:**

- `Grep` - `Get-AgentTicketLiveness` matches in `spec-next-session.ps1`.
- `Grep` - the local implementation's transcript-write-time comparison no longer appears in this file.
- `Grep` - `'none'` still returned by `Get-OwnerCheck`.
- Run `-Verb Init`, then `-Verb Report` in the same session - the owner check reports `self`, exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Two shells with different `CLAUDE_CODE_SESSION_ID` values both run `-Verb Init` successfully.
- [x] A single session with no sibling behaves exactly as before this phase - §3.2 compatibility.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Amendment during implementation (2026-08-06) - the resume-across-reset defect

Per-session state files broke `--resume`, which is the whole reason the state file exists. A context reset gives the resuming agent a **new** session id, so the round being resumed is always filed under the **old** one - and that old session's transcript was written seconds ago, so every liveness test reads it as a live foreigner. The plan's step 03.1 assumed a session could find its own file; after a reset it never can.

Liveness cannot separate the two cases, because they are identical to it:

- a round stopped for a context reset, waiting to be picked up;
- a sibling session working its own round right now.

Adopting on `foreign-live` steals the sibling's round. Refusing on `foreign-live` loses the reset round. Neither is acceptable, and no threshold fixes it - the signal is simply not in the liveness data.

Resolution, added to this phase: `-Verb Handoff` - which the threshold stop already calls before recommending the reset - stamps `handoffAt` on the state, meaning "parked, waiting for pickup". `-Verb Resume` adopts the newest candidate that is either stamped **or** owned by a genuinely stale session (which covers a crash that never reached `Handoff`), and never an unstamped live one. `Resume` clears the stamp so a third session cannot adopt the round out from under the one that just took it. `-Verb Init` does not adopt at all: it means a fresh round and overwrites state anyway.

Both cases are covered by the verification below.

---

## Handoff Notes to Next Phase

The refusal is gone, so Phase 05's driver text must stop instructing the agent to stop and report on exit 4. Round state is per session; the holder map across sessions comes from `ticket-lease.ps1 -Verb Status`, not from this script. Only one liveness implementation remains outside `agent-lock.ps1`, and it is a thin adapter.

---

## Rollback Plan

Revert the single modified script and restore the timestamped backup from `temp/S1437/`. A per-session state file left behind is inert - the reverted script ignores it and falls back to the legacy path.
