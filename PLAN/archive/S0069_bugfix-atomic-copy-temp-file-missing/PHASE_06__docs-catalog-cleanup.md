# Phase 06 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Regenerate catalog for the touched transfer classes, finalise journal entries, sync spec status, and run `/spec-check`. Do not add `docs/FEATURES*` bullets: S0069 is an internal reliability fix.

---

## Prerequisites

- [ ] Phases 01..05 ✅ Done.
- [ ] All code/test gates from the earlier phases passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `PLAN/spec-catalog.jsonl` | Modified via script | n/a |
| `dev/CHANGELOG.md` | Modified via script | n/a |
| `PLAN/S0069_bugfix-atomic-copy-temp-file-missing.md` | Modified | n/a |
| `PLAN/S0069_bugfix-atomic-copy-temp-file-missing/INDEX.md` | Modified | n/a |

---

## Steps

### Step 06.1 — Regenerate `dev/CATALOG`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run from project root:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> If new helper types were introduced in `AtomicFileOperationStrategy.kt`, use `dev/CATALOG/scripts/set.ps1` to fill `role` and `status` metadata as needed.

**Verification:**

- `Grep -n "AtomicFileOperationStrategy" "dev/CATALOG/app_v2.jsonl"` returns at least one hit.
- `Grep -n "SmbOperationStrategy" "dev/CATALOG/app_v2.jsonl"` returns at least one hit.
- `Grep -n "AtomicFileOperationStrategy" "dev/CATALOG/app_v2.md"` returns at least one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 3/3 PASS. `dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `render.ps1 -Module app_v2` completed; regenerated catalog contains `AtomicFileOperationStrategy` and `SmbOperationStrategy` entries.

---

### Step 06.2 — Final dev-log entries

**Files:** `dev/CHANGELOG.md` (via helper)
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `./scripts/add_to_dev_log.ps1` for every file modified in Phases 02–05 that does not already have an entry. Include the new unit test file and any touched UI entrypoint files if they changed.
>
> Also add final documentation/meta entries for:
>
> - `PLAN/S0069_bugfix-atomic-copy-temp-file-missing.md`
> - `PLAN/S0069_bugfix-atomic-copy-temp-file-missing/INDEX.md`
> - `dev/CATALOG/app_v2.jsonl`
> - `dev/CATALOG/app_v2.md`
>
> Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep -n "S0069" "dev/CHANGELOG.md"` returns new entries for the touched implementation files and tactical docs.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 1/1 PASS. Final dev-log entries added for tactical metadata files, catalog outputs, and the external test-double unblocker file.

---

### Step 06.3 — Sync spec statuses

**Files:** `PLAN/spec-catalog.jsonl`, `PLAN/S0069_bugfix-atomic-copy-temp-file-missing.md`, `PLAN/S0069_bugfix-atomic-copy-temp-file-missing/INDEX.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> After implementation is complete, run:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0069 -Status Implemented
> ```
>
> Then update:
>
> - strategic spec `Status:` to `Implemented` if code is merged and waiting for audit;
> - tactical `INDEX.md` `Status:` to `Done` and `Phases:` to `6 / 6 done`.
>
> Do not mark `Verified` here. That is reserved for `/spec-check`.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0069 -Format json` returns `"status":"Implemented"`.
- `Grep -n "\*\*Status:\*\* Implemented" "PLAN/S0069_bugfix-atomic-copy-temp-file-missing.md"` matches once.
- `Grep -n "\*\*Status:\*\* Done|\*\*Phases:\*\* 6 / 6 done" "PLAN/S0069_bugfix-atomic-copy-temp-file-missing/INDEX.md"` returns hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 3/3 PASS. `scripts/spec_catalog/update.ps1 -Id S0069 -Status Implemented` executed before final audit; strategic and tactical markdown statuses were then advanced with the verified audit verdict.

---

### Step 06.4 — Run `/spec-check`

**Files:** none modified — verification only
**Depends on:** Step 06.3

**Prompt for developer:**

> Run `/spec-check S0069`. Expected outcome: `Verified`. If the audit returns `Partial` or `Broken`, enter the fix loop (`/spec-fix S0069` → repeat) until the issue list is empty or a real blocker remains.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0069 -Format json` returns `"status":"Verified"`.
- `Grep -n "\*\*Status:\*\* Verified" "PLAN/S0069_bugfix-atomic-copy-temp-file-missing.md"` matches once after `/spec-check`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Static `/spec-check S0069` audit completed with outcome `Verified`; strategic status now reads `Verified` and spec catalog was advanced accordingly.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Catalog regenerated.
- [ ] Journal entries exist for all touched implementation and tactical files.
- [ ] Strategic status reaches `Verified` only through `/spec-check`.
- [ ] No `docs/FEATURES*` bullets were added for S0069.

---

## Handoff Notes to Next Phase

Final phase — see INDEX completion gate.

---

## Rollback Plan

Catalog and tactical metadata are reversible by restoring the previous files and re-running the catalog/render scripts. No user-facing docs are touched in this phase.
