---
name: devlogs-array-binding
description: close-and-log.ps1 -DevLogs multi-entry - pass ONE JSON-array string; since S1063 a PS array literal via pwsh -File is rejected at bind time
metadata:
  type: feedback
---

Multi-entry `-DevLogs` for `close-and-log.ps1`: pass a SINGLE string holding a JSON array - the script expands it in-process (fixed at source 2026-07-03 after owner said "fix the script instead of writing wrappers each time"):

`pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 -Id Sxxxx -Status Verified -DevLogs '[{"file":"..","target":"..","desc":".."},{"file":".."}]' -FuncOp FIX -FuncDesc "..."`

**Why:** `pwsh -File` binds only the FIRST element of a multi-element array argument; the rest become positional args. Since S1063 (2026-07-16) the script declares `PositionalBinding = $false`, so a stray positional arg dies at bind time ("A positional parameter cannot be found") before any mutation - do NOT read that error as a script bug, it means the call shape is wrong. Before S1063 the leftover silently bound to `-FeatId` and only surfaced at the all-features step, after the status flip and first dev-log had already been written (partial close on S1062, 2026-07-15). The same bad call had failed loudly back on S0082 (2026-06-03) - the failure mode went silent when the `-Feat*` params landed (2026-06-17), because each new optional `[string]` param opens another positional slot.

**How to apply:** Default to the JSON-array-string form above. An in-process `&` call with a real `@(..)` array still works from the PowerShell tool. NEVER author a temp wrapper .ps1 for this (owner flagged the ceremony 2026-07-03); reserve temp wrappers for Cyrillic args crossing the Bash->pwsh boundary or rituals reused across runs. Regression suite: `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` (18 cases) - run it after touching the facade. General rule for any OTHER script with an array param: per CLAUDE.md Rule 13 fix the script the same way (JSON-array transport string + `PositionalBinding = $false`); a point-check on one param rots as soon as the next param is added. See [[string-array-param-csv-via-pwsh-file]], [[spec-catalog-exit-code-contract]].
