---
name: spec-catalog-delete-is-soft-and-ids-burn
description: delete.ps1 soft-deletes to spec-catalog-archive.jsonl, select.ps1 -Id reads that archive back and prints `[]` on a miss, and next-id.ps1 races - allocate an id only immediately before insert.
metadata:
  type: project
---

`scripts/spec_catalog/delete.ps1 -Id X -Confirm` is a **soft** delete: the record leaves `PLAN/spec-catalog.jsonl` and lands in `PLAN/spec-catalog-archive.jsonl` with status `Archived`. `select.ps1 -Id X -Format json` reads the archive too, so it still returns the record - a plain `grep` of `spec-catalog.jsonl` returns nothing for the same id. An absent id gives `[]` + exit 0 from `select.ps1`, and exit **1** from `delete.ps1`.

Consequence: a spec id is **burned for good** once used. S1534 (Verified 2026-08-09) removed the biggest source of that burn - `preview.tests/Run-Tests.ps1` no longer touches the production journals - and added `PLAN/spec-catalog-burned-ids.jsonl`, which `New-CatalogId` reads so a removed record's id can never be reissued.

**Why:** verified 2026-08-08 during S1490. A cleanup check asserting "the probe is absent from the catalog" fired on every green run, because `select.ps1` found the archived copy. Separately, `next-id.ps1` handed back S1526 for a real parked draft, a harness run consumed that id before `insert.ps1` ran, and the insert died with `Duplicate id 'S1526'`.

**How to apply:**
- Writing a cleanup/audit predicate over the catalog: assert **"not live"** (`status -ne 'Archived'`), never "absent". And read the **parsed count**, never the truthiness of `select.ps1` output - a miss prints the literal `[]`, which is a non-empty string and passes an `if ($raw)` test. That exact bug shipped twice: once as a check that never went red, once (S1534) as one that was always red.
- Allocating an id: call `next-id.ps1` **immediately** before `insert.ps1`, never before writing the spec file or running anything else - under parallel sessions ([[project-spec-all-concurrent-tree-red]], S1437) the gap is a real race. On `Duplicate id`, re-allocate rather than assuming a sibling session is at fault.
- A test harness must never allocate a real id: point the CLI at a snapshot with `$env:FMS_SPEC_CATALOG_DIR` (+ `$env:FMS_SKIP_RELEASE_QUEUE`) and take ids from a fixed high block. See `preview.tests/Run-Tests.ps1` as the reference consumer.
- Never conclude "the record is gone" from `select.ps1` alone - check its `status` field.
