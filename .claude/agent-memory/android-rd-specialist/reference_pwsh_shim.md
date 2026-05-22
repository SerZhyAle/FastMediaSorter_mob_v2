---
name: reference-pwsh-shim
description: Bare `pwsh` works in Git Bash on this machine via shim at /c/Users/serzh/bin/pwsh — no need for full PowerShell 7 path
metadata:
  type: reference
---

`pwsh` is callable as a bare command in Git Bash (including non-interactive `bash -c "..."` invocations from Claude Code) thanks to a shim at `/c/Users/serzh/bin/pwsh`.

The shim is a 3-line bash script that `exec`s `/c/Program Files/PowerShell/7/pwsh.exe "$@"`. The directory `/c/Users/serzh/bin` is in `$PATH` (Git Bash adds it automatically even when missing — the shim creation just materialized it).

**How to apply:**
- Just write `pwsh -NoProfile -File <path>` or `pwsh -NoProfile -Command "..."` directly. No need for `"/c/Program Files/PowerShell/7/pwsh.exe"` anywhere.
- This supersedes any older memory or comment saying "pwsh is not on bash PATH" — that was true before the shim was installed on 2026-05-21.
- If the shim ever disappears (Windows reinstall, profile wipe), recreate with: `mkdir -p /c/Users/serzh/bin && printf '#!/usr/bin/env bash\nexec "/c/Program Files/PowerShell/7/pwsh.exe" "$@"\n' > /c/Users/serzh/bin/pwsh && chmod +x /c/Users/serzh/bin/pwsh`.

Related: [[feedback_pwsh_efficiency]] (still applies — always pass `-NoProfile`, batch related calls in one process).
