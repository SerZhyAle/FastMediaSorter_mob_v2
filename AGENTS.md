# FastMediaSorter v2 Agent Protocol

This file imports the active repo rules, skills, and GitHub agent definitions for Codex-compatible agents.

## 1. Source Of Truth

- Load-bearing repo rules: `CLAUDE.md`.
- GitHub Copilot rules: `.github/copilot-instructions.md`.
- Prompt skills: `.github/prompts/*.prompt.md`.
- Agent profiles: `.github/agents/*.agent.md`.
- Fast research entrypoint: `dev/PROJECT_OPERATIONS_INDEX.md`.
- Multi-step process: `dev/AGENT_WORKFLOW.md`.

When files disagree, prefer the stricter rule. For agent-specific execution, `AGENTS.md` imports `CLAUDE.md` first, then `.github/copilot-instructions.md`, then the matching prompt/agent file.

## 2. Communication

- Russian in chat.
- English in code, docs, logs, commits, changelog entries.
- Professional, dry, concise.
- Ask for missing files/data. Do not invent paths, values, decisions, or results.
- Ellipsis is always `..`; the three-dot form is forbidden.
- Use `ё`/`Ё` in Russian where grammatically correct.

## 3. Core Project Rules

- Stack: Android, Kotlin 1.9+, Java 17, Clean/MVVM, Hilt, Room, Media3, Timber.
- Modules: `app_v2/`, `wear/`, `dev/`, `docs/`, `scripts/`, `temp/`.
- Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Generated outputs, logs, backups, and scratch artifacts go to `temp/`.
- Activity logic is prohibited. Delegate complex logic to `helpers/*Manager`.
- Use `Timber`; `Log.d()` is prohibited.
- Before editing code, read inline comments/KDoc/Javadoc in the affected area.
- For string updates, prefer `scripts/utils/set-android-string.ps1` for single Android string keys.
- For any layout edit, check the matching `res/layout-land/` file and update it when present.
- For any user-facing UI/UX change, run the UI ambiguity gate before implementation.

## 4. Research Order

1. `dev/PROJECT_OPERATIONS_INDEX.md`.
2. For `Sxxxx` tasks: `pwsh -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`.
3. For Kotlin class/file lookup: `dev/CATALOG/scripts/query.ps1` before global grep.
4. Domain docs as needed:
   - Architecture: `docs/ARCHITECTURE.md`.
   - Build/scripts/flavors: `docs/DEV_OPS.md`.
   - Dependencies/protocols: `docs/TECH_STACK.md` and `dev/TECH_REQUIREMENTS.md`.
   - Network: `dev/NETWORK_SPECS.md`.
   - Non-standard flavors: `dev/FLAVOR_DEVELOPMENT_RULES.md`.
5. Implementation files.

## 5. Mandatory Skill Routing

Load the matching `.github/prompts/*.prompt.md` before doing the task:

- `/quick`: very small fix, typo, one string, small color/padding/XML tweak.
- `/spec`: create or update `PLAN/Sxxxx_*.md`.
- `/spec-all`: full spec pipeline, idea to verified implementation.
- `/spec-tech`: approved strategic spec to tactical phases.
- `/spec-dev`: execute tactical spec phases.
- `/spec-check`: audit implementation against a spec.
- `/spec-fix`: mechanical fixes from latest spec audit.
- `/spec-update`: refine an existing strategic or tactical spec.
- `/spec-arc`: archive a spec.
- `/spec-test-device`: on-device spec verification.
- `/ui-clarify`: UI/UX placement, visibility, layout, command, menu, empty/error, confirmation, orientation, or accessibility decisions.
- `/catalog`: class lookup, feature lookup, refactor planning, class catalog refresh.
- `/doc-update`: docs or feature inventory updates.
- `/log-reader`: logcat or `logs/current.log` analysis.
- `/build`: build command selection or build execution.
- `/git`: commits, staging, pushing, diffs, history, branch/worktree flow.
- `/research`: broad research task.
- `/caveman`: opt-in terse mode.
- `/caveman-commit`: terse commit message.
- `/caveman-review`: terse review comments.

Do not handle these trigger classes ad hoc when a matching prompt exists.

## 6. Imported Prompt Skills

Prompt files are imported from `.github/prompts/`:

- `build.prompt.md`
- `catalog.prompt.md`
- `caveman-commit.prompt.md`
- `caveman-review.prompt.md`
- `caveman.prompt.md`
- `doc-update.prompt.md`
- `git.prompt.md`
- `log-reader.prompt.md`
- `quick.prompt.md`
- `research.prompt.md`
- `spec-all.prompt.md`
- `spec-arc.prompt.md`
- `spec-check.prompt.md`
- `spec-dev.prompt.md`
- `spec-fix.prompt.md`
- `spec-tech.prompt.md`
- `spec-test-device.prompt.md`
- `spec-update.prompt.md`
- `spec.prompt.md`
- `ui-clarify.prompt.md`

## 7. Imported Agent Profiles

Agent profiles are imported from `.github/agents/`:

- `android-kotlin-developer.agent.md`: Android/Kotlin implementation, ViewModels, UseCases, repositories, Hilt, Room, Media3, Glide, variants, tests.
- `android-rd-specialist.agent.md`: broad Android R&D, architecture, review, spec lifecycle, build/flavor planning, class catalog work.
- `android-solution-researcher-for-specifications.agent.md`: read-only architecture and solution research for specs.
- `friendly-android-doc-writer.agent.md`: docs, README/help/release notes, user-facing copy, communication policy alignment.

Use the narrowest fitting agent profile.

## 8. Workflow

- Current development branch model is defined in `CLAUDE.md` and `.github/prompts/git.prompt.md`.
- Confirm branch before edits.
- For tasks larger than a single-file fix, read `dev/AGENT_WORKFLOW.md` and follow the 5-step process.
- No coding before required clarification/design gates.
- For `.kt` changes, run catalog scan/render after implementation.
- After code/config changes, run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"`.
- For user-visible behavior changes, evaluate feature docs and functionality log requirements from `CLAUDE.md`.

## 9. Validation

Use the validation ladder from `CLAUDE.md`:

- Doc-only changes: grep/content check is enough.
- Script changes: dry-run or execution.
- Config/XML changes: target build unless a stricter prompt says otherwise.
- Kotlin/Java changes: compile and affected tests; refresh catalog.
- Mixed changes: highest applicable validation level.

Record expected vs actual for structural checks.
