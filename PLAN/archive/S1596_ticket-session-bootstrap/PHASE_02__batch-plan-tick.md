# Phase 02 - Batch tactical-plan tick

**Strategic spec:** [`../S1596_ticket-session-bootstrap.md`](../S1596_ticket-session-bootstrap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Introduce `scripts/spec_catalog/plan-tick.ps1`, which moves an explicit list of steps to a target state in one operation across both plan surfaces and all four state shapes, and route `/spec-dev` through it.

---

## Prerequisites

- [x] Strategic §6 items 1 and 4 are Resolved - read `research/01__index-edit-composition.md` and `research/04__execution-trace-channel.md` before writing the script.
- [x] `research/00__as-is-chain-and-tick-mechanics.md` §5 read - it carries the literal shape of every state marker and names the machine-read INDEX field.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/plan-tick.ps1` | New | ≤ 380 |
| `scripts/spec_catalog/plan-tick.tests/Run-Tests.ps1` | New | ≤ 320 |
| `.claude/commands/spec-dev.md` | Modified | ≤ 50 changed |
| `.claude/reference/spec-dev.md` | Modified | ≤ 30 changed |

---

## Steps

### Step 02.1 - Step-state engine over the phase file

**Files:** `scripts/spec_catalog/plan-tick.ps1`

**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/plan-tick.ps1` with the house header shape (comment-based help ending in `.EXIT CODES`, then `param(..)`, then `$ErrorActionPreference = 'Stop'`).
>
> Parameters: `-Id` (Sxxxx, mandatory), `-Phase` (two-digit phase number, mandatory), `-Steps` (string, comma-separated step numbers such as `3,4,5` or full ids such as `02.3`), `-State` with the set `NotDone`, `InProgress`, `Done`, `Manual`, `-Note` (string, appended after `done` or carried as the body of the manual marker), `-Json`.
>
> Rewrite the `**Status:**` inline marker of exactly the listed steps in `PLAN/<Id>_<slug>/PHASE_<Phase>__*.md`, mapping states to the literal forms already in use: `` `[ ]` not done ``, `` `[~]` in progress ``, `` `[x]` done ``, `` `[manual - deferred]` <note> ``. Preserve any trailing prose on a `done` marker unless `-Note` replaces it.
>
> There is no form that means "every step in the phase". A step number not present in the file is an error, not a silent no-op. Exit codes: `0` all listed steps rewritten; `1` a listed step was not found or the file could not be written; `2` usage error or the plan folder does not exist.

**Why:**

Strategic ADR-2 forbids a whole-phase form because the cost of one false tick exceeds the cost of the edits it saves, and research 04 shows the explicit step list is simultaneously the execution trace - a short form would delete the trace along with the safety.

**Verification:**

- `Glob` - `scripts/spec_catalog/plan-tick.ps1` exists.
- `Grep` - `NotDone`, `InProgress`, `Done` and `Manual` all match in the `ValidateSet`.
- `Grep` - no parameter or switch whose name contains `AllSteps`, `Phase.*All` or `-All` exists.
- Round-trip on a scratch copy: setting three steps to `Done` then back to `NotDone` restores the file byte for byte. (True as of this step; step 02.4 adds a Step Log entry on `Done`, after which the round trip restores every marker exactly and deliberately keeps the log.)
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 5/5 PASS. Files: scripts/spec_catalog/plan-tick.ps1 (+218 LOC). Round-trip on a disposable PLAN/S9991_plan-tick-probe fixture was byte-identical, trailing prose on an already-done step survived the batch, and an unknown step number exited 1 having written nothing. assert-exit-contract PASS. This step's own marker was then flipped by the tool itself.

---

### Step 02.2 - Index counters and consistency check

**Files:** `scripts/spec_catalog/plan-tick.ps1`

**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the same operation to `PLAN/<Id>_<slug>/INDEX.md`: recompute the phase row's `Steps` cell from the phase file's actual marker counts rather than incrementing it, flip the row `Status` cell to `✅ Done` when every step in that phase is done, recompute the header `**Phases:** X / N done`, and set `**Last updated:**` to today.
>
> Recompute, never increment - the counter must equal what the phase file says even if a previous edit was made by hand.
>
> If the index and the phase file disagree before the write, report the disagreement in the output and in a dedicated exit code, and write nothing at all - a divergence report issued after half the write has landed would be a report about damage this script just did. Recompute the phase file's own `**Steps done:**` header in the same pass, otherwise fixing the index only swaps one divergence for another.

**Why:**

Strategic §3.2 records that `**Last updated:**` is read mechanically by the drift check, so a writer that stops maintaining it fixes cost and breaks a gate, and strategic §11 criterion 5 requires the two surfaces to agree after the operation because one operation now writes both.

**Verification:**

- `Grep` - `Last updated` matches in `plan-tick.ps1`.
- `Grep` - `Phases:` matches in `plan-tick.ps1`.
- On a scratch plan copy whose index says `2/6` while the phase file has 3 done markers: the run reports the disagreement and exits non-zero.
- After a successful run on a consistent scratch copy, the index `Steps` cell equals the phase file's done count.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 4/4 PASS. On a disposable PLAN/S9991 fixture a two-step batch drove the index row to `✅ Done 3/3`, the header to `**Phases:** 1 / 2 done`, `**Last updated:**` to today and the phase header to `3 / 3`. Injecting a `1/3` index against a `3/3` phase file produced exit 3 with the phase file byte-identical. Two things beyond the prompt: exit code 3 was added for the divergence case, and the phase file's own `**Steps done:**` header is recomputed too. A `Measure-DoneMarkers` binding bug surfaced first - a mandatory `[string[]]` rejects the blank lines every markdown file is full of - and was fixed with `AllowEmptyString`.

---

### Step 02.3 - GFM checkbox support

**Files:** `scripts/spec_catalog/plan-tick.ps1`

**Depends on:** Step 02.2

**Prompt for developer:**

> Add `-Checkbox` (string, comma-separated label fragments) which ticks ordinary `- [ ]` / `- [x]` bullets by matching the fragment against the bullet text, in either the phase file or `INDEX.md` depending on `-Target` (`Phase` default, `Index`).
>
> Match must be unambiguous: a fragment matching zero or more than one bullet is an error naming the candidates, never a guess. `-Checkbox` and `-Steps` are mutually exclusive in one invocation.

**Why:**

Research 01 measured 236 GFM-checkbox edits a week across prerequisites, done criteria and blockers, which is the second-largest bookkeeping class, so a ticker covering only the inline step marker would leave most of one surface untouched.

**Verification:**

- `Grep` - `-Checkbox` matches in the param block.
- A fragment matching two bullets on a scratch copy exits non-zero and prints both candidates.
- A unique fragment flips exactly one bullet and leaves every other line unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. -Checkbox added as its own parameter set: a unique fragment flips exactly one bullet, an ambiguous one exits 4 naming every candidate line and writes nothing, and -Target Index works without -Phase. GFM states are limited to NotDone/Done because a bullet has no in-progress form.

---

### Step 02.4 - Durable execution trace

**Files:** `scripts/spec_catalog/plan-tick.ps1`

**Depends on:** Step 02.3

**Prompt for developer:**

> When a step moves to `Done`, append its Step Log line inside the phase file in the shape `/spec-dev` writes today, carrying the ticket, the step id and the note. Write no other file and create no new store.

**Why:**

Research 04 makes the invocation itself the primary machine-readable trace, and keeps the Step Log as the durable in-repository copy so a reader of the plan sees the same fact as a reader of the transcript; strategic §3.2 forbids introducing a new storage location for it.

**Verification:**

- `Grep` - `Step Log` matches in `plan-tick.ps1`.
- After moving two steps to `Done` on a scratch copy, the phase file gains exactly two Step Log lines.
- `Grep` - no path under `dev/` or `temp/` is written by `plan-tick.ps1`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. Two real bugs found and fixed here. A local \ silently WAS the \ parameter - PowerShell names are case-insensitive - so the first step's generated text leaked into every later step; renamed to \. And the walk to the end of an existing log stepped over the block's own --- separator, because a horizontal rule also starts with a dash. -Log was added so the marker keeps a short clause while the log carries the account.

---

### Step 02.5 - Regression suite

**Files:** `scripts/spec_catalog/plan-tick.tests/Run-Tests.ps1`

**Depends on:** Step 02.4

**Prompt for developer:**

> Add a suite in the shape of `scripts/spec_catalog/preview.tests/Run-Tests.ps1`, operating on a scratch copy of a real plan folder and never on a live one.
>
> Cases: (A) three steps to `Done` in one call rewrite exactly three markers; (B) the same call updates the index `Steps` cell, `**Phases:**` and `**Last updated:**`; (C) reversal to `NotDone` restores every marker exactly, while the Step Log the forward pass wrote survives - changing a state back does not erase the record that the state was once set; (D) an unknown step number exits non-zero and writes nothing; (E) a pre-existing index/phase disagreement is reported, not overwritten; (F) an ambiguous `-Checkbox` fragment exits non-zero naming both candidates; (G) two steps to `Done` append exactly two Step Log lines.
>
> Case D must assert that the file is unchanged, not merely that the exit code is non-zero.

**Why:**

Strategic §7 lists a false tick and a silent index/phase divergence as the two failure modes whose cost exceeds the savings, and both are invisible without a case that asserts the file did not change.

**Verification:**

- `Glob` - `scripts/spec_catalog/plan-tick.tests/Run-Tests.ps1` exists.
- `pwsh -NoProfile -File scripts/spec_catalog/plan-tick.tests/Run-Tests.ps1` exits 0 and reports seven cases passed.
- `Grep` - the suite never writes under `PLAN/` outside its scratch copy.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. Suite exits 0, seven cases pass against a disposable PLAN/S9991 fixture. Every negative case asserts the file is byte-identical, not merely that the exit code went non-zero. Case C changed shape during the run: reversal cannot be byte-exact once step 02.4 writes a Step Log, and erasing that log on reversal would be wrong - so the case asserts markers restore exactly while the log survives.

---

### Step 02.6 - Route `/spec-dev` through the ticker

**Files:** `.claude/commands/spec-dev.md`, `.claude/reference/spec-dev.md`

**Depends on:** Step 02.5

**Prompt for developer:**

> In `.claude/commands/spec-dev.md`, replace the instructions that tell the agent to edit the step marker and the index counters by hand with a `plan-tick.ps1` invocation, in both places: the `[~] in progress` flip at step start and the `[x] done` flip after Verification passes. Keep the rule that a step goes to `Done` only when every Verification predicate passed in the current run - the ticker enforces nothing about truth, it only writes.
>
> State that consecutive completed steps are ticked in one call rather than one per step. Update the "Phase File Conventions" and "INDEX Conventions" blocks, and mirror the change in `.claude/reference/spec-dev.md` wherever it restates the tick mechanic.

**Why:**

Strategic §11 criteria 4 and 2 require the batch form to be what the driver actually calls, and research 01 measured 1 437 bookkeeping edits a week that only disappear when the driver stops ordering them one at a time.

**Verification:**

- `Grep` - `plan-tick.ps1` matches at least twice in `.claude/commands/spec-dev.md`.
- `Grep` - the driver no longer instructs a manual `Edit` of `**Status:**` or of the index `Steps` cell.
- `Grep` - the "only when every Verification predicate passed" rule still matches in `.claude/commands/spec-dev.md`.
- `Grep` - `plan-tick.ps1` matches in `.claude/reference/spec-dev.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 4/4 PASS. /spec-dev now calls plan-tick.ps1 at both flips (4 references) and its INDEX Conventions block no longer instructs a manual counter bump. The rule that a step reaches Done only when every Verification predicate passed in the current run is restated next to the call - the tool writes state and judges nothing. The reference's Step Log format now shows the call that writes both halves.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/plan-tick.tests/Run-Tests.ps1` exits 0.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `post-change.ps1 -Files "<the four files>" -ScopeToFile -Target "S1596" -ChangeType Script` exits 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Known limitation, recorded rather than hidden: the tool recomputes the INDEX row, both index counters and the phase file's `**Steps done:**` header, but not the phase file's own `**Status:**` / `**Completed:**` header lines - those are still hand-set once per phase. That is roughly four edits per ticket against the 1 437 a week the tool removes, so it is left alone deliberately rather than overlooked.

Plan state is written by one tool across both surfaces and all four shapes. The invocation carries the ticket, phase and step ids, which is the execution trace a later process audit reads. Phase 03 shares no file with this phase.

---

## Rollback Plan

Revert the phase commit. The ticker adds a script and rewrites driver prose; no plan file format changed, so reverting restores hand-editing with no migration.
