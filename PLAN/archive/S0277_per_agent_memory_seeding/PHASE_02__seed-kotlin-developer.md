# Phase 02 - Seed `android-kotlin-developer`

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

Populate `.claude/agent-memory/android-kotlin-developer/` with 23 starter memory files derived from the donor and the two user-memory ritual invariants, plus a `MEMORY.md` index. Each file carries a target-profile-adapted `**How to apply:**` block.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Phase 01 mapping table is frozen.
- [ ] Target dir `.claude/agent-memory/android-kotlin-developer/` exists (empty placeholder is acceptable).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/agent-memory/android-kotlin-developer/MEMORY.md` | New | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/feedback_timber_tags_before_test.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-kotlin-developer/feedback_features_nolegal.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/android-kotlin-developer/feedback_timestamp_in_chat.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/android-kotlin-developer/feedback_flavor_isolation_strict.md` | New (U) | ≤ 80 |
| `.claude/agent-memory/android-kotlin-developer/project_build_gotchas.md` | New (U) | ≤ 80 |
| `.claude/agent-memory/android-kotlin-developer/project_agp_manifest_srcfile_overrides_flavor_manifest.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/project_functionality_log.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/feedback_no_backticks_in_bash_args.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-kotlin-developer/feedback_build_pre_existing_test_failures.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/project_catalog_scan_source_sets.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-kotlin-developer/project_catalog_set_ps1_stops_on_error.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-kotlin-developer/project_msal_signing_hash_per_keystore.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/feedback_no_owner_questions_when_architecture_already_answers.md` | New (U) | ≤ 80 |
| `.claude/agent-memory/android-kotlin-developer/feedback_verify_subagent_build_failures.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/feedback_pwsh_efficiency.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/feedback_dont_infer_from_buildconfig_names.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-kotlin-developer/feedback_build_output_pipe_truncation.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/android-kotlin-developer/project_vr_inclusion_hierarchy.md` | New (U) | ≤ 70 |
| `.claude/agent-memory/android-kotlin-developer/feedback_log_levels.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/android-kotlin-developer/feedback_no_scaffolding_as_done.md` | New (U) | ≤ 70 |
| `.claude/agent-memory/android-kotlin-developer/feedback_check_generated_binding_types.md` | New (U) | ≤ 80 |
| `.claude/agent-memory/android-kotlin-developer/feedback_pwsh_bash_dollar_escape_trap.md` | New (U) | ≤ 100 |
| `.claude/agent-memory/android-kotlin-developer/project_minsdk_flavors.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/android-kotlin-developer/user_author_style.md` | New (user-ritual) | ≤ 30 |
| `.claude/agent-memory/android-kotlin-developer/feedback_pwsh_path.md` | New (user-ritual) | ≤ 30 |

> No file in this phase reaches the 500-LOC backup threshold; no backups required.

---

## Steps

### Step 02.1 - Write all 25 memory files and the MEMORY.md index

**Files:** every file in "Files Touched".
**Depends on:** - start of phase

**Prompt for developer:**

> For each row in Phase 01 mapping table where the K column is `U`, create a corresponding `.md` file in `.claude/agent-memory/android-kotlin-developer/` with:
>
> - A frontmatter block carrying `name`, `description`, and `metadata.type` matching the donor record.
> - The body verbatim from the donor record.
> - A re-written `**How to apply:**` line that targets the Kotlin developer responsibility (writing/editing `.kt` and build configs, running `/build`, syncing the class catalogue).
>
> Add the two user-ritual records (`user_author_style.md`, `feedback_pwsh_path.md`) with the same frontmatter shape.
>
> Then write `MEMORY.md` as a flat index: `- [Title](file.md) - one-line hook`. Keep under 60 lines.

**Verification:**

- `Glob` - all 25 files listed in "Files Touched" exist.
- `Grep` - every `*.md` in `.claude/agent-memory/android-kotlin-developer/` (excluding `MEMORY.md`) contains the literal token `**How to apply:**` (or, for user-ritual records, an equivalent role-targeted note).
- `Grep` - `MEMORY.md` contains ≥ 25 lines starting with `- [`.

**Status:** `[x]` done

---

### Step 02.2 - Validate frontmatter shape

**Files:** `.claude/agent-memory/android-kotlin-developer/*.md` (excluding `MEMORY.md`).
**Depends on:** Step 02.1

**Prompt for developer:**

> Open every newly-written file and verify the frontmatter block:
>
> ```
> ---
> name: <short-kebab-case-slug>
> description: <one-line summary>
> metadata:
>   type: <user|feedback|project|reference>
> ---
> ```
>
> No `{{placeholder}}` tokens, no missing keys.

**Verification:**

- `Grep` - `name:` appears in every `*.md` (excluding `MEMORY.md`) under the target dir.
- `Grep` - `metadata:` appears in every `*.md` (excluding `MEMORY.md`) under the target dir.
- `Grep` - `{{` returns zero hits inside `.claude/agent-memory/android-kotlin-developer/`.

**Status:** `[x]` done

---

### Step 02.3 - Dev-log every new file

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`).
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `scripts/post-change.ps1 -ChangeType Doc` once per new file (or a single batched call where the script supports it). Description must be in English and name the seeded invariant explicitly.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains at least 25 new lines tagged with `android-kotlin-developer` from 2026-05-21.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All steps `[x]`.
- [ ] `git diff --stat .claude/agent-memory/android-rd-specialist/` empty (donor unchanged).
- [ ] `git diff --stat .claude/agents/ .claude/settings*.json` empty (harness config untouched).

---

## Handoff Notes to Next Phase

`android-kotlin-developer` is fully seeded; phases 03 and 04 follow the same pattern with their own per-profile subset from the Phase 01 mapping table.

---

## Rollback Plan

`rm -rf .claude/agent-memory/android-kotlin-developer/*.md` and revert `MEMORY.md`. No code or harness config is affected.
