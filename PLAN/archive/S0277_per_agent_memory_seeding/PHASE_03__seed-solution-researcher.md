# Phase 03 - Seed `android-solution-researcher`

**Strategic spec:** [../S0277_per_agent_memory_seeding.md](../S0277_per_agent_memory_seeding.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Populate `.claude/agent-memory/android-solution-researcher/` with the role-appropriate subset of starter memory derived from the donor and the two user-memory ritual invariants. The researcher profile is read-only by definition; every adapted record must reflect "reading and reporting" rather than "writing and committing".

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Phase 01 mapping table is frozen.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/agent-memory/android-solution-researcher/MEMORY.md` | New | ≤ 50 |
| `.claude/agent-memory/android-solution-researcher/feedback_timber_tags_before_test.md` | New (R) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/feedback_features_nolegal.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/android-solution-researcher/feedback_timestamp_in_chat.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/android-solution-researcher/feedback_flavor_isolation_strict.md` | New (R) | ≤ 80 |
| `.claude/agent-memory/android-solution-researcher/project_build_gotchas.md` | New (R) | ≤ 80 |
| `.claude/agent-memory/android-solution-researcher/project_agp_manifest_srcfile_overrides_flavor_manifest.md` | New (R) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/project_functionality_log.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/feedback_no_backticks_in_bash_args.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-solution-researcher/feedback_build_pre_existing_test_failures.md` | New (R) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/project_catalog_scan_source_sets.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-solution-researcher/project_catalog_set_ps1_stops_on_error.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-solution-researcher/project_msal_signing_hash_per_keystore.md` | New (R) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/feedback_no_owner_questions_when_architecture_already_answers.md` | New (U) | ≤ 80 |
| `.claude/agent-memory/android-solution-researcher/feedback_verify_subagent_build_failures.md` | New (R) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/feedback_pwsh_efficiency.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/feedback_dont_infer_from_buildconfig_names.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-solution-researcher/feedback_build_output_pipe_truncation.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-solution-researcher/project_vr_inclusion_hierarchy.md` | New (U) | ≤ 70 |
| `.claude/agent-memory/android-solution-researcher/feedback_log_levels.md` | New (R) | ≤ 60 |
| `.claude/agent-memory/android-solution-researcher/feedback_no_scaffolding_as_done.md` | New (U) | ≤ 70 |
| `.claude/agent-memory/android-solution-researcher/feedback_pwsh_bash_dollar_escape_trap.md` | New (U) | ≤ 100 |
| `.claude/agent-memory/android-solution-researcher/project_minsdk_flavors.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/android-solution-researcher/user_author_style.md` | New (user-ritual) | ≤ 30 |
| `.claude/agent-memory/android-solution-researcher/feedback_pwsh_path.md` | New (user-ritual) | ≤ 30 |

---

## Steps

### Step 03.1 - Write all 23 memory files plus user-ritual records

**Files:** every file in "Files Touched".
**Depends on:** - start of phase

**Prompt for developer:**

> For each row in the Phase 01 mapping table where the R column is `U` or `R`, create a file in `.claude/agent-memory/android-solution-researcher/`:
>
> - **U rows:** body verbatim from donor; only `**How to apply:**` line re-targeted to "as a read-only researcher: when reading existing code / build logs / catalog data".
> - **R rows:** rewrite the body to address research consumption rather than code authorship. Example: `feedback_log_levels` becomes "Reading Timber.e in code or logs is a strong signal of dev-action-required; do not equate it with expected fallbacks." `feedback_timber_tags_before_test` becomes "When grepping for `Timber.d("Sxxxx:` in research, treat presence as live-test gate, never as evidence of finished work."
>
> Add the two user-ritual records unchanged in body but with researcher-targeted `**How to apply:**`.

**Verification:**

- `Glob` - all 24 files (23 invariants + MEMORY.md) exist under target dir.
- `Grep` - every record file contains `**How to apply:**`.
- `Grep` - zero hits for the literal token `write Kotlin` inside the target dir (researcher profile must not be prompted to write code).

**Status:** `[x]` done

---

### Step 03.2 - Validate frontmatter shape

**Files:** `.claude/agent-memory/android-solution-researcher/*.md` (excluding `MEMORY.md`).
**Depends on:** Step 03.1

**Prompt for developer:**

> Same frontmatter validation as Phase 02.2.

**Verification:**

- `Grep` - `name:` appears in every record.
- `Grep` - `metadata:` appears in every record.
- `Grep` - `{{` returns zero hits under the target dir.

**Status:** `[x]` done

---

### Step 03.3 - Dev-log every new file

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`).
**Depends on:** Step 03.2

**Prompt for developer:**

> Same dev-log procedure as Phase 02.3 - description names the seeded invariant.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains at least 23 new lines tagged with `android-solution-researcher` from 2026-05-21.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All steps `[x]`.
- [ ] `git diff --stat .claude/agent-memory/android-rd-specialist/` empty.
- [ ] No record file contains an imperative-mood instruction to edit Kotlin (preserves read-only role).

---

## Handoff Notes to Next Phase

Researcher profile is fully seeded; Phase 04 covers the documentation profile.

---

## Rollback Plan

`rm -rf .claude/agent-memory/android-solution-researcher/*.md`. No code or harness config is affected.
