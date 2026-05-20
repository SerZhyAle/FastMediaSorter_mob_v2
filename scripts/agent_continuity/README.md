# Agent Continuity Layer (S0268)

Internal infrastructure for cross-session continuity inside this repository. The layer is composed of five small PowerShell utilities under `scripts/agent_continuity/`. Each one runs in isolation, has an explicit exit code, and writes only into `temp/` or gitignored paths under `dev/`. No file produced by this layer enters a published build.

The five utilities map one-to-one to strategic spec [`PLAN/S0268_agent_continuity_layer.md`](../../PLAN/S0268_agent_continuity_layer.md) §5.1..§5.5.

---

## Pillars

| # | Strategic § | Utility | One-line purpose |
|---|-------------|---------|------------------|
| 1 | §5.1 | `scripts/agent_continuity/start-packet.ps1` | Bootstrap packet - prints seven informational blocks at the start of a significant session. |
| 2 | §5.2 | `scripts/agent_continuity/session-snapshot.ps1` + `scripts/agent_continuity/session-resume.ps1` | Resume layer - writes and reads compact Markdown snapshots under `temp/sessions/<ts>_<agent>_state.md`. |
| 3 | §5.3 | `scripts/agent_continuity/request-log.ps1` | Request logger - appends one JSONL line per significant session to `dev/agent-continuity/requests.jsonl`. |
| 4 | §5.4 | `scripts/agent_continuity/request-digest.ps1` | Request digest - reads the JSONL log, `dev/FUNCTIONALITY.log`, and recent `PLAN/S*.md` to print a ranked profile; tolerates an empty log. |
| 5 | §5.5 | `scripts/agent_continuity/dirty-tree-guard.ps1` | Dirty-tree guard - classifies an intended set of edit paths against the current working tree (`clean` / `same area` / `same file` / `high-risk overlap`) and informs without blocking. |

Each utility may be invoked alone. No shared runtime, no shared module. ADR-4 in the strategic spec is the source for this independence.

---

## Tactical decisions

These resolve the six open research items recorded in strategic §6. The resolutions are mechanical; no follow-up owner input is required.

### §6.1 - Reanimate vs replace `scripts/log-ai-request.ps1`

**REPLACE.** The legacy script uses an XML router-logging contract (`<question>`, `<analyzer>`, `<complexity>`, `<recommended_model>`, `<reasoning>`) which is incompatible with the session-activity contract required by strategic §5.3 (raw request, route, module, flavor, ticket, files touched, validation, interruption, outcome). A new utility `scripts/agent_continuity/request-log.ps1` ships with the correct schema. The legacy script stays on disk so existing callers (if any surface during rollout) keep working; it must not be invoked by new code or skills. Physical removal is deferred to a follow-up cleanup spec.

### §6.2 - Agent identifier source for snapshot filename

**Resolution order:** `-Agent <name>` parameter wins; otherwise environment variable `AGENT_NAME` if set and non-empty; otherwise literal `agent`. No harness changes required. Snapshots from anonymous sessions land at `temp/sessions/<ts>_agent_state.md`; named sessions at `temp/sessions/<ts>_<agent>_state.md`.

### §6.3 - Snapshot trigger

**Skill-driven (option а).** Each significant skill calls `session-snapshot.ps1` as a documented post-step in its own contract. The in-spec integration target is `/spec-dev` (`.claude/commands/spec-dev.md`), added by Phase 03.4 of the tactical plan. Other skills may adopt the same convention in follow-up specs - no obligation here. There is no harness hook, no daemon, no implicit invocation.

### §6.4 - High-risk overlap file list

**Baseline embedded in `dirty-tree-guard.ps1`:**

- `CLAUDE.md`
- `AGENTS.md`
- `app_v2/build.gradle.kts`

The list is extensible at runtime via the `-ExtraHighRiskPaths` parameter without modifying the script. Expansion of the baseline list is a separate tactical decision and should be driven by real overlap incidents, not speculation.

### §6.5 - Sxxxx candidate source for bootstrap packet

**Option (г) combined.** Explicit `-Ticket S####` parameter on `start-packet.ps1` overrides everything else. Otherwise the script reads `PLAN/spec-catalog.jsonl` and picks the record with the most-recent `updated` timestamp whose `status` is one of `Draft`, `Approved`, `Tactical`, `In Progress`, `BlockNeedUserTest`. If no record matches, the block prints `none`. The terminal/finished statuses (`Implemented`, `Verified`, `Partial`, `Broken`, `BlockByOtherTask`, `BlockQuestions`, `BlockExternal`, `Archived`) are excluded from the heuristic.

### §6.6 - Request log format

**Single JSONL** at `dev/agent-continuity/requests.jsonl`. One JSON object per line, append-only via `Add-Content`. Eleven keys in fixed order: `ts`, `request`, `route`, `module`, `flavor`, `ticket`, `files_touched`, `validation_kind`, `validation_exit`, `interruption_marker`, `outcome`. The directory is gitignored (added by Phase 04.2) so accumulated logs never enter version control. Schema is frozen by the eleven-key contract; future fields must be additive (appended to the object) so older lines stay parseable.

---

## Deprecation notice

`scripts/log-ai-request.ps1` is superseded by `scripts/agent_continuity/request-log.ps1`. The legacy script remains on disk for callers that may still invoke it during a transition window, but it must not be referenced by new code, new skills, or new documentation. Physical removal is deferred to a follow-up cleanup spec. Any digest produced by `scripts/agent_continuity/request-digest.ps1` reads only the new JSONL log; it does not read the legacy XML log.
