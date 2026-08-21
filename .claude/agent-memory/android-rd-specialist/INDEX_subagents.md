---
name: index-subagents
description: Second-level pointer list for subagent and verification-discipline memories - what a subagent may not do, which of its claims to re-check yourself, device ownership between sessions, and the traps in loops, wakeups and spec edits. Open when delegating work or judging a subagent's report.
metadata:
  type: reference
---

# Subagents & verification discipline - pointers

Split out of `MEMORY.md` (2026-08-21): the top-level index is billed on every turn of every session, and these are needed only for this kind of work. The hooks here are each memory's own `description`, restored in full - the top-level index had squeezed several of them mid-word.

- [Grep the symbol, not the warning](feedback_grep_the_symbol_not_the_warning.md) - A deprecation-warning inventory undercounts call sites wherever local @Suppress exists - always grep the symbol itself before planning a removal.
- [Split device acceptance first](feedback_split_device_acceptance_before_draining.md) - Before spending a device pass on a BlockNeedUserTest ticket, split its acceptance note into the half a grep can decide and the half only the device can - the static half is usually unchecked and sometimes already false
- [Verify every variant](feedback_verify_all_variants_of_the_screen.md) - Device-verify every variant of a changed screen (both media kinds, both filters, both display modes) - not just the branch you touched
- [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md) - When a device fix "doesn't work", first confirm the phone runs the NEW build - same-version debug APKs can silently keep the old one
- [Device agent needs a known tap path](feedback_device_subagent_needs_known_tap_path.md) - delegate a device task only when the exact tap path is already known; a device subagent's budget runs out mid-UI-navigation, and even a one-log-line task costs more delegated than driven directly
- [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) - implementation subagents for multi-phase tooling plans tend to finish core phases but run out of budget before the final docs-catalog-cleanup phase; plan a central pickup
- [no git/build](feedback_parallel_agents_no_git_build.md) - Parallel implementation subagents must never run git (stash/checkout) or gradle builds; one agent's git stash silently clobbers another's uncommitted edits
- [Concurrent red tree](project_spec_all_concurrent_tree_red.md) - Owner runs /spec-all on sub-specs in parallel with a foreground /spec-dev; the shared src tree can go red from another ticket's half-written file
- [Frozen? TracerPid](feedback_frozen_app_check_tracerpid.md) - App frozen on emulator with no crash/ANR trace? Check /proc/<pid>/status TracerPid - lldb-server (native debugger) attached freezes the VM, not a code bug
- [Never tap a device an agent holds](feedback_never_drive_a_device_an_agent_holds.md) - Do not input to, or kill, a device another session is using - check SPEC-TICKET.LEASES and CODE.LOCK first, and never stop an emulator by process name
- [Only you install on a device](feedback_orchestrator_owns_device_installs.md) - A subagent must never build or install an APK on a device - the orchestrator installs, names the file first, and records versionName before handing the device over; a wrong-flavor install is silent because -d allows the downgrade
- [Silent != stuck](feedback_silent_subagent_is_not_stuck.md) - Before force-killing a long-running background agent, check its artifact directory mtimes - chat silence is not evidence of a stall
- [Researcher may lack web tools](feedback_research_subagent_may_lack_web_tools.md) - A research subagent can lack WebSearch/WebFetch even when the parent has them, so its platform claims may be trained knowledge only - measure on device before believing them
- [Welcome consolidation](feedback_welcome_process_consolidation.md) - Owner welcomes cutting/merging workflow ceremony and authorizes editing CLAUDE.md, agent defs, and skill files to do it
- [5h limit](feedback_workflow_session_limit_budget.md) - Low parallelism by default - owner's 5h session limit is shared with all subagents; big fan-outs burn it and die unfinished; hard cap ~6-8 agents without owner GO
- [Don't stop on a guess](feedback_dont_stop_loop_on_context_guess.md) - Never end a /spec-next or other long loop citing "context is running out" - there is no context meter; keep going until work is genuinely done or blocked
- [ScheduleWakeup is /loop-only](feedback_schedulewakeup_loop_mode_only.md) - ScheduleWakeup tool is scoped to /loop dynamic mode - don't call it to wait on a background task inside /spec-do or any other non-loop session.
- [Never style-edit a spec](feedback_no_ellipsis_edits_in_verbatim_code_spans.md) - the house text style never applies to PLAN/*.md - no gate checks it, and editing a spec's punctuation is the mistake, not the fix
- [Edit splice](feedback_edit_line_delete_splice.md) - Deleting a line via Edit with old_string starting with "\n" splices neighbours on CRLF files - use the full adjacent line instead
- [python heredoc eats `\a`/`\b`](feedback_python_heredoc_eats_backslash_escapes.md) - A python3 heredoc run through the Bash tool loses one backslash level, so `\a` becomes BEL and `\b` becomes backspace inside spec text - build such literals from chr(92)
- [An invariant is a claim](feedback_documented_invariant_is_a_claim.md) - A phase-log audit line or an ARCHITECTURE.md invariant is a claim someone wrote, not a verified fact - re-check it in code before trusting or repeating it
- [Resolved may be inference](feedback_resolved_research_item_may_be_inference.md) - A strategic spec's §6 item marked Resolved may have been answered by inference, not measurement - re-measure the premise before planning phases on it, especially screen classifications and resource-variant deltas
- [Visibility != action](feedback_visibility_condition_is_not_the_action.md) - A control's visibility condition says nothing about what its handler does - read the handler before describing behaviour
- [Audit the fixes too](feedback_audit_fixes_need_their_own_round.md) - Re-run the auditor on the fixes themselves - in this repo each round of P1 fixes has introduced a fresh defect
- [Read the screenshot yourself](feedback_subagent_pixel_measurements_unreliable.md) - A device-operator subagent's reported pixel extents from a screenshot can be flatly wrong - read the image yourself when the measurement is the evidence
- [javap the android.jar](feedback_verify_platform_api_with_javap.md) - Confirm a platform API's exact signature with javap against the compileSdk android.jar before planning or speccing around it
- [Verify owner's mechanism](feedback_verify_owner_proposed_remedy_mechanism.md) - The owner reports symptoms accurately but his proposed fix encodes a guessed mechanism - trace the real cause before implementing what he asked for
- [Signed-off needs widget check](feedback_owner_signed_values_still_need_widget_validation.md) - Owner sign-off on a data table (preset matrix, config CSV, defaults) is approval of intent, not proof the values are accepted by the widget that renders them - check step/option domain before writing.
- [Never pass inference as owner's](feedback_never_attribute_agent_inference_to_owner.md) - Specs must not state an agent's guess about the owner's motivation as if the owner reported it - the owner disowned an invented "accidental VR exits" complaint on 2026-07-28
