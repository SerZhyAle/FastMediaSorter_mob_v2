---
name: skill-aliases
description: Short slash-command aliases are real generated command files now, not chat expansions - never hand-write one, add a table row and regenerate
metadata:
  type: feedback
---

Short aliases for the owner's hot commands are **real files** under `.claude/commands/`, generated from the
table in `scripts/utils/generate-command-aliases.ps1`. The CLI routes them; there is nothing for you to
expand by hand.

**Why:** they used to be a memory-only table that only worked when this memory happened to be loaded, and
Claude Code has no native alias field - a command is addressed by its file name and `name:` is ignored in
`.claude/commands/*.md`, so a second invocable name can only be a second file. Made real on 2026-08-21 after
transcript mining showed the owner typing `/spec-do`, `/spec-all`, `/spec-next` and `/spec-quiz` 60-110 times
each in five weeks, plus misspellings (`/sped-do`, `/spec-d`) caused by the length.

**How to apply:**

- Each canonical command carries three alias names: two-letter Latin (`/sd`), Cyrillic transliteration
  (`/сд`), and the same physical keys on the ЙЦУКЕН layout (`/ыв`). Read the table from the generator, never
  from memory - it is the source of truth and this note deliberately does not copy it.
- Never hand-write or hand-edit an alias file. Add a row to the table and re-run the generator; `-Check`
  verifies, `-Prune` retires, `-Sets latin` drops the Cyrillic sets if the CLI ever stops routing non-ASCII
  names (undocumented upstream, so treat it as a live bet - see [[cyrillic-command-name-bet]]).
- Every generated file sets `disable-model-invocation: true`, so an alias never appears in your skill listing
  and costs nothing per turn. If you ever see one listed, the flag stopped working - say so.
- **A built-in CLI command cannot be aliased by a file.** `/compact` and `/clear` are the two the owner uses
  most (117 and 63 invocations) and neither can get a short name this way.
- `/quick` has no alias by design - it measured 2 invocations. If the owner starts reaching for it, that is a
  table row, not an argument about the scheme.
