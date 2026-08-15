---
name: string-array-param-csv-via-pwsh-file
description: PowerShell [string[]] params don't split a quoted CSV when invoked via pwsh -File; pass an array or split inside the script
type: feedback
metadata:
  type: feedback
---

A PowerShell `[string[]]$Param` does NOT re-parse commas when the value is passed as one quoted token through `pwsh -File script.ps1 -Param "a,b,c"` - the whole CSV binds as a single-element array `["a,b,c"]`. Native pwsh `-Param a,b,c` (unquoted) splits; `-File` with a quoted string does not. Space-separated bare values after the param leak into the NEXT positional parameter instead.

**Why:** spec-next-preflight.ps1 `-Exclude` silently failed to exclude round-memory ids (the CSV matched no real id), so /spec-next kept re-selecting an already-processed ticket (S0525). Confirmed via `excluded_ids: ["S0526,S0527,S0528,S0525"]` (one element).

**How to apply:** When a project script takes a `[string[]]` and you call it via `pwsh -File`, either (a) invoke via `pwsh -Command '& "script" -Param @("a","b")'` with a real array, or (b) fix the script to split each element on comma: `foreach ($e in $Param) { foreach ($x in ($e -split ',')) { ... } }`. Prefer (b) per CLAUDE.md Rule 13 (fix the script). Applies to any `pwsh -File` call from the Bash tool. See [[devlogs-array-binding]] for the related close-and-log array-binding trap.

**Check which side the script is on before choosing the form - guessing wrong fails SILENTLY.** Several scripts already implement (b), and for those the CSV string is the CORRECT form while a PowerShell array literal is the broken one. `post-change.ps1` is the main example: `-Files "a.kt,b.kt"` and `-RegistryAck "developer-operations,quality-assurance"` both work, but `-RegistryAck 'developer-operations','quality-assurance'` through `pwsh -File` binds only the first element and the gate then reports the second record as simply *not acknowledged* - it reads as "you didn't do the work", not "your argument didn't bind" (cost two wasted closure runs plus duplicate dev-log rows on 2026-08-03, S1375). The dev log is append-only with no removal script, so a wasted closure run is permanent noise. When a `[string[]]` argument appears not to take effect, suspect binding before suspecting the gate.

**The same mistake also has a loud form - do not let it mislead you into doubting the paths.** Passing `post-change.ps1 -Files` a real array from the PowerShell tool fails two different ways depending on shape: a multi-line `@( .. )` literal loses everything after the first element to "A positional parameter cannot be found", and a single-line comma-separated list of quoted strings exits **2** with `invalid file argument(s): "<path>" (not found)` for every path, quotes included in the echoed value. Both look like a path problem and are not - the files exist. The fix is the same in every case: one quoted CSV string, `-Files "a.kt,b.kt,c.md"`.
