# Phase 04 - Catalog write serialization

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - topologically independent
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Serialize catalog journal read-modify-write and ticket-id allocation across processes, so two concurrent mutations both survive instead of the later one silently replacing the earlier.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/_lib.ps1` | Modified | ≤ 640 |
| `scripts/spec_catalog/insert.ps1` | Modified | ≤ 130 |
| `scripts/spec_catalog/next-id.ps1` | Modified | ≤ 30 |
| `scripts/spec_catalog/update.ps1` | Modified | ≤ 200 |
| `scripts/spec_catalog/close.ps1` | Modified | ≤ 110 |
| `scripts/spec_catalog/archive.ps1` | Modified | ≤ 180 |
| `scripts/spec_catalog/delete.ps1` | Modified | ≤ 100 |
| `scripts/spec_catalog/bulk-update.ps1` | Modified | ≤ 140 |
| `scripts/spec_catalog/migrate-archive-split.ps1` | Modified | ≤ 60 |

> Backup / split thresholds: `_lib.ps1` is 545 LOC - over the 500 LOC line, so step 04.1 carries an explicit backup sub-step per CLAUDE.md Rule 5.
>
> **Budget revised during implementation (2026-08-06):** 610 -> 640. The original figure assumed the scriptblock form alone; the Enter/Exit pair the amendment in step 04.1 explains costs a second function plus its contract comment. Actual 623 LOC, well inside CLAUDE.md Rule 2's 1500 ceiling.

---

## Steps

### Step 04.1 - Add a catalog transaction helper

**Files:** `scripts/spec_catalog/_lib.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `scripts/spec_catalog/_lib.ps1` to `temp/S1437/` with a timestamped name first - the file is 545 LOC.
> Add an `Enter-CatalogLock` / `Exit-CatalogLock` pair plus an `Invoke-CatalogTransaction` scriptblock wrapper over them, all taking an optional `[int]$TimeoutSeconds = 30`. They acquire a named system-wide mutex - a `System.Threading.Mutex` under a fixed global name derived from the repo root so two checkouts do not share one - and release it, `Invoke-CatalogTransaction` doing so in a `finally` so a throwing body cannot strand it.
> **Amended during implementation (2026-08-06):** the plan originally specified only the scriptblock form. A scriptblock runs in a child scope, so assignments inside it - `$Id`, `$File`, `$records` in `insert.ps1`, the branch variables in `update.ps1` - do not escape to the caller that prints or exits afterwards. The Enter/Exit pair keeps every mutator's variables in its own scope; the wrapper stays for callers whose whole mutation fits one block. Both are backed by the same mutex, and a stranded lock is released by the OS when the short-lived process exits.
> Treat `AbandonedMutexException` as acquired: a process that died holding the mutex must not deadlock every later one.
> On timeout, throw with a message naming the timeout and the catalog path. Do not silently proceed unserialized.
> Do not change `Read-Catalog`, `Write-Catalog` or `Write-JsonlFile` themselves - the transaction wraps callers, because the lost pair is read-then-write and neither function alone spans it.

**Why:**

Strategic goal 4 requires concurrent journal changes not to be lost, and ADR-5 places the mutual exclusion around the whole read-modify-write rather than around the write, because the write is already atomic by temp-file rename and tearing was never the failure - the lost pair is a stale read followed by a full-file overwrite. §4 records that the journal is rewritten whole, which is what makes a stale snapshot destructive.

**Verification:**

- `Grep` - `function Invoke-CatalogTransaction` matches exactly once in `_lib.ps1`.
- `Grep` - `AbandonedMutexException` matches.
- `Grep` - `finally` appears inside the function body.
- `Grep` - `function Write-Catalog` body is unchanged apart from surrounding whitespace.
- `Glob` - a timestamped backup exists under `temp/S1437/`.
- Run `pwsh -NoProfile -Command ". ./scripts/spec_catalog/_lib.ps1; Invoke-CatalogTransaction -Body { 'ok' }"` - prints `ok`, exit 0.

**Status:** `[x]` done

---

### Step 04.2 - Serialize id allocation

**Files:** `scripts/spec_catalog/insert.ps1`, `scripts/spec_catalog/next-id.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Wrap `insert.ps1`'s read-validate-append-write span in `Invoke-CatalogTransaction`, so the `New-CatalogId` call, the duplicate check and the `Write-Catalog` all sit inside one critical section.
> Wrap `next-id.ps1`'s single `New-CatalogId` call the same way. It only reads, but two callers reading concurrently is exactly how one id gets handed out twice.
> Keep both scripts' stdout contract byte-identical - `insert.ps1` still prints only the allocated id, `next-id.ps1` still prints only `S####`. A transaction wrapper that leaks a diagnostic line into stdout breaks every caller that captures it.

