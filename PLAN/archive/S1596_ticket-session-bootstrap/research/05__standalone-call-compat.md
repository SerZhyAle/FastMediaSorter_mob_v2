# S1596 research 05 - callers of the chain outside session start

**Resolves:** strategic §6 item 5
**Performed:** 2026-08-12, from research 00.

## Calls that are not session start

| Script | Non-bootstrap caller | Why it cannot be absorbed |
| --- | --- | --- |
| `spec-next-preflight.ps1` | `spec-next-session.ps1 -Verb Handoff` (composes it with `search.ps1` for the "what's next" report) | Runs at context-threshold stop, not at session start |
| `ticket-lease.ps1` | `spec-next-preflight.ps1 -Verb Status` (to exclude leased ids from ranking); `run-spec-all-queue.ps1` (external driver, claims per ticket); `.claude/commands/spec-next.md` Stage 5 (`-Verb Release`, every outcome) | Release is a session-end act; the external queue driver runs outside Claude entirely |
| `spec-next-session.ps1` | Stage 5 `-Verb Record`, Stage 5b `-Verb CheckContext`, Stage 6 `-Verb Report` | Mid- and end-of-session verbs, unrelated to bootstrap |
| `device-ready.ps1` | `.claude/commands/spec-dev.md` device-test gate (branches on exit code, no `-Json`) | Runs when a ticket reaches device verification |
| `select.ps1` | `.claude/commands/spec-dev.md` argument resolution (slug to id); many ad-hoc uses - 143 runs in the measured week, the single most frequent command shape in the repo | General-purpose catalog read |

## Decision

Every component stays callable standalone with its current parameter surface and exit codes. The package is **additive**: it composes them for the one path where they always run together, and removes nothing.

This is not a compromise, it is the only workable answer - four of the five have live callers outside session start, and one of them (`run-spec-all-queue.ps1`) runs outside Claude Code entirely and cannot be routed through an agent-facing facade.

Consequence for the plan: no phase may change a component's parameter surface or exit-code contract. If the package needs something a component does not expose, it is added as a new optional parameter with the old default preserved.
