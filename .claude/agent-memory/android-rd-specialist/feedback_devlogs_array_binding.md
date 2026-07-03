---
name: devlogs-array-binding
description: close-and-log.ps1 -DevLogs multi-entry - pass ONE JSON-array string (script expands it since 2026-07-03); never wrapper scripts
metadata:
  type: feedback
---

Multi-entry `-DevLogs` for `close-and-log.ps1`: pass a SINGLE string holding a JSON array - the script expands it in-process (fixed at source 2026-07-03 after owner said "fix the script instead of writing wrappers each time"):

`pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 -Id Sxxxx -Status Verified -DevLogs '[{"file":"..","target":"..","desc":".."},{"file":".."}]' -FuncOp FIX -FuncDesc "..."`

**Why:** `pwsh -File` binds only the FIRST element of a multi-element array argument (rest become unbound positional args - cost two failed finalize calls on S0082, 2026-06-03). A single JSON-array token survives the process boundary; nested single-quoting is safe from both PS and Bash.

**How to apply:** Default to the JSON-array-string form above. In-process `&` call with a real `@(...)` array also still works from the PowerShell tool. NEVER author a temp wrapper .ps1 for this (owner flagged the ceremony 2026-07-03); reserve temp wrappers for Cyrillic args crossing the Bash->pwsh boundary or rituals reused across runs. General rule for any OTHER script with a multi-element array param: fix the script the same way (accept a JSON-array transport string) per CLAUDE.md Rule 13, don't work around it.
