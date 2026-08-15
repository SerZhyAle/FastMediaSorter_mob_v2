# Phase 06 - docs-catalog-cleanup

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new public classes, record the delivered capability in the developer feature inventory, and confirm dev-log completeness.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ +2 |

> `docs/FEATURES*.md` is NOT edited here - the public showcase is owned by `/skill-release` (CLAUDE.md §11). Per-spec capability goes only to `docs/ALL_FEATURES.jsonl`.

---

## Steps

### Step 06.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role`+`status` for the two new public classes via `dev/CATALOG/scripts/set.ps1`: `QrCodeEncoder` and `CompanionQrShareActivity`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*QrCodeEncoder*"` returns the class.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*CompanionQrShareActivity*"` returns the class.

**Status:** `[ ]` not done

---

### Step 06.2 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Grep `docs/ALL_FEATURES.jsonl` for `S1039`. If absent (i.e. `/spec-dev` did not already record it on the Implemented transition), add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing: "Share an SFTP resource as a QR code from the resource menu; the recipient scans it to add the resource instantly." Then validate with `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `S1039` present in `docs/ALL_FEATURES.jsonl` exactly once.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[ ]` not done

---

### Step 06.3 - Dev-log completeness

**Files:** (verification only)
**Depends on:** Step 06.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry covering every file modified across phases 01-05 (batch any missing via `close-and-log.ps1 -DevLogs`). Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an entry referencing S1039 / the QR-share change.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/ALL_FEATURES.jsonl` has the S1039 record.
- [ ] All phases in INDEX show ✅ Done.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S1039` (expect `BlockNeedUserTest` if only on-device QR scan round-trip remains to confirm, else `Verified`).

---

## Rollback Plan

Catalog and inventory are regenerated artifacts - re-run the sync scripts to restore. No source rollback.
