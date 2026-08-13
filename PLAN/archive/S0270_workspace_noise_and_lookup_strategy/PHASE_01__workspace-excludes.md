# Phase 01 - Workspace Excludes

**Strategic spec:** [`../S0270_workspace_noise_and_lookup_strategy.md`](../S0270_workspace_noise_and_lookup_strategy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Apply the agreed heavy-directory exclude set to the workspace configuration without changing any app code or build logic.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.2 research item is Resolved.
- [x] Strategic §6.5 research item is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.vscode/settings.json` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Extend editor and search excludes

**Files:** `.vscode/settings.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Update `files.exclude`, `search.exclude`, and `files.watcherExclude` in `.vscode/settings.json` so they cover the frozen heavy-directory set agreed from strategic §6.2. Preserve the existing historical read-only exclusions and keep the JSON style consistent with the file's current formatting.

**Verification:**

- `Grep` - `"**/temp"` appears in `.vscode/settings.json`.
- `Grep` - `"**/DOWNLOADS"` appears in `.vscode/settings.json`.
- `Grep` - `"**/.venv"` appears in `.vscode/settings.json`.
- `Grep` - `"**/logs"` appears in `.vscode/settings.json`.
- `Grep` - `"**/.kotlin"` appears in `.vscode/settings.json`.
- `Grep` - `node_modules` appears in `.vscode/settings.json`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 6/6 PASS. Files: .vscode/settings.json. Dev log recorded.

---

### Step 01.2 - Align Java import exclusions with the same surface

**Files:** `.vscode/settings.json`
**Depends on:** Step 01.1

**Prompt for developer:**

> Update `java.import.exclusions` so the Java/Kotlin tooling skips the same heavy directories as the general workspace/search configuration wherever the exclusion is meaningful. Do not add unrelated paths or build-system edits.

**Verification:**

- `Grep` - `"**/build/**"` remains present in `.vscode/settings.json`.
- `Grep` - `"**/.gradle/**"` remains present in `.vscode/settings.json`.
- `Grep` - `node_modules` appears in the `java.import.exclusions` block.
- `Grep` - `DOWNLOADS` appears in the `java.import.exclusions` block.
- `Grep` - `temp` appears in the `java.import.exclusions` block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 5/5 PASS. Files: .vscode/settings.json. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.vscode/settings.json` remains valid JSON text.
- [x] `Grep` for `V1`, `v2_6`, and `spec_v2` in `.vscode/settings.json` still returns the existing exclusions.
- [x] Dev log entry added for `.vscode/settings.json` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The workspace config now carries the frozen exclude set; `CLAUDE.md` can reference the same scope without redefining new directories.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
