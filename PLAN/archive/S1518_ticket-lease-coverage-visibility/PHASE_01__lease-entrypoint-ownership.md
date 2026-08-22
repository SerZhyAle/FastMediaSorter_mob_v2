# Phase 01 - Lease Entrypoint Ownership

**Strategic spec:** [`../S1518_ticket-lease-coverage-visibility.md`](../S1518_ticket-lease-coverage-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Define one explicit lease ownership lifecycle for direct and nested specification entry points.

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-all.md` | Modified | ≤ 80 |
| `.claude/commands/spec-dev.md` | Modified | ≤ 80 |

## Steps

### Step 01.1 - Define top-level lease lifecycle

**Files:** `.claude/commands/spec-all.md`
**Depends on:** - start of phase

**Prompt for developer:**

> After ticket resolution and before Stage F1, require `/spec-all` to claim the ticket lease. Handle exit 3 as a live sibling owner and stop without work. Define an idempotent refresh at long-running boundaries and a guaranteed release in every final exit path. Pass an explicit parent-owned lease context when delegating to `/spec-dev`.

**Why:**

Direct `/spec-all` currently has no ownership signal during research or planning, so another session can duplicate work before either one holds a source lock.

**Verification:**

- `Grep` - `ticket-lease.ps1 -Verb Claim` occurs in `.claude/commands/spec-all.md`.
- `Grep` - `ticket-lease.ps1 -Verb Release` occurs in `.claude/commands/spec-all.md`.
- `Grep` - `parent-owned lease` occurs in `.claude/commands/spec-all.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Claim, refresh and release ownership rules documented; direct and nested executors are distinguished.
- 2026-08-14 - Claim, refresh and release ownership rules documented; direct and nested executors are distinguished.

### Step 01.2 - Define standalone and nested executor behaviour

**Files:** `.claude/commands/spec-dev.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Require standalone `/spec-dev` to claim after ticket resolution, refresh on each phase boundary, and release at its final exit. When the explicit parent-owned lease context is present, re-claim only to refresh and never release; the parent `/spec-all` remains owner.

**Why:**

An idempotent claim alone cannot distinguish a standalone executor from a nested executor; without the explicit ownership rule, either direct work stays invisible or nested work can free its parent’s ticket.

**Verification:**

- `Grep` - `ticket-lease.ps1 -Verb Claim` occurs in `.claude/commands/spec-dev.md`.
- `Grep` - `ticket-lease.ps1 -Verb Release` occurs in `.claude/commands/spec-dev.md`.
- `Grep` - `parent-owned lease` occurs in `.claude/commands/spec-dev.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Claim, refresh and release ownership rules documented; direct and nested executors are distinguished.
- 2026-08-14 - Claim, refresh and release ownership rules documented; direct and nested executors are distinguished.

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Verification predicates pass.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

The command contract identifies exactly one release owner and preserves the existing lease-store schema.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
