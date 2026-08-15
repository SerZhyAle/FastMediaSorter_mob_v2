# Phase 01 — Schema Foundations

**Strategic spec:** [`../S0102_spec-catalog-ergonomics.md`](../S0102_spec-catalog-ergonomics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Document new optional journal fields in SCHEMA.md and update `_lib.ps1` + `update.ps1` to pass arbitrary optional fields through every read-write cycle without loss.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `pwsh -File scripts/spec_catalog/validate.ps1` exits 0 on the current catalog.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/SCHEMA.md` | Modified | ≤ 130 |
| `scripts/spec_catalog/_lib.ps1` | Modified | ≤ 200 |
| `scripts/spec_catalog/update.ps1` | Modified | ≤ 90 |

---

## Steps

### Step 1.1 — Document optional fields in SCHEMA.md

**Files:** `scripts/spec_catalog/SCHEMA.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Append a new section `## Optional fields` to `scripts/spec_catalog/SCHEMA.md`. Document each field below with its type, allowed values/semantics, and a note that absence in any record (old or new) is valid:
>
> - `title` (string) — human-readable display name; free text; used by `search.ps1` for substring matching alongside `name`.
> - `tags` (string array) — thematic labels, e.g. `["tooling","scripts"]`; filterable by `search.ps1 -Tag`.
> - `type` (string) — work kind: one of `feature`, `bugfix`, `tooling`, `research`; filterable by `search.ps1 -Type`.
> - `blocked_by` (string array) — ids of tickets this one depends on, e.g. `["S0099"]`; informational, not enforced by validate.ps1.
> - `closed_at` (string) — `YYYY-MM-DD` date of intentional finalization; written by `close.ps1`; absent until the ticket is closed.
> - `has_tactical` (boolean) — `true` when a `PLAN/Sxxxx_*/INDEX.md` tactical folder exists; written by `/spec-tech` during status transition to Tactical.

**Verification:**

- `Grep` — `## Optional fields` present in `scripts/spec_catalog/SCHEMA.md`.
- `Grep` — `closed_at` present in `scripts/spec_catalog/SCHEMA.md`.
- `Grep` — `has_tactical` present in `scripts/spec_catalog/SCHEMA.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: `scripts/spec_catalog/SCHEMA.md` (+17 LOC). Dev log recorded.

---

### Step 1.2 — Update `Write-Catalog` in `_lib.ps1` to pass through unknown optional fields

**Files:** `scripts/spec_catalog/_lib.ps1`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `scripts/spec_catalog/_lib.ps1`, update the `Write-Catalog` function. After the `$ordered` dictionary is populated with the fixed set of keys (`id`, `name`, `status`, `priority`, `tier`, `file`, `created`, `updated`), add a loop that iterates over all remaining properties of `$r` — those whose names are not in the fixed set — and appends them to `$ordered` sorted by property name. Use `$r.PSObject.Properties` to enumerate. This ensures that any optional field (`title`, `tags`, `type`, `blocked_by`, `closed_at`, `has_tactical`, or future additions) survives every read-write cycle intact.
>
> PowerShell 5.1 compatible. Do not touch `Assert-Record` — it already ignores unknown fields.

**Verification:**

- `Grep` — `PSObject.Properties` appears at least twice in `_lib.ps1` (existing tier check line + new pass-through loop).
- `Grep` — pattern `foreach.*PSObject.Properties` present in `_lib.ps1`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: `scripts/spec_catalog/_lib.ps1` (+7 LOC). Dev log recorded.

---

### Step 1.3 — Update `update.ps1` to copy optional fields to the mutable object

**Files:** `scripts/spec_catalog/update.ps1`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `scripts/spec_catalog/update.ps1`, after the fixed-field mutable copy (`$updated`) is constructed from `$old`, add a loop that copies every extra property from `$old` that is not in the fixed set (`id`, `name`, `status`, `priority`, `tier`, `file`, `created`, `updated`) to `$updated` using `Add-Member -NotePropertyName ... -NotePropertyValue ...`. This prevents optional fields from being silently dropped whenever `update.ps1` is called on a record that carries them.
>
> PowerShell 5.1 compatible.

**Verification:**

- `Grep` — `Add-Member` appears at least twice in `update.ps1` (existing tier block + new pass-through loop).
- `Grep` — pattern `foreach.*PSObject.Properties` present in `update.ps1`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: `scripts/spec_catalog/update.ps1` (+7 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 1.* above is `[x] done`.
- [ ] `pwsh -File scripts/spec_catalog/validate.ps1` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits across `scripts/spec_catalog/`.
- [ ] Dev log entry added for each file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Round-trip test: create a test record with an extra field via `insert.ps1`, then call `update.ps1 -Id <id>` (touch-only), confirm the extra field is still present in the journal via `select.ps1 -Id <id> -Format json`.

---

## Handoff Notes to Next Phase

- All optional fields (`title`, `tags`, `type`, `blocked_by`, `closed_at`, `has_tactical`) survive `update.ps1` and `Write-Catalog` without schema changes to `Assert-Record`.
- Phases 02, 03, 04 may now proceed independently in any order.

---

## Rollback Plan

Revert phase commit(s) — no schema migration, no user-facing surface changed. All changes are in tooling scripts only.
