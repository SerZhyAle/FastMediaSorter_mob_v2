---
name: aps1-launcher-pwsh-cwd
description: a.ps1 build launcher must be run via pwsh from the repo root; bash invocation or wrong cwd silently no-ops with exit 0
metadata:
  type: feedback
---

Run `a.ps1` (and other `.ps1` launchers) ONLY as `pwsh -NoProfile -File ./a.ps1 <cmd>` from the repo root. Two silent-failure traps:

- **Via Bash directly** (`./a.ps1 fc` in the Bash tool): bash tries to interpret the PowerShell script, chokes on the UTF-8 BOM + `<#` comment block ("syntax error near unexpected token `newline`"), and the background task still reports **exit 0**. No build ran.
- **Wrong cwd**: the Bash tool's cwd persists between calls. After a `cd app_v2/src/main/res` (e.g. for a grep), a later `pwsh -NoProfile -File ./a.ps1 fc` fails with "argument './a.ps1' is not recognized as the name of a script file", pwsh prints its usage banner and exits **0**. Again no build.

**Why:** both produce a green exit code with no `BUILD SUCCESSFUL`, so the notification's "exit 0" is meaningless (see [[feedback_background_task_exit_code_is_echo]]). I trusted it twice in one session and thought the wiring was validated when nothing had compiled.

**How to apply:** always invoke `pwsh -NoProfile -File ./a.ps1 ...`; ensure cwd is the repo root first (pass an absolute path or run from a fresh command, don't inherit a `cd`-drifted cwd). After any build, grep the output for `BUILD SUCCESSFUL|BUILD FAILED` - never accept the task-notification exit code as proof the build ran.

**Now hard-gated (2026-07-25, CLAUDE.md Rule 25):** running a `.ps1` as a Bash command head (`./a.ps1 fk`) is blocked before bash spawns by the global PreToolUse hook `~/.claude/hooks/guard-ps1-in-bash.ps1`. It recurred despite this memory being advisory-only (an agent ran `./a.ps1 fk` in bash minutes before the gate was added), so it was promoted to a gate mirroring the Rule 24 find-guard. The gate is quote-aware: it allows `pwsh`/`powershell` heads and `.ps1` as an argument (`grep ... foo.ps1`), blocks only head-position `.ps1`. The wrong-cwd silent-exit-0 trap above is NOT gated - still verify `BUILD SUCCESSFUL`.
