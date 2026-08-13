# Phase 07 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases (04 skipped → S0390)
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-09
**Completed:** 2026-06-09

**Step Log (phase):**

- 2026-06-09 - 07.1 catalog synced (1729 records); role/status=new filled for ResolveLocalPathFromUriUseCase, ResolveOpenInFmsTargetUseCase, StandaloneFolderPagingManager, StandalonePagingControlsBinder. 07.2/07.3 FEATURES EN/RU/UK: "External file viewing" (folder paging + Open in FastMediaSorter) - scoped to shipped features, panel parity deferred to S0390. 07.4 dev-log complete per phase.

---

## Objective

Finalize: regenerate the class catalog, ensure dev-log completeness, and add the trilingual FEATURES entry mandated by strategic §8.

---

## Prerequisites

- [ ] Phases 01–06 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +1 entry |
| `docs/FEATURES_RU.md` | Modified | +1 entry |
| `docs/FEATURES_UK.md` | Modified | +1 entry |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 07.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the catalog sync for the app module so the new use cases and managers are indexed. Fill `role` + `status` for new classes via `set.ps1`.

**Verification:**

- Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - exit 0.
- `Grep` - `ResolveOpenInFmsTargetUseCase`, `ResolveLocalPathFromUriUseCase`, `StandaloneFolderPagingManager` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 07.2 - Add the FEATURES entry (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add one user-facing entry: opening a file from another app shows the full player with command panel and folder paging; "Open in FastMediaSorter" opens the in-app player directly on the selected file. Match the existing FEATURES section style. Do not duplicate an existing entry.

**Verification:**

- `Grep` - the new sentence present in `docs/FEATURES.md`.

**Status:** `[x]` done

---

### Step 07.3 - Mirror the FEATURES entry (RU + UK)

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Add the same entry in RU and UK, consistent wording and placement with the EN entry. Use `ё` in RU where applicable.

**Verification:**

- `Grep` - the new entry present in both `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`.

**Status:** `[x]` done

---

### Step 07.4 - Dev-log completeness pass

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 07.3

**Prompt for developer:**

> Confirm every modified source file across phases has a `dev/CHANGELOG.md` entry; add any missing via `.\scripts\add_to_dev_log.ps1`. Do not hand-edit the changelog.

**Verification:**

- `Grep` - changelog references the new use cases / manager / handler change.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` carry the new entry.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and indexes new classes.
- [x] `/spec-check S0389` ready to run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0389`.

---

## Rollback Plan

Docs/catalog only - revert the doc edits and regenerate the catalog. No code or schema impact.
