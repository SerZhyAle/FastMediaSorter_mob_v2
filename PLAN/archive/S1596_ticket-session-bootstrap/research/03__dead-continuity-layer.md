# S1596 research 03 - fate of the agent-continuity start layer

**Resolves:** strategic §6 item 3, ADR-4
**Performed:** 2026-08-12
**Method:** same transcript sweep as research 01 (`temp/S1596/classify_index_edits.py`), counting `Bash`/`PowerShell` tool calls whose command mentions each script.

## Invocations, window from 2026-08-05

| Script | Calls |
| --- | ---: |
| `scripts/agent_continuity/session-snapshot.ps1` | 120 |
| `scripts/agent_continuity/start-packet.ps1` | 0 |
| `scripts/agent_continuity/session-resume.ps1` | 0 |
| `scripts/agent_continuity/request-log.ps1` | 0 |
| `scripts/agent_continuity/request-digest.ps1` | 0 |
| `scripts/agent_continuity/dirty-tree-guard.ps1` | 0 |

## Comparison against the live chain

`start-packet.ps1` prints seven read-only blocks: branch, dirty-tree, active-ticket, modules, prompt-routing, docs-vs-gradle, ux-volatility. Against the live selection chain:

- **active-ticket** - superseded. `ticket-lease.ps1 -Verb List/Status` and `spec-next-preflight.ps1`'s `leased_ids[]` answer it with liveness, which `start-packet` does not carry.
- **branch**, **dirty-tree** - not consumed by any selection decision; `post-change.ps1 -ScopeToFile` handles the dirty-tree case at closure time instead.
- **modules**, **docs-vs-gradle**, **ux-volatility**, **prompt-routing** - orientation prose with no consumer in any command driver.

Nothing in `start-packet.ps1` carries information the bootstrap facade needs and cannot get from the live chain.

## Decision

Delete four scripts - `start-packet.ps1`, `request-log.ps1`, `request-digest.ps1`, `dirty-tree-guard.ps1` - together with their references in `dev/AGENT_WORKFLOW.md`, `docs/SCRIPT_CHEATSHEET.md`, `docs/AGENT_COST_PLAYBOOK.md` and `scripts/agent_continuity/README.md`. Fold nothing into the facade. `request-log.ps1` and `request-digest.ps1` are a writer/reader pair that is dead on both ends, so removing them leaves no orphaned channel.

Keep the `session-snapshot.ps1` / `session-resume.ps1` pair intact. The writer is called 120 times a week by `/spec-dev` at phase boundaries and is referenced by `.claude/commands/spec-dev.md` and `.claude/reference/spec-dev.md`; deleting its reader would leave a live channel with no interface to read it, which is a worse state than the one being fixed. The pair's actual problem - writes with no observed reads - is parked as **S1603**, because retiring a live per-phase-boundary ritual is a different decision from removing files nobody calls, and it first needs evidence about whether snapshot files are read directly rather than through their reader.
