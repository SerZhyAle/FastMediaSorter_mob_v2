# Phase 01 - Serialization Contract

**Strategic spec:** [`../S1630_bugfix-cached-mediafile-gson-obfuscation.md`](../S1630_bugfix-cached-mediafile-gson-obfuscation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** -
**Completed:** -

---

## Objective

Preserve media-file JSON field names in minified builds so future cached snapshots remain cross-release compatible.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] The existing Gson persistence keep-rule section is present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/proguard-rules.pro` | Modified | ≤ 20 |

---

## Steps

### Step 01.1 - Keep persisted media-file fields stable

**Files:** `app_v2/proguard-rules.pro`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a narrow R8 keep rule for the Gson-persisted media-file model beside the existing cross-version Gson persistence rules. Preserve the class fields without widening the rule to the whole domain package, and document that the blob survives application updates.

**Why:**

Future cached snapshots must use stable JSON keys because the strategic research confirmed that mapping-dependent keys make a previously saved list incompatible after a release update.

**Verification:**

- `Grep` - the model's fully qualified name and `<fields>;` appear in `app_v2/proguard-rules.pro`.
- `Grep` - the rule is under the persistence-model Gson comment block.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Verified narrow MediaFile persistence rule with preserved fields under the Gson cross-version block.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Newly written cache snapshots retain stable field names; Phase 02 adds recovery for snapshots already written with an incompatible mapping.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface changed.
