# Phase 04 — Bulk Update

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

Create `bulk-update.ps1` — an atomic multi-record status/priority mutation that validates every target before touching the journal.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/bulk-update.ps1` | New | ≤ 90 |

---

## Steps

### Step 4.1 — Create `bulk-update.ps1`

**Files:** `scripts/spec_catalog/bulk-update.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/bulk-update.ps1`. Parameters:
>
> - `-Id [string[]]` (mandatory) — one or more ticket ids in `S####` format.
> - `-Status [string]` (optional) — new status to apply to every specified ticket; must be in the status enum from `_lib.ps1`.
> - `-Priority [int]` (optional, range 0..100) — new priority to apply to every specified ticket.
>
> At least one of `-Status` or `-Priority` must be provided; if neither is given, write an error and exit with code 1.
>
> Logic (all-or-nothing):
>
> 1. Validate every id in `-Id` matches `^S\d{4}$` and exists in the catalog. Collect all errors into a list; do not stop at the first error. If the error list is non-empty after checking all ids, print every error line and exit with code 1 — the journal is not touched.
> 2. Load the full records list. For each id in `-Id`, update the matching record in memory: apply `-Status` and/or `-Priority` as specified, stamp `updated = Get-Now`. Call `Assert-Record` on each updated record; add any assertion errors to the error list.
> 3. If the error list is non-empty after assertions, print every error and exit with code 1 — journal still not touched.
> 4. Call `Write-Catalog` once with the full (modified) records array.
> 5. Write one summary line per updated record: `<id> <old_status> -> <new_status> (priority: <N>)`.
>
> PowerShell 5.1 compatible. Source `_lib.ps1`.

**Verification:**

- `Glob` — `scripts/spec_catalog/bulk-update.ps1` exists.
- `Grep` — `-Id` parameter declared as `[string[]]` in `bulk-update.ps1`.
- `Grep` — error collection before any write (pattern: a list or array of errors populated in a loop, checked before `Write-Catalog`) present in `bulk-update.ps1`.
- `Grep` — `Write-Catalog` present in `bulk-update.ps1`.
- `Grep` — `Assert-Record` called in `bulk-update.ps1`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 5/5 PASS. All-or-nothing tested: exit 1 + journal unchanged on bad id. Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 4.1 is `[x] done`.
- [ ] Calling `bulk-update.ps1` with one valid and one nonexistent id exits non-zero and leaves the journal unchanged (verify via `validate.ps1`).
- [ ] Calling `bulk-update.ps1` with all valid ids applies changes to all of them in a single `Write-Catalog` call.
- [ ] `pwsh -File scripts/spec_catalog/validate.ps1` exits 0 after a successful bulk-update.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits across `scripts/spec_catalog/`.
- [ ] Dev log entry added for `scripts/spec_catalog/bulk-update.ps1`.

---

## Handoff Notes to Next Phase

- The all-or-nothing guarantee is achieved by validating all ids before any `Write-Catalog` call.
- `-DryRun` is intentionally absent from v1 per §6.2 design decision; add in a follow-up if needed.

---

## Rollback Plan

Revert phase commit(s) — `bulk-update.ps1` is a new file. No data migration needed.
