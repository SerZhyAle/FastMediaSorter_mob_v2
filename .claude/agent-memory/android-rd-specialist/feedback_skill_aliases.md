---
name: skill-aliases
description: Owner-defined short aliases for slash-command skills; expand to the real Skill call
metadata:
  type: feedback
---

When the owner types one of these short aliases as a slash command, treat it as the canonical skill and invoke it via the Skill tool.

**Why:** Owner wants faster invocation of the spec-pipeline skills without typing full names.

**How to apply:** Match the alias exactly (leading `/` optional). Expand to the canonical skill, then run it. Do NOT invent new single-letter aliases beyond this table - they collide (e.g. `/c` is compact, so spec-check must not also claim `/c`). Ask the owner before adding aliases for skills not listed here.

Canonical -> aliases:
- `spec-all` <- `spc-all`, `spc-a`, `all`, `a`
- `spec-next` <- `spc-nxt`, `spc-n`, `next`, `n`
- `compact` <- `cmp`, `c`
- `spec-quiz` <- `spc-q`, `quiz`, `q`
- `spec-tech` <- `spc-t`, `tech`, `t`
- `spec-dev` <- `spc-d`, `dev`, `d`
- `spec-draft` <- `spc-draft`, `draft`, `sd`

Note: `compact` here is the built-in `/compact`, not a Skill-tool skill - handle as the CLI command.
Note: these chat aliases are unrelated to the `.\a.ps1` bash launcher verbs (`d`=debug build, `t`=..); different namespace, no conflict.
