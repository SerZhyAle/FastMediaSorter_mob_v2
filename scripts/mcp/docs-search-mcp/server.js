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
const docsRoot = process.env.DOCS_ROOT
  ? path.resolve(process.env.DOCS_ROOT)
  : path.join(workspaceRoot, "docs");

const server = new McpServer({
  name: "fastmediasorter-docs-search",
  version: "1.0.0",
});

function normalizePath(p) {
  return p.replace(/\\/g, "/");
}

function detectLanguage(fileName) {
  if (/_RU\.md$/i.test(fileName)) {
    return "ru";
  }
  if (/_UK\.md$/i.test(fileName)) {
    return "uk";
  }
  if (/_EN\.md$/i.test(fileName)) {
    return "en";
  }
  return "en";
}

function getMirrorBase(fileName) {
  return fileName.replace(/_(RU|UK|EN)\.md$/i, "").replace(/\.md$/i, "");
}

function tokenizeQuery(query) {
  return query
    .toLowerCase()
    .split(/\s+/)
    .map((token) => token.trim())
    .filter(Boolean);
}

function scoreText(haystack, tokens) {
  const lower = haystack.toLowerCase();
  let score = 0;

  for (const token of tokens) {
    if (lower.includes(token)) {
      score += 1;
    }
  }

  return score;
}

