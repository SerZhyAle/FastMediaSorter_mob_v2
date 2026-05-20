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

// Keep the repo-knowledge surface explicit so the server never turns into a whole-workspace crawler.
const curatedSources = {
  files: [
    "CLAUDE.md",
    "AGENTS.md",
    ".github/copilot-instructions.md",
    "dev/PROJECT_OPERATIONS_INDEX.md",
    "dev/AGENT_WORKFLOW.md",
    "PLAN/spec-catalog.jsonl",
  ],
  directories: [
    "dev/CATALOG",
    "dev/ACTIVITY_CATALOG",
  ],
};

const curatedDirectoryExtensions = new Set([".md", ".jsonl"]);

const server = new McpServer({
  name: "fastmediasorter-repo-knowledge",
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

function isCuratedRelativePath(relativePath) {
  if (curatedSources.files.includes(relativePath)) {
    return true;
  }

  if (curatedSources.directories.includes(relativePath)) {
    return true;
  }

  return curatedSources.directories.some((directoryPath) => {
    if (!(relativePath === directoryPath || relativePath.startsWith(`${directoryPath}/`))) {
      return false;
    }

    return curatedDirectoryExtensions.has(path.extname(relativePath).toLowerCase());
  });
}

function resolveCuratedPath(relativePath) {
  const normalized = normalizePath(relativePath || ".").replace(/^\/+/, "");
  const absolutePath = path.resolve(workspaceRoot, normalized);

  assertInsideWorkspace(absolutePath);

  if (!isCuratedRelativePath(normalized)) {
    throw new Error(`Path is outside the repo-knowledge allowlist: ${normalized}`);
  }

  return {
    absolutePath,
    relativePath: normalized,
  };
}

function tokenizeQuery(query) {
  return query
    .toLowerCase()
    .split(/\s+/)
    .map((token) => token.trim())
    .filter(Boolean);
}

function extractTitle(content, fallbackName) {
  const headingMatch = content.match(/^#\s+(.+)$/m);
  if (headingMatch?.[1]) {
    return headingMatch[1].trim();
  }

  return fallbackName;
}

function splitMarkdownSections(content) {
  const lines = content.split(/\r?\n/);
  const sections = [];

  let current = {
    heading: "Document",
    line: 1,
    body: "",
  };

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const headingMatch = line.match(/^(#{1,3})\s+(.+)$/);

    if (headingMatch) {
      sections.push(current);
      current = {
        heading: headingMatch[2].trim(),
        line: index + 1,
        body: "",
      };
      continue;
    }

    current.body += `${line}\n`;
  }

  sections.push(current);
  return sections.filter((section) => section.body.trim() || section.heading !== "Document");
}

function buildJsonlSnippet(record) {
  const keys = [
    "id",
    "name",
    "status",
    "priority",
    "tier",
    "file",
    "class",
    "path",
    "role",
    "roleRu",
    "module",
  ];

  return keys
    .filter((key) => record[key] !== undefined && record[key] !== null && `${record[key]}`.trim() !== "")
    .map((key) => `${key}: ${Array.isArray(record[key]) ? record[key].join(", ") : record[key]}`)
    .join(" | ");
}

function buildMarkdownEntries(relativePath, content) {
  const title = extractTitle(content, path.basename(relativePath));

  return splitMarkdownSections(content).map((section) => {
    const snippet = section.body.replace(/\s+/g, " ").trim();
    return {
      sourceType: "markdown",
      relativePath,
      title,
      section: section.heading,
      line: section.line,
      snippet,
      searchText: `${title} ${section.heading} ${section.body}`,
    };
  });
}

function buildJsonlEntries(relativePath, content) {
  const lines = content.split(/\r?\n/);
  const entries = [];

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index].trim();
    if (!rawLine) {
      continue;
    }

    let record;
    try {
      record = JSON.parse(rawLine);
    } catch {
      continue;
    }

    const title = record.id ?? record.class ?? record.name ?? record.path ?? `record ${index + 1}`;
    const section = record.id ?? record.class ?? record.name ?? "Record";
    const snippet = buildJsonlSnippet(record) || rawLine;

    entries.push({
      sourceType: "jsonl",
      relativePath,
      title,
      section,
      line: index + 1,
      snippet,
      searchText: `${snippet} ${rawLine}`,
      record,
    });
  }

  return entries;
}

function scoreEntry(entry, tokens) {
  const lowerTitle = `${entry.title}`.toLowerCase();
  const lowerSection = `${entry.section}`.toLowerCase();
  const lowerPath = entry.relativePath.toLowerCase();
  const lowerSearchText = entry.searchText.toLowerCase();

  let score = 0;
  for (const token of tokens) {
    if (lowerTitle.includes(token)) {
      score += 3;
    }
    if (lowerSection.includes(token)) {
      score += 2;
    }
    if (lowerPath.includes(token)) {
      score += 2;
    }
    if (lowerSearchText.includes(token)) {
      score += 1;
    }
  }

  return score;
}

async function listCuratedFiles() {
  const fileSet = new Set();

  for (const relativePath of curatedSources.files) {
    try {
      const resolved = resolveCuratedPath(relativePath);
      const stats = await fs.stat(resolved.absolutePath);
      if (stats.isFile()) {
        fileSet.add(resolved.relativePath);
      }
    } catch {
      // Skip missing optional local artifacts such as gitignored generated catalog files.
    }
  }

  for (const directoryPath of curatedSources.directories) {
    const resolvedDirectory = resolveCuratedPath(directoryPath);
    const entries = await fs.readdir(resolvedDirectory.absolutePath, { withFileTypes: true }).catch(() => []);

    for (const entry of entries) {
      if (!entry.isFile()) {
        continue;
      }

      if (!curatedDirectoryExtensions.has(path.extname(entry.name).toLowerCase())) {
        continue;
      }

      fileSet.add(normalizePath(path.join(directoryPath, entry.name)));
    }
  }

  return Array.from(fileSet).sort((left, right) => left.localeCompare(right));
}

async function buildKnowledgeEntries() {
  const relativePaths = await listCuratedFiles();
  const entries = [];

  for (const relativePath of relativePaths) {
    const resolved = resolveCuratedPath(relativePath);
    const content = await fs.readFile(resolved.absolutePath, "utf8");
    const extension = path.extname(relativePath).toLowerCase();

    if (extension === ".md") {
      entries.push(...buildMarkdownEntries(relativePath, content));
    } else if (extension === ".jsonl") {
      entries.push(...buildJsonlEntries(relativePath, content));
    }
  }

  return entries;
}

function renderSearchResults(results) {
  if (!results.length) {
    return "No matches found in repo knowledge.";
  }

  return results
    .map((entry, index) => {
      const snippet = entry.snippet.replace(/\s+/g, " ").trim().slice(0, 260);
      return [
        `${index + 1}. [${entry.score}] ${entry.title}`,
        `   file: ${entry.relativePath}`,
        `   type: ${entry.sourceType} | section: ${entry.section} | line: ${entry.line}`,
        `   ${snippet}`,
      ].join("\n");
    })
    .join("\n\n");
}

async function readCuratedFile(relativePath, maxChars) {
  const resolved = resolveCuratedPath(relativePath);
  const content = await fs.readFile(resolved.absolutePath, "utf8");
  const truncated = content.length > maxChars;

  return {
    relativePath: resolved.relativePath,
    content: truncated ? `${content.slice(0, maxChars)}\n\n[TRUNCATED]` : content,
    length: content.length,
    truncated,
  };
}

async function readSpecCatalogRecords() {
  const resolved = resolveCuratedPath("PLAN/spec-catalog.jsonl");
  const content = await fs.readFile(resolved.absolutePath, "utf8");

  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      try {
        return JSON.parse(line);
      } catch {
        return null;
      }
    })
    .filter(Boolean);
}

