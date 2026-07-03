---
name: feedback-parallel-agents-no-git-build
description: Parallel implementation subagents must never run git (stash/checkout) or gradle builds; one agent's git stash silently clobbers another's uncommitted edits
metadata:
  type: feedback
---

When launching implementation subagents **in parallel**, their briefs must explicitly forbid: any `git` command (especially `git stash` / `git stash pop` / `git checkout` / `git restore`), any gradle build, and `catalog_sync`. The orchestrator owns all builds, git, and catalog ops. Assign each parallel agent a **disjoint file set** and run ONE central build per wave after they return.

**Why:** In the S0366–S0374 batch (2026-06-06), two implementation agents ran concurrently on disjoint files. One of them (the camera-widget agent) ran `git stash` to isolate its own compile check, then restored - which wiped out the **other** agent's uncommitted edits to 6 settings files. The settings agent misdiagnosed it as "an external editor (Android Studio) is reverting my edits within seconds" and gave up on those files. The edits had to be re-applied afterward. Two agents racing on the shared working-tree git state silently lose uncommitted changes; git stash captures/restores the *entire* tree, not just the stashing agent's files.

**How to apply:**
- Every parallel impl-agent prompt: a hard line "NO git, NO gradle build, NO catalog_sync - orchestrator owns these; report your changes and stop."
- If an agent genuinely needs an isolated compile, give it `isolation: "worktree"` via the Agent tool (its own git worktree), never let it stash in the shared tree.
- If a subagent reports "an external editor / IDE is reverting my files," first suspect a concurrent agent's git/stash, not a real IDE - verify the actual on-disk state by reading the files (Grep for the expected sentinels) before re-doing work, not via git history.
- A green central build after all agents return is the authoritative validation; subagent self-reports of "compiles in isolation" are not (their kapt cache and stash games can mislead). See [[feedback_verify_subagent_build_failures]].
