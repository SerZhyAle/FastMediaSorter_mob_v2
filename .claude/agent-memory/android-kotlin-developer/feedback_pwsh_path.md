---
name: feedback_pwsh_path
description: PowerShell 7 lives at /c/Program Files/PowerShell/7/pwsh.exe and is NOT on the Bash tool's PATH - use the full path for PS7-only scripts
metadata:
  type: feedback
---

`pwsh` (PowerShell 7) is installed at `/c/Program Files/PowerShell/7/pwsh.exe` on this machine and is NOT on the Bash tool's `$PATH`. The bare command `pwsh` from a Bash-tool invocation will fail with "command not found"; only Windows PowerShell 5.x (`powershell.exe`) is on PATH, and several project scripts (`dev/CATALOG/scripts/render.ps1`, every `scripts/spec_catalog/*.ps1` mutator) require PS7 features and break under 5.x.

**Why:** The user explicitly flagged this trap - when I called `pwsh -NoProfile -File scripts/...` from the Bash tool and got a command-not-found / silent script failure, the cause was always the missing absolute path, not the script itself.

**How to apply:** When launching any project PowerShell script from the Bash tool, use the full quoted path: `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/<name>.ps1 ...`. The PowerShell tool itself (not Bash) already maps `pwsh` to PS7 - inside that tool, plain `pwsh -NoProfile` is fine. Rule of thumb: if the tool call starts with `Bash`, prefix the full path; if it starts with `PowerShell`, the bare `pwsh` resolves correctly.
