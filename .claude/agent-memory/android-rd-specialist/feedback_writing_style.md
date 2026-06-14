---
name: writing-style-dashes-yo-ellipsis
description: Owner's text standard - hyphen not em-dash, ё not е, .. not ...; apply to all text I produce
type: feedback
---

Owner's writing standard for ALL text I author - chat (RU), docs, specs, commit messages, memory files, log/changelog entries:

1. Hyphen `-`, never the long dash `—` (em-dash) or `–` (en-dash). Replace any long dash with a plain hyphen.
2. Russian `ё`/`Ё` wherever grammatically correct (всё, ещё, её, нём, идёт..), never bare `е` in those positions.
3. Ellipsis `..`, never `...` (or longer runs of dots).

**Why:** Owner stated this verbatim as «мой стандарт» on 2026-06-14 and asked to always do exactly this. CLAUDE.md already mandates `..` and ё; the new/easy-to-miss part is the em-dash -> hyphen rule, which I had been violating in my own memory index and docs.

**How to apply:** On every piece of text I write or edit. Self-check before sending chat and before saving any `.md`/spec/commit. When editing an existing file, fix stray `—`/`–`/`...` in the lines I touch. Do not run an unrequested project-wide sweep - apply going forward and offer a scoped cleanup if the owner wants past text fixed.
