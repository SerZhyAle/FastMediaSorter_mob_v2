# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01-05
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Record the delivered capability, regenerate the class catalog, and finalise the dev changelog.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | - |
| `dev/CHANGELOG.md` | Modified (via script) | - |

---

## Steps

### Step 06.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phase 05 done

**Prompt for developer:**

> Add one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing: "When the selected save destination (network/cloud resource) is unavailable, files (screenshots, downloads, video frames, photos, video, voice recordings) are saved to the default local folder for their media type and the user is notified." EN-only. Then validate with `scripts/all_features/validate.ps1`. Do not edit `docs/FEATURES*.md` - the public showcase is populated only by `/skill-release`.

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl` (match on a distinctive phrase, e.g. `unavailable`).
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. ALL_FEATURES record 'file-saving.unavailable-destination-fallback' added; validate exit 0.

---

### Step 06.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 05 done

**Prompt for developer:**

> Regenerate the local catalog index for the new/changed classes (`SaveFallbackReason`, `SaveFallbackPolicy`, `SaveFallbackNotifier`, modified flows). Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role`/`status` for the three new classes via `set.ps1`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*SaveFallback*"` lists the new classes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. catalog_sync app_v2 (1883 records); role/status=new set on 3 new classes.

---

### Step 06.3 - Dev changelog entry

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.1, 06.2

**Prompt for developer:**

> Add one logical dev-log entry covering the S0522 change set via `.\scripts\add_to_dev_log.ps1` (or batch via `close-and-log.ps1 -DevLogs`). Do not edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` - an `S0522` entry present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. 23 per-file S0522 dev-log entries already recorded via post-change; final status entry added at finalization.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has the S0522 entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0522`.

---

## Rollback Plan

Revert the `ALL_FEATURES.jsonl` and `CHANGELOG.md` entries; the catalog index is gitignored and regenerable.
