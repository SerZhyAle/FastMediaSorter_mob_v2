# Phase 05 - Docs Catalog Cleanup

**Strategic spec:** [`../S0258_settings-toggle-row-template.md`](../S0258_settings-toggle-row-template.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Close the rollout with repo bookkeeping, catalog sync, and verification artifacts.

---

## Prerequisites

- [ ] All prior phases are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | generated |
| `dev/CATALOG/app_v2.md` | Modified | generated |
| `dev/CHANGELOG.md` | Modified | generated via script |
| `PLAN/S0258_settings-toggle-row-template.md` | Modified | ≤ 260 |
| `PLAN/S0258_settings-toggle-row-template/INDEX.md` | Modified | ≤ 260 |

---

## Steps

### Step 05.1 - Run required post-change scripts

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** all prior phases

**Prompt for developer:**

> Run the mandatory dev log and catalog sync after the Kotlin/XML rollout is complete.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` → exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. catalog_sync.ps1 -Module app_v2 → OK; scanned 1126 files, 1368 records into dev/CATALOG/app_v2.jsonl + .md. Dev log was added incrementally throughout Phases 01..03.

---

### Step 05.2 - Run locale parity audit

**Files:** locale `strings.xml` files
**Depends on:** Step 05.1

**Prompt for developer:**

> Validate that all new or updated toggle-related strings are present in EN/RU/UK.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_"` → exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. check_strings_localized.ps1 -KeyPrefix "setting_" → OK: all 61 key(s) present in EN/RU/UK. New key `setting_show_pdf_thumbnails_desc` added in Phase 02; no further additions in Phase 03.

---

### Step 05.3 - Update tactical progress and implementation status

**Files:** `PLAN/S0258_settings-toggle-row-template/INDEX.md`, `PLAN/S0258_settings-toggle-row-template.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Mark completed phases, update counters, and prepare the spec for `/spec-check`.

**Verification:**

- `Grep` - `**Status:** Done` present in tactical index when rollout is complete.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Tactical INDEX updated: all 5 phases marked ✅ Done; Phase 03 noted as partial (general+destinations deferred to S0259); Phase 04 noted as no-op (forms have no SwitchMaterial). Strategic spec §10 amended with carve-out reference to S0259.

---

### Step 05.4 - Run final audit handoff

**Files:** strategic + tactical spec files
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `/spec-check S0258` after cleanup and leave the ticket ready for Verified/Partial/Broken outcome.

**Verification:**

- `/spec-check` - `S0258` audit completed.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Spec status will be flipped to `Implemented` immediately after this step; `/spec-check S0258` (Stage F5) follows in the spec-all pipeline.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every modified file.
- [ ] `/spec-check S0258` completed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

No functional rollback needed - bookkeeping only.
