---
name: check-existing-tooling-first
description: Before authoring any new repo script/tool, grep scripts/ (incl. scripts/utils/) AND the skills for an existing helper; extend it rather than create a parallel one
metadata:
  type: feedback
---

Before writing a new repo script or helper tool, search for an existing one and extend it instead of creating a parallel tool.

**Why:** I created `scripts/strings_tool.ps1` for string mutation without checking `scripts/utils/`, where `set-android-string.ps1` already existed and was already wired into skills (`/spec-dev`, `/doc-update`). The result was two overlapping tools. The owner chose consolidation; the duplicate was deleted. This is exactly the CLAUDE.md research-order + Rule 14 (internal script ownership) failure mode.

**How to apply:** before authoring tooling, run two checks - (1) grep `scripts/` and `scripts/utils/` for a script in the same domain; (2) grep `.claude/commands/` (skills) for a script already referenced for that job. If one exists, extend it (add `-Action` verbs, params) keeping its existing invocations backward-compatible, rather than starting fresh. Only create a new file when no overlapping helper exists.
