---
name: index-pwsh-traps
description: Second-level pointer list for PowerShell, shell scripting, CLI tools and automation traps. Open when authoring or debugging scripts, CLI wrappers or pwsh/bash commands.
metadata:
  type: reference
---

# PowerShell & Shell Scripting - pointers

Split out of `MEMORY.md` (S1731, 2026-08-17): memories specific to PowerShell authoring, bash/pwsh boundary traps, CLI wrappers and script debugging. Open this file when writing or troubleshooting `.ps1` scripts, command pipelines or CLI helpers.

- [Tool-bypass discipline](feedback_tool_bypass_discipline.md) · script params: `scripts/utils/help.ps1 -Name <script>`
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) · [Check existing tooling](feedback_check_existing_tooling.md)
- [Cyrillic bash->pwsh](feedback_cyrillic_bash_pwsh_boundary.md) · [pwsh shim](reference_pwsh_shim.md) · [byte traps](feedback_pwsh_authoring_byte_traps.md)
- [Bash `cd` leaks CWD](feedback_bash_cd_leaks_into_powershell_cwd.md)
- [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) · [param/local collision](feedback_pwsh_param_local_case_collision.md)
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) · [strings tool](reference_strings_tool.md) + [main/res only](feedback_string_tools_main_res_only.md)
- [Grep mangles `//`](feedback_grep_output_mangles_comment_markers.md) · [`Measure-Object -Line` skips blank lines](feedback_measure_object_line_undercounts_loc.md) - use `wc -l` for LOC gates
- [rg skips CATALOG](feedback_rg_gitignore_catalog.md) · [BG exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md)
- [rg missing in the Bash tool](project_rg_absent_in_bash_tool.md) - a script branching on rg answers differently per tool; git-index fallbacks miss uncommitted work
- [`Select -First N` detaches a running script](feedback_select_first_detaches_running_script.md) - state read next is mid-write
- [$LASTEXITCODE guard after a cmdlet](feedback_lastexitcode_null_after_cmdlet.md)
- [Backgroundable script -> scripts/utils](feedback_background_waiter_must_not_live_in_spec_catalog.md) - the hook matches the directory
- [Workflow journal](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md)
- [exit codes](project_spec_catalog_exit_code_contract.md) · [delete is soft](project_spec_catalog_delete_is_soft_and_ids_burn.md) · [insert -File](project_insert_ps1_file_validation.md)
