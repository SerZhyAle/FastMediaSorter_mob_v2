---
agent: "agent"
description: "Use when: user asks for a caveman commit message, terse commit message, minimal commit text, or asks to run /caveman-commit. Triggers on: caveman commit, terse commit, short commit message, minimal commit, compressed commit message."
---

# Caveman Commit

> **LOCAL EXECUTION DIRECTIVES:**
> 1. Output commit messages in ENGLISH.
> 2. Use `..` (two dots) — never `...`.
> 3. Generate the message only. Do NOT run `git commit`, do NOT stage files, do NOT amend history.
> 4. Repo safety rules win. For breaking changes, security fixes, data migrations, or reverts, include enough context even if it costs more words.

Generate a terse Conventional Commit message with minimal noise and exact intent.

## Usage

```text
/caveman-commit [optional: context]
```

Examples:
- `/caveman-commit` — infer from current diff if available
- `/caveman-commit fix network thumbnail retry` — generate from explicit context

## Process

When this command is invoked with `$ARGUMENTS`:

1. If the user already described the change, use that description.
2. If the user did not describe the change, inspect current git diff/status as needed to infer the commit message.
3. Generate subject in Conventional Commits format:
   - `<type>(<scope>): <imperative summary>`
   - `<scope>` optional
   - Prefer `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `chore`, `build`, `ci`, `style`, `revert`
4. Keep the subject terse:
   - target `<= 50` chars when practical
   - hard cap `72` chars
   - no trailing period
5. Add a body only when the why is not obvious from the subject.
6. Always add a body for:
   - breaking changes
   - security fixes
   - data migrations
   - reverts
7. Keep body lines concise and wrap near 72 chars.
8. Never include AI attribution or filler phrases.

## Output Rules

- Output the commit message as a fenced code block ready to paste.
- Do not explain the obvious diff.
- Keep the message exact, terse, and technically complete.