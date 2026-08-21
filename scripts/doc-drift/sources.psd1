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
#   KnownRoutes - routes cited as `/name` that legitimately have no .claude/commands/<name>.md:
#                 built-in CLI commands, plugin skills, documented chat aliases, retired
#                 command names cited historically, and documentation metavariables. Each entry
#                 carries its own reason so the exemption stays re-judgeable (S1849).
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

        # S1850: a document is a rule/prompt SURFACE when it PRESCRIBES an executable command to an
        # agent - "run this" - rather than merely mentioning one. That line excludes, deliberately:
        # logs and dated records (dev/CHANGELOG.md, dev/*_audit.md, dev/AGENT_PROCESS_AUDIT_*.md,
        # dev/TASK_S0588_*), generated render targets (docs/SCRIPT_CHEATSHEET.md, docs/FLAVOR_MATRIX.md),
        # and publication material (delivery/INVENTORY.md, play/listing/, store_assets/). Those cite
        # commands as history or as data; auditing them would gate the present on the past.
        # Measured before widening: 60 candidate documents mention a command, 14 carried a finding,
        # 18 findings in total - the set below is the prescriptive subset and carried 5 of them.
        @{ path = '.claude/reference/*.md';           kind = 'skill';     audited = $true }
        @{ path = '.claude/skills/*/SKILL.md';        kind = 'skill';     audited = $true }
        @{ path = '.github/agents/*.agent.md';        kind = 'agent';     audited = $true }
        @{ path = 'GEMINI.md';                        kind = 'agentsmd';  audited = $true }
        @{ path = 'dev/CATALOG/README.md';            kind = 'workflow';  audited = $true }
        @{ path = 'dev/ACTIVITY_CATALOG/README.md';   kind = 'workflow';  audited = $true }
        @{ path = 'dev/RULE_AND_SKILL_AUTHORING.md';  kind = 'workflow';  audited = $true }
        @{ path = 'docs/DEV_OPS.md';                  kind = 'workflow';  audited = $true }
        @{ path = 'docs/BUILD_TEST_FAST_PATH.md';     kind = 'workflow';  audited = $true }
        @{ path = 'docs/AGENT_HOOKS.md';              kind = 'workflow';  audited = $true }
        @{ path = 'docs/CODE_AUDIT_PROTOCOL.md';      kind = 'workflow';  audited = $true }
    )

    ScriptRoots = @(
        'scripts'
        'dev/CATALOG/scripts'
        'dev/ACTIVITY_CATALOG/scripts'
        # Hook scripts are cited by CLAUDE.md, AGENTS.md and dev/PROJECT_OPERATIONS_INDEX.md;
        # without this root every one of them reads as a documented-but-absent script (S1849).
        '.claude/hooks'
        # S1850: both hold real, runnable scripts that surfaces cite by path. Without them a present
        # file reads as absent - run-fastmediasorter/smoke.ps1 and scripts/ci/fetch-prebuilt-libs.sh both did.
        '.claude/skills'
        'scripts/ci'
    )

    # S1850: scripts a surface legitimately cites although this repository does not contain them.
    # The sza plugin ships its guard hooks, and CLAUDE.md rules 24-28 name them by path; they are
    # absent by design, not by drift, and each entry carries its reason so it stays re-judgeable.
    KnownAbsentScripts = @(
        @{ script = '.claude/hooks/guard-fire-and-forget.ps1'; reason = 'shipped by the sza plugin, not by this repository - CLAUDE.md Rule 26' }
        @{ script = '.claude/hooks/guard-bash.ps1';            reason = 'shipped by the sza plugin, not by this repository - CLAUDE.md rules 24, 25, 27, 28' }
        @{ script = 'hooks/tests/smoke-hooks.ps1';             reason = 'lives in the sza plugin repository beside the hooks it exercises, not here' }
    )

    KnownRoutes = @(
        @{ route = 'compact'; reason = 'built-in Claude Code CLI command - no repo command file exists' }
        @{ route = 'clear';   reason = 'built-in Claude Code CLI command - no repo command file exists' }
        @{ route = 'loop';    reason = 'plugin-provided skill, not a repo command file' }
        @{ route = 'run';     reason = 'plugin-provided skill, not a repo command file' }
        @{ route = 'ns';      reason = 'retired command folded into /skill-fix (S1338 phase 07), cited historically' }
        @{ route = 'name';    reason = 'documentation metavariable - "every /name is .claude/commands/<name>.md"' }
        @{ route = 'proc';    reason = 'the Linux /proc filesystem, cited for VmHWM in docs/DEV_OPS.md - not a command route' }
    )
}
