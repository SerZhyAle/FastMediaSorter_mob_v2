---
name: pwsh-param-local-case-collision
description: PowerShell locals and function params that collide with a typed script param, or with an automatic variable like $Args, silently corrupt the value
metadata:
  type: feedback
---

In a PowerShell script, a local variable whose name differs only in case from a declared `param()` is the SAME variable (PS is case-insensitive). If the param is typed, assigning the local's value re-coerces/overwrites the param.

**Why:** `scripts/utils/search-log.ps1` had two such collisions in its JSON `.logcat` parse loop. `$json = ... | ConvertFrom-Json` hit `[switch]$Json` -> threw "Cannot convert PSCustomObject to SwitchParameter". After fixing that, `$tag = $h.tag` (10475 iterations) silently left `[string]$Tag` set to the LAST entry's tag, so the downstream `if ($Tag -ne "")` filter dropped all output to that one tag - every `-Pattern`/`-Level`/`-AppOnly`/`-Errors` query returned wrong/empty results with no error. Cost ~10 probes to localize because the symptom (false "No matches found") looked like the data, not the tool.

**Same class, automatic variables (2026-07-28, S1256):** a helper declared `function Invoke-Adb { param([string[]]$Args) ... @Args }`. `$Args` is PowerShell's automatic variable for unbound arguments, so splatting it behaved inconsistently - most calls went through, but one `adb shell -Cmd "cmd locale set-app-locales .."` silently never reached the device. The screenshots that followed looked perfectly valid and were simply in the wrong language, which is exactly the kind of failure no exit code reports. Renaming to `$AdbArgs` fixed it. Never name a param `$Args`, `$Input`, `$Error`, `$Host`, `$PSItem`.

**Neighbouring trap, same debugging signature (2026-08-10, S1495):** `$list.Add('{0} {1}' -f $a, $b)` does NOT pass one formatted string. Inside a method call the commas are parsed as further *method* arguments, so `-f` receives only `$a` and the call fails with "Index (zero based) must be .. less than the size of the argument list" - reported at the CALLER's line, not at the format string. Assignment form (`$x = 'fmt' -f $a, $b`) is fine, which is why the same expression works two lines earlier. Wrap it: `$list.Add(('fmt' -f $a, $b))`. Worth pairing with the collision above because both produce an error that points away from the defect: one at the wrong variable, one at the wrong line.

**How to apply:** When authoring/fixing pwsh scripts here, never name a loop-local or temp the same (case-insensitively) as a `param()`, and never shadow an automatic variable. For any device/CLI side effect that a later step depends on, read the state back and fail loudly - `set-app-locales` is silent on failure, so the run must verify with `get-app-locales`. Prefer distinct names (`$jsonDoc`, `$msgTag`). When a JSON/large-loop script "returns nothing" or a switch param throws a type-coercion error, suspect a local/param name clash before suspecting the data. Related: [[pwsh-authoring-byte-traps]], [[pwsh-efficiency]].
