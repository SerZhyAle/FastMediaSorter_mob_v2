# Phase 03 — Close Command

**Strategic spec:** [`../S0102_spec-catalog-ergonomics.md`](../S0102_spec-catalog-ergonomics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Create `close.ps1` — a single operator action that finalises a spec atomically, stamping `closed_at` and transitioning to a terminal status.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (optional-field pass-through in `Write-Catalog` and `update.ps1`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/close.ps1` | New | ≤ 70 |

---

## Steps

### Step 3.1 — Create `close.ps1`

**Files:** `scripts/spec_catalog/close.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/close.ps1`. Parameters:
>
> - `-Id [string]` (mandatory) — ticket id in `S####` format.
> - `-Status [ValidateSet: Verified, Archived]` (mandatory) — target terminal status.
>
> Logic:
>
> 1. Validate `-Id` matches `^S\d{4}$`; throw on mismatch.
> 2. Load the record via `Find-Record`. If not found, write an error and exit with code 1.
> 3. If the current status starts with `Block` (i.e. `-match '^Block'`), write error "Unblock first: update.ps1 -Id <id> -Status <previous>" and exit with code 1.
> 4. Build an updated copy of the record, setting `status = <target>`, adding/overwriting `closed_at = Get-Today`, and stamping `updated = Get-Now`. Copy all other existing fields (the pass-through from Phase 01 in `Write-Catalog` handles optional extras automatically).
> 5. Replace the record in the full catalog list and call `Write-Catalog` atomically.
> 6. Write to stdout: `<id> <old_status> -> <new_status> [closed <date>]`.
>
> PowerShell 5.1 compatible. Source `_lib.ps1`.

**Verification:**

- `Glob` — `scripts/spec_catalog/close.ps1` exists.
- `Grep` — `Block` guard present (pattern `^Block` checked against current status) in `close.ps1`.
- `Grep` — `closed_at` assigned in `close.ps1`.
- `Grep` — `Write-Catalog` called in `close.ps1`.
- `Grep` — `ValidateSet` includes `Verified` and `Archived` in `close.ps1`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 5/5 PASS. Block guard tested: exit 1 + correct error message. Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 3.1 is `[x] done`.
- [ ] `pwsh -File scripts/spec_catalog/close.ps1` with a Block-status record exits non-zero and prints the unblock message.
- [ ] After a successful close, `select.ps1 -Id <id> -Format json` shows the target `status` and a non-empty `closed_at` field.
- [ ] `pwsh -File scripts/spec_catalog/validate.ps1` exits 0 after the close.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits across `scripts/spec_catalog/`.
- [ ] Dev log entry added for `scripts/spec_catalog/close.ps1`.

---

## Handoff Notes to Next Phase

- `close.ps1` is the only mutator that writes `closed_at`; `update.ps1` does not touch it.
- The Block-state guard is the implementation of the §6.1 design decision: "operator unblocks first."
- Phase 05 (skill-integration) will replace manual `update.ps1 -Status Verified` finalization sequences with `close.ps1 -Status Verified`.

---

## Rollback Plan

Revert phase commit(s) — `close.ps1` is a new file; reverting removes it entirely. No data migration needed.
