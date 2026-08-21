---
name: compress-memory-index-by-splitting-not-truncating
description: When MEMORY.md is over budget, move whole topic sections into second-level INDEX_*.md files - never shorten a hook by cutting it mid-phrase, which keeps the pointer's cost and destroys the only part that decides relevance
metadata:
  type: feedback
---

`MEMORY.md` is billed on every turn, so it goes over budget regularly. Compress it by **moving whole
topic sections into a second-level `INDEX_<topic>.md`**, leaving one pointer line behind. Never
compress by trimming the hook text off the end of a line.

**Why:** measured 2026-08-21. A previous compression pass (my own) had squeezed the index to 12832 B
against a 12947 B ceiling by chopping hooks, and 27 of them ended mid-phrase or mid-word - `use
`exit-code-l`, `orphan .flat`, `` `.claude/**` = ``, `detekt 86%; ms`. That trade is backwards on both
sides:

- The **pointer** is the expensive half (a memory filename runs 40-70 characters) and it survived every
  cut. The **hook** is the cheap half, and it is the only thing that lets a future turn decide whether
  the file is worth opening. Cutting the hook keeps the whole cost and removes the whole value.
- Two pointers were dropped outright, leaving a dangling `·` at the end of a line and orphaning
  memories that were still perfectly good.

Splitting three sections into `INDEX_build_flavors.md`, `INDEX_ui_conventions.md` and
`INDEX_subagents.md` took the index from 12832 B to 7346 B - 43% smaller than the truncated version -
while *restoring* 93 hooks to full sentences. Compression and quality moved the same direction, which
is the tell that truncation was never the mechanism to reach for.

**How to apply:**

- Over budget -> find the largest sections with `grep -n '^## '` and count each one's bytes. A section
  worth 2 KB+ whose subject is task-scoped ("open when building", "open when editing a layout") is the
  candidate.
- Write `INDEX_<topic>.md` with `type: reference` frontmatter, then generate its bullets from each
  target memory's own `description:` field - that field is already a one-line summary written for this
  exact purpose, so the hooks come out complete and correct without hand-authoring.
- Leave exactly one line in `MEMORY.md`: `- Second-level index: [what is in it](INDEX_<topic>.md) -
  open when <condition>.` State the condition; it is what stops the file from being opened needlessly.
- Keep at the top level anything that is a **precondition** rather than a lookup - the
  "OPEN BEFORE WRITING ANY KOTLIN" detekt line must be seen without being sought.
- Never let a line end in `·`, `+`, `-`, `the`, `is`, `a`. If a hook will not fit complete, the entry
  belongs in a second-level index, not on a shorter line.
- `assert-memory-budget.ps1 -Gate` reports `index | ceiling | stretch`. It only writes a baseline file;
  it never edits `MEMORY.md`, so any damage in there was written by an agent.
