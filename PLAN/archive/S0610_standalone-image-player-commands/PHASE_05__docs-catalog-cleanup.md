# Phase 05 - Docs, catalog, capability inventory cleanup

**Strategic spec:** [`../S0610_standalone-image-player-commands.md`](../S0610_standalone-image-player-commands.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

**Step Log:**

- 2026-06-22 - 05.1 ALL_FEATURES record `player.standalone-image-print-copy-move` added + validate PASS (377 records). 05.2 catalog regenerated (1969 records). 05.3 dev logs recorded for all modified files + inventory + status.

---

## Objective

Record the delivered capability, regenerate the class catalog, and complete dev-log journaling for the change set.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended (via script) | n/a |

---

## Steps

### Step 05.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the delivered capability: the standalone image player can print via the unified «Send to..» menu and copy/move the current file to configured destinations. Validate with `scripts/all_features/validate.ps1`. Do not edit `docs/FEATURES*.md` (the public showcase is populated only by `/skill-release`).

**Verification:**

- `Grep` - new record present in `docs/ALL_FEATURES.jsonl` referencing standalone image print + copy/move.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 05.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the changed constructor signature of `DestinationButtonsManager` and the new methods in `StandaloneFileOperationsHandler`. This index is gitignored; regeneration is the deliverable, not a commit.

**Verification:**

- `scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[ ]` not done

---

### Step 05.3 - Complete dev-log journaling

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Ensure one dev-log entry exists per logical change (print receiver; destination-buttons root-view refactor; standalone bottom panels layout; standalone copy/move wiring) via `.\scripts\add_to_dev_log.ps1`. Batch related files per logical change, not per file.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains entries covering all four implementation logical changes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates.
- [ ] Catalog regenerated.
- [ ] Dev-log complete for the change set.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0610` to advance the spec to `Verified`.

---

## Rollback Plan

Documentation/catalog only - revert the `ALL_FEATURES.jsonl` record; catalog regenerates from source.
