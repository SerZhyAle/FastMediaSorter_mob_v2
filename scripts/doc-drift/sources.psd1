# sources.psd1 - canonical-source manifest for the rule/prompt executable drift audit (S0315).
#
# Loaded via Import-PowerShellDataFile. Declares the audited source set as DATA so the
# set is extended without code change (strategic ADR-1; tactical Phase 01 Step 01.1).
#
# Keys:
#   Canonical   - the single canonical rule authority (repo-relative path).
#   Surfaces    - comparison surfaces audited against the canonical and against on-disk
#                 script reality. Each entry: path (repo-relative file or glob), kind, audited.
#                 kind in: skill, agent, agentsmd, copilot, workflow.
#   ScriptRoots - directories whose *.ps1 files form the on-disk script reality; a detector
#                 asks "is this documented script present on disk" against this set.
#
# This manifest declares executable surfaces only; the audit reports executable mismatch
# (missing scripts, route conflicts, missing -NoProfile, abolished artefacts) per ADR-1.
@{
    Canonical = 'CLAUDE.md'

    Surfaces  = @(
        @{ path = '.claude/commands/*.md';            kind = 'skill';     audited = $true }
        @{ path = '.claude/agents/*.md';              kind = 'agent';     audited = $true }
        @{ path = 'AGENTS.md';                        kind = 'agentsmd';  audited = $true }
        @{ path = '.github/copilot-instructions.md';  kind = 'copilot';   audited = $true }
        @{ path = 'dev/AGENT_WORKFLOW.md';            kind = 'workflow';  audited = $true }
        @{ path = 'dev/PROJECT_OPERATIONS_INDEX.md';  kind = 'workflow';  audited = $true }
    )

    ScriptRoots = @(
        'scripts'
        'dev/CATALOG/scripts'
        'dev/ACTIVITY_CATALOG/scripts'
    )
}