function renderSpecCatalogResults(records) {
  if (!records.length) {
    return "No spec catalog matches found.";
  }

  return records
    .map((record, index) => `${index + 1}. ${record.id} | ${record.status} | p=${record.priority} | ${record.name} | ${record.file}`)
    .join("\n");
}

server.registerTool(
  "repo_knowledge_search",
  {
    title: "Search repo knowledge",
    description: "Search curated repo guidance, spec, and catalog sources and return ranked snippets.",
    inputSchema: {
      query: z.string().min(2),
      limit: z.number().int().min(1).max(50).optional(),
    },
  },
  async ({ query, limit = 10 }) => {
    const tokens = tokenizeQuery(query);
    const entries = await buildKnowledgeEntries();
    const matches = entries
      .map((entry) => ({
        ...entry,
        score: scoreEntry(entry, tokens),
      }))
      .filter((entry) => entry.score > 0)
      .sort((left, right) => right.score - left.score)
      .slice(0, limit);

    return {
      content: [{ type: "text", text: renderSearchResults(matches) }],
      structuredContent: {
        query,
        returned: matches.length,
        results: matches,
      },
    };
  }
);

server.registerTool(
  "repo_knowledge_read",
  {
    title: "Read curated repo knowledge file",
    description: "Read a curated repo-knowledge markdown or JSONL file by relative path.",
    inputSchema: {
      path: z.string().min(1),
      maxChars: z.number().int().min(200).max(40000).optional(),
    },
  },
  async ({ path: relativePath, maxChars = 12000 }) => {
    const result = await readCuratedFile(relativePath, maxChars);

    return {
      content: [{ type: "text", text: result.content }],
      structuredContent: {
        path: result.relativePath,
        length: result.length,
        truncated: result.truncated,
      },
    };
  }
);