**Why:**

Criterion 6 requires two concurrently created tickets to receive different numbers, and §4 records that the next id is handed out by a bare "max plus one" with no reservation, so two readers compute the same value. The stdout constraint exists because `/spec` captures `insert.ps1`'s output directly as the ticket id.

**Verification:**

- `Grep` - `Invoke-CatalogTransaction` matches in both scripts.
- `Grep -n` - in `insert.ps1` the transaction opens before the `New-CatalogId` line and closes after the `Write-Catalog` line.
- Run `pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1` - output matches `^S\d{4}$` exactly with no extra lines, exit 0.
- Launch two `next-id.ps1` calls concurrently after inserting a record between them and confirm neither returns a stale id.

**Status:** `[x]` done

---

### Step 04.3 - Serialize the status mutators

**Files:** `scripts/spec_catalog/update.ps1`, `scripts/spec_catalog/close.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Wrap each script's read-modify-write span in `Invoke-CatalogTransaction`, starting before its `Read-Catalog` call and ending after the last `Write-Catalog` or `Write-ArchiveCatalog` call on every branch. `update.ps1` has three write branches and `close.ps1` two - all must sit inside the same transaction, not one each.
> Keep the spec-file header sync inside the transaction as well, so a header and its journal row cannot disagree if two sessions flip the same ticket.
> Preserve both scripts' existing exit codes and stdout unchanged.

**Why:**

Strategic goal 4 and criterion 5 require two concurrent status changes both to survive, and §4 records that these are the scripts holding a snapshot across the mutation. Covering every write branch with one transaction rather than one each matters because a script that opens two transactions in sequence re-reads nothing between them and still writes from the first snapshot.

**Verification:**

- `Grep` - `Invoke-CatalogTransaction` matches exactly once in each of the two scripts.
- `Grep -n` - in `update.ps1` all three `Write-Catalog` / `Write-ArchiveCatalog` call sites fall between the transaction's opening and closing braces.
- Insert two scratch tickets via `insert.ps1`, run two concurrent `update.ps1 -Status` calls against them, confirm both changes are present in the journal afterwards, then remove both via `delete.ps1`. Never use `S1437` itself as the probe - flipping the live ticket's own status mid-implementation corrupts the pipeline driving this phase.
- Run `select.ps1 -Id <scratch-id> -Format json` between the two steps - exit 0, status matches what was written.

**Status:** `[x]` done

---

### Step 04.4 - Serialize the remaining mutators

**Files:** `scripts/spec_catalog/archive.ps1`, `scripts/spec_catalog/delete.ps1`, `scripts/spec_catalog/bulk-update.ps1`, `scripts/spec_catalog/migrate-archive-split.ps1`
**Depends on:** Step 04.3

**Prompt for developer:**

> Apply the same read-modify-write wrapping to the four remaining scripts that call `Write-Catalog` or `Write-ArchiveCatalog`. In `bulk-update.ps1` and `migrate-archive-split.ps1` the active and archive writes are adjacent and must share one transaction.
> After this step, grep the whole `scripts/spec_catalog/` tree for `Write-Catalog` and `Write-ArchiveCatalog` call sites and confirm every one outside `_lib.ps1` itself is inside a transaction. Record the list in the step's completion note.

**Why:**

Strategic goal 4 covers the journal, not a subset of the scripts that write it, and a single unwrapped mutator reintroduces the lost-update path for every ticket it touches. The closing sweep exists because the call sites are spread across seven scripts and a missed one is invisible until it silently drops someone's status change.

**Verification:**

- `Grep` - `Invoke-CatalogTransaction` matches in all four scripts.
- `Grep -n` - `Write-Catalog|Write-ArchiveCatalog` across `scripts/spec_catalog/*.ps1` returns only call sites inside a transaction, plus the definitions in `_lib.ps1`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` - exit 0, journal still well-formed.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` exits 0.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1437 -Format json` still returns the ticket - no read-path regression.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every catalog mutation is now serialized across processes, so parallel sessions may flip statuses freely. The mutex is process-wide and short-held; no caller waits on it for more than a journal rewrite. Nothing in the picker path needs to know it exists.

---

## Rollback Plan

Revert the nine scripts and restore the `_lib.ps1` backup from `temp/S1437/`. The journal format is unchanged throughout, so no data migration is involved.
