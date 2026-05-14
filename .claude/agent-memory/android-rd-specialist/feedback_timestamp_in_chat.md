---
name: feedback-timestamp-in-chat
description: Current time is auto-injected via UserPromptSubmit hook — no need to call Bash for it
metadata:
  type: feedback
---

**The hook handles time injection automatically.** A `UserPromptSubmit` hook in `~/.claude/settings.json` runs `date +"%H:%M:%S"` on every user message and injects the result into model context as `additionalContext`. Claude sees "Current time: HH:MM:SS" automatically.

**Do NOT call `date` via Bash at the start of each response** — that wastes tokens and the user explicitly flagged it as unnecessary.

**Why:** LLM has no clock; user wants real wall-clock timestamps to track time spent per step. Hook approach costs zero extra tokens.

**How to apply:** Just read the injected time from context and use it in `[HH:MM:SS]` prefix. If context shows no time (e.g. first session before hook fired), omit the prefix rather than guessing.
