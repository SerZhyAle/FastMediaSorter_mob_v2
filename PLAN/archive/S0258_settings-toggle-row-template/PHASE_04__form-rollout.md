# Phase 04 - Form Rollout

**Strategic spec:** [`../S0258_settings-toggle-row-template.md`](../S0258_settings-toggle-row-template.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (no-op - form layouts contain no SwitchMaterial; spec over-scoped this phase)
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Apply the canonical toggle row to add/edit resource forms.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_resource_editor.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout/*addresource*` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/*.kt` | Modified | ≤ 500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/*.kt` | Modified | ≤ 500 each |

---

## Steps

### Step 04.1 - Inventory and migrate form toggle rows

**Files:** form XML files
**Depends on:** Phase 03

**Prompt for developer:**

> Replace ad-hoc switch rows in add/edit resource forms with the canonical component while preserving field visibility and validation behavior.

**Verification:**

- `Grep` - `SettingsToggleRow` appears in the migrated form XML files.

**Status:** `[x]` done (no-op)

**Step Log:**

- 2026-05-19 - Survey: `fragment_resource_editor.xml` (193 LOC) and `activity_add_resource.xml` (448 LOC) contain 0 `SwitchMaterial` controls. Forms use EditText/Spinner/MaterialCheckBox inputs — none of which fall under Pattern A switch-row scope. No migration targets exist in `addresource/` or `resourceeditor/` layouts. Step closed without changes.

---

### Step 04.2 - Update form bindings and help behavior

**Files:** related form Kotlin files
**Depends on:** Step 04.1

**Prompt for developer:**

> Update form controllers/managers so migrated rows use the component API and existing help flows remain functional.

**Verification:**

- `Grep` - migrated form Kotlin files reference row IDs rather than removed direct switch IDs.

**Status:** `[x]` done (no-op)

**Step Log:**

- 2026-05-19 - No row migrations occurred in Step 04.1, so no form binding updates are needed. Step closed without changes.

---

### Step 04.3 - Validate forms build gate

**Files:** migrated form XML + Kotlin files
**Depends on:** Step 04.2

**Prompt for developer:**

> Build after the form rollout batch.

**Verification:**

- `/build` - `standard debug` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. Phase 03 build already validated `standardDebug` (v2.60.5192.135). No Phase 04 source changes; no incremental build needed.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every modified file.

---

## Handoff Notes to Next Phase

Phase 05 closes spec bookkeeping, catalog sync, and any residual docs/tooling updates.

---

## Rollback Plan

Revert phase commit(s) - no stored data migration involved.
