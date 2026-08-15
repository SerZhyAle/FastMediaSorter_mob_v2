# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S1145_stream-edit-parameters-dialog.md`](../S1145_stream-edit-parameters-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Record the delivered capability change and refresh the class catalog. `docs/FEATURES*.md` is release-owned (not edited here).

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 05.1 - Record the capability change in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Record a `CHANGE` to the existing streams edit capability (baseline record `streams.card-overflow-actions-menu`, S0660) via the close-and-log feature block: the edit dialog for a manual stream now also lets the user set its type (Auto / Audio / Video) and no longer crashes when the address collides with another stream (shows a duplicate message instead). Read `-FeatFlavors` off the real gate `BuildConfig.SUPPORT_STREAMS` -> `standard,noLegal,legacy,vr`. Read the record back to confirm the flavor list.

**Verification:**

- `Grep` - a record referencing `S1145` present in `docs/ALL_FEATURES.jsonl` with `flavors` = `standard,noLegal,legacy,vr`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 05.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to index the changed use-case signature and the new `UpdateResult.Duplicate` variant. Do not hand-edit the generated index.

**Verification:**

- `catalog_sync.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/FEATURES*.md` untouched (release-owned).
- [ ] `dev/CHANGELOG.md` has entries for the ticket (via `add_to_dev_log.ps1`).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Device verification (edit a manual channel's type; edit URL to a duplicate) is the remaining gate before `Verified`.

---

## Rollback Plan

Revert the ALL_FEATURES record and regenerate the catalog - no runtime impact.
