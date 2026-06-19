# Memory Index

- [noLegal features go to FEATURES_noLegal.md only](feedback_features_nolegal.md) - public `docs/FEATURES*.md` are for standard/VR builds; noLegal-only copy lives in gitignored `_noLegal` mirrors
- [Timestamp every chat message](feedback_timestamp_in_chat.md) - read the auto-injected current time and use it as `[HH:MM:SS]` prefix; never shell out to `date`
- [Capability inventory](project_functionality_log.md) - FUNCTIONALITY.log RETIRED (S0489); capabilities go to docs/ALL_FEATURES.jsonl via all_features/add.ps1; FEATURES*.md is /skill-release-owned; pure rewording = CHANGELOG only
- [No backticks in Bash-tool args](feedback_no_backticks_in_bash_args.md) - bash command-substitution silently eats backticked words inside double-quoted CLI arguments; use single quotes or plain prose
- [Don't ask owner questions the communication policy already answers](feedback_no_owner_questions_when_architecture_already_answers.md) - if `docs/COMMUNICATION_POLICY.md` mandates a wording rule, cite it and pick - don't fabricate a "choice" question
- [PowerShell efficiency: -NoProfile + batching](feedback_pwsh_efficiency.md) - chain string-audit and dev-log calls into one `pwsh -NoProfile -Command` invocation, not two tool calls
- [Don't call scaffolding "done"](feedback_no_scaffolding_as_done.md) - a doc draft is not done until EN/RU/UK mirrors land and the tone checklist passes; one-locale polish is scaffolding
- [pwsh-bash dollar-escape trap](feedback_pwsh_bash_dollar_escape_trap.md) - `\$LASTEXITCODE` inside bash double quotes collapses to empty and breaks the PS chain silently; use newline-separated `-Command` or single-quoted bash
- [Author style: `..` and `ё`/`Ё`](user_author_style.md) - load-bearing voice rule for every user-facing string in any locale
- [pwsh 7 location](feedback_pwsh_path.md) - `/c/Program Files/PowerShell/7/pwsh.exe`; quote the full path when launching PS 7 scripts from Bash
