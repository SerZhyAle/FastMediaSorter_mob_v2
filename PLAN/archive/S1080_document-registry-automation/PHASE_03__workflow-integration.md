# Phase 03 - Workflow Integration

**Strategic spec:** [`../S1080_document-registry-automation.md`](../S1080_document-registry-automation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-17
**Completed:** 2026-07-17

## Objective

Route repository documentation workflows and local knowledge search through the registry.

## Prerequisites

- [ ] Phase 02 is ✅ Done.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 1500 |
| `AGENTS.md` | Modified | ≤ 1500 |
| `.github/copilot-instructions.md` | Modified | ≤ 1500 |
| `.github/prompts/doc-update.prompt.md` | Modified | ≤ 500 |
| `scripts/mcp/docs-search-mcp/repo-knowledge-server.js` | Modified | ≤ 500 |
| `scripts/mcp/docs-search-mcp/README.md` | Modified | ≤ 500 |

## Steps

### Step 03.1 - Require registry research and registry-aware documentation updates

**Files:** `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`, `.github/prompts/doc-update.prompt.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one consistent rule: before documentation research or documentation closure, query the document registry by changed product area and update triggers. Require validation and generated-view drift checks whenever a registered document, page, or registry record changes. Preserve existing feature and settings-specific obligations.

**Verification:**

- `rg -n 'DOCUMENT_REGISTRY|document_registry' CLAUDE.md AGENTS.md .github/copilot-instructions.md .github/prompts/doc-update.prompt.md` returns matches in all files.

**Status:** `[x]` done

### Step 03.2 - Expose registry through local curated knowledge search

**Files:** `scripts/mcp/docs-search-mcp/repo-knowledge-server.js`, `scripts/mcp/docs-search-mcp/README.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Include the registry and its guide in curated local knowledge sources and document the new searchable source. Do not change external MCP configuration or dependencies.

**Verification:**

- `rg -n 'DOCUMENT_REGISTRY' scripts/mcp/docs-search-mcp/repo-knowledge-server.js scripts/mcp/docs-search-mcp/README.md` returns matches.

**Status:** `[x]` done

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- [ ] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

## Handoff Notes to Next Phase

Agents can query registry-backed document relationships before planning and closure.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing application surface changed.
