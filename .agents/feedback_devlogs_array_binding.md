---
name: devlogs-array-binding
description: close-and-log.ps1 -DevLogs multi-entry - pass ONE JSON-array string; a PS array literal through pwsh -File is rejected at bind time
metadata:
  type: feedback
---

Multi-entry `-DevLogs` for `close-and-log.ps1`: pass a SINGLE string holding a JSON array - the script expands it in-process (fixed at source 2026-07-03 after owner said "fix the script instead of writing wrappers each time"):

`pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 -Id Sxxxx -Status Verified -DevLogs '[{"file":"..","target":"..","desc":".."},{"file":".."}]' -FuncOp FIX -FuncDesc "..."`

**Why:** `pwsh -File` binds only the FIRST element of a multi-element array argument; the rest become positional args. Since S1063 (2026-07-16) `close-and-log.ps1` declares `PositionalBinding = $false`, so such a call dies at bind time with "A positional parameter cannot be found" before any mutation. Do not treat that error as a script bug - it means the call shape is wrong, so switch to the JSON-array string. Before S1063 the leftover silently bound to `-FeatId` and only surfaced at the all-features step, after the status flip had already been written (partial close on S1062).

**How to apply:** Default to the JSON-array-string form above. In-process `&` call with a real `@(...)` array also still works from the PowerShell tool. NEVER author a temp wrapper .ps1 for this (owner flagged the ceremony 2026-07-03); reserve temp wrappers for Cyrillic args crossing the Bash->pwsh boundary or rituals reused across runs. General rule for any OTHER script with a multi-element array param: fix the script the same way (accept a JSON-array transport string, turn positional binding off) per CLAUDE.md Rule 13, don't work around it.
