---
name: pwsh-param-local-case-collision
description: PowerShell lowercase loop-local that case-insensitively collides with a typed script param silently corrupts the param
metadata:
  type: feedback
---

In a PowerShell script, a local variable whose name differs only in case from a declared `param()` is the SAME variable (PS is case-insensitive). If the param is typed, assigning the local's value re-coerces/overwrites the param.

**Why:** `scripts/utils/search-log.ps1` had two such collisions in its JSON `.logcat` parse loop. `$json = ... | ConvertFrom-Json` hit `[switch]$Json` -> threw "Cannot convert PSCustomObject to SwitchParameter". After fixing that, `$tag = $h.tag` (10475 iterations) silently left `[string]$Tag` set to the LAST entry's tag, so the downstream `if ($Tag -ne "")` filter dropped all output to that one tag - every `-Pattern`/`-Level`/`-AppOnly`/`-Errors` query returned wrong/empty results with no error. Cost ~10 probes to localize because the symptom (false "No matches found") looked like the data, not the tool.

**How to apply:** When authoring/fixing pwsh scripts here, never name a loop-local or temp the same (case-insensitively) as a `param()`. Prefer distinct names (`$jsonDoc`, `$msgTag`). When a JSON/large-loop script "returns nothing" or a switch param throws a type-coercion error, suspect a local/param name clash before suspecting the data. Related: [[feedback_pwsh_authoring_byte_traps]], [[feedback_pwsh_efficiency]].
