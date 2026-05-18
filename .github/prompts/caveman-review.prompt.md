---
agent: "agent"
description: "Use when: user asks for caveman review, terse code review comments, one-line review findings, compressed PR feedback, or asks to run /caveman-review. Triggers on: caveman review, terse review, one-line review, minimal review, compressed review comments."
---

# Caveman Review

> **LOCAL EXECUTION DIRECTIVES:**
> 1. Keep the review in the current chat language unless the user explicitly asks for English or paste-ready English PR comments.
> 2. Use `..` (two dots) - never `...`.
> 3. Findings first. No throat-clearing, no praise padding.
> 4. Repo safety rules win. Use fuller prose when a one-line comment would hide important security, architectural, or destructive-risk context.

Produce terse, actionable code review findings.

## Usage

```text
/caveman-review [optional: file, diff, or topic]
```

Examples:
- `/caveman-review` - review the current diff or provided context
- `/caveman-review BrowseRoutingDecisionTest` - focus on one file or symbol

## Process

When this command is invoked with `$ARGUMENTS`:

1. Review with the normal repo code-review bar: bugs, risks, regressions, missing tests first.
2. Output findings before any summary.
3. Prefer one line per finding when clarity is preserved.
4. Use this format for concise findings:
   - `<file>:L<line>: bug: <problem>. <fix>.`
   - `<file>:L<line>: risk: <problem>. <fix>.`
   - `<file>:L<line>: nit: <problem>. <fix>.`
   - `<file>:L<line>: q: <question>.`
5. Keep exact symbols, file names, line numbers, commands, APIs, and error strings unchanged.
6. If there are no findings, say that explicitly and mention residual test or validation gaps briefly.
7. If the issue is security-sensitive or architecturally non-trivial, switch to a short paragraph instead of forcing one-line compression.

## Output Rules

- No filler.
- No generic praise.
- No vague advice like "consider refactoring" without a concrete direction.
- Findings must stay actionable and technically exact.