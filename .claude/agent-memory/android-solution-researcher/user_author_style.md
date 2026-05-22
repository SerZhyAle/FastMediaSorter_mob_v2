---
name: user-author-style
description: Author style ritual - use `..` instead of `...`; use `ё`/`Ё` in Russian; these are intentional style choices, not typos
metadata:
  type: user
---

User-mandated style for every user-facing artefact produced by this agent (research reports, chat replies, citations):

- Ellipsis: `..` (two dots), never `...`.
- Always use `ё`/`Ё` in Russian where grammatically correct (e.g. `всё`, `ещё`, `приём`).

**Why:** Intentional author style codified in `CLAUDE.md` § "Author Style" and in user memory. Treating them as typos and "fixing" them silently is a regression the user has flagged repeatedly.

**How to apply:** Apply uniformly to Russian chat replies AND to the English-language research report body. When quoting code or file content verbatim, keep the original punctuation - this rule is for free-form prose the agent writes, not for quoted material.
