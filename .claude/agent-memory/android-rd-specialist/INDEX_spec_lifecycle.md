---
name: index-spec-lifecycle
description: Second-level pointer list for spec lifecycle, catalog scripts, and planning conventions. Open when authoring, updating, validating or closing specs.
metadata:
  type: reference
---

# Spec Lifecycle & Catalog - pointers

Split out of `MEMORY.md` (S1731, 2026-08-17): memories specific to spec creation, catalog synchronization, owner input gates, probe tags and closure protocols. Open this file when running `/spec*` commands or manipulating specs and catalogs.

- [Rework = new ticket](feedback_new_ticket_not_reopen_for_rework.md) - never reopen a shipped one; old id is context only
- [Unblocks at BlockNeedUserTest](feedback_blocker_unblocks_at_needusertest.md) - don't wait for Verified
- [Tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) · [Verify spec id first](feedback_verify_spec_id_before_pipeline.md)
- [DRIFT from a commit mention](feedback_drift_check_false_positive_on_commit_mention.md) - 0 markers = not done
- [drift-check reads .kt only](feedback_drift_check_scans_kotlin_only.md) - XML-only fix looks CLEAN; grep layouts
- [IDE finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) · [Draft style gate](feedback_draft_style_gate.md) · [Status auto-syncs](feedback_spec_header_autosync.md)
- [§0 alone fails the gate](feedback_owner_inputs_gate_needs_section_33.md) - check-owner-inputs reads only §3.3; add it alongside
- [Owner's answer may outrank the menu](feedback_owner_answer_may_outrank_the_menu.md) - or contradict the option he picked; show the collision back
- [Owner gate](feedback_strategic_spec_owner_gate.md) · [§3.3 ≠ owner ruling](feedback_spec_tech_ui_placement_refusal.md) · [plan quality](feedback_spec_tech_plan_quality.md) · [Phase audit](feedback_phase_boundary_audit.md)
- [Verify code first](feedback_spec_dev_continue_verify_code_first.md) · [Plan file lists can be wrong](feedback_tactical_plan_file_list_may_be_wrong.md)
- [Verify a capture's claims](feedback_old_capture_may_be_superseded.md) - age is not the tell · [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md)
- [Superseded phases: delete, don't tick](feedback_delete_superseded_phase_files_not_tick.md) - /spec-dev would undo it
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) · [no fake autopilot blocker](feedback_no_safety_blocker_gating_autopilot.md)
- [No quotes in -StatusNote](feedback_status_note_quotes_corrupt_catalog.md)
- [Status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) · [close.ps1 2-step](project_close_ps1_two_step_unblock.md)
- [Predicates grep Timber form](feedback_probe_predicate_names_timber_form.md) · [Zero-hit predicate](feedback_zero_hit_predicate_cannot_name_the_literal.md)
- [Probe marker must start a line](feedback_probe_marker_must_start_a_line.md) - mid-line = `contracts: 0`, gate passes vacuously
- [Busy = lease, not status](feedback_ticket_busyness_is_a_lease_not_a_status.md) + [queue driver mute in Stage 0](project_spec_all_queue_driver_stage0_silence.md)
- [Long gap voids the round](feedback_long_gap_invalidates_round_state.md)
- [Capability inventory](project_functionality_log.md) · [flavors from the gate](feedback_feature_record_flavors_from_gate.md) · [noLegal features](feedback_features_nolegal.md)
- [Owner translates in bulk](project_owner_external_translation_route.md) - never hand-translate the ten
- [Scan roots](project_catalog_scan_source_sets.md) · [Dedup: 1 word](feedback_spec_dedup_query_shape.md)
- [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) · [-Search coverage](reference_catalog_search_coverage.md) · [Big-file decomposition](project_s0002_decomposition_toolkit.md)
