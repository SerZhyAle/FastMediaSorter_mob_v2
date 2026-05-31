# Rule/prompt executable drift audit (S0315)

`check-rule-prompt-drift.ps1` reports **executable** mismatch between the canonical rules and every surface that is supposed to agree with them: prompt skills, agent profiles, `AGENTS.md`, copilot instructions, workflow docs, and the scripts that actually exist on disk. It never reports wording, tone, or narrative differences - only mismatch that would make an agent run the wrong command or a missing one.

Strategic context: `PLAN/S0315_rule-prompt-drift-audit.md`. Parent umbrella: S0311.

## Canonical source and audited surfaces

- Canonical authority: `CLAUDE.md`.
- Comparison surfaces are declared as data in `scripts/doc-drift/sources.psd1` (`.claude/commands/*.md`, `.claude/agents/*.md`, `AGENTS.md`, `.github/copilot-instructions.md`, `dev/AGENT_WORKFLOW.md`, `dev/PROJECT_OPERATIONS_INDEX.md`). Extend the set by editing the manifest - no code change.
- On-disk script reality is gathered from the manifest `ScriptRoots`.

## Mismatch kinds (closed taxonomy)

- **MissingDocumentedScript** - a surface cites a repo-relative script path that is absent on disk. Evidence: `.claude/commands/foo.md` references `scripts/does-not-exist.ps1`.
- **ConflictingRouteName** - a surface cites a `/skill` route with no matching `.claude/commands/<route>.md`. Evidence: a doc tells the agent to run `/totally-made-up-route`.
- **MissingNoProfile** - a documented `pwsh` invocation omits `-NoProfile`. Evidence: `pwsh -File scripts/foo.ps1` in a command example.
- **AbolishedArtifactReference** - a surface prescribes an abolished artefact. Evidence: a `_spec_` segment in a `PLAN/` path, or a separate audit/fix file under `PLAN/`.
- **StaleCommandExample** - a documented command example no longer matches the script it names.
- **OutdatedValidationRule** - a per-step closure rule a surface prescribes that the canonical no longer lists.

## Output grammar

```text
<MISMATCHKIND> | <sourceA> | <sourceB> | expected: <X> | actual: <Y>
SUMMARY | total: N | conflict: A | stale: B | missing: C
```

`-Json` emits one object `{ verdict, total, byKind, records }` where `verdict` is `DRIFT` when `total > 0` else `CLEAN`.

## Exit codes

- `0` CLEAN - no mismatch records.
- `1` DRIFT - one or more mismatch records.
- `2` USAGE - manifest or module load error.

## Quick start

```powershell
# Resolved source set + category list, no scan, writes nothing
pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -DryRun

# Machine output
pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -Json

# Human report (also writes temp/rule-prompt-drift_<ts>.json)
pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1
```

## Out of scope and reuse boundary

- Out of scope by construction: wording, tone, and narrative quality. The taxonomy has no such category; the audit cannot emit one.
- Version-pin drift stays in `scripts/check-doc-vs-gradle.ps1` - this audit does not re-implement it.
- Spec-id-marker drift stays in `scripts/spec_catalog/drift-check.ps1`.
- Adjacent tickets `S0278` (doc-vs-gradle wear coverage) and `S0279` (doc-vs-gradle PR-gate wiring) own the doc-vs-gradle checker's growth. S0315 must not fork, restore, or absorb their scope; it layers rule-vs-prompt-vs-script mismatch on top of the existing drift family.
