---
name: pwsh-file-does-not-split-a-comma-list-into-an-array
description: `pwsh -File script.ps1 -Flavors standard,wear` binds ONE literal "standard,wear" to a [string[]] parameter - only PowerShell's own parser splits on the comma, so the release spectrum script rejects it
metadata:
  type: feedback
---

A comma-joined list passed through `pwsh -NoProfile -File <script> -Param a,b` arrives as a **single
array element** `"a,b"`, not as two. PowerShell only splits on the comma when *it* parses the command
line; under `-File` the arguments come from the shell verbatim.

**Why:** measured 2026-08-23 running `/skill-release wear`. The command file says to record the flavor
scope as `$FLAVORS` "comma-joined, e.g. `standard,vr`" and pass it verbatim to
`scripts/release/build-release-spectrum.ps1 -Flavors $FLAVORS`. That is correct advice **inside
PowerShell** and wrong from the Bash tool: the script threw
`Unknown flavor 'standard,wear'. Valid: standard, lite, photos, legacy, vr, wear, noLegal, or 'all'.`
and the release stalled one step before publishing. The same trap waits for any `[string[]]` parameter
in `scripts/` reached from bash.

**How to apply:** from the Bash tool, either pass the elements as separate tokens
(`-Flavors standard wear`) or, more reliably, switch to `-Command` with an explicit array and a real
exit code:

    pwsh -NoProfile -Command "& './scripts/release/build-release-spectrum.ps1' -ReuseVersion -Flavors @('standard','wear'); exit \$LASTEXITCODE"

Better still, use the PowerShell tool for any call carrying a list parameter - there the documented
`-Flavors standard,wear` form works as written. Related:
[[piping-a-gate-through-tail-masks-its-exit-code]].
