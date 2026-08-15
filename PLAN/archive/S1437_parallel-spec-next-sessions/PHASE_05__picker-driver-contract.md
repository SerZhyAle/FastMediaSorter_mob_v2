# Phase 05 - Picker driver contract

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Make the picker driver claim a ticket before working it, retry on a lost claim, release on completion, and report a busy queue distinctly from an exhausted one.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `ticket-lease.ps1` returns exit 3 on a lost claim.
- [ ] `spec-next-preflight.ps1` emits `selected_none_reason`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-next.md` | Modified | ≤ 260 |
| `.claude/reference/spec-next.md` | Modified | ≤ 250 |
| `.claude/commands/spec-do.md` | Modified | ≤ 30 |
| `docs/DEV_OPS.md` | Modified | ≤ 640 |

> Backup / split thresholds: `docs/DEV_OPS.md` is 585 LOC - over the 500 LOC line, so step 05.4 carries an explicit backup sub-step. No source file is touched in this phase.

---

## Steps

### Step 05.1 - Claim the selected ticket before delegating

**Files:** `.claude/commands/spec-next.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Insert a claim step between Stage 3 (drift gate) and Stage 4 (delegate to `/spec-all`): call `scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id <selected.id> -Reason "/spec-next"`.
> Exit 0 - proceed to Stage 4 as today.
> Exit 3 - a sibling took it first. Add the id to the in-memory `processed` set and re-run Stage 1 with the updated `-Exclude`. Log one line `[claim-lost] <id> - held by <session>`. Do not treat it as an error and do not stop the round.
> State explicitly that the claim happens after the drift gate, not before: a ticket deferred for manual drift review must not be left leased.

**Why:**

ADR-3 keeps the ranker read-only and puts the claim in the caller, so the claim is what actually arbitrates between two sessions that ranked the same top ticket. Research artifact 02 records that losing a claim is the normal contended path, resolved by re-picking rather than by waiting, which is why it must not stop the round.

**Verification:**

- `Grep` - `ticket-lease.ps1 -Verb Claim` matches in `.claude/commands/spec-next.md`.
- `Grep` - `claim-lost` matches.
- `Grep -n` - the claim block appears after the `### Stage 3` heading and before the `### Stage 4` heading.

**Status:** `[x]` done

---

### Step 05.2 - Release the lease when the ticket leaves the loop

**Files:** `.claude/commands/spec-next.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> In Stage 5, where the round records an outcome via `spec-next-session.ps1 -Verb Record`, add a release call `ticket-lease.ps1 -Verb Release -Id <id>` for every outcome - advanced, verified, blocked and skipped alike. A blocked ticket must not stay leased, or no sibling can pick it up.
> Add the same release to the drift-defer path in Stage 3, which adds the id to `processed` without ever reaching Stage 5.
> State that a failed release is logged and does not stop the round: the lease expires on its own by liveness, so a missed release costs a delay, not a stuck ticket.

**Why:**

Strategic goal 2 only holds while a lease reflects work actually in progress; a lease outliving its ticket removes that ticket from every sibling's candidate set for the whole staleness window. The tolerant failure handling follows §5.1, where expiry by session liveness is the backstop that makes an unreleased lease self-correcting.

**Verification:**

- `Grep` - `ticket-lease.ps1 -Verb Release` matches at least twice in `.claude/commands/spec-next.md`.
- `Grep -n` - one release call sits in the Stage 5 record block and one in the Stage 3 drift-defer block.
- `Grep` - the four outcome names `advanced`, `verified`, `blocked`, `skipped` all appear in the release instruction's scope sentence.

**Status:** `[x]` done

---

### Step 05.3 - Replace the refusal instruction and report exhaustion distinctly

**Files:** `.claude/commands/spec-next.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> In Stage 0, delete the paragraph instructing the agent to stop and report when `-Verb Init` exits 4, including the "never `-Force` past it unsupervised" sentence. Replace it with one sentence stating that parallel picker sessions are supported and each session gets its own round state.
> In Stage 1, replace the flat rule `selected == null -> eligible set exhausted -> final report and stop` with a branch on `selected_none_reason`: `queue-exhausted` and `no-candidate` keep today's behaviour, while `all-leased` reports which sibling holds each remaining ticket, states that re-running later will pick one up, and stops without waiting.
> Keep the `--resume` guidance intact - it is still the right verb after a threshold `/clear`.

