---
name: pwsh-path
description: pwsh 7 is at `/c/Program Files/PowerShell/7/pwsh.exe` on this machine - not on the bash PATH; use the full quoted path for any PS7-only script
metadata:
  type: feedback
---

PowerShell 7 (`pwsh.exe`) lives at `/c/Program Files/PowerShell/7/pwsh.exe` on this development machine. It is NOT exposed on the default MSYS bash `PATH`, so a bare `pwsh -NoProfile -File ...` from the Bash tool resolves to Windows PowerShell 5.1 (or fails to launch at all on some shells).

**Why:** Several project scripts require PS 7+ features (modern `class`, `-Parallel`, certain `ConvertFrom-Json` flags). Notable PS7-only callers: `dev/CATALOG/scripts/render.ps1`, the spec-catalog mutators (`insert.ps1`, `update.ps1`, etc.). Calling them via WinPS 5.1 produces obscure parse errors that look unrelated to the version mismatch.

**How to apply:** When the research agent needs to invoke a PS7-only script via the Bash tool, always use the full quoted path: `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File <script>`. The researcher mostly invokes `dev/CATALOG/scripts/query.ps1` (PS 5.1 compatible) so this trap is rare here, but cite the full path in research output when recommending a CLI to a downstream writer agent.
