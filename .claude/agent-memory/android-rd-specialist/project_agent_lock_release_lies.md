---
name: agent-lock-release-lies
description: agent-lock.ps1 is a dot-source library with no CLI; running it as a script now fails with exit 2 (was a silent exit 0) - exit-code-lock.ps1 is the releaser (S1505, fixed 2026-08-08)
metadata:
  type: project
---

`scripts/utils/agent-lock.ps1` is a **dot-source library, not a command line**. It has no top-level `param()` and no verb dispatch, so `pwsh -File agent-lock.ps1 -Name Code -Action Release` never released anything. The releaser paired with `enter-code-lock.ps1` is `scripts/utils/exit-code-lock.ps1`.

**Why:** observed 2026-08-08 during S1471 - two consecutive `-Action Release` calls each printed nothing, each exited **0**, and `lock-status.ps1` still showed `HELD`; the lock stayed wedged 479s across a whole subagent phase. The cause was never a broken release path: the arguments landed in `$args`, were ignored, the body defined the functions, and the script exited 0. **Fixed in S1505 (Verified 2026-08-08):** a direct-invocation guard now prints the correct entry points and exits **2**. The silent-success trap is gone.

**How to apply:**
- Release the code lock with `exit-code-lock.ps1`; acquire with `enter-code-lock.ps1`; inspect with `lock-status.ps1`; force-clear with `clear-agent-lock.ps1`.
- From a script, dot-source the library and call `Exit-AgentLock -Name Code` - that is how all ~47 consumers load it.
- Exit **2** from `agent-lock.ps1` now means "you invoked a library as a command", not "the lock operation failed" - read the message, it names the right command.

Related: [[code-lock-is-per-step-not-per-ticket]], [[do-not-idle-on-a-lock]].
