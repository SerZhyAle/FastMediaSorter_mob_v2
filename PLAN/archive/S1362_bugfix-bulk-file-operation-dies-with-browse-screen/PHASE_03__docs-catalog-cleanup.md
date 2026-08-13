# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md`](../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🔄 In progress
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 1 / 2

## Objective

Close the ticket with static validation, device evidence, and repository maintenance.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md` | Modified | n/a |

## Steps

### Step 03.1 - Run scoped static and compile validation

**Files:** no source file
**Depends on:** Phase 02

**Prompt for developer:**

> Run `post-change.ps1` with `-ScopeToFile` for the three modified Kotlin files, then run the fast Kotlin compile check. Record expected and actual results in the ticket.

**Why:** The fix changes lifecycle and coroutine ownership, so it requires a compile proof and focused mechanical gates before device verification.

**Verification:**

- `pwsh -NoProfile -File .\a.ps1 fk` exits 0.

**Status:** `[x]` done - `a.ps1 fk` exited 0 on 2026-08-03; scoped post-change gate pending.

### Step 03.2 - Verify active transfer on the ready emulator

**Files:** no source file
**Depends on:** Step 03.1

**Prompt for developer:**

> Use the ready device to start a multi-file slow network transfer, leave Browse twice, reopen Browse, and inspect logcat for the transfer completion and absence of `JobCancellationException` from `executeInternal`. Add temporary `Timber.d("S1362: ...")` probes only if existing logs cannot establish the result; retain them only while status is `BlockNeedUserTest`.

**Why:** Cloud I/O and Activity recreation are the production conditions that exposed the defect and cannot be fully established by a unit-level static check.

**Verification:**

- `pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json` returns `ready:true`.
- Captured device evidence records completion after Browse recreation and zero matching cancellation exception.

**Status:** `[manual - deferred to human]` - ready emulator lacks a provisioned slow cloud destination; temporary S1362 probes added at guarded cleanup paths.

## Phase Done Criteria

- [ ] Every step is `[x] done`.
- [ ] `/spec-check S1362` reports `Verified` or records the concrete residual blocker.
