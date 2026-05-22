---
name: feedback_pwsh_efficiency
description: Always use `pwsh -NoProfile`; batch related script calls into one process; use `scripts/catalog_sync.ps1` instead of separate scan/render
metadata:
  type: feedback
---

Every `pwsh` invocation is a fresh process — shell state, modules, variables do NOT persist between Bash/PowerShell tool calls. PowerShell 7 cold start on Windows is 200..500 ms; chaining 100+ calls per turn makes startup overhead dominate real work.

**Why:** user explicitly flagged that the agent spawns ~150 PowerShell sessions per request. Each one re-initialises the shell. He asked: make this a rule for skills and agents, not a per-turn habit.

**How to apply:**
- **Always pass `-NoProfile`** to every `pwsh` call I issue (post-change dev log, catalog sync, spec-catalog update, builders). No project script depends on the user's `$PROFILE` and profile loading adds ~200 ms with zero benefit. Wrong: `pwsh -File foo.ps1`. Right: `pwsh -NoProfile -File foo.ps1`.
- **Use the wrapper** `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` for the catalogue ritual after every `.kt` edit. Never call `scan.ps1` then `render.ps1` in two separate tool invocations - that doubles the cold-start cost.
- **Batch related script calls** into one tool invocation via `pwsh -NoProfile -Command "& { ./a.ps1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./b.ps1 }"`. Two cold starts → one. (See [[feedback_pwsh_bash_dollar_escape_trap]] for the bash-quoting gotcha when launching this from the Bash tool.)
- **Independent commands** in the same tool call use `;` not `&&`.
- **Do NOT invent** background-daemon / long-running-shell workarounds. If overhead remains painful after these rules, raise it as an MCP-server proposal — not a local hack.

Rule lives in CLAUDE.md → "PowerShell Efficiency" section. See also [[feedback_no_backticks_in_bash_args]] for related Bash-arg pitfalls.
