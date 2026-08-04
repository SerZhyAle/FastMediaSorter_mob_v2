---
name: pwsh-param-local-case-collision
description: PowerShell locals and function params that collide with a typed script param, or with an automatic variable like $Args, silently corrupt the value
metadata:
  type: feedback
---

In a PowerShell script, a local variable whose name differs only in case from a declared `param()` is the SAME variable (PS is case-insensitive). If the param is typed, assigning the local's value re-coerces/overwrites the param.

**Why:** `scripts/utils/search-log.ps1` had two such collisions in its JSON `.logcat` parse loop. `$json = ... | ConvertFrom-Json` hit `[switch]$Json` -> threw "Cannot convert PSCustomObject to SwitchParameter". After fixing that, `$tag = $h.tag` (10475 iterations) silently left `[string]$Tag` set to the LAST entry's tag, so the downstream `if ($Tag -ne "")` filter dropped all output to that one tag - every `-Pattern`/`-Level`/`-AppOnly`/`-Errors` query returned wrong/empty results with no error. Cost ~10 probes to localize because the symptom (false "No matches found") looked like the data, not the tool.

**Same class, automatic variables (2026-07-28, S1256):** a helper declared `function Invoke-Adb { param([string[]]$Args) ... @Args }`. `$Args` is PowerShell's automatic variable for unbound arguments, so splatting it behaved inconsistently - most calls went through, but one `adb shell -Cmd "cmd locale set-app-locales .."` silently never reached the device. The screenshots that followed looked perfectly valid and were simply in the wrong language, which is exactly the kind of failure no exit code reports. Renaming to `$AdbArgs` fixed it. Never name a param `$Args`, `$Input`, `$Error`, `$Host`, `$PSItem`.

**How to apply:** When authoring/fixing pwsh scripts here, never name a loop-local or temp the same (case-insensitively) as a `param()`, and never shadow an automatic variable. For any device/CLI side effect that a later step depends on, read the state back and fail loudly - `set-app-locales` is silent on failure, so the run must verify with `get-app-locales`. Prefer distinct names (`$jsonDoc`, `$msgTag`). When a JSON/large-loop script "returns nothing" or a switch param throws a type-coercion error, suspect a local/param name clash before suspecting the data. Related: [[pwsh-authoring-byte-traps]], [[pwsh-efficiency]].
