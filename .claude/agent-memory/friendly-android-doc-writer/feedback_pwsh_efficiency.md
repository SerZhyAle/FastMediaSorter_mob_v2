---
name: pwsh-efficiency
description: Always use `pwsh -NoProfile`; batch related script calls into one process; use `scripts/catalog_sync.ps1` instead of separate scan/render
metadata:
  type: feedback
---

Every `pwsh` invocation is a fresh process - shell state, modules, variables do NOT persist between Bash/PowerShell tool calls. PowerShell 7 cold start on Windows is 200..500 ms; chaining 100+ calls per turn makes startup overhead dominate real work.

**Why:** user explicitly flagged that the agent spawns ~150 PowerShell sessions per request. Each one re-initialises the shell. He asked: make this a rule for skills and agents, not a per-turn habit.

**How to apply:** When invoking the string-localisation audit (`scripts/check_strings_localized.ps1`) or any dev-log script after a docs edit, always pass `-NoProfile`. Batch the audit and the dev-log call into a single PowerShell `-Command` chain rather than two separate tool invocations - one cold start instead of two, and the chain documents the closure ritual in one place. Wrong: a tool call for `add_to_dev_log.ps1`, then a second tool call for `check_strings_localized.ps1`. Right: one tool call that runs both via `pwsh -NoProfile -Command '& { ./scripts/add_to_dev_log.ps1 ..; ./scripts/check_strings_localized.ps1 .. }'`. Independent commands inside the same call use `;`, not `&&`. Do not invent background-daemon workarounds - if overhead still hurts after these rules, raise it as a tooling task, not a local hack.