function normalizeHeading(heading) {
  // Normalize punctuation/case to compare equivalent headings across language mirrors.
  return heading
    .toLowerCase()
    .replace(/[`*_~]/g, "")
    .replace(/\s+/g, " ")
    .replace(/[.,:;!?()[\]{}]/g, "")
    .trim();
}

function countContentChars(content) {
  // Compare translation density by meaningful text size, not by whitespace formatting noise.
  return content.replace(/\s+/g, "").length;
}

async function listMarkdownFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const nested = await listMarkdownFiles(full);
      files.push(...nested);
      continue;
    }

    if (entry.isFile() && /\.md$/i.test(entry.name)) {
      files.push(full);
    }
  }

  return files;
}

function extractTitle(content, fallbackName) {
  const heading = content.match(/^#\s+(.+)$/m);
  if (heading?.[1]) {
    return heading[1].trim();
  }
  return fallbackName;
}

function splitSections(content) {
  const lines = content.split(/\r?\n/);
  const sections = [];

  let current = {
    heading: "Document",
    level: 1,
    line: 1,
    body: "",
  };

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const headerMatch = line.match(/^(#{1,3})\s+(.+)$/);

    if (headerMatch) {
      sections.push(current);
      current = {
        heading: headerMatch[2].trim(),
        level: headerMatch[1].length,
        line: i + 1,
        body: "",
      };
    } else {
      current.body += `${line}\n`;
    }
  }

  sections.push(current);
  return sections.filter((s) => s.body.trim().length > 0 || s.heading !== "Document");
}

async function buildIndex() {
  const mdFiles = await listMarkdownFiles(docsRoot);
  const documents = [];

  for (const absPath of mdFiles) {
    const relPath = normalizePath(path.relative(workspaceRoot, absPath));
    const fileName = path.basename(absPath);
    const language = detectLanguage(fileName);
    const mirrorBase = getMirrorBase(fileName);

    const content = await fs.readFile(absPath, "utf8");
    const title = extractTitle(content, fileName);
    const sections = splitSections(content);

    documents.push({
      absPath,
      relPath,
      fileName,
      language,
      mirrorBase,
      title,
      content,
      sections,
    });
  }

  return documents;
}

function toTextSearchResult(results) {
  if (!results.length) {
    return "No matches found in docs/.";
  }

  return results
    .map((item, idx) => {
      const snippet = item.snippet.replace(/\s+/g, " ").trim().slice(0, 260);
      return [
        `${idx + 1}. [${item.score}] ${item.title}`,
        `   file: ${item.relPath}`,
        `   lang: ${item.language} | section: ${item.section} | line: ${item.line}`,
        `   ${snippet}`,
      ].join("\n");
    })
    .join("\n\n");
}

server.registerTool(
  "docs_search",
  {
    title: "Search documentation",
    description:
      "Search local docs/*.md with EN/RU/UK awareness and return ranked section matches.",
    inputSchema: {
      query: z.string().min(2),
      language: z.enum(["any", "en", "ru", "uk"]).optional(),
      limit: z.number().int().min(1).max(50).optional(),
    },
  },
  async ({ query, language = "any", limit = 10 }) => {
    const docs = await buildIndex();
    const tokens = tokenizeQuery(query);

    const matches = [];

    for (const doc of docs) {
      if (language !== "any" && doc.language !== language) {
        continue;
      }

      for (const section of doc.sections) {
        const headingScore = scoreText(section.heading, tokens) * 3;
        const bodyScore = scoreText(section.body, tokens);
        const titleScore = scoreText(doc.title, tokens) * 2;
        const fileScore = scoreText(doc.fileName, tokens);
        const score = headingScore + bodyScore + titleScore + fileScore;

        if (score <= 0) {
          continue;
        }

        matches.push({
          score,
          title: doc.title,
          relPath: doc.relPath,
          language: doc.language,
          section: section.heading,
          line: section.line,
          snippet: section.body,
        });
      }
    }

    matches.sort((a, b) => b.score - a.score);
    const top = matches.slice(0, limit);

    return {
      content: [
        {
          type: "text",
          text: toTextSearchResult(top),
        },
      ],
      structuredContent: {
        query,
        totalMatches: matches.length,
        returned: top.length,
        results: top,
      },
    };
  }
);

server.registerTool(
  "docs_list_mirrors",
  {
    title: "List EN/RU/UK mirrors",
    description:
      "List documentation mirror groups and show which languages exist for each base document.",
    inputSchema: {
      onlyMissing: z.boolean().optional(),
    },
  },
  async ({ onlyMissing = false }) => {
    const docs = await buildIndex();
    const grouped = new Map();

    for (const doc of docs) {
      if (!grouped.has(doc.mirrorBase)) {
        grouped.set(doc.mirrorBase, {
          mirrorBase: doc.mirrorBase,
          files: {},
        });
      }

      grouped.get(doc.mirrorBase).files[doc.language] = doc.relPath;
    }

    const result = Array.from(grouped.values())
      .map((item) => {
        const langs = ["en", "ru", "uk"];
        const missing = langs.filter((lang) => !item.files[lang]);
        return {
          mirrorBase: item.mirrorBase,
          files: item.files,
          missing,
        };
      })
      .filter((row) => (onlyMissing ? row.missing.length > 0 : true))
      .sort((a, b) => a.mirrorBase.localeCompare(b.mirrorBase));

    const text = result.length
      ? result
        .map((row, idx) => {
          const en = row.files.en ?? "-";
          const ru = row.files.ru ?? "-";
          const uk = row.files.uk ?? "-";
          const missing = row.missing.length ? row.missing.join(", ") : "none";
          return `${idx + 1}. ${row.mirrorBase}\n   en: ${en}\n   ru: ${ru}\n   uk: ${uk}\n   missing: ${missing}`;
        })
        .join("\n\n")
      : "No mirror groups found for requested filter.";

    return {
      content: [{ type: "text", text }],
      structuredContent: {
        totalGroups: result.length,
        groups: result,
      },
    };
  }
);

server.registerTool(
  "docs_read",
  {
    title: "Read documentation file",
    description: "Read a local docs markdown file by relative path.",
    inputSchema: {
      path: z.string().min(1),
      maxChars: z.number().int().min(200).max(40000).optional(),
    },
  },
  async ({ path: relativePath, maxChars = 12000 }) => {
    const normalized = normalizePath(relativePath).replace(/^\/+/, "");

    if (!normalized.startsWith("docs/")) {
      throw new Error("Path must be inside docs/ (for example: docs/FEATURES.md)");
    }

    if (!/\.md$/i.test(normalized)) {
      throw new Error("Only markdown files are allowed.");
    }

    const abs = path.resolve(workspaceRoot, normalized);
    const relCheck = normalizePath(path.relative(workspaceRoot, abs));

    if (!relCheck.startsWith("docs/")) {
      throw new Error("Path escapes docs/ and is not allowed.");
    }

    const content = await fs.readFile(abs, "utf8");
    const truncated = content.length > maxChars;
    const text = truncated ? `${content.slice(0, maxChars)}\n\n[TRUNCATED]` : content;

    return {
      content: [{ type: "text", text }],
      structuredContent: {
        path: relCheck,
        length: content.length,
        truncated,
      },
    };
  }
);

server.registerTool(
  "docs_diff_mirrors",
  {
    title: "Diff documentation mirrors",
    description:
      "Compare EN/RU/UK mirror files by headings and content density (length/section count).",
    inputSchema: {
      mirrorBase: z.string().min(1).optional(),
      onlyWithDiffs: z.boolean().optional(),
      mode: z.enum(["headings", "density", "both"]).optional(),
      lagThreshold: z.number().min(0.1).max(1).optional(),
    },
  },
  async ({ mirrorBase, onlyWithDiffs = true, mode = "both", lagThreshold = 0.7 }) => {
    const docs = await buildIndex();
    const grouped = new Map();

    for (const doc of docs) {
      if (!grouped.has(doc.mirrorBase)) {
        grouped.set(doc.mirrorBase, {
          mirrorBase: doc.mirrorBase,
          docsByLang: {},
        });
      }
      grouped.get(doc.mirrorBase).docsByLang[doc.language] = doc;
    }

    const groups = Array.from(grouped.values())
      .filter((group) => !mirrorBase || group.mirrorBase === mirrorBase)
      .sort((a, b) => a.mirrorBase.localeCompare(b.mirrorBase));

    const diffRows = [];

    for (const group of groups) {
      const langs = ["en", "ru", "uk"];
      const presentLangs = langs.filter((lang) => group.docsByLang[lang]);
      const missingLangs = langs.filter((lang) => !group.docsByLang[lang]);

      const referenceLang = group.docsByLang.en ? "en" : presentLangs[0];
      const referenceDoc = referenceLang ? group.docsByLang[referenceLang] : null;

      if (!referenceDoc) {
        continue;
      }

      const referenceHeadings = new Set(
        referenceDoc.sections.map((section) => normalizeHeading(section.heading)).filter(Boolean)
      );
      const referenceSectionCount = referenceDoc.sections.length;
      const referenceChars = countContentChars(referenceDoc.content);

      const headingDiff = {};
      const densityDiff = {};
      let hasHeadingDiff = false;
      let hasDensityDiff = false;

      for (const lang of presentLangs) {
        const doc = group.docsByLang[lang];
        const currentHeadings = new Set(
          doc.sections.map((section) => normalizeHeading(section.heading)).filter(Boolean)
        );

        const missingFromCurrent = Array.from(referenceHeadings).filter(
          (heading) => !currentHeadings.has(heading)
        );
        const extraInCurrent = Array.from(currentHeadings).filter(
          (heading) => !referenceHeadings.has(heading)
        );

        if (missingFromCurrent.length > 0 || extraInCurrent.length > 0) {
          hasHeadingDiff = true;
        }

        headingDiff[lang] = {
          missingFromCurrent,
          extraInCurrent,
        };

        const sectionCount = doc.sections.length;
        const contentChars = countContentChars(doc.content);
        const sectionRatio = referenceSectionCount > 0 ? sectionCount / referenceSectionCount : 1;
        const contentRatio = referenceChars > 0 ? contentChars / referenceChars : 1;
        const isStrongLag =
          lang !== referenceLang &&
          (sectionRatio < lagThreshold || contentRatio < lagThreshold);

        if (
          lang !== referenceLang &&
          (Math.abs(sectionRatio - 1) > 0.15 || Math.abs(contentRatio - 1) > 0.2)
        ) {
          hasDensityDiff = true;
        }

        densityDiff[lang] = {
          sectionCount,
          contentChars,
          sectionRatio,
          contentRatio,
          isStrongLag,
        };
      }

      const compareHeadings = mode === "headings" || mode === "both";
      const compareDensity = mode === "density" || mode === "both";

      const hasDiff =
        missingLangs.length > 0 ||
        (compareHeadings && hasHeadingDiff) ||
        (compareDensity && hasDensityDiff);
      if (onlyWithDiffs && !hasDiff) {
        continue;
      }

      const nonReferenceDensity = ["en", "ru", "uk"]
        .filter((lang) => lang !== referenceLang && densityDiff[lang])
        .map((lang) => densityDiff[lang]);
      const worstDensityLag = nonReferenceDensity.reduce((worst, entry) => {
        const sectionLag = Math.max(0, 1 - entry.sectionRatio);
        const contentLag = Math.max(0, 1 - entry.contentRatio);
        return Math.max(worst, sectionLag, contentLag);
      }, 0);

      diffRows.push({
        mirrorBase: group.mirrorBase,
        referenceLang,
        files: {
          en: group.docsByLang.en?.relPath,
          ru: group.docsByLang.ru?.relPath,
          uk: group.docsByLang.uk?.relPath,
        },
        missingLangs,
        headingDiff,
        densityDiff,
        densitySeverity: missingLangs.length > 0 ? 2 + missingLangs.length * 0.1 : worstDensityLag,
        hasDiff,
      });
    }

    if (mode === "density") {
      // In density mode we prioritize the most problematic mirrors first for triage speed.
      diffRows.sort((a, b) => b.densitySeverity - a.densitySeverity);
    }

    const text = diffRows.length
      ? diffRows
        .map((row, idx) => {
          const fileLine = `en=${row.files.en ?? "-"}, ru=${row.files.ru ?? "-"}, uk=${row.files.uk ?? "-"}`;
          const missingLine = row.missingLangs.length
            ? row.missingLangs.join(", ")
            : "none";

          const perLangHeadings = ["en", "ru", "uk"]
            .filter((lang) => row.headingDiff[lang])
            .map((lang) => {
              const missingCount = row.headingDiff[lang].missingFromCurrent.length;
              const extraCount = row.headingDiff[lang].extraInCurrent.length;
              return `${lang}(missing:${missingCount}, extra:${extraCount})`;
            })
            .join(", ");

          const perLangDensity = ["en", "ru", "uk"]
            .filter((lang) => row.densityDiff[lang])
            .map((lang) => {
              const entry = row.densityDiff[lang];
              const sectionRatioPct = `${(entry.sectionRatio * 100).toFixed(0)}%`;
              const contentRatioPct = `${(entry.contentRatio * 100).toFixed(0)}%`;
              const lagMark = entry.isStrongLag ? " LAG" : "";
              return `${lang}(sec:${entry.sectionCount}/${sectionRatioPct}, len:${entry.contentChars}/${contentRatioPct}${lagMark})`;
            })
            .join(", ");

          const lines = [
            `${idx + 1}. ${row.mirrorBase} (ref=${row.referenceLang})`,
            `   files: ${fileLine}`,
            `   missing languages: ${missingLine}`,
          ];

          if (mode === "headings" || mode === "both") {
            lines.push(`   heading diff: ${perLangHeadings || "none"}`);
          }

          if (mode === "density" || mode === "both") {
            lines.push(`   density diff: ${perLangDensity || "none"}`);
            lines.push(`   lag threshold: ${(lagThreshold * 100).toFixed(0)}%`);
          }

          return lines.join("\n");
        })
        .join("\n\n")
      : "No mirror differences found for requested filter.";

    return {
      content: [{ type: "text", text }],
      structuredContent: {
        mirrorBase: mirrorBase ?? null,
        onlyWithDiffs,
        mode,
        lagThreshold,
        total: diffRows.length,
        rows: diffRows,
      },
    };
  }
);

async function main() {
  try {
    await fs.access(docsRoot);
  } catch (error) {
    throw new Error(`Docs directory not found: ${docsRoot}`);
  }

  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error("Docs Search MCP failed to start:", error);
  process.exit(1);
});
