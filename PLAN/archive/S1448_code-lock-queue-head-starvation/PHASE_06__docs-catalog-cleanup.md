# Phase 06 - Documentation and closure

**Strategic spec:** [`../S1448_code-lock-queue-head-starvation.md`](../S1448_code-lock-queue-head-starvation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Bring the written lock contract in line with the shipped behaviour and close the ticket through the mechanical facade.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 40 changed lines |

---

## Steps

### Step 06.1 - Update the "Concurrent-agent locks" contract

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Concurrent-agent locks (BUILD.LOCK / CODE.LOCK)" section state four changed facts: acquiring a lock retires every queue ticket of the acquiring session; the turn is decided by ticket identity, so a caller holding no ticket never inherits its own session's head; a ticket's liveness is its own `lastSeenAt` heartbeat first and the session transcript second, while the absolute ticket ceiling is unchanged and un-extendable; and `lock-status.ps1 -Queue` marks a ticket held by the current lock holder, with `headOwnedByHolder` in the JSON payload. Add S1448 alongside the existing S1432 / S1437 attributions.

**Why:**

Strategic §2.5 requires the pathology be discoverable, and the section is the only written description of the queue contract - a reader following the pre-S1448 text would conclude a starving session is behaving normally.

**Verification:**

- `Grep` - `S1448` matches at least once in `docs/DEV_OPS.md`.
- `Grep` - `headOwnedByHolder` matches at least once in that file.
- `Grep` - `lastSeenAt` matches at least once in that file.

**Status:** `[x]` done

---

### Step 06.2 - Document the lease liveness signal

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> In the "Parallel picker sessions (S1437)" bullet on `ticket-lease.ps1`, record that a lease is also considered live while its owning session holds `CODE.LOCK` or `BUILD.LOCK` with a reason naming that ticket id, and that the 480-minute ceiling still applies on top.

**Why:**

Strategic §2.6 requires a lease not to expire under a session demonstrably working the ticket, and strategic §4 Д5 shows the preflight offering an actively-worked ticket to a sibling when that rule was absent.

**Verification:**

- `Grep` - the S1437 bullet block in `docs/DEV_OPS.md` mentions holding a lock as a liveness signal.
- `Grep` - `480` still matches in that block, proving the ceiling statement survives.

**Status:** `[x]` done

---

### Step 06.3 - Close through the facade

**Files:** `scripts/post-change.ps1` (invoked, not edited)
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file changed across phases 01-06>" -Target "S1448" -Description "CODE.LOCK queue fairness: retire session tickets on acquire, ticket-owned turn, waiter heartbeat, honest refusal, lease liveness from lock ownership" -ChangeType Mixed -ScopeToFile` and read its verdict. Record the exact verdict line and exit code. Add the capability record only if the run surfaces a user-visible capability, which this ticket does not have - strategic §8 reads "Без изменений".

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade so the gates run before the changelog row is written, and strategic §11 criterion 10 names the exit-contract gate specifically because this ticket changes script exit-code documentation.

**Verification:**

- Run - `scripts/post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES`.
- `Grep` - `dev/CHANGELOG.md` contains an entry naming S1448.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 06.1 Verification 3/3 PASS. `docs/DEV_OPS.md` gained a "Queue fairness and liveness (S1448)" block stating all four changed facts, plus the `lock-status.ps1` diagnostic fields.
- 2026-08-07 - Step 06.2 Verification 2/2 PASS. The S1437 lease bullet now records `lastSeenAt` and lock-ownership-as-liveness; the 480-minute ceiling sentence survives.
- 2026-08-07 - Step 06.3 Verification 2/2 PASS. `post-change.ps1 -ChangeType Doc -ScopeToFile -RegistryAck 'developer-operations'`: `post-change: PASS`, exit 0, no advisories. `dev/CHANGELOG.md` carries 8 rows naming S1448.
- 2026-08-07 - The first closure pass returned `PASS WITH ADVISORIES (2)`: the script cheatsheet was stale (this ticket added `test-agent-lock-queue.ps1`) and `docs/DEV_OPS.md` is a registered document needing acknowledgement. Cheatsheet regenerated (274 scripts); registry siblings scanned for lock-contract text - only `AGENT_COST_PLAYBOOK.md` mentions parallel sessions and it does so about fan-out cost policy, not the lock queue, so no sibling needed the same edit.
- 2026-08-07 - No capability record in `docs/ALL_FEATURES.jsonl`: strategic §8 reads "Без изменений в docs/FEATURES" - this is agent infrastructure with no user-visible surface.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` untouched - strategic §8 reads "Без изменений в docs/FEATURES".
- [x] `dev/CATALOG` regeneration skipped - no Kotlin changed by this ticket.
- [x] `pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1` still exits 0 after the documentation edits.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the `docs/DEV_OPS.md` edit. No code or data is touched by this phase.
