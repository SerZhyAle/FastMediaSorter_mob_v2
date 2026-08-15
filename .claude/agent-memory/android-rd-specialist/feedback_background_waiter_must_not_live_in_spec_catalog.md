---
name: background-waiter-must-not-live-in-spec-catalog
description: guard-fire-and-forget refuses run_in_background for ANY path under scripts/spec_catalog|document_registry|all_features - a script meant to be backgrounded belongs in scripts/utils.
metadata:
  type: feedback
---

The Rule 26 hook matches on the **directory**, not on what the script does: its pattern list carries
`[\\/](spec_catalog|document_registry|all_features)[\\/]` as "a catalog mutator or query". A read-only
waiter filed there is refused from the Bash tool exactly like a mutator would be, even though being
backgrounded is its whole contract.

**Why:** on 2026-08-14 the `/spec-do` idle waiter was first written as
`scripts/spec_catalog/wait-for-ticket-work.ps1`. Nothing in the repo hints at the problem - the hook is
global (per-machine, `~/.claude/hooks/guard-fire-and-forget.ps1`, shipped by the sza plugin), so it is
absent from a fresh checkout, and `docs/AGENT_HOOKS.md` describes it only as refusing "a catalog
mutator". Moved to `scripts/utils/wait-for-ticket-work.ps1`, beside `wait-for-lock-turn.ps1`, which is
the precedent for the same shape.

**How to apply:** any new script whose contract is "run me in the background and read my marker file"
goes in `scripts/utils/`, never under the three catalog directories, and resolves its catalog helpers
by path from `$PSScriptRoot`. The hook reads the agent's command line only, so a catalog script invoked
*inside* the backgrounded script is fine. Same family: [[background-task-exit-code-is-echo]] - the
verdict travels in a marker file, never in the background exit code.
