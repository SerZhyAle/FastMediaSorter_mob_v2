# Authoring Rules, Gates, Commands & Skills

How to add or evolve an agent-facing directive (a `CLAUDE.md` rule, a `scripts/quality/assert-*.ps1` gate, a `.claude/commands/*.md` slash command, a `.claude/skills/*/SKILL.md`, or a `.claude/agents/*.md`) so it actually changes agent behavior instead of adding noise.

Adapted from the `obra/superpowers` `writing-skills` and `verification-before-completion` disciplines, reconciled with this project's reality: rules live in `CLAUDE.md` (+ `AGENTS.md` mirror), recurring rules get mechanized as gates, and routing is centralized in `CLAUDE.md` §3.

## Core principle: no rule without an observed failure first

Do not write a directive from imagination. Write it only after an agent has demonstrably failed without it. A rule authored to "look complete" teaches nothing the model was not already doing; a rule authored against a real failure closes a real gap.

The loop:

1. **OBSERVE** - capture the actual failure verbatim: the wrong action, and the *rationalization* the agent used to justify it ("this build looked fine", "the diff was trivial", "tests probably pass"). The rationalization is the load-bearing evidence - it is the loophole the rule must close.
2. **WRITE minimal** - the shortest directive that would have prevented *that specific* failure. Do not generalize to hypothetical cases; add them only when they too are observed.
3. **CLOSE loopholes** - list the excuses that would let an agent wriggle out, and counter each explicitly (see rationalization table below). "Violating the letter violates the spirit."
4. **PROMOTE if recurring** - if the failure is mechanically detectable and recurs, convert the rule into a `scripts/quality/assert-*.ps1` gate wired into `post-change.ps1` / `a.ps1 fg`. Prose rules are advisory; gates are enforced. See `CLAUDE.md` Rule 19/20 and `docs/CODE_AUDIT_PROTOCOL.md` Layer 8.

## Rationalization table

For any discipline rule (one the agent is tempted to skip under time/sunk-cost/authority pressure), record the real excuses and their counters. Example shape:

- Excuse: "The change is one line, no need to build." -> Counter: one-line changes to gated files still run the fast gate; `a.ps1 fk` is seconds.
- Excuse: "The subagent reported the build passed." -> Counter: self-reports are not evidence; re-run the command yourself and read the exit code (see verification red-flags in `CLAUDE.md` §12).

Keep excuses that were *actually used*, not invented ones - invented excuses bloat the rule and dilute the real counters.

## Description-SDO: make triggers, not summaries

A command / skill / agent `description` is the model-facing surface that decides whether the right directive loads. It must describe **when to reach for this**, never summarize the workflow inside.

Checklist for every `description:` field:

- Start with "Use when.." and lead with triggering conditions - user intents, symptoms, error messages, file/area contexts.
- Do **not** pretell the process ("first does X, then Y"). Testing shows agents follow a description's process summary and skip the fuller body.
- Keep it under ~500 characters.
- Prefer concrete triggers ("fix a typo / one string / a colour", "logcat analysis", "archive a spec") over vague titles ("Quick Fix", "Log Reader").

`CLAUDE.md` §3 is the authoritative routing prose; per-command descriptions are the redundant trigger surface that lets the model self-route when §3 is not front-of-mind. Keep descriptions to stable trigger phrases so the two do not drift.

## Where each artifact lives

- Rule (behavioral, always-loaded): `CLAUDE.md` - mirror into `AGENTS.md` (and update `.github/copilot-instructions.md` if the shared rule appears there).
- Mechanical gate: `scripts/quality/assert-*.ps1`, wired into `scripts/post-change.ps1` and `a.ps1 fg`.
- Slash command: `.claude/commands/<name>.md` with a trigger-focused `description:` in frontmatter.
- Auto-triggered skill: `.claude/skills/<name>/SKILL.md`.
- Subagent: `.claude/agents/<name>.md`.

## When NOT to add a rule

- The failure was a genuine one-off with no recurrence risk - fix it inline, do not codify.
- The rule would duplicate an existing rule or gate - update the existing one instead (no two sources of truth).
- The rule is mechanically checkable and would be ignored as prose - write the gate directly.
