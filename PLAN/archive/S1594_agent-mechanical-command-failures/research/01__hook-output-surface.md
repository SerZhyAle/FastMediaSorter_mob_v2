# Research 01 - PreToolUse hook output surface, measured against the running Claude Code

**Date:** 2026-08-12
**Ticket:** S1594
**Question:** spec §6 open item - can a `PreToolUse` hook inject `limit` into a `Read` call instead of
refusing it, and if it does, can it tell the model that it did?

The 2026-08-12 audit recorded the capability as *documented but thinly specified per tool*, with an
explicit instruction to confirm the Read-tool shape against the running version before relying on it.
This is that confirmation. Both probes ran against the live harness by temporarily replacing
`~/.claude/hooks/guard-uncapped-read.ps1`; the production guard was restored from
`temp/S1594/guard-uncapped-read.ps1.bak-20260812-100311` immediately afterwards.

## Probe 1 - does `updatedInput` reach the Read tool?

Hook emitted on stdout, exit 0:

```json
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow",
 "permissionDecisionReason":"S1594 probe: injected limit=42",
 "updatedInput":{"file_path":"..","limit":42}}}
```

Target: `.claude/commands/spec-next.md`, 254 lines, Read issued with neither `offset` nor `limit`.

- **expected:** either 254 lines (input ignored) or 42 lines (input rewritten)
- **actual:** exactly 42 lines returned

**`updatedInput` works for the Read tool.** The object must be the *full* input - the probe rebuilt
every original property and added `limit`, and that shape is confirmed working. Whether a partial
object merges was not tested; the full-object form is what this ticket will ship.

## Probe 2 - can the hook say that it truncated?

Probe 1 returned 42 lines with **no visible indication** that anything had been injected -
`permissionDecisionReason` was not surfaced to the model at all. A silent cap is a correctness
hazard: the model would reason about a file it had only partly seen and would have no signal that
more existed.

Probe 2 added `additionalContext` (and a top-level `systemMessage`) next to `updatedInput`, injecting
`limit: 37` against `.claude/commands/build.md`, 334 lines.

- **expected:** 37 lines, plus some visible note if either field is surfaced
- **actual:** 37 lines, and this line appeared attached to the tool result:

```text
PreToolUse:Read hook additional context: S1594 PROBE-2: this Read was capped to 37 of 334 lines by guard-uncapped-read.
```

**`additionalContext` is surfaced to the model; `permissionDecisionReason` is not.** The warning must
therefore ride in `additionalContext`. `systemMessage` was emitted in the same payload and could not be
distinguished from `additionalContext` in the tool result - it is not needed and will not be shipped.

## Consequences for the design

1. The read guard converts from **deny** to **rewrite**: inject a window, never refuse. The 381 blocks
   per week become 0 turns instead of 381, and the context saving is preserved.
2. Every truncating rewrite **must** carry an `additionalContext` line naming the injected limit and the
   real line count, so the model knows to re-read with an explicit window if it needs more. A rewrite
   that does not truncate (file shorter than the injected limit) must stay silent - a warning that
   fires when nothing was lost trains the model to ignore it.
3. Fail-open stays mandatory. A malformed emission from this hook now affects the *content* the model
   receives, not merely whether the call proceeds, so any error path must fall back to plain `exit 0`.

## Confirmed NOT possible (unchanged from the audit, not re-tested here)

- `PostToolUse` cannot rewrite a tool result.
- No hook can fix-and-retry a failed Bash command. This is why the `exit 127` half of this ticket needs
  a PATH shim (`python3`) and a pre-call refusal (PowerShell cmdlets, `node`), not a post-failure fixer.
