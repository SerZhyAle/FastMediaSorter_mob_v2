---
name: rg-absent-in-bash-tool
description: ripgrep is on PATH in the PowerShell tool but NOT in the Bash tool on this machine - a script that branches on rg availability answers differently depending on which tool launched it
metadata:
  type: project
---

`rg` resolves in the **PowerShell tool** and does **not** resolve in the **Bash tool** on this machine. Any script
that does `Get-Command rg -ErrorAction SilentlyContinue` and falls back to something else therefore takes a
different branch depending only on which tool invoked it - same repo, same second, different answer.

**Why:** measured 2026-08-18 while working S1800. `drift-check.ps1` used ripgrep when present and `git grep`
otherwise. `git grep` reads the index, so a marker in a new uncommitted file was invisible to it. The same ticket
read `DRIFT` (1 marker) from the PowerShell tool and `CLEAN` (0 markers) from the Bash tool, one minute apart,
with no edits between - and `CLEAN` is the reassuring answer, so the miss was silent. Fixed in S1800 by making
the fallback walk the working tree, but the environment asymmetry that exposed it is still there and will expose
the next such branch too.

**How to apply:**
- When two runs of the same script disagree, check which tool ran each one before suspecting the repo state.
- Reviewing or writing a script that branches on an external binary: make both branches agree, and prefer a
  fallback that reads the same source of truth. Here the working tree is the truth, never the git index
  (`git grep`, `git ls-files`, `git diff` all miss uncommitted work - and most work here is uncommitted).
- A tool-availability fallback that silently returns the *safer-looking* verdict is the dangerous shape; make it
  fail loudly or report "cannot verify" instead.

Related: [[rg-gitignore-catalog]] (rg's own ignore rules skip `dev/CATALOG`), [[bash-cd-leaks-into-powershell-cwd]].
