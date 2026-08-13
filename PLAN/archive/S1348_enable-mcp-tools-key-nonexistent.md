# Стратегическая спецификация: S1348 - enable_mcp_tools key nonexistent

**Ticket:** S1348
**Status:** Archived
**Priority:** 35
**Date:** 2026-08-01
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-08-01
**Tactical spec:** none - Simple path, phase inline below.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-01

**Текст:**

CLAUDE.md section 6 says "Subagent MCP isolation: When defining a subagent, always set enable_mcp_tools to false unless the subagent strictly requires driving the UI/emulator." S1341 section 4.2 also says "Set enable_mcp_tools: false on every new agent except android-device-operator, per CLAUDE.md section 6."

During S1341 tactical execution (2026-08-01), before writing two new agent-definition files, I checked whether `enable_mcp_tools` is a real, functioning control point: grepped every existing `.claude/agents/*.md` file (4 total: android-kotlin-developer, android-rd-specialist, android-solution-researcher, friendly-android-doc-writer) for the literal key - zero matches, none of them set it. The Agent tool's own JSONSchema (visible in this session's tool list) accepts only `description`, `isolation`, `model`, `prompt`, `run_in_background`, `subagent_type` as parameters - no `enable_mcp_tools` field anywhere.

This suggests the instruction may be describing a control that does not exist in the current Claude Code harness's agent-definition schema or Agent tool - either stale text carried over from a different agent-authoring system (e.g. a different SDK context), or guidance that was written but never actually validated against the real schema. The demonstrated, real mechanism this project actually uses to restrict a subagent's tool surface is the `tools:` frontmatter key (see `android-solution-researcher.md`: `tools: Read, Grep, Glob, Bash` - no MCP tools listed, which is the real way that agent is kept off mobile-mcp).

Out of S1341's own contract (S1341 asks to *apply* enable_mcp_tools, not to validate whether it exists as a mechanism) and non-trivial (needs research into whether this is a documented Claude Agent SDK option available in some other invocation context, or purely aspirational text to correct in CLAUDE.md section 6). Draft a ticket to investigate and either find the real mechanism (fix the docs to point at it, e.g. `tools:` restriction) or correct CLAUDE.md/S1341-family text that references a non-existent control.

**Вложения:** нет

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1341 (обнаружено при его выполнении, использует эту же формулировку в §4.2)
- **Scope:** `CLAUDE.md` section 6, `AGENTS.md` (parallel ruleset, "sync AGENTS.md too" per CLAUDE.md's
  own header). No code, no `.claude/agents/*.md` fix needed - already verified clean below.
- **Flavors:** n/a - repository instructions, not shipped in any APK.

---

## Research: does `enable_mcp_tools` exist anywhere in Claude Code

Resolved via `claude-code-guide` agent against official docs (`code.claude.com/docs/en/agent-sdk/subagents.md`)
and the GitHub issue tracker. **It does not exist as a real, currently-functioning configuration key**
in any Claude Code surface - not the `.claude/agents/*.md` frontmatter schema, not
`.claude/settings.json`, not the Agent SDK. It appears to be conflated with two OPEN, unimplemented
GitHub feature requests (anthropics/claude-code#34935, #6915) proposing a scoped MCP-access
mechanism for subagents that never shipped.

The real, documented mechanisms:

- **`tools:` frontmatter allowlist** (what this repo's own agent files already use) - listing
  explicit tool names grants ONLY those; omitting `tools:` grants everything the parent session has,
  MCP-registered tools included.
- **SDK-only `disallowedTools`** with `mcp__*`/`mcp__<server>`/`mcp__<server>__<tool>` patterns - not
  applicable to `.md` frontmatter files, only programmatic SDK agent definitions.

Independently re-verified this repo's own `.claude/agents/*.md` (6 files, not the 4 the raw capture
saw - S1341 added `android-device-operator.md` and `repo-mechanic.md` since): zero files reference
`enable_mcp_tools` anywhere. The two device/build-facing agents (`android-device-operator`,
`repo-mechanic`) both already correctly restrict `tools: Bash, Read, Grep, Glob` (no MCP tools
listed) - S1341's actual IMPLEMENTATION used the real mechanism even though its own spec TEXT (§4.2)
cited the fictional field name. The three broader R&D/implementation agents
(`android-kotlin-developer`, `android-rd-specialist`, `friendly-android-doc-writer`) correctly omit
`tools:` entirely, since they legitimately need full access. **No functional bug exists anywhere in
the actual agent definitions - this is a pure documentation correction.**

## Goal

`CLAUDE.md` §6 и `AGENTS.md` ссылаются на несуществующий ключ `enable_mcp_tools`. Заменяем формулировку
на реальный механизм - явный allowlist `tools:` во frontmatter агента, который уже используется
корректно во всех существующих файлах `.claude/agents/*.md`. Историческую спеку S1341 не трогаем -
её собственная реализация уже была правильной, текст остаётся историческим снимком на момент
написания.

## Phase 1 - Correct the stale instruction in both rule files

- [x] `CLAUDE.md` section 6 - replace the `enable_mcp_tools` bullet with the real mechanism: an
  explicit `tools:` frontmatter allowlist that omits MCP tool names isolates a subagent from MCP;
  omitting `tools:` entirely grants full access including MCP. Keep the same exemption (UI/emulator
  driving agents may need MCP) and the same rationale (avoid duplicate Node/MCP server instances).
- [x] `AGENTS.md` - apply the identical correction, per CLAUDE.md's own header rule to keep the two
  rule files in sync.
- **Verification:** `Grep "set .enable_mcp_tools. to .false."` (the old instruction-to-act-on-it wording)
  in both files returns zero hits; `Grep "explicit .tools:. frontmatter allowlist"` matches once in
  each, confirming the corrected text names the real mechanism. The literal string `enable_mcp_tools`
  deliberately survives once per file, inside a "this is not a real option, do not use it" warning -
  removing all mention would just let the same mistake happen again to the next reader.

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact spec, Simple path)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Research resolved via `claude-code-guide` against official docs: `enable_mcp_tools` is not a real
Claude Code option anywhere (frontmatter schema, settings.json, Agent SDK) - conflated with two open,
unimplemented GitHub feature requests. `CLAUDE.md` section 6 and `AGENTS.md` both corrected to name
the real mechanism (explicit `tools:` frontmatter allowlist), keeping a one-line "not real, do not
use it" warning so the same mistake does not recur. Verified: the old instruction-to-act-on-it
wording (`set enable_mcp_tools to false`) has zero hits in both files; the corrected phrase
("explicit `tools:` frontmatter allowlist") matches exactly once in each. Document-registry sibling
check (`repository-rules` group) confirmed `GEMINI.md`, `.github/copilot-instructions.md`, and every
`.claude/agents/*.md`/`commands/*.md`/`reference/*.md`/`templates/*.md`/`skills/*/SKILL.md` file
carries zero mentions of the fictional key - nothing else needed the fix. Independently re-confirmed
this repo's 6 agent definition files (not the 4 the original raw capture saw) are already correctly
configured via `tools:` allowlists or intentional omission - no functional bug, pure documentation
correction. `post-change.ps1 -ScopeToFile -RegistryAck repository-rules`: PASS.

### Manual / on-device

- None. This ticket has no on-device or build-verifiable surface.
