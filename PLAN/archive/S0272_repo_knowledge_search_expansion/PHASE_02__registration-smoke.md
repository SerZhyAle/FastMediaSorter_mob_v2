# Phase 02 - Registration Smoke

**Strategic spec:** [../S0272_repo_knowledge_search_expansion.md](../S0272_repo_knowledge_search_expansion.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Register the new MCP server in workspace config while preserving the existing MCP servers unchanged.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` already exposes the final tool names.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.vscode/mcp.json` | Modified | ≤ 250 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Register `repo-knowledge` in workspace MCP config

**Files:** `.vscode/mcp.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a sibling MCP server entry named `repo-knowledge` to `.vscode/mcp.json`. Use `node` with `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` and keep the environment scoped to `WORKSPACE_ROOT`. Do not rename or repurpose any existing server entry.

**Verification:**

- `Grep` - `"repo-knowledge"` present in `.vscode/mcp.json`.
- `Grep` - `"scripts/mcp/docs-search-mcp/repo-knowledge-server.js"` present in `.vscode/mcp.json`.
- `Grep` - `"WORKSPACE_ROOT"` present inside the new server block.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `.vscode/mcp.json`.

---

### Step 02.2 - Preserve the existing MCP server set unchanged

**Files:** `.vscode/mcp.json`
**Depends on:** Step 02.1

**Prompt for developer:**

> Keep `docs-search`, `filesystem_rw`, `filesystem_ro`, and `gradle_safe` intact while adding the new sibling server. This phase is complete only if the new entry coexists with the current ones without renaming, deleting, or re-pointing any of them.

**Verification:**

- `Grep` - `"docs-search"` matches once in `.vscode/mcp.json`.
- `Grep` - `"filesystem_rw"` matches once in `.vscode/mcp.json`.
- `Grep` - `"filesystem_ro"` matches once in `.vscode/mcp.json`.
- `Grep` - `"gradle_safe"` matches once in `.vscode/mcp.json`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: `.vscode/mcp.json`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Workspace MCP config parses and the new server can be started for a smoke run.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render). Not applicable - no public API change in this phase.

---

## Handoff Notes to Next Phase

The workspace config must expose a stable `repo-knowledge` entry before documentation is updated.

---

## Rollback Plan

Revert `.vscode/mcp.json` to the pre-phase version and remove the new `repo-knowledge` entry.