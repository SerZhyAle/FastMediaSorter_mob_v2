# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1168_webcam-resource-source-category.md`](../S1168_webcam-resource-source-category.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Record the delivered capability in the feature inventory and refresh the class catalog.

---

## Prerequisites

- [x] Phases 01 and 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 03.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` for the streams topic filter, in English. Read the shipping flavours off the actual gate - `SUPPORT_STREAMS` in `app_v2/build.gradle.kts` - and not off a neighbouring record.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `S1168` matches once in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

---

### Step 03.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket.

**Verification:**

- Command exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has an entry for every file modified across the ticket.
- [ ] `/spec-check S1168` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated index only.
