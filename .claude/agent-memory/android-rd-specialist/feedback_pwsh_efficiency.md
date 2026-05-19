---
name: pwsh-efficiency
description: Always use `pwsh -NoProfile`; batch related script calls into one process; use `scripts/catalog_sync.ps1` instead of separate scan/render
metadata:
  type: feedback
---

Every `pwsh` invocation is a fresh process — shell state, modules, variables do NOT persist between Bash/PowerShell tool calls. PowerShell 7 cold start on Windows is 200..500 ms; chaining 100+ calls per turn makes startup overhead dominate real work.

**Why:** user explicitly flagged that the agent spawns ~150 PowerShell sessions per request. Each one re-initialises the shell. He asked: make this a rule for skills and agents, not a per-turn habit.

**How to apply:**
- **Always pass `-NoProfile`** to `pwsh`. No project script depends on the user's `$PROFILE` and profile loading adds ~200 ms with zero benefit. Wrong: `pwsh -File foo.ps1`. Right: `pwsh -NoProfile -File foo.ps1`.
- **Batch related script calls** into one tool invocation via `pwsh -NoProfile -Command "& { ./a.ps1; if (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }; ./b.ps1 }"`. Two cold starts → one.
- **Use the wrapper** `scripts/catalog_sync.ps1 -Module <app_v2|wear>` for the catalogue ritual instead of separate `scan.ps1` + `render.ps1` calls. If you see a frequently-chained ritual without a wrapper, create one in `scripts/` (single-purpose, fail-fast on `$LASTEXITCODE`) and add it to the wrapper table in CLAUDE.md.
- **Independent commands** in the same tool call use `;` not `&&`.
- **Do NOT invent** background-daemon / long-running-shell workarounds. If overhead remains painful after these rules, raise it as an MCP-server proposal — not a local hack.

Rule lives in CLAUDE.md → "PowerShell Efficiency" section. See also [[no-backticks-in-bash-args]] for related Bash-arg pitfalls.
