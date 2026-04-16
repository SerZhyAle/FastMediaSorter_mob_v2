# Docs Search MCP (Local)

Local MCP server for FastMediaSorter documentation (`docs/*.md`) with mirror-aware support for EN/RU/UK.

## Tools

- `docs_search`
  - Full-text section search across local markdown docs.
  - Parameters: `query`, optional `language` (`any|en|ru|uk`), optional `limit`.

- `docs_list_mirrors`
  - Shows mirror groups by base filename and highlights missing language variants.
  - Parameter: optional `onlyMissing`.

- `docs_read`
  - Reads a markdown file from `docs/` by relative path.
  - Parameters: `path`, optional `maxChars`.

- `docs_diff_mirrors`
  - Compares EN/RU/UK mirror files and reports missing language files, heading diffs, and translation density gaps.
  - In `mode=density`, output is sorted by lag severity (most problematic mirrors first).
  - Parameters:
    - optional `mirrorBase`
    - optional `onlyWithDiffs`
    - optional `mode` (`headings|density|both`, default `both`)
    - optional `lagThreshold` (0.1..1.0, default `0.7`) for marking strongly lagging translations

## Install

```powershell
cd scripts/mcp/docs-search-mcp
npm install
```

## Run (manual)

```powershell
cd scripts/mcp/docs-search-mcp
npm start
```

## VS Code MCP Config Example

Use `.vscode/mcp.json`:

```json
{
  "servers": {
    "docs-search": {
      "type": "stdio",
      "command": "node",
      "args": ["scripts/mcp/docs-search-mcp/server.js"],
      "env": {
        "WORKSPACE_ROOT": "${workspaceFolder}",
        "DOCS_ROOT": "${workspaceFolder}/docs"
      }
    }
  }
}
```
