---
name: feedback-persistent-logs-no-ticket-id
description: Persistent Timber.i/w/e/d log messages (those that stay in code after the task ends) must NOT contain the Sxxxx ticket id; that prefix is reserved exclusively for the BlockNeedUserTest device probes
metadata:
  type: feedback
---

Any debug/info/warn/error log line that is meant to remain in the codebase after the task is finished MUST NOT include a spec ticket number (`Sxxxx:` or any other `Sxxxx` reference). The ticket id in a log line is the explicit, project-wide signal that the spec is still open and the line is a temporary device-test probe.

**Why:** the project uses presence of `Sxxxx:` inside `Timber.d(...)` (and any log call) as a binary status indicator - it means "this spec is currently in `BlockNeedUserTest`, this line is the operator's logcat probe, remove it on status transition out". Mixing this convention with permanent operational logging would break the invariant: a grep for `"Sxxxx"` in `.kt` would surface lines that must not be deleted, and the lifecycle rule (CLAUDE.md "Debug Verification Tags") becomes unenforceable. See also [[timber-tags-before-test]] and [[feedback-log-levels]].

**How to apply:**
- When writing a *permanent* log line (informational state, recoverable warning, real error worth investigating), describe the subject in plain English: `Timber.i("VR controller reconnected after timeout")`, `Timber.w("Glide cache trim skipped: low memory")`, `Timber.e(t, "Drive token refresh failed")`. Never embed `S0283`, `Sxxxx`, "for ticket S0283", or similar.
- When writing a *temporary* device-test probe for a spec transitioning into `BlockNeedUserTest`, the line MUST start with `Sxxxx: ` exactly - that is the one and only legitimate use of the ticket id inside a log call.
- During `/spec-check`, `/spec-fix`, `/spec-arc`, or any review of touched files: a log line containing `Sxxxx` whose spec is no longer `BlockNeedUserTest` is stale and must be deleted. A log line that legitimately needs to survive must be rewritten to drop the ticket id before the spec leaves `BlockNeedUserTest`.
- If during implementation a probe message turns out to be valuable enough to keep permanently, rewrite it to remove `Sxxxx:` before the spec exits `BlockNeedUserTest`. Decide the line's permanence by content, not by accident of where it was originally typed.
- Commit messages, KDoc, inline `// Sxxxx ...` code comments, and dev-changelog entries are NOT logs - they may freely reference the ticket id. The rule applies strictly to runtime log calls (`Timber.*`, `Log.*` in any form).
