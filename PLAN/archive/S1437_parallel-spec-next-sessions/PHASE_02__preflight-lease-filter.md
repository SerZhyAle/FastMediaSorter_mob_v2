# Phase 02 - Preflight lease filter

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Teach the ranker to skip tickets leased by live sibling sessions and to distinguish "every candidate is taken" from "the queue is empty", without giving up its read-only contract.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `ticket-lease.ps1 -Verb List -Json` returns a parseable array.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/spec-next-preflight.ps1` | Modified | ≤ 320 |

> Backup / split thresholds: 261 LOC - under the 500 LOC line, no backup step required.

---

## Steps

### Step 02.1 - Read live leases as a fourth exclusion source

**Files:** `scripts/spec_catalog/spec-next-preflight.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Shell out to `scripts/spec_catalog/ticket-lease.ps1 -Verb List -Json` the same way the script already shells out to `skip-cache.ps1 -Action list`, and build a set of ids leased by sessions other than this one. A lease owned by this session must not exclude its own ticket, otherwise a resuming session cannot get back to its own work.
> Apply the set inside the existing drop loop that currently handles the skip-cache and `-Exclude` sets, as a third `continue` beside them, and collect what was dropped into a new `$leasedIds` array.
> Leave the five sort keys untouched. If the lease script is missing or fails, treat the lease set as empty and carry on - the picker must still work when the store is unavailable.

**Why:**

Strategic goal 2 requires that a ticket held by a live session is not offered to another, and §5.1 places that as an extra exclusion source next to the existing one specifically so the owner's release-plan order is not disturbed. Constraint §3.2 "Порядок владельца" makes the sort keys off-limits: only the candidate set may change.

**Verification:**

- `Grep` - `ticket-lease.ps1` matches in `spec-next-preflight.ps1`.
- `Grep` - `leasedIds` matches at least twice (collection and output).
- `Grep -n` - the `Sort-Object` block still contains exactly the five original expressions, unchanged.
- Claim `S9997` from a second shell with a different `CLAUDE_CODE_SESSION_ID`, run the preflight, confirm `S9997` appears in `leased_ids` and is not `selected`; release it.

**Status:** `[x]` done

---

### Step 02.2 - Report leased ids and name their holders in the payload

**Files:** `scripts/spec_catalog/spec-next-preflight.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `leased_ids` to the result object next to `skip_cached_ids`, `excluded_ids` and `auto_skipped`, carrying one entry per dropped ticket with its id, owning session id and how long ago that session was last seen.
> Add the same rows to the `-Format table` console summary, so a human running the preflight by hand sees who holds what without a second command.

**Why:**

Criterion 3 requires that when nothing is free the answer names the holders, and research artifact 02 records that a bare "no candidate" would tell the owner the queue is finished while three siblings are working it. The payload is the channel because this family's convention puts the verdict in the structured output rather than in an exit code.

**Verification:**

- `Grep` - `leased_ids` matches inside the `$result = [PSCustomObject]@{` literal.
- Run the preflight with one foreign lease held and `-Format json`, pipe through `ConvertFrom-Json`, confirm `leased_ids` is a non-empty array whose first element has `id` and `sessionId` properties.
- Run with `-Format table` and confirm the leased row is printed.

**Status:** `[x]` done

---

### Step 02.3 - Distinguish "all leased" from "queue exhausted"

**Files:** `scripts/spec_catalog/spec-next-preflight.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> When `selected` is null, add a `selected_none_reason` field to the payload taking one of `queue-exhausted` (no eligible ticket existed before any lease filtering) or `all-leased` (eligible tickets existed and every one of them was dropped by the lease filter). Any other null-selection path - everything skip-cached, everything auto-skipped, walk cutoff - keeps the existing meaning and reports `no-candidate`.
> Print the distinction in the `-Format table` summary as a single explicit line.
> Exit code stays `0` for all three - a busy queue is an answer, not a failure.

**Why:**

Criterion 3 makes the two states separately observable, and ADR-4 turns them into different next moves for the caller: an exhausted queue means the work is done, while an all-leased queue means the work is taken and re-running later gets a ticket. Research artifact 02 records that collapsing them would report the queue as finished while siblings are still working it.

**Verification:**

- `Grep` - `selected_none_reason` matches in `spec-next-preflight.ps1`.
- `Grep` - all three literals `queue-exhausted`, `all-leased` and `no-candidate` match.
- With every eligible ticket leased by a foreign session, run the preflight and confirm `selected` is null, `selected_none_reason` is `all-leased`, and exit code is 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Format json` exits 0 and its output parses as JSON.
- [x] With no lease held, the selected ticket is byte-identical to the pre-change run - single-session behaviour unchanged per §3.2.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The ranker still claims nothing - the claim is the caller's move, per ADR-3, and Phase 05 makes it. `selected_none_reason` is the field Phase 05 branches on when reporting exhaustion. With this phase in, two sessions no longer collide on the same top ticket, which is the precondition Phase 03 needs before it removes the refusal.

---

## Rollback Plan

Revert the single modified script. Phase 01's store keeps working and is simply unread.
