# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01-06
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Record the shipped capability, document the directory-operations subsystem and the folder workflow, and refresh the generated indexes.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.
- [ ] Document registry queried for product areas `browse` and `architecture`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | n/a |
| `docs/ARCHITECTURE.md` | Modified | n/a |
| `docs/HOW_TO.md` + `_RU.md` + `_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 07.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing folder selection and recursive folder copy/move across resource types. Take the flavor list from the gate that actually limits it: every flavor ships the capability, and `lite` reaches only local resources because network and cloud are disabled there. Then run `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - the new record's id matches exactly once in `docs/ALL_FEATURES.jsonl`.
- `validate.ps1` exits 0 - record `expected: 0 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Record `file-operations.folder-selection-and-transfer` added to `docs/ALL_FEATURES.jsonl`; `all_features/validate.ps1` - expected: 0 | actual: 0 (620 records). Flavors taken from the gates, not from a sibling record: every flavor ships the capability, and `lite` reaches only local resources because `SUPPORT_LOCAL_NETWORK` / `SUPPORT_CLOUD` are false there.

---

### Step 07.2 - Document the directory operations subsystem

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> `docs/ARCHITECTURE.md` has no section on directory operations even though the subsystem predates this ticket. Add one that states the dispatch entry point, the per-protocol strategy layer, the cross-type tree manager, where the safety guards live, and the rule that per-item operation applicability is answered by one policy object rather than inline type tests.

**Verification:**

- `Grep` - a "Directory Operations" heading matches exactly once in `docs/ARCHITECTURE.md`.
- `Grep` - `DirectoryTreeTransferManager` and `BrowseItemOperationPolicy` both appear in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. New "Directory Operations Subsystem" section in `docs/ARCHITECTURE.md` states the single dispatch point, the pre-flight refusals, the same-protocol strategies, the streaming cross-protocol manager, the typed listing, progress/cancellation, and the item-applicability policy.

---

### Step 07.3 - Update the user guide in three locales

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Describe how to select a folder shown as a list item, where its operations menu is, and what copying or moving a folder does with the structure at the destination, including the case of two different resource types. Keep the three files in lockstep - the same instruction, same placement, no locale left behind.

**Verification:**

- `Grep` - the new section heading matches exactly once in each of the three files.
- `pwsh -NoProfile -File scripts/quality/assert-howto-settings-path.ps1` if the section names a settings path; record the exit code.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. "How to Work with Folders" added to `HOW_TO.md`, `HOW_TO_RU.md`, `HOW_TO_UK.md` in lockstep. The gate (real name `assert-howto-settings-paths.ps1`) first failed twice on my text: a made-up tab name ("Основные"/"Основні" instead of "Общие"/"Загальні") and prose trailing after the setting inside the same path line. Corrected to the manifest's own titles ("Показывать подпапки отдельно" / "Показувати підтеки окремо") with the per-resource note as its own sentence - final run: OK, 50 recipes, locales in parity.

---

### Step 07.4 - Refresh the generated indexes

**Files:** `dev/CATALOG/app_v2.jsonl`, `docs/DOCS_MAP.md`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for the new classes through `set.ps1`. Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` and `generate.ps1 -Check`; never hand-edit the generated map.

**Verification:**

- `Grep` - `BrowseItemOperationPolicy` and `DirectoryTreeTransferManager` present in `dev/CATALOG/app_v2.jsonl` with a non-empty `role`.
- `validate.ps1` and `generate.ps1 -Check` both exit 0 - record both.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `catalog_sync.ps1 -Module app_v2` re-run; roles and `status=new` set through `set.ps1 -Path` for `DirectoryTreeTransferManager`, `BrowseItemOperationPolicy` (2 records - enum plus object) and `DirectoryRefusalMessages`. Document registry: `validate.ps1` expected 0 | actual 0 (24 records); `generate.ps1 -Check` expected 0 | actual 0 ("Generated document views are current").

---

### Step 07.5 - Close the change mechanically

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 07.4

**Prompt for developer:**

> Close through the facade: `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManager.kt" -Target "browse" -Description "Folder selection, per-row operations and recursive folder copy/move across resource types" -ChangeType Mixed -Module app_v2`. Batch the remaining dev-log entries with `close-and-log.ps1 -DevLogs` rather than one call per file.

**Verification:**

- `Grep` - the ticket's dev-log entry present in `dev/CHANGELOG.md`.
- `post-change.ps1` exits 0 - record `expected: 0 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Closure runs per phase rather than one final call: each phase ran its own `post-change.ps1` and its entry is in `dev/CHANGELOG.md`. The ticket's status transition and the `BlockNeedUserTest` note go through `close-and-log.ps1` at the end of the run.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles - final `check-standard-fast.ps1 -Mode CodeAndResources` after the S1325 debug tags: BUILD SUCCESSFUL, exit 0. One build validated implementation plus tags, as required.
- [x] Dev log entries present for every phase in `dev/CHANGELOG.md`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and re-rendered (2395 records).
- [x] Phase-boundary audit - documentation phase, no source logic touched beyond the debug tags. Six `Timber.d("S1325:` tags at the changed flow entries: row selection, row menu, copy-directory dispatch, move-directory dispatch, cross-type tree walk, worker folder ops.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The showcase text in `docs/FEATURES*.md` stays untouched here; `/skill-release` generates it from the `ALL_FEATURES` diff.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only.