server.registerTool(
  "repo_spec_catalog",
  {
    title: "Query spec catalog",
    description: "Query PLAN/spec-catalog.jsonl by ticket id, status, priority, or tier.",
    inputSchema: {
      id: z.string().regex(/^S\d{4}$/).optional(),
      status: z.enum([
        "Draft",
        "Approved",
        "Tactical",
        "In Progress",
        "Implemented",
        "Verified",
        "Partial",
        "Broken",
        "BlockByOtherTask",
        "BlockNeedUserTest",
        "BlockQuestions",
        "BlockExternal",
        "Archived",
      ]).optional(),
      minPriority: z.number().int().min(0).max(100).optional(),
      tier: z.number().int().min(0).max(4).optional(),
      limit: z.number().int().min(1).max(100).optional(),
    },
  },
  async ({ id, status, minPriority, tier, limit = 20 }) => {
    const records = await readSpecCatalogRecords();
    const filtered = records
      .filter((record) => !id || record.id === id)
      .filter((record) => !status || record.status === status)
      .filter((record) => minPriority === undefined || Number(record.priority) >= minPriority)
      .filter((record) => tier === undefined || Number(record.tier) === tier)
      .sort((left, right) => {
        const priorityDelta = Number(right.priority) - Number(left.priority);
        if (priorityDelta !== 0) {
          return priorityDelta;
        }

        return `${right.updated ?? ""}`.localeCompare(`${left.updated ?? ""}`);
      })
      .slice(0, limit);

    return {
      content: [{ type: "text", text: renderSpecCatalogResults(filtered) }],
      structuredContent: {
        filters: {
          id: id ?? null,
          status: status ?? null,
          minPriority: minPriority ?? null,
          tier: tier ?? null,
          limit,
        },
        returned: filtered.length,
        records: filtered,
      },
    };
  }
);

async function main() {
  await fs.access(workspaceRoot);
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error("Repo Knowledge MCP failed to start:", error);
  process.exit(1);
});