---
name: bash-cd-leaks-into-powershell-cwd
description: A `cd` inside a Bash tool call changes the PowerShell tool's working directory too - later `pwsh -NoProfile -File scripts/..` then fails with "not recognized as the name of a script file"
metadata:
  type: feedback
---

The Bash tool and the PowerShell tool share one working directory in this environment. A `cd` at the
head of a Bash command (`cd "p:/.../app_v2/src/main/res" && wc -l ...`) leaves the PowerShell tool
sitting in that directory for every later call.

**Why:** the failure does not look like a directory problem. `pwsh -NoProfile -File scripts/spec_catalog/update.ps1` answers `The argument 'scripts/spec_catalog/update.ps1' is not recognized as the name of a script file` and then prints the whole pwsh usage banner, which reads like a syntax error in the command being built. On 2026-08-07 this silently killed a four-command batch (spec status update, session record, lease release, dev log) mid-`/spec-do` round; the real cause was a `cd` in a Bash call two steps earlier.

**How to apply:**

- Prefer `cd <dir> && <cmd>` in Bash only when the command genuinely needs it, and assume the change persists.
- When a `pwsh -NoProfile -File <relative path>` suddenly stops resolving, run `Get-Location` first - do not start rewriting the command.
- Cheapest guard for a batch that matters: `Set-Location "P:\ANDROID\FastMediaSorter_mob_v2"` as the first statement, or pass repo-absolute script paths.

Related: [[pwsh-authoring-byte-traps]], [[cli-project-wrappers-first]], [[tool-bypass-discipline]].
