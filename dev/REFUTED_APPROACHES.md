# Refuted approaches

This index admits an entry only when a source ticket supplies a measurement. It records
what someone might propose, what the measurement disproved, and what shipped instead.

| Proposed approach | Source and measurement | What shipped instead |
|---|---|---|
| Extend the lexical detekt preflight with more size rules | S1595: the three current rules fully cover only 13.9% of attributable failures; nine hand-listed rules reach 48.1%, and flagged and unflagged classes overlap across a 240-line band, so size rules have no lexical threshold. | Run the real analyser over changed files. |
| Turn on detekt `--auto-correct` | S1595: the only switch is the shared `formatting` flag, which would arm about 5,591 findings, 46% of the baseline; line length is not mechanically repaired. | Keep the real analyser scoped and fix findings deliberately. |
| Put a closed `ValidateSet` on document-registry query values | S1597: PowerShell rejects the value before the script body runs, returning the same empty answer more expensively and duplicating the registry vocabulary. | Resolve near-misses and print the live vocabulary. |
| Add a hook that fixes and retries a failed command | S1594: `PostToolUse` cannot rewrite a result and no hook can retry; a failed command is cheaper to prevent with a PATH shim or `PreToolUse` rewrite. | Make names available through a shim or prevent the call before execution. |
| Parallelise closure gates instead of accumulating their failures | S1598: parallelism improves fast-path time but does not solve recovery and breaks deterministic output order. | Run gates through the accumulating facade and report every failure. |
| Price context from `UserPromptSubmit` | `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` section 5, carried by S1338: the cost accrues inside autonomous blocks where no prompt is submitted, so the event is blind to exactly the case that pays. Routing on the same event is the inverse and remains correct - record the boundary, or this refutation gets reused against the nudge hook it does not apply to. | Keep prompt-time routing separate from context measurement. |
| Treat the S1599 zero-hit pattern count as a miss count | S1599: the counter incremented on every call, not only on misses, so the claimed miss total was not evidence. | Count only the narrowed zero-hit condition and stay silent when the widened search is also empty. |
| Add `ComplexMethod` to the top detekt failure list | S1595: it caused zero failures because `CyclomaticComplexMethod` supersedes it; the capture named the wrong rule. | Follow measured rule names and use the real analyser rather than guessing a list. |

Retractions are entries too: an incorrect capture is a measured dead end, not a reason to
discard the measurement.
