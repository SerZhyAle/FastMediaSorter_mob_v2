# Phase 02 — docs-catalog-cleanup

**Strategic spec:** [`../S0098_bugfix-smb-precheck-false-fail.md`](../S0098_bugfix-smb-precheck-false-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Regenerate catalog, update dev changelog, advance spec status to Implemented.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |
| `dev/CHANGELOG.md` | Modified | — |

---

## Steps

### Step 02.1 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` modified timestamp is today.

**Status:** `[ ]` not done

---

### Step 02.2 — Dev changelog entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt" "S0098" "Add hasActiveConnectionForServer for precheck bypass"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt" "S0098" "Skip TCP precheck when pool has live entry for same server:port"
> ```

**Verification:**

- `Grep` — `S0098` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 02.3 — Advance spec status to Implemented

**Files:** `PLAN/spec-catalog.jsonl`
**Depends on:** Step 02.2

**Prompt for developer:**

> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0098 -Status Implemented
> ```

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0098 -Format json` → `"status":"Implemented"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has `S0098` entries.
- [ ] Catalog `.jsonl` updated.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0098`.

---

## Rollback Plan

Revert catalog regen commit. No code changes in this phase.
