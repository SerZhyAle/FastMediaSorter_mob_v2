---
name: pwsh-efficiency
description: Always use `pwsh -NoProfile`; batch related script calls into one process; use `scripts/catalog_sync.ps1` instead of separate scan/render
metadata:
  type: feedback
---

Every `pwsh` invocation is a fresh process - shell state, modules, variables do NOT persist between Bash/PowerShell tool calls. PowerShell 7 cold start on Windows is 200..500 ms; chaining 100+ calls per turn makes startup overhead dominate real work.

**Why:** user explicitly flagged that the agent spawns ~150 PowerShell sessions per request. Each one re-initialises the shell. He asked: make this a rule for skills and agents, not a per-turn habit.

**How to apply:**
- **Always pass `-NoProfile`** to `pwsh`. No project script depends on the user's `$PROFILE` and profile loading adds ~200 ms with zero benefit. Wrong: `pwsh -File foo.ps1`. Right: `pwsh -NoProfile -File foo.ps1`.
- **Batch related read-only queries** into one tool invocation when researching. E.g. several `query.ps1` calls with different filters → one `pwsh -NoProfile -Command "& { ./query.ps1 ...; ./query.ps1 ...; ./query.ps1 ... }"`. Two cold starts → one.
- The research agent does NOT run `catalog_sync.ps1`, `scan.ps1`, or `render.ps1` - those are write-mode tools owned by writer agents. The researcher consumes the already-rendered `dev/CATALOG/<module>.jsonl` / `.md` and uses `query.ps1` for semantic lookups only.
- **Independent commands** in the same tool call use `;` not `&&`.
- **Do NOT invent** background-daemon / long-running-shell workarounds.

Rule lives in CLAUDE.md → "PowerShell Efficiency" section. See also [[no-backticks-in-bash-args]] and [[pwsh-bash-dollar-escape-trap]] for related Bash-arg pitfalls.
