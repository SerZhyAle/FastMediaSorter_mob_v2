import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const workspaceRoot = process.env.WORKSPACE_ROOT
  ? path.resolve(process.env.WORKSPACE_ROOT)
  : path.resolve(__dirname, "..", "..", "..");

const server = new McpServer({
  name: "fastmediasorter-filesystem-ro",
  version: "1.0.0",
});

function normalizePath(value) {
  return value.replace(/\\/g, "/");
}

function assertInsideWorkspace(targetPath) {
  const relativePath = normalizePath(path.relative(workspaceRoot, targetPath));
  if (relativePath.startsWith("../") || relativePath === "..") {
    throw new Error("Path escapes workspace root and is not allowed.");
  }
}

function resolveWorkspacePath(relativePath) {
  const targetPath = path.resolve(workspaceRoot, relativePath || ".");
  assertInsideWorkspace(targetPath);
  return targetPath;
}

async function statEntry(relativePath) {
  const absolutePath = resolveWorkspacePath(relativePath);
  const stats = await fs.stat(absolutePath);

  return {
    path: normalizePath(path.relative(workspaceRoot, absolutePath)) || ".",
    type: stats.isDirectory() ? "directory" : "file",
    size: stats.size,
    modifiedAt: stats.mtime.toISOString(),
  };
}

server.registerTool(
  "list_directory",
  {
    title: "List directory",
    description: "List files and folders under a workspace-relative directory.",
    inputSchema: {
      relativePath: z.string().optional(),
    },
    annotations: { readOnlyHint: true },
  },
  async ({ relativePath = "." }) => {
    const absolutePath = resolveWorkspacePath(relativePath);
    const entries = await fs.readdir(absolutePath, { withFileTypes: true });
    const items = entries
      .sort((left, right) => left.name.localeCompare(right.name))
      .map((entry) => `${entry.name}${entry.isDirectory() ? "/" : ""}`)
      .join("\n");

    return {
      content: [
        {
          type: "text",
          text: items || "<empty>",
        },
      ],
    };
  }
);

server.registerTool(
  "read_text_file",
  {
    title: "Read text file",
    description: "Read a UTF-8 text file from the workspace.",
    inputSchema: {
      relativePath: z.string().min(1),
    },
    annotations: { readOnlyHint: true },
  },
  async ({ relativePath }) => {
    const absolutePath = resolveWorkspacePath(relativePath);
    const content = await fs.readFile(absolutePath, "utf8");

    return {
      content: [
        {
          type: "text",
          text: content,
        },
      ],
    };
  }
);

server.registerTool(
  "stat_path",
  {
    title: "Stat path",
    description: "Return metadata for a workspace-relative file or directory.",
    inputSchema: {
      relativePath: z.string().min(1),
    },
    annotations: { readOnlyHint: true },
  },
  async ({ relativePath }) => {
    const entry = await statEntry(relativePath);

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(entry, null, 2),
        },
      ],
    };
  }
);

server.registerTool(
  "search_text",
  {
    title: "Search text",
    description: "Search for a case-insensitive literal string across workspace text files.",
    inputSchema: {
      query: z.string().min(1),
      relativePath: z.string().optional(),
      maxResults: z.number().int().min(1).max(200).optional(),
    },
    annotations: { readOnlyHint: true },
  },
  async ({ query, relativePath = ".", maxResults = 50 }) => {
    const rootPath = resolveWorkspacePath(relativePath);
    const needle = query.toLowerCase();
    const results = [];

    async function walk(currentPath) {
      if (results.length >= maxResults) {
        return;
      }

      const stats = await fs.stat(currentPath);
      if (stats.isDirectory()) {
        const entries = await fs.readdir(currentPath, { withFileTypes: true });
        for (const entry of entries) {
          await walk(path.join(currentPath, entry.name));
          if (results.length >= maxResults) {
            return;
          }
        }
        return;
      }

      const relativeFilePath = normalizePath(path.relative(workspaceRoot, currentPath));
      const content = await fs.readFile(currentPath, "utf8").catch(() => null);
      if (content === null) {
        return;
      }

      const lines = content.split(/\r?\n/);
      for (let index = 0; index < lines.length; index += 1) {
        if (lines[index].toLowerCase().includes(needle)) {
          results.push(`${relativeFilePath}:${index + 1}: ${lines[index].trim()}`);
          if (results.length >= maxResults) {
            return;
          }
        }
      }
    }

    // Read-only server intentionally exposes only inspection tools, so searching skips unreadable files.
    await walk(rootPath);

    return {
      content: [
        {
          type: "text",
          text: results.join("\n") || "No matches found.",
        },
      ],
    };
  }
);

const transport = new StdioServerTransport();
await server.connect(transport);