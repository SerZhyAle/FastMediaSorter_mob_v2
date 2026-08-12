---
name: code-lock-is-per-step-not-per-ticket
description: CODE.LOCK is released by post-change.ps1's closure, so it must be re-acquired before every step; and a queue ticket is owned by the SESSION, so waiting silently in the background gets you evicted from your own slot
type: feedback
---

**Rule.** Treat `CODE.LOCK` as scoped to one step, never to a phase or a ticket, and never wait for it silently.

- `post-change.ps1` **auto-releases** `CODE.LOCK` as part of closure (owner-checked, so it never steals another session's lock). Every `/spec-dev` step therefore needs its own `enter-code-lock.ps1` immediately before its edit. A skill that skips the facade (`/skill-fix`) must call `exit-code-lock.ps1` itself.
- A queue ticket is owned by an agent **session**, and its liveness is the write time of that session's transcript. Ceiling 20 min for Code, 60 for Build. A session that parks on a background `wait-for-lock-turn.ps1` and produces no turns looks dead and **gets its ticket evicted** - you lose the position you waited for.
- Never read the outcome from a background task's exit code. Read the marker `temp/<NAME>.TURN-<sessionId>.json` (`outcome`: `granted` / `timeout` / `evicted`), or the waiter's own stdout.
- **Release only with `exit-code-lock.ps1`.** `agent-lock.ps1` is a dot-source library with no command line: calling it as a script used to print nothing and exit **0** while leaving the lock held (confirmed 2026-08-07 on S1478, again 2026-08-08 on S1471). Since S1505 (Verified 2026-08-08) that call fails loudly with exit **2** and names the right command, so the silent-success trap is gone - but `exit-code-lock.ps1` is still the releaser. Note it refuses (correctly) when the lock has since passed to another session. See [[agent-lock-release-lies]].

**Why.** On 2026-08-06, driving S1179 with two sibling `/spec-do` sessions on the same tree: I acquired `CODE.LOCK` for step 01.1, and after `post-change` closed that step I kept editing for step 01.4 believing I still held it. I did not - a sibling working S1436 had taken it. The tell was a build warning naming a *different* reason than mine (`CODE.LOCK present (reason: '/spec-dev S1436 step 05.5')`); a warning naming your own reason is just your own lock. Then, waiting 15 min in a background task without emitting turns, my head-of-queue ticket was evicted and I re-entered behind the sibling.

**How to apply.**

- Acquire → edit → `post-change` (which releases) → acquire again for the next step. Do not "hold it for the phase" to avoid queue churn; the queue is the mechanism, and holding starves the siblings you share the tree with.
- Read the `reason:` field on any lock warning. Yours vs someone else's is the whole signal.
- **Do not start a `-Reason` with a slash from the Bash tool.** MSYS path-converts any argument that looks like an absolute path *before* pwsh sees it, so `-Reason "/spec-dev S1178 step 02.1"` is stored as `C:/Program Files/Git/spec-dev S1178 step 02.1` (observed 2026-08-08, S1178). The lock still works, but the one field you are told to read to tell your lock from a sibling's is now mangled - and a sibling reading it cannot tell which skill holds it. Write `-Reason "spec-dev S1178 step 02.1"` with no leading slash.
- While queued, keep producing small lock-free turns (spec/doc/catalog/research edits, memory writes) so the session stays live. Blocked time is the right moment for exactly that work - but silence in it costs the slot.
- Contention is a throughput fact, not a blocker: with 2-3 sessions on one tree a step costs its own wait. Say so plainly rather than letting a ticket look stalled. See [[concurrent-spec-all-red-tree]] and [[no-concurrent-gradle-invocations]].
