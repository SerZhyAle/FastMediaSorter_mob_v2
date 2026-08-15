# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S0542_download-link-from-clipboard.md`](../S0542_download-link-from-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Record the delivered capability in the developer inventory and regenerate the class catalog for the two new classes. No source behavior changes.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored index) | n/a |
| `dev/CHANGELOG.md` | Modified (via `add_to_dev_log.ps1`) | n/a |

---

## Steps

### Step 04.1 - Record capability in ALL_FEATURES.jsonl

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only) describing: manual "Download by link" entry in the main-window dropdown menu, visible when link auto-download is enabled, opening a clipboard-prefilled single-line dialog that routes the link through the existing external-link download path. Do NOT edit `docs/FEATURES*.md` - the public showcase is populated by `/skill-release` from the ALL_FEATURES diff.

**Verification:**

- `Grep` - a new record mentioning "Download by link" (or the chosen wording) present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (gitignored local index)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to scan + render the catalog so the two new classes are indexed. Fill `role` + `status` for `MainLinkDownloadMenuManager` and `MainLinkDownloadManager` via `set.ps1` if the scan leaves them blank.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*MainLinkDownload*"` lists both classes.

**Status:** `[x]` done

---

### Step 04.3 - Dev changelog entry for the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one logical dev-log entry for S0542 via `.\scripts\add_to_dev_log.ps1` summarizing the feature (manual download-by-link menu entry reusing the external-link receiver). Batch the changed files into a single ticket entry rather than one per file.

**Verification:**

- `Grep` - an S0542 / download-by-link entry present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates (`validate.ps1` exits 0).
- [ ] `dev/CATALOG/app_v2.jsonl` lists both new classes.
- [ ] `dev/CHANGELOG.md` has the S0542 entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0542` to advance to Verified.

---

## Rollback Plan

Revert the ALL_FEATURES record and dev-log entry; the catalog index regenerates from source. No source or data changed in this phase.
