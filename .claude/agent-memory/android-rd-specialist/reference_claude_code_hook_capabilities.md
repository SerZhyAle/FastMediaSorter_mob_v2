---
name: claude-code-hook-capabilities
description: What a Claude Code hook can and cannot do - PreToolUse rewrites tool input (measured), PostToolUse cannot rewrite a result, nothing auto-retries, and a pre-filter can silently disarm a hook
metadata:
  type: reference
---

Verified 2026-08-12 against the official docs, then **confirmed by experiment against the running
Claude Code** while shipping S1594.

**A PreToolUse hook can MODIFY the tool input**, not just allow/deny/ask. Return
`hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "allow", updatedInput: {..} }`
on stdout and exit 0. That turns a refusing guard into a correcting one - the refusal costs a turn,
the correction costs nothing.

Measured facts for the **Read** tool, no longer guesses (the earlier "confirm before relying on it"
caveat is discharged):

- `updatedInput` **works**. A Read of a 2000-line file with no `limit` returned exactly the injected
  800 lines.
- `updatedInput` must carry the **full input object** - copy every property of `tool_input` and add
  yours. The partial-patch form was never tested and is not what ships.
- `additionalContext` **is surfaced to the model**, attached to the tool result as
  `PreToolUse:<Tool> hook additional context: ..`. This is the only usable channel for telling the
  model what the hook did.
- `permissionDecisionReason` is **NOT surfaced**. A rewrite carrying only that field is completely
  silent, which is a correctness hazard: the model reasons about a file it only partly saw.

**What is NOT possible** - do not design around these:
- PostToolUse cannot rewrite or replace the tool result the model sees. It can only add
  `additionalContext` or `decision: "block"`, and by then the tool already ran.
- No hook can fix-and-retry a failed command (e.g. rewrite `python3` -> `python` after the failure).
  So a failing command class is cheaper to prevent with a PATH shim or a PreToolUse rewrite than to
  catch after.
- `UserPromptSubmit`'s `additionalContext` does not persist - current turn only, and a resumed
  session replays the saved text instead of re-running the hook, so dynamic values go stale.

**The bash pre-filter is part of the hook, and it can silently disarm it.** Every guard in
`~/.claude/settings.json` is wired as `case "$input" in <glob>) .. | pwsh -File <hook>`, to avoid
paying the ~170-250 ms pwsh start on calls the hook would allow anyway. A pre-filter that stops
matching makes the hook unreachable, and **an unreachable hook is indistinguishable from one that
allows everything** - nothing fails, nothing logs. S1594 shipped `*[A-Z]-[A-Z]*` for PowerShell
cmdlets and it matched none of them: in `Select-Object` the character before the hyphen is lowercase.
Correct shape is `*[a-z]-[A-Z]*`, which also avoids over-matching capital flags (`grep -A`, `ls -F`).
**How to apply:** when adding or editing a guard, test the registered case pattern under Git Bash
against both must-reach and must-skip commands, not just the hook's own exit codes. Pin
`C:\Program Files\Git\bin\bash.exe` - `C:\WINDOWS\system32\bash.exe` is WSL and tests the wrong shell.

**Why:** this decides whether a recurring failure class becomes a shim, a blocking guard or a silent
rewrite. Guessing it wrong costs a rewrite of the hook.

**How to apply:** before proposing "a hook could fix this automatically", check which of the three
shapes it needs. A hook that changes what the model *reads* must fail open by emitting nothing, since
its errors now corrupt content rather than merely gating a call. Measured perimeter for this repo's
guards: [[process-audit-2026-07]]. Live contract tests for the two S1594 hooks:
`.claude/hooks/global-hook-tests/`.
