---
name: writing-style-dashes-yo-ellipsis
description: Owner's text standard - hyphen not em-dash, ё not е, .. not ...; docs prose + UI text ONLY, never code/specs/commands/logs/chat
type: feedback
---

Owner's writing standard applies to documentation prose and user-visible UI strings ONLY:

1. Hyphen `-`, never the long dash `—` (em-dash) / `–` (en-dash) / `―` (horizontal bar). Replace any long dash with a plain hyphen.
2. Russian `ё`/`Ё` wherever grammatically correct (всё, ещё, её, нём, идёт..), never bare `е` in those positions.
3. Ellipsis `..`, never `...` (or longer runs of dots).

**Scope clarification (2026-06-26):** Owner narrowed these three rules to text in documentation or the UI ONLY. Verbatim: «речь идёт только о тексте в документации или юзеринтерфейсе! никогда не о коде или технических спецификациях - там мне на это насрать». Owner's own follow-up edit to CLAUDE.md §1 / AGENTS.md §2 made the exclusion list explicit: NEVER enforce in code, technical/tactical specs, commands, logs, or chat. So do NOT spend effort on `..`/ё/no-em-dash inside `.kt`/code, `PLAN/Sxxxx_*.md` spec bodies, command lines, log strings, or RU chat.

**Caveat:** CLAUDE.md Rule 19 (neuroslop) still independently bans long dashes `—`/`–`/`―` in `.kt` as an AI-tell gate - that is a code-quality gate, separate from this style preference, and remains in force.

**Why:** Owner stated the original standard as «мой стандарт» on 2026-06-14; on 2026-06-26 clarified it is purely a presentation rule for human-facing text, not code/specs.

**How to apply:** On documentation `.md` prose and `strings.xml` user-facing values only - self-check before saving. Do NOT spend effort applying it to code, spec bodies, commands, logs, or chat. When editing an existing doc/UI file, fix stray `—`/`–`/`...` in lines I touch; no unrequested project-wide sweep.
