---
name: feedback-pwsh-path
description: pwsh 7 lives at /c/Program Files/PowerShell/7/pwsh.exe and is not on the bash PATH; use the full quoted path for PS7-only scripts
metadata:
  type: feedback
---

PowerShell 7 is installed at `/c/Program Files/PowerShell/7/pwsh.exe` on this machine. The harness Bash tool does not have `pwsh` on `PATH`, so `pwsh -NoProfile ..` from a bash invocation will either pick up Windows PowerShell 5.1 (different parser, missing operators like `??`, `?.`, ternary) or fail outright.

**Why:** the user flagged this after a chain of dev-log calls silently fell back to PS 5.1 and produced parser errors that didn't surface in the tool output. The native `PowerShell` tool already runs under pwsh 7; the trap is only for cross-shell calls from `Bash`.

**How to apply:** For any docs-closure script that depends on PS 7 syntax - `scripts/check_strings_localized.ps1`, the spec-catalog mutators, the dev-log writer when chained with `&&`/`??` - launch it via the full path: `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File <script>`. Quote the path; the space in `Program Files` breaks unquoted forms. Inside the native `PowerShell` tool no path prefix is needed - the harness is already pwsh 7. When you have a choice, prefer `PowerShell` over `Bash + pwsh.exe` for PS-only chains; it sidesteps the path issue and the dollar-escape trap at the same time.
