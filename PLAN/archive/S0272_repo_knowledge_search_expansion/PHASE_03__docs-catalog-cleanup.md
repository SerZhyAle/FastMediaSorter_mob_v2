# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [../S0272_repo_knowledge_search_expansion.md](../S0272_repo_knowledge_search_expansion.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Final phase - see INDEX completion gate
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Update operator-facing documentation for the new server and close the internal-only cleanup items without touching user-facing feature docs.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `.vscode/mcp.json` already registers `repo-knowledge`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/mcp/docs-search-mcp/README.md` | Modified | ≤ 350 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Document the new server and tool surface

**Files:** `scripts/mcp/docs-search-mcp/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend `scripts/mcp/docs-search-mcp/README.md` to cover the new `repo-knowledge` server alongside `docs-search`. Document the new server name, the tool names, the curated source groups, and the `.vscode/mcp.json` registration pattern.

**Verification:**

- `Grep` - `repo-knowledge` present in `scripts/mcp/docs-search-mcp/README.md`.
- `Grep` - `repo_knowledge_search` present in `scripts/mcp/docs-search-mcp/README.md`.
- `Grep` - `repo_spec_catalog` present in `scripts/mcp/docs-search-mcp/README.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `scripts/mcp/docs-search-mcp/README.md`.

---

### Step 03.2 - Document internal-only scope and curated source coverage

**Files:** `scripts/mcp/docs-search-mcp/README.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Document that `repo-knowledge` is an internal repo-guidance server, not a user-facing feature. List the curated sources at a group level (`CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`, `PLAN/spec-catalog.jsonl`, `dev/CATALOG/*`, `dev/ACTIVITY_CATALOG/*`) and make clear that public `docs/FEATURES*.md` files are not part of this change.

**Verification:**

- `Grep` - `CLAUDE.md` present in `scripts/mcp/docs-search-mcp/README.md`.
- `Grep` - `PLAN/spec-catalog.jsonl` present in `scripts/mcp/docs-search-mcp/README.md`.
- `Grep` - `dev/CATALOG` present in `scripts/mcp/docs-search-mcp/README.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `scripts/mcp/docs-search-mcp/README.md`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Public feature docs remain untouched because strategic §8 marks the work as internal-only.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render). Not applicable - no public API change in this phase.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the README changes and the phase commit(s) - no persisted data or user-facing feature inventory changes are involved.