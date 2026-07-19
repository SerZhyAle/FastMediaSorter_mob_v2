---
description: "Use when committing in caveman mode - terse EN commit message, minimal ceremony. Triggers: 'caveman commit', a quick commit with filler dropped."
model: sonnet
---

# Caveman Commit

> **LOCAL DIRECTIVES:**
> 1. Commit messages in ENGLISH.
> 2. `..` (two dots) - never `...`.
> 3. Generate the message only. Do NOT run `git commit`, stage files, or amend history.
> 4. Repo safety rules win. For breaking changes, security fixes, data migrations, or reverts, include enough context even if it costs more words.

Generate terse Conventional Commit message, minimal noise, exact intent.

## Usage

```text
/caveman-commit [optional: context]
```

Examples:
- `/caveman-commit` - infer from current diff if available
- `/caveman-commit fix network thumbnail retry` - generate from explicit context

## Process

On invoke with `$ARGUMENTS`:

1. User already described change → use that description.
2. User did not describe → inspect current git diff/status as needed to infer message.
3. Subject in Conventional Commits format:
   - `<type>(<scope>): <imperative summary>`
   - `<scope>` optional
   - Prefer `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `chore`, `build`, `ci`, `style`, `revert`
4. Keep subject terse:
   - target `<= 50` chars when practical
   - hard cap `72` chars
   - no trailing period
5. Add body only when why is not obvious from subject.
6. Always add body for: breaking changes, security fixes, data migrations, reverts.
7. Body lines concise, wrap near 72 chars.
8. Never include AI attribution or filler phrases.

## Output Rules

- Output commit message as fenced code block ready to paste.
- Do not explain obvious diff.
- Keep message exact, terse, technically complete.
