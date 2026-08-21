---
name: cyrillic-command-name-bet
description: The Cyrillic slash-command aliases are an unverified bet - Claude Code does not document non-ASCII command names; confirm routing before relying on them
metadata:
  type: project
---

Twenty-eight of the 42 generated alias files carry Cyrillic names (`/сд`, `/ыв`). Claude Code's documentation
says nothing about non-ASCII command names - discovery, name parsing and the `/` autocomplete matcher are all
unspecified for them. Created 2026-08-21 on the owner's explicit choice, knowing this.

**Why:** the owner types in two layouts, and the shadow set (`/ыв` = the same physical keys as `/sd` on
ЙЦУКЕН) is the only thing that removes the "wrong layout" miss entirely. Worth an unverified bet because the
downside is one command that silently does not route, and the escape hatch is a single call.

**How to apply:**

- The filesystem half is **verified**: NTFS round-trips the names with codepoints intact (probed the same
  day). Any failure is in the CLI, not on disk.
- The CLI half is **not verified** - only the owner typing `/сд` settles it. If they report it does not
  route, run `generate-command-aliases.ps1 -Prune -Sets latin`; that drops both Cyrillic sets and leaves the
  Latin set untouched.
- Do not quietly widen the Cyrillic sets to more commands before the routing is confirmed working.
- See [[skill-aliases]] for the mechanism and the source-of-truth table.
