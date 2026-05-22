---
name: feedback_timestamp_in_chat
description: Current time is auto-injected via UserPromptSubmit hook - no need to call Bash for it
metadata:
  type: feedback
---

**The hook handles time injection automatically.** A `UserPromptSubmit` hook in `~/.claude/settings.json` runs `date +"%H:%M:%S"` on every user message and injects the result into model context as `additionalContext`. Claude sees "Current time: HH:MM:SS" automatically.

**Do NOT call `date` via Bash at the start of each response** - that wastes tokens and the user explicitly flagged it as unnecessary.

**Why:** LLM has no clock; user wants real wall-clock timestamps to track time spent per step. Hook approach costs zero extra tokens.

**How to apply:** Before each chat response that opens a coding step, read the injected `Current time:` from context and use it as the `[HH:MM:SS]` prefix. Never spawn a `date` / `Get-Date` Bash/PowerShell call to fetch the time - it is already in context. If context shows no time (first session before hook fired), omit the prefix rather than guessing.
