# Phase 01 - Search Server

**Strategic spec:** [../S0272_repo_knowledge_search_expansion.md](../S0272_repo_knowledge_search_expansion.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Introduce a standalone read-only `repo-knowledge` MCP server with curated source coverage, ranked snippet search, curated file reads, and structured spec-catalog lookup.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/mcp/docs-search-mcp/` remains the hosting directory for the new server.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` | New | ≤ 500 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Add curated source manifest and read-only guards

**Files:** `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` as a standalone stdio MCP server. Restrict coverage to the approved root guidance files, `PLAN/spec-catalog.jsonl`, and the `dev/CATALOG/*` / `dev/ACTIVITY_CATALOG/*` surfaces. Reject any path outside the curated allowlist and keep the tool surface read-only only.

**Verification:**

- `Glob` - `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` exists.
- `Grep` - `const curatedSources` present in that file.
- `Grep` - `PLAN/spec-catalog.jsonl` present in that file.
- `Grep` - `dev/ACTIVITY_CATALOG` present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.

---

### Step 01.2 - Add markdown and JSONL entry builders for ranked search

**Files:** `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add source adapters for the curated file types. Markdown inputs must be split into section entries with heading and line metadata. JSONL inputs must be parsed record-by-record and turned into searchable entries that preserve path, source type, and a readable snippet body.

**Verification:**

- `Grep` - `function buildMarkdownEntries` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.
- `Grep` - `function buildJsonlEntries` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.
- `Grep` - `function scoreEntry` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.

---

### Step 01.3 - Register MCP tools for search, read, and structured spec queries

**Files:** `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`
**Depends on:** Step 01.2

**Prompt for developer:**

> Register the final read-only tool surface in the new server file. The minimum surface is `repo_knowledge_search`, `repo_knowledge_read`, and `repo_spec_catalog`. Keep the general search output aligned with `docs-search`: ranked snippet, relative path, section/record metadata, and structured payload for callers.

**Verification:**

- `Grep` - `name: "fastmediasorter-repo-knowledge"` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.
- `Grep` - `"repo_knowledge_search"` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.
- `Grep` - `"repo_knowledge_read"` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.
- `Grep` - `"repo_spec_catalog"` present in `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Runtime smoke passes: the new server starts and exposes `repo_knowledge_search`, `repo_knowledge_read`, and `repo_spec_catalog`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render). Not applicable - no public API change in this phase.

---

## Handoff Notes to Next Phase

The server file must already expose stable tool names, curated source coverage, and structured spec-catalog output before workspace registration begins.

---

## Rollback Plan

Delete `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` and revert the phase commit(s) - no user-facing surface or persisted data changes.