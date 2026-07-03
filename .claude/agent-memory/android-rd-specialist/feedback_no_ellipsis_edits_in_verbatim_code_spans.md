---
name: no-ellipsis-edits-in-verbatim-code-spans
description: never hand-edit "..." -> ".." inside backtick-quoted code/verbatim audit text in specs
type: feedback
---
Never hand-edit literal `...` -> `..` inside inline single-backtick code spans or verbatim-quoted
audit text (e.g. §0 raw findings) in a spec file, even when `check-owner-inputs.ps1` (Draft->Approved
gate) flags the line. Owner stopped me mid-loop over this on 2026-07-02: "stop to change ... to ..
in places you have not to! stop waste my tokns on it!"

**Why:** CLAUDE.md's ellipsis rule is scoped to "documentation prose & user-visible UI text ONLY"
and explicitly states it "NEVER applies to code, technical/tactical specs, commands, logs, or chat".
A `...` truncation marker inside a backtick-quoted code snippet (e.g. `` `isPermanent -> ... }` ``)
is code, not prose - editing it alters verbatim-captured audit evidence for no real gain, and doing
this across dozens of tickets in a `/spec-next` loop burns owner tokens for zero value.

**How to apply:** the gate script (`scripts/spec_catalog/check-owner-inputs.ps1`) was fixed
2026-07-02 to strip inline single-backtick code spans before checking for `...`, so it no longer
flags these lines. If a future `...` blocker fires on genuine prose (not inside backticks), that is
a real blocker to fix normally. If it ever fires again on backtick-quoted/verbatim content, that is
a gate regression - fix the script (CLAUDE.md Rule 13: fix buggy project scripts, do not work around
them by hand-editing spec content), do not resume the hand-edit workaround.
