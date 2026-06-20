---
name: string-array-param-csv-via-pwsh-file
description: PowerShell [string[]] params don't split a quoted CSV when invoked via pwsh -File; pass an array or split inside the script
type: feedback
metadata:
  type: feedback
---

A PowerShell `[string[]]$Param` does NOT re-parse commas when the value is passed as one quoted token through `pwsh -File script.ps1 -Param "a,b,c"` - the whole CSV binds as a single-element array `["a,b,c"]`. Native pwsh `-Param a,b,c` (unquoted) splits; `-File` with a quoted string does not. Space-separated bare values after the param leak into the NEXT positional parameter instead.

**Why:** spec-next-preflight.ps1 `-Exclude` silently failed to exclude round-memory ids (the CSV matched no real id), so /spec-next kept re-selecting an already-processed ticket (S0525). Confirmed via `excluded_ids: ["S0526,S0527,S0528,S0525"]` (one element).

**How to apply:** When a project script takes a `[string[]]` and you call it via `pwsh -File`, either (a) invoke via `pwsh -Command '& "script" -Param @("a","b")'` with a real array, or (b) fix the script to split each element on comma: `foreach ($e in $Param) { foreach ($x in ($e -split ',')) { ... } }`. Prefer (b) per CLAUDE.md Rule 13 (fix the script). Applies to any `pwsh -File` call from the Bash tool. See [[feedback_devlogs_array_binding]] for the related close-and-log array-binding trap.