**Why:**

§1 records the refusal as a deliberate stub against exactly what the owner now wants, and Phase 03 removed the mechanism behind it, so an instruction telling the agent to stop on a code that is no longer returned would be dead guidance. Criterion 3 requires the busy and exhausted cases to read differently, and ADR-4 rules out waiting as the response to a busy queue.

**Verification:**

- `Grep` - `never .Force past it unsupervised` returns zero hits.
- `Grep` - `exiting \*\*4\*\*` returns zero hits in the Stage 0 session-state paragraph.
- `Grep` - `all-leased` and `queue-exhausted` both match in `.claude/commands/spec-next.md`.
- `Grep` - `--resume` still present in Stage 0.

**Status:** `[x]` done

---

### Step 05.4 - Document the lease family and the new payload fields

**Files:** `.claude/reference/spec-next.md`, `docs/DEV_OPS.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Back up `docs/DEV_OPS.md` to `temp/S1437/` with a timestamped name first - the file is 585 LOC.
> In `.claude/reference/spec-next.md`, extend the "Preflight payload field contract" with `leased_ids` and `selected_none_reason`, giving each field its type, its possible values and what the driver does with it.
> In `docs/DEV_OPS.md`, extend the "Concurrent-agent locks" section with the ticket lease: where the registry lives, that a claim is an atomic file creation and a lost claim is normal, that expiry follows session liveness with an independent ceiling, that release is owner-checked, and that `ticket-lease.ps1 -Verb Status` is the one command showing who holds what. Note that the round state is now per session and the old single-file refusal is gone.

**Why:**

Strategic goal 6 requires the holder map to be discoverable, and the document registry lists `docs/DEV_OPS.md` under the `developer-operations` record as the maintained home for this family - the two existing locks are already documented there, so a third mechanism living only in a script header would be the one nobody finds.

**Verification:**

- `Grep` - `leased_ids` and `selected_none_reason` both match in `.claude/reference/spec-next.md`.
- `Grep` - `ticket-lease.ps1` matches in `docs/DEV_OPS.md`.
- `Grep` - `Concurrent-agent locks` section in `docs/DEV_OPS.md` mentions the lease and the per-session round state.
- `Glob` - a timestamped backup of `DEV_OPS.md` exists under `temp/S1437/`.

**Status:** `[x]` done

---

### Step 05.5 - State the parallel-session contract in the `/spec-do` driver

**Files:** `.claude/commands/spec-do.md`
**Depends on:** Step 05.4

**Prompt for developer:**

> Add one sentence stating that several `/spec-do` and `/spec-next` sessions may run at once against one working tree, that each takes a different ticket through the lease, and that the stage text it inherits from `spec-next.md` already carries the claim and release calls.
> Do not restate the claim or release commands here - the file defers to `spec-next.md` by design and duplicating them would create a second place to update.

**Why:**

The owner's captured request in §0 names `/spec-do` and `/spec-next` together, so a reader of the `/spec-do` driver must find the parallel-session answer without first inferring that the file inherits stages from elsewhere.

**Verification:**

- `Grep` - `parallel` or `at once` matches in `.claude/commands/spec-do.md`.
- `Grep` - `ticket-lease.ps1` returns zero hits in `.claude/commands/spec-do.md` - the deferral is preserved.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] No Kotlin or resource file touched - no gradle build applies to this phase.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The full loop is live: rank, claim, work, release. Phase 06 regenerates the cheatsheet and closes the documentation surfaces. The document-registry records touched by this phase are `repository-rules` (the `.claude/**` drivers) and `developer-operations` (`docs/DEV_OPS.md`).

---

## Rollback Plan

Revert the four documentation and driver files and restore the `DEV_OPS.md` backup from `temp/S1437/`. The scripts from Phases 01-04 stay in place and are simply not called by the driver.
