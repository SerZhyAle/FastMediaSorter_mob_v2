# Phase 01 - Inventory and Mapping

**Strategic spec:** [../S0277_per_agent_memory_seeding.md](../S0277_per_agent_memory_seeding.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Produce an authoritative mapping table that for each donor invariant declares (a) which of the three target profiles receives a copy, and (b) whether the copy is verbatim-with-How-to-apply tweak or a role-rewritten variant. The mapping is the single source of truth consumed by phases 02-04.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §0 + §6 fully resolved (`Approved`).
- [ ] Donor directory `.claude/agent-memory/android-rd-specialist/` is read-only for the duration of S0277.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0277_per_agent_memory_seeding/PHASE_01__inventory-and-mapping.md` | Modified (this file - mapping table appended) | n/a |

> No code or memory files are written in this phase. The output is the mapping table inside this phase document.

---

## Steps

### Step 01.1 - Enumerate donor records and classify universal vs role-specific

**Files:** read-only enumeration of `.claude/agent-memory/android-rd-specialist/` and user-memory entries flagged as ritual invariants (`user_author_style`, `feedback_pwsh_path`).
**Depends on:** - start of phase

**Prompt for developer:**

> List every `*.md` file in the donor agent-memory directory. Classify each into one of three buckets:
>
> - **U (Universal ritual invariant):** mechanically applies to any agent that touches scripts, tooling, or chat tone. Body unchanged; only the `**How to apply:**` line is re-targeted per profile.
> - **R (Role-rewritten):** applies in spirit but the technical content must be rewritten for the target profile's responsibility (e.g. Kotlin-specific build trap → researcher receives a re-phrased "reading the build output" variant).
> - **N (Not applicable):** does not belong in a given target profile; skip.
>
> Also enumerate the two user-memory ritual invariants explicitly approved by §0 (`author_style`, `pwsh_path`); personal `user`-records (email, hardware, currentDate) are excluded by §0 scope.

**Verification:**

- Inventory table below populated with one row per donor record + the two universal user-records.
- Every row carries explicit U/R/N marks for each of the three target profiles.

**Status:** `[x]` done

---

### Step 01.2 - Lock the mapping table

**Files:** `PLAN/S0277_per_agent_memory_seeding/PHASE_01__inventory-and-mapping.md` (this file)
**Depends on:** Step 01.1

**Prompt for developer:**

> Once the table below is filled, treat it as the contract for phases 02-04. Subsequent phases must not re-evaluate U/R/N marks; they only apply the decisions.

**Verification:**

- No `?` characters remain in the U/R/N columns of the mapping table.
- Each profile column produces a non-empty list of records to seed.

**Status:** `[x]` done

---

## Mapping Table (output of this phase)

Columns: **K** = `android-kotlin-developer`, **R** = `android-solution-researcher`, **D** = `friendly-android-doc-writer`.
Marks: **U** = universal copy (body verbatim, How-to-apply re-targeted), **R** = role-rewritten, **N** = not applicable.

| # | Donor record | K | R | D | Rationale |
|---|--------------|:-:|:-:|:-:|-----------|
| 1 | `feedback_timber_tags_before_test.md` | U | R | N | K writes Kotlin and inserts/removes tags; R reads tags from code/logs without writing; D does not touch `.kt`. |
| 2 | `feedback_features_nolegal.md` | U | U | U | All three may touch `FEATURES*.md`; routing rule is universal. |
| 3 | `feedback_timestamp_in_chat.md` | U | U | U | Chat-output ritual applies to every agent. |
| 4 | `feedback_flavor_isolation_strict.md` | U | R | N | K writes flavor-isolated code; R must understand it when researching; D rarely touches flavor source sets. |
| 5 | `project_build_gotchas.md` | U | R | N | K runs builds; R interprets build failures during research; D is doc-only. |
| 6 | `project_agp_manifest_srcfile_overrides_flavor_manifest.md` | U | R | N | Build/manifest mechanics matter to K; R needs to recognise the pattern; D not applicable. |
| 7 | `project_functionality_log.md` | U | U | U | Functionality log applies to user-visible changes any agent may produce. |
| 8 | `feedback_no_backticks_in_bash_args.md` | U | U | U | Tool-call hygiene applies to every Bash invocation. |
| 9 | `feedback_build_pre_existing_test_failures.md` | U | R | N | K runs/interprets unit tests; R interprets test reports during research; D not applicable. |
| 10 | `project_catalog_scan_source_sets.md` | U | U | N | K runs scan after `.kt` changes; R uses catalog as primary discovery tool; D rarely runs scan. |
| 11 | `project_catalog_set_ps1_stops_on_error.md` | U | U | N | Same scope as #10. |
| 12 | `project_msal_signing_hash_per_keystore.md` | U | R | N | K may touch signing wiring; R may need to explain hash chain; D not applicable. |
| 13 | `feedback_no_owner_questions_when_architecture_already_answers.md` | U | U | U | Anti-fabrication rule applies to every agent. |
| 14 | `feedback_verify_subagent_build_failures.md` | U | R | N | K spawns sub-agents and must verify; R may consume sub-agent reports; D not applicable. |
| 15 | `feedback_pwsh_efficiency.md` | U | U | U | All three call PowerShell scripts. |
| 16 | `feedback_dont_infer_from_buildconfig_names.md` | U | U | N | K must verify usage before treating a constant as a gate; R must verify before reporting. D not applicable. |
| 17 | `feedback_build_output_pipe_truncation.md` | U | U | N | K reads build logs after failures; R reads logs during research; D not applicable. |
| 18 | `project_vr_inclusion_hierarchy.md` | U | U | N | VR/noLegal source-set hierarchy is required when K writes flavor code and when R researches flavor-gated features. D may surface it indirectly via docs but does not need the rule itself. |
| 19 | `feedback_log_levels.md` | U | R | N | K chooses log level when writing code; R interprets log severity when reading; D not applicable. |
| 20 | `feedback_no_scaffolding_as_done.md` | U | U | U | "Don't call scaffolding done" applies to any deliverable; D version covers documentation drafts. |
| 21 | `feedback_check_generated_binding_types.md` | U | N | N | View-binding downcast trap is Kotlin-implementation-specific. |
| 22 | `feedback_strategic_spec_owner_gate.md` | N | N | N | Spec-pipeline owner-gate is owned by the spec-skill agents (rd-specialist); none of the three target profiles drives `/spec*` directly. |
| 23 | `feedback_pwsh_bash_dollar_escape_trap.md` | U | U | U | Generic Bash/pwsh interop trap; applies anywhere shell commands are composed. |
| 24 | `feedback_verify_spec_id_before_pipeline.md` | N | N | N | Same scope as #22 - spec-pipeline-only. |
| 25 | `project_minsdk_flavors.md` | U | U | N | minSdk per flavor is foundational for K when picking APIs and for R when reasoning about constraints. D does not need it. |
| 26 | `user_author_style` (user-memory) | U | U | U | Universal style rule (`..` and `ё`/`Ё`); single most-cited author-style invariant. |
| 27 | `feedback_pwsh_path` (user-memory) | U | U | U | pwsh 7 path is required by every PowerShell call. |

**Per-profile totals (target):**

- `android-kotlin-developer` (K): 23 records (24 minus #22 and #24).
- `android-solution-researcher` (R): 20 records.
- `friendly-android-doc-writer` (D): 9 records (universal-only subset).

---

## Phase Done Criteria

- [ ] Step 01.1 marked `[x]` and the table above shows no `?` placeholders.
- [ ] Step 01.2 marked `[x]`.
- [ ] Per-profile totals frozen; phases 02-04 reference this table only.

---

## Handoff Notes to Next Phase

Phases 02-04 consume the mapping table verbatim. Per-record `**How to apply:**` adaptation happens inside each seeding phase, not here.

---

## Rollback Plan

Revert this phase file to the prior commit. No external artefacts are produced.
